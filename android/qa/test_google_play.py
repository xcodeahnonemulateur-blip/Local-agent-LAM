"""Contract tests for publication; no network, credentials or Play mutations."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch


class Response:
    def __init__(self, value=None, ok=True):
        self.value = value or {}
        self.ok = ok
        self.status_code = 200 if ok else 400
        self.content = json.dumps(self.value).encode()

    def json(self):
        return self.value


class Session:
    def __init__(self, reject_image=False):
        self.calls = []
        self.reject_image = reject_image

    def request(self, method, url, **options):
        self.calls.append((method, url, options))
        if url.endswith('/edits'):
            return Response({'id': 'isolated-edit'})
        if method == 'GET' and url.endswith('/bundles'):
            return Response({'bundles': [{'versionCode': 308}]})
        if method == 'GET' and url.endswith('/tracks'):
            return Response({'tracks': [{'releases': [{'versionCodes': ['302', '309']}]}]})
        if method == 'GET' and url.endswith('/listings'):
            return Response({'listings': [{'language': 'en-US', 'title': 'Old name', 'shortDescription': 'Existing summary', 'fullDescription': 'Existing description', 'video': 'https://www.youtube.com/watch?v=existing'}]})
        if method == 'POST' and '/bundles?' in url:
            return Response({'versionCode': 310})
        if method == 'POST' and '/icon?' in url and self.reject_image:
            return Response({'error': {'message': 'Image validation failed'}}, ok=False)
        return Response()

    def delete(self, url, **options):
        return self.request('DELETE', url, **options)

    def close(self):
        pass


class PublishContract(unittest.TestCase):
    def setUp(self):
        fake_modules = {name: types.ModuleType(name) for name in ('google', 'google.auth', 'google.auth.transport', 'google.auth.transport.requests', 'google.oauth2', 'google.oauth2.service_account')}
        fake_modules['google.auth.transport.requests'].AuthorizedSession = Session
        fake_modules['google.oauth2.service_account'].Credentials = types.SimpleNamespace(from_service_account_info=lambda *args, **kwargs: object())
        fake_modules['google.oauth2'].service_account = fake_modules['google.oauth2.service_account']
        spec = importlib.util.spec_from_file_location('google_play_contract', Path(__file__).resolve().parents[1] / 'scripts/google_play.py')
        self.module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, fake_modules):
            spec.loader.exec_module(self.module)
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        for file in ('store/icon-512.png', 'store/screenshots/01.png', 'store/screenshots/02.png', 'app/build/outputs/bundle/release/app-release.aab'):
            target = root / file
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b'contract fixture')
        self.module.ROOT = root
        self.output = root / 'outputs'

    def execute(self, session, *arguments):
        self.module.AuthorizedSession = lambda credentials: session
        with patch.object(sys, 'argv', ['google_play.py', *arguments]), patch.dict(os.environ, {'PLAY_SERVICE_ACCOUNT': '{}', 'GITHUB_OUTPUT': str(self.output), 'GITHUB_STEP_SUMMARY': str(self.output.parent / 'summary')}):
            self.module.main()

    def test_version_uses_all_tracks_and_bundles(self):
        session = Session()
        self.execute(session, 'version')
        self.assertIn('code=310', self.output.read_text())
        self.assertFalse(any(url.endswith(':commit') for _, url, _ in session.calls))
        self.assertEqual(session.calls[-1][0], 'DELETE')

    def test_publish_preserves_descriptions_and_video(self):
        session = Session()
        self.execute(session, 'publish', '--code', '310', '--version', '0.4.8')
        body = next(options['json'] for method, url, options in session.calls if method == 'PUT' and url.endswith('/listings/en-US'))
        self.assertEqual(body, {'language': 'en-US', 'title': 'Local Agent LLM', 'shortDescription': 'Existing summary', 'fullDescription': 'Existing description', 'video': 'https://www.youtube.com/watch?v=existing'})
        self.assertTrue(session.calls[-1][1].endswith(':commit'))

    def test_version_advances_past_a_discarded_workflow_upload(self):
        session = Session()
        with patch.dict(os.environ, {'GITHUB_RUN_NUMBER': '18'}):
            self.execute(session, 'version')
        self.assertIn('code=317', self.output.read_text())

    def test_rejected_asset_never_commits_partial_listing(self):
        session = Session(reject_image=True)
        with self.assertRaisesRegex(RuntimeError, 'Image validation failed'):
            self.execute(session, 'publish', '--code', '310', '--version', '0.4.8')
        self.assertFalse(any(url.endswith(':commit') for _, url, _ in session.calls))
        self.assertEqual(session.calls[-1][:2], ('DELETE', self.module.BASE + '/edits/isolated-edit'))


if __name__ == '__main__':
    unittest.main()
