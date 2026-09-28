plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.badalab.yeonfeel"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.badalab.yeonfeel"
        minSdk = 23
        targetSdk = 36
        versionCode = 22
        versionName = "1.0.10"
    }

    // 릴리스 키스토어는 저장소 밖(~/.gradle/gradle.properties)에서 읽는다.
    // 키가 없는 환경(포크·CI)에서는 디버그 서명으로 대체돼 빌드는 항상 가능하다.
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
    implementation("androidx.core:core-ktx:1.16.0")
    // ExploreByTouchHelper — 커스텀 그린 키 그리드에 TalkBack 접근성 노드를 제공한다.
    implementation("androidx.customview:customview:1.1.0")
    // Activity Embedding — 폴드·태블릿에서 설정을 2단(목록/상세)으로 나란히 표시한다.
    implementation("androidx.window:window:1.3.0")
    // 키보드 번역 엔진 (설정에서 선택). 시스템 번역은 프레임워크 API라 의존성이 없다.
    // ML Kit 번역: 언어 모델을 내려받기 위해 INTERNET 권한을 앱에 병합한다.
    implementation("com.google.mlkit:translate:17.0.3")
    // Gemini Nano (AICore) Prompt API — minSdk 26, 런타임에서 API 26 이상일 때만 로드한다.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    testImplementation("junit:junit:4.13.2")
}
