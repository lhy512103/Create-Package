#!/usr/bin/env python3
"""Publish a validated GitHub release JAR and its *current* body (stdlib only).

Run one serialized job per platform with GH_TOKEN and MODRINTH_TOKEN or
CURSEFORGE_TOKEN. Intent and receipt assets are immutable: never delete them.
Updates reuse the uploaded file after verifying its hash; they never reupload it.
Even an update requires the original GitHub JAR. An ambiguous create fails closed;
--adopt-file-id is an operator assertion that the remote file and hash were checked
(CurseForge has no legacy API to verify that assertion). No HTTP write is retried.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

from release_metadata import validate_jar, version_from_tag

GH = "https://api.github.com"
UPLOAD = "https://uploads.github.com"
MR = "https://api.modrinth.com/v2"
CF = "https://minecraft.curseforge.com/api"
DEFAULT_PROJECTS = {"modrinth": "5vJMWJv6", "curseforge": "1596843"}


class PublicationError(ValueError):
    pass


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        target = urllib.parse.urlsplit(newurl)
        if req.get_method() != "GET" or target.scheme != "https" or not (
            target.hostname == "api.github.com" or
            (target.hostname or "").endswith(".githubusercontent.com")
        ):
            raise PublicationError("Refused HTTP redirect")
        # GitHub's asset API may redirect to a signed CDN URL. Never forward tokens.
        return urllib.request.Request(newurl, headers={"User-Agent": "CreatePackage-release"})


class HTTP:
    def __init__(self):
        self.opener = urllib.request.build_opener(SafeRedirect())

    def request(self, method, url, token, *, data=None, content_type=None, binary=False):
        host = urllib.parse.urlsplit(url)
        if host.scheme != "https" or host.netloc not in {
            "api.github.com", "uploads.github.com", "api.modrinth.com", "minecraft.curseforge.com"
        }:
            raise PublicationError("Refused non-official API host")
        headers = {"User-Agent": "CreatePackage-release", "Accept": "application/json"}
        headers["X-Api-Token" if host.netloc == "minecraft.curseforge.com" else "Authorization"] = (
            token if host.netloc in {"minecraft.curseforge.com", "api.modrinth.com"} else f"Bearer {token}"
        )
        if binary:
            headers["Accept"] = "application/octet-stream"
        if content_type:
            headers["Content-Type"] = content_type
        try:
            with self.opener.open(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=120) as response:
                raw = response.read()
            return raw if binary else (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as error:
            # Never include response bodies, URLs, or exception strings (possibly secrets).
            raise PublicationError(f"HTTP {error.code}; no automatic retry; inspect remote state") from None
        except (urllib.error.URLError, TimeoutError, OSError, ValueError):
            raise PublicationError("HTTP/JSON failure; no automatic retry; inspect remote state") from None


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True).encode("utf-8")


def multipart(field, metadata, jar=None):
    boundary = "release-" + uuid.uuid4().hex
    chunks = [f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"\r\nContent-Type: application/json\r\n\r\n'.encode(), encoded(metadata), b"\r\n"]
    if jar is not None:
        name, raw = jar
        chunks += [f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: application/java-archive\r\n\r\n'.encode(), raw, b"\r\n"]
    chunks += [f"--{boundary}--\r\n".encode()]
    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


def checked_id(value, *, numeric=False):
    value = str(value)
    if not re.fullmatch(r"[1-9][0-9]*" if numeric else r"[A-Za-z0-9]+", value):
        raise PublicationError("Invalid platform/release identifier")
    return value


def get_assets(http, repo, release_id, token):
    assets = []
    for page in range(1, 101):
        batch = http.request("GET", f"{GH}/repos/{repo}/releases/{release_id}/assets?per_page=100&page={page}", token)
        if not isinstance(batch, list):
            raise PublicationError("Invalid GitHub asset list")
        assets.extend(batch)
        if len(batch) < 100:
            return assets
    raise PublicationError("Too many release assets")


def asset_bytes(http, repo, asset, token):
    asset_id = checked_id(asset["id"], numeric=True)
    return http.request("GET", f"{GH}/repos/{repo}/releases/assets/{asset_id}", token, binary=True)


def state_asset(http, repo, assets, name, token):
    matches = [a for a in assets if a["name"] == name]
    if len(matches) > 1:
        raise PublicationError("Duplicate publication state assets")
    if not matches:
        return None
    try:
        state = json.loads(asset_bytes(http, repo, matches[0], token))
    except (ValueError, UnicodeError):
        raise PublicationError("Invalid publication state JSON") from None
    if not isinstance(state, dict):
        raise PublicationError("Invalid publication state")
    return state


def persist(http, repo, release_id, name, state, token):
    # Never replace/delete an existing asset; a lost response is recovered by GET next run.
    http.request("POST", f"{UPLOAD}/repos/{repo}/releases/{release_id}/assets?name={urllib.parse.quote(name)}", token,
                 data=encoded(state), content_type="application/json")


def verify_state(state, expected):
    if any(state.get(key) != value for key, value in expected.items()):
        raise PublicationError("Publication state/JAR mismatch; use a new version, never replace the remote JAR")


def verify_mr(remote, project, version, tag, raw):
    if remote.get("project_id") != project or remote.get("version_number") not in {version, tag}:
        raise PublicationError("Modrinth project/version mismatch")
    if remote.get("game_versions") != ["1.20.1"] or remote.get("loaders") != ["forge"]:
        raise PublicationError("Modrinth Minecraft/loader mismatch")
    files = remote.get("files", [])
    primary = [f for f in files if f.get("primary")] or files[:1]
    if len(primary) != 1:
        raise PublicationError("Modrinth primary JAR is ambiguous")
    hashes = primary[0].get("hashes", {})
    algorithms = [a for a in ("sha512", "sha1") if a in hashes]
    if not algorithms or any(hashes[a] != hashlib.new(a, raw).hexdigest() for a in algorithms):
        raise PublicationError("Modrinth JAR hash differs; use a new version")
    return checked_id(remote["id"])


def publish(args, http=None, env=None):
    http = http or HTTP()
    env = os.environ if env is None else env
    if not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9_.-]{0,99})/[A-Za-z0-9](?:[A-Za-z0-9_.-]{0,99})", args.repo):
        raise PublicationError("Invalid owner/repo")
    version = version_from_tag(args.tag, minecraft_version="1.20.1")
    if not version.startswith("20.1."):
        raise PublicationError("Only Forge 20.1.* versions are publishable")
    if not re.fullmatch(r"[A-Za-z0-9_.-]{1,128}", args.tag):
        raise PublicationError("Unsafe tag")
    platform = args.platform
    project = checked_id(args.project_id or env.get(f"{platform.upper()}_PROJECT_ID") or DEFAULT_PROJECTS[platform], numeric=platform == "curseforge")
    gh_token = env.get("GH_TOKEN")
    token = env.get("MODRINTH_TOKEN" if platform == "modrinth" else "CURSEFORGE_TOKEN")
    if not gh_token or not token:
        raise PublicationError("Missing GH_TOKEN or platform token")
    release = http.request("GET", f"{GH}/repos/{args.repo}/releases/tags/{urllib.parse.quote(args.tag, safe='')}", gh_token)
    if release.get("draft") is not False or not release.get("published_at") or release.get("tag_name") != args.tag:
        raise PublicationError("Expected a published non-draft release with the exact tag")
    body = release.get("body")
    if not isinstance(body, str) or not body.strip():
        raise PublicationError("GitHub release body is empty")
    release_id = checked_id(release["id"], numeric=True)
    assets = get_assets(http, args.repo, release_id, gh_token)
    jars = [a for a in assets if a["name"].lower().endswith(".jar") and not re.search(r"(?:^|[-_.])(?:sources?|dev|javadoc)(?:[-_.]|$)", a["name"], re.I)]
    if len(jars) != 1:
        raise PublicationError("Expected exactly one primary release JAR")
    asset = jars[0]
    name = asset["name"]
    if not re.fullmatch(r"[A-Za-z0-9_.+-]+\.jar", name) or not re.search(r"(?<![0-9.])" + re.escape(version) + r"(?![A-Za-z0-9.])", name[:-4]):
        raise PublicationError("JAR filename must safely match release version")
    raw = asset_bytes(http, args.repo, asset, gh_token)
    if not raw or len(raw) != asset.get("size"):
        raise PublicationError("GitHub JAR is empty or size mismatched")
    directory = Path(args.directory)
    directory.mkdir(parents=True, exist_ok=True)
    jar = directory / name
    jar.write_bytes(raw)
    validate_jar(jar, mod_id="createpackage", version=version, check_java17=True)
    expected = dict(repo=args.repo, release_id=release_id, tag=args.tag, platform=platform,
                    project_id=project, jarname=name, size=len(raw), sha512=hashlib.sha512(raw).hexdigest())
    prefix = f"_publication-forge-1.20.1-{platform}"
    intent = state_asset(http, args.repo, assets, prefix + "-intent.json", gh_token)
    receipt = state_asset(http, args.repo, assets, prefix + "-receipt.json", gh_token)
    for state in (intent, receipt):
        if state is not None:
            verify_state(state, expected)
    if receipt is not None and intent is None:
        raise PublicationError("Receipt exists without immutable intent")
    remote_id = checked_id(receipt["file_id"], numeric=platform == "curseforge") if receipt else None
    adopt = checked_id(args.adopt_file_id, numeric=platform == "curseforge") if args.adopt_file_id else None
    if adopt and not intent:
        raise PublicationError("Adoption requires an existing matching intent")
    if adopt and remote_id and adopt != remote_id:
        raise PublicationError("Adoption cannot change a completed receipt")
    if platform == "modrinth":
        versions = http.request("GET", f"{MR}/project/{project}/version", token)
        matches = [v for v in versions if v.get("version_number") in {version, args.tag}]
        if len(matches) > 1:
            raise PublicationError("Ambiguous Modrinth versions")
        if matches:
            found = verify_mr(matches[0], project, version, args.tag, raw)
            if (remote_id and remote_id != found) or (adopt and adopt != found):
                raise PublicationError("Modrinth receipt/adoption ID mismatch")
            remote_id = found
        elif remote_id or adopt:
            raise PublicationError("Modrinth recorded version is missing")
    elif adopt:
        remote_id = adopt  # Explicit operator assertion; legacy CF has no hash lookup.
    if intent and not remote_id:
        raise PublicationError("Pending intent: upload result unknown; verify remote file then --adopt-file-id")
    if not intent:
        persist(http, args.repo, release_id, prefix + "-intent.json", expected, gh_token)
    kind = "alpha" if "-alpha" in version else "beta" if "-" in version or release.get("prerelease") else "release"
    title = release.get("name") or args.tag
    if platform == "modrinth":
        metadata = dict(name=title, changelog=body, version_type=kind)
        if remote_id:
            http.request("PATCH", f"{MR}/version/{remote_id}", token, data=encoded(metadata), content_type="application/json")
        else:
            metadata.update(project_id=project, version_number=version, game_versions=["1.20.1"], loaders=["forge"],
                            dependencies=[dict(project_id=p, dependency_type="required") for p in ("LNytGWDc", "XxWD5pD3")],
                            file_parts=["file"], primary_file="file", featured=False,
                             status="listed", environment="client_and_server")
            payload, content_type = multipart("data", metadata, (name, raw))
            result = http.request("POST", f"{MR}/version", token, data=payload, content_type=content_type)
            remote_id = checked_id(result["id"])
    else:
        metadata = dict(changelog=body, changelogType="markdown", displayName=title, releaseType=kind,
                        gameVersionNames=["1.20.1", "Forge", "Client", "Server"],
                        relations={"projects": [dict(slug=s, projectID=p, type="requiredDependency") for s, p in
                                                (("create", 328085), ("applied-energistics-2", 223794))]})
        if remote_id:
            metadata["fileID"] = int(remote_id)
        payload, content_type = multipart("metadata", metadata, None if remote_id else (name, raw))
        result = http.request("POST", f"{CF}/projects/{project}/{'update-file' if remote_id else 'upload-file'}", token,
                              data=payload, content_type=content_type)
        result_id = checked_id(result["id"], numeric=True)
        if remote_id and result_id != remote_id:
            raise PublicationError("CurseForge update returned a different file ID")
        remote_id = result_id
    if receipt is None:
        persist(http, args.repo, release_id, prefix + "-receipt.json", dict(expected, file_id=remote_id), gh_token)
    return remote_id


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--platform", choices=DEFAULT_PROJECTS, required=True)
    parser.add_argument("--directory", type=Path, default=Path("release-download"))
    parser.add_argument("--project-id")
    parser.add_argument("--adopt-file-id", help="Operator-confirmed existing remote file ID and identical hash")
    args = parser.parse_args(argv)
    try:
        remote_id = publish(args)
    except PublicationError as error:
        # These are controlled local messages; HTTP response bodies never enter them.
        print(f"Publication refused: {error}", file=sys.stderr)
        return 1
    except (ValueError, OSError, KeyError, TypeError):
        # Do not echo arbitrary server data/metadata/exception strings.
        print("Publication refused or result unknown; check release/JAR/state and recover explicitly. No automatic retry.", file=sys.stderr)
        return 1
    print(f"Published/updated {args.platform} file {remote_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
