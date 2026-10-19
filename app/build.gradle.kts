plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.example.romear"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.romear"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SMS_APP_HASH", "\"${providers.gradleProperty("hello.smsAppHash").getOrElse("")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("hello.debugUrl").getOrElse("https://prod-hello-api.onrender.com/")}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("hello.productionUrl").getOrElse("https://unconfigured.invalid/")}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("hello.stagingUrl").getOrElse("https://prod-hello-api.onrender.com/")}\"")
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        warningsAsErrors = true
        disable += setOf(
            "ApplySharedPref",
            "UnusedAttribute",
            "InflateParams",
            "NetworkSecurityConfig",
            "VectorRaster",
            "ObsoleteSdkInt",
            "Overdraw",
            "UnusedResources",
            "UseKtx",
            "LabelFor",
            "SetTextI18n",
            "HardcodedText",
        )
    }
}

dependencies {
    implementation(libs.coil)
    implementation(libs.coil.network)
    implementation(libs.sms.retriever)
    implementation(libs.exif)
    implementation(libs.datastore)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.timber)
    debugImplementation(libs.leakcanary)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
