package cn.local.adskip;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

/** Bundled Chinese model: no model download or cloud request. One frame at a time. */
final class OcrEngine {
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean closed;
    private int activeTasks;
    private volatile boolean expandNext;
    interface Callback { void complete(Rect bounds, String text, Exception error); default boolean current(){return true;} }
    java.util.function.Consumer<String> trace;
    private final TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());

    void recognize(Bitmap frame, Callback callback) { recognize(frame, null, trace, callback); }
    void recognize(Bitmap frame, java.util.function.Consumer<String> observer, Callback callback) { recognize(frame, null, observer, callback); }

    void recognize(Bitmap frame, Rect hotspotHint, java.util.function.Consumer<String> observer, Callback callback) {
        java.util.List<Rect> hints=new java.util.ArrayList<>();
        if(hotspotHint!=null)hints.add(hotspotHint);
        recognizeHotspots(frame,hints,observer,callback);
    }
    void recognizeHotspots(Bitmap frame,java.util.List<Rect> hints,java.util.function.Consumer<String> observer,Callback callback){
        Search search=new Search();
        // Advance on submission, including frames which expire before all regions.
        // Late callbacks never mutate the scan mode for a newer request/session.
        synchronized(this){search.expanded=expandNext;expandNext=!expandNext;}
        probeHotspot(frame,hints,0,observer,callback,search);
    }
    void recognizeHotspots(Bitmap frame,java.util.List<Rect> hints,java.util.function.Consumer<String> observer,java.util.function.BooleanSupplier valid,Callback callback){
        recognizeHotspots(frame,hints,observer,new Callback(){
            @Override public boolean current(){return valid.getAsBoolean();}
            @Override public void complete(Rect bounds,String text,Exception error){callback.complete(bounds,text,error);}
        });
    }
    /** Submit every region immediately; hits never wait for the rest of the batch.
     * The caller retains the frame until finished runs, including losing tasks. */
    void recognizeParallel(Bitmap frame,java.util.List<Rect> manual,java.util.List<Rect> hints,boolean corners,
                           java.util.function.Consumer<String> observer,java.util.function.BooleanSupplier valid,
                           Callback hits,Runnable finished){
        java.util.List<Rect> crops=new java.util.ArrayList<>(),authorized=new java.util.ArrayList<>();
        java.util.List<String> names=new java.util.ArrayList<>();
        int w=frame.getWidth(),h=frame.getHeight();
        for(int i=0;i<manual.size();i++){
            Rect roi=new Rect(manual.get(i));if(!roi.intersect(0,0,w,h)||roi.isEmpty())continue;
            Rect crop=new Rect(roi);crop.inset(-8,-8);crop.intersect(0,0,w,h);
            crops.add(crop);authorized.add(roi);names.add("rectangle_"+i);
        }
        for(int i=0;i<hints.size();i++){
            Rect crop=new Rect(hints.get(i));crop.inset(-Math.max(80,w/15),-20);
            if(!crop.intersect(0,0,w,h)||crop.isEmpty())continue;
            crops.add(crop);authorized.add(null);names.add("hotspot_"+i);
        }
        if(corners)for(int i=0;i<4;i++){
            boolean right=i<2,bottom=i%2==1;
            Rect crop=new Rect(right?(int)(w*.48):0,bottom?(int)(h*.75):0,
                right?w:(int)(w*.52),bottom?h:(int)(h*.25));
            crops.add(crop);authorized.add(null);names.add(ZONE_NAMES[i+4]);
        }
        if(crops.isEmpty()){finished.run();return;}
        java.util.concurrent.atomic.AtomicInteger remaining=new java.util.concurrent.atomic.AtomicInteger(crops.size());
        Search common=new Search();
        for(int i=0;i<crops.size();i++){
            final Rect crop=crops.get(i),roi=authorized.get(i);final String name=names.get(i);
            Runnable complete=()->{if(remaining.decrementAndGet()==0)finished.run();};
            if(!valid.getAsBoolean()){complete.run();continue;}
            Search search=roi==null?common:new Search();search.manualRegion=roi!=null;
            if(observer!=null)observer.accept("zone="+name+" submitted=true");
            processCrop(frame,crop.left,crop.top,crop.width(),crop.height(),name,observer,search,(bounds,text)->{
                try{
                    if(valid.getAsBoolean()&&bounds!=null&&(roi==null||roi.contains(bounds.centerX(),bounds.centerY()))){
                        Rect target=new Rect(bounds);if(roi!=null)target.intersect(roi);
                        hits.complete(target,text,null);
                    }
                }finally{complete.run();}
            },error->{try{if(observer!=null)observer.accept("zone="+name+" error="+error);}finally{complete.run();}});
        }
    }
    /** Explicit user rectangles only: never expand into unrelated screen regions. */
    void recognizeRectangles(Bitmap frame,java.util.List<Rect> regions,java.util.function.Consumer<String> observer,
                             java.util.function.BooleanSupplier valid,Callback callback){
        rectangleRegion(frame,regions,0,observer,new Callback(){
            @Override public boolean current(){return valid.getAsBoolean();}
            @Override public void complete(Rect b,String text,Exception error){callback.complete(b,text,error);}
        });
    }
    private void rectangleRegion(Bitmap frame,java.util.List<Rect> regions,int index,java.util.function.Consumer<String> observer,Callback callback){
        if(!callback.current()){callback.complete(null,"",new IllegalStateException("OCR request expired"));return;}
        if(isClosed()){callback.complete(null,"",new IllegalStateException("OCR closed"));return;}
        if(index>=regions.size()){callback.complete(null,"",null);return;}
        Rect roi=new Rect(regions.get(index));
        if(!roi.intersect(0,0,frame.getWidth(),frame.getHeight())||roi.isEmpty()){
            rectangleRegion(frame,regions,index+1,observer,callback);return;
        }
        Search search=new Search();search.manualRegion=true;
        // Give glyphs at the user's boundary a small OCR margin, while the click
        // center must remain inside the original authorized rectangle.
        Rect crop=new Rect(roi);crop.inset(-8,-8);crop.intersect(0,0,frame.getWidth(),frame.getHeight());
        processCrop(frame,crop.left,crop.top,crop.width(),crop.height(),"rectangle_"+index,observer,search,(bounds,text)->{
            if(!callback.current()){callback.complete(null,"",new IllegalStateException("OCR request expired"));return;}
            if(bounds!=null&&roi.contains(bounds.centerX(),bounds.centerY())){
                Rect target=new Rect(bounds);target.intersect(roi);callback.complete(target,text,null);
            }
            else rectangleRegion(frame,regions,index+1,observer,callback);
        },error->callback.complete(null,"",error));
    }
    /** Per-frame evidence only: a close on an ordinary page never becomes a target. */
    private static final class Search {
        boolean adContext;
        boolean manualRegion;
        boolean expanded;
        Rect closeBounds;
        String closeText="";
    }
    private void probeHotspot(Bitmap frame,java.util.List<Rect> hints,int index,java.util.function.Consumer<String> observer,Callback callback,Search search){
        if(!callback.current()){callback.complete(null,"",new IllegalStateException("OCR request expired"));return;}
        if (isClosed()) { callback.complete(null,"",new IllegalStateException("OCR closed")); return; }
        if(index>=hints.size()){region(frame,0,observer,callback,search);return;}
        Rect hotspotHint=hints.get(index);
        if (hotspotHint != null && hotspotHint.width() > 0 && hotspotHint.height() > 0) {
            int padX = Math.max(80, frame.getWidth()/15), padY = 20;
            int cropLeft = Math.max(0, hotspotHint.left - padX);
            int cropTop = Math.max(0, hotspotHint.top - padY);
            int cropRight = Math.min(frame.getWidth(), hotspotHint.right + padX);
            int cropBottom = Math.min(frame.getHeight(), hotspotHint.bottom + padY);
            int cropWidth = cropRight - cropLeft;
            int cropHeight = cropBottom - cropTop;
            if (cropWidth >= 20 && cropHeight >= 10) {
                processCrop(frame, cropLeft, cropTop, cropWidth, cropHeight, "hotspot_roi", observer, search,
                    (bounds, matched) -> {
                        if (bounds != null) {
                            callback.complete(bounds, matched, null);
                        } else {
                            if (observer != null) observer.accept("zone=hotspot_roi miss=true fallback=zones");
                            probeHotspot(frame,hints,index+1,observer,callback,search);
                        }
                    },
                    error -> {
                        if (observer != null) observer.accept("zone=hotspot_roi error=" + error.getMessage() + " fallback=zones");
                        probeHotspot(frame,hints,index+1,observer,callback,search);
                    });
                return;
            }
        }
        probeHotspot(frame,hints,index+1,observer,callback,search);
    }

    private static final String[] ZONE_NAMES = {"top_right_small", "bottom_right_small", "top_left_small", "bottom_left_small",
        "top_right_fallback", "bottom_right_fallback", "top_left_fallback", "bottom_left_fallback"};

    private void region(Bitmap frame, int index, java.util.function.Consumer<String> observer, Callback callback,Search search) {
        if(!callback.current()){callback.complete(null,"",new IllegalStateException("OCR request expired"));return;}
        if(isClosed()){callback.complete(null,"",new IllegalStateException("OCR closed"));return;}
        int w=frame.getWidth(),h=frame.getHeight(),corner=index%4;
        boolean right=corner<2,bottom=(corner%2)==1,expanded=search.expanded;
        int left=right?(int)(w*.48):0;
        int width=right?w-left:(int)(w*.52);
        double fraction=expanded?.25:(bottom?.16:.15);
        int top=bottom?(int)(h*(1-fraction)):0;
        int height=bottom?h-top:(int)(h*fraction);
        processCrop(frame,left,top,width,height,ZONE_NAMES[index+(search.expanded?4:0)],observer,search,
            (bounds,matched)->{
                if(bounds!=null||index==3){callback.complete(bounds,matched,null);}
                else region(frame,index+1,observer,callback,search);
            },error->{
                if(index==3){callback.complete(null,"",error);}
                else region(frame,index+1,observer,callback,search);
            });
    }

    private static Rect mapToFrame(Rect b, int scale, int cropLeft, int cropTop) {
        if (b == null) return null;
        return new Rect(
            (b.left / scale) + cropLeft,
            (b.top / scale) + cropTop,
            ((b.right + scale - 1) / scale) + cropLeft,
            ((b.bottom + scale - 1) / scale) + cropTop
        );
    }

    private void processCrop(Bitmap frame, int cropLeft, int cropTop, int cropWidth, int cropHeight, String zoneName,
                            java.util.function.Consumer<String> observer, Search search,
                            java.util.function.BiConsumer<Rect, String> onSuccess,
                            java.util.function.Consumer<Exception> onFailure) {
        synchronized(this) {
        if(closed){onFailure.accept(new IllegalStateException("OCR closed"));return;}
        activeTasks++;
        }
        final Bitmap crop;
        final int scale;
        try {
            Bitmap raw = Bitmap.createBitmap(frame, cropLeft, cropTop, cropWidth, cropHeight);
            if (cropWidth < 320 || cropHeight < 160) {
                scale = 2;
                crop = Bitmap.createScaledBitmap(raw, cropWidth * 2, cropHeight * 2, true);
                if (crop != raw) raw.recycle();
            } else {
                scale = 1;
                crop = raw;
            }
        } catch(RuntimeException error){finishTask();onFailure.accept(error);return;}
        final long began = android.os.SystemClock.elapsedRealtime();
        final java.util.concurrent.atomic.AtomicLong resultReady=new java.util.concurrent.atomic.AtomicLong();
        try { recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnCompleteListener(Runnable::run,task->resultReady.set(android.os.SystemClock.elapsedRealtime()))
            .addOnCompleteListener(worker, task -> {
                try {
                if(!task.isSuccessful()){onFailure.accept(task.getException()!=null?task.getException():new IllegalStateException("OCR cancelled"));return;}
                Text result=task.getResult();
                for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){
                    Rect marker=mapToFrame(line.getBoundingBox(), scale, cropLeft, cropTop);
                    if(Matcher.adSignal(line.getText())||(marker!=null&&Matcher.adBadge(line.getText(),
                        marker.left,marker.top,marker.right,marker.bottom,frame.getWidth(),frame.getHeight())))search.adContext=true;
                }
                long duration = android.os.SystemClock.elapsedRealtime() - began;
                if (observer != null) {
                    StringBuilder detail = new StringBuilder("zone=" + zoneName + " left=" + cropLeft + " top=" + cropTop + " width=" + cropWidth + " height=" + cropHeight + " scale=" + scale + " durationMs=" + duration + " blocks=" + result.getTextBlocks().size());
                    long ready=resultReady.get();
                    detail.append(" taskMs=").append(ready==0?-1:ready-began)
                        .append(" callbackQueueMs=").append(ready==0?-1:android.os.SystemClock.elapsedRealtime()-ready);
                    for (Text.TextBlock block : result.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            String value = line.getText();
                            boolean hasSkipChar = value.contains("跳") || value.contains("过") || value.toLowerCase(java.util.Locale.ROOT).contains("skip");
                            boolean hasDigit = value.matches(".*[0-9].*");
                            if (hasSkipChar || hasDigit || Matcher.isClose(value) || Matcher.adSignal(value)) {
                                detail.append("\ntext=").append(value.substring(0, Math.min(120, value.length())))
                                      .append(" bounds=").append(line.getBoundingBox())
                                      .append(" isSkip=").append(Matcher.isSkip(value))
                                      .append(hasDigit ? " suspectedCountdown=true" : "");
                            }
                        }
                    }
                    observer.accept(detail.toString());
                }
                Rect bounds = null; String matched = "";
                for (Text.TextBlock block : result.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        // Reject whole sentences such as 跳过登录; never mine isolated words from them.
                        boolean close=Matcher.isClose(line.getText());
                        String skipText=Matcher.ocrSkipText(line.getText());
                        if (skipText.isEmpty()&&!close) continue;
                        if(close){
                            Rect box=mapToFrame(line.getBoundingBox(), scale, cropLeft, cropTop);
                            if(box!=null){
                                if((search.manualRegion&&box.width()<=frame.getWidth()*.42&&box.height()<=frame.getHeight()*.12)||Matcher.accept(line.getText(),false,true,box.left,box.top,box.right,box.bottom,frame.getWidth(),frame.getHeight(),0)){
                                    search.closeBounds=box;search.closeText=line.getText();
                                }
                            }
                            continue;
                        }
                        Rect candidate = null; boolean first = false, second = false;
                        // Symbol bounds isolate 跳过 even if OCR joins the nearby VIP link into one line.
                        for (Text.Element element : line.getElements()) {
                            for (Text.Symbol symbol : element.getSymbols()) {
                                String sym = symbol.getText();
                                if (sym != null && !sym.isEmpty() && symbol.getBoundingBox() != null) {
                                    char c = sym.charAt(0);
                                    boolean isFirst = (c == '跳' || c == '桃' || c == '姚' || c == '佻' || c == '兆');
                                    boolean isSecond = (c == '过' || c == '寸' || c == '辽' || c == '辻' || c == '迈');
                                    if (isFirst || isSecond) {
                                        if (candidate == null) candidate = new Rect(symbol.getBoundingBox()); else candidate.union(symbol.getBoundingBox());
                                        first |= isFirst; second |= isSecond;
                                    }
                                }
                            }
                        }
                        if (!first || !second) {
                            candidate = null;
                            for (Text.Element element : line.getElements()) {
                                if (Matcher.isSkip(element.getText()) && !element.getText().contains("VIP") && !element.getText().contains("会员") && element.getBoundingBox() != null) {
                                    candidate = element.getBoundingBox(); break;
                                }
                            }
                            if (candidate == null && !line.getText().contains("VIP") && !line.getText().contains("会员")) candidate = line.getBoundingBox();
                        }
                        if (candidate == null) continue;
                        Rect absolute = mapToFrame(candidate, scale, cropLeft, cropTop);
                        if (!Matcher.accept(skipText, false, false, absolute.left, absolute.top,
                            absolute.right, absolute.bottom, frame.getWidth(), frame.getHeight(), 0)) continue;
                        bounds = absolute; matched = line.getText(); break;
                    }
                    if (bounds != null) break;
                    // Vertical text check: adjacent lines forming a skip action
                    if (bounds == null && block.getLines().size() >= 2) {
                        java.util.List<Text.Line> lines = block.getLines();
                        for (int i = 0; i < lines.size() - 1; i++) {
                            Text.Line l1 = lines.get(i), l2 = lines.get(i + 1);
                            String combo = l1.getText() + l2.getText();
                            String comboSkip = Matcher.ocrSkipText(combo);
                            Rect comboCandidate = null;
                            if (!comboSkip.isEmpty() && l1.getBoundingBox() != null && l2.getBoundingBox() != null) {
                                comboCandidate = new Rect(l1.getBoundingBox());
                                comboCandidate.union(l2.getBoundingBox());
                            } else if (i + 2 < lines.size()) {
                                Text.Line l3 = lines.get(i + 2);
                                String combo3 = combo + l3.getText();
                                String combo3Skip = Matcher.ocrSkipText(combo3);
                                if (!combo3Skip.isEmpty() && l1.getBoundingBox() != null && l2.getBoundingBox() != null && l3.getBoundingBox() != null) {
                                    comboCandidate = new Rect(l1.getBoundingBox());
                                    comboCandidate.union(l2.getBoundingBox());
                                    comboCandidate.union(l3.getBoundingBox());
                                    comboSkip = combo3Skip;
                                }
                            }
                            if (comboCandidate != null) {
                                Rect absolute = mapToFrame(comboCandidate, scale, cropLeft, cropTop);
                                if (Matcher.accept(comboSkip, false, false, absolute.left, absolute.top,
                                    absolute.right, absolute.bottom, frame.getWidth(), frame.getHeight(), 0)) {
                                    bounds = absolute; matched = comboSkip; break;
                                }
                            }
                        }
                        if (bounds != null) break;
                    }
                }
                if(bounds==null&&(search.adContext||search.manualRegion)&&search.closeBounds!=null){bounds=search.closeBounds;matched=search.closeText;}
                onSuccess.accept(bounds, matched);
                } finally {if(crop!=null&&crop!=frame)crop.recycle();finishTask();}
            });
        } catch(RuntimeException error){if(crop!=null&&crop!=frame)crop.recycle();finishTask();onFailure.accept(error);}
    }

    void recognizeTarget(Bitmap frame, Rect hint, Callback callback) {recognizeTarget(frame,hint,null,callback);}
    void recognizeTarget(Bitmap frame, Rect hint, java.util.function.Consumer<String> observer,Callback callback) {
        int padX=Math.max(80,frame.getWidth()/15);
        int l=Math.max(0,hint.left-padX),t=Math.max(0,hint.top-20);
        int r=Math.min(frame.getWidth(),hint.right+padX),b=Math.min(frame.getHeight(),hint.bottom+20);
        if(r<=l||b<=t){callback.complete(null,"",new IllegalArgumentException("Invalid target"));return;}
        Search search=new Search();search.adContext=true; // Revalidate an already authorized target only.
        processCrop(frame,l,t,r-l,b-t,"target_refresh",observer,search,
            (bounds,text)->callback.complete(bounds,text,null),error->callback.complete(null,"",error));
    }
    void warmUp(java.util.function.Consumer<Exception> callback) {
        Bitmap blank=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        blank.eraseColor(android.graphics.Color.WHITE);
        recognizeTarget(blank,new Rect(0,0,64,64),(bounds,text,error)->{blank.recycle();callback.accept(error);});
    }
    private synchronized boolean isClosed(){return closed;}
    private synchronized void finishTask(){activeTasks--;if(closed&&activeTasks==0){recognizer.close();worker.shutdown();}}
    synchronized void close(){if(closed)return;closed=true;if(activeTasks==0){recognizer.close();worker.shutdown();}}
}
