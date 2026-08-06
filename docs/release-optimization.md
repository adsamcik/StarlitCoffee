# Release optimization

Starlit Coffee release builds use Android Gradle Plugin 9.3's unified R8
optimizer. Debug builds remain unminified for readable debugging and fast local
iteration; release builds enable code shrinking, obfuscation, optimization, and
optimized resource shrinking together.

## Keep-rule policy

The app uses generated, statically reachable integrations wherever possible:
Room uses KSP-generated database code and kotlinx.serialization uses generated
serializers. AndroidX, Compose, WorkManager, CameraX, OpenCV, and Mindlayer are
expected to provide consumer rules for their own reflective or JNI boundaries.
Manifest entry points are covered by Android's default platform rules.

Do not add package-wide `-keep` rules for entities, databases, serializers,
composables, or an entire dependency. They prevent R8 from optimizing code that
is safe to rename or remove. If new app code is reached only through reflection
or JNI, add the narrowest member-level rule in a `.keep` file under
`app/src/main/keepRules/`, explain the runtime boundary in a comment, and verify
the optimized build on a device.

## Verification

Run:

```powershell
.\gradlew.bat :app:lintRelease :app:verifyReleaseOptimization
```

`verifyReleaseOptimization` builds the unsigned release APK and fails unless R8
produces non-empty configuration, mapping, removed-code, and resource-shrinking
reports. It also verifies that the mapping contains renamed classes, proving
that obfuscation is active rather than merely configured.

The release workflow preserves `mapping.txt` as a time-limited workflow artifact for
90 days so production stack traces can be retraced. Do not publish the mapping
as a GitHub Release asset. Preserve the mapping longer in the release owner's
secure artifact store before distributing a signed build.

A successful build proves that optimization completed; it does not prove every
dynamic path works. Before release, smoke-test startup, Room migration and
persistence, background WorkManager jobs, camera capture, OpenCV preprocessing,
serialization restore, navigation, notifications/Picture-in-Picture, and the
Mindlayer connection using the optimized artifact.
