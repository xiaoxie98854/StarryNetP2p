# StarryNet 手机版（Android）

Java 版客户端的安卓 counterparts —— 原生 Android App，零第三方依赖。

## 定位

手机版负责「在手机上能做的部分」：

- **账号**：登录 / 注册、VIP 天数、每日签到领流量、流量余额
- **大厅**：查看公开房间，一键把房间码带到加入页
- **加入**：输入房间码 → 拿到 MC 服务器地址 → 一键复制，粘贴进游戏即可联机
- **我的**：卡密兑换、爱发电订单认领、四个套餐直达购买页、公告、退出登录

> 开房需要后台常驻跑隧道进程（frpc / easytier），手机系统对后台保活、VPN 权限限制太多，
> 所以**开房仍由电脑客户端负责**，手机版专注加入与账号管理。

## UI 适配

按拇指操作设计，解决「手机上按钮按不到」：

- 主按钮最小 **52dp**、底部标签 **62dp**、输入框 **48dp**
- 正文字号 ≥14sp，四标签大按钮布局（账号 / 大厅 / 加入 / 我的）
- 整页可滚动，小屏也能滑到最下方按钮
- 深色主题，与电脑客户端视觉一致

## 接口

复用协调服务器纯文本协议（`OK|` / `ERR|` 分段），敏感接口强制 HTTPS，
请求带 `&v=` 版本号（与桌面端 `ApiClient.VERSION` 一致）。服务器地址与桌面端相同。

## 构建

本地需 Android Studio（JDK 17 + Android SDK 35），打开本目录直接运行；
或用仓库里的 GitHub Actions（`.github/workflows/build-apk.yml`）云端编译：

```
在 GitHub 仓库 Actions 页面点 "Build StarryNet Android APK" → Run workflow
```

产物 APK 在 Actions 的 artifacts 里，或直接用已发布到 Release 的成品：
`StarryNet-1.0.0-android.apk`。

签名密钥 `app/starrynet-release.jks` 是固定内测密钥，保证后续更新能覆盖安装。

## 目录

```
android/
├── build.gradle / settings.gradle / gradle.properties
├── app/
│   ├── build.gradle
│   ├── starrynet-release.jks      固定签名
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/top/starrynet/mobile/
│       │   ├── MainActivity.java   四大标签页 UI
│       │   ├── Api.java            网络协议
│       │   ├── Prefs.java          本地账号存储
│       │   └── Ui.java             控件工厂（大按钮）
│       └── res/                    配色 / 主题 / 图标
```
