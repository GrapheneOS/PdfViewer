import org.apache.tools.ant.taskdefs.condition.Os
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
    id("org.jetbrains.kotlin.android")
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
            }

            create("play") {
                storeFile = rootProject.file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["uploadKeyAlias"] as String
                keyPassword = keystoreProperties["uploadKeyPassword"] as String
            }
        }
    }

    compileSdk = 34
    buildToolsVersion = "34.0.0"

    namespace = "app.grapheneos.pdfviewer"

    defaultConfig {
        applicationId = "app.grapheneos.pdfviewer"
        minSdk = 26
        targetSdk = 34
        versionCode = 18
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
            buildConfig = true
            compose = true
        }

        composeOptions {
            kotlinCompilerExtensionVersion = "1.5.6"
        }
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_17)
        targetCompatibility(JavaVersion.VERSION_17)
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("com.google.android.material:material:1.11.0")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
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
