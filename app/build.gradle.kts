plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The release workflow passes the tag (`-PversionTag=vX.Y.Z`); it is the single source of truth.
// Untagged builds are 0.0.0-dev / versionCode 1.
val versionTag: String? = providers.gradleProperty("versionTag").orNull
val semver: Triple<Int, Int, Int>? =
    versionTag?.let { tag ->
        val match =
            Regex("""v?(\d+)\.(\d+)\.(\d+)""").matchEntire(tag)
                ?: error("versionTag must be vX.Y.Z, was '$tag'")
        val (major, minor, patch) = match.destructured
        Triple(major.toInt(), minor.toInt(), patch.toInt())
    }
semver?.let { (_, minor, patch) ->
    require(minor < 1_000 && patch < 1_000) { "minor and patch must be < 1000, was '$versionTag'" }
}

// Set by the release workflow from repo secrets. Without them (local, CI on PRs) release is signed
// with the committed debug key so R8 breakage still surfaces.
val releaseKeystore: String? = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull

android {
    namespace = "io.github.jgoedde.pielauncher"
    // API 37, minor level 2: the Compose BOM needs compileSdk 37.1 or later.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "io.github.jgoedde.pielauncher"
        minSdk = 37
        targetSdk = 37
        versionName = semver?.let { (major, minor, patch) -> "$major.$minor.$patch" } ?: "0.0.0-dev"
        versionCode = semver?.let { (major, minor, patch) -> major * 1_000_000 + minor * 1_000 + patch } ?: 1
        manifestPlaceholders["appLabel"] = "Pie Launcher"
        // The git tag as is; the Pie Config $schema URL points at spec/ at this tag.
        buildConfigField("String", "VERSION_TAG", versionTag?.let { "\"$it\"" } ?: "null")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Pie Launcher (debug)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom.alpha))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
}

// The licenses page shows NOTICE as is (PRD 10.2), so the APK bundles the repository's copy.
abstract class CopyNoticeTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val notice: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        notice.get().asFile.copyTo(outputDir.get().file("NOTICE").asFile, overwrite = true)
    }
}

val copyNotice =
    tasks.register<CopyNoticeTask>("copyNotice") {
        notice.set(rootProject.layout.projectDirectory.file("NOTICE"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyNotice, CopyNoticeTask::outputDir)
    }
}
