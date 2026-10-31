package cn.local.adskip;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.io.File;
import java.io.FileOutputStream;

public final class Store {
    public static final class AppRule {
        public String pkg, name, sampledVersion;
        public final Set<String> skipIds = new HashSet<>();
        public RectRule rectangle; // Legacy alias for the first saved rectangle.
        public final List<RectRule> rectangles = new ArrayList<>();
        public List<RectRule> activeRectangles(){
            List<RectRule> active=new ArrayList<>();
            for(RectRule r:rectangles)if(r.enabled)active.add(r);
            return active;
        }
        public boolean rectangleCreatedApp;
        public String visualTemplate;
        public boolean darkText;
        public final List<AppRule> visualVariants=new ArrayList<>();
        public int[] textBounds, tap;
        public int referenceWidth, referenceHeight;
    }
    private final Context context;
    public final SharedPreferences prefs;
    public Store(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (prefs.getInt("capturePolicy", 0) < 1) prefs.edit().putInt("capturePolicy",1).putBoolean("collecting",false).putBoolean("diagnostics",false).apply();
    }
    public boolean diagnostics() { return prefs.getBoolean("diagnostics", false) || collecting(); }
    public void setCollecting(boolean enabled) {
        prefs.edit().putBoolean("collecting",enabled).putLong("collectingStarted",android.os.SystemClock.elapsedRealtime()).apply();
    }
    public boolean collecting() {
        if (!prefs.getBoolean("collecting", false)) return false;
        long age=android.os.SystemClock.elapsedRealtime()-prefs.getLong("collectingStarted",0);
        if(age<0 || age>=CapturePolicy.DURATION_MS) { prefs.edit().putBoolean("collecting",false).apply(); return false; }
        return true;
    }
    private static final Object DIAGNOSTIC_LOCK=new Object();
    private static final java.util.concurrent.atomic.AtomicLong diagnosticSequence=new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong droppedDiagnostics=new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong failedDiagnostics=new java.util.concurrent.atomic.AtomicLong();
    private static final String diagnosticRun=java.util.UUID.randomUUID().toString();
    private static final java.util.concurrent.ThreadPoolExecutor diagnosticWriter=new java.util.concurrent.ThreadPoolExecutor(
        1,1,0,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(2048));
    public void diagnostic(String kind, JSONObject fields) {
        if (!diagnostics() && !kind.startsWith("SERVICE") && !kind.equals("CRASH")) return;
        try {
            JSONObject row=fields==null?new JSONObject():fields;
            row.put("schemaVersion",2).put("writerRun",diagnosticRun).put("sequence",diagnosticSequence.incrementAndGet())
                .put("kind",kind).put("time",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.CHINA).format(new Date()))
                .put("uptimeMs",android.os.SystemClock.elapsedRealtime()).put("queueDepth",diagnosticWriter.getQueue().size())
                .put("droppedBefore",droppedDiagnostics.get()).put("writeErrorsBefore",failedDiagnostics.get());
            final byte[] bytes=(row.toString()+"\n").getBytes(StandardCharsets.UTF_8);
            diagnosticWriter.execute(()->{
                synchronized(DIAGNOSTIC_LOCK){
                    try {
                        File file=new File(context.getFilesDir(),"diagnostic.jsonl");
                        if(file.length()+bytes.length>4L*1024*1024){
                            File old=new File(context.getFilesDir(),"diagnostic.previous.jsonl");
                            long removed=old.length();
                            if(old.exists()&&!old.delete())throw new java.io.IOException("cannot remove previous diagnostic");
                            if(file.exists()&&!file.renameTo(old))throw new java.io.IOException("cannot rotate diagnostic");
                            prefs.edit().putLong("diagnosticRotations",prefs.getLong("diagnosticRotations",0)+1)
                                .putLong("diagnosticEvictedBytes",prefs.getLong("diagnosticEvictedBytes",0)+removed).apply();
                        }
                        try(FileOutputStream out=new FileOutputStream(file,true)){out.write(bytes);}
                    }catch(Exception e){failedDiagnostics.incrementAndGet();android.util.Log.e("AdSkip","diagnostic write failed",e);}
                }
            });
        }catch(java.util.concurrent.RejectedExecutionException e){droppedDiagnostics.incrementAndGet();}
        catch(Exception e){failedDiagnostics.incrementAndGet();}
    }
    public String diagnosticReport() {
        boolean flushed=false;
        try {diagnosticWriter.submit(()->{}).get(3,java.util.concurrent.TimeUnit.SECONDS);flushed=true;}catch(Exception ignored){}
        StringBuilder body=new StringBuilder();
        synchronized(DIAGNOSTIC_LOCK){
            for(String name:new String[]{"diagnostic.previous.jsonl","diagnostic.jsonl"}){
                File f=new File(context.getFilesDir(),name);if(!f.exists())continue;
                try(InputStream in=new java.io.FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))>0)out.write(bytes,0,n);
                    body.append(new String(out.toByteArray(),StandardCharsets.UTF_8));
                }catch(Exception e){body.append("读取日志失败：").append(e.getMessage()).append("\n");}
            }
        }
        String version="unknown",ruleHash="unknown";
        try{version=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(Exception ignored){}
        try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(rulesJson().getBytes(StandardCharsets.UTF_8));StringBuilder h=new StringBuilder();for(byte v:hash)h.append(String.format("%02x",v&255));ruleHash=h.toString();}catch(Exception ignored){}
        StringBuilder report=new StringBuilder(context.getString(R.string.app_name)+"详细诊断报告 schema=2\n")
            .append("appVersion=").append(version).append(" Android=").append(android.os.Build.VERSION.RELEASE).append(" API=").append(android.os.Build.VERSION.SDK_INT)
            .append(" model=").append(android.os.Build.MODEL).append("\nserviceConnected=").append(SkipService.connected)
            .append(" masterEnabled=").append(enabled()).append(" diagnostics=").append(diagnostics()).append(" collecting=").append(collecting())
            .append("\nenvironment=").append(DeviceInfo.json(context))
            .append("\napkSha256=").append(DeviceInfo.apkSha256(context))
            .append("\nexportRuleSha256=").append(ruleHash)
            .append("\nwriterFlushed=").append(flushed).append(" droppedThisProcess=").append(droppedDiagnostics.get()).append(" writeErrorsThisProcess=").append(failedDiagnostics.get())
            .append(" rotations=").append(prefs.getLong("diagnosticRotations",0)).append(" evictedBytes=").append(prefs.getLong("diagnosticEvictedBytes",0))
            .append("\n说明：日志最多约8MB；轮转可能截断会话。未发现目标不代表无广告或跳过失败。手势完成不代表跳过成功。手动点击与自然结束不能仅凭这些事件确定。截图仅在采集开启时另存，文本导出不包含图片。\n");
        java.util.LinkedHashMap<String,DiagnosticSummary> summaries=new java.util.LinkedHashMap<>();
        String first="",last="";int malformed=0,legacy=0;
        for(String line:body.toString().split("\n")){
            if(line.isEmpty())continue;
            try{
                JSONObject row=new JSONObject(line);if(first.isEmpty())first=row.optString("time");last=row.optString("time");
                String id=row.optString("sessionId");if(id.isEmpty()){legacy++;continue;}
                DiagnosticSummary summary=summaries.get(id);
                if(summary==null){summary=new DiagnosticSummary(row.optString("foreground"));summaries.put(id,summary);}
                summary.acceptDetailed(row.optString("kind"),row.optLong("elapsedMs"),row.optBoolean("accepted")||row.optBoolean("matched"),row.optString("reason"),row.optString("state"),row.optString("decisionClass"),row.optLong("targetFrameCapturedAtMs",-1),row.optLong("targetResolvedAtMs",-1));
            }catch(Exception e){malformed++;}
        }
        report.append("coverage=").append(first).append(" ~ ").append(last).append(" malformedLines=").append(malformed).append(" legacyOrUnscopedEvents=").append(legacy).append("\n\n会话摘要（仅依据保留事件）\n");
        for(java.util.Map.Entry<String,DiagnosticSummary> entry:summaries.entrySet())report.append(entry.getKey()).append(" ").append(entry.getValue().describe()).append("\n");
        return report.append("\n完整事件 JSONL\n").append(body).toString();
    }
    public boolean enabled() { return prefs.getBoolean("enabled", true); }
    public boolean enabled(String pkg) { return enabled(); }
    public static String read(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int size;
            while ((size = input.read(buf)) != -1) {
                if (out.size() + size > 262144) throw new IllegalArgumentException("规则文件超过 256KB");
                out.write(buf, 0, size);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    public String rulesJson() throws Exception {
        String custom = prefs.getString("rules", null);
        return custom != null ? custom : read(context.getAssets().open("rules.json"));
    }
    public static List<AppRule> parse(String json) throws Exception {
        JSONObject doc = new JSONObject(json);
        if (doc.getInt("schemaVersion") != 1) throw new IllegalArgumentException("不支持的规则格式");
        JSONArray apps = doc.getJSONArray("apps");
        if (apps.length() < 1 || apps.length() > 100) throw new IllegalArgumentException("应用数量应为 1～100");
        List<AppRule> result = new ArrayList<>(); Set<String> unique = new HashSet<>();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject row = apps.getJSONObject(i); AppRule rule = new AppRule();
            rule.pkg = row.getString("packageName"); rule.name = row.getString("name");
            rule.sampledVersion = row.optString("sampledVersion", "");
            rule.rectangleCreatedApp=row.optBoolean("rectangleCreatedApp",false);
            if (!rule.pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
                || rule.pkg.equals("cn.local.adskip") || !unique.add(rule.pkg)) throw new IllegalArgumentException("包名无效或重复");
            if (rule.name.trim().isEmpty() || rule.name.length() > 40) throw new IllegalArgumentException("应用名称无效");
            JSONArray ids = row.optJSONArray("skipIds");
            if (ids != null) {
                if (ids.length() > 30) throw new IllegalArgumentException("单个应用最多 30 个按钮 ID");
                for (int n = 0; n < ids.length(); n++) {
                    String id = ids.getString(n);
                    if (!id.startsWith(rule.pkg + ":id/") || id.length() > 200) throw new IllegalArgumentException("按钮 ID 必须属于对应应用");
                    rule.skipIds.add(id);
                }
            }
            JSONArray rectangles=row.optJSONArray("rectangles");
            if(row.has("rectangles")&&rectangles==null)throw new IllegalArgumentException("矩形列表格式无效");
            if(rectangles!=null){
                if(rectangles.length()>10)throw new IllegalArgumentException("每个应用最多 10 条矩形规则");
                for(int n=0;n<rectangles.length();n++)rule.rectangles.add(parseRectangle(rectangles.getJSONObject(n)));
            }else if(row.has("rectangle"))rule.rectangles.add(parseRectangle(row.getJSONObject("rectangle")));
            rule.rectangle=rule.rectangles.isEmpty()?null:rule.rectangles.get(0);
            JSONObject visual = row.optJSONObject("visual");
            if(visual!=null)parseVisual(rule,visual);
            JSONArray variants=row.optJSONArray("visualVariants");
            if(variants!=null){
                if(variants.length()>4)throw new IllegalArgumentException("最多 4 个广告按钮样式");
                for(int n=0;n<variants.length();n++){
                    AppRule variant=new AppRule();variant.pkg=rule.pkg;variant.name=rule.name;
                    parseVisual(variant,variants.getJSONObject(n));rule.visualVariants.add(variant);
                }
            }
            result.add(rule);
        }
        return result;
    }
    private static void parseVisual(AppRule rule,JSONObject visual) throws Exception {
                rule.visualTemplate=visual.getString("template");
                rule.darkText=visual.optBoolean("darkText",false);
                if(!rule.visualTemplate.matches("bili|rail|netdisk|netdiskblue|xunlei|qqbrowser|music|uc|ucshort|uccircle|douyin|mijia|market"))throw new IllegalArgumentException("未知按钮图像模板");
                rule.referenceWidth=visual.getInt("referenceWidth");rule.referenceHeight=visual.getInt("referenceHeight");
                if(rule.referenceWidth!=1220||rule.referenceHeight!=2712)throw new IllegalArgumentException("模板参考尺寸不匹配");
                JSONArray b=visual.getJSONArray("textBounds"),t=visual.getJSONArray("tap");
                if(b.length()!=4||t.length()!=2)throw new IllegalArgumentException("图像坐标格式无效");
                rule.textBounds=new int[]{b.getInt(0),b.getInt(1),b.getInt(2),b.getInt(3)};rule.tap=new int[]{t.getInt(0),t.getInt(1)};
                if(rule.textBounds[0]<0||rule.textBounds[1]<0||rule.textBounds[2]>1220||rule.textBounds[3]>2712||rule.textBounds[2]<=rule.textBounds[0]||rule.textBounds[3]<=rule.textBounds[1]||rule.tap[0]<0||rule.tap[0]>=1220||rule.tap[1]<0||rule.tap[1]>=2712)throw new IllegalArgumentException("图像坐标越界");
    }
    public List<AppRule> rules() {
        try {
            List<AppRule> result=parse(rulesJson());
            // Saved rectangle rules retain their older copy of rules.json. Add this
            // built-in UC layout in memory without rewriting the user's rectangles.
            if(prefs.contains("rules")){
                AppRule bundledUc=null;
                for(AppRule rule:parse(read(context.getAssets().open("rules.json"))))
                    if("com.UCMobile".equals(rule.pkg)){bundledUc=rule;break;}
                if(bundledUc!=null)for(AppRule rule:result){
                    if(!"com.UCMobile".equals(rule.pkg)||!"uc".equals(rule.visualTemplate)
                        ||rule.visualVariants.size()>=4)continue;
                    boolean oldStyle=false,circle=false;
                    for(AppRule variant:rule.visualVariants){
                        oldStyle|="ucshort".equals(variant.visualTemplate);
                        circle|="uccircle".equals(variant.visualTemplate);
                    }
                    if(oldStyle&&!circle)for(AppRule variant:bundledUc.visualVariants)
                        if("uccircle".equals(variant.visualTemplate)){rule.visualVariants.add(variant);break;}
                }
            }
            return result;
        }
        catch (Exception e) { return new ArrayList<>(); }
    }
    private static RectRule parseRectangle(JSONObject r) throws Exception {
        return new RectRule(r.getInt("left"),r.getInt("top"),r.getInt("right"),r.getInt("bottom"),
            r.getInt("referenceWidth"),r.getInt("referenceHeight"),r.optInt("delayMs",300),r.optBoolean("enabled",true),r.optBoolean("relative",true));
    }
    private static JSONObject rectangleJson(RectRule r) throws Exception {
        return new JSONObject().put("left",r.left).put("top",r.top).put("right",r.right).put("bottom",r.bottom)
            .put("referenceWidth",r.width).put("referenceHeight",r.height).put("delayMs",r.delayMs).put("enabled",r.enabled).put("relative",r.relative);
    }
    public void saveRectangle(String pkg,String name,RectRule rule) throws Exception {
        List<RectRule> list=new ArrayList<>();if(rule!=null)list.add(rule);saveRectangles(pkg,name,list);
    }
    public void saveRectangleAt(String pkg,String name,int index,RectRule rule) throws Exception {
        List<RectRule> list=new ArrayList<>();for(AppRule app:parse(rulesJson()))if(pkg.equals(app.pkg))list.addAll(app.rectangles);
        if(index==-1){if(rule==null)return;list.add(rule);}
        else {if(index<0||index>=list.size())throw new IllegalArgumentException("规则已变化，请重新打开列表");if(rule==null)list.remove(index);else list.set(index,rule);}
        saveRectangles(pkg,name,list);
    }
    public void saveRectangles(String pkg,String name,List<RectRule> list) throws Exception {
        if(list.size()>10)throw new IllegalArgumentException("每个应用最多 10 条矩形规则");
        JSONObject doc=new JSONObject(rulesJson());JSONArray apps=doc.getJSONArray("apps");JSONObject target=null;int targetIndex=-1;
        for(int i=0;i<apps.length();i++)if(pkg.equals(apps.getJSONObject(i).getString("packageName"))){target=apps.getJSONObject(i);targetIndex=i;}
        if(target==null){if(list.isEmpty())return;target=new JSONObject().put("packageName",pkg).put("name",name).put("rectangleCreatedApp",true);apps.put(target);}
        target.remove("rectangle");target.remove("rectangles");
        if(list.isEmpty()){if(target.optBoolean("rectangleCreatedApp",false))apps.remove(targetIndex);}
        else if(list.size()==1)target.put("rectangle",rectangleJson(list.get(0)));
        else {JSONArray rows=new JSONArray();for(RectRule r:list)rows.put(rectangleJson(r));target.put("rectangles",rows);}
        importRules(doc.toString());
        log(name+"：已保存 "+list.size()+" 条矩形规则；仅用于 OCR，与其他方式并行识别，命中按钮即点击");
    }
    public void importRules(String json) throws Exception {
        parse(json);
        String old = rulesJson();
        prefs.edit().putString("previousRules", old).putString("rules", json).apply();
        log("规则已导入，可恢复上一版");
    }
    public boolean rollback() {
        String old = prefs.getString("previousRules", null);
        if (old == null) return false;
        String current = prefs.getString("rules", null);
        SharedPreferences.Editor edit = prefs.edit().putString("rules", old);
        if (current == null) edit.remove("previousRules"); else edit.putString("previousRules", current);
        edit.apply(); return true;
    }
    public synchronized void log(String message) {
        JSONObject fields = new JSONObject();
        try { fields.put("message", message); } catch (Exception ignored) {}
        diagnostic("ACTION", fields);
        String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
        String log = timestamp + "  " + message + "\n" + prefs.getString("log", "");
        if (log.length() > 16000) log = log.substring(0, 16000);
        prefs.edit().putString("log", log).apply();
        android.util.Log.i("AdSkip", message);
    }
    public void observedChange(String pkg, String name) {
        prefs.edit().putInt("observedChanges", prefs.getInt("observedChanges", 0) + 1).apply();
        log(name + "：点击后原位置连续两次 OCR 未见跳过按钮（只能确认按钮消失，不能证明点击生效）");
    }
}
