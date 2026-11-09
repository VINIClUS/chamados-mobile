import os
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).parents[2]


class ReleaseApiUrlTest(unittest.TestCase):
    def validate(self, value):
        return subprocess.run([sys.executable, str(ROOT / 'tools/ci/check-api-url.py')],
                              env=dict(os.environ, CHAMADOS_API_BASE_URL=value), capture_output=True, text=True)

    def test_valid_https_api_endpoint(self):
        self.assertEqual(0, self.validate('https://example.invalid/api/').returncode)

    def test_missing_or_unusable_endpoint_is_rejected_before_build(self):
        for value in ('', 'http://example.invalid/api/', 'https:///api/',
                      'https://example.invalid/api', 'https://user:password@example.invalid/api/'):
            with self.subTest(value=value):
                result = self.validate(value)
                self.assertNotEqual(0, result.returncode)
                self.assertIn('Invalid API URL', result.stderr)


if __name__ == '__main__':
    unittest.main()
