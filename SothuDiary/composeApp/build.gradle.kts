import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Desktop (Windows / macOS / Linux) target. Web (wasmJs) is planned for later.
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            // Thumbnails / photos (Compose Multiplatform image loading)
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.2")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
            // OpenStreetMap map view (no API key needed)
            implementation("org.osmdroid:osmdroid-android:6.1.18")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
            // On-device OCR for shared posters / screenshots (Latin script)
            implementation("com.google.mlkit:text-recognition:16.0.1")
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
            implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
        }
    }
}

// 2.4.0: the app's display name lives in one place, AppName.kt (both languages). Its two values are read here and
// written as Android string resources (launcher label + share target), so a rename is a one-line change there.
val appNameFile = file("src/commonMain/kotlin/com/hark/sothudiary/AppName.kt")
fun appNameValue(key: String): String =
    Regex("""const val $key = "([^"]*)"""").find(appNameFile.readText())?.groupValues?.get(1) ?: "Sothu Diary"
val appNameResDir = layout.buildDirectory.dir("generated/appname/res").get().asFile
fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")
listOf("values" to appNameValue("EN"), "values-ta" to appNameValue("TA")).forEach { (dir, name) ->
    val f = File(appNameResDir, "$dir/app_name.xml")
    f.parentFile.mkdirs()
    val share = if (dir == "values") "Add to $name" else "சேர் – $name"
    f.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n    <string name=\"app_name\">${xmlEscape(name)}</string>\n    <string name=\"share_label\">${xmlEscape(share)}</string>\n</resources>\n")
}

android {
    namespace = "com.hark.sothudiary"
    compileSdk = 34

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res", appNameResDir)

    defaultConfig {
        applicationId = "com.hark.sothudiary"
        minSdk = 24
        targetSdk = 34
        versionCode = 12
        versionName = "2.6.0"
        // Phones are ARM; dropping x86 keeps ML Kit's native OCR libraries out of the APK for emulators only.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        // Fixed key committed to the repo so every build (CI or local) can update the installed app in place.
        create("sothu") {
            storeFile = file("sothu-release.jks")
            storePassword = "sothudiary"
            keyAlias = "sothu"
            keyPassword = "sothudiary"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("sothu")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("sothu")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        // kotlinx-datetime uses java.time; desugaring keeps it working on API 24-25.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    "coreLibraryDesugaring"("com.android.tools:desugar_jdk_libs:2.1.2")
}

compose.desktop {
    application {
        mainClass = "com.hark.sothudiary.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SothuDiary"
            packageVersion = "2.6.0"
        }
    }
}

// 2.6.0: the live endpoint probe (desktopTest LiveEndpointsProbe, CI only) prints "LIVE PROBE | …" lines – show them
// in the CI log.
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
