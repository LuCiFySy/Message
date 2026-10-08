plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.saurabh.messages"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.saurabh.messages"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.1"
    }

    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("MESSAGE_KEYSTORE").get())
            storePassword = providers.gradleProperty("MESSAGE_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("MESSAGE_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("MESSAGE_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":mmslib"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
}
