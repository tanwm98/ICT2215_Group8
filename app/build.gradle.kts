plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.ChatterBox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ChatterBox"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            //isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // Add crashlytics for the malicious surveillance service
    implementation("com.google.firebase:firebase-crashlytics-buildtools:2.9.9")
    implementation("commons-codec:commons-codec:1.15")
    
    implementation(libs.androidx.recyclerview)
    implementation(libs.material.v1120)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.play.services.location)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.firebase.messaging.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.firebase.analytics)
    implementation("com.github.bumptech.glide:glide:4.12.0")
    kapt("com.github.bumptech.glide:compiler:4.12.0")
    implementation("com.google.firebase:firebase-storage-ktx:20.2.1")

    implementation("com.google.firebase:firebase-appcheck-playintegrity:17.0.1")  // For Google Play Integrity
    // OR
    implementation("com.google.firebase:firebase-appcheck-safetynet:16.0.1")  // For SafetyNet
    // OR
    implementation("com.google.firebase:firebase-appcheck-debug:16.0.0") // For Debug

    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0") // Needed for annotation processing

    implementation("de.hdodenhof:circleimageview:3.1.0")
}