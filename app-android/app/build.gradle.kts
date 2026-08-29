plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.myapplicationbetat"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.myapplicationbetat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // --- TUS DEPENDENCIAS ACTUALES ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.9.0")

    // --- FIREBASE ---
    // Importante: Agrega esta línea para controlar las versiones
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))

    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Esta es la que soluciona tu error actual (Mantenla)
    implementation("com.google.android.gms:play-services-tasks:18.0.2")

    // --- PARA LAS GRÁFICAS (Requerimiento RF-09) ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // --- TESTING ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}