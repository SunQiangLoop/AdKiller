package cn.local.adskip;

/** A user-defined button rectangle; no image or text recognition is involved. */
public final class RectRule {
    public final int left,top,right,bottom,width,height,delayMs;
    public final boolean enabled,relative;
    public RectRule(int l,int t,int r,int b,int w,int h,int delay,boolean enabled,boolean relative){
        if(w<1||h<1||l<0||t<0||r<=l||b<=t||r>w||b>h||delay<0||delay>5000)
            throw new IllegalArgumentException("矩形需位于参考屏幕内，延迟须为 0～5000 毫秒");
        left=l;top=t;right=r;bottom=b;width=w;height=h;delayMs=delay;this.enabled=enabled;this.relative=relative;
    }
    public int[] map(int w,int h){
        if(w<=0||h<=0||(width<height)!=(w<h)||(!relative&&(w!=width||h!=height)))return null;
        int l=(int)((long)left*w/width),t=(int)((long)top*h/height);
        int r=Math.min(w,(int)Math.ceil((double)right*w/width)),b=Math.min(h,(int)Math.ceil((double)bottom*h/height));
        return r>l&&b>t?new int[]{l,t,r,b}:null;
    }
    public static int[] box(int x1,int y1,int x2,int y2,int w,int h){
        return new int[]{Math.max(0,Math.min(w,Math.min(x1,x2))),Math.max(0,Math.min(h,Math.min(y1,y2))),
            Math.max(0,Math.min(w,Math.max(x1,x2))),Math.max(0,Math.min(h,Math.max(y1,y2)))};
    }
}
