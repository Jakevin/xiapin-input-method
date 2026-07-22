plugins {
    id("com.android.application")
}

android {
    namespace = "com.xiapin.ime"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xiapin.ime"
        minSdk = 21
        targetSdk = 34
        versionCode = 19
        versionName = "0.2.3"

        ndk {
            // 與 librime 靜態庫編譯的 ABI 對齊
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DRIME_BUILD_DIR=" + System.getenv("RIME_BUILD_DIR")
                        .let { it ?: "/Volumes/1Tb/projects/android-rime-build" }
                )
            }
        }
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

    buildFeatures {
        // 不使用 viewBinding / AndroidX（全用 framework 類，避免 appcompat 與 AGP 7.4.2 R8 衝突）
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            // 蝦拼 Rime 資源放在 assets/rime/
            assets.srcDirs("src/main/assets")
        }
    }
}

// 本 App 只使用 Android framework 類（InputMethodService / KeyboardView 等），
// 不依賴 AndroidX / AppCompat / Material，避免與 AGP 7.4.2 內建 R8 不相容的問題。
dependencies {
}
