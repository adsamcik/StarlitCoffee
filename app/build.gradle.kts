plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.adsamcik.starlitcoffee"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adsamcik.starlitcoffee"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

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
    }

    assetPacks += listOf(":ai_model_pack")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Push coffee bag test images to connected device/emulator
tasks.register("pushTestImages") {
    group = "verification"
    description = "Push test coffee bag images to connected device/emulator"

    doLast {
        val testDataDir = rootProject.file("testdata/coffee-bags")
        require(testDataDir.exists() && testDataDir.isDirectory) {
            "testdata/coffee-bags/ not found. See testdata/README.md for setup instructions."
        }

        val adb = (extensions.getByName("android") as com.android.build.gradle.BaseExtension)
            .adbExecutable.absolutePath

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

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ML Kit Barcode Scanning
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.text.recognition)

    // LiteRT-LM (on-device LLM inference with Gemma 3n)
    implementation(libs.litertlm.android)

    // OpenCV (image preprocessing for OCR)
    implementation(libs.opencv)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Play Asset Delivery
    implementation(libs.play.asset.delivery.ktx)

    // Coroutines
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

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
