pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "yeonfeel-keyboard"
include(":app")
// ML Kit 번역 애드온 앱과, 키보드·애드온이 함께 쓰는 IPC 프로토콜 (#25)
include(":translate-mlkit")
include(":translate-protocol")
