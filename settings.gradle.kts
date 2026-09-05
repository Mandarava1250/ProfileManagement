pluginManagement {
    repositories {
        // 国内镜像优先，官方仓库作 fallback（大陆网络环境）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内镜像优先，官方仓库作 fallback（大陆网络环境）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        // MPAndroidChart 来自 JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PersonalAssistant"
include(":app")
