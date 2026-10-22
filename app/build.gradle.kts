import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 正式 release 签名：凭据放根目录 keystore.properties（已 gitignore，不入库）；
// 文件缺失时 release 回退 debug 签名，保证任何环境都能出包
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// 版本号只在下面一处定义，产物文件名（tangshi-v<版本号>.apk）与清单里的 versionName 都取这里
val appVersionCode = 1
val appVersionName = "1.0"

// 产物文件名：release → tangshi-v1.0.apk，debug → tangshi-v1.0-debug.apk（不再叫 app-release.apk / app-debug.apk）
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val suffix = if (variant.buildType == "release") "" else "-${variant.buildType}"
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName
                ?.set("tangshi-v$appVersionName$suffix.apk")
        }
    }
}

android {
    namespace = "com.rakor.tangshi"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.rakor.tangshi"
        minSdk = 23
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
    lint {
        // 两处都是 lint.jar 与 AGP 8.7.3 的版本不兼容，不是代码问题：
        // - NullSafeMutableLiveData：lifecycle 2.9.4 的 lint.jar（BOM 2026.02）
        // - RememberInComposition：Compose 检测器崩在 Kotlin 分析 API 上
        //   （IncompatibleClassChangeError: Found class KaSimpleVariableAccessCall,
        //   but interface was expected），会让 :app:lintDebug 整体失败、报告停更
        disable += setOf("NullSafeMutableLiveData", "RememberInComposition")
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.tv.material)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
