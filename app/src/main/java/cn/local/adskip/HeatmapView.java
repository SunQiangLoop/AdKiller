package cn.local.adskip;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Visual interactive heatmap display showing AI-learned skip coordinates,
 * hit stats, and simulated smartphone screen canvas with radial heat glows.
 */
final class HeatmapView extends ScrollView {
    private static final int INK = Color.rgb(21, 35, 27);
    private static final int MUTED = Color.rgb(96, 108, 101);
    private static final int GREEN = Color.rgb(0, 109, 77);
    private static final int DEEP = Color.rgb(0, 68, 47);

    private final HotspotStore store;
    private final Runnable backAction;
    private final LinearLayout root;
    private final TextView statApps, statHits, statSpeed, statDays;
    private final LinearLayout chipRow;
    private final PhoneCanvas canvas;
    private final TextView detailAppName, detailPkg, detailCoords, detailHits, detailSource, detailTime;
    private final LinearLayout detailCard;
    private String selectedPkg = null;

    HeatmapView(Context context, HotspotStore store, Runnable back) {
        super(context);
        this.store = store;
        this.backAction = back;
        setFillViewport(true);
        setBackgroundColor(HomeView.CANVAS);
        setVerticalScrollBarEnabled(false);

        root = column();
        root.setPadding(dp(18), dp(8), dp(18), dp(28));
        addView(root, new ScrollView.LayoutParams(-1, -2));

        // 1. Back button & Title header
        TextView returnButton = text("‹  返回设置", 15, GREEN);
        returnButton.setGravity(Gravity.CENTER_VERTICAL);
        returnButton.setMinHeight(dp(48));
        returnButton.setPadding(dp(4), 0, dp(12), 0);
        returnButton.setContentDescription("返回设置");
        returnButton.setOnClickListener(v -> backAction.run());
        root.addView(returnButton);

        TextView title = text("AI 跳过热力图", 30, INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(4), dp(8), 0, dp(4));
        root.addView(title);

        TextView subtitle = text("历史识别位置 · 小目标需复核 · 记忆保留 180 天", 13, MUTED);
        subtitle.setPadding(dp(4), 0, 0, dp(14));
        root.addView(subtitle);

        // 2. Overview Stats Card
        LinearLayout statsCard = column();
        statsCard.setBackground(shape(Color.WHITE, 18));
        statsCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        statsCard.setElevation(dp(1));

        LinearLayout r1 = row();
        LinearLayout b1 = statBox("学习应用", "0 款", GREEN); statApps = (TextView) b1.getChildAt(1);
        LinearLayout b2 = statBox("累计热点记录", "0 次", Color.rgb(220, 80, 20)); statHits = (TextView) b2.getChildAt(1);
        r1.addView(b1, new LinearLayout.LayoutParams(0, -2, 1));
        r1.addView(b2, new LinearLayout.LayoutParams(0, -2, 1));
        statsCard.addView(r1);

        View div = new View(context); div.setBackgroundColor(Color.rgb(240, 244, 247));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(1));
        dlp.topMargin = dp(12); dlp.bottomMargin = dp(12); statsCard.addView(div, dlp);

        LinearLayout r2 = row();
        LinearLayout b3 = statBox("实际响应耗时", "以诊断日志为准", Color.rgb(24, 121, 210)); statSpeed = (TextView) b3.getChildAt(1);
        statSpeed.setTextSize(14);
        LinearLayout b4 = statBox("记忆有效周期", "180 天", DEEP); statDays = (TextView) b4.getChildAt(1);
        r2.addView(b3, new LinearLayout.LayoutParams(0, -2, 1));
        r2.addView(b4, new LinearLayout.LayoutParams(0, -2, 1));
        statsCard.addView(r2);

        root.addView(statsCard);
        gap(root, 18);

        // 3. Section: App selector chips
        TextView chipHeader = text("选择查看应用", 12, MUTED);
        chipHeader.setPadding(dp(4), 0, 0, dp(8));
        root.addView(chipHeader);

        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = row();
        chipRow.setPadding(dp(2), dp(2), dp(2), dp(2));
        chipScroll.addView(chipRow, new HorizontalScrollView.LayoutParams(-2, -2));
        root.addView(chipScroll);
        gap(root, 16);

        // 4. Smartphone Canvas displaying the Heatmap
        LinearLayout canvasWrapper = column();
        canvasWrapper.setGravity(Gravity.CENTER);
        canvasWrapper.setBackground(shape(Color.rgb(15, 23, 42), 24)); // Dark slate frame
        canvasWrapper.setPadding(dp(16), dp(16), dp(16), dp(16));
        canvasWrapper.setElevation(dp(2));

        canvas = new PhoneCanvas(context);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(220), dp(440));
        canvasWrapper.addView(canvas, clp);
        root.addView(canvasWrapper);
        gap(root, 16);

        // 5. Details Card
        detailCard = column();
        detailCard.setBackground(shape(Color.WHITE, 18));
        detailCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        detailCard.setElevation(dp(1));

        detailAppName = text("选择应用查看热点", 16, INK);
        detailAppName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        detailCard.addView(detailAppName);

        detailPkg = text("", 11, MUTED); detailPkg.setPadding(0, dp(2), 0, dp(10)); detailCard.addView(detailPkg);

        detailCoords = detailRow(detailCard, "🎯 记忆热区坐标", "-");
        detailHits = detailRow(detailCard, "🔥 历史记录次数", "-");
        detailSource = detailRow(detailCard, "⚡ 识别匹配渠道", "-");
        detailTime = detailRow(detailCard, "🕒 最近命中时间", "-");

        root.addView(detailCard);
        gap(root, 16);

        // 6. Action buttons (Reset current app / Clear all)
        LinearLayout actions = row();
        TextView btnReset = actionButton("管理当前热点", Color.rgb(240, 244, 247), INK, () -> resetCurrentApp());
        TextView btnClearAll = actionButton("🗑️ 清空所有热点", Color.rgb(254, 242, 242), Color.rgb(185, 28, 28), () -> clearAllApps());
        actions.addView(btnReset, new LinearLayout.LayoutParams(0, -2, 1));
        View space = new View(context);
        actions.addView(space, new LinearLayout.LayoutParams(dp(12), dp(1)));
        actions.addView(btnClearAll, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);

        refreshData();
    }

    void refreshData() {
        store.reload();
        List<String> pkgs = store.getAllAppPackages();
        int appCount = pkgs.size();
        int hitCount = store.getTotalHitsCount();

        statApps.setText(appCount + " 款");
        statHits.setText(hitCount + " 次");

        chipRow.removeAllViews();
        if (pkgs.isEmpty()) {
            selectedPkg = null;
            chipRow.addView(text("暂无已学习的应用", 13, MUTED));
            canvas.setHotspots(null, "暂无已学习的应用\n开启服务并跳过广告后自动记录");
            detailAppName.setText("暂无热点记忆");
            detailPkg.setText("打开包含开屏广告的 App 后将自动学习");
            detailCoords.setText("-");
            detailHits.setText("-");
            detailSource.setText("-");
            detailTime.setText("-");
            return;
        }

        if (selectedPkg == null || !pkgs.contains(selectedPkg)) {
            selectedPkg = pkgs.get(0);
        }

        for (String pkg : pkgs) {
            String label = getAppLabel(pkg);
            List<Hotspot> list = store.getHotspots(pkg);
            int hits = 0; for (Hotspot h : list) hits += h.hitCount;
            boolean selected = pkg.equals(selectedPkg);

            TextView chip = new TextView(getContext());
            chip.setText(label + " (" + hits + ")");
            chip.setTextSize(12);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(shape(selected ? GREEN : Color.WHITE, 16));
            chip.setTextColor(selected ? Color.WHITE : INK);
            chip.setTypeface(Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            chip.setOnClickListener(v -> {
                selectedPkg = pkg;
                refreshData();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            chipRow.addView(chip, lp);
        }

        // Update selected app details
        String label = getAppLabel(selectedPkg);
        List<Hotspot> list = store.getHotspots(selectedPkg);
        detailAppName.setText(label);
        detailPkg.setText(selectedPkg);

        if (!list.isEmpty()) {
            Hotspot best = list.get(0);
            canvas.setHotspots(list, label);
            detailCoords.setText("Rect(" + best.left + ", " + best.top + ", " + best.right + ", " + best.bottom + ") · " + best.width() + "×" + best.height() + "px");
            detailHits.setText("历史记录 " + best.hitCount + " 次 · " + (best.predictive()?"用于预测识别":"小目标待复核"));
            detailSource.setText(best.source.equals("rectangle_ocr") ? "框选区域 OCR" : best.source.equals("ocr") ? "截图 OCR" : best.source.equals("text") ? "无障碍节点文字" : "图像模板匹配");
            if (best.lastSeen > 0) {
                detailTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(best.lastSeen)));
            } else {
                detailTime.setText("已生效");
            }
        }
    }

    private void resetCurrentApp() {
        if (selectedPkg == null) return;
        final String pkg=selectedPkg;
        String label = getAppLabel(pkg);
        List<Hotspot> list=store.getHotspots(pkg);
        String[] choices=new String[list.size()];
        for(int i=0;i<list.size();i++){
            Hotspot h=list.get(i);
            choices[i]="("+h.left+", "+h.top+")～("+h.right+", "+h.bottom+") · "+h.hitCount+" 次"+(h.predictive()?"":" · 待复核");
        }
        new AlertDialog.Builder(getContext())
            .setTitle("管理「" + label + "」的热点")
            .setItems(choices,(d,index)->new AlertDialog.Builder(getContext())
                .setTitle("删除这条热点记录？").setMessage(choices[index]+"\n删除后仍会通过 OCR 查找按钮。")
                .setPositiveButton("删除",(dialog,which)->{
                    boolean removed=store.remove(pkg,list.get(index));refreshData();
                    if(!removed)android.widget.Toast.makeText(getContext(),"热点已有新记录，请重新选择",android.widget.Toast.LENGTH_SHORT).show();
                }).setNegativeButton("取消",null).show())
            .setPositiveButton("清空该应用", (d, w) -> {
                new AlertDialog.Builder(getContext()).setTitle("清空「"+label+"」的全部热点？")
                    .setPositiveButton("清空",(dialog,which)->{store.clear(pkg);refreshData();})
                    .setNegativeButton("取消",null).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void clearAllApps() {
        new AlertDialog.Builder(getContext())
            .setTitle("清空全部 AI 热力图数据？")
            .setMessage("将清除所有已记录应用的跳过按钮坐标与命中统计。")
            .setPositiveButton("全部清空", (d, w) -> {
                store.clearAll();
                refreshData();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private String getAppLabel(String pkg) {
        PackageManager pm = getContext().getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private LinearLayout statBox(String label, String value, int valColor) {
        LinearLayout b = column();
        TextView v = text(label, 11, MUTED);
        TextView num = text(value, 20, valColor);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        num.setPadding(0, dp(4), 0, 0);
        b.addView(v);
        b.addView(num);
        return b;
    }

    private TextView detailRow(LinearLayout parent, String title, String val) {
        LinearLayout r = row();
        r.setPadding(0, dp(5), 0, dp(5));
        TextView t = text(title, 12, MUTED);
        TextView v = text(val, 12, INK);
        v.setGravity(Gravity.RIGHT);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(v, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(r);
        return v;
    }

    private TextView actionButton(String text, int bg, int textColor, Runnable action) {
        TextView btn = new TextView(getContext());
        btn.setText(text);
        btn.setTextSize(13);
        btn.setTextColor(textColor);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(14), dp(12), dp(14), dp(12));
        btn.setBackground(shape(bg, 14));
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(getContext()); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(getContext()); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String s, int size, int color) {
        TextView v = new TextView(getContext()); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(false); return v;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void gap(LinearLayout parent, int dps) {
        View v = new View(getContext()); parent.addView(v, new LinearLayout.LayoutParams(-1, dp(dps)));
    }

    /**
     * Simulated smartphone canvas with OLED screen, punch hole, and radiant heatmap glow.
     */
    static final class PhoneCanvas extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<Hotspot> hotspots;
        private String appTitle = "";

        PhoneCanvas(Context context) {
            super(context);
        }

        void setHotspots(List<Hotspot> list, String title) {
            this.hotspots = list;
            this.appTitle = title;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            // 1. Draw Phone Outer Chassis (Metallic titanium edge)
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(30, 41, 59)); // Slate border
            RectF chassis = new RectF(0, 0, w, h);
            canvas.drawRoundRect(chassis, 24, 24, paint);

            // 2. Draw Screen Glass Area (OLED black with subtle gradient)
            int border = 8;
            RectF screen = new RectF(border, border, w - border, h - border);
            paint.setColor(Color.rgb(10, 15, 29)); // Deep dark OLED blue-black
            canvas.drawRoundRect(screen, 18, 18, paint);

            // 3. Draw subtle grid lines on screen
            paint.setColor(Color.argb(20, 255, 255, 255));
            paint.setStrokeWidth(1);
            for (float y = screen.top + 30; y < screen.bottom - 20; y += 40) {
                canvas.drawLine(screen.left, y, screen.right, y, paint);
            }
            for (float x = screen.left + 30; x < screen.right - 20; x += 40) {
                canvas.drawLine(x, screen.top, x, screen.bottom, paint);
            }

            // 4. Draw Top Camera Punch-hole (Dynamic Island pill)
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);
            float pillW = 26, pillH = 10;
            RectF camera = new RectF(w / 2f - pillW / 2f, border + 8, w / 2f + pillW / 2f, border + 8 + pillH);
            canvas.drawRoundRect(camera, 5, 5, paint);

            // 5. Draw Bottom Home Indicator Bar
            paint.setColor(Color.argb(90, 255, 255, 255));
            float barW = 60, barH = 3;
            RectF homeBar = new RectF(w / 2f - barW / 2f, h - border - 8, w / 2f + barW / 2f, h - border - 8 + barH);
            canvas.drawRoundRect(homeBar, 2, 2, paint);

            // 6. Draw Heatmap Glows
            if (hotspots != null && !hotspots.isEmpty()) {
                float screenW = screen.width();
                float screenH = screen.height();

                for (int i = 0; i < hotspots.size(); i++) {
                    Hotspot hotspot = hotspots.get(i);
                    float refW = hotspot.refWidth > 0 ? hotspot.refWidth : 1220f;
                    float refH = hotspot.refHeight > 0 ? hotspot.refHeight : 2712f;

                    // Map relative coordinates to mini screen
                    float l = screen.left + (hotspot.left / refW) * screenW;
                    float t = screen.top + (hotspot.top / refH) * screenH;
                    float r = screen.left + (hotspot.right / refW) * screenW;
                    float b = screen.top + (hotspot.bottom / refH) * screenH;

                    float cx = (l + r) / 2f;
                    float cy = (t + b) / 2f;
                    float radius = Math.max(28f, Math.max(r - l, b - t) * 1.4f);

                    // A. Vibrant Radial Heatmap Glow (Gradient core -> amber halo -> fade)
                    int coreColor = Color.argb(220, 239, 68, 68); // Neon Red core
                    int midColor = Color.argb(130, 245, 158, 11); // Amber
                    int edgeColor = Color.argb(0, 245, 158, 11);  // Transparent
                    RadialGradient gradient = new RadialGradient(cx, cy, radius,
                        new int[]{coreColor, midColor, edgeColor},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
                    glowPaint.setShader(gradient);
                    canvas.drawCircle(cx, cy, radius, glowPaint);

                    // B. Button Outline box with neon stroke
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2f);
                    paint.setColor(Color.rgb(254, 215, 170)); // Bright warm border
                    RectF btnRect = new RectF(l, t, r, b);
                    canvas.drawRoundRect(btnRect, 6, 6, paint);

                    // C. Center label "跳过"
                    textPaint.setTextSize(9f);
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(hotspot.predictive()?"热点":"待复核", cx, cy + 3f, textPaint);

                    // D. Hit count badge bubble
                    textPaint.setTextSize(8f);
                    textPaint.setColor(Color.rgb(254, 240, 138));
                    canvas.drawText("🔥" + hotspot.hitCount + "次", cx, b + 12f, textPaint);
                }
            } else {
                // Empty state on canvas
                textPaint.setTextSize(11f);
                textPaint.setColor(Color.argb(120, 255, 255, 255));
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("暂无热点数据", w / 2f, h / 2f - 10, textPaint);
                textPaint.setTextSize(9f);
                textPaint.setColor(Color.argb(80, 255, 255, 255));
                canvas.drawText("打开 App 成功跳过后自动呈现", w / 2f, h / 2f + 12, textPaint);
            }
        }
    }
}
