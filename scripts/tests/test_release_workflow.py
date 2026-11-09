from pathlib import Path
import unittest
import yaml


ROOT = Path(__file__).parents[2]


class ReleaseWorkflowTest(unittest.TestCase):
    def test_sdk_setup_uses_current_available_packages(self):
        workflow = yaml.safe_load((ROOT / '.github/workflows/android.yml').read_text())
        for job in workflow['jobs'].values():
            setup = next(step for step in job['steps'] if step.get('uses', '').startswith('android-actions/setup-android@'))
            packages = setup.get('with', {}).get('packages', 'tools platform-tools').split()
            self.assertNotIn('tools', packages)
            self.assertIn('platform-tools', packages)
            self.assertEqual('16111833', str(setup.get('with', {}).get('cmdline-tools-version', '')))
            install = next(step for step in job['steps'] if step.get('name') == 'Install Android SDK 37')
            self.assertIn('platforms;android-37.0', install['run'])

    def test_staging_receives_signing_and_release_credentials_together(self):
        workflow = yaml.safe_load((ROOT / '.github/workflows/android.yml').read_text())
        step = next(step for step in workflow['jobs']['staging']['steps']
                    if step.get('name') == 'Build signed staging APK')
        self.assertTrue({'ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS',
                         'CHAMADOS_API_BASE_URL', 'ANDROID_SIGNING_CERT_SHA256', 'GH_TOKEN'} <= set(step['env']))


if __name__ == '__main__':
    unittest.main()
