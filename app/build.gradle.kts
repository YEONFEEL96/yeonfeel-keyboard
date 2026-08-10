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
        versionCode = 11
        versionName = "0.9.8"
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    // ExploreByTouchHelper — 커스텀 그린 키 그리드에 TalkBack 접근성 노드를 제공한다.
    implementation("androidx.customview:customview:1.1.0")
    testImplementation("junit:junit:4.13.2")
}
