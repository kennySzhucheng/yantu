import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.monesy.kaoyan"
    compileSdk = 35

    // 发布签名：本地 keystore.properties（已 gitignore）；文件不存在时 release 不签名（不影响 debug）
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply { propsFile.inputStream().use { load(it) } }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.monesy.studyreminder"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.6.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // 发布瘦身：中文 OCR 模型是通用资产无法拆，native 库按 ABI 拆分。
    // arm64-v8a 覆盖主流手机，armeabi-v7a 兼容老设备，x86_64 供模拟器；不再生成全量 universal 包
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.jxl)
    // 课表图片识别：ML Kit 中文 OCR（离线模型打包进 APK，不联网、不依赖 GMS）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
