# Tracebox integration

Starlit Coffee uses Tracebox `0.1.0-alpha.3` as its single production logging
and failure-diagnostics backend. The app opts into the managed runtime, native
capture, and reusable Compose diagnostics UI.

## Runtime contract

- `StarlitCoffeeApp.attachBaseContext()` installs Tracebox before ordinary app
  startup with the standard persisted policy. Native capture is enabled when
  the current process has a supported 64-bit ABI.
- The dedicated `:tracebox_handler` process is detected before Starlit Coffee
  starts Mindlayer, database recovery, WorkManager reconciliation, or other app
  services. Tracebox owns that process.
- The app policy records `INFO` and above and enables every applicable source:
  JVM crashes, handled exceptions, ANRs, Android process exits, and native
  crashes. The inert Rust-only control is omitted because Starlit Coffee has no
  Rust participant.
- The native component ships for `arm64-v8a` and `x86_64`. On a 32-bit process,
  managed diagnostics stay available and the inapplicable native capture
  control is omitted.
- A user's disabled or restricted policy is persisted and is not overwritten
  when the app restarts or the diagnostics screen opens.
- Tracebox storage lives in Android's `noBackupFilesDir`, so diagnostic records,
  policy state, and staged packages are excluded from cloud backup and
  device-to-device transfer.

## Logging and privacy

Production call sites use `Tracebox.log` directly. Templates contain only
static application text. Runtime values are passed as parameters so Tracebox
classifies them before durable storage or optional Logcat mirroring:

- numbers, booleans, characters, and enums are public by default;
- strings and unknown domain objects are PII and redacted by default; and
- throwable overloads preserve bounded stack identity without persisting the
  exception message.

The app intentionally retains its small scan-session and LLM-pass history. That
history supports a separate, user-invoked product troubleshooting flow and is
not a second crash or general logging backend.

## User experience

Settings → Support & privacy → Diagnostics opens the embedded Tracebox screen.
The default path is one reviewed Android share action. Before any package can
leave the device, Tracebox finalizes the exact deterministic bytes and shows its
disclosure for approval. Users can also save an approved package locally,
delete all Tracebox data, or expand advanced runtime controls. Starlit Coffee
does not configure an uploader, endpoint, retry worker, or automatic transport.

## Build contract

The app declares `tracebox`, `tracebox-native`, and `tracebox-ui-compose` from
the Tracebox GitHub Packages repository. Core-library desugaring with
`desugar_jdk_libs_nio` is required for Tracebox's Java NIO use on supported
Android versions. `mavenLocal()` remains available for coordinated local
Tracebox development.

## Verification

The integration is guarded by unit tests for handler-process isolation and the
default capture policy, plus an architecture test that rejects any return of
`android.util.Log` production calls. Normal debug compilation, unit tests,
Detekt, lint, manifest inspection, and APK assembly exercise the dependency and
manifest merge, including the private handler service and Tracebox export
components.
