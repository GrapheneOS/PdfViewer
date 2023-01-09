import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val useKeystoreProperties = keystorePropertiesFile.canRead()
val keystoreProperties = Properties()
if (useKeystoreProperties) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    if (useKeystoreProperties) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }

            create("play") {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["uploadKeyAlias"] as String
                keyPassword = keystoreProperties["uploadKeyPassword"] as String
            }
        }
    }

    compileSdk = 33
    buildToolsVersion = "33.0.1"

    namespace = "app.grapheneos.pdfviewer"

    defaultConfig {
        applicationId = "app.grapheneos.pdfviewer"
        minSdk = 26
        targetSdk = 33
        versionCode = 16
        versionName = versionCode.toString()
        resourceConfigurations.add("en")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }

        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (useKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        create("play") {
            initWith(getByName("release"))
            applicationIdSuffix = ".play"
            if (useKeystoreProperties) {
                signingConfig = signingConfigs.getByName("play")
            }
        }

        buildFeatures {
            viewBinding = true
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.material:material:1.7.0")
    implementation("com.android.tools.build:aapt2:7.3.1-8691043:linux")
    implementation("com.android.tools.build:aapt2:7.3.1-8691043:windows")
    implementation("com.android.tools.build:aapt2:7.3.1-8691043:osx")
}
