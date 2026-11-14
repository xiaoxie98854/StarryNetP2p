<div align="center">

# StarryNet

### Minecraft 联机隧道 · 不用端口映射，不用公网 IP

[![version](https://img.shields.io/badge/version-0.9.8-3ddc97?style=flat-square)]()
[![java](https://img.shields.io/badge/Java-17-orange?style=flat-square)]()
[![platform](https://img.shields.io/badge/platform-Windows-blue?style=flat-square)]()
[![license](https://img.shields.io/badge/license-MIT-green?style=flat-square)]()

</div>

---

## 这是什么

给不会折腾端口映射的玩家用的 Minecraft 联机工具。

房主点一下「建房」，拿到一个地址（类似 `f1.starrynet.top:22724`），复制发给朋友，
朋友粘进 MC 的「多人游戏 → 直接连接」就能进。**全程不用碰路由器、不用公网 IP、朋友不用装任何东西。**

装了客户端的朋友还能走 **P2P 直连**，延迟更低（20–40ms vs 中继 70–80ms），且不占服务器带宽。

> 中继永远在，P2P 是加速不是替代——打洞失败也不会没得玩。

---

## 特性

- **一键建房** —— 不碰路由器，不配防火墙，不给运营商打电话
- **中继 + P2P 双通道** —— 朋友没装客户端走中继，装了客户端自动打洞直连
- **网页登录注册** —— 客户端点登录自动跳浏览器，配对码授权，30 天免登录
- **邮箱验证 + 找回密码** —— 注册收验证码，忘密码用邮箱重置
- **卡密充值** —— 发卡平台卖卡密，客户端输码兑换 VIP
- **免费档 + VIP 档** —— 免费线路限速 8Mbps 够 3 人玩，VIP 不限速
- **深色 UI + 滑动动画** —— 仿现代设计语言，设置面板左滑切换
- **ProGuard 混淆** —— 客户端加固，入口混淆，调试信息移除

---

## 下载

前往 **[官网](https://starrynet.top/#download)** 下载最新版 Windows 安装包。

解压后双击 `StarryNet.exe` 即可运行，无需安装 Java（自带精简 JRE）。

---

## 快速开始

### 房主

1. 打开 StarryNet
2. 点「登录」→ 浏览器里注册 / 登录 → 回到客户端自动完成
3. 游戏内「对局域网开放」→ 回填端口 → 点「建房」
4. 复制弹出的地址发给朋友

### 朋友（两种方式）

| 方式 | 需要 | 延迟 |
|---|---|---|
| **直接连接**（推荐给小白） | 只要有地址 | 70–80ms |
| **P2P 加入**（装了客户端） | 房间码 | 20–40ms |

---

## 工作原理

```
  房主 MC (25565)                      朋友 MC
       │                                  │
       ▼                                  ▼
   ┌────────┐    中继隧道    ┌────────┐
   │ frpc   │ ◄════════════► │ 直接连 │  ← 朋友没装客户端
   └────────┘   f1.starrynet │        │
       │          :端口       └────────┘
       │
       │ P2P 直连（朋友装了客户端）
       ▼
   ┌────────┐   easytier 打洞   ┌────────┐
   │  ET    │ ◄═══ UDP 直连 ═══► │  ET    │
   └────────┘                    └────────┘
```

- **中继**：frp 把房主的 MC 端口映射到公网域名，朋友直接连域名
- **P2P**：easytier 在两端之间打 UDP 洞，流量不经过服务器

---

## 项目结构

```
starrynet/
├── client/              # 客户端（Java Swing + jpackage）
│   ├── Launcher.java    #   主界面、建房/加入、设置面板、滑动动画
│   ├── Ui.java          #   深色 UI 组件库（Card / Sheet / Btn / Dot）
│   ├── ApiClient.java   #   HTTP 客户端（多地址降级，敏感接口强制 https）
│   ├── FrpManager.java  #   frpc 进程管理 + 配置生成
│   └── EtManager.java   #   easytier P2P 进程管理
├── server/              # 协调服务器（纯 Java，无框架）
│   ├── CoordinatorServer.java  #   HTTP API + 路由 + 限流 + 配对码
│   ├── UserStore.java   #   用户/卡密/令牌（SQLite，PBKDF2 密码哈希）
│   ├── RoomManager.java #   房间生命周期
│   ├── Mailer.java      #   阿里云 DirectMail 发信
│   └── Room.java        #   房间数据
├── deploy/
│   ├── site/            # 官网（单文件 HTML，仿深色设计）
│   │   ├── index.html   #   首页
│   │   ├── auth/        #   登录/注册/找回密码
│   │   └── admin/       #   管理后台（卡密生成/库存/作废）
│   ├── server.properties.example  # 配置模板（脱敏）
│   └── nginx-starrynet-site.conf  # nginx 反代配置
└── README.md
```

---

## 自建部署

### 服务端

```bash
# 1. 编译（需要 JDK 17）
cd server
javac -encoding UTF-8 -cp lib/sqlite-jdbc-3.46.1.3.jar -d out *.java
jar --create --file=mc-coordinator.jar --main-class=server.CoordinatorServer -C out .

# 2. 配置
cp deploy/server.properties.example server.properties
# 编辑 server.properties 填入你自己的：
#   admin.secret = openssl rand -hex 12
#   mail.user / mail.pass = 你的发信邮箱和授权码
#   auth.site = https://你的域名/auth/

# 3. 运行
java -jar mc-coordinator.jar server.properties
```

### 客户端打包

```bash
cd client
# 需要 JDK 17、ProGuard 7.5、easytier 二进制
JDK=/path/to/jdk17 \
PGLIB=/path/to/proguard/lib \
ET_SRC=/path/to/easytier-windows-x86_64 \
VERSION=0.9.8 \
bash package-obf.sh
```

产物在 `build/out/StarryNet/`，用 `jpackage` 打成 Windows app-image。

---

## API 协议

服务端是纯 HTTP GET（没框架没依赖），成功 `OK|` 开头，失败 `ERR|` 开头：

| 接口 | 参数 | 返回 |
|---|---|---|
| `GET /ping` | — | `OK\|时间戳` |
| `GET /config` | — | `OK\|emailRequired=..\|requireLogin=..` |
| `GET /nodes` | — | `OK\|key~名称~档位~域名\|...` |
| `GET /auth/new` | — | `OK\|6位配对码` |
| `GET /auth/poll` | `pair` | `WAIT` 或 `OK\|token\|昵称\|vip天数` |
| `GET /auth/bind` | `pair`, `token` | `OK` |
| `GET /reg` | `u`, `p`, `email`, `code` | `OK\|token\|昵称\|vip天数` |
| `GET /login` | `u`, `p` | `OK\|token\|昵称\|vip天数` |
| `GET /me` | `token` | `OK\|昵称\|vip天数` |
| `GET /sendcode` | `email`, `mode`, `cid`, `cans` | `OK\|已发送` |
| `GET /resetpwd` | `email`, `code`, `p` | `OK\|密码已重置` |
| `GET /redeem` | `token`, `key` | `OK\|剩余天数` |
| `GET /captcha` | — | `OK\|id\|算术题` |

> 登录/注册/令牌/卡密接口只接受 **HTTPS**（经 nginx 反代），明文 8080 直连会被拒绝。
> 联机信令（`/nodes` `/create` `/join`）仍放行 HTTP，老客户端不受影响。

---

## 安全

- **密码** PBKDF2-HMAC-SHA256，10 万次迭代 + 16 字节随机盐
- **令牌** 30 天滑动有效期，每次 `/me` 自动续期
- **限流** 注册 5/IP/小时、登录 20/IP/10分钟、发信 20/IP/天 + 全站 150/天
- **人机验证** 算术题（`/captcha`），一题一次，5 分钟过期
- **发信防刷** 异步队列 + 四道闸（IP / 全站 / 邮箱 / 人机验证）
- **X-Forwarded-For** 只信任 nginx 覆盖的 `$remote_addr`，客户端伪造无效
- **敏感接口** 强制 HTTPS，`access_log off` 不记录密码和令牌

---

## 技术栈

| 层 | 技术 |
|---|---|
| 客户端 | Java 17 / Swing / jpackage / ProGuard |
| 中继 | [frp](https://github.com/fatedier/frp) |
| P2P | [easytier](https://github.com/EasyTier/EasyTier) |
| 服务端 | Java 17 HttpServer / SQLite (WAL) |
| 发信 | 阿里云 DirectMail |
| 前端 | 单文件 HTML / 原生 JS / 无框架 |
| 反代 | nginx (HTTPS + HTTP/2) |

---

## 版本历史

| 版本 | 主要变更 |
|---|---|
| **0.9.8** | 安全加固：TLS 模块修复、敏感接口强制 HTTPS、人机验证、发信防刷、ProGuard 混淆增强 |
| 0.9.7 | 登录注册搬到网页（配对码授权），令牌 30 天滑动有效期 |
| 0.9.6 | 登录和注册拆成独立 UI |
| 0.9.4 | 邮箱验证注册、强制登录开关 |
| 0.9.3 | 深色卡片 UI（Ui.Sheet） |
| 0.9.1 | 改名 StarryNet，域名替代 IP |
| 0.9.0 | P2P 直连（easytier） |

---

<div align="center">

**[官网](https://starrynet.top)** · **[下载](https://starrynet.top/#download)** · **[登录](https://starrynet.top/auth/)**

</div>
"# StarryNetP2p" 
