plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.malttuk"
    useLibrary("android.car", false)
    // Reuse the tested transport contract; do not copy the desktop smoke runner into Android.
    sourceSets.named("main") {
        kotlin.directories += "../../../../services/api/examples/kotlin-client/src/main/kotlin"
    }
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.malttuk"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":vehicle-state"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Bundle the reviewed React design locally. No remote page or personal .env is loaded by WebView.
val vehicleWeb = tasks.register<Exec>("buildVehicleWebUi") {
    val webDir = rootProject.projectDir.resolve("../../web")
    workingDir(webDir)
    commandLine("node", "node_modules/vite/bin/vite.js", "build", "--config", "vite.native.config.js")
    inputs.files(fileTree(webDir.resolve("src")), webDir.resolve("native-vehicle.html"), webDir.resolve("vite.native.config.js"), webDir.resolve("package-lock.json"))
    outputs.dir(layout.buildDirectory.dir("generated/vehicleWebAssets"))
}
android.sourceSets.named("main") { assets.srcDir(layout.buildDirectory.dir("generated/vehicleWebAssets").get().asFile) }
tasks.named("preBuild") { dependsOn(vehicleWeb) }
