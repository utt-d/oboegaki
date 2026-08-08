import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.foundation)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

val releaseStorePath = System.getenv("OBOEGAKI_RELEASE_KEYSTORE")
val releaseStorePassword = System.getenv("OBOEGAKI_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("OBOEGAKI_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("OBOEGAKI_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val releaseArtifactTaskRequested = gradle.startParameter.taskNames.any { requestedTask ->
    val taskName = requestedTask.substringAfterLast(':')
    val firstPathSegment = requestedTask.removePrefix(":").substringBefore(":")
    val isRootOrAndroidAppRequest = !requestedTask.startsWith(":") || firstPathSegment != "shared"
    isRootOrAndroidAppRequest && (
        taskName.contains("release", ignoreCase = true) ||
            taskName in setOf("assemble", "build", "bundle")
        )
}
if (releaseArtifactTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is unavailable for release/assemble/build/bundle artifacts; " +
            "set the OBOEGAKI_RELEASE_* environment variables.",
    )
}

android {
    namespace = "jp.oboegaki.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "jp.oboegaki.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 9
        versionName = "0.3.0"
    }
    signingConfigs {
        if (hasReleaseSigning) create("release") {
            storeFile = File(requireNotNull(releaseStorePath))
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildFeatures { buildConfig = true }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
