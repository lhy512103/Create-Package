#!/usr/bin/env python3
"""Fail-closed Forge 1.20.1 release metadata (Python 3.11+, stdlib only).

CLI: python tools/release_metadata.py --tag v20.1.1 --output release-notes.md
Add --jar build/libs/<exact-file>.jar to validate the publishable artifact.
Paths for gradle.properties and CHANGELOG.md are anchored to this repository,
not the caller's working directory. --output is relative to the caller.

Public helpers raise ValueError for invalid release data. extract_changelog
accepts Markdown text, not a filename. validate_jar returns an absolute Path.
No artifact discovery, Gradle invocation, network access, or publishing occurs.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import sys
import tomllib
import zipfile


MINECRAFT_VERSION = "1.20.1"
_NUMBER = r"(?:0|[1-9][0-9]*)"
_VERSION = rf"{_NUMBER}\.{_NUMBER}\.{_NUMBER}(?:-(?:alpha|beta|rc)(?:[.-]?[0-9]+)?)?"
_VERSION_RE = re.compile(rf"{_VERSION}\Z", re.ASCII)
_TAG_RE = re.compile(rf"(?:forge-1\.20\.1-v|v)?(?P<version>{_VERSION})\Z", re.ASCII)
_HEADING_RE = re.compile(r"^ {0,3}(#{1,6})[ \t]+(.+?)[ \t]*#*[ \t]*$")
_VERSION_HEADING_RE = re.compile(
    rf"^(?:\[v?(?P<bracket>{_VERSION})\]|v?(?P<plain>{_VERSION}))(?=$|\s)",
    re.ASCII,
)
_LANGUAGE_NAMES = {"english": "English", "中文": "中文", "简体中文": "中文"}
_CLASSIFIER_RE = re.compile(r"(?:^|[-_.])(?:sources?|dev|javadoc)(?:[-_.]|$)", re.I)


def read_properties(path: str | Path) -> dict[str, str]:
    """Read the repository's simple UTF-8 Java properties (key=value or key:value).

    Comments and blank lines are ignored. Duplicate keys and continuation lines
    are rejected rather than silently choosing a potentially incorrect version.
    Java escape/continuation syntax is deliberately not interpreted.
    """
    result: dict[str, str] = {}
    for number, raw in enumerate(Path(path).read_text(encoding="utf-8-sig").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if line.endswith("\\"):
            raise ValueError(f"Unsupported property continuation at line {number}")
        match = re.fullmatch(r"([^\s=:]+)\s*[=:]\s*(.*)", line)
        if not match:
            raise ValueError(f"Invalid property at line {number}")
        key, value = match.groups()
        if key in result:
            raise ValueError(f"Duplicate property: {key}")
        result[key] = value.strip()
    return result


def version_from_tag(
    tag: str,
    expected_version: str | None = None,
    minecraft_version: str = MINECRAFT_VERSION,
) -> str:
    """Accept only VERSION, vVERSION or forge-1.20.1-vVERSION.

    VERSION is a three-part numeric version, optionally suffixed with alpha,
    beta or rc (e.g. -alpha, -beta.2, -rc1). No refs/tags/, build metadata,
    whitespace, shell metacharacters, path separators or other prefixes pass.
    If supplied, expected_version must match exactly, including prerelease.
    """
    if minecraft_version != MINECRAFT_VERSION:
        raise ValueError(f"Only Minecraft {MINECRAFT_VERSION} is supported")
    match = _TAG_RE.fullmatch(tag) if isinstance(tag, str) and len(tag) <= 128 else None
    if not match:
        raise ValueError("Invalid tag; expected vVERSION, VERSION or forge-1.20.1-vVERSION")
    version = match.group("version")
    if expected_version is not None and version != expected_version:
        raise ValueError(f"Tag version {version} does not match mod_version {expected_version}")
    return version


def _headings(lines: list[str]) -> list[tuple[int, int, str]]:
    """Find ATX headings outside fenced code blocks."""
    headings: list[tuple[int, int, str]] = []
    fence_char = ""
    fence_length = 0
    for index, line in enumerate(lines):
        fence = re.match(r"^ {0,3}(`{3,}|~{3,})(.*)$", line)
        if fence:
            marker, tail = fence.groups()
            if not fence_char:
                fence_char, fence_length = marker[0], len(marker)
            elif marker[0] == fence_char and len(marker) >= fence_length and not tail.strip():
                fence_char = ""
            continue
        if not fence_char:
            match = _HEADING_RE.match(line)
            if match:
                headings.append((index, len(match.group(1)), match.group(2).strip()))
    return headings


def _has_content(body: str) -> bool:
    body = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    for line in body.splitlines():
        text = line.strip()
        if not text or _HEADING_RE.match(line) or re.fullmatch(r"[-*_\s]+", text):
            continue
        if re.sub(r"^(?:[-*+]|[0-9]+[.)])(?:\s+|$)", "", text).strip():
            return True
    return False


def extract_changelog(text: str, version: str) -> str:
    """Extract all exact level-two version sections, never Unreleased.

    Supports a version section containing bilingual subheadings, and the legacy
    layout with separate repeated version sections under ## English / ## 中文.
    Legacy sections receive explicit language headings. Any level-one/two
    heading ends a version section, so unrelated top-level material cannot leak.
    Original prose, lists and nested headings are preserved in document order.
    """
    if not _VERSION_RE.fullmatch(version):
        raise ValueError("Invalid changelog version")
    lines = text.splitlines()
    headings = _headings(lines)
    language: str | None = None
    sections: list[str] = []
    seen_languages: set[str | None] = set()
    for position, (start, level, title) in enumerate(headings):
        if level == 1:
            language = _LANGUAGE_NAMES.get(title.casefold())
        elif level == 2 and title.casefold() in _LANGUAGE_NAMES:
            language = _LANGUAGE_NAMES[title.casefold()]
        if level != 2:
            continue
        match = _VERSION_HEADING_RE.match(title)
        if not match or (match.group("bracket") or match.group("plain")) != version:
            continue
        end = next((index for index, depth, _ in headings[position + 1:] if depth <= level), len(lines))
        body_lines = lines[start + 1:end]
        # A legacy language section may be followed by a horizontal separator.
        while body_lines and (not body_lines[-1].strip() or re.fullmatch(r" {0,3}(?:-{3,}|\*{3,}|_{3,})\s*", body_lines[-1])):
            body_lines.pop()
        body = "\n".join(body_lines).strip()
        if not _has_content(body):
            raise ValueError(f"Changelog for {version} is empty")
        if language in seen_languages:
            raise ValueError(f"Ambiguous duplicate changelog for {version} ({language or 'unlabelled'})")
        seen_languages.add(language)
        sections.append(f"## {language}\n\n{body}" if language else body)
    if not sections:
        raise ValueError(f"No changelog section for {version}; Unreleased is not a release fallback")
    return "\n\n".join(sections) + "\n"


def validate_jar(
    path: str | Path,
    mod_id: str,
    version: str,
    *,
    check_java17: bool = False,
) -> Path:
    """Validate one exact non-classifier JAR and return its resolved path.

    Requires a nonempty valid ZIP, exactly one META-INF/mods.toml, exactly one
    [[mods]] entry with the expected literal modId/version, and valid CRCs.
    Unexpanded ${...} placeholders are not accepted. No manifest fallback or
    glob expansion is performed. Optional check_java17 rejects class major >61
    (including classes embedded in multi-release locations in this archive).
    """
    if not isinstance(path, (str, Path)):
        raise ValueError("Specify exactly one JAR path, not a list")
    if any(character in str(path) for character in "\r\n*?[]"):
        raise ValueError("JAR path must be one literal path without globs or newlines")
    jar = Path(path).resolve()
    if jar.suffix.lower() != ".jar" or _CLASSIFIER_RE.search(jar.stem):
        raise ValueError("Expected a release .jar, not a sources/dev/javadoc artifact")
    if not jar.is_file() or jar.stat().st_size == 0:
        raise ValueError(f"JAR is missing or empty: {jar}")
    try:
        with zipfile.ZipFile(jar) as archive:
            entries = archive.infolist()
            names = [entry.filename for entry in entries]
            if len(names) != len(set(names)):
                raise ValueError("JAR contains duplicate ZIP entries")
            if names.count("META-INF/mods.toml") != 1:
                raise ValueError("JAR must contain exactly one META-INF/mods.toml")
            if archive.testzip() is not None:
                raise ValueError("JAR failed ZIP CRC validation")
            metadata = tomllib.loads(archive.read("META-INF/mods.toml").decode("utf-8-sig"))
            mods = metadata.get("mods")
            if not isinstance(mods, list) or len(mods) != 1 or not isinstance(mods[0], dict):
                raise ValueError("JAR must declare exactly one [[mods]] entry")
            if mods[0].get("modId") != mod_id or mods[0].get("version") != version:
                raise ValueError(f"JAR modId/version must be exactly {mod_id}/{version}")
            if check_java17:
                for entry in entries:
                    if entry.filename.endswith(".class"):
                        with archive.open(entry) as source:
                            header = source.read(8)
                        if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                            raise ValueError(f"Invalid class header: {entry.filename}")
                        if int.from_bytes(header[6:8], "big") > 61:
                            raise ValueError(f"Class exceeds Java 17: {entry.filename}")
    except (zipfile.BadZipFile, UnicodeError, tomllib.TOMLDecodeError, RuntimeError, NotImplementedError, EOFError) as error:
        raise ValueError(f"Invalid JAR: {error}") from error
    return jar


def _github_outputs(values: dict[str, str]) -> str:
    for key, value in values.items():
        if "\r" in value or "\n" in value:
            raise ValueError(f"GitHub output {key} must be a single line")
    return "".join(f"{key}={value}\n" for key, value in values.items())


def main(argv: list[str] | None = None) -> int:
    """Write UTF-8 release notes and append metadata to GITHUB_OUTPUT if set."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", required=True, type=Path, help="UTF-8 release notes destination")
    parser.add_argument("--jar", type=Path, help="One exact built release JAR (no glob)")
    parser.add_argument("--check-java17", action="store_true", help="Also reject classes newer than Java 17; requires --jar")
    args = parser.parse_args(argv)
    if args.check_java17 and args.jar is None:
        parser.error("--check-java17 requires --jar")
    root = Path(__file__).resolve().parents[1]
    try:
        properties = read_properties(root / "gradle.properties")
        for name in ("mod_version", "mod_id", "minecraft_version"):
            if not properties.get(name):
                raise ValueError(f"Missing required property: {name}")
        mod_id = properties["mod_id"]
        if not re.fullmatch(r"[a-z][a-z0-9_]{1,63}", mod_id, re.ASCII):
            raise ValueError("Invalid mod_id")
        version = version_from_tag(args.tag, properties["mod_version"], properties["minecraft_version"])
        notes = extract_changelog((root / "CHANGELOG.md").read_text(encoding="utf-8-sig"), version)
        jar = validate_jar(args.jar, mod_id, version, check_java17=args.check_java17) if args.jar else None
        # Avoid accidentally replacing source inputs or the validated artifact.
        output = args.output.resolve()
        protected = {root / "gradle.properties", root / "CHANGELOG.md", Path(__file__).resolve()}
        if jar:
            protected.add(jar)
        if output in protected:
            raise ValueError("Release notes output would overwrite a release input")
        version_type = "alpha" if "-alpha" in version else "beta" if "-" in version else "release"
        values = {
            "version": version,
            "minecraft_version": properties["minecraft_version"],
            "mod_id": mod_id,
            "jar_path": str(jar) if jar else "",
            "title": f"{properties.get('mod_name', mod_id)} {version} (Forge {MINECRAFT_VERSION})",
            "version_type": version_type,
        }
        github_values = _github_outputs(values)
        output.write_text(notes, encoding="utf-8", newline="\n")
        if os.environ.get("GITHUB_OUTPUT"):
            with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8", newline="\n") as destination:
                destination.write(github_values)
        print(github_values, end="")
    except (OSError, ValueError) as error:
        print(f"release_metadata: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
