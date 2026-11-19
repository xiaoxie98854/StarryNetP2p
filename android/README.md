# StarryNet 手机版（Android）

Java 版客户端的安卓 counterparts —— 原生 Android App，零第三方依赖。

## 定位

手机端功能完整，既能加入也能自己开房：

- **账号**：登录 / 注册、VIP 天数、每日签到领流量、流量余额
- **大厅**：查看公开房间，一键把房间码带到加入页
- **开房**：把手机上 MC 的端口经 frp 隧道映射到公网，自己当房主（v1.1.0 起）
- **加入**：输入房间码 → 拿到 MC 服务器地址 → 一键复制，粘贴进游戏即可联机
- **我的**：卡密兑换、爱发电订单认领、四个套餐直达购买页、公告、退出登录

### 开房是怎么实现的

与电脑端同一套机制，只是把 `frpc.exe` 子进程换成 APK 内置的 `libfrpc.so`：

1. `GET /create` 申请房间 → 服务端返回房间码、线路域名、公网端口、frp 令牌
2. 生成 frpc 的 TOML 配置（与桌面端 `FrpManager` 同构），
   用 `ProcessBuilder` 拉起内置的 frpc 进程，把 `127.0.0.1:端口` 转出去
3. 每 20 秒 `POST /hb` 续房间有效期
4. 前台服务（常驻通知）保活，退到后台不会断

关键点：**隧道二进制必须放在 `nativeLibraryDir`**。Android 10 起禁止从应用数据
目录（`/data/data/...`）执行任何文件，只有 `/data/app/.../lib/arm64/` 允许 exec。
所以 frpc 以 `jniLibs/arm64-v8a/libfrpc.so` 的形式打包，并开启 `extractNativeLibs`。

frpc 取自 frp 官方 `frp_<ver>_android_arm64.tar.gz`（NDK 交叉编译，比通用
linux/arm64 更适配 Android 的 bionic 与 seccomp），版本与桌面端 `frpc.exe` 对齐。

## UI 适配

按拇指操作设计，解决「手机上按钮按不到」：

- 主按钮最小 **52dp**、底部标签 **62dp**、输入框 **48dp**
- 正文字号 ≥14sp，五标签大按钮布局（账号 / 大厅 / 开房 / 加入 / 我的）
- 整页可滚动，小屏也能滑到最下方按钮
- 深色主题，与电脑客户端视觉一致

## 接口

复用协调服务器纯文本协议（`OK|` / `ERR|` 分段），敏感接口强制 HTTPS，
请求带 `&v=` 版本号（与桌面端 `ApiClient.VERSION` 一致）。服务器地址与桌面端相同。

涉及开房的接口：`/nodes`（线路表）、`/create`、`/close`、`/hb`。

## 构建

本地需 Android Studio（JDK 17 + Android SDK 35），打开本目录直接运行；
或用仓库里的 GitHub Actions（`.github/workflows/build-apk.yml`）云端编译：

```
在 GitHub 仓库 Actions 页面点 "Build StarryNet Android APK" → Run workflow
```

workflow 会先下载 frp 官方 android_arm64 包，放进 `jniLibs`，再编译签名。

产物 APK 在 Actions 的 artifacts 里，或直接用已发布到 Release 的成品：
`StarryNet-1.1.0-android.apk`。

签名密钥 `app/starrynet-release.jks` 是固定内测密钥，保证后续更新能覆盖安装。

## 目录

```
android/
├── build.gradle / settings.gradle / gradle.properties
├── app/
│   ├── build.gradle
│   ├── starrynet-release.jks      固定签名
│   └── src/main/
│       ├── AndroidManifest.xml    权限 / extractNativeLibs / 前台服务
│       ├── java/top/starrynet/mobile/
│       │   ├── MainActivity.java   五标签页 UI
│       │   ├── Tunnel.java         开房核心：frpc 子进程 + 心跳
│       │   ├── TunnelService.java  前台服务保活
│       │   ├── Api.java            网络协议
│       │   ├── Prefs.java          本地账号存储
│       │   └── Ui.java             控件工厂（大按钮）
│       └── res/                    配色 / 主题 / 图标
└── (jniLibs/arm64-v8a/libfrpc.so 由 CI 在编译时放入，不入库)
```
