#!/usr/bin/env python3
"""Publish a tag's GitHub Release only after its validated mod JAR is attached."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

from release_metadata import extract_changelog, read_properties, validate_jar, version_from_tag


def gh(*arguments: str, allow_missing: bool = False):
    result = subprocess.run(['gh', *arguments], capture_output=True, text=True, encoding='utf-8')
    if result.returncode:
        if allow_missing and 'HTTP 404' in result.stderr:
            return None
        raise ValueError(f'GitHub CLI failed (exit {result.returncode}); check repository permissions and the workflow log')
    return result.stdout


def release(repo: str, tag: str):
    response = gh('api', f'repos/{repo}/releases/tags/{tag}', allow_missing=True)
    return json.loads(response) if response is not None else None


def publish(repo: str, tag: str, jar: Path, notes: Path, root: Path):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
        raise ValueError('Invalid GitHub repository')
    properties = read_properties(root / 'gradle.properties')
    version = version_from_tag(tag, properties['mod_version'], properties['minecraft_version'])
    if not version.startswith('20.1.'):
        raise ValueError('This workflow publishes only the Forge 1.20.1 version line (20.1.x)')
    jar = validate_jar(jar, mod_id=properties['mod_id'], version=version, check_java17=True)
    if jar.name != f'{properties["mod_id"]}-{version}.jar':
        raise ValueError('Expected the exact Gradle release JAR filename')
    body = extract_changelog((root / 'CHANGELOG.md').read_text(encoding='utf-8'), version)
    if notes.read_text(encoding='utf-8') != body:
        raise ValueError('Release notes must be exactly the matching version from CHANGELOG.md')
    title = f'{properties.get("mod_name", "Create Package")} v{version} (Forge 1.20.1)'
    prerelease = 'true' if '-' in version else 'false'
    existing = release(repo, tag)
    if existing is None:
        # A draft prevents observers from seeing a published release without its JAR.
        gh('release', 'create', tag, str(jar), '--repo', repo, '--verify-tag', '--draft',
           '--title', title, '--notes-file', str(notes), '--latest=false')
    else:
        assets = [asset for asset in existing['assets'] if asset['name'] == jar.name]
        if len(assets) > 1:
            raise ValueError('Ambiguous release JAR assets')
        if not existing['draft'] and assets:
            # Never replace an already published binary (including after a moved tag).
            with tempfile.TemporaryDirectory() as directory:
                gh('release', 'download', tag, '--repo', repo, '--pattern', jar.name, '--dir', directory)
                remote = Path(directory) / jar.name
                if hashlib.sha512(remote.read_bytes()).digest() != hashlib.sha512(jar.read_bytes()).digest():
                    raise ValueError('Published JAR differs: use a NEW version/tag; for text-only retries run Publish release assets')
        else:
            gh('release', 'upload', tag, str(jar), '--repo', repo, '--clobber')
    attached = release(repo, tag)
    assets = [asset for asset in attached['assets'] if asset['name'] == jar.name and asset['size'] == jar.stat().st_size]
    if len(assets) != 1:
        raise ValueError('Release JAR upload was not confirmed; refusing to publish')
    gh('release', 'edit', tag, '--repo', repo, '--verify-tag', '--draft=false',
       f'--prerelease={prerelease}', '--latest=false', '--title', title, '--notes-file', str(notes))
    print(f'GitHub Release ready: {repo}/releases/tag/{tag} ({jar.name})')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--notes', required=True, type=Path)
    args = parser.parse_args()
    try:
        publish(args.repo, args.tag, args.jar, args.notes, Path(__file__).resolve().parents[1])
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as error:
        parser.exit(1, f'create_github_release: {error}\n')


if __name__ == '__main__':
    main()
