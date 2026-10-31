package cn.local.adskip;

import java.util.Arrays;

/** Match measured skip-button strokes, ignoring ad artwork and countdown digits. */
public final class VisualMatcher {
    public interface PixelReader { void read(int[] pixels, int left, int top, int width, int height); }
    /** Shared full-frame ROI search used by the service and real-screenshot tests. */
    public static double search(boolean[] reference,int w,int h,int x,int y,int frameWidth,int frameHeight,PixelReader reader) {
        return search(reference,w,h,x,y,frameWidth,frameHeight,false,reader);
    }
    public static double search(boolean[] reference,int w,int h,int x,int y,int frameWidth,int frameHeight,boolean darkText,PixelReader reader) {
        int[] pixels=new int[w*h];double best=0;
        for(int dx=-12;dx<=12;dx+=3)for(int dy=-12;dy<=12;dy+=3){
            int left=x+dx,top=y+dy;
            if(left<0||top<0||left+w>frameWidth||top+h>frameHeight)continue;
            reader.read(pixels,left,top,w,h);
            best=Math.max(best,score(reference,mask(pixels,darkText)));
        }
        return best;
    }
    /** Immutable evidence of already-recognized text, rechecked on a new frame. */
    public static final class Patch {
        public final int x,y,width,height;
        private final boolean[] light,dark;
        public Patch(int x,int y,int width,int height,PixelReader reader){
            this.x=x;this.y=y;this.width=width;this.height=height;
            int[] pixels=new int[width*height];reader.read(pixels,x,y,width,height);
            light=sparseMask(pixels,false);dark=sparseMask(pixels,true);
        }
        private static boolean[] sparseMask(int[] pixels,boolean dark){
            boolean[] ink=mask(pixels,dark);int count=0;for(boolean p:ink)if(p)count++;
            return count>=20&&count<=pixels.length*.65?ink:null;
        }
        public double similarity(int frameWidth,int frameHeight,PixelReader reader){
            if(x<0||y<0||width<=0||height<=0||x+width>frameWidth||y+height>frameHeight)return 0;
            int[] pixels=new int[width*height];reader.read(pixels,x,y,width,height);
            double result=0;
            if(light!=null)result=Math.max(result,score(light,mask(pixels,false)));
            if(dark!=null)result=Math.max(result,score(dark,mask(pixels,true)));
            return result;
        }
    }
    private VisualMatcher() {}
    public static boolean[] mask(int[] pixels) {
        return mask(pixels,false);
    }
    public static boolean[] mask(int[] pixels,boolean darkText) {
        int[] gray = new int[pixels.length];
        for (int i=0;i<pixels.length;i++) {int p=pixels[i];gray[i]=(77*((p>>16)&255)+150*((p>>8)&255)+29*(p&255))>>8;}
        int[] sorted=gray.clone();Arrays.sort(sorted);
        int threshold=darkText ? Math.min(100, sorted[sorted.length/2]-25) : Math.max(180, sorted[sorted.length/2]+25);
        boolean[] mask=new boolean[pixels.length];
        for(int i=0;i<gray.length;i++)mask[i]=darkText ? gray[i]<threshold : gray[i]>threshold;
        return mask;
    }
    public static double score(boolean[] reference, boolean[] current) {
        if(reference.length!=current.length)return 0;
        int intersection=0,union=0,foreground=0;
        for(int i=0;i<reference.length;i++) {if(reference[i])foreground++;if(reference[i]&&current[i])intersection++;if(reference[i]||current[i])union++;}
        if(foreground<20||union==0)return 0;
        return (double)intersection/union;
    }
}
