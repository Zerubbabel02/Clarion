plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Applied only once google-services.json is in place, so a fresh checkout without it still
// configures and builds — real push wiring is opt-in until that file exists.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.clarion.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clarion.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Real phones only — drop the x86/x86_64 emulator-only ABIs MapLibre's native
        // library otherwise bundles for all four architectures, which quadruples APK size.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    // Deliberately NOT the latest BOM: Compose 1.12 (BOM 2026.08.00+) bumped the minimum
    // compileSdk to 37 / AGP to 9.1+, which this project isn't on. Pinned to the last BOM
    // release still compatible with compileSdk 36 / AGP 8.13.0.
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    // The small core icon set (a few hundred KB), NOT material-icons-extended (35MB, which
    // repeatedly failed to download on this project's unstable connection and was dropped
    // entirely) — Edit is one of the ~80 icons the core set actually includes.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("org.maplibre.gl:android-sdk:11.11.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    // NOT ktor-client-android: that engine wraps Android's old bundled com.android.okhttp
    // fork, which has a real, reproducible "Unbalanced enter/exit" crash when a network
    // call's coroutine is cancelled mid-response (e.g. leaving a screen while a Supabase
    // request is in flight, or the receiving-poll loop getting cancelled). ktor-client-okhttp
    // uses the real, actively-maintained OkHttp library and is Ktor's recommended engine for
    // Android for exactly this reason. Hit this crash live on-device before switching.
    implementation("io.ktor:ktor-client-okhttp:3.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Real background delivery: a Flare must ring even when the app is fully closed, which
    // the old foreground-only poll (kept below as a redundant fast-path while the app is
    // open) can't do. KTX artifacts were merged into the main modules and dropped from the
    // BOM as of Firebase BOM 34 — use firebase-messaging directly, not firebase-messaging-ktx.
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-messaging")

    // Sign in with Google, via Credential Manager (the current Android-recommended API,
    // not the deprecated GoogleSignInClient) exchanging a Google ID token for a real Supabase
    // session (auth-kt's signInWith(IDToken)). credentials-play-services-auth is the backport
    // that makes this work below Android 14, where Credential Manager has no native OS
    // provider yet — this app's real target users skew toward older/cheaper devices than even
    // the Tecno CN5 this was tested on, so it isn't optional here.
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
}
