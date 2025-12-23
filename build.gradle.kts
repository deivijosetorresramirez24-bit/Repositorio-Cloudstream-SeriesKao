buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:cce1b8d84d")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

// Eliminamos el bloque allprojects para limpiar los errores de configuración
// Los repositorios ya se manejan correctamente en settings.gradle.kts

task("clean", Delete::class) {
    delete(layout.buildDirectory)
}
