plugins {
    id("com.android.application")
}

android {
    namespace = "com.zjy.bydmini"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zjy.bydmini"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.9.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
