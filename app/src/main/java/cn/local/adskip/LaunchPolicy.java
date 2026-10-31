package cn.local.adskip;

/** Shared hard deadline and coalescing policy: events may advance a poll, never postpone it. */
public final class LaunchPolicy {
    public static final long WINDOW_MS = 8000;
    public static final long IMAGE_MS = 2000;
    public static final long FRESH_IMAGE_MS = 1000;
    public static final long UC_OCR_MAX_MS = 10000;
    public static boolean runGeneralOcr(String pkg,boolean hasManualRegion,long elapsed,long lastRun){
        // A saved UC rectangle is an explicit, measured button area. Generic
        // corner OCR has opened the advertiser app from a different UC layout.
        return !"com.UCMobile".equals(pkg)||!hasManualRegion;
    }
    public static String ucClickGuard(String pkg,String source,String text,long frameAgeMs,int lastCountdown,boolean sameButton){
        if(!"com.UCMobile".equals(pkg))return "";
        if(("ocr".equals(source)||"rectangle_ocr".equals(source))&&frameAgeMs>250)return "uc_ocr_frame_older_than_250ms";
        int countdown=Matcher.skipCountdown(text);
        if(sameButton&&lastCountdown!=Integer.MIN_VALUE
            &&(countdown==lastCountdown||countdown<0&&lastCountdown>=0))return "uc_countdown_already_tapped";
        return "";
    }
    public static boolean imageNeedsRefresh(long age) { return age >= FRESH_IMAGE_MS && imageActive(age); }
    public static boolean delegatedAdOwner(String host,String owner){return "com.xiaomi.market".equals(host)&&"com.miui.systemAdSolution".equals(owner);}
    public static boolean sameOwner(String host,String owner){return host!=null&&(host.equals(owner)||delegatedAdOwner(host,owner));}
    public static boolean focusedSurface(String host,String owner,int type,boolean focused){
        return focused&&sameOwner(host,owner)&&(type==1||(type==3&&delegatedAdOwner(host,owner)));
    }
    public static boolean sameWindow(int captured, int focused) { return captured >= 0 && captured == focused; }
    public static boolean imageActive(long elapsed) { return elapsed >= 0 && elapsed < IMAGE_MS; }
    public static boolean textActive(long elapsed) { return active(elapsed); }
    public static long windowMs(String pkg) { return ("com.baidu.netdisk".equals(pkg)||"com.UCMobile".equals(pkg)) ? 20000 : WINDOW_MS; }
    public static boolean active(long elapsed, String pkg) { return elapsed >= 0 && elapsed < windowMs(pkg); }
    public static boolean active(long elapsed) { return active(elapsed, ""); }
    public static long imageInterval(String pkg, long elapsed) { return 350; }
    public static long captureDelay(String pkg,long elapsed,long sinceCapture,boolean inFlight) {
        if(inFlight)return 25;
        return Math.max(1,imageInterval(pkg,elapsed)-Math.max(0,sinceCapture));
    }
    public static boolean musicSplash(String pkg, String activity) { return "com.miui.player".equals(pkg) && activity != null && activity.endsWith("SplashAdActivity"); }
    public static boolean musicFixedAllowed(String pkg,String focusedActivity,boolean splashVisible) {
        return "com.miui.player".equals(pkg)&&(musicSplash(pkg,focusedActivity)||splashVisible);
    }
    public static boolean advance(long now, long due, long delay) { return now + delay < due; }
    private LaunchPolicy() {}
}
