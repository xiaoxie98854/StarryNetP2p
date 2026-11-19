package top.starrynet.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 手机端开房核心：把手机上的 Minecraft 端口经 frp 隧道映射到公网。
 *
 * 与桌面端 FrpManager 是同一套机制 —— 同一个 /create 接口、同一份 frpc TOML 配置、
 * 同一个 7000 控制端口，只是把 "frpc.exe 子进程" 换成了 "APK 内置的 libfrpc.so 子进程"。
 *
 * 为什么要用 nativeLibraryDir 里的 libfrpc.so：
 *   Android 10 起禁止从应用数据目录 (/data/data/...) 执行任何文件（W^X 限制），
 *   而 nativeLibraryDir (/data/app/.../lib/arm64/) 是系统允许执行的目录。
 *   所以隧道二进制必须以 lib*.so 的名字打进 APK 的 jniLibs，并在打包时开启
 *   extractNativeLibs，安装后才会被解压成可执行文件。
 *
 * 生命周期由 TunnelService 的前台服务兜底：Activity 退到后台不会掐断隧道。
 */
final class Tunnel {

    /** 回调都抛到主线程，调用方可以直接改 UI。 */
    interface Listener {
        /** frpc 进程自己退出了（被系统杀、配置错、端口冲突等）。 */
        void onTunnelDead(String reason);
        /** 服务端查不到这个房间了（服务器重启 / 房间被回收），已在玩的人不受影响。 */
        void onRoomLost(String reason);
    }

    /** frps 控制端口，与桌面端 FrpManager 写死的 7000 一致。 */
    private static final int CONTROL_PORT = 7000;
    /** 心跳间隔：服务端按分钟级回收房间，20 秒足够稳。 */
    private static final long HEARTBEAT_MS = 20_000L;
    /** 连续多少次心跳拿不到正常响应才提示房间可能失效，避免一次网络抖动就吓用户。 */
    private static final int HB_FAIL_LIMIT = 3;

    private static Tunnel instance;

    static synchronized Tunnel get(Context c) {
        if (instance == null) {
            instance = new Tunnel(c.getApplicationContext());
        }
        return instance;
    }

    private final Context app;
    private final Api api = new Api();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Process proc;
    private Thread beatThread;
    private File confFile;
    private File logFile;

    private volatile boolean running;
    private volatile String lastError = "";
    private String roomCode = "";
    private String ownerKey = "";
    private String relayAddr = "";
    private Listener listener;

    private Tunnel(Context c) {
        this.app = c;
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    // ==================== 对外状态 ====================

    boolean isRunning() {
        return running;
    }

    String roomCode() {
        return roomCode;
    }

    /** 形如 f1.starrynet.top:30212，朋友在 MC 里填这个。 */
    String relayAddr() {
        return relayAddr;
    }

    String lastError() {
        return lastError;
    }

    // ==================== 开房 ====================

    /**
     * 开一间房：申请端口 → 起 frpc → 开心跳。
     * 任一步失败都会回滚（关掉已开的房间 / 杀掉半启动的进程），并让 lastError 说明原因。
     */
    boolean start(String nick, int localPort, String nodeKey, String token, boolean pub) {
        stop();
        lastError = "";

        Api.Result r = api.create(nick, localPort, nodeKey, token, pub);
        if (!r.ok) {
            lastError = r.error();
            return false;
        }
        // 段序与桌面端一致（桌面端因为保留了 "OK" 这一段，索引整体 +1）
        roomCode = r.part(0).trim();
        String host = usableHost(r.part(1), r.part(4));
        int remotePort = parseInt(r.part(2), 0);
        ownerKey = r.part(5).trim();
        String roomToken = r.part(6).trim();
        String legacyToken = r.part(7).trim();

        if (roomCode.isEmpty() || remotePort <= 0) {
            lastError = "服务端没有返回有效的房间信息";
            closeRoomQuietly();
            return false;
        }

        try {
            spawnFrpc(host, roomToken, legacyToken, localPort, remotePort);
        } catch (Exception e) {
            lastError = "隧道启动失败：" + e.getMessage();
            closeRoomQuietly();
            return false;
        }

        relayAddr = host + ":" + remotePort;
        running = true;
        startHeartbeat();
        return true;
    }

    /**
     * 房主主动关房：先杀本地进程（界面立刻响应），再后台通知服务端。
     * 关房可能从主线程调用（点按钮、Service 销毁），所以网络请求必须扔到子线程，
     * 否则会撞上 NetworkOnMainThreadException。
     */
    void stopAndClose() {
        final String code = roomCode;
        final String key = ownerKey;
        stop();
        roomCode = "";
        ownerKey = "";
        relayAddr = "";
        if (!code.isEmpty() && !key.isEmpty()) {
            Thread t = new Thread(() -> {
                try {
                    api.close(code, key);
                } catch (Exception ignored) {
                    // 关房失败不影响本地已经断开的事实，服务端最终会按超时回收
                }
            }, "close-room");
            t.setDaemon(true);
            t.start();
        }
    }

    /** 只停本地资源，不通知服务端（异常恢复、进程重启时用）。 */
    void stop() {
        running = false;

        Thread t = beatThread;
        beatThread = null;
        if (t != null) {
            t.interrupt();
        }

        Process p = proc;
        proc = null;
        if (p != null) {
            p.destroy();
            try {
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        // 配置里有 token，不该长期留在磁盘上
        if (confFile != null) {
            //noinspection ResultOfMethodCallIgnored
            confFile.delete();
            confFile = null;
        }
    }

    // ==================== frpc 子进程 ====================

    /** APK 内置的隧道二进制。打包时以 jniLibs/arm64-v8a/libfrpc.so 的形式进入安装包。 */
    static File binary(Context c) {
        return new File(c.getApplicationInfo().nativeLibraryDir, "libfrpc.so");
    }

    private void spawnFrpc(String serverAddr, String roomToken, String legacyToken,
                           int localPort, int remotePort) throws Exception {
        File bin = binary(app);
        if (!bin.isFile()) {
            throw new Exception("安装包里没有隧道程序（libfrpc.so），请用带开房功能的版本");
        }
        if (!bin.canExecute()) {
            throw new Exception("隧道程序不可执行：" + bin.getAbsolutePath());
        }

        String toml = buildToml(safe(serverAddr, "f1.starrynet.top"),
                safe(legacyToken, ""), safe(roomToken, ""),
                safe(roomCode, "room"), localPort, remotePort);

        File conf = new File(app.getFilesDir(), "frpc-" + safe(roomCode, "room") + ".toml");
        writeText(conf, toml);
        confFile = conf;

        File log = new File(app.getFilesDir(), "frpc.log");
        logFile = log;

        ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath(), "-c", conf.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.redirectOutput(log);
        proc = pb.start();
    }

    /**
     * 与桌面端 FrpManager 生成的 TOML 完全同构：
     * auth.token 走旧版共享鉴权，metadatas.token 走 frps 的 httpPlugin 房间校验。
     * 服务端配哪个都能认，过渡期两边都写。
     */
    private static String buildToml(String addr, String legacyToken, String roomToken,
                                    String code, int localPort, int remotePort) {
        String metaLine = roomToken.isEmpty() ? "" : "metadatas.token = \"" + roomToken + "\"\n";
        return "serverAddr = \"" + addr + "\"\n"
                + "serverPort = " + CONTROL_PORT + "\n"
                + "auth.token = \"" + legacyToken + "\"\n"
                + metaLine
                + "\n"
                + "[[proxies]]\n"
                + "name = \"mc-room-" + code + "\"\n"
                + "type = \"tcp\"\n"
                + "localIP = \"127.0.0.1\"\n"
                + "localPort = " + localPort + "\n"
                + "remotePort = " + remotePort + "\n";
    }

    // ==================== 心跳 ====================

    private void startHeartbeat() {
        final String code = roomCode;
        final String key = ownerKey;
        Thread t = new Thread(() -> {
            int fails = 0;
            boolean warned = false;
            while (running) {
                try {
                    Thread.sleep(HEARTBEAT_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (!running) {
                    return;
                }

                // 进程自己没了：多半是打洞被掐、端口冲突或配置被拒，服务端房间也留不住
                Process p = proc;
                if (p != null && !p.isAlive()) {
                    running = false;
                    fireTunnelDead("隧道进程已退出" + logHint());
                    return;
                }

                Api.Result r = api.heartbeat(code, key);
                if (r.ok) {
                    fails = 0;
                    warned = false;
                    continue;
                }
                // "请求太频繁" 只是这一拍没打上，房间本身没事
                if (r.error().contains("频繁")) {
                    continue;
                }
                fails++;
                if (fails >= HB_FAIL_LIMIT && !warned) {
                    warned = true;
                    fireRoomLost("服务器上已查不到这个房间，新朋友可能加不进来");
                }
            }
        }, "tunnel-heartbeat");
        t.setDaemon(true);
        beatThread = t;
        t.start();
    }

    private void fireTunnelDead(String reason) {
        Listener l = listener;
        if (l != null) {
            main.post(() -> l.onTunnelDead(reason));
        }
    }

    private void fireRoomLost(String reason) {
        Listener l = listener;
        if (l != null) {
            main.post(() -> l.onRoomLost(reason));
        }
    }

    /** frpc 日志末尾几行，用来解释为什么挂掉（连不上 frps、port already used 等）。 */
    private String logHint() {
        File f = logFile;
        if (f == null || !f.isFile()) {
            return "";
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            String last = "";
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    last = line.trim();
                }
            }
            last = last.replaceAll("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}", "").trim();
            return last.isEmpty() ? "" : "：" + (last.length() > 90 ? last.substring(0, 90) : last);
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 小工具 ====================

    /**
     * 只允许字母数字点和横线，杜绝把服务端下发的值直接拼进 TOML 造成配置注入。
     * 与桌面端 FrpManager.safe 完全一致。
     */
    private static String safe(String s, String fallback) {
        if (s == null || s.trim().isEmpty()) {
            return fallback;
        }
        String t = s.trim();
        return t.matches("[A-Za-z0-9._-]{1,64}") ? t : fallback;
    }

    /** 线路域名优先，解析不了时退回线路 IP（与桌面端 usableHost 同思路）。 */
    private static String usableHost(String domain, String ip) {
        if (domain != null && !domain.trim().isEmpty()) {
            return domain.trim();
        }
        if (ip != null && !ip.trim().isEmpty()) {
            return ip.trim();
        }
        return "f1.starrynet.top";
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void writeText(File f, String text) throws Exception {
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(text);
        }
    }

    /** 关房但不走 UI 通知（内部回滚用）。 */
    private void closeRoomQuietly() {
        String code = roomCode;
        String key = ownerKey;
        roomCode = "";
        ownerKey = "";
        relayAddr = "";
        if (!code.isEmpty() && !key.isEmpty()) {
            try {
                api.close(code, key);
            } catch (Exception ignored) {
                // 回滚失败也无所谓，服务端会超时回收
            }
        }
    }
}
