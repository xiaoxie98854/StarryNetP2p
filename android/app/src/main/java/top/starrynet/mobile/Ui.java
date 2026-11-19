package top.starrynet.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 原生控件工厂：统一深色配色 + 手机友好的大触控目标。零第三方依赖。 */
final class Ui {

    private Ui() {}

    /** 预留入口：当前所有方法都直接接收 Context，这里保留以兼容调用方。 */
    static void init(Context c) {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static int color(Context c, int res) {
        return c.getResources().getColor(res, c.getTheme());
    }

    static TextView text(Context c, String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        return t;
    }

    static TextView bold(Context c, String s, float size, int color) {
        TextView t = text(c, s, size, color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView center(Context c, String s, float size, int color) {
        TextView t = text(c, s, size, color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** 卡片：深色底 + 1px 实线边，圆角 8dp。 */
    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color(c, R.color.card));
        gd.setCornerRadius(dp(c, 8));
        gd.setStroke(1, color(c, R.color.line));
        l.setBackground(gd);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.MarginLayoutParams mp = new LinearLayout.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.setMargins(0, 0, 0, dp(c, 12));
        l.setLayoutParams(mp);
        return l;
    }

    static TextView caption(Context c, String s) {
        TextView t = text(c, s, 13, color(c, R.color.muted));
        t.setPadding(0, 0, 0, dp(c, 6));
        return t;
    }

    /** 大按钮：最小高度 52dp，圆角填充，按下有反馈。 */
    static Button button(Context c, String label, int fillColor, int textColor) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setCornerRadius(dp(c, 8));
        b.setBackground(d);
        return b;
    }

    /** 描边按钮（次要操作），按下有淡色反馈。 */
    static Button outlineButton(Context c, String label, int strokeColor) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(strokeColor);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setCornerRadius(dp(c, 8));
        normal.setStroke(2, strokeColor);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(colorWithAlpha(strokeColor, 40));
        pressed.setCornerRadius(dp(c, 8));
        pressed.setStroke(2, strokeColor);
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sd.addState(new int[]{}, normal);
        b.setBackground(sd);
        return b;
    }

    /** 输入框：48dp 高，深色底，1px 边框。 */
    static EditText field(Context c, String hint, boolean password) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(color(c, R.color.dim));
        e.setTextColor(color(c, R.color.text));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setMinHeight(dp(c, 48));
        e.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        e.setSingleLine(true);
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color(c, R.color.field_bg));
        gd.setCornerRadius(dp(c, 6));
        gd.setStroke(1, color(c, R.color.line));
        e.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 10));
        e.setLayoutParams(lp);
        return e;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** 等权重的水平按钮组。 */
    static void equalWidth(ViewGroup group, android.view.View... views) {
        for (android.view.View v : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            v.setLayoutParams(lp);
        }
    }

    static void marginLeft(android.view.View v, Context c, int dpValue) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(dp(c, dpValue), 0, 0, 0);
        }
    }

    static void marginRight(android.view.View v, Context c, int dpValue) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, dp(c, dpValue), 0);
        }
    }

    /** 整页滚动容器，小屏也能滑到底部按钮。 */
    static ScrollView page(Context c, android.view.View child) {
        ScrollView s = new ScrollView(c);
        s.setFillViewport(true);
        s.setBackgroundColor(color(c, R.color.bg));
        s.addView(child, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    static android.view.View spacer(Context c, int dpHeight) {
        android.view.View v = new android.view.View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, dpHeight)));
        return v;
    }

    private static int colorWithAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
