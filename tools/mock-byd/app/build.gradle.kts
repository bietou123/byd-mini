plugins {
    id("com.android.application")
}

android {
    namespace = "com.byd.aeri.caranywhere"
    compileSdk = 35

    defaultConfig {
        // 必须与官方比亚迪 App 包名一致：Car 的无障碍服务按包名识别窗口
        applicationId = "com.byd.aeri.caranywhere"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "mock-1.0"
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
