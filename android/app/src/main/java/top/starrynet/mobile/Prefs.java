package top.starrynet.mobile;

import android.content.Context;
import android.content.SharedPreferences;

/** 本地账号与偏好存储。token 存在应用私有目录，其他 App 读不到。 */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("starrynet", Context.MODE_PRIVATE);
    }

    String token() { return sp.getString("token", ""); }

    String name() { return sp.getString("name", ""); }

    int vipDays() { return sp.getInt("vip", 0); }

    boolean admin() { return sp.getBoolean("admin", false); }

    boolean logged() { return !token().isEmpty(); }

    void saveLogin(String token, String name, int vip, boolean admin) {
        sp.edit().putString("token", token).putString("name", name)
                .putInt("vip", vip).putBoolean("admin", admin).apply();
    }

    void saveMe(String name, int vip, boolean admin) {
        sp.edit().putString("name", name).putInt("vip", vip)
                .putBoolean("admin", admin).apply();
    }

    void saveVip(int vip) { sp.edit().putInt("vip", vip).apply(); }

    void logout() {
        sp.edit().remove("token").remove("name").remove("vip").remove("admin").apply();
    }

    long lastNoticeId() { return sp.getLong("notice_id", 0); }

    void saveNoticeId(long id) { sp.edit().putLong("notice_id", id).apply(); }
}
