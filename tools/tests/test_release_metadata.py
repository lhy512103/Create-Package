"""Run with: python -m unittest discover -s tools/tests -p test_release_metadata.py -v."""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import os
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("release_metadata", ROOT / "tools" / "release_metadata.py")
assert SPEC is not None and SPEC.loader is not None
metadata = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(metadata)

LEGACY = """# Changelog
## Unreleased — Forge 1.20.1
### 中文
1. 不可发布
### English
1. Unreleased change
## English
## [20.1.2] - 2026-11-08
### Added
1. Added: English first.
2. Added: English second.
### Fixed
1. Fixed: English fix.
## [20.1.1] - 2026-08-08
### Added
- Old English.
---
# 更新日志
## 中文
## [20.1.2] - 2026-11-08
### 新增
1. 新增：中文第一条。
2. 新增：中文第二条。
### 修复
1. 修复：中文修复。
## [20.1.1] - 2026-08-08
### 新增
- 旧中文。
"""

NEW = """# Changelog
## Unreleased — Forge 1.20.1
### 中文
1. 尚未发布。
### English
1. Not yet released.
## 20.1.2 — Forge 1.20.1 (2026-11-08)
### 中文
1. 新增：第一项。
2. 修复：第二项。
### English
1. Added: First item.
2. Fixed: Second item.
## [20.1.1] - 2026-08-08
### English
1. Old release.
# Other documentation
Do not publish this.
"""


class PropertiesTests(unittest.TestCase):
    def test_reads_real_properties(self):
        properties = metadata.read_properties(ROOT / "gradle.properties")
        self.assertEqual(properties["minecraft_version"], "1.20.1")
        self.assertEqual(properties["mod_id"], "createpackage")
        self.assertTrue(properties["mod_version"])

    def test_simple_syntax_bom_comments_and_unicode(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "gradle.properties"
            path.write_text("\ufeff# comment\n! ignored\n key = value=x\nname: 中文\n", encoding="utf-8")
            self.assertEqual(metadata.read_properties(path), {"key": "value=x", "name": "中文"})

    def test_ambiguous_or_unsupported_properties_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "gradle.properties"
            for content in ("key=a\nkey=b", "broken", "key=value\\\ncontinued"):
                with self.subTest(content=content):
                    path.write_text(content, encoding="utf-8")
                    with self.assertRaises(ValueError):
                        metadata.read_properties(path)


class TagTests(unittest.TestCase):
    def test_accepted_prefixes(self):
        for tag in ("v20.1.2", "20.1.2", "forge-1.20.1-v20.1.2"):
            with self.subTest(tag=tag):
                self.assertEqual(metadata.version_from_tag(tag, "20.1.2"), "20.1.2")

    def test_prereleases(self):
        for suffix in ("alpha", "alpha1", "alpha.2", "beta", "beta.12", "rc", "rc1", "rc.3"):
            version = f"20.1.2-{suffix}"
            for prefix in ("", "v", "forge-1.20.1-v"):
                with self.subTest(version=version, prefix=prefix):
                    self.assertEqual(metadata.version_from_tag(prefix + version, version), version)

    def test_unsafe_and_nonconforming_tags_fail(self):
        for tag in ("", "v20.1", "v20.1.2\n", " v20.1.2", "v20.1.2 ", "refs/tags/v20.1.2",
                    "../v20.1.2", "v20.1.2;echo owned", "$(whoami)", "v20.1.2/notes", "v20.1.2\\notes",
                    "forge-1.21.1-v20.1.2", "forge-1.20.1-20.1.2", "v20.1.2+build", "v020.1.2",
                    "v20.1.2-preview.1", "v20.1.2-rc.1/../", "v２０.1.2", "v" + "1" * 130):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                metadata.version_from_tag(tag)

    def test_exact_property_and_minecraft_match_required(self):
        for tag, expected, mc in (("v20.1.2", "20.1.1", "1.20.1"),
                                  ("v20.1.2-rc1", "20.1.2", "1.20.1"),
                                  ("v20.1.2", "20.1.2", "1.21.1")):
            with self.subTest(tag=tag, expected=expected, mc=mc), self.assertRaises(ValueError):
                metadata.version_from_tag(tag, expected, mc)


class ChangelogTests(unittest.TestCase):
    def test_legacy_repeated_version_preserves_both_complete_languages(self):
        result = metadata.extract_changelog(LEGACY, "20.1.2")
        for expected in ("## English", "## 中文", "English first.", "English second.", "English fix.",
                         "中文第一条。", "中文第二条。", "中文修复。", "### Added", "### 修复"):
            self.assertIn(expected, result)
        for excluded in ("Unreleased", "不可发布", "Old English", "旧中文", "20.1.1", "# 更新日志", "---"):
            self.assertNotIn(excluded, result)
        self.assertEqual(result.count("English second."), 1)

    def test_new_bilingual_version_section(self):
        result = metadata.extract_changelog(NEW, "20.1.2")
        for expected in ("### 中文", "### English", "第一项。", "第二项。", "First item.", "Second item."):
            self.assertIn(expected, result)
        for excluded in ("Unreleased", "尚未发布", "Not yet released", "Old release", "Other documentation", "Do not publish"):
            self.assertNotIn(excluded, result)

    def test_bracketed_and_v_version_headings(self):
        for title in ("[20.1.2] - 2026-11-08", "[v20.1.2] - date", "v20.1.2 — Forge 1.20.1", "20.1.2"):
            with self.subTest(title=title):
                self.assertEqual(metadata.extract_changelog(f"## {title}\n- Release change.\n", "20.1.2"), "- Release change.\n")

    def test_missing_version_has_no_unreleased_or_full_document_fallback(self):
        for text in (LEGACY, NEW, "", "## Unreleased — Forge 1.20.1\n- only change", "## [20.1.20]\n- Wrong version"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                metadata.extract_changelog(text, "20.1.9" if text in (LEGACY, NEW) else "20.1.2")

    def test_empty_release_fails(self):
        for body in ("", "\n", "### 中文\n### English", "<!-- TODO -->", "### Added\n---\n", "1. \n- "):
            with self.subTest(body=body), self.assertRaises(ValueError):
                metadata.extract_changelog(f"## [20.1.2]\n{body}\n## Unreleased\n- Not released", "20.1.2")

    def test_top_level_and_same_level_headings_end_release(self):
        for ending in ("# Updating\nSecret text", "## Other\nSecret text", "## English\nSecret text"):
            with self.subTest(ending=ending):
                result = metadata.extract_changelog("## [20.1.2]\n- Correct.\n" + ending, "20.1.2")
                self.assertEqual(result, "- Correct.\n")

    def test_fenced_headings_do_not_truncate_prose(self):
        result = metadata.extract_changelog("## [20.1.2]\n- Before\n```md\n## Example\n```\n- After\n## [20.1.1]\n- Old", "20.1.2")
        self.assertIn("## Example", result)
        self.assertIn("- After", result)
        self.assertNotIn("- Old", result)

    def test_ambiguous_same_language_repeated_version_fails(self):
        with self.assertRaises(ValueError):
            metadata.extract_changelog("## [20.1.2]\n- First\n## [20.1.2]\n- Second", "20.1.2")

    def test_real_legacy_release_excludes_current_unreleased(self):
        result = metadata.extract_changelog((ROOT / "CHANGELOG.md").read_text(encoding="utf-8"), "20.1.1")
        self.assertIn("## English", result)
        self.assertIn("## 中文", result)
        self.assertIn("Extended Kinetic Pattern Provider", result)
        self.assertIn("扩展动力样板供应器", result)
        self.assertNotIn("Unreleased", result)
        self.assertNotIn("20.1.0", result)
        self.assertNotIn("Machine Linker", result)


class JarTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.jar = Path(self.temporary.name) / "createpackage-20.1.2.jar"

    def make_jar(self, *, text=None, filename="META-INF/mods.toml", extra=None):
        if text is None:
            text = 'modLoader="javafml"\n[[mods]]\nmodId="createpackage"\nversion="20.1.2"\n'
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr(filename, text)
            for name, content in (extra or {}).items():
                archive.writestr(name, content)
        return self.jar

    def validate(self, **kwargs):
        return metadata.validate_jar(self.jar, mod_id="createpackage", version="20.1.2", **kwargs)

    def test_valid_jar_returns_absolute_path(self):
        self.make_jar()
        self.assertEqual(self.validate(), self.jar.resolve())

    def test_missing_empty_fake_zip_and_empty_zip_fail(self):
        with self.assertRaises(ValueError):
            self.validate()
        for content in (b"", b"not a zip", b"PK\x03\x04invalid"):
            with self.subTest(content=content):
                self.jar.write_bytes(content)
                with self.assertRaises(ValueError):
                    self.validate()
        with zipfile.ZipFile(self.jar, "w"):
            pass
        with self.assertRaises(ValueError):
            self.validate()

    def test_missing_or_wrong_case_metadata_fails(self):
        for filename in ("mods.toml", "META-INF/MODS.TOML", "nested/META-INF/mods.toml"):
            with self.subTest(filename=filename):
                self.make_jar(filename=filename)
                with self.assertRaises(ValueError):
                    self.validate()

    def test_wrong_unexpanded_ambiguous_and_invalid_metadata_fail(self):
        for text in ('[[mods]]\nmodId="wrong"\nversion="20.1.2"',
                     '[[mods]]\nmodId="createpackage"\nversion="20.1.1"',
                     '[[mods]]\nmodId="${mod_id}"\nversion="${mod_version}"',
                     '[[mods]]\nmodId="createpackage"\nversion="${file.jarVersion}"',
                     '[[mods]]\nmodId="createpackage"\nversion=20',
                     '[[mods]]\nmodId="createpackage"',
                     '[[mods]]\nmodId="createpackage"\nversion="20.1.2"\n[[mods]]\nmodId="extra"\nversion="1.0.0"',
                     '[mods]\nmodId="createpackage"\nversion="20.1.2"',
                     'this is not TOML', ''):
            with self.subTest(text=text):
                self.make_jar(text=text)
                with self.assertRaises(ValueError):
                    self.validate()

    def test_duplicate_metadata_entry_fails(self):
        self.make_jar()
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.jar, "a") as archive:
                archive.writestr("META-INF/mods.toml", '[[mods]]\nmodId="createpackage"\nversion="20.1.2"')
        with self.assertRaises(ValueError):
            self.validate()

    def test_sources_dev_javadoc_nonjar_and_glob_fail(self):
        self.make_jar()
        for filename in ("mod-sources.jar", "mod-source.jar", "mod-dev.jar", "mod-javadoc.jar", "mod.jar.txt", "*.jar"):
            with self.subTest(filename=filename), self.assertRaises(ValueError):
                metadata.validate_jar(self.jar.parent / filename, mod_id="createpackage", version="20.1.2")

    def test_multiple_paths_rejected(self):
        self.make_jar()
        with self.assertRaises(ValueError):
            metadata.validate_jar([self.jar, self.jar], mod_id="createpackage", version="20.1.2")

    def test_crc_corruption_rejected(self):
        self.make_jar()
        original = self.jar.read_bytes()
        self.jar.write_bytes(original.replace(b'modLoader="javafml"', b'modLoader="xxxxxfl"', 1))
        with self.assertRaises(ValueError):
            self.validate()

    def test_optional_java17_major_check(self):
        for major in (52, 61, 65):
            with self.subTest(major=major):
                self.make_jar(extra={"example/Main.class": struct.pack(">IHH", 0xCAFEBABE, 0, major)})
                if major <= 61:
                    self.assertEqual(self.validate(check_java17=True), self.jar.resolve())
                else:
                    self.assertEqual(self.validate(), self.jar.resolve())
                    with self.assertRaises(ValueError):
                        self.validate(check_java17=True)

    def test_invalid_class_header_rejected_when_checking_java17(self):
        self.make_jar(extra={"example/Main.class": b"fake"})
        with self.assertRaises(ValueError):
            self.validate(check_java17=True)


class CliTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "tools").mkdir()
        self.properties = self.root / "gradle.properties"
        self.properties.write_text("mod_version=20.1.2\nmod_id=createpackage\nminecraft_version=1.20.1\nmod_name=Create Package\n", encoding="utf-8")
        (self.root / "CHANGELOG.md").write_text(NEW, encoding="utf-8")
        self.output = self.root / "notes.md"
        self.github = self.root / "github-output"
        self.jar = self.root / "createpackage-20.1.2.jar"
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("META-INF/mods.toml", '[[mods]]\nmodId="createpackage"\nversion="20.1.2"')

    def run_cli(self, *extra, tag="v20.1.2", output=None):
        with patch.object(metadata, "__file__", str(self.root / "tools" / "release_metadata.py")), \
             patch.dict(os.environ, {"GITHUB_OUTPUT": str(self.github)}), \
             redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            return metadata.main(["--tag", tag, "--output", str(output or self.output), *map(str, extra)])

    def test_cli_writes_notes_and_all_outputs_with_validated_jar(self):
        self.github.write_text("existing=value\n", encoding="utf-8")
        self.assertEqual(self.run_cli("--jar", self.jar), 0)
        self.assertIn("第一项", self.output.read_text(encoding="utf-8"))
        self.assertNotIn("Not yet released", self.output.read_text(encoding="utf-8"))
        outputs = dict(line.split("=", 1) for line in self.github.read_text(encoding="utf-8").splitlines())
        self.assertEqual(outputs, {
            "existing": "value", "version": "20.1.2", "minecraft_version": "1.20.1",
            "mod_id": "createpackage", "jar_path": str(self.jar.resolve()),
            "title": "Create Package 20.1.2 (Forge 1.20.1)", "version_type": "release",
        })

    def test_notes_only_does_not_claim_jar_validated(self):
        self.assertEqual(self.run_cli(), 0)
        self.assertIn("jar_path=\n", self.github.read_text(encoding="utf-8"))

    def test_failure_writes_no_notes_or_github_outputs(self):
        for mode in ("mismatch", "missing-notes", "bad-jar", "wrong-minecraft"):
            with self.subTest(mode=mode):
                (self.root / "CHANGELOG.md").write_text(NEW if mode != "missing-notes" else "## Unreleased\n- pending", encoding="utf-8")
                self.properties.write_text(f"mod_version=20.1.2\nmod_id=createpackage\nminecraft_version={'1.21.1' if mode == 'wrong-minecraft' else '1.20.1'}\n", encoding="utf-8")
                extra = ("--jar", self.root / "missing.jar") if mode == "bad-jar" else ()
                self.assertEqual(self.run_cli(*extra, tag="v20.1.1" if mode == "mismatch" else "v20.1.2"), 1)
                self.assertFalse(self.output.exists())
                self.assertFalse(self.github.exists())

    def test_cli_rejects_multiple_jars(self):
        with self.assertRaises(SystemExit) as raised:
            self.run_cli("--jar", self.jar, self.jar)
        self.assertEqual(raised.exception.code, 2)

    def test_java_check_requires_jar(self):
        with self.assertRaises(SystemExit) as raised:
            self.run_cli("--check-java17")
        self.assertEqual(raised.exception.code, 2)

    def test_output_cannot_overwrite_artifact_or_inputs(self):
        for target in (self.jar, self.properties, self.root / "CHANGELOG.md"):
            with self.subTest(target=target):
                original = target.read_bytes()
                self.assertEqual(self.run_cli("--jar", self.jar, output=target), 1)
                self.assertEqual(target.read_bytes(), original)

    def test_prerelease_output_types(self):
        for suffix, expected in (("alpha.1", "alpha"), ("beta.2", "beta"), ("rc1", "beta")):
            with self.subTest(suffix=suffix):
                version = f"20.1.2-{suffix}"
                self.properties.write_text(f"mod_version={version}\nmod_id=createpackage\nminecraft_version=1.20.1\n", encoding="utf-8")
                (self.root / "CHANGELOG.md").write_text(f"## [{version}]\n### 中文\n1. 修复：测试。\n### English\n1. Fixed: Test.\n", encoding="utf-8")
                self.assertEqual(self.run_cli(tag=f"v{version}"), 0)
                self.assertIn(f"version_type={expected}\n", self.github.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
