package cn.local.adskip;

/** Limits optional diagnostic images independently of the recognition lifetime. */
public final class CapturePolicy {
    public static final long DURATION_MS=5*60*1000;
    private long session=Long.MIN_VALUE, last=Long.MIN_VALUE;
    private int count;
    public synchronized boolean wants(long session, long elapsed) {
        return this.session!=session || (count<4 && elapsed-last>=1200);
    }
    public synchronized boolean claim(long session, long elapsed) {
        if(!wants(session,elapsed))return false;
        if(this.session!=session){this.session=session;count=0;}
        count++;last=elapsed;return true;
    }
}
