# Outstanding work integration

Requested: “Please contribute all outstanding work to main even changes not made by you”.

Audited all six registered worktrees, every local branch and the refreshed `origin` references against `main` at `7bcffc21`. All worktrees were clean. Brewing, artwork, scan integration and cleanup contributions were already ancestors of `main`.

The aggregate merge retains the original outstanding branch histories while preserving current application code. No generated artwork, private settings, ignored build outputs or validation evidence are imported or removed.

| Contribution | Resolution |
|---|---|
| `dev/v10` (`d111a9f3`) | Its unused-dependency premise is stale. Retain explicit Espresso for current diagnostics tests and lifecycle dependencies for current lifecycle callers. |
| `copilot/agp-9.3-stable` (`7a594cd1`) | Superseded by current AGP 9.4.1. |
| `copilot/dependabot-configuration` (`c41dca04`) | Equivalent configuration already present. |
| `copilot/validate-dependabot-pr5` (`dff6e323`) | Superseded by current Kotlin 2.4.21 and KSP 2.3.12. |
| `origin/dependabot/github_actions/actions/setup-java-6.0.0` (`f25047eb`) | Current `v6` major reference already includes this contribution. |
| `origin/dependabot/github_actions/actions/setup-python-7` (`a953a13a`) | Version 7 already present. |
| `origin/dependabot/github_actions/gradle/actions-6` (`e8a71dec`) | Version 6 and current cache configuration already present. |
| `origin/fix/mindlayer-perm-name-typo` (`571f8503`) | Equivalent manifest correction already present. |
| `origin/dependabot/gradle/gradle-wrapper-9.8.1` (`e5f4f468`) | Apply original wrapper JAR, URL and SHA-256 pin for 9.8.1. |
| `origin/dependabot/gradle/org.bitbucket.b_c-jose4j-0.9.7` (`7a7c0778`) | Apply 0.9.7 build-classpath constraint. |
| `origin/dependabot/gradle/org.bouncycastle-bcpkix-jdk18on-1.86` (`0781fc4e`), `org.bouncycastle-bcprov-jdk18on-1.86` (`70a8ecc5`), `org.bouncycastle-bcutil-jdk18on-1.86` (`7e4f353e`) | These overlapping patches agree: align all three modules at 1.86 in both the build classpath and isolated Android lint configuration. |
| `origin/dependabot/pip/tools/pip-bbf36862e1` (`4c86c308`) | Apply Pillow 13.0.0 in the development-only vectorizer requirements. |

## Verification

The checksum-pinned Gradle 9.8.1 wrapper starts successfully with the existing JDK 21. Pillow 13.0.0 and the exact repository tool requirements install in an isolated environment under the ignored build directory.

All 17 existing monochrome-vectorizer acceptance tests passed with Pillow 13.0.0 (329.672 seconds). The initial restricted-sandbox attempt could not write its temporary files; the unchanged suite passed outside that restriction. Production artwork was not edited.

The complete JVM suite ran 1,819 cases: 1,813 passed, four skipped and two failed. Both failures read resource XML that is byte-identical to the pre-integration `7bcffc21` baseline:

- `ChemexResourceCoverageTest`: expects the removed `label_filter_chemex_bonded` key in `chemex_strings.xml`.
- `AndroidValuesParityVerifierTest`: recent brewing labels are absent from several locale files, including `label_live_target` in Czech.

These baseline failures remain open. No checks are disabled and no unrelated localization or test changes are included. The complete JVM suite is not claimed as passing.

Detekt, `assembleDebug` and `assembleDebugAndroidTest` passed under Gradle 9.8.1. The actual build classpath resolves all three Bouncy Castle modules to 1.86 and jose4j to 0.9.7; the isolated `androidLintTool` graph also resolves Bouncy Castle to 1.86 without unresolved nodes.

Debug lint executed all app and test analyses and reported 42 errors: 23 missing translations, 13 unused resources, two Compose modifier-parameter ordering findings and four newer-version advisories. Every reported location is in unchanged application source/resources or the unchanged version catalog; no finding concerns the integrated dependency declarations or wrapper. These are findings on existing content, not a clean lint result. The overall Gradle invocation therefore exited unsuccessfully on the two unit failures and lint findings, despite successful APK builds and Detekt. No Android runtime or UI changes are introduced, so no emulator access or new screenshots are required.

Independent review found no blocking integration findings and recommended commit and local main integration. It confirmed preservation of all 14 contribution heads and current application code, and accepted the explicitly documented baseline validation limits. This is not a clean release-validation result.
