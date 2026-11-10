"""Offline publication tests: synthetic Java 17 JAR, mocked HTTP, no credentials."""
import argparse
import copy
from email import policy
from email.parser import BytesParser
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch, MagicMock
import urllib.error
import urllib.parse
import urllib.request
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import publish_release as publish


def jar_bytes(version="20.1.1", major=61, mod_id="createpackage", extra=b""):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("META-INF/mods.toml", f'[[mods]]\nmodId="{mod_id}"\nversion="{version}"\n')
        archive.writestr("Example.class", b"\xca\xfe\xba\xbe\0\0" + major.to_bytes(2, "big") + extra)
    return output.getvalue()


def parts(call):
    _, _, _, kwargs = call
    message = BytesParser(policy=policy.default).parsebytes(
        f'Content-Type: {kwargs["content_type"]}\r\nMIME-Version: 1.0\r\n\r\n'.encode() + kwargs["data"])
    return {part.get_param("name", header="content-disposition"): part.get_payload(decode=True)
            for part in message.iter_parts()}


class FakeHTTP:
    def __init__(self):
        self.release = dict(id=7, tag_name="v20.1.1", draft=False, published_at="2026-01-01T00:00:00Z",
                            prerelease=False, name="Create Package 20.1.1", body="用户编辑正文\n\n- release notes")
        self.assets = []
        self.blobs = {}
        self.calls = []
        self.versions = []
        self.fail_platform = False
        self.fail_receipt = False
        self.add_asset("createpackage-20.1.1.jar", jar_bytes())

    def add_asset(self, name, raw):
        asset = dict(id=len(self.blobs) + 1, name=name, size=len(raw), browser_download_url="https://evil.invalid/never-used")
        self.assets.append(asset)
        self.blobs[str(asset["id"])] = raw
        return asset

    def platform_writes(self):
        return [c for c in self.calls if c[0] != "GET" and c[1].startswith((publish.MR, publish.CF))]

    def request(self, method, url, token, **kwargs):
        self.calls.append((method, url, token, kwargs))
        if method == "GET" and "/releases/tags/" in url:
            return copy.deepcopy(self.release)
        if method == "GET" and "/assets?per_page=" in url:
            return copy.deepcopy(self.assets)
        if method == "GET" and "/releases/assets/" in url:
            return self.blobs[url.rsplit("/", 1)[-1]]
        if method == "GET" and url.startswith(publish.MR):
            return copy.deepcopy(self.versions)
        if method == "POST" and url.startswith(publish.UPLOAD):
            name = urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)["name"][0]
            if any(a["name"] == name for a in self.assets):
                raise AssertionError("State must never be replaced")
            asset = self.add_asset(name, kwargs["data"])
            if self.fail_receipt and name.endswith("-receipt.json"):
                raise publish.PublicationError("Lost receipt response")
            return asset
        if url.startswith((publish.MR, publish.CF)) and method != "GET":
            if self.fail_platform:
                raise publish.PublicationError("Unknown platform result")
            if url == publish.MR + "/version":
                metadata = json.loads(parts(self.calls[-1])["data"])
                self.versions.append(dict(metadata, id="Remote123", files=[dict(primary=True, hashes={
                    "sha512": hashlib.sha512(parts(self.calls[-1])["file"]).hexdigest()})]))
                return {"id": "Remote123"}
            return {"id": 456}
        raise AssertionError(f"Unexpected mock request: {method} {url}")


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.args = argparse.Namespace(repo="owner/repo", tag="v20.1.1", platform="curseforge",
                                       directory=Path(self.temp.name), project_id=None, adopt_file_id=None)
        self.env = dict(GH_TOKEN="gh-secret", MODRINTH_TOKEN="mr-secret", CURSEFORGE_TOKEN="cf-secret")
        self.http = FakeHTTP()
        # Any accidental real urllib HTTP (including new code paths) is forbidden.
        self.network = patch("urllib.request.OpenerDirector.open", side_effect=AssertionError("Real HTTP forbidden"))
        self.network.start()
        self.addCleanup(self.network.stop)

    def run_publish(self):
        return publish.publish(self.args, self.http, self.env)

    def assert_blocked(self):
        with self.assertRaises((ValueError, KeyError, TypeError)):
            self.run_publish()
        self.assertFalse(self.http.platform_writes())
        self.assertFalse([c for c in self.http.calls if c[0] != "GET"])

    def test_default_project_ids_are_exact(self):
        self.assertEqual(publish.DEFAULT_PROJECTS, {"modrinth": "5vJMWJv6", "curseforge": "1596843"})

    def test_missing_jar(self):
        self.http.assets.clear()
        self.assert_blocked()

    def test_empty_body(self):
        self.http.release["body"] = " \n\t"
        self.assert_blocked()

    def test_fake_jar_and_metadata_java_validation(self):
        for raw in (b"not a ZIP", b"", jar_bytes(version="20.1.2"), jar_bytes(major=65), jar_bytes(mod_id="wrong")):
            with self.subTest(raw=raw[:12]):
                self.http = FakeHTTP()
                self.http.blobs["1"] = raw
                self.http.assets[0]["size"] = len(raw)
                self.assert_blocked()

    def test_multiple_jars(self):
        self.http.add_asset("other-20.1.1.jar", jar_bytes())
        self.assert_blocked()

    def test_sources_jar_is_not_primary(self):
        self.http.add_asset("createpackage-20.1.1-sources.jar", b"ignored")
        self.assertEqual(self.run_publish(), "456")

    def test_invalid_release_or_identity(self):
        for field, value in (("draft", True), ("published_at", None), ("tag_name", "v20.1.2")):
            with self.subTest(field=field):
                self.http = FakeHTTP()
                self.http.release[field] = value
                self.assert_blocked()
        for repo, tag in (("owner/repo?token=x", "v20.1.1"), ("owner/repo", "v21.1.1"), ("owner/repo", "v20.1.1;echo")):
            self.http = FakeHTTP()
            self.args.repo, self.args.tag = repo, tag
            self.assert_blocked()

    def test_cf_create_payload_and_state_order(self):
        self.assertEqual(self.run_publish(), "456")
        calls = self.http.platform_writes()
        self.assertEqual(len(calls), 1)
        self.assertTrue(calls[0][1].endswith("/projects/1596843/upload-file"))
        fields = parts(calls[0])
        self.assertEqual(fields["file"], self.http.blobs["1"])
        meta = json.loads(fields["metadata"])
        self.assertEqual(meta["changelog"], self.http.release["body"])
        self.assertEqual(meta["gameVersionNames"], ["1.20.1", "Forge", "Client", "Server"])
        self.assertEqual(meta["changelogType"], "markdown")
        self.assertEqual(meta["releaseType"], "release")
        self.assertEqual(meta["relations"]["projects"], [
            dict(slug="create", projectID=328085, type="requiredDependency"),
            dict(slug="applied-energistics-2", projectID=223794, type="requiredDependency")])
        writes = [c for c in self.http.calls if c[0] != "GET"]
        self.assertIn("-intent.json", writes[0][1])
        self.assertEqual(writes[1], calls[0])
        self.assertIn("-receipt.json", writes[2][1])
        self.assertTrue(all("evil.invalid" not in c[1] for c in self.http.calls))

    def test_edit_updates_only_and_keeps_state_immutable(self):
        self.run_publish()
        original = copy.deepcopy(self.http.assets)
        self.http.calls.clear()
        self.http.release["body"] = "完全新用户正文"
        self.run_publish()
        calls = self.http.platform_writes()
        self.assertEqual(len(calls), 1)
        self.assertTrue(calls[0][1].endswith("/update-file"))
        fields = parts(calls[0])
        self.assertEqual(set(fields), {"metadata"})
        self.assertEqual(json.loads(fields["metadata"])["changelog"], "完全新用户正文")
        self.assertEqual(json.loads(fields["metadata"])["fileID"], 456)
        self.assertEqual(self.http.assets, original)
        self.assertTrue(any(c[0] == "GET" and c[1].endswith("/assets/1") for c in self.http.calls))

    def test_update_still_requires_github_jar(self):
        self.run_publish()
        self.http.assets.pop(0)
        self.http.calls.clear()
        self.assert_blocked()

    def test_changed_jar_hash_refused_even_with_cf_receipt_and_same_size(self):
        self.http.blobs["1"] = jar_bytes(extra=b"old code")
        self.http.assets[0]["size"] = len(self.http.blobs["1"])
        self.run_publish()
        replacement = jar_bytes(extra=b"new code")
        self.assertEqual(len(replacement), self.http.assets[0]["size"])
        self.http.blobs["1"] = replacement
        self.http.calls.clear()
        self.assert_blocked()

    def test_pending_intent_never_retries_create(self):
        self.http.fail_platform = True
        with self.assertRaises(publish.PublicationError):
            self.run_publish()
        self.http.calls.clear()
        self.http.fail_platform = False
        self.assert_blocked()

    def test_cf_adopt_pending_then_update_no_jar_upload(self):
        self.http.fail_platform = True
        with self.assertRaises(publish.PublicationError):
            self.run_publish()
        self.http.fail_platform = False
        self.http.calls.clear()
        self.args.adopt_file_id = "456"
        self.assertEqual(self.run_publish(), "456")
        self.assertEqual(set(parts(self.http.platform_writes()[0])), {"metadata"})
        self.assertIn("-receipt.json", self.http.assets[-1]["name"])

    def test_adopt_without_intent_refused(self):
        self.args.adopt_file_id = "456"
        self.assert_blocked()

    def test_lost_receipt_response_recovers_without_new_upload(self):
        self.http.fail_receipt = True
        with self.assertRaises(publish.PublicationError):
            self.run_publish()
        self.http.fail_receipt = False
        self.http.calls.clear()
        self.assertEqual(self.run_publish(), "456")
        self.assertTrue(self.http.platform_writes()[0][1].endswith("/update-file"))
        self.assertFalse(any(c[1].startswith(publish.UPLOAD) for c in self.http.calls))

    def test_mr_create_and_edit(self):
        self.args.platform = "modrinth"
        self.assertEqual(self.run_publish(), "Remote123")
        call = self.http.platform_writes()[0]
        fields = parts(call)
        meta = json.loads(fields["data"])
        self.assertEqual(fields["file"], self.http.blobs["1"])
        self.assertEqual(meta["changelog"], self.http.release["body"])
        self.assertEqual(meta["file_parts"], ["file"])
        self.assertEqual(meta["primary_file"], "file")
        self.assertIs(meta["featured"], False)
        self.assertEqual(meta["status"], "listed")
        self.assertEqual(meta["environment"], "client_and_server")
        self.assertEqual(meta["project_id"], "5vJMWJv6")
        self.assertEqual(meta["dependencies"], [dict(project_id=p, dependency_type="required") for p in ("LNytGWDc", "XxWD5pD3")])
        self.http.calls.clear()
        self.http.release["body"] = "edited"
        self.run_publish()
        calls = self.http.platform_writes()
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0][0], "PATCH")
        self.assertEqual(json.loads(calls[0][3]["data"])["changelog"], "edited")
        self.assertNotIn("file", json.loads(calls[0][3]["data"]))

    def test_mr_recovers_pending_by_existing_version_hash(self):
        self.args.platform = "modrinth"
        self.run_publish()
        self.http.assets = [a for a in self.http.assets if not a["name"].endswith("-receipt.json")]
        self.http.calls.clear()
        self.assertEqual(self.run_publish(), "Remote123")
        self.assertEqual([c[0] for c in self.http.platform_writes()], ["PATCH"])
        self.assertIn("-receipt.json", self.http.assets[-1]["name"])

    def test_mr_pending_without_remote_never_retries(self):
        self.args.platform = "modrinth"
        self.http.fail_platform = True
        with self.assertRaises(publish.PublicationError):
            self.run_publish()
        self.http.calls.clear()
        self.http.fail_platform = False
        self.assert_blocked()

    def test_mr_existing_tag_alias_sha1_recovers_without_create(self):
        self.args.platform = "modrinth"
        self.http.versions = [dict(id="Existing1", project_id="5vJMWJv6", version_number="v20.1.1",
                                  game_versions=["1.20.1"], loaders=["forge"], files=[dict(primary=True,
                                  hashes={"sha1": hashlib.sha1(self.http.blobs["1"]).hexdigest()})])]
        self.assertEqual(self.run_publish(), "Existing1")
        self.assertEqual([c[0] for c in self.http.platform_writes()], ["PATCH"])

    def test_every_state_identity_field_must_match(self):
        self.run_publish()
        state_asset = next(a for a in self.http.assets if a["name"].endswith("-intent.json"))
        key = str(state_asset["id"])
        original = json.loads(self.http.blobs[key])
        for field in original:
            with self.subTest(field=field):
                self.http.blobs[key] = publish.encoded(dict(original, **{field: "wrong"}))
                self.http.calls.clear()
                self.assert_blocked()
        self.http.blobs[key] = publish.encoded(original)

    def test_jar_name_and_size_must_match(self):
        for field, value in (("name", "createpackage-20.1.10.jar"), ("name", "../createpackage-20.1.1.jar"),
                             ("size", 1)):
            with self.subTest(field=field, value=value):
                self.http = FakeHTTP()
                self.http.assets[0][field] = value
                self.assert_blocked()

    def test_mr_hash_mc_loader_id_ambiguity_refused(self):
        self.args.platform = "modrinth"
        self.run_publish()
        baseline = copy.deepcopy(self.http.versions[0])
        for key, value in (("files", [{"primary": True, "hashes": {"sha512": "bad"}}]),
                           ("loaders", ["fabric"]), ("game_versions", ["1.21.1"]), ("id", "different")):
            with self.subTest(key=key):
                self.http.versions = [dict(baseline, **{key: value})]
                self.http.calls.clear()
                self.assert_blocked()
        self.http.versions = [baseline, baseline]
        self.http.calls.clear()
        self.assert_blocked()


class TransportTests(unittest.TestCase):
    def test_cf_header_and_sanitized_error_no_retry(self):
        http = publish.HTTP()
        http.opener = MagicMock()
        http.opener.open.side_effect = urllib.error.HTTPError("https://secret.invalid", 401, "token-secret", {}, io.BytesIO(b"token-secret"))
        with self.assertRaises(publish.PublicationError) as caught:
            http.request("POST", publish.CF + "/projects/1596843/upload-file", "token-secret", data=b"x")
        self.assertNotIn("secret", str(caught.exception))
        self.assertEqual(http.opener.open.call_count, 1)
        request = http.opener.open.call_args.args[0]
        self.assertEqual(request.get_header("X-api-token"), "token-secret")
        self.assertIsNone(request.get_header("Authorization"))

    def test_host_guard_and_redirect_token_stripping(self):
        http = publish.HTTP()
        with self.assertRaises(publish.PublicationError):
            http.request("GET", "https://evil.invalid/api", "secret")
        handler = publish.SafeRedirect()
        request = urllib.request.Request(publish.GH + "/repos/o/r/releases/assets/1", headers={"Authorization": "Bearer secret"})
        redirected = handler.redirect_request(request, None, 302, "", {}, "https://release-assets.githubusercontent.com/file?signature=test")
        self.assertIsNone(redirected.get_header("Authorization"))
        with self.assertRaises(publish.PublicationError):
            handler.redirect_request(request, None, 302, "", {}, "https://evil.invalid/file")
        with self.assertRaises(publish.PublicationError):
            handler.redirect_request(urllib.request.Request(publish.MR + "/version", method="POST"), None, 307, "", {}, publish.GH)


if __name__ == "__main__":
    unittest.main()
