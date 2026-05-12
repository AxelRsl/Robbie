# 开发环境配置指南（猎户星空机器人 APK 二开）

## 目录

1. [Android Studio 安装](#1-android-studio-安装)
2. [SDK 与环境要求](#2-sdk-与环境要求)
3. [ADB 调试配置](#3-adb-调试配置)
4. [新建项目模板](#4-新建项目模板)
5. [Gradle 配置模板](#5-gradle-配置模板)
6. [常用 ADB 命令](#6-常用-adb-命令)
7. [开发调试技巧](#7-开发调试技巧)


---

## 1. Android Studio 安装

官方下载：https://developer.android.com/studio

推荐版本：Hedgehog (2023.1.1) 以上

安装步骤：
1. 下载安装包并安装
2. 首次启动时，Android Studio 会自动下载 Android SDK
3. 在 SDK Manager 中安装 API 26 - 34 的 SDK Platform

---

## 2. SDK 与环境要求

| 要求 | 版本 |
|---|---|
| JDK | 11（Android Studio 内置） |
| Android minSdk | 26 (Android 8.0) |
| Android targetSdk | 34 |
| Kotlin | 1.9.22 |
| AGP（Android Gradle Plugin）| 8.2.2 |
| Gradle | 8.7 |
| AgentOS SDK | 0.4.5-SNAPSHOT |
| RobotAPI (robotservice.jar) | 11.3 |
| AgentOS Product Version | V1.3.0.250515 |

---

## 3. ADB 调试配置

### 3.1 通过 WiFi 连接机器人

```bash
# 连接机器人（机器人和电脑需在同一局域网）
adb connect <机器人IP>:5555

# 确认连接成功
adb devices
# 输出应包含: <IP>:5555  device

# 断开连接
adb disconnect <机器人IP>:5555
```

### 3.2 如果机器人 TCP 调试未开启

部分机器人出厂时 ADB TCP 调试未启用，需要先用 USB 连接开启：

```bash
# USB 连接后执行
adb shell setprop service.adb.tcp.port 5555
adb shell stop adbd
adb shell start adbd
# 之后可以 WiFi 连接
```

---

## 4. 新建项目模板

在 Android Studio 中新建项目：
1. **File → New → New Project**
2. 选择 **Empty Views Activity**
3. 设置：
   - **Language**: Kotlin
   - **Minimum SDK**: API 26 (Android 8.0)
   - **Build configuration language**: Kotlin DSL

---

## 5. Gradle 配置模板

### settings.gradle.kts

```kotlin
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
        // 猎户星空 AgentOS SDK Maven 仓库
        maven {
            credentials.username = "agentMaven"
            credentials.password = "agentMaven"
            url = uri("https://npm.ainirobot.com/repository/maven-public/")
        }
    }
}

rootProject.name = "YourProjectName"
include(":app")
```

### app/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.your.package"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.your.package"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("com.orionstar.agent:sdk:0.4.5-SNAPSHOT")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
```

### AndroidManifest.xml 关键配置

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 权限 -->
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="com.ainirobot.coreservice.robotSettingProvider" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>

    <application
        android:name=".MainApplication"
        android:allowBackup="true"
        android:label="@string/app_name">

        <activity android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
            <!-- 开机自启（可选） -->
            <intent-filter>
                <action android:name="action.orionstar.default.app" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

### app/src/main/assets/actionRegistry.json

```json
{
  "appId": "app_ebbd1e6e22d6499eb9c220daf095d465",
  "platform": "apk",
  "actionList": []
}
```

---

## 6. 常用 ADB 命令

```bash
# 安装/覆盖安装 APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 启动 App
adb shell am start -n com.your.package/.MainActivity

# 停止 App
adb shell am force-stop com.your.package

# 查看 App 日志（按包名过滤）
adb logcat --pid=$(adb shell pidof com.your.package) -v time

# 按 Tag 过滤日志
adb logcat -s YourTag:D

# 发送测试广播（ADB 测试 Action 触发）
adb shell am broadcast -a com.your.package.TEST_ACTION \
  --es query_text "开始" \
  -n com.your.package/.TestReceiver

# 截图
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png .

# 查看 App 存储目录
adb shell run-as com.your.package ls /data/data/com.your.package/

# 推送文件到设备
adb push local_file.txt /sdcard/
```

---

## 7. 开发调试技巧

### 7.1 Action 测试技巧

可以通过广播在不说话的情况下触发 AgentCore.query()：

```kotlin
// MainActivity.kt 中注册测试 Receiver
private val testReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("query_text") ?: return
        AgentCore.query(text)
    }
}

// 注册
registerReceiver(testReceiver, IntentFilter("com.your.package.TEST_QUERY"),
    Context.RECEIVER_NOT_EXPORTED)

// ADB 触发
// adb shell am broadcast -a com.your.package.TEST_QUERY --es query_text "开始"
```

### 7.2 开机启动配置

三指下拉 → 系统设置 → 开发者设置 → 开机启动程序 → 配置你的应用
（需要 OTA3+ 支持）
