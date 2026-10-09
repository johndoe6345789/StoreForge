plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release builds get their version from the git tag (see .github/workflows/release.yml):
//   ./gradlew assembleRelease -PversionName=1.2.3
// versionCode is derived as MAJOR * 10000 + MINOR * 100 + PATCH so every tag upgrades cleanly.
val storeVersionName = (findProperty("versionName") as String?)?.removePrefix("v") ?: "0.1.0"
val storeVersionCode = storeVersionName.substringBefore('-').split('.').let { parts ->
    val (major, minor, patch) = List(3) { parts.getOrNull(it)?.toIntOrNull() ?: 0 }
    major * 10000 + minor * 100 + patch
}

// The catalog the app reads. Point a fork at its own repo with -PcatalogUrl=...
val catalogUrl = (findProperty("catalogUrl") as String?)
    ?: "https://raw.githubusercontent.com/johndoe6345789/StoreForge/main/apps.json"

android {
    namespace = "io.github.johndoe6345789.storeforge"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.johndoe6345789.storeforge"
        minSdk = 26
        targetSdk = 36
        versionCode = storeVersionCode
        versionName = storeVersionName
        buildConfigField("String", "CATALOG_URL", "\"$catalogUrl\"")
    }

    signingConfigs {
        // Supplied by CI from repository secrets. Every release must be signed with the same
        // key, otherwise Android refuses to install it as an update.
        val keystore = System.getenv("STOREFORGE_KEYSTORE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("STOREFORGE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("STOREFORGE_KEY_ALIAS")
                keyPassword = System.getenv("STOREFORGE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

// Unit tests validate the repo's catalog, so they need to know where it is.
tasks.withType<Test>().configureEach {
    systemProperty("storeforge.catalog", rootProject.file("apps.json").absolutePath)
}
