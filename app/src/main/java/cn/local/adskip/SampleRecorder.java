package cn.local.adskip;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Optional bounded local launch captures, written independently of recognition. */
final class SampleRecorder {
    private final File directory;
    private final Store store;
    private final CapturePolicy policy=new CapturePolicy();
    boolean wants(long session,long elapsed){return store.collecting()&&policy.wants(session,elapsed);}
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(3));
    SampleRecorder(Context context, Store store) {
        this.store=store;
        File external=context.getExternalFilesDir(null);
        directory=new File(external==null?context.getFilesDir():external,"captures");directory.mkdirs();
        File previous=new File(context.getFilesDir(),"captures");
        if(!directory.equals(previous))writer.execute(()->{
            File[] old=previous.listFiles((d,n)->n.endsWith(".jpg")||n.endsWith(".json"));
            if(old!=null)for(File file:old)try{java.nio.file.Files.move(file.toPath(),new File(directory,file.getName()).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(Exception ignored){}
            File[] existing=directory.listFiles();
            if(existing!=null)for(File file:existing)file.setReadable(true,false);
            prune();
        });
    }
    private long lastSkipLog;
    private String lastSkipReason="";
    private void logSkip(String reason,String pkg,long elapsed,String sessionId,String workId){
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastSkipLog<1000&&reason.equals(lastSkipReason))return;
        lastSkipLog=now;lastSkipReason=reason;
        JSONObject event=new JSONObject();
        try{event.put("sessionId",sessionId).put("workId",workId).put("package",pkg).put("elapsedMs",elapsed).put("reason",reason);}
        catch(Exception ignored){}
        store.diagnostic("SAMPLE_SKIPPED",event);
    }
    void save(Bitmap frame,String pkg,long session,long elapsed,int windowId,String stage,String sessionId,String workId) {
        if(!store.collecting()){logSkip("collecting_disabled",pkg,elapsed,sessionId,workId);return;}
        if(writer.isShutdown()){logSkip("writer_shutdown",pkg,elapsed,sessionId,workId);return;}
        if(!policy.claim(session,elapsed)){logSkip("policy_quota_or_interval",pkg,elapsed,sessionId,workId);return;}
        // copy on the image worker; the live bitmap is recycled by the recognizer.
        int width=Math.min(720,frame.getWidth()), height=Math.max(1,frame.getHeight()*width/frame.getWidth());
        Bitmap scaled=Bitmap.createScaledBitmap(frame,width,height,true);
        Bitmap copy=scaled==frame?frame.copy(Bitmap.Config.ARGB_8888,false):scaled;
        JSONObject info=new JSONObject();
        try{info.put("sessionId",sessionId).put("workId",workId).put("foreground",pkg).put("package",pkg).put("session",session).put("elapsedMs",elapsed).put("windowId",windowId)
            .put("stage",stage).put("width",frame.getWidth()).put("height",frame.getHeight()).put("savedWidth",copy.getWidth()).put("savedHeight",copy.getHeight()).put("wallTimeMs",System.currentTimeMillis());}
        catch(Exception ignored){}
        try{writer.execute(()->{
            String id=session+"-"+elapsed+"-"+pkg;
            File temp=new File(directory,id+".tmp"),image=new File(directory,id+".jpg"),metadata=new File(directory,id+".json");
            try{
                if(!store.collecting())return;
                prune();
                try(FileOutputStream out=new FileOutputStream(temp)){copy.compress(Bitmap.CompressFormat.JPEG,75,out);}
                if(!temp.renameTo(image))throw new java.io.IOException("capture rename failed");
                try(FileOutputStream out=new FileOutputStream(metadata)){out.write(info.toString().getBytes(StandardCharsets.UTF_8));}
                image.setReadable(true,false);metadata.setReadable(true,false);
                JSONObject event=new JSONObject(info.toString());event.put("file",image.getName());store.diagnostic("SAMPLE_SAVED",event);
            }catch(Exception error){android.util.Log.w("AdSkip","sample write failed",error);}
            finally{copy.recycle();temp.delete();}
        });}catch(java.util.concurrent.RejectedExecutionException ignored){copy.recycle();logSkip("queue_full",pkg,elapsed,sessionId,workId);}
    }
    private void prune() {
        File[] files=directory.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return;
        Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));
        long bytes=0;for(File file:files)bytes+=file.length();int count=files.length;
        for(File file:files){if(count<400&&bytes<128L*1024*1024)break;bytes-=file.length();count--;file.delete();new File(directory,file.getName().replace(".jpg",".json")).delete();}
    }
    void close(){writer.shutdown();}
}
