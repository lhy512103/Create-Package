from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import create_github_release as module


class CreateGitHubReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / 'gradle.properties').write_text('mod_id=createpackage\nmod_version=20.1.2\nminecraft_version=1.20.1\n', encoding='utf-8')
        (self.root / 'CHANGELOG.md').write_text('## [20.1.2] - 2026-11-08\n\n### 中文\n1. 修复：测试。\n\n### English\n1. Fixed: Test.\n', encoding='utf-8')
        self.notes = self.root / 'notes.md'
        self.notes.write_text(module.extract_changelog((self.root / 'CHANGELOG.md').read_text(encoding='utf-8'), '20.1.2'), encoding='utf-8')
        self.jar = self.root / 'createpackage-20.1.2.jar'
        with zipfile.ZipFile(self.jar, 'w') as archive:
            archive.writestr('META-INF/mods.toml', 'modLoader="javafml"\n[[mods]]\nmodId="createpackage"\nversion="20.1.2"\n')
            archive.writestr('com/lhy/createpackage/CreatePackage.class', bytes.fromhex('cafebabe0000003d'))

    def publish(self):
        module.publish('owner/repo', 'v20.1.2', self.jar, self.notes, self.root)

    def asset(self):
        return {'name': self.jar.name, 'size': self.jar.stat().st_size}

    @patch.object(module, 'gh')
    @patch.object(module, 'release')
    def test_draft_and_jar_before_publication(self, release, gh):
        release.side_effect = [None, {'assets': [self.asset()], 'draft': True}]
        self.publish()
        commands = [call.args for call in gh.call_args_list]
        self.assertIn('--draft', commands[0])
        self.assertIn(str(self.jar), commands[0])
        self.assertIn('--verify-tag', commands[0])
        self.assertIn('--notes-file', commands[0])
        self.assertIn('--draft=false', commands[-1])
        self.assertIn('--latest=false', commands[-1])

    @patch.object(module, 'gh')
    @patch.object(module, 'release')
    def test_missing_attachment_never_publishes(self, release, gh):
        release.side_effect = [None, {'assets': [], 'draft': True}]
        with self.assertRaisesRegex(ValueError, 'upload was not confirmed'):
            self.publish()
        self.assertFalse(any('--draft=false' in call.args for call in gh.call_args_list))

    @patch.object(module, 'gh')
    def test_missing_jar_does_not_contact_github(self, gh):
        self.jar.unlink()
        with self.assertRaises(ValueError):
            self.publish()
        gh.assert_not_called()

    @patch.object(module, 'gh')
    def test_notes_cannot_be_the_entire_changelog(self, gh):
        self.notes.write_text('wrong release text', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'matching version'):
            self.publish()
        gh.assert_not_called()

    @patch.object(module, 'gh')
    @patch.object(module, 'release')
    def test_published_binary_cannot_be_replaced(self, release, gh):
        release.return_value = {'assets': [self.asset()], 'draft': False}
        def download(*arguments, **kwargs):
            if arguments[:2] == ('release', 'download'):
                destination = Path(arguments[arguments.index('--dir') + 1]) / self.jar.name
                destination.write_bytes(b'previous published binary')
        gh.side_effect = download
        with self.assertRaisesRegex(ValueError, 'NEW version/tag'):
            self.publish()
        self.assertFalse(any(call.args[:2] == ('release', 'upload') for call in gh.call_args_list))
        self.assertFalse(any('--draft=false' in call.args for call in gh.call_args_list))

    def test_api_errors_are_not_misread_as_missing_release(self):
        from subprocess import CompletedProcess
        with patch.object(module.subprocess, 'run', return_value=CompletedProcess([], 1, '', 'HTTP 403 Forbidden')):
            with self.assertRaises(ValueError):
                module.release('owner/repo', 'v20.1.2')
        with patch.object(module.subprocess, 'run', return_value=CompletedProcess([], 1, '', 'HTTP 404 Not Found')):
            self.assertIsNone(module.release('owner/repo', 'v20.1.2'))


if __name__ == '__main__':
    unittest.main()
