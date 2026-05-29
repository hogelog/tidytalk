import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.github.triplet.play")
}

val gitShortRev: String = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }.getOrElse("unknown")

fun requireEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Required environment variable $name is missing or empty.")

val prNumber: String? = System.getenv("PR_NUMBER")?.takeIf { it.isNotBlank() }
val releaseVersion: String? = System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }
val releaseVersionSuffix: String? = System.getenv("RELEASE_VERSION_SUFFIX")?.takeIf { it.isNotBlank() }
val baseVersionName = "0.1.0"
val appVersionName: String = when {
    releaseVersion != null && releaseVersionSuffix != null -> "$releaseVersion-$releaseVersionSuffix"
    releaseVersion != null -> releaseVersion
    prNumber != null -> "$baseVersionName-pr-$prNumber-$gitShortRev"
    else -> baseVersionName
}

// versionCode source: main builds pass RELEASE_VERSION (the base version); PR builds fall back to
// the base version so they share the same monotonic space as main; local builds use the sentinel 1.
val versionForCode: String? = when {
    releaseVersion != null -> releaseVersion
    prNumber != null -> baseVersionName
    else -> null
}

// PR builds count commits up to the merge-base with the PR base (BASE_SHA) so the PR's own commits
// never bump versionCode; main builds count HEAD. Outside CI (no BASE_SHA) PR builds fall back to HEAD.
val countTip: String = if (prNumber != null) {
    val baseSha = System.getenv("BASE_SHA")?.takeIf { it.isNotBlank() }
    if (baseSha != null) {
        providers.exec {
            commandLine("git", "merge-base", "HEAD", baseSha)
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim() }.getOrElse("").ifEmpty { "HEAD" }
    } else {
        "HEAD"
    }
} else {
    "HEAD"
}

val commitsSinceTag: Int = if (versionForCode != null) {
    val prevTag = providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0", "--match=v*", "--exclude=v$versionForCode", countTip)
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }.getOrElse("")
    val countCmd = if (prevTag.isNotEmpty()) {
        listOf("git", "rev-list", "--count", "$prevTag..$countTip")
    } else {
        listOf("git", "rev-list", "--count", countTip)
    }
    providers.exec {
        commandLine(countCmd)
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 0 }.getOrElse(0)
} else {
    0
}

// MAJOR*1_000_000 + MINOR*10_000 + PATCH*100 + COMMITS_SINCE_TAG.
val appVersionCode: Int = if (versionForCode != null) {
    val parts = versionForCode.split(".")
    require(parts.size == 3) { "Version for versionCode must be MAJOR.MINOR.PATCH, got '$versionForCode'" }
    val (major, minor, patch) = parts.map {
        it.toIntOrNull() ?: error("Version segment '$it' is not an integer")
    }
    require(commitsSinceTag in 0..99) {
        "commitsSinceTag must be 0..99 to avoid colliding with the PATCH segment; got $commitsSinceTag"
    }
    major * 1_000_000 + minor * 10_000 + patch * 100 + commitsSinceTag
} else {
    1
}

android {
    namespace = "org.hogel.tidytalk"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.hogel.tidytalk"
        minSdk = 34
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // Committed debug keystore so debug APKs from different CI runners share a
    // signing identity and can be installed as updates over each other.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signingConfig is wired up only when CI provides the keystore env vars,
        // so local `assembleDebug` keeps working without release credentials.
        val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = requireEnv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = requireEnv("RELEASE_KEY_ALIAS")
                keyPassword = requireEnv("RELEASE_KEY_PASSWORD")
            }
        } else if (releaseVersion != null) {
            error("RELEASE_VERSION is set but RELEASE_KEYSTORE_PATH is missing; release builds must be signed.")
        }
    }

    buildTypes {
        debug {
            // Lets debug and release variants coexist on the same device.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

play {
    track.set("internal")
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.COMPLETED)

    // google-github-actions/auth writes the WIF credentials file and exposes its
    // path via GOOGLE_APPLICATION_CREDENTIALS. GoogleCredentials.fromStream (used
    // by GPP) accepts both service account keys and external_account (WIF) JSON.
    System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.takeIf { it.isNotBlank() }?.let {
        serviceAccountCredentials.set(file(it))
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
