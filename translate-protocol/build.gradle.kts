plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// 키보드(:app)와 ML Kit 애드온(:translate-mlkit)이 함께 컴파일하는 IPC 프로토콜.
// 상수·코덱을 한곳에 두어 두 앱이 서로 다른 키·코드를 쓰는 일이 없게 한다.
android {
    namespace = "dev.badalab.yeonfeel.translate.protocol"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
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
    testImplementation("junit:junit:4.13.2")
}
