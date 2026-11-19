package top.starrynet.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * StarryNet 手机版 —— 五个大标签：账号 / 大厅 / 开房 / 加入 / 我的。
 *
 * 手机端功能齐全：能加入别人的房间，也能自己开房（把手机上 MC 的端口经 frp 隧道
 * 映射到公网，隧道由内置的 libfrpc.so 子进程承担，靠前台服务保活）。
 *
 * 所有控件按拇指操作设计：主按钮 52dp、标签 62dp、输入框 48dp，
 * 字号不小于 14sp，整页可滚动，小屏也不会有按不到的按钮。
 */
public class MainActivity extends Activity {

    private static final String APP_VERSION = "1.0.0";

    private final Api api = new Api();
    private Prefs prefs;

    // 底部四个标签
    private Button[] tabs;
    private View[] pages;
    private FrameLayout content;
    private int currentTab = -1;

    // 账号页
    private EditText userField, passField;
    private LinearLayout guestBox, loggedBox;
    private TextView accountName, accountVip, accountFlow, accountAdmin, signinState;

    // 大厅页
    private LinearLayout roomsBox;
    private TextView roomsInfo;

    // 加入页
    private EditText codeField, joinNameField;
    private TextView joinResult, copyBtn, joinHint;

    // 开房页
    private EditText hostPortField;
    private TextView hostNodeRow, hostRoomCode, hostAddr, hostStatus;
    private Button hostBtn;
    private LinearLayout hostResultBox;
    /** /nodes 拉回来的线路 key 与显示名，默认选第一条。 */
    private final java.util.List<String> nodeKeys = new java.util.ArrayList<>();
    private final java.util.List<String> nodeNames = new java.util.ArrayList<>();
    private int nodeIdx = 0;
    private boolean nodesLoaded = false;

    // 我的页
    private EditText redeemField, claimField;
    private TextView redeemResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = new Prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.bg));
        root.setFitsSystemWindows(true);

        // ---------- 顶部标题 ----------
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Ui.color(this, R.color.card));
        int hp = Ui.dp(this, 16);
        header.setPadding(hp, Ui.dp(this, 14), hp, Ui.dp(this, 14));
        TextView logo = Ui.bold(this, "StarryNet", 20, Ui.color(this, R.color.text));
        TextView ver = Ui.text(this, "手机版 v" + APP_VERSION, 13, Ui.color(this, R.color.muted));
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        logo.setLayoutParams(logoLp);
        header.addView(logo);
        header.addView(ver);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---------- 内容区 ----------
        content = new FrameLayout(this);
        content.setBackgroundColor(Ui.color(this, R.color.bg));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---------- 底部标签栏 ----------
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Ui.color(this, R.color.card));
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        String[] names = {"账号", "大厅", "开房", "加入", "我的"};
        tabs = new Button[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            Button t = new Button(this);
            t.setText(names[i]);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            t.setAllCaps(false);
            t.setBackground(null);
            t.setMinHeight(Ui.dp(this, 62));
            t.setPadding(0, 0, 0, 0);
            t.setTextColor(Ui.color(this, R.color.muted));
            t.setOnClickListener(v -> switchTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            t.setLayoutParams(lp);
            tabs[i] = t;
            nav.addView(t);
        }
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        // ---------- 构建五个页面 ----------
        pages = new View[]{
                buildAccountPage(),
                buildLobbyPage(),
                buildHostPage(),
                buildJoinPage(),
                buildMinePage()
        };
        for (View p : pages) {
            content.addView(p, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        switchTab(0);

        // 隧道状态回调（进程被系统杀掉、房间在服务端失效时会用到）
        attachTunnel();

        // 启动后：校验令牌 + 拉公告（都不阻塞界面）
        if (prefs.logged()) {
            refreshMe();
            refreshFlow();
        }
        loadNotices();
    }

    // ==================== 账号页 ====================

    private View buildAccountPage() {
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));

        // 未登录：登录 / 注册表单
        guestBox = Ui.card(this);
        guestBox.addView(Ui.bold(this, "登录账号", 17, Ui.color(this, R.color.text)));
        guestBox.addView(Ui.spacer(this, 12));
        userField = Ui.field(this, "用户名", false);
        passField = Ui.field(this, "密码", true);
        guestBox.addView(userField);
        guestBox.addView(passField);

        LinearLayout btnRow = Ui.row(this);
        Button loginBtn = Ui.button(this, "登录", Ui.color(this, R.color.green),
                Ui.color(this, R.color.bg));
        Button regBtn = Ui.outlineButton(this, "注册", Ui.color(this, R.color.blue));
        Ui.equalWidth(btnRow, loginBtn, regBtn);
        Ui.marginRight(loginBtn, this, 6);
        Ui.marginLeft(regBtn, this, 6);
        btnRow.addView(loginBtn);
        btnRow.addView(regBtn);
        guestBox.addView(btnRow);
        loginBtn.setOnClickListener(v -> doLogin(false));
        regBtn.setOnClickListener(v -> doLogin(true));
        page.addView(guestBox);

        // 已登录：账号信息 + 签到
        loggedBox = Ui.card(this);
        accountName = Ui.bold(this, "", 18, Ui.color(this, R.color.text));
        loggedBox.addView(accountName);
        loggedBox.addView(Ui.spacer(this, 10));
        accountVip = Ui.text(this, "", 15, Ui.color(this, R.color.gold));
        accountAdmin = Ui.text(this, "", 13, Ui.color(this, R.color.blue));
        loggedBox.addView(accountVip);
        loggedBox.addView(accountAdmin);
        loggedBox.addView(Ui.spacer(this, 14));

        View line = new View(this);
        line.setBackgroundColor(Ui.color(this, R.color.line_soft));
        loggedBox.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        loggedBox.addView(Ui.spacer(this, 14));
        loggedBox.addView(Ui.caption(this, "今日流量"));
        accountFlow = Ui.bold(this, "查询中…", 16, Ui.color(this, R.color.text));
        loggedBox.addView(accountFlow);
        signinState = Ui.text(this, "", 13, Ui.color(this, R.color.muted));
        loggedBox.addView(signinState);
        loggedBox.addView(Ui.spacer(this, 12));

        Button signinBtn = Ui.button(this, "每日签到领流量", Ui.color(this, R.color.green),
                Ui.color(this, R.color.bg));
        loggedBox.addView(signinBtn);
        Button refreshBtn = Ui.outlineButton(this, "刷新流量", Ui.color(this, R.color.blue));
        loggedBox.addView(Ui.spacer(this, 10));
        loggedBox.addView(refreshBtn);
        signinBtn.setOnClickListener(v -> doSignin());
        refreshBtn.setOnClickListener(v -> refreshFlow());

        page.addView(loggedBox);

        TextView tip = Ui.text(this,
                "提示：每日签到可领取流量，VIP 每日额度更高。流量用于限制长时间挂机，正常联机不受影响。",
                13, Ui.color(this, R.color.dim));
        tip.setPadding(0, Ui.dp(this, 6), 0, 0);
        page.addView(tip);

        return Ui.page(this, page);
    }

    private void doLogin(boolean register) {
        String u = userField.getText().toString().trim();
        String p = passField.getText().toString().trim();
        if (u.isEmpty() || p.isEmpty()) {
            toast("请输入用户名和密码");
            return;
        }
        toast("正在" + (register ? "注册" : "登录") + "…");
        async(() -> register ? api.register(u, p) : api.login(u, p), r -> {
            if (!r.ok) {
                toast(r.error());
                return;
            }
            // OK|token|昵称|vip天数|admin=0/1
            String token = r.part(0);
            String name = r.part(1);
            int vip = parseInt(r.part(2));
            boolean admin = "admin=1".equalsIgnoreCase(r.part(3));
            prefs.saveLogin(token, name, vip, admin);
            toast((register ? "注册成功" : "登录成功") + "：" + name);
            refreshAccountUi();
            refreshFlow();
        });
    }

    private void doSignin() {
        if (!needLogin()) return;
        toast("正在签到…");
        async(() -> api.signin(prefs.token()), r -> {
            if (!r.ok) {
                toast(r.error());
                return;
            }
            // OK|本次到账字节|当前余额字节
            toast("签到成功，获得 " + Api.fmtBytes(parseLong(r.part(0))));
            refreshFlow();
        });
    }

    private void refreshAccountUi() {
        boolean in = prefs.logged();
        guestBox.setVisibility(in ? View.GONE : View.VISIBLE);
        loggedBox.setVisibility(in ? View.VISIBLE : View.GONE);
        if (in) {
            accountName.setText(prefs.name() + (prefs.admin() ? "  ★管理" : ""));
            accountVip.setText("VIP 剩余 " + prefs.vipDays() + " 天");
            accountAdmin.setVisibility(prefs.admin() ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshMe() {
        if (!prefs.logged()) return;
        async(() -> api.me(prefs.token()), r -> {
            if (r.ok) {
                // OK|昵称|vip天数|admin=0/1
                prefs.saveMe(r.part(0), parseInt(r.part(1)), "admin=1".equalsIgnoreCase(r.part(2)));
                refreshAccountUi();
            } else if (r.raw.contains("过期")) {
                prefs.logout();
                refreshAccountUi();
            }
        });
    }

    private void refreshFlow() {
        if (!prefs.logged()) return;
        async(() -> api.flow(prefs.token()), r -> {
            if (!r.ok) {
                accountFlow.setText("查询失败");
                signinState.setText(r.error());
                return;
            }
            // OK|余额字节|今日已签到|签到额度|是否VIP
            long balance = parseLong(r.part(0));
            boolean signed = "1".equals(r.part(1));
            long grant = parseLong(r.part(2));
            accountFlow.setText("余额 " + Api.fmtBytes(balance));
            signinState.setText((signed ? " today 已签到" : "今天还没签到")
                    + " · 每次约 " + Api.fmtBytes(grant) + (signed ? "" : "，点上方按钮领取"));
        });
    }

    // ==================== 大厅页 ====================

    private View buildLobbyPage() {
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));

        LinearLayout top = Ui.row(this);
        TextView title = Ui.bold(this, "公开房间", 17, Ui.color(this, R.color.text));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tl);
        top.addView(title);
        Button refreshBtn = Ui.outlineButton(this, "刷新", Ui.color(this, R.color.blue));
        refreshBtn.setMinHeight(Ui.dp(this, 44));
        refreshBtn.setOnClickListener(v -> refreshRooms());
        top.addView(refreshBtn);
        page.addView(top);
        page.addView(Ui.spacer(this, 10));

        roomsInfo = Ui.text(this, "点「刷新」查看当前公开房间", 13, Ui.color(this, R.color.dim));
        page.addView(roomsInfo);
        page.addView(Ui.spacer(this, 8));

        roomsBox = Ui.vertical(this);
        page.addView(roomsBox);

        TextView tip = Ui.text(this, "没有想进的房间？到「开房」页就能自己开一间，开好后把房间码发给朋友。",
                13, Ui.color(this, R.color.dim));
        tip.setPadding(0, Ui.dp(this, 12), 0, 0);
        page.addView(tip);

        return Ui.page(this, page);
    }

    private void refreshRooms() {
        roomsInfo.setText("正在加载…");
        async(() -> api.rooms(), r -> {
            if (!r.ok) {
                roomsInfo.setText("加载失败：" + r.error());
                return;
            }
            int count = parseInt(r.part(0));
            roomsInfo.setText("当前 " + count + " 个公开房间");
            roomsBox.removeAllViews();
            if (count == 0) {
                TextView empty = Ui.center(this, "暂无公开房间", 15, Ui.color(this, R.color.dim));
                empty.setPadding(0, Ui.dp(this, 40), 0, Ui.dp(this, 40));
                roomsBox.addView(empty);
                return;
            }
            for (int i = 1; i < r.parts.length; i++) {
                // code~房主~人数~版本~开房分钟
                String[] f = r.part(i).split("~", -1);
                if (f.length < 5) continue;
                final String code = f[0];
                LinearLayout card = Ui.card(this);
                card.addView(Ui.bold(this, f[1], 16, Ui.color(this, R.color.text)));
                card.addView(Ui.spacer(this, 6));
                card.addView(Ui.text(this, "房间码 " + f[0] + "   " + f[2] + " 人   " + f[4] + " 分钟前",
                        14, Ui.color(this, R.color.muted)));
                card.addView(Ui.text(this, "游戏版本 " + f[3], 13, Ui.color(this, R.color.dim)));
                card.addView(Ui.spacer(this, 10));
                Button join = Ui.button(this, "加入这个房间", Ui.color(this, R.color.blue),
                        Ui.color(this, R.color.bg));
                card.addView(join);
                final String roomName = f[1];
                join.setOnClickListener(v -> {
                    codeField.setText(code);
                    switchTab(3);
                    toast("已填入「" + roomName + "」的房间码，点连接即可");
                });
                roomsBox.addView(card);
            }
        });
    }

    // ==================== 开房页 ====================

    /**
     * 手机开房：把本机端口经 frp 映射到公网。
     *
     * 与电脑端同一套流程：/create 申请房间 → 起 frpc 隧道 → 20 秒心跳续期。
     * 差别只在隧道程序换成了 APK 内置的 libfrpc.so，并且用前台服务保活。
     */
    private View buildHostPage() {
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));

        LinearLayout card = Ui.card(this);
        card.addView(Ui.bold(this, "开一间房", 17, Ui.color(this, R.color.text)));
        card.addView(Ui.spacer(this, 12));

        card.addView(Ui.caption(this, "要开放的端口"));
        hostPortField = Ui.field(this, "25565", false);
        hostPortField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        hostPortField.setText("25565");
        card.addView(hostPortField);

        TextView portTip = Ui.text(this,
                "游戏里点「对局域网开放」后，聊天栏会显示「本地游戏已在端口 XXXXX 上开放」，"
                        + "把那个数字填到这里。开正式服务端的话就是 server.properties 里的端口。",
                12, Ui.color(this, R.color.dim));
        portTip.setPadding(0, 0, 0, Ui.dp(this, 16));
        card.addView(portTip);

        card.addView(Ui.caption(this, "线路（点击切换）"));
        hostNodeRow = Ui.text(this, "正在加载线路…", 16, Ui.color(this, R.color.text));
        hostNodeRow.setMinHeight(Ui.dp(this, 52));
        hostNodeRow.setGravity(Gravity.CENTER_VERTICAL);
        hostNodeRow.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        android.graphics.drawable.GradientDrawable nd =
                new android.graphics.drawable.GradientDrawable();
        nd.setColor(Ui.color(this, R.color.field_bg));
        nd.setCornerRadius(Ui.dp(this, 6));
        nd.setStroke(1, Ui.color(this, R.color.line));
        hostNodeRow.setBackground(nd);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.setMargins(0, 0, 0, Ui.dp(this, 14));
        hostNodeRow.setLayoutParams(nlp);
        hostNodeRow.setOnClickListener(v -> pickNode());
        card.addView(hostNodeRow);

        hostBtn = Ui.button(this, "开房", Ui.color(this, R.color.gold), Ui.color(this, R.color.bg));
        card.addView(hostBtn);
        page.addView(card);

        hostStatus = Ui.text(this,
                "开房需要登录账号。开好之后退到后台也不会断，通知栏会常驻显示房间状态。",
                13, Ui.color(this, R.color.dim));
        hostStatus.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 14));
        page.addView(hostStatus);

        hostResultBox = Ui.card(this);
        hostResultBox.addView(Ui.caption(this, "房间码（发给朋友）"));
        hostRoomCode = Ui.bold(this, "—", 28, Ui.color(this, R.color.green));
        hostRoomCode.setLetterSpacing(0.08f);
        hostResultBox.addView(hostRoomCode);
        hostResultBox.addView(Ui.spacer(this, 14));

        hostResultBox.addView(Ui.caption(this, "朋友连接地址"));
        hostAddr = Ui.text(this, "—", 16, Ui.color(this, R.color.text));
        hostAddr.setTextIsSelectable(true);
        hostResultBox.addView(hostAddr);
        hostResultBox.addView(Ui.spacer(this, 14));

        Button copyHostAddr = Ui.outlineButton(this, "复制地址发给朋友",
                Ui.color(this, R.color.green));
        copyHostAddr.setOnClickListener(v -> {
            String a = hostAddr.getText().toString();
            if (a.isEmpty() || "—".equals(a)) {
                toast("还没有地址");
                return;
            }
            copyText(a);
        });
        hostResultBox.addView(copyHostAddr);
        hostResultBox.addView(Ui.spacer(this, 10));

        Button closeRoomBtn = Ui.outlineButton(this, "关闭房间", Ui.color(this, R.color.red));
        closeRoomBtn.setOnClickListener(v -> doCloseRoom());
        hostResultBox.addView(closeRoomBtn);

        hostResultBox.setVisibility(View.GONE);
        page.addView(hostResultBox);

        hostBtn.setOnClickListener(v -> doHost());

        return Ui.page(this, page);
    }

    /**
     * 把隧道状态回调挂到界面上。放在 onCreate 里注册而不是 doHost 里，
     * 这样从后台回来、Activity 重建之后，隧道断开仍然能第一时间反映到界面。
     */
    private void attachTunnel() {
        Tunnel.get(this).setListener(new Tunnel.Listener() {
            @Override
            public void onTunnelDead(String reason) {
                if (hostStatus == null) return;
                hostStatus.setText("隧道已断开：" + reason + "　请重新开房。");
                if (hostResultBox != null) hostResultBox.setVisibility(View.GONE);
                if (hostBtn != null) {
                    hostBtn.setText("开房");
                    hostBtn.setEnabled(true);
                }
                TunnelService.end(MainActivity.this);
            }

            @Override
            public void onRoomLost(String reason) {
                if (hostStatus == null) return;
                hostStatus.setText(reason + "。已经在玩的朋友不受影响，但新朋友凭房号加不进来。");
            }
        });
    }

    private void loadNodes() {
        async(() -> api.nodes(), r -> {
            if (!r.ok) {
                hostNodeRow.setText("线路加载失败，点这里重试");
                return;
            }
            nodeKeys.clear();
            nodeNames.clear();
            // OK|key~名称~档位~域名|...
            for (int i = 1; i < r.parts.length; i++) {
                String[] f = r.part(i).split("~", -1);
                if (f.length < 2 || f[0].trim().isEmpty()) continue;
                nodeKeys.add(f[0].trim());
                nodeNames.add(f[1].trim());
            }
            if (nodeKeys.isEmpty()) {
                hostNodeRow.setText("没有可用线路，点这里重试");
                return;
            }
            nodesLoaded = true;
            if (nodeIdx >= nodeKeys.size()) nodeIdx = 0;
            hostNodeRow.setText(nodeNames.get(nodeIdx) + "  ▾");
        });
    }

    private void pickNode() {
        if (!nodesLoaded) {
            loadNodes();
            return;
        }
        if (nodeNames.size() <= 1) {
            toast("当前只有一条线路");
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("选择线路")
                .setSingleChoiceItems(nodeNames.toArray(new String[0]), nodeIdx, (d, which) -> {
                    nodeIdx = which;
                    hostNodeRow.setText(nodeNames.get(nodeIdx) + "  ▾");
                    d.dismiss();
                })
                .show();
    }

    private void doHost() {
        if (!prefs.logged()) {
            toast("开房需要先登录账号");
            switchTab(0);
            return;
        }
        final Tunnel t = Tunnel.get(this);
        if (t.isRunning()) {
            toast("已经有房间开着了，先关掉再开");
            return;
        }
        final int port = parseInt(hostPortField.getText().toString().trim());
        if (port <= 0 || port > 65535) {
            toast("端口要填 1-65535 的数字");
            return;
        }
        if (!nodesLoaded || nodeKeys.isEmpty()) {
            toast("线路还没加载好，稍等一下");
            loadNodes();
            return;
        }

        final String node = nodeKeys.get(Math.min(nodeIdx, nodeKeys.size() - 1));
        final String nick = prefs.name() == null || prefs.name().isEmpty() ? "玩家" : prefs.name();

        hostBtn.setEnabled(false);
        hostBtn.setText("正在开房…");
        hostStatus.setText("正在申请房间并启动隧道，大约需要几秒…");

        async(() -> {
            boolean ok = t.start(nick, port, node, prefs.token(), false);
            if (ok) {
                return new Api.Result(true, new String[]{t.roomCode(), t.relayAddr()}, "OK");
            }
            return new Api.Result(false, new String[0], "ERR|" + t.lastError());
        }, r -> {
            if (!r.ok) {
                hostBtn.setEnabled(true);
                hostBtn.setText("开房");
                hostStatus.setText("开房失败：" + r.error());
                toast("开房失败：" + r.error());
                return;
            }
            // 隧道起来了才需要保活；顺序不能反，否则服务会立刻判定「没在跑」自行退出
            TunnelService.begin(MainActivity.this);
            refreshHostUi();
            hostStatus.setText("隧道已建立，可以退到后台。通知栏会显示房间状态。");
            toast("开房成功，房间码 " + r.part(0));
        });
    }

    private void doCloseRoom() {
        Tunnel t = Tunnel.get(this);
        if (!t.isRunning() && t.roomCode().isEmpty()) {
            toast("当前没有开着的房间");
            hostResultBox.setVisibility(View.GONE);
            return;
        }
        t.stopAndClose();
        TunnelService.end(this);
        hostResultBox.setVisibility(View.GONE);
        hostStatus.setText("房间已关闭，朋友将无法再连入。");
        hostBtn.setText("开房");
        hostBtn.setEnabled(true);
        toast("已关房");
    }

    /** 把 Tunnel 的真实状态同步到界面：切回本页、从后台回来都要调。 */
    private void refreshHostUi() {
        Tunnel t = Tunnel.get(this);
        if (t.isRunning()) {
            hostResultBox.setVisibility(View.VISIBLE);
            hostRoomCode.setText(t.roomCode());
            hostAddr.setText(t.relayAddr());
            hostStatus.setText("隧道运行中。退出这个界面也不会断，可随时回来关房。");
            hostBtn.setText("正在开房");
            hostBtn.setEnabled(false);
        } else {
            if (hostResultBox != null) hostResultBox.setVisibility(View.GONE);
            if (hostBtn != null) {
                hostBtn.setText("开房");
                hostBtn.setEnabled(true);
            }
        }
    }

    private void copyText(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("StarryNet", s));
        toast("已复制：" + s);
    }

    // ==================== 加入页 ====================

    private View buildJoinPage() {
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));

        LinearLayout card = Ui.card(this);
        card.addView(Ui.bold(this, "加入房间", 17, Ui.color(this, R.color.text)));
        card.addView(Ui.spacer(this, 12));
        card.addView(Ui.caption(this, "房间码（房主复制给你的那串）"));
        codeField = Ui.field(this, "例如 SN-XXXXXX", false);
        codeField.setSingleLine(true);
        card.addView(codeField);

        card.addView(Ui.caption(this, "你的游戏昵称（选填，房主能看到）"));
        joinNameField = Ui.field(this, "选填", false);
        card.addView(joinNameField);

        Button joinBtn = Ui.button(this, "连接房间", Ui.color(this, R.color.gold),
                Ui.color(this, R.color.bg));
        card.addView(joinBtn);
        page.addView(card);

        joinHint = Ui.text(this,
                "连接后会得到 MC 服务器地址，复制后在游戏里「多人游戏 → 添加服务器」粘贴即可。",
                13, Ui.color(this, R.color.dim));
        joinHint.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        page.addView(joinHint);

        LinearLayout resultCard = Ui.card(this);
        resultCard.addView(Ui.caption(this, "房间地址"));
        joinResult = Ui.text(this, "尚未连接", 15, Ui.color(this, R.color.muted));
        joinResult.setTextIsSelectable(true);
        resultCard.addView(joinResult);
        resultCard.addView(Ui.spacer(this, 12));
        copyBtn = Ui.bold(this, "复制地址", 16, Ui.color(this, R.color.green));
        copyBtn.setGravity(Gravity.CENTER);
        copyBtn.setMinHeight(Ui.dp(this, 52));
        GradientDrawableWorkaround(copyBtn);
        resultCard.addView(copyBtn);
        page.addView(resultCard);

        joinBtn.setOnClickListener(v -> doJoin());
        copyBtn.setOnClickListener(v -> doCopy());

        return Ui.page(this, page);
    }

    /** 给「复制地址」文字按钮加一个可点击的描边背景。 */
    private void GradientDrawableWorkaround(View v) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(Ui.color(this, R.color.card_on));
        d.setCornerRadius(Ui.dp(this, 8));
        d.setStroke(2, Ui.color(this, R.color.green));
        v.setBackground(d);
    }

    private String lastAddress = "";

    private void doJoin() {
        String code = codeField.getText().toString().trim();
        if (code.isEmpty()) {
            toast("请输入房间码");
            return;
        }
        String name = joinNameField.getText().toString().trim();
        String token = prefs.logged() ? prefs.token() : "";
        toast("正在连接…");
        async(() -> api.join(code, token), r -> {
            if (!r.ok) {
                joinResult.setText("连接失败：" + r.error());
                joinResult.setTextColor(Ui.color(this, R.color.red));
                return;
            }
            // OK|房主昵称|域名|端口|MC版本
            String host = r.part(1);
            String port = r.part(2);
            String ver = r.part(3);
            lastAddress = host + ":" + port;
            joinResult.setText(lastAddress + "\n房主 " + r.part(0) + " · 版本 " + ver);
            joinResult.setTextColor(Ui.color(this, R.color.text));
            toast("连接成功，已获取房间地址");
        });
    }

    private void doCopy() {
        if (lastAddress.isEmpty()) {
            toast("请先连接房间获取地址");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("MC服务器地址", lastAddress));
            toast("已复制：" + lastAddress);
        }
    }

    // ==================== 我的页 ====================

    private View buildMinePage() {
        LinearLayout page = Ui.vertical(this);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 24));

        // 卡密兑换
        LinearLayout redeemCard = Ui.card(this);
        redeemCard.addView(Ui.bold(this, "卡密兑换", 17, Ui.color(this, R.color.text)));
        redeemCard.addView(Ui.spacer(this, 10));
        redeemField = Ui.field(this, "输入卡密", false);
        redeemCard.addView(redeemField);
        Button redeemBtn = Ui.button(this, "兑换 VIP", Ui.color(this, R.color.gold),
                Ui.color(this, R.color.bg));
        redeemCard.addView(redeemBtn);
        redeemResult = Ui.text(this, "", 13, Ui.color(this, R.color.muted));
        redeemCard.addView(redeemResult);
        page.addView(redeemCard);
        redeemBtn.setOnClickListener(v -> doRedeem());

        // 订单认领
        LinearLayout claimCard = Ui.card(this);
        claimCard.addView(Ui.bold(this, "订单认领", 17, Ui.color(this, R.color.text)));
        claimCard.addView(Ui.spacer(this, 6));
        claimCard.addView(Ui.text(this, "爱发电付款后，把订单号填这里补到账", 13, Ui.color(this, R.color.muted)));
        claimCard.addView(Ui.spacer(this, 10));
        claimField = Ui.field(this, "爱发电订单号", false);
        claimCard.addView(claimField);
        Button claimBtn = Ui.outlineButton(this, "认领订单", Ui.color(this, R.color.blue));
        claimCard.addView(claimBtn);
        page.addView(claimCard);
        claimBtn.setOnClickListener(v -> doClaim());

        // 购买套餐
        LinearLayout buyCard = Ui.card(this);
        buyCard.addView(Ui.bold(this, "购买 VIP", 17, Ui.color(this, R.color.text)));
        buyCard.addView(Ui.spacer(this, 6));
        buyCard.addView(Ui.text(this, "付款走爱发电，下单后在上面认领订单", 13, Ui.color(this, R.color.muted)));
        buyCard.addView(Ui.spacer(this, 12));
        int[] days = {1, 30, 90, 365};
        String[] labels = {"体验 1天 ¥1.99", "月卡 30天 ¥15", "季卡 90天 ¥43", "年卡 365天 ¥170"};
        for (int i = 0; i < days.length; i++) {
            final int d = days[i];
            Button b = Ui.outlineButton(this, labels[i], Ui.color(this, R.color.gold));
            b.setOnClickListener(v -> openBuy(d));
            buyCard.addView(b);
            buyCard.addView(Ui.spacer(this, 8));
        }
        page.addView(buyCard);

        // 公告
        LinearLayout noticeCard = Ui.card(this);
        noticeCard.addView(Ui.bold(this, "公告", 17, Ui.color(this, R.color.text)));
        noticeCard.addView(Ui.spacer(this, 8));
        noticeBox = Ui.vertical(this);
        noticeCard.addView(noticeBox);
        page.addView(noticeCard);

        // 退出登录
        Button logoutBtn = Ui.outlineButton(this, "退出登录", Ui.color(this, R.color.red));
        page.addView(logoutBtn);
        logoutBtn.setOnClickListener(v -> {
            prefs.logout();
            refreshAccountUi();
            toast("已退出登录");
            switchTab(0);
        });

        TextView foot = Ui.center(this, "StarryNet 手机版 v" + APP_VERSION + " · starrynet.top",
                12, Ui.color(this, R.color.dim));
        foot.setPadding(0, Ui.dp(this, 16), 0, 0);
        page.addView(foot);

        return Ui.page(this, page);
    }

    private LinearLayout noticeBox;

    private void doRedeem() {
        if (!needLogin()) return;
        String key = redeemField.getText().toString().trim();
        if (key.isEmpty()) { toast("请输入卡密"); return; }
        toast("正在兑换…");
        async(() -> api.redeem(prefs.token(), key), r -> {
            if (!r.ok) {
                redeemResult.setText(r.error());
                redeemResult.setTextColor(Ui.color(this, R.color.red));
                return;
            }
            int days = parseInt(r.part(0));
            prefs.saveVip(days);
            redeemResult.setText("兑换成功，VIP 剩余 " + days + " 天");
            redeemResult.setTextColor(Ui.color(this, R.color.green));
            refreshAccountUi();
        });
    }

    private void doClaim() {
        if (!needLogin()) return;
        String order = claimField.getText().toString().trim();
        if (order.isEmpty()) { toast("请输入订单号"); return; }
        toast("正在查单…");
        async(() -> api.claim(prefs.token(), order), r -> toast(r.ok ? r.raw.replaceFirst("OK\\|", "") : r.error()));
    }

    private void openBuy(int days) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(api.buyUrl(days))));
        } catch (Exception e) {
            toast("无法打开浏览器");
        }
    }

    private void loadNotices() {
        async(() -> api.notices(0), r -> {
            if (noticeBox == null || !r.ok) return;
            noticeBox.removeAllViews();
            int shown = 0;
            for (int i = 0; i < r.parts.length && shown < 5; i++, shown++) {
                // id~标题~正文~级别~时间
                String[] f = r.part(i).split("~", -1);
                if (f.length < 3) continue;
                TextView t = Ui.text(this, "• " + f[1] + "\n  " + f[2], 14,
                        Ui.color(this, R.color.text));
                t.setPadding(0, 0, 0, Ui.dp(this, 10));
                noticeBox.addView(t);
            }
            if (shown == 0) {
                noticeBox.addView(Ui.text(this, "暂无公告", 14, Ui.color(this, R.color.dim)));
            }
        });
    }

    // ==================== 通用 ====================

    private void switchTab(int idx) {
        currentTab = idx;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(Ui.color(this, i == idx ? R.color.green : R.color.muted));
        }
        if (idx == 1) {
            refreshRooms();
        }
        if (idx == 2) {
            // 线路表只在第一次进页面时拉一次；房间状态每次都跟 Tunnel 单例对齐，
            // 否则从别的标签切回来会看到过期信息
            if (!nodesLoaded) loadNodes();
            refreshHostUi();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从后台回来时隧道可能已经被系统掐了，界面要跟着变
        if (pages != null && currentTab == 2) {
            refreshHostUi();
        }
    }

    @Override
    protected void onDestroy() {
        // 隧道本身由前台服务托着，Activity 销毁不该停它；只解掉回调避免泄漏
        Tunnel.get(this).setListener(null);
        super.onDestroy();
    }

    private boolean needLogin() {
        if (!prefs.logged()) {
            toast("请先到「账号」页登录");
            switchTab(0);
            return false;
        }
        return true;
    }

    /** 后台跑网络请求，结果回主线程更新 UI。 */
    private void async(NetCall call, UiCallback cb) {
        new Thread(() -> {
            Api.Result r;
            try {
                r = call.run();
            } catch (Exception e) {
                r = new Api.Result(false, new String[0], "ERR|" + e.getMessage());
            }
            final Api.Result rr = r;
            runOnUiThread(() -> {
                try { cb.onResult(rr); } catch (Exception ignored) {}
            });
        }, "net").start();
    }

    private interface NetCall { Api.Result run(); }
    private interface UiCallback { void onResult(Api.Result r); }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
}
