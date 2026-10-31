package cn.local.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ComponentName;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainActivity extends Activity {
    private final int ink = Color.rgb(27, 45, 38);
    private Store store;
    private static final int PAGE_HOME = 0;
    private static final int PAGE_SETTINGS = 1;
    private static final int PAGE_HEATMAP = 2;
    private int currentPage = PAGE_HOME;
    private HomeView home;
    private SettingsView settingsPage;
    private HeatmapView heatmapPage;
    private android.window.OnBackInvokedCallback backCallback;
    private final Handler handler = new Handler();
    private String exportContent;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        store.diagnostic("UI_OPEN", null);
        if (state != null) {
            currentPage = state.getInt("currentPage", state.getBoolean("settingsPage") ? PAGE_SETTINGS : PAGE_HOME);
        }
        render();
        handler.post(() -> RectangleRulesUi.picked(this, store, getIntent()));
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handler.post(()->RectangleRulesUi.picked(this,store,intent));}
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3), 1); return view;
    }
    private void updateBackCallback(boolean needed) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (needed && backCallback == null) {
                backCallback = this::onBackPressed;
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
            } else if (!needed && backCallback != null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
                backCallback = null;
            }
        }
    }
    private void render() {
        getWindow().setStatusBarColor(HomeView.CANVAS); getWindow().setNavigationBarColor(HomeView.CANVAS);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (currentPage == PAGE_HEATMAP) { renderHeatmap(); return; }
        if (currentPage == PAGE_SETTINGS) { renderSettings(); return; }
        updateBackCallback(false);
        settingsPage = null;
        heatmapPage = null;
        home = new HomeView(this, getString(R.string.app_name), this::serviceSettings, this::rulesMenu, this::openSettings);
        home.master.setChecked(store.enabled());
        home.master.setOnCheckedChangeListener((b, checked) -> {store.prefs.edit().putBoolean("enabled", checked).apply();updateStatus();});
        home.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(home); home.requestApplyInsets(); updateStatus();
    }
    private void openSettings(){currentPage = PAGE_SETTINGS; render();}
    private void openHeatmap(){currentPage = PAGE_HEATMAP; render();}
    private void renderSettings(){
        updateBackCallback(true);
        home = null;
        heatmapPage = null;
        settingsPage = new SettingsView(this, DeviceInfo.version(this), this::showHome, this::logs, this::openHeatmap, this::exportDiagnostics, this::backgroundSettings, this::deviceInfo);
        settingsPage.collecting.setChecked(store.collecting());
        settingsPage.collecting.setOnCheckedChangeListener((b,checked)->store.setCollecting(checked));
        settingsPage.diagnostic.setChecked(store.prefs.getBoolean("diagnostics",false));
        settingsPage.diagnostic.setOnCheckedChangeListener((b,checked)->store.prefs.edit().putBoolean("diagnostics",checked).apply());
        settingsPage.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            }else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(settingsPage);settingsPage.requestApplyInsets();updateStatus();
    }
    private void renderHeatmap(){
        updateBackCallback(true);
        home = null;
        settingsPage = null;
        heatmapPage = new HeatmapView(this, new HotspotStore(this), this::openSettings);
        heatmapPage.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            }else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(heatmapPage);heatmapPage.requestApplyInsets();
    }
    private void showHome(){currentPage = PAGE_HOME; render();}
    private void deviceInfo(){
        TextView text=label(DeviceInfo.describe(this),13,ink);text.setTextIsSelectable(true);text.setPadding(dp(20),dp(12),dp(20),dp(12));
        ScrollView scroll=new ScrollView(this);scroll.addView(text);
        new AlertDialog.Builder(this).setTitle("手机与系统信息").setView(scroll)
            .setPositiveButton("关闭",null).setNeutralButton("导出设备信息",(d,w)->{
                exportContent=DeviceInfo.describe(this);createDocument("qiang-device-info.txt","text/plain",12);
            }).show();
    }
    @Override public void onBackPressed(){if(currentPage==PAGE_HEATMAP)openSettings();else if(currentPage==PAGE_SETTINGS)showHome();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("currentPage",currentPage);super.onSaveInstanceState(state);}
    private boolean diagnosticExportInFlight;
    private void exportDiagnostics(){
        if(diagnosticExportInFlight)return;
        diagnosticExportInFlight=true;
        new Thread(()->{
            try{
                final String report=store.diagnosticReport();
                handler.post(()->{diagnosticExportInFlight=false;if(isFinishing()||isDestroyed())return;
                    exportContent=report;createDocument("adskip-diagnostic.txt","text/plain",12);});
            }catch(Exception error){handler.post(()->{diagnosticExportInFlight=false;if(!isFinishing()&&!isDestroyed())
                android.widget.Toast.makeText(this,"诊断日志导出失败："+error.getMessage(),android.widget.Toast.LENGTH_LONG).show();});}
        },"diagnostic-export").start();
    }
    private void serviceSettings() {
        new AlertDialog.Builder(this).setTitle("允许自动点击跳过")
            .setMessage(getString(R.string.accessibility_enable_message, getString(R.string.app_name)))
            .setPositiveButton("前往系统设置", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNegativeButton("取消", null).show();
    }
    private void backgroundSettings() {
        new AlertDialog.Builder(this).setTitle("后台运行设置")
            .setMessage("如果使用一段时间后停止跳过，请先检查无障碍服务是否仍开启。\n\n在本应用的系统信息页中，允许后台自启动，并把省电策略设置为「无限制」（具体入口随系统版本变化）。")
            .setPositiveButton("打开应用信息", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("返回", null).show();
    }
    private void updateStatus() {
        if(settingsPage!=null){
            boolean capture=store.collecting();
            if(settingsPage.collecting.isChecked()!=capture)settingsPage.collecting.setChecked(capture);
            boolean diagnostic=store.prefs.getBoolean("diagnostics",false);
            if(settingsPage.diagnostic.isChecked()!=diagnostic)settingsPage.diagnostic.setChecked(diagnostic);
            settingsPage.updateObservations(store.prefs.getInt("observedChanges",0));
        }
        if (home == null) return;
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String component = new ComponentName(this, SkipService.class).flattenToString();
        String shortComponent = new ComponentName(this, SkipService.class).flattenToShortString();
        boolean enabled = false;
        if (enabledServices != null) for (String entry : enabledServices.split(":")) if (entry.equals(component) || entry.equals(shortComponent)) enabled = true;
        home.update(enabled, SkipService.connected, store.enabled(), store.prefs.getInt("observedChanges", 0), store.rules().size());
    }
    private void logs() {
        String log = store.prefs.getString("log", "暂无运行记录，开启服务后打开目标应用。");
        TextView text = label(log, 13, ink); text.setTextIsSelectable(true); text.setPadding(dp(18),dp(10),dp(18),dp(10));
        ScrollView scroll = new ScrollView(this); scroll.addView(text);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("实时运行记录").setView(scroll).setPositiveButton("关闭", null)
            .setNeutralButton("导出记录", (d,w) -> {exportContent=store.prefs.getString("log",""); createDocument("adskip-log.txt", "text/plain", 12);}).create();
        android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(prefs,key)->{
            if(key.equals("log"))handler.post(()->text.setText(prefs.getString("log","")));
        };
        dialog.setOnShowListener(d->store.prefs.registerOnSharedPreferenceChangeListener(changes));
        dialog.setOnDismissListener(d->store.prefs.unregisterOnSharedPreferenceChangeListener(changes));
        dialog.show();
    }
    private void rulesMenu() {
        new AlertDialog.Builder(this).setTitle("规则管理").setItems(new String[]{"矩形坐标规则 / 屏幕框选", "导出当前规则", "导入规则 / 增加应用", "恢复上一版规则"}, (d,w) -> {
            if(w==0){RectangleRulesUi.manage(this,store);return;}
            if(w==1) try {exportContent=store.rulesJson();createDocument("adskip-rules.json","application/json",12);}catch(Exception e){error(e);}
            else if(w==2) {Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);in.addCategory(Intent.CATEGORY_OPENABLE);in.setType("*/*");startActivityForResult(in,11);}
            else {new AlertDialog.Builder(this).setTitle("恢复上一版？").setMessage("将替换当前规则，自动跳过总开关保持不变。")
                .setPositiveButton("恢复", (a,b)->{boolean changed=store.rollback();if(changed){store.log("已恢复上一版规则");render();}else error(new Exception("没有可恢复的上一版规则"));}).setNegativeButton("取消",null).show();}
        }).show();
    }
    private void createDocument(String name, String type, int code) {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(type);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,code);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try {
            if(request==11) {
                String json=Store.read(getContentResolver().openInputStream(data.getData())); List<Store.AppRule> list=Store.parse(json);
                StringBuilder names=new StringBuilder();for(Store.AppRule r:list)names.append("• ").append(r.name).append("\n");
                new AlertDialog.Builder(this).setTitle("替换为这 "+list.size()+" 款应用的规则？").setMessage(names+"\n请仅导入你信任且确认按钮 ID 的规则。原规则可恢复。")
                    .setPositiveButton("导入",(d,w)->{try{store.importRules(json);render();}catch(Exception e){error(e);}}).setNegativeButton("取消",null).show();
            } else if(request==12 && exportContent!=null) {
                try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){out.write(exportContent.getBytes(StandardCharsets.UTF_8));}
                android.widget.Toast.makeText(this,"已导出",android.widget.Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){error(e);}
    }
    private void error(Exception e) {new AlertDialog.Builder(this).setTitle("操作未完成").setMessage(e.getMessage()).setPositiveButton("知道了",null).show();}
    @Override protected void onResume() {super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onDestroy(){updateBackCallback(false);super.onDestroy();}
    @Override protected void onPause() {super.onPause();handler.removeCallbacks(refresh);}
}
