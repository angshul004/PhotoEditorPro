plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.photoeditorpro"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.photoeditorpro"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        buildConfigField("String", "GEMINI_API_KEY", "\"${project.properties["GEMINI_API_KEY"]}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = project.properties["STORE_PASS"] as? String ?: ""
            keyAlias = "key0"
            keyPassword = project.properties["KEY_PASS"] as? String ?: ""
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // UCrop with required exclusions to prevent conflicts
    implementation("com.github.yalantis:ucrop:2.2.8") {
        exclude(group = "androidx.core", module = "core")
        exclude(group = "com.android.support", module = "appcompat-v7")
    }

    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.exifinterface:exifinterface:1.3.6") // Required by UCrop

    // File provider support
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("com.google.ai.client.generativeai:generativeai:0.3.0")
}