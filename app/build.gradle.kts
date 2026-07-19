import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    jacoco
}

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.adsamcik.starlitcoffee"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.adsamcik.starlitcoffee"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Launcher label, overridden per build type. Release resolves the
        // localized app name; debug uses a literal "(Debug)" marker so the
        // suffixed debug app is distinguishable in the launcher.
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    // Sign debug builds with the shared Mindlayer "knownCertsOwner" keystore
    // (`G:/Github/Mindlayer/app/keystores/knowncerts-owner.jks`) when present so
    // local debug installs satisfy Mindlayer's `signature|knownSigner` BIND_ML_SERVICE
    // permission gate. Falls back to the default Android Studio debug keystore when
    // the Mindlayer keystore is unavailable (CI, contributors without that repo).
    val mindlayerKeystore = rootProject.file("../Mindlayer/app/keystores/knowncerts-owner.jks")
    signingConfigs {
        if (mindlayerKeystore.exists()) {
            create("mindlayerKnownCerts") {
                storeFile = mindlayerKeystore
                storePassword = "knowncertstest"
                keyAlias = "knowncerts-owner"
                keyPassword = "knowncertstest"
            }
        }
    }

    buildTypes {
        debug {
            // Distinct package + launcher label so a debug build installs
            // alongside a release build instead of replacing it.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Starlit Coffee (Debug)"
            if (mindlayerKeystore.exists()) {
                signingConfig = signingConfigs.getByName("mindlayerKnownCerts")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Expose the exported Room schemas to instrumented tests so
        // MigrationTestHelper can create historical databases and validate
        // migrations against the real schema history (not hand-written copies).
        getByName("androidTest").assets.directories.add("$projectDir/schemas")

        // Pure-JVM corpus/scoring harness shared by both unit tests
        // (src/test) and instrumented tests (src/androidTest). Keep it
        // Android-free so the JVM unit tests can exercise the scoring math
        // deterministically while the on-device tests reuse the same logic.
        getByName("test").kotlin.directories.add("src/sharedTest/kotlin")
        getByName("androidTest").kotlin.directories.add("src/sharedTest/kotlin")
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-Xannotation-default-target=param-property",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
    doFirst {
        extensions.getByType(JacocoTaskExtension::class.java)
            .destinationFile
            ?.parentFile
            ?.mkdirs()
        binaryResultsDirectory.get().asFile.mkdirs()
    }
}

// Push the committed synthetic coffee-bag corpus (sidecar metadata + images) to a
// connected device/emulator for the instrumented OCR/LLM benchmark + Q0 gate.
tasks.register("pushTestImages") {
    group = "verification"
    description = "Push the synthetic coffee-bag corpus to a connected device/emulator"

    // Capture providers at configuration time so doLast doesn't reach into
    // configuration-only extensions during the execution phase.
    val adbProvider = androidComponents.sdkComponents.adb
    val corpusDir = rootProject.file("testdata/synthetic-coffee-bag-corpus")

    doLast {
        val metadataFiles = corpusDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".metadata.json") }
            ?.sortedBy { it.name }
            .orEmpty()
        val images = corpusDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in listOf("webp", "png", "jpg", "jpeg") }
            ?.sortedBy { it.name }
            .orEmpty()
        require(metadataFiles.isNotEmpty() && images.isNotEmpty()) {
            "Synthetic corpus not found at testdata/synthetic-coffee-bag-corpus/ " +
                "(expected *.metadata.json + sibling image files). See testdata/README.md."
        }

        val adb = adbProvider.get().asFile.absolutePath
        val deviceDir = "/data/local/tmp/coffee-bags"

        fun adb(vararg args: String) {
            val cmd = listOf(adb) + args.toList()
            val proc = ProcessBuilder(cmd).inheritIO().start()
            require(proc.waitFor() == 0) { "adb command failed: ${cmd.joinToString(" ")}" }
        }

        // Clean first so a stale flat-JPEG layout can never mask a broken push.
        adb("shell", "rm", "-rf", deviceDir)
        adb("shell", "mkdir", "-p", deviceDir)
        metadataFiles.forEach { file ->
            adb("push", file.absolutePath, "$deviceDir/${file.name}")
        }
        images.forEach { file ->
            adb("push", file.absolutePath, "$deviceDir/${file.name}")
        }

        println("Pushed ${metadataFiles.size} sidecars + ${images.size} images to $deviceDir")
    }
}

// Run the instrumented bag-scan LLM benchmark the RELIABLE way.
//
// `connectedDebugAndroidTest` UNINSTALLS the app after every run, which wipes the
// OCR fixtures the capture step writes to the app's external files dir — so
// running capture and benchmark as two separate `connectedAndroidTest`
// invocations silently loses the fixtures in between (run-as then reports
// "unknown package"). This task installs both APKs ONCE and drives the
// instrumentation directly via `adb am instrument`, so the app — and its
// fixtures — survive across the capture and benchmark passes.
//
//   .\gradlew.bat scanBenchmark                 # text-only: capture OCR fixtures, then benchmark
//   .\gradlew.bat scanBenchmark -PskipCapture   # reuse existing fixtures (prompt-only iteration)
//   .\gradlew.bat scanBenchmark -PfullPipeline  # text->vision->combine, ONE bag per process (curated subset)
//   .\gradlew.bat scanBenchmark -PfullPipeline -PbagIds=scb-001-en-q0,scb-003-cs-q2
//   .\gradlew.bat scanBenchmark -PfullPipeline -PallBags   # every automation-ready bag (slow: ~3-4 min/bag)
//   .\gradlew.bat scanBenchmark -PfullPipeline -PknownVocab # also ground passes with the collection vocabulary
//   .\gradlew.bat scanBenchmark -PfullPipeline -Pstitch     # composite front+back into one image for the vision pass
//   .\gradlew.bat scanBenchmark -PfullPipeline -PselfConsistency=3  # vote the text pass over N samples (text only)
//
// The per-field quality report(s) are pulled to build/reports/scan-benchmark/.
// Full-pipeline mode runs one bag per `am instrument` invocation because the
// on-device vision pass has a hard one-multimodal-inference-per-process budget.
tasks.register("scanBenchmark") {
    group = "verification"
    description = "Install once + run OCR capture and the LLM benchmark via am instrument (no uninstall between runs)"
    dependsOn("pushTestImages", "installDebug", "installDebugAndroidTest")

    val adbProvider = androidComponents.sdkComponents.adb
    val skipCapture = project.hasProperty("skipCapture")
    val fullPipeline = project.hasProperty("fullPipeline")
    val allBags = project.hasProperty("allBags")
    val knownVocab = project.hasProperty("knownVocab")
    val stitch = project.hasProperty("stitch")
    val selfConsistency = (project.findProperty("selfConsistency") as String?)?.takeIf { it.toIntOrNull() != null }
    val explicitBagIds = (project.findProperty("bagIds") as String?)
        ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
    val corpusDir = rootProject.file("testdata/synthetic-coffee-bag-corpus")
    // Curated spread across languages (en/cs/it/de/fr) and capture tiers (Q0-Q3)
    // for a tractable (~20-30 min) full-pipeline run; override with -PbagIds / -PallBags.
    val curatedSubset = listOf(
        "scb-001-en-q0", "scb-003-cs-q2", "scb-004-it-q3",
        "scb-007-de-q0", "scb-013-fr-q1", "scb-021-en-q3",
    )
    val reportOut = layout.buildDirectory.dir("reports/scan-benchmark")

    doLast {
        val adb = adbProvider.get().asFile.absolutePath
        val appId = "com.adsamcik.starlitcoffee.debug"
        val instrumentation = "$appId.test/androidx.test.runner.AndroidJUnitRunner"
        val benchmarkPkg = "com.adsamcik.starlitcoffee.benchmark"
        val deviceFixturesDir = "/sdcard/Android/data/$appId/files/coffee-bags-fixtures"

        fun adb(vararg args: String, allowFailure: Boolean = false): Int {
            val cmd = listOf(adb) + args.toList()
            val code = ProcessBuilder(cmd).inheritIO().start().waitFor()
            require(allowFailure || code == 0) { "adb command failed: ${cmd.joinToString(" ")}" }
            return code
        }

        fun instrument(testClass: String, vararg extraArgs: String) {
            // am instrument exits 0 even when individual tests fail (the
            // benchmark only asserts run validity), so a non-zero here means the
            // instrumentation itself could not start, e.g. the app is not installed.
            adb(
                "shell", "am", "instrument", "-w",
                *extraArgs, "-e", "class", "$benchmarkPkg.$testClass", instrumentation,
            )
        }

        // Let the on-device Mindlayer AI service run unattended (debug builds
        // expose an auto-accept receiver). Harmless no-op if already enabled.
        // Broadcast to BOTH the pre-rename and post-rename service package
        // names (mirrors the AndroidManifest <queries> dual-package fix):
        // the SDK's actual bound component depends on which Mindlayer build
        // is installed, and targeting only one name silently no-ops against
        // the other, leaving fresh consent ungranted with no visible error.
        listOf("com.adsamcik.mindlayer.service.debug", "com.adsamcik.mindlayer.debug").forEach { pkg ->
            adb(
                "shell", "am", "broadcast", "-n",
                "$pkg/com.adsamcik.mindlayer.service.security.DebugAutoAcceptReceiver",
                "-a", "com.adsamcik.mindlayer.debug.SET_AUTO_ACCEPT", "--ez", "enabled", "true",
                allowFailure = true,
            )
        }

        if (skipCapture) {
            println("scanBenchmark: -PskipCapture set — reusing existing OCR fixtures")
        } else {
            println("scanBenchmark: capturing OCR fixtures (OcrFixtureCaptureTest)…")
            instrument("OcrFixtureCaptureTest")
        }

        val outDir = reportOut.get().asFile.apply { mkdirs() }
        fun pull(baseName: String) = listOf("txt", "json").forEach { ext ->
            adb("pull", "$deviceFixturesDir/$baseName.$ext", "${outDir.absolutePath}/$baseName.$ext", allowFailure = true)
        }

        if (fullPipeline) {
            val bagIds = when {
                explicitBagIds != null -> explicitBagIds
                allBags -> corpusDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".metadata.json") }
                    ?.map { it.name.substringBefore('_') }
                    ?.sorted().orEmpty()
                else -> curatedSubset
            }
            require(bagIds.isNotEmpty()) { "No bag ids resolved for the full-pipeline run." }

            // Fresh accumulator: each per-bag process appends one JSONL line.
            adb("shell", "rm", "-f", "$deviceFixturesDir/full-pipeline-records.jsonl", allowFailure = true)
            println("scanBenchmark: full pipeline over ${bagIds.size} bag(s), one process each (vision budget is per-process)…")
            bagIds.forEachIndexed { index, bagId ->
                println("  [${index + 1}/${bagIds.size}] $bagId")
                val extra = buildList {
                    add("-e"); add("bagId"); add(bagId)
                    if (knownVocab) { add("-e"); add("knownVocab"); add("true") }
                    if (stitch) { add("-e"); add("stitch"); add("true") }
                    if (selfConsistency != null) { add("-e"); add("selfConsistency"); add(selfConsistency) }
                }
                instrument("FullPipelineBenchmarkTest", *extra.toTypedArray())
            }
            println("scanBenchmark: aggregating full-pipeline records…")
            instrument("FullPipelineAggregateTest")

            pull("full-pipeline-text-only")
            pull("full-pipeline-combined")
            adb(
                "pull", "$deviceFixturesDir/full-pipeline-delta.txt",
                "${outDir.absolutePath}/full-pipeline-delta.txt", allowFailure = true,
            )
            println("scanBenchmark: full-pipeline reports pulled to $outDir")
        } else {
            println("scanBenchmark: running text-only LLM benchmark (LlmCorpusBenchmarkTest)…")
            instrument("LlmCorpusBenchmarkTest")
            pull("llm-fixture-quality-report")
            println("scanBenchmark: report pulled to $outDir")
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // EXIF metadata
    implementation(libs.exifinterface)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ML Kit Barcode Scanning
    implementation(libs.mlkit.barcode)
    // ML Kit text recognition is NOT used in production OCR — Mindlayer
    // (PaddleOCR / PP-OCRv5) does on-device OCR via MindlayerOcrService, and
    // the LLM does field extraction. The bundled Latin model (~15 MB in the
    // APK) is only needed by OcrPipelineInstrumentedTest as a reference OCR
    // primitive, so scope it to instrumented tests to keep it out of the
    // shipped AAB.
    androidTestImplementation(libs.mlkit.text.recognition)

    // OpenCV (image preprocessing for OCR)
    implementation(libs.opencv)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Coroutines
    implementation(libs.coroutines.android)

    // WorkManager (durable background extraction)
    implementation(libs.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Mindlayer on-device LLM SDK
    implementation(libs.mindlayer.sdk)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
