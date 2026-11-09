import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

// ===== Release 签名 =====
// keystore.properties 与 *.jks 都不进版本库（见 .gitignore）。
// 没配签名时 release 依然能构建，只是产物未签名 —— 手机装不上（这是有意的失败，不是静默降级）。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.example.personalassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.personalassistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 有 keystore 才签名；没有则保持未签名（不静默换 debug key —— 那样会让正式包无法覆盖安装）
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Room 导出 schema，便于版本演进和迁移
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }

    // Phase 5: 让 JVM 单元测试里 android.util.Log 等返回默认值（而不是抛 RuntimeException），
    // 这样 CompositeReminderScheduler 等调用 Log.i 的代码可以被直接单测覆盖。
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = false
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    // ---- Core AndroidX ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // ---- Compose BOM ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ---- Navigation ----
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // ---- Coroutines ----
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ---- Serialization ----
    implementation(libs.kotlinx.serialization.json)

    // ---- Room（用 KSP，比 kapt 快一倍）----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ---- DataStore ----
    implementation(libs.androidx.datastore.preferences)

    // ---- WorkManager ----
    implementation(libs.androidx.work.runtime.ktx)

    // ---- Charts ----
    implementation(libs.mpandroidchart)

    // ---- Material Components（仅用于 Splash / Snackbar 等少数 Compose 未覆盖的场景）----
    implementation(libs.google.material)

    // ---- Lunar Calendar (农历日期转换，驱动月视图角标) ----
    implementation(libs.lunar)

    // ---- Image loading (Coil — Phase 2 附件缩略图) ----
    implementation(libs.coil.compose)

    // ---- Baseline Profile（AndroidX ProfileInstaller 运行时）----
    // 接收并安装系统/Play 下发的 ART baseline profile，让 AOT 编译器提前编译启动热路径。
    implementation(libs.androidx.profileinstaller)

    // ---- Test ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Phase 3：Room MigrationTestHelper（真跑迁移需要设备，无设备时仅保证编译通过）
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Phase 9 A3：DailyAutoBackupWorkerTest 用的 TestListenableWorkerBuilder
    androidTestImplementation(libs.androidx.work.testing)
}
