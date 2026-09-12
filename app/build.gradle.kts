import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 发布签名从 keystore.properties 读取，密钥口令不写进构建脚本。
// 该文件与 .jks 都不应进版本库；文件缺失时 release 仍能构建，只是产出未签名包。
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.fumi.voice"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fumi.voice"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.5"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")

                // 签名方案：V2 + V3 是实测生效的组合。
                //
                // 关于 V1 (JAR)：这里显式开了，AGP 也把 signing-config-versions.json
                // 记成了 "enableV1Signing":true，但**产物里并没有 V1 签名块**
                // （apksigner verify -v 实测 v1 scheme: false，删包强制重跑后同样如此）。
                // 推测是 AGP 在 minSdk >= 24 时不再写 JAR 签名——这只是推测，未获证实。
                // 实际影响很小：minSdk 24 意味着所有支持设备都按 V2 校验；
                // 若某个商店的加固工具真的只认 V1，改用 apksigner
                // `--v1-signing-enabled true` 补签即可。
                enableV1Signing = true
                // V3 是 Android 9+ 的首选，也是日后"密钥轮换"的前提（AGP 默认不开）。
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // 故意不开混淆：BASS 官方绑定靠原生侧 FindClass / GetFieldID 反射
            // Java 类名与字段名，R8 一改名，JNI 查找就会在运行时失败——
            // 而且是编译期完全不报错的那种失败。
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        // 本环境里 AGP 8.5 的 lintVital 会崩（Already disposed: MessageBus），
        // 它只是发布前的静态检查、不产出产物，先关掉以免阻塞出包。
        // 换到正常环境后建议改回 true，并单独跑一次 `gradle :app:lint`。
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // 只有 MP3 编码需要原生代码（LAME）；BASS 走官方 Java 绑定，不需要自写 JNI
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // 页面左右滑动用 Pager，动效用 animation；两者本来由 material3 传递进来，
    // 但既然是直接用到的能力，就显式声明，避免上游换依赖时被动跟着崩。
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")
}
