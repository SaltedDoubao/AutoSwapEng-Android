plugins {
    id("com.android.application") version "8.6.1"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "com.autoswapeng.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autoswapeng.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    
    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 使用复制任务重命名产物，避免依赖 AGP 变体 API

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ML Kit Text Recognition Chinese/Latin
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Accessibility + window manager
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.window:window:1.3.0")

    // Kotlin utils
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

// 将 release APK 复制到项目根目录的 release/AutoSwapEng.apk
val copyReleaseApk by tasks.register<Copy>("copyReleaseApk") {
    from(layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk"))
    into(layout.projectDirectory.dir("../app/release"))
    rename { "AutoSwapEng.apk" }
}

afterEvaluate {
    tasks.findByName("assembleRelease")?.finalizedBy(copyReleaseApk)
}


