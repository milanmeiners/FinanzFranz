import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.finanzfranz.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.finanzfranz.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Only modern phone CPUs (keeps the bundled OCR model small)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Personal signing key is NOT part of this repository (see keystore.properties).
    val keystorePropsFile = rootProject.file("keystore.properties")
    val hasPersonalKey = keystorePropsFile.exists()
    if (hasPersonalKey) {
        val props = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
        signingConfigs {
            create("personal") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasPersonalKey) signingConfig = signingConfigs.getByName("personal")
        }
        debug {
            if (hasPersonalKey) signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "2g"
                // Optional: pre-downloaded Robolectric SDK jars (offline test runs)
                val jars = System.getenv("ROBOLECTRIC_JARS")
                if (jars != null) {
                    it.systemProperty("robolectric.offline", "true")
                    it.systemProperty("robolectric.dependency.dir", jars)
                }
            }
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

configurations.all {
    resolutionStrategy.force("org.robolectric:nativeruntime-dist-compat:1.0.12")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Offline text recognition for receipts (model bundled in the APK, no internet needed)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
