from pathlib import Path
import unittest
import yaml


ROOT = Path(__file__).parents[2]


class ReleaseWorkflowTest(unittest.TestCase):
    def test_staging_receives_signing_and_release_credentials_together(self):
        workflow = yaml.safe_load((ROOT / '.github/workflows/android.yml').read_text())
        step = next(step for step in workflow['jobs']['staging']['steps']
                    if step.get('name') == 'Build signed staging APK')
        self.assertTrue({'ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS',
                         'CHAMADOS_API_BASE_URL', 'ANDROID_SIGNING_CERT_SHA256', 'GH_TOKEN'} <= set(step['env']))


if __name__ == '__main__':
    unittest.main()
