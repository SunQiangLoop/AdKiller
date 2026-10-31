package cn.local.adskip;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/** Local compatibility metadata, without device identifiers or extra permissions. */
final class DeviceInfo {
    static String version(Context context){
        try{PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);return p.versionName+"（"+(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode)+"）";}
        catch(Exception error){return "无法读取";}
    }
    static LinkedHashMap<String,String> collect(Context context){
        LinkedHashMap<String,String> values=new LinkedHashMap<>();
        values.put("软件版本",version(context));
        values.put("品牌 / 制造商",Build.BRAND+" / "+Build.MANUFACTURER);
        values.put("手机型号",Build.MODEL);
        values.put("设备代号",Build.DEVICE);
        values.put("Android 版本",Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT);
        values.put("系统构建版本",Build.DISPLAY);
        values.put("系统增量版本",Build.VERSION.INCREMENTAL);
        values.put("安全补丁",Build.VERSION.SECURITY_PATCH);
        values.put("硬件平台",Build.HARDWARE+" / "+Build.BOARD);
        if(Build.VERSION.SDK_INT>=31)values.put("芯片",Build.SOC_MANUFACTURER+" / "+Build.SOC_MODEL);
        values.put("CPU 架构",android.text.TextUtils.join(", ",Build.SUPPORTED_ABIS));
        WindowManager wm=context.getSystemService(WindowManager.class);
        DisplayMetrics dm=new DisplayMetrics();
        if(wm!=null){
            if(Build.VERSION.SDK_INT>=30){Rect bounds=wm.getMaximumWindowMetrics().getBounds();values.put("屏幕分辨率",bounds.width()+" × "+bounds.height()+" px");}
            else{wm.getDefaultDisplay().getRealMetrics(dm);values.put("屏幕分辨率",dm.widthPixels+" × "+dm.heightPixels+" px");}
            values.put("当前刷新率",String.format(Locale.ROOT,"%.1f Hz",wm.getDefaultDisplay().getRefreshRate()));
        }
        dm=context.getResources().getDisplayMetrics();
        Configuration cfg=context.getResources().getConfiguration();
        values.put("显示密度",dm.densityDpi+" dpi · "+String.format(Locale.ROOT,"%.2f×",dm.density));
        values.put("界面可用尺寸",cfg.screenWidthDp+" × "+cfg.screenHeightDp+" dp");
        values.put("字体缩放",String.format(Locale.ROOT,"%.2f×",cfg.fontScale));
        values.put("屏幕方向",cfg.orientation==Configuration.ORIENTATION_LANDSCAPE?"横屏":"竖屏");
        values.put("系统主题",(cfg.uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES?"深色":"浅色");
        values.put("系统语言",cfg.getLocales().isEmpty()?"未知":cfg.getLocales().get(0).toLanguageTag());
        ActivityManager am=context.getSystemService(ActivityManager.class);
        if(am!=null){ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();am.getMemoryInfo(memory);
            values.put("运行内存",String.format(Locale.ROOT,"总计 %.1f GB · 当前可用 %.1f GB",memory.totalMem/1e9,memory.availMem/1e9));
            values.put("当前内存压力",memory.lowMemory?"系统报告低内存":"系统未报告低内存");}
        android.os.PowerManager power=context.getSystemService(android.os.PowerManager.class);
        if(power!=null){values.put("系统省电模式",power.isPowerSaveMode()?"开启":"关闭");values.put("电池优化豁免",power.isIgnoringBatteryOptimizations(context.getPackageName())?"已豁免":"未豁免（不等同于厂商后台限制）");}
        return values;
    }
    static String describe(Context context){StringBuilder text=new StringBuilder();for(Map.Entry<String,String> entry:collect(context).entrySet())text.append(entry.getKey()).append("\n").append(entry.getValue()).append("\n\n");return text.toString();}
    static String json(Context context){return new JSONObject(collect(context)).toString();}
    // Called only from the existing background export worker.
    static String apkSha256(Context context){
        try(java.io.InputStream in=new java.io.FileInputStream(context.getApplicationInfo().sourceDir)){
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[32768];int n;
            while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);
            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));return hash.toString();
        }catch(Exception error){return "unavailable";}
    }
}
