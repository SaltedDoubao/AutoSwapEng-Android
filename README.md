# AutoSwapEng for Android

思路来源于 [ZRedTea/AutoSwapEng](https://github.com/ZRedTea/AutoSwapEng)

- 目标：将桌面 Python 自动化脚本重构为 Android 应用（无障碍 + OCR）
- 最低支持：Android 8.0 (API 28)
- 当前版本：v0.2.1

## 预期功能
- 自动完成翻转外语任务

**预计支持的题型：**
- 单词选择题
- 单词拼写题
- 听力题

## 技术栈

### 核心技术
- **Kotlin** - Android 开发语言
- **Jetpack Compose** - 现代化声明式 UI 框架
- **Material3** - Material Design 3 设计规范

### 关键功能
- **Android Accessibility Service** - 无障碍服务实现自动化操作
- **Google ML Kit** - OCR 文字识别（支持中文/拉丁文）
- **Kotlin Coroutines** - 协程实现异步编程

### 开发工具
- **Gradle (Kotlin DSL)** - 项目构建工具
- **ProGuard** - 代码混淆与优化
- **Android Gradle Plugin 8.6.1**

### 最低要求
- Android 8.0 (API 28) 及以上
- 目标 SDK: Android 14 (API 34)

## 快速开始
> 构建项目前请确认已正确配置JAVA_HOME、Gradle环境变量，并确保已安装Android Studio

### 克隆仓库
```bash
git clone https://github.com/SaltedDoubao/AutoSwapEng.git
```
```bash
cd AutoSwapEng
```
```bash
git checkout android
```

### 构建项目
> 使用Android Studio打开项目即可自动构建项目

### 打包项目
> 使用package.bat脚本打包项目
```bash
.\package.bat release
# 打包后的文件位于 app/release/
```

## 待办
- 根据收集的节点数据优化翻转外语小程序支持
- 完整OCR功能
