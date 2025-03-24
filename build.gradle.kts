plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}

allprojects {
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint", "-Xlint:-classfile", "-Xlint:-serial"))
    }
}
