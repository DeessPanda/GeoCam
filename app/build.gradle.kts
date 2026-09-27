import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing is OPTIONAL so that anyone cloning this repo can still build it.
// The signing key is deliberately kept out of version control. If keystore.properties
// is absent (e.g. on a fresh clone or on CI), the release build is simply left unsigned.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

// Single source of truth. Bump these two together: the code MUST always increase
// so existing installs accept the update, the name is what users read.
val geoCamVersionName = "1.0"
val geoCamVersionCode = 4

android {
    namespace = "dev.geocam.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.geocam.app"
        minSdk = 29
        targetSdk = 35
        // versionCode MUST increase for existing installs to accept the update,
        // even though the user-facing name stays 1.0.
        versionCode = geoCamVersionCode
        versionName = geoCamVersionName
    }

    // Two shareable variants of the same app:
    //  - google : fused location + Google map tiles (Play / general distribution)
    //  - floss  : platform LocationManager + OpenStreetMap tiles, no proprietary
    //             code at all, so it qualifies for the F-Droid main repository
    flavorDimensions += "maps"
    productFlavors {
        create("google") {
            dimension = "maps"
            resValue("string", "app_name", "GeoCam")
            resValue("bool", "supports_satellite", "true")
        }
        create("floss") {
            dimension = "maps"
            applicationIdSuffix = ".floss"
            resValue("string", "app_name", "GeoCam FLOSS")
            resValue("bool", "supports_satellite", "false")
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
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
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity)
    implementation("com.github.bumptech.glide:glide:4.16.0")
}

// Play fused location is proprietary, so it is compiled into the google flavor only.
// The floss flavor must not pull this in or F-Droid cannot build it.
dependencies {
    "googleImplementation"("com.google.android.gms:play-services-location:21.3.0")
}

// Builds a signed release APK and drops it in the project root, named after the
// version, so there is never any doubt which file to upload.
//
//   ./gradlew packageReleaseApk    -> GeoCam v1.0.apk          (google flavor)
//   ./gradlew packageFlossApk      -> GeoCam v1.0 FLOSS.apk    (floss flavor)
fun registerPackageTask(
    taskName: String,
    assembleTask: String,
    outputSubdir: String,
    fileName: String,
    description: String
) {
    tasks.register(taskName) {
        group = "distribution"
        this.description = description
        dependsOn(assembleTask)
        val srcDir = layout.buildDirectory.dir("outputs/apk/$outputSubdir")
        val targetFile = rootProject.layout.projectDirectory.file(fileName).asFile
        doLast {
            val dir = srcDir.get().asFile
            val built = dir.listFiles { f -> f.name.endsWith("-release.apk") }?.firstOrNull()
                ?: throw GradleException("No release APK found in $dir")
            built.copyTo(targetFile, overwrite = true)
            logger.lifecycle("Signed release APK -> $targetFile")
        }
    }
}

registerPackageTask(
    "packageReleaseApk", "assembleGoogleRelease", "google/release",
    "GeoCam v$geoCamVersionName.apk",
    "Signed Google-flavor APK -> project root as GeoCam v$geoCamVersionName.apk"
)
registerPackageTask(
    "packageFlossApk", "assembleFlossRelease", "floss/release",
    "GeoCam v$geoCamVersionName FLOSS.apk",
    "Signed FLOSS-flavor APK -> project root as GeoCam v$geoCamVersionName FLOSS.apk"
)
