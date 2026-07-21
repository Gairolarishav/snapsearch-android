import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.objectbox)
}

// Local-only release signing, read from local.properties (gitignored) — see 1.7 install-size checkpoint.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.snapsearch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.snapsearch"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64-v8a only — no armeabi-v7a, no x86/x86_64
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/*.kotlin_module",
            "**/kotlin/**",
        )
    }

    signingConfigs {
        create("release") {
            val storeFileName = localProperties.getProperty("release.storeFile")
            if (storeFileName != null) {
                storeFile = rootProject.file(storeFileName)
                storePassword = localProperties.getProperty("release.storePassword")
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = localProperties.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // Android App Bundle splits for minimal install size
    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        language { enableSplit = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    // ObjectBox — on-device database with a built-in HNSW vector index (ANN search)
    // for image/text embeddings + metadata. Debug builds get the Admin data browser
    // for inspecting stored entities during development; release builds skip it to
    // keep installed size down.
    debugImplementation(libs.objectbox.android.admin)
    releaseImplementation(libs.objectbox.android)

    // Background indexing
    implementation(libs.androidx.work.runtime.ktx)

    // Image loading for search results
    implementation(libs.coil.compose)

    // ONNX Runtime Mobile — MobileCLIP inference (arm64 + XNNPACK)
    implementation(libs.onnxruntime.android)

    // ML Kit Text Recognition v2 (bundled Latin model)
    implementation(libs.mlkit.text.recognition)

    // Coroutine bridge for ML Kit's Task API
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
