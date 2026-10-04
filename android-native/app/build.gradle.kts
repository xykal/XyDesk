val versionFile = rootProject.file("../VERSION").readText().trim()
val appVersionName = versionFile.substringBefore("+")
val appVersionCode = versionFile.substringAfter("+").toInt()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "id.xyverse.xydesk"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.xyverse.xydesk"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "API_URL", "\"https://signal.xydesk.my.id\"")
        buildConfigField("String", "SIGNALING_URL", "\"wss://signal.xydesk.my.id/ws\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${System.getenv("GOOGLE_WEB_CLIENT_ID").orEmpty()}\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures { viewBinding = true; buildConfig = true; compose = true }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("ANDROID_KEYSTORE_FILE")
            if (!ks.isNullOrEmpty()) {
                storeFile = file(ks)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASS")
                keyAlias = "xydesk"
                keyPassword = System.getenv("ANDROID_KEYSTORE_PASS")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!System.getenv("ANDROID_KEYSTORE_FILE").isNullOrEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    val compose = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(project(":xyadapt"))
    implementation(compose)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.webrtc-sdk:android:125.6422.07")
}

abstract class CopyLegalTask : DefaultTask() {
    @get:InputFiles abstract val sources: ConfigurableFileCollection
    @get:OutputDirectory abstract val outDir: DirectoryProperty
    @TaskAction fun run() {
        val names = mapOf("LEGAL.md" to "legal.md", "THIRD-PARTY-LICENSES.md" to "attribution.md", "CHANGELOG.md" to "changelog.md", "SECURITY.md" to "security.md")
        sources.files.forEach { f -> f.copyTo(outDir.get().asFile.resolve(names.getValue(f.name)), overwrite = true) }
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<CopyLegalTask>("copyLegal${variant.name.replaceFirstChar(Char::uppercase)}") {
            sources.from(rootProject.file("../docs/LEGAL.md"), rootProject.file("../docs/THIRD-PARTY-LICENSES.md"), rootProject.file("../CHANGELOG.md"), rootProject.file("../docs/SECURITY.md"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, CopyLegalTask::outDir)
    }
}
