import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val sifatKeystore = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// Repo rilis untuk cek update (asset .apk di GitHub Releases).
val repoRilis = (project.findProperty("CARWASH_REPO") as String?) ?: "djvpri/zx-carwash"

// Alamat API. Bisa ditimpa tanpa menyentuh repo:
//   ~/.gradle/gradle.properties  ->  CARWASH_API=http://host:3000
val apiBase = (project.findProperty("CARWASH_API") as String?)
    ?: "http://bg7dxicf3ixlex0nzzussq6u.103.93.129.94.sslip.io"

android {
    namespace = "com.zomet.zxcarwash"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zomet.zxcarwash"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.0"
        buildConfigField("String", "REPO_RILIS", "\"$repoRilis\"")
        buildConfigField("String", "API_BASE", "\"$apiBase\"")
    }

    signingConfigs {
        create("release") {
            if (sifatKeystore.getProperty("storeFile") != null) {
                storeFile = rootProject.file(sifatKeystore.getProperty("storeFile"))
                storePassword = sifatKeystore.getProperty("storePassword")
                keyAlias = sifatKeystore.getProperty("keyAlias")
                keyPassword = sifatKeystore.getProperty("keyPassword")
            }
        }
    }

    // Lint vital me-crash di JDK tanpa AWT ("no awt in system library path")
    // dan bukan error kode — dimatikan supaya rilis tidak gagal karena itu.
    lint { checkReleaseBuilds = false; abortOnError = false }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (sifatKeystore.getProperty("storeFile") != null)
                signingConfigs.getByName("release") else null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation("junit:junit:4.13.2")
}
