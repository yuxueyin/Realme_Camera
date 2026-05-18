import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.min.lite"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.min.lite"
        minSdk = 36
        targetSdk = 36
        versionCode = 22
        versionName = "2.3_enhance"

        // åªä¿ç•™å¸¸ç”¨è¯­è¨€èµ„æºï¼Œé…åˆ R8/resource shrink å‡å°‘ APK ä½“ç§¯ã€‚
        resourceConfigurations += listOf("zh-rCN", "en")
    }

    sourceSets {
        getByName("main") {
            resources.setSrcDirs(listOf("src/main/resources"))
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // å¹³æ—¶ä½ å®‰è£…çš„æ˜¯ debug åŒ…ï¼Œæ‰€ä»¥è¿™é‡Œä¹Ÿæ‰“å¼€ R8ï¼Œé¿å…åªç˜¦ release ä¸ç˜¦ debugã€‚
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
}

