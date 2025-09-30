import java.io.FileInputStream
import java.util.Properties

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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

android {
    if (useKeystoreProperties) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                enableV4Signing = true
            }

            create("play") {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["uploadKeyAlias"] as String
                keyPassword = keystoreProperties["uploadKeyPassword"] as String
            }
        }
    }

    compileSdk = 36
    buildToolsVersion = "36.0.0"

    namespace = "app.future.pdfviewer"

    defaultConfig {
        applicationId = "app.grapheneos.pdfviewer"
        minSdk = 26
        targetSdk = 35
        versionCode = 32
        versionName = versionCode.toString()
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "PDF Viewer d")
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
            buildConfig = true
        }
    }

    androidResources {
        localeFilters += listOf("en")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.google.android.material:material:1.13.0")
}


val npmPath = "/opt/homebrew/bin/npm"
val nodePath = "/opt/homebrew/bin/node"
val npxPath = "/opt/homebrew/bin/npx"

val npmSetup = tasks.register("npmSetup", Exec::class) {
    workingDir = rootDir
    commandLine(npmPath, "ci", "--ignore-scripts")
}

val processStatic = tasks.register("processStatic", Exec::class) {
    workingDir = rootDir
    dependsOn(npmSetup)
    commandLine(npxPath, "tsx", "process_static.ts")
}

val cleanStatic = tasks.register("cleanStatic", Delete::class) {
    delete("src/main/assets/viewer", "src/debug/assets/viewer")
}

tasks.preBuild {
    dependsOn(processStatic)
}

tasks.clean {
    dependsOn(cleanStatic)
}
