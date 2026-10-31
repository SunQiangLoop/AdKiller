package cn.local.adskip;

/** A single click owner across competing recognizers and foreground sessions. */
public final class ClickGate {
    private long session=-1;
    public static final long HOLD_MS = 900;
    private boolean claimed;
    private String owner = "";
    private long claimedAt;
    public synchronized void reset(long session) { this.session=session;claimed=false; }
    public synchronized boolean available(long session) { return this.session==session&&!claimed; }
    public synchronized boolean claim(long session) {
        if(!available(session))return false;
        claimed=true;owner="";claimedAt=0;return true;
    }
    public synchronized boolean claim(long session,String owner,long now) {
        if(!claim(session))return false;
        this.owner=owner;claimedAt=now;return true;
    }
    public synchronized boolean owns(long session,String owner) {
        return this.session==session&&claimed&&this.owner.equals(owner);
    }
    public synchronized boolean overdue(long session,String owner,long now) {
        return owns(session,owner)&&now-claimedAt>=HOLD_MS;
    }
    public synchronized boolean release(long session,String owner) {
        if(!owns(session,owner))return false;
        claimed=false;return true;
    }
    public synchronized void release(long session) { if(this.session==session)claimed=false; }
}
