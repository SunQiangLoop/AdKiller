package cn.local.adskip;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public class SkipService extends AccessibilityService {
    public static volatile boolean connected;
    public static volatile SkipService instance;
    private RectanglePicker rectanglePicker;
    private boolean multiRectangles(Store.AppRule rule){return rule!=null&&rule.activeRectangles().size()>1;}
    public Rect rectangleScreen(){return screen();}
    public void beginRectanglePicker(String pkg,String name,int index){
        if(destroyed)throw new IllegalStateException("无障碍服务未连接");
        if(rectanglePicker!=null)rectanglePicker.close();
        endSession("rectangle_picking");generation++;done=true;
        rectanglePicker=new RectanglePicker(this,pkg,name,index,()->{rectanglePicker=null;});
        try{rectanglePicker.show();
            android.content.Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
            if(launch!=null)startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch(RuntimeException error){if(rectanglePicker!=null)rectanglePicker.close();throw error;}
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private final Map<String, Store.AppRule> rules = new HashMap<>();
    private volatile String foreground = "";
    private final Map<String,String> appNames = new HashMap<>();
    private final java.util.Set<String> launchableApps = new java.util.HashSet<>();
    private final java.util.Set<String> discoveringApps = new java.util.HashSet<>();
    private final java.util.Set<String> genericApps = new java.util.HashSet<>();
    private static final java.util.Set<String> EXCLUDED_PACKAGES = new java.util.HashSet<>(java.util.Arrays.asList(
        "com.android.settings", "com.android.systemui", "com.miui.home",
        "com.android.camera", "com.miui.camera", "com.google.android.GoogleCamera",
        "com.samsung.android.camera", "com.oppo.camera", "com.oneplus.camera",
        "com.android.incallui", "com.android.phone", "com.android.dialer", "com.google.android.dialer",
        "com.miui.gallery", "com.android.gallery3d", "com.google.android.apps.photos",
        "com.miui.securitycenter", "com.miui.securityadd", "com.miui.packageinstaller",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.miui.screenrecorder"
    ));
    boolean isExcluded(String pkg) {
        return pkg == null || pkg.isEmpty() || pkg.equals(getPackageName()) || EXCLUDED_PACKAGES.contains(pkg);
    }
    private SampleRecorder samples;
    private long lastWindowEventAt;
    private long lastDiagnosticHeartbeat;
    private boolean sessionEnded;
    private long pollDue = Long.MAX_VALUE;
    private boolean scanInFlight;
    private boolean foregroundProbeInFlight;
    private final Map<Integer,String> windowOwners = new HashMap<>();
    private final Map<Integer,String> windowClasses = new HashMap<>();
    private boolean parallelStarted, musicSplashVisible;
    private long lastTextRequest;
    private final java.util.concurrent.ExecutorService focusProbes = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean focusInFlight, focusDirty;
    private long focusSequence;
    private int focusedWindowId=-1;
    private volatile boolean destroyed;
    private final java.util.concurrent.ExecutorService probes = java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile long started, generation;
    private int attempts;
    private static final long UC_FIRST_TARGET_SETTLE_MS=600;
    private long ucFirstTargetToken=-1,ucFirstTargetSeenAt;
    private int ucFirstTargetWindow=-1;
    private Rect ucFirstTargetBounds;
    private boolean pending, done;
    private final ClickGate clickGate = new ClickGate();
    private long eventCount, lastEventLog, lastScanLog, lastCacheProbe;
    private boolean pollQueued;
    private boolean captureInFlight;
    private long lastCapture;
    private long lastUcGeneralOcrAt;
    private final java.util.concurrent.atomic.AtomicInteger ucRectangleSubmitted=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger ucRectangleBusy=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger ucRectangleMissed=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger ucRectangleFound=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger ucGeneralBusy=new java.util.concurrent.atomic.AtomicInteger();
    private final Object latestFrameLock=new Object();
    private Frame latestFrame;
    private long latestFrameToken,latestFrameCapturedAt;
    private int latestFrameWindow=-1;
    private OcrEngine ocr,rectangleOcr,observationOcr;
    private HotspotStore hotspots;
    private volatile Rect lastTargetBounds;
    private final java.util.concurrent.atomic.AtomicBoolean ocrBusy = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.ExecutorService images = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean rectangleOcrBusy = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean templateBusy = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.ExecutorService templates = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ExecutorService rectangleTasks = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ExecutorService ocrTasks = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.ArrayDeque<Target> targets = new java.util.ArrayDeque<>();
    private final java.util.concurrent.ExecutorService observations=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean observationBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile Target observation;
    private Target lastCompletedTarget;
    private static final long OBSERVATION_WINDOW_MS=2400;
    private Rect lastClickedBounds;
    private long lastClickedAt;
    private int lastClickedWindow=-1;
    private int lastUcClickCountdown=Integer.MIN_VALUE;
    interface TapDispatcher { boolean dispatch(GestureDescription gesture,GestureResultCallback callback,Handler handler); }
    TapDispatcher tapDispatcher=(gesture,callback,timer)->dispatchGesture(gesture,callback,timer);
    private static final class Frame {
        final Bitmap bitmap;final java.util.concurrent.atomic.AtomicInteger users=new java.util.concurrent.atomic.AtomicInteger(1);
        Frame(Bitmap bitmap){this.bitmap=bitmap;}
        void retain(){users.incrementAndGet();}
        void release(){if(users.decrementAndGet()==0)bitmap.recycle();}
    }
    private static final class Target {
        final Store.AppRule rule;final long token,capturedAt,resolvedAt;final int window;
        final String source,text;final Rect bounds;final Trace trace;
        VisualMatcher.Patch proof;volatile long completedAt;
        volatile String attemptId="",observationOutcome="pending";
        volatile int absentFrames,presentFrames,observationFrames,observationBusyFrames,observationRequests,observationErrors,captureErrors;
        volatile long lastObservationCaptureAt;
        volatile boolean observationDecisionPending;
        Target(Store.AppRule rule,long token,int window,long capturedAt,String source,String text,Rect bounds,Trace trace){
            this.rule=rule;this.token=token;this.window=window;this.capturedAt=capturedAt;this.resolvedAt=SystemClock.elapsedRealtime();
            this.source=source;this.text=text;this.bounds=new Rect(bounds);this.trace=trace;
        }
    }
    private boolean imageEnded;
    private final Map<String, Bitmap> visualTemplates = new HashMap<>();
    private int scanCount, nullRoots, maxNodes;
    private Thread.UncaughtExceptionHandler previousCrashHandler;
    private final String serviceRun = java.util.UUID.randomUUID().toString();
    private final java.util.concurrent.atomic.AtomicLong workIds = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong attemptCounter = new java.util.concurrent.atomic.AtomicLong();
    private String activeClaimWorkId = "";
    private String activeClaimStrategy = "";
    private boolean recognitionEnded;
    private String recognitionEndReason = "";
    private long recognitionEndedAt;
    private volatile Trace sessionTrace = new Trace("", 0, 0, "service");
    private static final class Trace {
        final String pkg, work; final long token, start, created;
        Trace(String pkg,long token,long start,String work){this.pkg=pkg;this.token=token;this.start=start;this.work=work;this.created=SystemClock.elapsedRealtime();}
        Trace work(String id){return new Trace(pkg,token,start,id);}
    }
    private Trace task(String type){return sessionTrace.work(type+"-"+workIds.incrementAndGet());}
    private void diagnostic(String kind,Object... pairs){diagnostic(sessionTrace,kind,pairs);}
    private void diagnostic(Trace trace,String kind,Object... pairs) {
        if(store==null || !store.diagnostics() && !kind.startsWith("SERVICE") && !kind.equals("CRASH") && !kind.equals("DIAGNOSTICS_STATE"))return;
        JSONObject fields=new JSONObject();
        try {
            long now=SystemClock.elapsedRealtime();
            boolean isEnded = recognitionEnded || done || sessionEnded;
            long afterEnd = (isEnded && recognitionEndedAt > 0) ? (now - recognitionEndedAt) : 0;
            fields.put("sessionId",serviceRun+":"+trace.token).put("workId",trace.work)
                .put("foreground",trace.pkg).put("currentForeground",foreground)
                .put("elapsedMs",trace.start==0?0:now-trace.start)
                .put("taskElapsedMs",now-trace.created).put("staleSession",generation!=trace.token).put("events",eventCount)
                .put("recognitionEnded",isEnded).put("recognitionEndReason",recognitionEndReason)
                .put("resultAfterEndMs",afterEnd)
                .put("thread",Thread.currentThread().getName());
            for(int i=0;i+1<pairs.length;i+=2)fields.put(String.valueOf(pairs[i]),pairs[i+1]);
        }catch(Exception ignored){}
        store.diagnostic(kind,fields);
    }
    private String gateReason(Store.AppRule rule,long token) {
        if(destroyed)return "service_destroyed";
        if(rectanglePicker!=null)return "rectangle_picking";
        if(generation!=token)return "session_changed";
        if(done)return "recognition_finished";
        if(pending)return "click_pending";
        if(!clickGate.available(token))return "click_gate_busy";
        if(!store.enabled(rule.pkg))return "disabled";
        if(!LaunchPolicy.active(elapsed(),foreground))return "session_deadline";
        if(getSystemService(KeyguardManager.class).isKeyguardLocked())return "screen_locked";
        return "allowed";
    }
    private String classifyDecision(String gateReason, String stage, boolean frameExpired, String specificReason) {
        if ("click_pending".equals(gateReason) || "recognition_finished".equals(gateReason)
            || "competition_yield".equals(specificReason) || "other_path_handling".equals(specificReason)) {
            return "COMPETITION_SETTLED";
        }
        if (frameExpired || "session_deadline".equals(gateReason) || "frame_expired".equals(specificReason) || "expired".equals(specificReason)) {
            return "PIPELINE_EXPIRED";
        }
        if ("window_changed".equals(specificReason) || "window_owner_mismatch".equals(specificReason) || "session_changed".equals(gateReason)) {
            return "WINDOW_MISMATCH";
        }
        if ("ocr_busy".equals(specificReason) || "validation_busy".equals(specificReason) || "click_gate_busy".equals(gateReason)) {
            return "RESOURCE_BUSY";
        }
        if ("generic_position_filter".equals(specificReason) || "target_too_large".equals(specificReason) || "invalid_bounds".equals(specificReason)) {
            return "GEOMETRY_FILTER";
        }
        return "POLICY_FILTER";
    }
    private void discard(Trace trace,String stage,Store.AppRule rule,long token,long capturedAt,int windowId) {
        long age=SystemClock.elapsedRealtime()-capturedAt;
        boolean expired=!LaunchPolicy.imageActive(age);
        String gate=gateReason(rule,token);
        String dClass=classifyDecision(gate,stage,expired,null);
        diagnostic(trace,"DECISION_REJECTED","stage",stage,"gateReason",gate,
            "decisionClass",dClass,
            "blockedByWorkId",activeClaimWorkId,"blockedByStrategy",activeClaimStrategy,
            "imageEnded",imageEnded,"frameAgeMs",age,
            "frameExpired",expired,"frameBudgetMs",LaunchPolicy.IMAGE_MS,
            "capturedWindow",windowId,"focusedWindow",focusedWindowId,"attempt",attempts);
    }
    @Override public void onCreate() {
        super.onCreate(); store = new Store(this);
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            diagnostic("CRASH", "thread", thread.getName(), "stack", android.util.Log.getStackTraceString(error));
            if (previousCrashHandler != null) previousCrashHandler.uncaughtException(thread, error);
        });
        diagnostic("SERVICE_CREATE", "pid", android.os.Process.myPid());
    }
    private void queuePoll(long delay) {
        long now=SystemClock.elapsedRealtime();
        if (pollQueued && !LaunchPolicy.advance(now,pollDue,delay)) return;
        if (pollQueued) handler.removeCallbacks(poll);
        pollQueued=true; pollDue=now+delay;
        handler.postDelayed(poll,delay);
    }
    private void diagnosticHeartbeat(){
        long now=SystemClock.elapsedRealtime();
        if(!store.diagnostics()||now-lastDiagnosticHeartbeat<1000||!rules.containsKey(foreground))return;
        lastDiagnosticHeartbeat=now;
        diagnostic("PIPELINE_STATE","remainingMs",Math.max(0,LaunchPolicy.windowMs(foreground)-elapsed()),
            "scanInFlight",scanInFlight,"captureInFlight",captureInFlight,"ocrBusy",ocrBusy.get(),
            "rectangleOcrBusy",rectangleOcrBusy.get(),"templateBusy",templateBusy.get(),"pending",pending,"done",done,"imageEnded",imageEnded,
            "attempts",attempts,"focusedWindow",focusedWindowId,"gateReason",gateReason(rules.get(foreground),generation));
    }
    private void endSession(String reason) {
        if(sessionEnded||!rules.containsKey(foreground))return;
        sessionEnded=true;
        clearLatestFrame();
        recognitionEnded=true;
        recognitionEndReason=reason;
        recognitionEndedAt=SystemClock.elapsedRealtime();
        diagnostic("SESSION_END","reason",reason,"scanCount",scanCount,"attempts",attempts,"windowMs",LaunchPolicy.windowMs(foreground));
        if("com.UCMobile".equals(foreground)&&attempts==0&&reason.equals("foreground_changed"))
            store.log("UC浏览器：本次未点击（停留"+elapsed()+"毫秒；框选OCR提交"+ucRectangleSubmitted.get()+"次、空结果"+ucRectangleMissed.get()+"次、忙跳过"+ucRectangleBusy.get()+"帧、识别到目标"+ucRectangleFound.get()+"次；普通OCR忙跳过"+ucGeneralBusy.get()+"帧）。切出时未完成的任务不计为空结果");
        if(reason.equals("deadline") && attempts==0)store.log(appName(foreground)+"："+(LaunchPolicy.windowMs(foreground)/1000)+" 秒内未识别到可点击的跳过按钮");
    }
    private final Runnable recoverPoll=()->{if(!destroyed)queuePoll(0);};
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            pollQueued = false; pollDue=Long.MAX_VALUE;
            try {
                observeForeground();
                diagnosticHeartbeat();
                if(!LaunchPolicy.active(elapsed(),foreground)) { endSession("deadline"); done=true; }
                else scan();
            } catch (RuntimeException e) {
                diagnostic("SCAN_ERROR", "stack", android.util.Log.getStackTraceString(e));
            }
            // Poll only while a launch is active; idle apps resume on window events.
            if(store.enabled(foreground)&&rules.containsKey(foreground)&&!done&&LaunchPolicy.active(elapsed(),foreground))queuePoll(200);
        }
    };
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if(key.equals("hotspots_v1")&&hotspots!=null)hotspots.reload();
        if(key.equals("enabled")||key.equals("rules")||key.equals("diagnostics")||key.equals("collecting"))diagnostic("DIAGNOSTICS_STATE","key",key,"enabled",store.enabled(),"diagnostics",store.diagnostics());
        if((key.equals("diagnostics")||key.equals("collecting"))&&store.diagnostics()&&rules.containsKey(foreground))diagnosticConfiguration(foreground);
        if (key.equals("rules") || key.equals("enabled")) {
            if(key.equals("rules")){endSession("rules_changed");generation++;done=true;}
            reload();
            if (!rules.containsKey(foreground)||!store.enabled(foreground)) { endSession("disabled_or_rule_removed");done = true; }
        }
    };

    @Override protected void onServiceConnected() {
        connected = true;instance=this;
        store = new Store(this);
        hotspots = new HotspotStore(this);
        android.content.Intent launch = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        for(android.content.pm.ResolveInfo item:getPackageManager().queryIntentActivities(launch,0)) {
            appNames.put(item.activityInfo.packageName,item.loadLabel(getPackageManager()).toString());
            launchableApps.add(item.activityInfo.packageName);
        }
        launchableApps.remove(getPackageName());
        launchableApps.removeAll(EXCLUDED_PACKAGES);
        String keyboard=android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        if(keyboard!=null)launchableApps.remove(keyboard.split("/")[0]);
        android.content.Intent home=new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME);
        for(android.content.pm.ResolveInfo item:getPackageManager().queryIntentActivities(home,0))launchableApps.remove(item.activityInfo.packageName);
        reload();samples=new SampleRecorder(this,store);
        handler.post(collectionExpiry);
        for(Store.AppRule rule:rules.values())appNames.put(rule.pkg,rule.name);
        if (ocr == null) {
            ocr = new OcrEngine();rectangleOcr=new OcrEngine();observationOcr=new OcrEngine();
            rectangleOcr.warmUp(error->diagnostic("RECT_OCR_READY","error",error==null?"":error.toString()));
            final long warmStarted = SystemClock.elapsedRealtime();
            ocr.warmUp(error -> diagnostic("OCR_READY", "durationMs", SystemClock.elapsedRealtime()-warmStarted,
                "error", error == null ? "" : error.toString()));
        }
        store.prefs.registerOnSharedPreferenceChangeListener(listener);
        store.log("无障碍服务已连接");
        diagnostic("SERVICE_CONNECTED", "ruleCount", rules.size(), "flags", getServiceInfo().flags, "capabilities", getServiceInfo().getCapabilities());
        queuePoll(0);
    }
    private void reload() {
        rules.clear();genericApps.clear();
        for(Store.AppRule rule:store.rules()){
            rules.put(rule.pkg,rule);
            // Saving a rectangle must not widen the original generic fallback policy.
            if(rule.rectangleCreatedApp)genericApps.add(rule.pkg);
        }
        for(String pkg:launchableApps)if(!rules.containsKey(pkg)) {
            Store.AppRule rule=new Store.AppRule();rule.pkg=pkg;rule.name=appName(pkg);
            rules.put(pkg,rule);genericApps.add(pkg);
        }
    }
    private long elapsed() { return SystemClock.elapsedRealtime() - started; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        eventCount++;
        if(store==null)return;
        String pkg=text(event.getPackageName());
        if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED && (rules.containsKey(pkg) || rules.containsKey(foreground)))diagnostic("OBSERVED_VIEW_CLICK","eventPackage",pkg,"windowId",event.getWindowId(),"class",text(event.getClassName()),"origin","unknown_user_or_service","pending",pending);
        if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED)return;
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // Launchers need not advertise CATEGORY_LAUNCHER themselves. Track every
            // window owner; requestWindowFocus still requires a focused app window.
            if(!pkg.isEmpty()&&event.getWindowId()>=0)windowOwners.put(event.getWindowId(),pkg);
            if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&event.getWindowId()>=0&&text(event.getClassName()).endsWith("Activity"))
                windowClasses.put(event.getWindowId(),text(event.getClassName()));
            lastWindowEventAt=SystemClock.elapsedRealtime();
            handler.removeCallbacks(recoverPoll);handler.postDelayed(recoverPoll,350);
            diagnostic("WINDOW_EVENT","eventPackage",pkg,"windowId",event.getWindowId(),"type",event.getEventType(),"class",text(event.getClassName()));
            focusSequence++;requestWindowFocus();
        }
        if(rules.containsKey(pkg) && SystemClock.elapsedRealtime()-lastEventLog>1000) {
            lastEventLog=SystemClock.elapsedRealtime();
            diagnostic("EVENT","eventPackage",pkg,"type",event.getEventType(),"class",text(event.getClassName()));
        }
        if(!done&&rules.containsKey(foreground)&&store.enabled(foreground)&&LaunchPolicy.active(elapsed(),foreground))queuePoll(0);
    }
    private void requestWindowFocus() {
        if(focusInFlight){focusDirty=true;return;}
        focusInFlight=true;focusDirty=false;
        final long sequence=focusSequence;final Trace trace=task("focus");
        final Map<Integer,String> owners=new HashMap<>(windowOwners);
        final Map<Integer,String> activities=new HashMap<>(windowClasses);
        final String sessionHost=foreground;
        focusProbes.execute(()->{
            String pkg="";int focusedId=-1;boolean visibleMusicAd=false;
            org.json.JSONArray snapshot=new org.json.JSONArray();
            try {
                for(android.view.accessibility.AccessibilityWindowInfo window:getWindows()) {
                    if(LaunchPolicy.active(elapsed(),foreground)){
                        JSONObject item=new JSONObject();try{item.put("id",window.getId()).put("type",window.getType()).put("focused",window.isFocused()).put("active",window.isActive()).put("owner",owners.get(window.getId())).put("title",text(window.getTitle()));snapshot.put(item);}catch(Exception ignored){}
                    }
                    String owner=owners.get(window.getId());
                    if(window.isFocused()&&(window.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
                        ||LaunchPolicy.focusedSurface(sessionHost,owner,window.getType(),true))) {
                        focusedId=window.getId();pkg=LaunchPolicy.delegatedAdOwner(sessionHost,owner)?sessionHost:(owner==null?"":owner);
                    }
                    if(window.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
                        &&LaunchPolicy.musicSplash(owners.get(window.getId()),activities.get(window.getId())))visibleMusicAd=true;
                    window.recycle();
                }
            }catch(Exception error){diagnostic(trace,"FOCUS_ERROR","error",error.toString());}
            if(snapshot.length()>0)diagnostic(trace,"WINDOW_SNAPSHOT","windows",snapshot);
            final String target=pkg;final int id=focusedId;final boolean visibleAd=visibleMusicAd;
            handler.post(()->{
                focusInFlight=false;
                if(destroyed)return;
                if(sequence==focusSequence&&!target.isEmpty()) {
                    focusedWindowId=id;
                    lastWindowEventAt=SystemClock.elapsedRealtime();
                    diagnostic(trace,"FOCUS_CONFIRMED","package",target,"windowId",id);
                    activateForeground(target,"focused_window");
                    musicSplashVisible=visibleAd&&target.equals("com.miui.player");
                    diagnostic(trace,"MUSIC_WINDOW","splashVisible",musicSplashVisible,"focusedActivity",windowClasses.get(id));
                }
                if(focusDirty||sequence!=focusSequence)requestWindowFocus();
            });
        });
    }
    private String appName(String pkg){return appNames.containsKey(pkg)?appNames.get(pkg):pkg;}
    private void activateForeground(String pkg,String source) {
        if(pkg.isEmpty()||(pkg.equals(foreground)&&!source.equals("new_install")))return;
        if(observation!=null&&observation.token==generation){
            observation.observationOutcome="foreground_changed";
            diagnostic(observation.trace,"POST_CLICK_RESULT","attemptId",observation.attemptId,"state","foreground_changed",
                "nextPackage",pkg,"confirmedSkip",false);
            store.log(observation.rule.name+"：点击后切换到"+appName(pkg)+"，原位置 OCR 复查中断，结果无法判断");
        }
        Target last=lastCompletedTarget;
        if(last!=null&&last.token==generation&&SystemClock.elapsedRealtime()-last.completedAt<=5000){
            long afterClick=SystemClock.elapsedRealtime()-last.completedAt;
            diagnostic(last.trace,"POST_CLICK_DESTINATION","attemptId",last.attemptId,"nextPackage",pkg,
                "afterClickMs",afterClick,"observationOutcome",last.observationOutcome,"causalityConfirmed",false);
            if(!pkg.equals("com.miui.home")&&!pkg.equals(last.rule.pkg))
                store.log(last.rule.name+"：点击后"+afterClick+"毫秒进入"+appName(pkg)+"（"+pkg+"）；是否由点击引起尚未确认");
        }
        endSession("foreground_changed");
        foreground=pkg;started=SystemClock.elapsedRealtime();generation++;
        clearLatestFrame();
        sessionTrace=new Trace(pkg,generation,started,"session");
        clickGate.reset(generation);
        activeClaimWorkId="";activeClaimStrategy="";recognitionEnded=false;recognitionEndReason="";recognitionEndedAt=0;
        attempts=0;pending=false;done=false;sessionEnded=false;scanCount=0;maxNodes=0;nullRoots=0;lastScanLog=0;
        lastUcGeneralOcrAt=-1200;ucRectangleSubmitted.set(0);ucRectangleBusy.set(0);ucRectangleMissed.set(0);ucRectangleFound.set(0);ucGeneralBusy.set(0);
        lastTargetBounds=null;observation=null;lastCompletedTarget=null;targets.clear();lastClickedBounds=null;lastClickedAt=0;lastClickedWindow=-1;lastUcClickCountdown=Integer.MIN_VALUE;
        // Every flag that means "this session already started something" must be cleared here.
        // Leaving one behind made startRecognition() return for the rest of the process while
        // scan() kept running, so sessions logged text scans but never a screenshot.
        imageEnded=false;parallelStarted=false;musicSplashVisible=false;lastTextRequest=0;
        // lastCapture is only a throttle timestamp; keeping the previous session's value delays
        // this session's first frame by up to a second for no reason.
        lastCapture=0;
        diagnostic("SESSION_RESET","parallelStarted",parallelStarted,"lastCapture",lastCapture);
        if(isExcluded(pkg)){
            done=true;sessionEnded=true;recognitionEnded=true;
            diagnostic("APP_OPEN","package",pkg,"name",appName(pkg),"source",source,"supported",false,"enabled",false,"generic",false,"excluded",true);
            return;
        }
        if(!rules.containsKey(pkg)&&discoveringApps.add(pkg)){
            probes.execute(()->{
                android.content.Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);
                String label=pkg;
                if(intent!=null)try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}
                final String name=label;
                handler.post(()->{
                    discoveringApps.remove(pkg);
                    if(destroyed||intent==null||isExcluded(pkg))return;
                    String keyboard=android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
                    android.content.Intent home=new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME);
                    for(android.content.pm.ResolveInfo item:getPackageManager().queryIntentActivities(home,0))if(pkg.equals(item.activityInfo.packageName))return;
                    if(keyboard!=null&&keyboard.startsWith(pkg+"/"))return;
                    launchableApps.add(pkg);appNames.put(pkg,name);reload();
                    diagnostic("APP_DISCOVERED","package",pkg);
                    if(foreground.equals(pkg)&&!parallelStarted)activateForeground(pkg,"new_install");
                });
            });
        }
        boolean supported=rules.containsKey(pkg), enabled=supported&&store.enabled(pkg);
        diagnostic("APP_OPEN","package",pkg,"name",appName(pkg),"source",source,"supported",supported,"enabled",enabled,"generic",genericApps.contains(pkg));
        boolean ucRect="com.UCMobile".equals(pkg)&&enabled&&!rules.get(pkg).activeRectangles().isEmpty();
        store.log("进入应用："+appName(pkg)+"（"+pkg+"）"+(enabled?(ucRect?" · UC 框选 OCR、控件和模板并行；普通 OCR 已暂停以避免误触":" · 框选 OCR、普通 OCR、控件和模板同级并行，命中即点"):supported?" · 识别已暂停":""));
        if(enabled){
            diagnostic("SESSION_START","package",pkg,"name",appName(pkg),"windowMs",LaunchPolicy.windowMs(foreground),"frameBudgetMs",LaunchPolicy.IMAGE_MS,"screen",screen().flattenToString(),"generic",genericApps.contains(pkg),"masterEnabled",store.enabled(),"collecting",store.collecting(),"sampleSession",started,"ruleIds",rules.get(pkg).skipIds.toString(),"template",rules.get(pkg).visualTemplate,"variantCount",rules.get(pkg).visualVariants.size(),"outcomePolicy","observation_only_no_causal_proof","rectanglePicking",rectanglePicker!=null,"rectangleEnabled",!rules.get(pkg).activeRectangles().isEmpty(),"rectangleCount",rules.get(pkg).activeRectangles().size(),"rectangleOcrOnly",ucRect);
            diagnosticConfiguration(pkg);
            final long token=generation;
            samplePoll(rules.get(pkg),generation);
            startRecognition(rules.get(pkg),token);
        }
    }
    private void diagnosticConfiguration(String pkg){
        if(!store.diagnostics())return;
        try{
            android.content.pm.PackageInfo app=getPackageManager().getPackageInfo(pkg,0);
            Hotspot hs=hotspots!=null?hotspots.getBestHotspot(pkg,screen().width(),screen().height()):null;
            diagnostic("SESSION_CONFIG","appVersion",app.versionName,"rulesJson",store.rulesJson(),
                "imageIntervalMs",LaunchPolicy.imageInterval(pkg,elapsed()),"maxAttempts",-1,"attemptPolicy","fresh_target_until_deadline","templateThreshold",0.60,
                "ocrRegions","com.UCMobile".equals(pkg)&&!rules.get(pkg).activeRectangles().isEmpty()?"saved_rectangles_only":"manual,hotspots,corners_all_parallel","manualAttributionAvailable",false,
                "imageOutcomeVerificationAvailable",true,
                "hotspotAvailable",hs!=null,"hotspotHits",hs!=null?hs.hitCount:0);
        }catch(Exception error){diagnostic("SESSION_CONFIG_ERROR","error",error.toString());}
    }
    private void startRecognition(Store.AppRule rule,long token) {
        if(rule==null||!recognitionCurrent(rule,token)||parallelStarted)return;
        parallelStarted=true;diagnostic("PARALLEL_START","frameBudgetMs",LaunchPolicy.IMAGE_MS,"priority","none");
        ocrPoll(rule,token);queuePoll(0);
    }
    private AccessibilityNodeInfo timedRoot(Trace trace) {
        long before = SystemClock.elapsedRealtime();
        boolean tracing=LaunchPolicy.active(elapsed(),foreground);
        if(tracing)diagnostic(trace,"API_BEGIN", "api", "root");
        AccessibilityNodeInfo root = android.os.Build.VERSION.SDK_INT >= 33 ? getRootInActiveWindow(0) : getRootInActiveWindow();
        if(tracing)diagnostic(trace,"API_END", "api", "root", "durationMs", SystemClock.elapsedRealtime() - before, "package", root == null ? "" : text(root.getPackageName()));
        return root;
    }
    private void observeForeground() {
        if(foregroundProbeInFlight||SystemClock.elapsedRealtime()-lastWindowEventAt<350)return;
        // Window events are primary during the launch session. Roots are recovery probes.
        if(rules.containsKey(foreground)&&LaunchPolicy.active(elapsed(),foreground))return;
        foregroundProbeInFlight=true;final long token=generation;final Trace trace=task("foreground");
        probes.execute(()->{
            String pkg="";int windowId=-1;
            try{AccessibilityNodeInfo root=timedRoot(trace);if(root!=null){pkg=text(root.getPackageName());windowId=root.getWindowId();root.recycle();}}
            catch(Exception error){diagnostic(trace,"FOREGROUND_ERROR","error",error.toString());}
            final String observed=pkg;final int observedWindow=windowId;
            handler.post(()->{
                foregroundProbeInFlight=false;
                if(!destroyed&&generation==token&&SystemClock.elapsedRealtime()-lastWindowEventAt>=350&&!observed.isEmpty()
                    &&!observed.equals("com.android.systemui")){focusedWindowId=observedWindow;windowOwners.put(observedWindow,observed);activateForeground(observed,"root_probe");}
            });
        });
    }
    private void ocrPoll(Store.AppRule rule,long token) {
        if(destroyed||generation!=token||!parallelStarted||done||imageEnded||!LaunchPolicy.active(elapsed(),foreground)||!store.enabled(rule.pkg))return;
        captureVisual(rule);
        handler.postDelayed(()->ocrPoll(rule,token),LaunchPolicy.captureDelay(rule.pkg,elapsed(),SystemClock.elapsedRealtime()-lastCapture,captureInFlight));
    }
    private void endImagePhase(long token,String reason) {
        if(destroyed||generation!=token||done||imageEnded)return;
        imageEnded=true;
        diagnostic("IMAGE_DISABLED","reason",reason);
        store.log(appName(foreground)+"：图像不可用（"+reason+"），文字识别继续");
        queuePoll(0);
    }

    private void samplePoll(Store.AppRule rule,long token) {
        if(destroyed||generation!=token||!LaunchPolicy.active(elapsed(),rule.pkg)||!rules.containsKey(rule.pkg)||!store.enabled(rule.pkg))return;
        // Observe after a winner finishes, or when image recognition is unavailable.
        if(samples.wants(started,elapsed())&&(done||imageEnded)&&!getSystemService(KeyguardManager.class).isKeyguardLocked())captureFrame(rule,true);
        handler.postDelayed(()->samplePoll(rule,token),600);
    }

    private final Runnable collectionExpiry=new Runnable(){
        public void run(){if(destroyed)return;store.collecting();handler.postDelayed(this,30000);}
    };
    private void recycle(List<AccessibilityNodeInfo> nodes) { for (AccessibilityNodeInfo n : nodes) n.recycle(); }
    private Rect screen() {
        if (android.os.Build.VERSION.SDK_INT >= 30) return getSystemService(android.view.WindowManager.class).getMaximumWindowMetrics().getBounds();
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getSystemService(android.view.WindowManager.class).getDefaultDisplay().getRealMetrics(dm);
        return new Rect(0, 0, dm.widthPixels, dm.heightPixels);
    }
    private String text(CharSequence text) { return text == null ? "" : text.toString(); }
    private List<AccessibilityNodeInfo> candidates(AccessibilityNodeInfo root, Store.AppRule rule, Trace trace) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        for (String token : new String[]{"跳过","关闭","×","X"}) {
            long before = SystemClock.elapsedRealtime(); diagnostic(trace,"API_BEGIN", "api", "findText", "token", token);
            if(!token.equals("跳过")&&!nodes.isEmpty()&&(Matcher.isSkip(text(nodes.get(0).getText()))||Matcher.isSkip(text(nodes.get(0).getContentDescription()))))break;
            nodes.addAll(root.findAccessibilityNodeInfosByText(token));
            diagnostic(trace,"API_END", "api", "findText", "durationMs", SystemClock.elapsedRealtime() - before, "count", nodes.size());
        }
        for (String id : rule.skipIds) {
            long before = SystemClock.elapsedRealtime(); diagnostic(trace,"API_BEGIN", "api", "findId", "id", id);
            nodes.addAll(root.findAccessibilityNodeInfosByViewId(id));
            diagnostic(trace,"API_END", "api", "findId", "durationMs", SystemClock.elapsedRealtime() - before, "count", nodes.size());
        }
        return nodes;
    }
    private boolean eligible(AccessibilityNodeInfo n, Store.AppRule rule, Rect screen, boolean adContext) {
        if (!n.isVisibleToUser() || !n.isEnabled() || !LaunchPolicy.sameOwner(rule.pkg,text(n.getPackageName()))) return false;
        Rect b = new Rect(); n.getBoundsInScreen(b);
        boolean id = rule.skipIds.contains(n.getViewIdResourceName());
        boolean accepted=Matcher.accept(text(n.getText()), id, adContext, b.left, b.top, b.right, b.bottom, screen.width(), screen.height(), elapsed(),rule.pkg)
            || Matcher.accept(text(n.getContentDescription()), id, adContext, b.left, b.top, b.right, b.bottom, screen.width(), screen.height(), elapsed(),rule.pkg);
        return accepted&&(!genericApps.contains(rule.pkg)||Matcher.splashCorner(b.left,b.top,b.right,b.bottom,screen.width(),screen.height()));
    }
    // Binder node calls stay on the probe worker, including ad evidence for close actions.
    private boolean nodeAdContext(AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes){
        boolean hasClose=false;
        for(AccessibilityNodeInfo n:nodes)if(Matcher.isClose(text(n.getText()))||Matcher.isClose(text(n.getContentDescription())))hasClose=true;
        if(!hasClose)return false;
        List<AccessibilityNodeInfo> markers=root.findAccessibilityNodeInfosByText("广告");
        try{for(AccessibilityNodeInfo marker:markers)if(marker.isVisibleToUser()
            &&(Matcher.adSignal(text(marker.getText()))||Matcher.adSignal(text(marker.getContentDescription()))))return true;}
        finally{recycle(markers);}
        return false;
    }
    private String candidateReason(AccessibilityNodeInfo n,Store.AppRule rule,Rect display,boolean adContext){
        if(!n.isVisibleToUser())return "not_visible";
        if(!n.isEnabled())return "not_enabled";
        if(!LaunchPolicy.sameOwner(rule.pkg,text(n.getPackageName())))return "package_mismatch";
        Rect b=new Rect();n.getBoundsInScreen(b);
        if(genericApps.contains(rule.pkg)&&!Matcher.splashCorner(b.left,b.top,b.right,b.bottom,display.width(),display.height()))return "generic_position_filter";
        if(!LaunchPolicy.active(elapsed(),rule.pkg))return "session_deadline";
        if(b.width()<=0||b.height()<=0||b.left<0||b.top<0||b.right>display.width()||b.bottom>display.height())return "invalid_bounds";
        if(b.width()>display.width()*.42||b.height()>display.height()*.12)return "target_too_large";
        return eligible(n,rule,display,adContext)?"accepted":"text_mismatch_or_id_without_ad_evidence";
    }
    private String signature(AccessibilityNodeInfo n) {
        Rect b = new Rect(); n.getBoundsInScreen(b);
        return text(n.getViewIdResourceName()) + ":" + b.flattenToString();
    }
    private void scan() {
        if (rectanglePicker!=null || !parallelStarted || scanInFlight || done || !LaunchPolicy.active(elapsed(),foreground) || !store.enabled(foreground)) return;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard.isKeyguardLocked()) return;
        Store.AppRule rule = rules.get(foreground); if (rule == null) return;
        long now=SystemClock.elapsedRealtime();if(now-lastTextRequest<200)return;lastTextRequest=now;
        Rect display=screen(); final long token=generation; scanInFlight=true;final Trace trace=task("scan");
        diagnostic(trace,"SCAN_REQUEST");
        probes.execute(()->{
            AccessibilityNodeInfo root=null;List<AccessibilityNodeInfo> nodes=null;
            try {
                root=timedRoot(trace);
                if(root==null||!LaunchPolicy.sameOwner(rule.pkg,text(root.getPackageName()))){
                    final String observed=root==null?"":text(root.getPackageName());final int id=root==null?-1:root.getWindowId();
                    diagnostic(trace,"SCAN_ROOT_UNAVAILABLE","observedPackage",observed,"windowId",id);
                    if(root!=null)root.recycle();
                    handler.post(()->{scanInFlight=false;
                        if(generation==token&&!observed.isEmpty()&&!observed.equals("com.android.systemui")){
                            windowOwners.put(id,observed);focusSequence++;requestWindowFocus();
                        }
                    });return;
                }
                nodes=candidates(root,rule,trace);final boolean adContext=nodeAdContext(root,nodes);final List<AccessibilityNodeInfo> result=nodes;final AccessibilityNodeInfo targetRoot=root;
                handler.post(()->{scanInFlight=false;handleNodes(rule,targetRoot,result,display,token,trace,adContext);queuePoll(200);});
            }catch(Exception error){
                if(nodes!=null)recycle(nodes);if(root!=null)root.recycle();
                handler.post(()->{scanInFlight=false;diagnostic(trace,"SCAN_ERROR","error",error.toString());});
            }
        });
    }
    private void handleNodes(Store.AppRule rule,AccessibilityNodeInfo root,List<AccessibilityNodeInfo> nodes,Rect screen,long scanToken,Trace trace,boolean adContext) {
        try {
            if(destroyed||generation!=scanToken||!recognitionCurrent(rule,scanToken)||!LaunchPolicy.active(elapsed(),foreground)){diagnostic(trace,"SCAN_DISCARDED","reason",gateReason(rule,scanToken),"decisionClass",classifyDecision(gateReason(rule,scanToken),"scan_nodes",false,null));return;}
            if(!LaunchPolicy.sameWindow(root.getWindowId(),focusedWindowId)){
                // Delegated ad overlays may expose a root before a package-bearing
                // window event. Learn its owner before resolving the focused surface.
                if(LaunchPolicy.sameOwner(rule.pkg,text(root.getPackageName()))){
                    windowOwners.put(root.getWindowId(),text(root.getPackageName()));focusSequence++;requestWindowFocus();
                }
                diagnostic(trace,"SCAN_DISCARDED","reason","window_changed","decisionClass","WINDOW_MISMATCH");return;
            }
            scanCount++;maxNodes=Math.max(maxNodes,nodes.size());
            // Coordinate fallback must also respect rotation, never apply portrait rules in landscape.
            if (screen.width() >= screen.height()){diagnostic(trace,"SCAN_DISCARDED","reason","landscape","decisionClass","GEOMETRY_FILTER");return;}
            Hotspot hs = hotspots != null ? hotspots.getBestHotspot(rule.pkg, screen.width(), screen.height()) : null;
            AccessibilityNodeInfo best = null;
            for (AccessibilityNodeInfo n : nodes) if (eligible(n, rule, screen, adContext)) {
                if (best == null) {
                    best = n;
                } else {
                    boolean nHasSkip = Matcher.isSkip(text(n.getText())) || Matcher.isSkip(text(n.getContentDescription()));
                    boolean bestHasSkip = Matcher.isSkip(text(best.getText())) || Matcher.isSkip(text(best.getContentDescription()));
                    if (!bestHasSkip && nHasSkip) {
                        best = n;
                    } else if (bestHasSkip == nHasSkip && hs != null) {
                        Rect nb = new Rect(); n.getBoundsInScreen(nb);
                        Rect bb = new Rect(); best.getBoundsInScreen(bb);
                        boolean nInHotspot = hs.intersects(nb.left, nb.top, nb.right, nb.bottom);
                        boolean bInHotspot = hs.intersects(bb.left, bb.top, bb.right, bb.bottom);
                        if (nInHotspot && !bInHotspot) best = n;
                    }
                }
            }
            if (store.diagnostics()) {
                lastScanLog = SystemClock.elapsedRealtime();
                diagnostic(trace,"SCAN", "candidateNodes", nodes.size(), "screen", screen.flattenToString(), "adContext", adContext, "matched", best != null, "scanCount", scanCount, "hotspotUsed", hs != null);
                int logged = 0;
                long capturedMs = Math.max(0, trace.created - trace.start);
                long resolvedMs = Math.max(0, SystemClock.elapsedRealtime() - trace.start);
                for (AccessibilityNodeInfo n : nodes) {
                    if (logged++ >= 10) break;
                    String value = text(n.getText()), desc = text(n.getContentDescription()), id = text(n.getViewIdResourceName());
                    {
                        Rect b = new Rect(); n.getBoundsInScreen(b);
                        diagnostic(trace,"CANDIDATE", "id", id, "text", value.length() <= 24 ? value : "[long text]", "description", desc.length() <= 24 ? desc : "[long text]", "bounds", b.flattenToString(), "visible", n.isVisibleToUser(), "enabled", n.isEnabled(), "clickable", n.isClickable(), "nodePackage", text(n.getPackageName()), "verifiedId", rule.skipIds.contains(id), "accepted", eligible(n,rule,screen,adContext),"reason",candidateReason(n,rule,screen,adContext),
                            "targetFrameCapturedAtMs", capturedMs, "targetResolvedAtMs", resolvedMs);
                    }
                }
            }
            if (best == null) return;
            Rect bounds=new Rect();best.getBoundsInScreen(bounds);
            if(SystemClock.elapsedRealtime()-trace.created<LaunchPolicy.FRESH_IMAGE_MS)
                releaseObservationForRetry(scanToken,root.getWindowId(),bounds,SystemClock.elapsedRealtime());
            offerTarget(new Target(rule,scanToken,root.getWindowId(),trace.created,"text",text(best.getText()),bounds,trace));
        } finally { recycle(nodes); root.recycle(); }
    }
    private double visualScore(Bitmap image, Store.AppRule rule) throws Exception {
        Bitmap original=visualTemplates.get(rule.visualTemplate);
        if(original==null){try(java.io.InputStream in=getAssets().open("visual/"+rule.visualTemplate+".png")){original=BitmapFactory.decodeStream(in);}visualTemplates.put(rule.visualTemplate,original);}
        double sx=(double)image.getWidth()/rule.referenceWidth,sy=(double)image.getHeight()/rule.referenceHeight;
        int w=Math.max(1,(int)Math.round(original.getWidth()*sx)),h=Math.max(1,(int)Math.round(original.getHeight()*sy));
        Bitmap reference=Bitmap.createScaledBitmap(original,w,h,false);
        int[] refPixels=new int[w*h];reference.getPixels(refPixels,0,w,0,0,w,h);
        boolean[] mask=new boolean[w*h];for(int i=0;i<mask.length;i++)mask[i]=(refPixels[i]&255)>128;
        if(reference!=original)reference.recycle();
        int x=(int)Math.round(rule.textBounds[0]*sx),y=(int)Math.round(rule.textBounds[1]*sy);
        return VisualMatcher.search(mask,w,h,x,y,image.getWidth(),image.getHeight(),rule.darkText,
            (pixels,left,top,width,height)->image.getPixels(pixels,0,width,left,top,width,height));
    }
    private VisualMatcher.Patch targetProof(Bitmap frame,Rect bounds){
        Rect safe=new Rect(bounds);if(!safe.intersect(0,0,frame.getWidth(),frame.getHeight()))return null;
        return new VisualMatcher.Patch(safe.left,safe.top,safe.width(),safe.height(),
            (pixels,x,y,w,h)->frame.getPixels(pixels,0,w,x,y,w,h));
    }
    private boolean imageCurrent(Store.AppRule rule,long token) {
        return recognitionCurrent(rule,token)&&!imageEnded;
    }
    private void captureVisual(Store.AppRule rule) {captureFrame(rule,false);}
    private void captureFrame(Store.AppRule rule,boolean diagnosticOnly) {
        if(rectanglePicker!=null)return;
        if(android.os.Build.VERSION.SDK_INT<30||ocr==null){endImagePhase(generation,"image_unavailable");return;}
        Target watch=observation;
        boolean observing=watch!=null&&watch.token==generation&&watch.rule.pkg.equals(rule.pkg)
            &&SystemClock.elapsedRealtime()-watch.completedAt<OBSERVATION_WINDOW_MS;
        if(captureInFlight||(!diagnosticOnly&&!imageCurrent(rule,generation)&&!observing)
            ||SystemClock.elapsedRealtime()-lastCapture<LaunchPolicy.imageInterval(rule.pkg,elapsed()))return;
        lastCapture=SystemClock.elapsedRealtime();captureInFlight=true;
        final long token=generation,before=lastCapture;final int windowId=focusedWindowId;final Trace trace=task("frame");
        diagnostic(trace,"IMAGE_CAPTURE_BEGIN","package",rule.pkg,"windowId",windowId,"diagnosticOnly",diagnosticOnly);
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
            @Override public void onFailure(int code){
                captureInFlight=false;diagnostic(trace,"SCREENSHOT_ERROR","errorCode",code);
                Target active=observation;
                if(active!=null&&active.token==token&&active.rule.pkg.equals(rule.pkg))active.captureErrors++;
                if(!diagnosticOnly&&code!=ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT)endImagePhase(token,"screenshot_error_"+code);
            }
            @Override public void onSuccess(ScreenshotResult result){
                diagnostic(trace,"IMAGE_CAPTURE_END","captureMs",SystemClock.elapsedRealtime()-before,"screenshotTimestamp",result.getTimestamp());
                if(destroyed){result.getHardwareBuffer().close();captureInFlight=false;return;}
                images.execute(()->processFrame(result,rule,token,before,windowId,diagnosticOnly,trace));
            }
        });
    }
    private void processFrame(ScreenshotResult result,Store.AppRule rule,long token,long before,int windowId,boolean diagnosticOnly,Trace trace){
        Bitmap hardware=null,bitmap=null;
        try{hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());if(hardware!=null)bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);}
        catch(Exception error){diagnostic(trace,"SCREENSHOT_ERROR","error",error.toString());}
        finally{if(hardware!=null)hardware.recycle();result.getHardwareBuffer().close();handler.post(()->captureInFlight=false);}
        if(bitmap==null){handler.post(()->endImagePhase(token,"empty_frame"));return;}
        Frame frame=new Frame(bitmap);Bitmap image=frame.bitmap;
        diagnostic(trace,"FRAME_READY","width",image.getWidth(),"height",image.getHeight(),"frameAgeMs",SystemClock.elapsedRealtime()-before);
        if(!destroyed&&generation==token)try{samples.save(image,rule.pkg,trace.start,SystemClock.elapsedRealtime()-trace.start,windowId,diagnosticOnly?"observe_only":"image_phase",serviceRun+":"+trace.token,trace.work);}catch(Exception error){diagnostic(trace,"SAMPLE_ERROR","error",error.toString());}
        try{
            if(destroyed||generation!=token||image.getWidth()>=image.getHeight())return;
            if(!diagnosticOnly)observeFrame(frame,token,before,windowId);
            if(diagnosticOnly||!imageCurrent(rule,token)||!LaunchPolicy.imageActive(SystemClock.elapsedRealtime()-before))return;
            rememberFrame(frame,token,before,windowId);
            List<Rect> regions=new ArrayList<>(),hints=new ArrayList<>();
            // Saved coordinate delays were for blind taps. Recognition starts immediately.
            for(RectRule rectangle:rule.activeRectangles()){
                int[] b=rectangle.map(image.getWidth(),image.getHeight());if(b!=null)regions.add(new Rect(b[0],b[1],b[2],b[3]));
            }
            if(hotspots!=null)for(Hotspot stored:hotspots.getHotspots(rule.pkg)){
                if(!stored.predictive())continue;
                Hotspot hs=stored.scaleTo(image.getWidth(),image.getHeight());hints.add(new Rect(hs.left,hs.top,hs.right,hs.bottom));
            }
            if(!regions.isEmpty())submitOcr(frame,rule,token,before,windowId,regions,new ArrayList<>(),false,rectangleOcr,rectangleOcrBusy,rectangleTasks,"rectangle_ocr",trace);
            boolean generalOcr=LaunchPolicy.runGeneralOcr(rule.pkg,!regions.isEmpty(),elapsed(),lastUcGeneralOcrAt);
            if(generalOcr){lastUcGeneralOcrAt=elapsed();submitOcr(frame,rule,token,before,windowId,new ArrayList<>(),hints,true,ocr,ocrBusy,ocrTasks,"ocr",trace);}
            if((rule.visualTemplate!=null||!rule.visualVariants.isEmpty())&&templateBusy.compareAndSet(false,true)){
                frame.retain();final Trace work=task("template");diagnostic(work,"CHANNEL_SUBMIT","channel","template","capturedAt",before);
                templates.execute(()->{
                    try{
                        List<Store.AppRule> styles=new ArrayList<>();if(rule.visualTemplate!=null)styles.add(rule);styles.addAll(rule.visualVariants);
                        for(Store.AppRule style:styles){
                            if(destroyed||generation!=token||!LaunchPolicy.imageActive(SystemClock.elapsedRealtime()-before))break;
                            double score=visualScore(image,style);diagnostic(work,"VISUAL_MATCH","template",style.visualTemplate,"score",score,"accepted",score>=.60,"durationMs",SystemClock.elapsedRealtime()-before);
                            if(score>=.60){
                                int x=(int)((long)style.tap[0]*image.getWidth()/style.referenceWidth),y=(int)((long)style.tap[1]*image.getHeight()/style.referenceHeight);
                                Target target=new Target(rule,token,windowId,before,"template",style.visualTemplate,new Rect(x-50,y-25,x+50,y+25),work);
                                target.proof=targetProof(image,target.bounds);
                                handler.post(()->offerTarget(target));
                            }
                        }
                    }catch(Exception error){diagnostic(work,"IMAGE_ERROR","channel","template","error",error.toString());}
                    finally{templateBusy.set(false);frame.release();}
                });
            }
        }finally{frame.release();}
    }
    private void submitOcr(Frame frame,Store.AppRule rule,long token,long before,int windowId,List<Rect> regions,List<Rect> hints,boolean corners,
                           OcrEngine engine,java.util.concurrent.atomic.AtomicBoolean busy,java.util.concurrent.ExecutorService executor,String channel,Trace origin){
        if(engine==null||!busy.compareAndSet(false,true)){
            if("com.UCMobile".equals(rule.pkg)&&generation==token){if("rectangle_ocr".equals(channel))ucRectangleBusy.incrementAndGet();else if("ocr".equals(channel))ucGeneralBusy.incrementAndGet();}
            diagnostic(origin,"CHANNEL_BUSY","channel",channel);return;
        }
        if("com.UCMobile".equals(rule.pkg)&&"rectangle_ocr".equals(channel)&&generation==token)ucRectangleSubmitted.incrementAndGet();
        frame.retain();final Trace work=task(channel);diagnostic(work,"CHANNEL_SUBMIT","channel",channel,"capturedAt",before);
        executor.execute(()->{
            try{
                java.util.concurrent.atomic.AtomicBoolean found=new java.util.concurrent.atomic.AtomicBoolean();
                engine.recognizeParallel(frame.bitmap,regions,hints,corners,store.diagnostics()?raw->diagnostic(work,"OCR_REGION","detail",raw):null,
                    ()->!destroyed&&generation==token&&LaunchPolicy.active(elapsed(),foreground)
                        &&(LaunchPolicy.imageActive(SystemClock.elapsedRealtime()-before)
                            ||("com.UCMobile".equals(rule.pkg)&&SystemClock.elapsedRealtime()-before<LaunchPolicy.UC_OCR_MAX_MS)),
                    (bounds,value,error)->{
                        found.set(true);
                        if("com.UCMobile".equals(rule.pkg)&&"rectangle_ocr".equals(channel)&&generation==token)ucRectangleFound.incrementAndGet();
                        Target target=new Target(rule,token,windowId,before,channel,value,bounds,work);
                        target.proof=targetProof(frame.bitmap,bounds);
                        diagnostic(work,"TARGET_FOUND","channel",channel,"text",value,"bounds",bounds.flattenToString(),"frameAgeMs",SystemClock.elapsedRealtime()-before);
                        handler.post(()->offerTarget(target));
                    },()->{if("com.UCMobile".equals(rule.pkg)&&"rectangle_ocr".equals(channel)&&!found.get()&&generation==token)ucRectangleMissed.incrementAndGet();
                        busy.set(false);frame.release();diagnostic(work,"CHANNEL_FINISHED","channel",channel,"durationMs",SystemClock.elapsedRealtime()-before);});
            }catch(Exception error){busy.set(false);frame.release();diagnostic(work,"IMAGE_ERROR","channel",channel,"error",error.toString());}
        });
    }
    private void observationTick(Target target){
        if(destroyed||observation!=target||generation!=target.token)return;
        long age=SystemClock.elapsedRealtime()-target.completedAt;
        if(age>=OBSERVATION_WINDOW_MS){
            observation=null;
            boolean stillPresent=target.presentFrames>=2&&target.absentFrames==0;
            String state=stillPresent?"button_present":"unavailable";
            String reason=target.absentFrames==1?"one_absent_frame":target.observationDecisionPending?"result_pending_main_thread"
                :target.observationBusyFrames>0&&target.observationRequests==0?"ocr_busy"
                :target.observationRequests>target.presentFrames+target.absentFrames+target.observationErrors?"ocr_pending"
                :target.observationErrors>0?"ocr_error":target.captureErrors>0?"screenshot_error":"no_fresh_frame";
            target.observationOutcome=stillPresent?state:state+":"+reason;
            diagnostic(target.trace,"POST_CLICK_RESULT","attemptId",target.attemptId,"state",state,"reason",reason,
                "presentFrames",target.presentFrames,"absentFrames",target.absentFrames,"frames",target.observationFrames,
                "ocrSubmitted",target.observationRequests,"ocrBusyFrames",target.observationBusyFrames,
                "ocrErrors",target.observationErrors,"screenshotErrors",target.captureErrors,
                "decisionPending",target.observationDecisionPending,"confirmedSkip",false);
            store.log(target.rule.name+"："+(stillPresent?"点击后两次 OCR 仍见跳过按钮，继续识别；最终是否跳过待确认":"点击后复查无法判断（"+reason+"；截图帧"+target.observationFrames+"、OCR提交"+target.observationRequests+"、忙跳过"+target.observationBusyFrames+"、OCR错误"+target.observationErrors+"、截屏错误"+target.captureErrors+"）"));
            return;
        }
        if(age>=150&&!captureInFlight)captureFrame(target.rule,false);
        handler.postDelayed(()->observationTick(target),350);
    }
    private void observeFrame(Frame frame,long token,long capturedAt,int window){
        Target target=observation;OcrEngine engine=observationOcr;
        if(target==null||target.token!=token||capturedAt<=target.lastObservationCaptureAt||capturedAt<target.completedAt+100
            ||capturedAt>=target.completedAt+OBSERVATION_WINDOW_MS)return;
        target.observationFrames++;
        if(engine==null){target.observationErrors++;return;}
        if(target.observationDecisionPending||!observationBusy.compareAndSet(false,true)){
            target.observationBusyFrames++;
            diagnostic(target.trace,"POST_CLICK_OCR_BUSY","attemptId",target.attemptId,"capturedAt",capturedAt,
                "decisionPending",target.observationDecisionPending);
            return;
        }
        target.lastObservationCaptureAt=capturedAt;
        target.observationRequests++;
        diagnostic(target.trace,"POST_CLICK_OCR_SUBMIT","attemptId",target.attemptId,"capturedAt",capturedAt,
            "frameAfterClickMs",capturedAt-target.completedAt,"request",target.observationRequests);
        frame.retain();observations.execute(()->{
            Runnable complete=()->{observationBusy.set(false);frame.release();};
            try{
                // OCR the original button area on a fresh frame. Countdown pixels can
                // change while the button remains, so pixel similarity is not a verdict.
                final int width=frame.bitmap.getWidth(),height=frame.bitmap.getHeight();
                final long began=SystemClock.elapsedRealtime();
                engine.recognizeTarget(frame.bitmap,target.bounds,(remaining,text,error)->{
                    final long duration=SystemClock.elapsedRealtime()-began;
                    target.observationDecisionPending=true;
                    diagnostic(target.trace,"POST_CLICK_OCR_RESULT","attemptId",target.attemptId,"durationMs",duration,
                        "capturedAt",capturedAt,"bounds",remaining==null?"":remaining.flattenToString(),
                        "text",text,"error",error==null?"":error.toString());
                    complete.run();
                    handler.post(()->{
                        try{
                            if(error!=null){target.observationErrors++;return;}
                            if(destroyed||generation!=token||observation!=target||!store.enabled(target.rule.pkg)
                                ||!target.rule.pkg.equals(foreground))return;
                            boolean originalClose=Matcher.isClose(target.text);
                            boolean sameAction=originalClose?Matcher.isClose(text):!Matcher.ocrSkipText(text).isEmpty();
                            boolean present=remaining!=null&&sameButton(target.bounds,remaining)&&sameAction;
                            if(present){target.presentFrames++;target.absentFrames=0;}
                            else target.absentFrames++;
                            diagnostic(target.trace,"POST_CLICK_OBSERVATION","attemptId",target.attemptId,"state",present?"button_present":"button_absent",
                                "presentFrames",target.presentFrames,"absentFrames",target.absentFrames,"capturedAt",capturedAt,"confirmedSkip",false);
                            if(present&&SystemClock.elapsedRealtime()-target.completedAt>=350){
                                observation=null;
                                target.observationOutcome="button_present_in_ocr";
                                diagnostic(target.trace,"POST_CLICK_RESULT","attemptId",target.attemptId,"state","button_present_in_ocr","confirmedSkip",false);
                                store.log(target.rule.name+"：点击后短时复查仍见跳过按钮，继续识别并择机重试；最终是否跳过待确认");
                                queuePoll(0);
                                return;
                            }
                            if(target.absentFrames<2)return;
                            observation=null;
                            target.observationOutcome="button_absent_twice";
                            if(hotspots!=null&&!"com.UCMobile".equals(target.rule.pkg))
                                hotspots.recordHit(target.rule.pkg,target.bounds.left,target.bounds.top,target.bounds.right,target.bounds.bottom,width,height,target.source);
                            store.observedChange(target.rule.pkg,target.rule.name);
                            diagnostic(target.trace,"POST_CLICK_RESULT","attemptId",target.attemptId,"state","button_absent_twice","confirmedSkip",false);
                            done=true;endSession("button_absent_after_click");
                        }finally{target.observationDecisionPending=false;}
                    });
                });
            }catch(Exception error){target.observationErrors++;diagnostic(target.trace,"OBSERVATION_ERROR","attemptId",target.attemptId,"error",error.toString());complete.run();}
        });
    }
    private boolean recognitionCurrent(Store.AppRule rule,long token){
        return rule!=null&&!destroyed&&rectanglePicker==null&&generation==token&&!done&&store.enabled(rule.pkg)&&LaunchPolicy.active(elapsed(),foreground)
            &&!getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    private boolean current(Store.AppRule rule,long token){return recognitionCurrent(rule,token)&&!pending&&clickGate.available(token);}
    private boolean claimClick(Store.AppRule rule,long token,String source,Trace trace) {
        String gate = gateReason(rule,token);
        if(source.equals("rectangle")||source.equals("music_fixed")){
            diagnostic(trace,"CLICK_CLAIM_REJECTED","reason","unverified_coordinate","source",source);return false;
        }
        if(!current(rule,token)||!clickGate.claim(token,trace.work,SystemClock.elapsedRealtime())){
            String dClass = classifyDecision(gate, source, false, null);
            diagnostic(trace,"CLICK_CLAIM_REJECTED","originGeneration",token,"reason",gate,
                "decisionClass",dClass,"blockedByWorkId",activeClaimWorkId,"blockedByStrategy",activeClaimStrategy);
            return false;
        }
        pending=true;attempts++;
        activeClaimWorkId=trace.work;
        activeClaimStrategy=source;
        diagnostic(trace,"CLICK_CLAIM","source",source,"session",token,"attempt",attempts);
        // Android Binder queries and OCR callbacks may never return. Bound ownership
        // on the main handler and fence every late result by this specific attempt.
        handler.postDelayed(()->{
            if(!ownsClick(token,trace)||!clickGate.overdue(token,trace.work,SystemClock.elapsedRealtime()))return;
            diagnostic(trace,"GESTURE_TIMEOUT","source",source,"holdMs",ClickGate.HOLD_MS);
            releaseClick(token,"gesture_timeout",trace);
            store.log(rule.name+"：点击回调超时，已释放手势占用，识别继续");
            if(!parallelStarted)startRecognition(rule,token);
        },ClickGate.HOLD_MS);
        return true;
    }
    private boolean ownsClick(long token,Trace trace){
        return !destroyed&&generation==token&&!done&&pending&&clickGate.owns(token,trace.work);
    }
    private void releaseClick(long token,String reason,Trace trace) {
        if(!clickGate.release(token,trace.work))return;
        if(generation==token){
            pending=false;
            activeClaimWorkId="";
            activeClaimStrategy="";
            diagnostic(trace,"CLICK_RELEASE","reason",reason,"session",token);
            queuePoll(0);handler.post(this::drainTargets);
        }
    }
    private boolean validTarget(Target target){
        long age=SystemClock.elapsedRealtime()-target.capturedAt;
        boolean fresh=LaunchPolicy.imageActive(age)&&age<LaunchPolicy.FRESH_IMAGE_MS;
        boolean ucRefresh=!fresh&&"com.UCMobile".equals(target.rule.pkg)
            &&(target.source.equals("ocr")||target.source.equals("rectangle_ocr"))&&age>=0&&age<LaunchPolicy.UC_OCR_MAX_MS;
        return recognitionCurrent(target.rule,target.token)&&(fresh||ucRefresh)
            &&LaunchPolicy.sameWindow(target.window,focusedWindowId)&&LaunchPolicy.sameOwner(target.rule.pkg,windowOwners.get(target.window))
            &&!(lastClickedWindow==target.window&&target.capturedAt<lastClickedAt)
            &&!target.bounds.isEmpty()&&targetPixelsCurrent(target,ucRefresh);
    }
    private void rememberFrame(Frame frame,long token,long capturedAt,int window) {
        synchronized(latestFrameLock){
            if(destroyed||generation!=token||capturedAt<latestFrameCapturedAt)return;
            frame.retain();
            if(latestFrame!=null)latestFrame.release();
            latestFrame=frame;latestFrameToken=token;latestFrameCapturedAt=capturedAt;latestFrameWindow=window;
        }
    }
    private void clearLatestFrame(){
        synchronized(latestFrameLock){
            if(latestFrame!=null)latestFrame.release();
            latestFrame=null;latestFrameCapturedAt=0;latestFrameWindow=-1;
        }
    }
    private boolean targetPixelsCurrent(Target target,boolean requireNewFrame){
        if(target.proof==null)return !requireNewFrame; // Old OCR must have pixel proof.
        Frame frame;long capturedAt;int window;
        synchronized(latestFrameLock){
            if(latestFrame==null||latestFrameToken!=target.token||latestFrameCapturedAt<=target.capturedAt)return !requireNewFrame;
            frame=latestFrame;capturedAt=latestFrameCapturedAt;window=latestFrameWindow;frame.retain();
        }
        long began=SystemClock.elapsedRealtime();
        try{
            if(requireNewFrame&&SystemClock.elapsedRealtime()-capturedAt>=500)return false;
            double similarity=window==target.window?target.proof.similarity(frame.bitmap.getWidth(),frame.bitmap.getHeight(),
                (pixels,x,y,w,h)->frame.bitmap.getPixels(pixels,0,w,x,y,w,h)):0;
            diagnostic(target.trace,"TARGET_FRAME_CHECK","latestCapturedAt",capturedAt,"refreshed",requireNewFrame,"score",similarity,"accepted",similarity>=.75,"durationMs",SystemClock.elapsedRealtime()-began);
            return similarity>=.75;
        }finally{frame.release();}
    }
    private boolean sameButton(Rect a,Rect b){
        return a!=null&&b!=null&&(Rect.intersects(a,b)||Math.abs(a.centerX()-b.centerX())<30&&Math.abs(a.centerY()-b.centerY())<30);
    }
    private String ucTargetGuard(Target target){
        if("com.UCMobile".equals(target.rule.pkg)&&attempts==0){
            long now=SystemClock.elapsedRealtime();
            if(ucFirstTargetToken!=target.token||ucFirstTargetWindow!=target.window
                ||!sameButton(ucFirstTargetBounds,target.bounds)){
                ucFirstTargetToken=target.token;ucFirstTargetWindow=target.window;
                ucFirstTargetBounds=new Rect(target.bounds);ucFirstTargetSeenAt=now;
            }
            if(now-ucFirstTargetSeenAt<UC_FIRST_TARGET_SETTLE_MS)return "uc_first_target_settling";
        }
        long frameAge=SystemClock.elapsedRealtime()-target.capturedAt;
        return LaunchPolicy.ucClickGuard(target.rule.pkg,target.source,target.text,frameAge,lastUcClickCountdown,
            lastClickedWindow==target.window&&sameButton(lastClickedBounds,target.bounds));
    }
    private void deferredUcTarget(Target target,String reason){
        diagnostic(target.trace,"TARGET_DEFERRED","channel",target.source,"reason",reason,"text",target.text,
            "bounds",target.bounds.flattenToString(),"frameAgeMs",SystemClock.elapsedRealtime()-target.capturedAt,
            "lastClickedCountdown",lastUcClickCountdown);
    }
    private boolean releaseObservationForRetry(long token,int window,Rect bounds,long now){
        Target previous=observation;
        if(previous==null||previous.token!=token||previous.window!=window||!sameButton(previous.bounds,bounds)
            ||now-previous.completedAt<350)return false;
        observation=null;
        previous.observationOutcome="button_present_in_fresh_tree";
        diagnostic(previous.trace,"POST_CLICK_RESULT","state","button_present_in_fresh_tree",
            "retryAfterMs",now-previous.completedAt,"confirmedSkip",false);
        store.log(previous.rule.name+"：点击后控件树仍见跳过按钮，继续识别并择机重试；最终是否跳过待确认");
        return true;
    }
    private void offerTarget(Target target){
        if(!validTarget(target)){diagnostic(target.trace,"TARGET_DISCARDED","channel",target.source,"reason","stale_window_or_pixels_changed","frameAgeMs",SystemClock.elapsedRealtime()-target.capturedAt);return;}
        String ucGuard=ucTargetGuard(target);if(!ucGuard.isEmpty()){deferredUcTarget(target,ucGuard);return;}
        Rect bounds=target.bounds;
        if(!target.source.equals("rectangle_ocr")&&genericApps.contains(target.rule.pkg)&&!Matcher.splashCorner(bounds.left,bounds.top,bounds.right,bounds.bottom,screen().width(),screen().height()))return;
        if(lastClickedWindow==target.window&&((observation!=null&&observation.token==target.token&&sameButton(observation.bounds,bounds))
            ||SystemClock.elapsedRealtime()-lastClickedAt<350&&sameButton(lastClickedBounds,bounds)))return;
        for(Target queued:targets)if(queued.token==target.token&&queued.window==target.window&&sameButton(queued.bounds,bounds))return;
        if(targets.size()>=24)targets.removeFirst();targets.addLast(target);
        drainTargets();
    }
    private void drainTargets(){
        if(pending)return;
        while(!targets.isEmpty()){
            Target target=targets.removeFirst();if(!validTarget(target))continue;
            String ucGuard=ucTargetGuard(target);if(!ucGuard.isEmpty()){deferredUcTarget(target,ucGuard);continue;}
            if(lastClickedWindow==target.window&&((observation!=null&&observation.token==target.token&&sameButton(observation.bounds,target.bounds))
                ||SystemClock.elapsedRealtime()-lastClickedAt<350&&sameButton(lastClickedBounds,target.bounds)))continue;
            dispatchTarget(target);return;
        }
    }
    private void dispatchTarget(Target target){
        Store.AppRule rule=target.rule;long token=target.token;Trace trace=target.trace;
        String ucGuard=ucTargetGuard(target);if(!ucGuard.isEmpty()){deferredUcTarget(target,ucGuard);return;}
        if(!claimClick(rule,token,target.source,trace))return;
        Rect bounds=target.bounds;lastTargetBounds=new Rect(bounds);
        lastClickedBounds=new Rect(bounds);lastClickedAt=SystemClock.elapsedRealtime();lastClickedWindow=target.window;
        String attemptId=serviceRun+":"+token+":"+attemptCounter.incrementAndGet();
        target.attemptId=attemptId;
        float[] tap=targetTap(target);
        Path path=new Path();path.moveTo(tap[0],tap[1]);
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,50)).build();
        diagnostic(trace,target.source.equals("text")?"CLICK":"IMAGE_CLICK","attemptId",attemptId,"strategy",target.source,"text",target.text,
            "x",tap[0],"y",tap[1],"recognitionBounds",bounds.flattenToString(),"attempt",attempts,"frameAgeMs",SystemClock.elapsedRealtime()-target.capturedAt,
            "recognitionToDispatchMs",SystemClock.elapsedRealtime()-target.resolvedAt,
            "tapInsideRecognizedText",bounds.contains((int)tap[0],(int)tap[1]),"tapOffsetX",tap[0]-bounds.exactCenterX(),"tapOffsetY",tap[1]-bounds.exactCenterY());
        boolean accepted=tapDispatcher.dispatch(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gesture){
                if(!ownsClick(token,trace))return;
                diagnostic(trace,"GESTURE_COMPLETED","attemptId",attemptId,"strategy",target.source,"confirmedSkip",false);
                // Completion frees only the short gesture slot. Observation never holds it.
                if(observation!=null&&observation.token==token){
                    observation.observationOutcome="superseded_by_click";
                    store.log(observation.rule.name+"：新目标已点击，上一次原位置 OCR 复查中断，结果无法判断");
                }
                target.completedAt=SystemClock.elapsedRealtime();observation=target;lastCompletedTarget=target;
                handler.postDelayed(()->observationTick(target),150);
                releaseClick(token,"gesture_completed",trace);
                store.log(rule.name+"："+target.source+" 识别到“"+target.text+"”，已尝试点击；系统回报手势完成，按钮是否响应待复查");
            }
            @Override public void onCancelled(GestureDescription gesture){releaseClick(token,"gesture_cancelled",trace);}
        },handler);
        diagnostic(trace,"GESTURE_DISPATCH","attemptId",attemptId,"strategy",target.source,"accepted",accepted);
        if(accepted&&"com.UCMobile".equals(rule.pkg))lastUcClickCountdown=Matcher.skipCountdown(target.text);
        if(!accepted)releaseClick(token,"gesture_rejected",trace);
    }
    private float[] targetTap(Target target){
        Rect b=target.bounds;
        if("com.UCMobile".equals(target.rule.pkg)&&(target.source.equals("ocr")||target.source.equals("rectangle_ocr"))){
            // The selected rectangle limits OCR; its center can be empty space
            // beside the actual skip label. Keep retries inside the fresh text box.
            float radius=Math.min(8f,Math.max(0f,(b.width()-8)/2f));
            float offset=attempts%3==2?-radius:attempts%3==0?radius:0f;
            return new float[]{b.exactCenterX()+offset,b.exactCenterY()};
        }
        return new float[]{b.exactCenterX(),b.exactCenterY()};
    }
    @Override public void onInterrupt() { if (store != null) store.log("无障碍服务被中断"); diagnostic("SERVICE_INTERRUPT"); }
    @Override public void onDestroy() {
        connected = false;instance=null;if(rectanglePicker!=null)rectanglePicker.close(); destroyed=true; probes.shutdownNow(); focusProbes.shutdownNow(); images.shutdown();templates.shutdown();rectangleTasks.shutdown();ocrTasks.shutdown();observations.shutdown();
        clearLatestFrame();
        if(samples!=null)samples.close();
        if (ocr != null) { ocr.close(); ocr = null; }
        if (rectangleOcr != null) { rectangleOcr.close(); rectangleOcr = null; }
        if (observationOcr != null) { observationOcr.close(); observationOcr = null; }
        endSession("service_destroyed");diagnostic("SERVICE_DESTROY");
        Thread.setDefaultUncaughtExceptionHandler(previousCrashHandler);
        handler.removeCallbacksAndMessages(null);
        if (store != null) { store.prefs.unregisterOnSharedPreferenceChangeListener(listener); store.log("无障碍服务已停止"); }
        super.onDestroy();
    }
}
