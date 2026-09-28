plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 연필키보드용 ML Kit 번역 애드온 (#25). ML Kit 네이티브 엔진(ABI당 약 16MB)과 언어 모델 다운로드용
// INTERNET 권한을 키보드 대신 이 앱이 가진다. 키보드는 서명 권한으로 보호된 서비스에 바인드해 번역한다.
android {
    namespace = "dev.badalab.yeonfeel.translate.mlkit"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.badalab.yeonfeel.translate.mlkit"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // 키보드(:app)와 같은 키로 서명해야 서명 권한으로 서로 연결된다. 릴리스 키스토어는 저장소 밖
    // (~/.gradle/gradle.properties)에서 읽고, 없으면 두 앱 모두 같은 디버그 키로 대체된다.
    val releaseStoreFile = providers.gradleProperty("YEONFEEL_RELEASE_STORE_FILE").orNull

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("YEONFEEL_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("YEONFEEL_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("YEONFEEL_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":translate-protocol"))
    // ML Kit 온디바이스 번역. 언어 모델(언어당 약 30MB)은 처음 쓸 때 내려받는다.
    implementation("com.google.mlkit:translate:17.0.3")
}
