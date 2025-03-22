import java.io.FileInputStream
import java.util.Properties
import org.apache.tools.ant.taskdefs.condition.Os

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

    compileSdk = 35
    buildToolsVersion = "35.0.0"

    namespace = "app.grapheneos.pdfviewer"

    defaultConfig {
        applicationId = "app.grapheneos.pdfviewer"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("com.google.android.material:material:1.12.0")
}

fun getCommand(command: String, winExt: String = "cmd"): String {
    return if (Os.isFamily(Os.FAMILY_WINDOWS)) "$command.$winExt" else command
}

val npmSetup = tasks.register("npmSetup", Exec::class) {
    workingDir = rootDir
    commandLine(getCommand("npm"), "ci", "--ignore-scripts")
}

val processStatic = tasks.register("processStatic", Exec::class) {
    workingDir = rootDir
    dependsOn(npmSetup)
    commandLine(getCommand("node", "exe"), "process_static.js")
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
