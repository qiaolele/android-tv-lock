# 电视锁（TV Lock）—— 安卓 TV Launcher

看电视前必须先做题的电视桌面（Launcher）。开机即进入答题锁屏，做对全部题目后进入「选择想看的电视」界面，选中即拉起对应电视 App。可看时长倒计时结束后自动弹回锁屏（防沉迷闭环）。

## 功能
- 开机自启 / 设为默认桌面后，按主页键也回不到别处（孩子逃不出）
- 随机算术题：运算类型（加/减/乘/除 多选）、数值上限（10/20/50/100/自定义）、题量、可看时长，均可在「设置」里改，且本地记忆
- 答题成功 → 选择电视 App（电视直播 / 腾讯视频 / 爱奇艺 / 哔哩哔哩 / 芒果 TV / 咪咕视频）→ 真拉起
- 倒计时结束自动回到锁屏

## 出包方式（二选一）

### A. GitHub Actions 云端出包（推荐，电脑零安装）
1. 把这个 `android-tv-lock` 文件夹作为仓库推到 GitHub（或 Fork 后上传）。
2. 打开仓库的 **Actions** 标签页，左侧选 `Build TV Lock APK`，点 **Run workflow**。
3. 跑完后在本次运行的 **Artifacts** 里下载 `tv-lock-apk`（里面是 `app-debug.apk`）。
> 构建在云端完成，你电脑无需安装 Android Studio / JDK / Gradle。

### B. 本地 Android Studio
直接打开 `android-tv-lock` 文件夹 → 等待 Gradle 同步 → Run（或 Build → Build Bundle(s) / APK(s)）。

## 安装到小米盒子（安卓 TV）
1. 盒子：设置 → 关于 → 连续点「产品型号」开启**开发者选项** → 打开 **USB 调试**（或网络调试）。
2. 电脑装好 ADB 后：`adb connect <盒子IP>:5555`（网络调试）或 USB 直连，然后 `adb install app-debug.apk`。
3. 盒子：设置 → 应用 → **默认应用 → 桌面**，选择「电视锁」。
4. 重启盒子验证：开机直接进入答题锁屏；按主页键也回不来。

## 家长退出 / 重置
- 临时退出看自己的内容：长按遥控器**主页键**，在弹出的桌面选择里切回原桌面。
- 彻底卸载：`adb uninstall com.example.tvlock`，或在盒子「默认应用 → 桌面」改回原桌面后卸载。

## 自定义题目 / 选择项
- 题目难度、题量、时长：直接在电视上「设置」里改（已本地记忆）。
- 想改「选择想看的电视」里的 App 列表：编辑 `app/src/main/assets/index.html` 里的 `app-tile`（改 `data-package` 包名与显示名），重新出包即可。

## 工程说明
- 纯原生 Android（Java），仅用系统自带 WebView，无任何第三方依赖。
- 网页 UI 在 `app/src/main/assets/index.html`（同时也在上层 `../index.html` 供电脑预览）。
- `MainActivity` 通过 `AndroidBridge` 暴露 `launchApp(pkg, name)` 与 `bringToFront()` 给网页。
- Manifest 注册了 `HOME` 桌面角色 + `BOOT_COMPLETED` 开机自启广播双重保障。
