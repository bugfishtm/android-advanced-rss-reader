import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Optional release signing: drop a keystore.properties in the project root
// (see keystore.properties.sample) and release builds get signed automatically.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// --- Auto-incrementing version code --------------------------------------
// The current value lives in version.properties (project root). It is bumped
// by +1 automatically whenever a bundle (.aab) is built, so every uploaded
// bundle gets a fresh, higher version code. Normal debug/assemble builds do
// not change it.
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}
var appVersionCode = (versionProps.getProperty("versionCode") ?: "2").trim().toInt()

val buildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
if (buildingBundle) {
    appVersionCode += 1
    versionProps.setProperty("versionCode", appVersionCode.toString())
    versionPropsFile.outputStream().use {
        versionProps.store(it, "Auto-incremented on each bundle build. Edit versionCode to set a starting point.")
    }
    println("versionCode bumped to $appVersionCode for this bundle")
}

android {
    namespace = "de.bugfish.rssreaderadvanced"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "de.bugfish.rssreaderadvanced"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    // Two product flavours from one codebase:
    //   free -> applicationId de.bugfish.rssreaderadvanced     (FREE_VERSION = true)
    //   pro  -> applicationId de.bugfish.rssreaderadvanced.pro (FREE_VERSION = false)
    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            // no suffix – this is the base application id
            buildConfigField("boolean", "FREE_VERSION", "true")
        }
        create("pro") {
            dimension = "tier"
            applicationIdSuffix = ".pro"
            versionNameSuffix = "-pro"
            buildConfigField("boolean", "FREE_VERSION", "false")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink + obfuscate. Produces a mapping (deobfuscation) file
            // that AGP embeds in the AAB, so Play can symbolicate crashes/ANRs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with the real release key ONLY if keystore.properties exists.
            // Otherwise the release bundle is left UNSIGNED on purpose, so it can
            // be signed manually with jarsigner without a conflicting debug
            // signature (which Google Play rejects as an invalid signature).
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        // Don't let lint block a release bundle.
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
