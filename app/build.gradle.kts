import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val baseVersion = rootProject.file("VERSION").takeIf { it.exists() }?.readText()?.trim() ?: "1.0.0"
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("versionName") as String?) ?: "$baseVersion-dev"

/**
 * Podpis wydań: w CI klucz przychodzi z sekretów (SIGNING_*), lokalnie – z katalogu
 * ~/.openclipflow-signing (release.p12 + password.txt). Ten sam klucz w obu miejscach,
 * więc APK z GitHuba i zbudowany lokalnie aktualizują się nawzajem.
 */
data class SigningKey(val file: File, val storePassword: String, val alias: String, val keyPassword: String)

fun releaseKey(): SigningKey? {
    System.getenv("SIGNING_KEYSTORE_PATH")?.let { path ->
        val f = file(path)
        val store = System.getenv("SIGNING_STORE_PASSWORD").orEmpty()
        if (f.exists() && store.isNotEmpty()) {
            return SigningKey(
                f,
                store,
                System.getenv("SIGNING_KEY_ALIAS").takeUnless { it.isNullOrBlank() } ?: "openclipflow",
                System.getenv("SIGNING_KEY_PASSWORD").takeUnless { it.isNullOrBlank() } ?: store,
            )
        }
    }
    val dir = File(System.getProperty("user.home"), ".openclipflow-signing")
    val local = File(dir, "release.p12")
    val pass = File(dir, "password.txt")
    if (local.exists() && pass.exists()) {
        val pw = pass.readText().trim()
        val props = Properties().apply { File(dir, "release.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
        return SigningKey(local, pw, props.getProperty("alias") ?: "openclipflow", pw)
    }
    return null
}

val signingKey = releaseKey()

android {
    namespace = "com.openclipflow.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.openclipflow.app"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "UPDATE_REPO", "\"BlackSingularities/OpenClipFlow\"")
        buildConfigField("boolean", "RELEASE_SIGNED", (signingKey != null).toString())
    }

    signingConfigs {
        if (signingKey != null) {
            create("release") {
                storeFile = signingKey.file
                storeType = "pkcs12"
                storePassword = signingKey.storePassword
                keyAlias = signingKey.alias
                keyPassword = signingKey.keyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Bez klucza (np. build z forka) APK i tak musi dać się zainstalować – podpis debug.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    val media3 = "1.10.1"

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Silnik montażu: odtwarzanie kompozycji (podgląd) i eksport z efektami GPU.
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-common:$media3")

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
