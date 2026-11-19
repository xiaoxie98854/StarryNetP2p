package top.starrynet.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * 隧道保活的前台服务。
 *
 * Android 8 起后台进程随时会被回收，隧道一旦被杀房间就废了。挂一个前台服务
 * （通知栏常驻「联机中 · 房间码 4821」）是唯一能在系统层面说明"这个进程别杀"的方式。
 *
 * 服务本身不管理隧道生命周期 —— 隧道是 Tunnel 的单例，这里只负责
 * ① 把进程拉到前台优先级 ② 在通知栏显示当前房间状态，方便用户随时确认还在联机。
 */
public class TunnelService extends Service {

    private static final String CHANNEL_ID = "starrynet_tunnel";
    private static final String CHANNEL_NAME = "联机状态";
    private static final int NOTIF_ID = 1001;

    /** 启动保活（开房成功后调用）。Android 8+ 必须走 startForegroundService。 */
    static void begin(Context c) {
        Intent i = new Intent(c, TunnelService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    /** 结束保活（关房后调用）。 */
    static void end(Context c) {
        c.stopService(new Intent(c, TunnelService.class));
    }

    /** 房间码或地址变化时刷新通知栏文字。 */
    static void refresh(Context c) {
        try {
            c.startService(new Intent(c, TunnelService.class));
        } catch (Exception ignored) {
            // 服务没在跑就算了，下次开房会重新拉起
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        // 隧道已经不在跑了（进程被杀后服务被系统重启）：没必要占着通知栏
        Tunnel t = Tunnel.get(this);
        if (!t.isRunning()) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 服务没了隧道也留着没意义，一并清掉，避免留下僵尸房间
        Tunnel t = Tunnel.get(this);
        if (t.isRunning()) {
            t.stopAndClose();
        }
    }

    private Notification buildNotification() {
        Tunnel t = Tunnel.get(this);
        String code = t.roomCode();
        String addr = t.relayAddr();

        String title = code.isEmpty() ? "StarryNet 联机中" : "联机中 · 房间 " + code;
        String text = addr.isEmpty() ? "隧道正在建立…" : "朋友连接 " + addr;

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setShowWhen(false);

        if (Build.VERSION.SDK_INT >= 21) {
            b.setPriority(Notification.PRIORITY_LOW);
        }
        return b.build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("开房期间保持隧道不被系统回收");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }
}
