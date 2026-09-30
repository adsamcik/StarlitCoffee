"""Integration checks for the actual Gradle auth scripts, using synthetic tokens.

Run with Python 3 and a working Gradle/JDK installation:
    python tools/test_github_packages_auth.py --gradle path/to/gradle

The fixture excludes Android plugins and uses a local HTTP registry. It never
reads, prints, refreshes, or changes a developer's stored GitHub login.
"""

import argparse
import base64
import http.server
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest

REPOSITORY = Path(__file__).resolve().parents[1]
GRADLE = str(REPOSITORY / ("gradlew.bat" if os.name == "nt" else "gradlew"))


class Registry(http.server.BaseHTTPRequestHandler):
    status = 200
    requests = []

    def do_HEAD(self):
        self.requests.append((self.path, self.headers.get("Authorization")))
        self.send_response(self.status)
        self.end_headers()

    def log_message(self, *_args):
        pass


class PackageAuthTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        (REPOSITORY / "build/tmp").mkdir(parents=True, exist_ok=True)
        cls.directory = tempfile.TemporaryDirectory(prefix="package-auth-", dir=REPOSITORY / "build/tmp")
        cls.fixture = Path(cls.directory.name)
        cls.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Registry)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        # Execute production auth code, without loading the Android project.
        settings = (REPOSITORY / "settings.gradle.kts").read_text(encoding="utf-8")
        auth = settings[settings.index("// Explicit build/CI credentials"):settings.index('rootProject.name =')]
        auth += f'''
dependencyResolutionManagement.repositories
    .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
    .filter {{ it.name.endsWith("GitHubPackages") }}
    .forEach {{ repository ->
        check(repository.authentication.single() is org.gradle.authentication.http.BasicAuthentication)
        repository.url = uri("http://127.0.0.1:{cls.server.server_port}/" + repository.name)
    }}
rootProject.name = "package-auth-test"
'''
        (cls.fixture / "settings.gradle.kts").write_text(auth, encoding="utf-8")
        build = (REPOSITORY / "build.gradle.kts").read_text(encoding="utf-8")
        (cls.fixture / "build.gradle.kts").write_text(build[build.index("// Check the exact published versions"):], encoding="utf-8")
        (cls.fixture / "gradle").mkdir()
        catalog = (REPOSITORY / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        (cls.fixture / "gradle/libs.versions.toml").write_text(catalog, encoding="utf-8")
        (cls.fixture / "empty-gh-config").mkdir()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()
        cls.directory.cleanup()

    def setUp(self):
        Registry.status = 200
        Registry.requests = []
        self.tokens = []

    def run_gradle(self, local=None, environment=None, properties=None, task="checkGitHubPackagesAuth", offline=False):
        local = local or {}
        environment = environment or {}
        # CLI blanks suppress credentials from global Gradle properties. The
        # resolver can then use only this test's synthetic env/local inputs.
        properties = {
            "GH_TOKEN": "", "GITHUB_TOKEN": "", "GITHUB_OWNER": "adsamcik",
            "GITHUB_USERNAME": "", "GITHUB_ACTOR": "", **(properties or {}),
        }
        self.tokens = [v for source in (local, environment, properties) for k, v in source.items()
                       if k in ("GH_TOKEN", "GITHUB_TOKEN") and v]
        (self.fixture / "local.properties").write_text(
            "\n".join(f"{key}={value}" for key, value in local.items()), encoding="utf-8"
        )
        env = os.environ.copy()
        for key in ("GH_TOKEN", "GITHUB_TOKEN", "GITHUB_USERNAME", "GITHUB_OWNER", "GITHUB_ACTOR"):
            env.pop(key, None)
            env.pop("ORG_GRADLE_PROJECT_" + key, None)
        env["GH_CONFIG_DIR"] = str(self.fixture / "empty-gh-config")
        env.update(environment)
        command = [GRADLE, "-p", str(self.fixture), task, "--console=plain", "--no-configuration-cache"]
        command.extend(f"-P{key}={value}" for key, value in properties.items())
        if offline:
            command.append("--offline")
        result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=150)
        output = result.stdout + result.stderr
        for token in self.tokens:
            self.assertNotIn(token, output, "A synthetic credential appeared in Gradle output")
        return result.returncode, output

    def assert_registry_token(self, token, username="adsamcik"):
        expected = "Basic " + base64.b64encode(f"{username}:{token}".encode()).decode()
        self.assertEqual(len(Registry.requests), 2)
        self.assertTrue(all(auth == expected for _, auth in Registry.requests))
        self.assertTrue(all(path.endswith(".pom") for path, _ in Registry.requests))

    def test_gradle_property_overrides_environment_and_stale_local_token(self):
        code, output = self.run_gradle(
            local={"GH_TOKEN": "local-sentinel"},
            environment={"GH_TOKEN": "environment-sentinel"},
            properties={"GITHUB_TOKEN": "property-sentinel"},
        )
        self.assertEqual(code, 0, output)
        self.assertIn("GITHUB_TOKEN (Gradle property)", output)
        self.assert_registry_token("property-sentinel")

    def test_gh_environment_token_overrides_github_token_and_local_token(self):
        code, output = self.run_gradle(
            local={"GITHUB_TOKEN": "local-sentinel"},
            environment={"GH_TOKEN": "gh-sentinel", "GITHUB_TOKEN": "github-sentinel"},
        )
        self.assertEqual(code, 0, output)
        self.assertIn("GH_TOKEN (environment)", output)
        self.assert_registry_token("gh-sentinel")

    def test_local_token_and_separate_authentication_username(self):
        code, output = self.run_gradle(local={"GITHUB_TOKEN": "local-sentinel", "GITHUB_USERNAME": "reader"})
        self.assertEqual(code, 0, output)
        self.assertIn("GITHUB_TOKEN (local.properties)", output)
        self.assert_registry_token("local-sentinel", "reader")

    def test_ci_actor_is_default_authentication_username(self):
        code, output = self.run_gradle(environment={"GITHUB_TOKEN": "ci-sentinel", "GITHUB_ACTOR": "ci-reader"})
        self.assertEqual(code, 0, output)
        self.assert_registry_token("ci-sentinel", "ci-reader")

    def test_registry_rejections_have_actionable_errors_and_never_claim_success(self):
        for status, recovery in ((401, "read:packages"), (403, "SSO"), (404, "GITHUB_OWNER")):
            with self.subTest(status=status):
                Registry.status = status
                Registry.requests = []
                code, output = self.run_gradle(environment={"GITHUB_TOKEN": "rejected-sentinel"})
                self.assertNotEqual(code, 0)
                self.assertIn(f"HTTP {status}", output)
                self.assertIn(recovery, output)
                self.assertNotIn("package access verified", output)

    def test_missing_credentials_are_reported_without_registry_requests(self):
        # gh can find a legacy keyring token even with an empty GH_CONFIG_DIR.
        # Pin an account that has no stored credential instead.
        code, output = self.run_gradle(properties={"GITHUB_USERNAME": "starlit-auth-test-no-stored-account"})
        self.assertNotEqual(code, 0)
        self.assertIn("credential source: unavailable", output)
        self.assertIn("credential store", output)
        self.assertEqual(Registry.requests, [])

    def test_offline_configuration_works_without_credentials(self):
        code, output = self.run_gradle(
            properties={"GITHUB_USERNAME": "starlit-auth-test-no-stored-account"}, task="help", offline=True
        )
        self.assertEqual(code, 0, output)
        self.assertNotIn("credentials are unavailable", output)
        self.assertEqual(Registry.requests, [])


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--gradle", default=GRADLE)
    args, remaining = parser.parse_known_args()
    GRADLE = args.gradle
    unittest.main(argv=[__file__, *remaining])
