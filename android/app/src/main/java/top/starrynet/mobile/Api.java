package top.starrynet.mobile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 协调服务器客户端 —— 与桌面端完全相同的纯文本协议。
 * 成功 OK|段1|段2...，失败 ERR|原因。所有请求带 &v= 版本号。
 *
 * 地址和敏感接口规则都对齐桌面 ApiClient：密码/令牌/卡密只走 https，
 * 绝不因为一次超时就降级到明文 http。
 */
final class Api {

    /** 和桌面客户端保持同一个版本号，服务端「封禁旧协议」才不会误伤移动端。 */
    static final String VERSION = "1.0.3";

    private static final String[] BASES = {
            "https://starrynet.top/api",
            "http://api.starrynet.top:8080",
            "http://43.161.225.143:8080"
    };

    /** 带凭据的接口：永远只走 https，跳过任何明文地址。 */
    private static final Set<String> SECURE_ONLY = new HashSet<>(Arrays.asList(
            "/login", "/reg", "/me", "/redeem", "/claim", "/flow", "/signin",
            "/create", "/close", "/hb"));

    private volatile int active = 0;

    /** 解析后的响应：ok + 用 | 切开的段（不含 OK/ERR 前缀）。 */
    static final class Result {
        final boolean ok;
        final String[] parts;
        final String raw;

        Result(boolean ok, String[] parts, String raw) {
            this.ok = ok;
            this.parts = parts;
            this.raw = raw;
        }

        String part(int i) {
            return i < parts.length ? parts[i] : "";
        }

        /** 给玩家看的报错：去掉 ERR| 前缀，没有就原样返回。 */
        String error() {
            return raw.startsWith("ERR|") ? raw.substring(4) : raw;
        }
    }

    Result ping() { return get("/ping"); }

    Result config() { return get("/config"); }

    Result login(String u, String p) {
        return get("/login?u=" + enc(u) + "&p=" + enc(p));
    }

    Result register(String u, String p) {
        return get("/reg?u=" + enc(u) + "&p=" + enc(p));
    }

    Result me(String token) { return get("/me?token=" + enc(token)); }

    Result flow(String token) { return get("/flow?token=" + enc(token)); }

    Result signin(String token) { return get("/signin?token=" + enc(token)); }

    Result redeem(String token, String key) {
        return get("/redeem?token=" + enc(token) + "&key=" + enc(key));
    }

    Result claim(String token, String order) {
        return get("/claim?token=" + enc(token) + "&order=" + enc(order));
    }

    Result rooms() { return get("/rooms"); }

    Result nodes() { return get("/nodes"); }

    Result join(String code, String token) {
        String p = "/join?code=" + enc(code);
        if (token != null && !token.isEmpty()) p += "&token=" + enc(token);
        return get(p);
    }

    Result notices(long since) { return get("/notices?since=" + since + "&n=10"); }

    /**
     * 开房。返回 OK|房间码|线路域名|转发端口|P2P密钥|线路IP|房主凭证|房间frpToken|共享frpToken
     * 与桌面端 ApiClient.create 完全同一个接口、同一段顺序。
     */
    Result create(String name, int localPort, String node, String token, boolean pub) {
        return get("/create?name=" + enc(name) + "&port=" + localPort
                + "&node=" + enc(node) + "&token=" + enc(token) + (pub ? "&public=1" : ""));
    }

    /** 房主关房。key 是建房返回第 6 段（房主凭证），只有房主有。 */
    Result close(String code, String key) {
        return get("/close?code=" + enc(code) + "&key=" + enc(key));
    }

    /** 房主心跳：续房间有效期。服务端要求 POST，凭 ownerKey 证明续命的是房主本人。 */
    Result heartbeat(String code, String key) {
        return request("/hb?code=" + enc(code) + "&key=" + enc(key), "POST");
    }

    /** 买卡地址：服务端建单后 302 到爱发电，App 只负责用浏览器打开。 */
    String buyUrl(int days) {
        return BASES[0] + "/buy?plan=" + days;
    }

    private Result get(String path) {
        return request(path, "GET");
    }

    /**
     * 统一的请求流程：https 优先、失败按序切换备用地址、敏感接口绝不降级明文。
     * POST 只用于心跳（服务端要求），不带请求体。
     */
    private Result request(String path, String method) {
        int qm = path.indexOf('?');
        String bare = qm >= 0 ? path.substring(0, qm) : path;
        boolean secureOnly = SECURE_ONLY.contains(bare);
        int start = secureOnly ? 0 : active;
        Exception last = null;

        for (int i = 0; i < BASES.length; i++) {
            int idx = (start + i) % BASES.length;
            String base = BASES[idx];
            if (secureOnly && !base.startsWith("https://")) continue;
            HttpURLConnection conn = null;
            try {
                String url = base + path + (path.indexOf('?') >= 0 ? "&" : "?") + "v=" + VERSION;
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestMethod(method);
                if ("POST".equals(method)) {
                    conn.setDoOutput(true);
                    conn.setFixedLengthStreamingMode(0);
                }
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                String body = read(in);
                if (code >= 400) {
                    last = new IOExceptionLite("HTTP " + code);
                    continue;
                }
                active = idx;
                return parse(body.trim());
            } catch (Exception e) {
                last = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        String msg = last == null ? "无可用服务器" : last.getMessage();
        return new Result(false, new String[0], "ERR|连接服务器失败：" + msg);
    }

    private static Result parse(String body) {
        if (body.startsWith("OK")) {
            String[] p = body.equals("OK") ? new String[0] : body.substring(3).split("\\|", -1);
            return new Result(true, p, body);
        }
        return new Result(false, new String[0], body.startsWith("ERR|") ? body : "ERR|" + body);
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return s == null ? "" : s;
        }
    }

    /** 避免为了一个字符串错误去引整个 java.io.IOException 之外的东西，这里简单包一层。 */
    private static final class IOExceptionLite extends Exception {
        IOExceptionLite(String m) { super(m); }
    }

    /** 字节数转人类可读流量。 */
    static String fmtBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return String.format("%.1f MB", b / 1024.0);
        return String.format("%.2f GB", b / 1024.0 / 1024.0);
    }
}
