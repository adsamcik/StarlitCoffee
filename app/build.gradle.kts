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
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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

// Push coffee bag test images to connected device/emulator
tasks.register("pushTestImages") {
    group = "verification"
    description = "Push test coffee bag images to connected device/emulator"

    // Capture providers at configuration time so doLast doesn't reach into
    // configuration-only extensions during the execution phase.
    val adbProvider = androidComponents.sdkComponents.adb
    val testDataDir = rootProject.file("testdata/coffee-bags")

    doLast {
        require(testDataDir.exists() && testDataDir.isDirectory) {
            "testdata/coffee-bags/ not found. See testdata/README.md for setup instructions."
        }

        val adb = adbProvider.get().asFile.absolutePath

        val deviceDir = "/data/local/tmp/coffee-bags"

        fun adb(vararg args: String) {
            val cmd = listOf(adb) + args.toList()
            val proc = ProcessBuilder(cmd).inheritIO().start()
            require(proc.waitFor() == 0) { "adb command failed: ${cmd.joinToString(" ")}" }
        }

        adb("shell", "mkdir", "-p", deviceDir)

        val images = testDataDir.listFiles()
            ?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
            ?: emptyList()

        images.forEach { file ->
            adb("push", file.absolutePath, "$deviceDir/${file.name}")
        }

        println("Pushed ${images.size} images to $deviceDir")
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
    implementation(libs.mlkit.text.recognition)

    // OpenCV (image preprocessing for OCR)
    implementation(libs.opencv)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Coroutines
    implementation(libs.coroutines.android)

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
