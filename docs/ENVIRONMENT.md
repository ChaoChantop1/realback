# Windows 无 Android Studio 纯命令行环境搭建实录

> 2026-09-05 在一台干净的 Windows 10（x64）机器上，从零搭起 RealBack 的 Android 构建环境并完成：core 全量单测（32/32 通过）→ debug APK 构建（9.8 MB）→ 签名校验。
> 目的：把过程中踩到的坑沉淀成可复现的手册，供无 Android Studio 的机器（CI、容器、临时开发机）参考。

## 一、总览

| 组件 | 版本 | 安装位置 |
|------|------|----------|
| JDK | Temurin 17.0.20.1 | `C:\android-demo\jdk-17.0.20.1+1` |
| Android SDK | platform-35 / build-tools 35.0.0 / platform-tools | `C:\android-demo\sdk` |
| cmdline-tools | 11076708 | `C:\android-demo\sdk\cmdline-tools\latest` |
| Gradle | 8.10.2 | `C:\android-demo\gradle-8.10.2` |

磁盘占用约 5 GB（含系统镜像约 8 GB）。总耗时约 15 分钟（百兆带宽）。

## 二、下载源选择（重要）

| 源 | 结果 |
|----|------|
| `api.adoptium.net`（API） | ✅ 可达，但返回的下载链接指向 GitHub Releases |
| **GitHub Releases 大文件** | ❌ **会被拦断**——JDK zip 只下到 500 KB 即中断 |
| 清华镜像 `mirrors.tuna.tsinghua.edu.cn/Adoptium/` | ✅ JDK 182 MB，约 37 MB/s |
| 腾讯镜像 `mirrors.cloud.tencent.com/gradle/` | ✅ Gradle 130 MB，稳定 |
| `dl.google.com`（Android SDK） | ✅ 国内直连可达，无需镜像 |

**结论**：JDK/Gradle 走清华/腾讯镜像，SDK 组件直连 Google 官方源。

## 三、搭建步骤（可复制）

### 1. JDK 17

```bash
curl -L -o jdk17.zip "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip"
unzip -q jdk17.zip   # 解压出 jdk-17.0.20.1+1/
```

### 2. Android cmdline-tools + 许可证

```bash
mkdir -p sdk/cmdline-tools && cd sdk/cmdline-tools
curl -L -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
unzip -q cmdtools.zip && mv cmdline-tools latest   # 必须叫 latest，sdkmanager 才认
export JAVA_HOME="C:\android-demo\jdk-17.0.20.1+1"
yes | latest/bin/sdkmanager.bat --licenses         # 接受全部许可证
```

### 3. SDK 组件

```bash
latest/bin/sdkmanager.bat "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

### 4. Gradle

```bash
curl -L -o gradle.zip "https://mirrors.cloud.tencent.com/gradle/gradle-8.10.2-bin.zip"
unzip -q gradle.zip
```

### 5. 项目配置 + 构建验证

```bash
# 项目根目录写 local.properties（注意坑 1！）
echo 'sdk.dir=C:/android-demo/sdk' > local.properties

export JAVA_HOME="C:\android-demo\jdk-17.0.20.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
gradle-8.10.2/bin/gradle :core:test                  # → 32 tests, 0 failed
gradle-8.10.2/bin/gradle :app-android:assembleDebug  # → BUILD SUCCESSFUL
sdk/build-tools/35.0.0/apksigner.bat verify --print-certs \
  app-android/build/outputs/apk/debug/app-android-debug.apk
```

## 四、踩坑记录

### 坑 1：local.properties 反斜杠路径 → 乱码 IOException（最隐蔽）

**症状**：`assembleDebug` 秒败，报
`java.io.IOException: 文件名、目录名或卷标语法不正确`（在 GBK 控制台上显示为乱码），
stacktrace 指向 `SdkLocator.validateSdkPath`。

**根因**：`.properties` 格式把 `\` 当转义符。写成 `sdk.dir=C:\android-demo\sdk` 时，
`\a` 被解析吃掉，SDK 路径变成 `C:android-demo sdk` 之类的废串。

**修复**：**永远用正斜杠**：

```properties
sdk.dir=C:/android-demo/sdk
```

### 坑 2：GitHub Releases 大文件下载被拦断

JDK 从 GitHub 下载只得到 500 KB（200 状态码但连接中断）。API 返回的"官方"链接都指向
github.com/releases——在这台机器的网络上不可用于大文件。**改用清华/腾讯镜像**（见第二节表格）。

### 坑 3：cmdline-tools 解压目录名

Google 的 zip 解压出来是 `cmdline-tools/`，而 sdkmanager 要求它位于
`<sdk>/cmdline-tools/latest/`，否则报找不到。解压后必须重命名：

```bash
unzip -q cmdtools.zip && mv cmdline-tools latest
```

### 坑 4：Git Bash 下调用 .bat 与 Windows 命令的引号/路径转换

- `yes | latest/bin/sdkmanager.bat --licenses` 在 Git Bash 下可直接运行；
- 但 `cmd //c ...` 会触发 UNC 路径错误，`taskkill //PID` 会被 MSYS 路径转换弄坏；
- **杀进程用 PowerShell 最稳**：`powershell.exe -Command "Stop-Process -Id <pid> -Force"`。

### 坑 5：模拟器需要 WHPX，纯软件模式不可用

- 检查：`emulator -accel-check` → `Android Emulator hypervisor driver is not installed`；
- 服务器/精简版 Windows 上 **WHPX（Windows 虚拟机监控程序平台）默认禁用**，
  启用需管理员权限 + **重启**；
- 强行 `-no-accel` 软件模拟 x86_64 + Android 35：内核能起，用户空间引导 15 分钟仍
  `offline`，基本不可用。**结论：要模拟器先启用 WHPX，别浪费时间试软件模式。**

### 坑 6：非交互环境下别忘了 JAVA_HOME

`gradle` / `apksigner` 都是 Java 程序，每次新 shell 都要：

```bash
export JAVA_HOME="C:\android-demo\jdk-17.0.20.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
```

否则报 `JAVA_HOME is not set`。

## 五、验收清单

环境是否真正可用，以下面三条为准（呼应本仓库 CI 教训：**验收看产物，不看绿色对勾**）：

1. `gradle :core:test` 输出 `BUILD SUCCESSFUL`，且 `core/build/test-results/test/*.xml`
   中 `failures="0" errors="0"`（当前 7 套件 32 用例）
2. `gradle :app-android:assembleDebug` 后 `ls app-android/build/outputs/apk/debug/*.apk`
   **文件真实存在**
3. `apksigner verify` 通过，证书为 Android Debug

## 六、已知限制

- 当前 debug APK 为 debug 签名；正式分发前需换 keystore（见 `app-android/build.gradle.kts` 注释）
- 模拟器演示需先启用 WHPX（见坑 5）
- 本文路径均基于 `C:\android-demo`，迁移目录时需同步改 `local.properties`
