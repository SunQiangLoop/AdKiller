package cn.local.adskip;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Native grouped settings layout, matching the supplied iOS-style prototype. */
final class HomeView extends ScrollView {
    // sRGB equivalents of the prototype's OKLCH design tokens.
    static final int CANVAS = Color.rgb(240, 244, 247);
    private static final int INK = Color.rgb(21, 35, 27), MUTED = Color.rgb(96, 108, 101);
    private static final int GREEN = Color.rgb(0, 109, 77), DEEP = Color.rgb(0, 68, 47);
    private static final int BLUE = Color.rgb(24, 121, 210), ORANGE = Color.rgb(220, 125, 0);
    private static final int GRAY = Color.rgb(131, 138, 146), LINE = Color.rgb(222, 226, 224);
    final Toggle master;
    private final TextView status, serviceDetail, rulesDetail;
    private final ImageView signal;
    private final LinearLayout group;
    private final boolean compact;

    HomeView(Context context, String appName, Runnable service, Runnable rules, Runnable settings) {
        super(context);
        compact = getResources().getConfiguration().screenWidthDp < 350;
        setFillViewport(true); setBackgroundColor(CANVAS); setVerticalScrollBarEnabled(false);
        LinearLayout root = column(); root.setPadding(dp(compact ? 14 : 18), dp(14), dp(compact ? 14 : 18), dp(28));
        addView(root, new ScrollView.LayoutParams(-1, -2));
        LinearLayout topline = row();
        ImageView shield = new ImageView(context); shield.setImageResource(R.drawable.home_shield); shield.setColorFilter(MUTED);
        topline.addView(shield, new LinearLayout.LayoutParams(dp(14), dp(14)));
        status = text("检查服务状态", 12, MUTED); TextView local=status; local.setPadding(dp(6), 0, 0, 0);
        topline.addView(local, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(topline); gap(root, 18);

        LinearLayout brand = row(); LinearLayout heading = column();
        TextView title = text(appName, 42, DEEP); title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); title.setLetterSpacing(-.035f);
        heading.addView(title); gap(heading, 6); heading.addView(text("打开应用，少等一步。", 15, MUTED));
        brand.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        signal = new ImageView(context); signal.setImageResource(R.drawable.home_bolt); signal.setPadding(dp(12), dp(12), dp(12), dp(12));
        signal.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams mark = new LinearLayout.LayoutParams(dp(50), dp(50)); mark.leftMargin = dp(12); brand.addView(signal, mark);
        root.addView(brand); gap(root, 24);
        TextView section = text("开屏自动化", 12, MUTED); section.setPadding(dp(15), 0, 0, 0); root.addView(section); gap(root, 8);
        group = column(); group.setBackground(shape(Color.WHITE, 20)); group.setClipToOutline(true); group.setElevation(dp(1)); root.addView(group);
        serviceDetail = tool("无障碍服务", "检查服务状态", R.drawable.home_person, GREEN, service);
        master = setting("自动跳过", "为所有应用识别开屏广告", R.drawable.home_auto, GREEN);
        rulesDetail = tool("规则管理", "查看专用规则，管理导入与导出", R.drawable.home_rules, GREEN, rules);
        tool("设置", "运行记录、诊断与设备信息", R.drawable.home_settings, GRAY, settings);
        gap(root, 16);
        TextView privacy = text("无需 root · 无需联网", 11, DEEP); privacy.setGravity(Gravity.CENTER); root.addView(privacy); gap(root, 3);
        TextView note = text("截图与诊断日志仅保存在本机，可通过 USB 收集到电脑。", 11, MUTED);
        note.setGravity(Gravity.CENTER); note.setPadding(dp(13), 0, dp(13), 0); root.addView(note);
    }

    void update(boolean permission, boolean connected, boolean enabled, int observations, int ruleCount) {
        boolean active = permission && connected && enabled;
        status.setText(!permission ? "服务未开启" : !connected ? "服务未连接" : !enabled ? "自动跳过已暂停" : "服务运行中");
        serviceDetail.setText(!permission ? "点击前往系统设置开启" : !connected ? "尚未连接，请重新开启服务" : !enabled ? "已连接，自动跳过已暂停" : "已连接，自动识别所有应用");
        rulesDetail.setText(ruleCount + " 款专用规则，其余应用通用识别");
        signal.setBackground(shape(active ? GREEN : GRAY, 25));
        signal.setAlpha(active ? 1f : .65f);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(getContext()); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(getContext()); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(getContext()); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(false); v.setLineSpacing(dp(2), 1); return v;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable v = new GradientDrawable(); v.setColor(color); v.setCornerRadius(dp(radius)); return v; }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(getContext()), new LinearLayout.LayoutParams(1, dp(height))); }
    private LinearLayout item(int icon, int color) {
        if (group.getChildCount() > 0) {
            View line = new View(getContext()); line.setBackgroundColor(LINE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))); lp.leftMargin = dp(59); group.addView(line, lp);
        }
        LinearLayout row = row(); row.setMinimumHeight(dp(64)); row.setPadding(dp(14), dp(11), dp(14), dp(11));
        ImageView image = new ImageView(getContext()); image.setImageResource(icon); image.setBackground(shape(color, 8)); image.setPadding(dp(6), dp(6), dp(6), dp(6));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(30), dp(30)); ip.rightMargin = dp(15); row.addView(image, ip);
        group.addView(row, new LinearLayout.LayoutParams(-1, -2)); return row;
    }
    private TextView copy(LinearLayout row, String title, String subtitle) {
        LinearLayout copy = column(); TextView heading = text(title, compact ? 14 : 15, INK);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); copy.addView(heading); gap(copy, 3);
        TextView detail = text(subtitle, 11, MUTED); copy.addView(detail);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.rightMargin = dp(11); row.addView(copy, lp); return detail;
    }
    private TextView tool(String title, String subtitle, int icon, int color, Runnable action) {
        LinearLayout row = item(icon, color); TextView detail = copy(row, title, subtitle);
        ImageView chevron = new ImageView(getContext()); chevron.setImageResource(R.drawable.home_chevron); chevron.setColorFilter(GRAY);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); row.addView(chevron, new LinearLayout.LayoutParams(dp(16), dp(16)));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x16246B57), null, shape(Color.WHITE, 0)));
        row.setFocusable(true); row.setOnClickListener(v -> action.run()); return detail;
    }
    private Toggle setting(String title, String subtitle, int icon, int color) {
        LinearLayout row = item(icon, color); copy(row, title, subtitle);
        Toggle control = new Toggle(getContext()); control.setContentDescription(title + "，" + subtitle);
        row.addView(control, new LinearLayout.LayoutParams(dp(51), dp(48)));
        row.setOnClickListener(v -> control.toggle());
        // The switch supplies the entire accessible label; avoid duplicate row actions.
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.getChildAt(1).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return control;
    }

    /** CompoundButton keeps native checked-state, keyboard and TalkBack semantics. */
    static final class Toggle extends CompoundButton {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Toggle(Context context) { super(context); setButtonDrawable(null); setBackground(null); setPadding(0, 0, 0, 0); setFocusable(true); }
        @Override public CharSequence getAccessibilityClassName() { return "android.widget.Switch"; }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.Switch"); info.setCheckable(true); info.setChecked(isChecked());
        }
        @Override public void setChecked(boolean checked) { super.setChecked(checked); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float unit = getWidth() / 51f, top = (getHeight() - 31 * unit) / 2;
            paint.setColor(isChecked() ? GREEN : Color.rgb(207, 213, 209));
            canvas.drawRoundRect(0, top, getWidth(), top + 31 * unit, 16 * unit, 16 * unit, paint);
            boolean right = isChecked() != (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
            float center = (right ? 35.5f : 15.5f) * unit;
            paint.setColor(0x18000000); canvas.drawCircle(center, top + 16.5f * unit, 13.5f * unit, paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(center, top + 15.5f * unit, 13.5f * unit, paint);
            if (isFocused()) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * unit); paint.setColor(BLUE);
                canvas.drawRoundRect(unit, top + unit, getWidth() - unit, top + 30 * unit, 15 * unit, 15 * unit, paint); paint.setStyle(Paint.Style.FILL);
            }
        }
    }
}
