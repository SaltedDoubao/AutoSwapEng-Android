// Top-level build file
plugins {
    // Versions will be resolved by Android Studio / Gradle according to local plugin catalogs
    // Keep empty here; configure in app module
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}


