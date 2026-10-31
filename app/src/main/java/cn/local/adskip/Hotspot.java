package cn.local.adskip;

/**
 * Historical skip button hotspot coordinates and hit statistics for predictive scanning.
 * Free of Android UI dependencies so it is testable on host JVM.
 */
public final class Hotspot {
    public int left;
    public int top;
    public int right;
    public int bottom;
    public final int refWidth;
    public final int refHeight;
    public int hitCount;
    public long lastSeen;
    public String source;

    public Hotspot(int left, int top, int right, int bottom, int refWidth, int refHeight, int hitCount, long lastSeen, String source) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.refWidth = refWidth;
        this.refHeight = refHeight;
        this.hitCount = hitCount;
        this.lastSeen = lastSeen;
        this.source = source == null ? "" : source;
    }

    public int centerX() { return (left + right) / 2; }
    public int centerY() { return (top + bottom) / 2; }
    public int width() { return Math.max(0, right - left); }
    public int height() { return Math.max(0, bottom - top); }
    /** A single tiny glyph is ambiguous; keep it visible without spending a predictive OCR task on it. */
    public boolean predictive() {
        return refWidth>0&&refHeight>0&&(hitCount>1||(long)width()*1220>=24L*refWidth||(long)height()*2712>=24L*refHeight);
    }

    public Hotspot scaleTo(int targetW, int targetH) {
        if (refWidth <= 0 || refHeight <= 0 || (targetW == refWidth && targetH == refHeight) || targetW <= 0 || targetH <= 0) {
            return this;
        }
        double sx = (double) targetW / refWidth;
        double sy = (double) targetH / refHeight;
        int l = (int) Math.round(left * sx);
        int t = (int) Math.round(top * sy);
        int r = (int) Math.round(right * sx);
        int b = (int) Math.round(bottom * sy);
        return new Hotspot(l, t, r, b, targetW, targetH, hitCount, lastSeen, source);
    }

    public boolean isCloseTo(int otherLeft, int otherTop, int otherRight, int otherBottom, int otherW, int otherH, int tolerancePx) {
        Hotspot scaledOther = new Hotspot(otherLeft, otherTop, otherRight, otherBottom, otherW, otherH, 0, 0, "").scaleTo(refWidth, refHeight);
        int dx = centerX() - scaledOther.centerX();
        int dy = centerY() - scaledOther.centerY();
        return (dx * dx + dy * dy) <= (tolerancePx * tolerancePx);
    }

    public void mergeWith(int otherLeft, int otherTop, int otherRight, int otherBottom, int otherW, int otherH, String newSource, long now) {
        Hotspot scaledOther = new Hotspot(otherLeft, otherTop, otherRight, otherBottom, otherW, otherH, 0, 0, "").scaleTo(refWidth, refHeight);
        int total = hitCount + 1;
        this.left = (this.left * hitCount + scaledOther.left) / total;
        this.top = (this.top * hitCount + scaledOther.top) / total;
        this.right = (this.right * hitCount + scaledOther.right) / total;
        this.bottom = (this.bottom * hitCount + scaledOther.bottom) / total;
        this.hitCount = total;
        this.lastSeen = now;
        if (newSource != null && !newSource.isEmpty()) this.source = newSource;
    }

    public int[] toCropBox(int frameWidth, int frameHeight, int padX, int padY) {
        Hotspot scaled = scaleTo(frameWidth, frameHeight);
        int cropLeft = Math.max(0, scaled.left - padX);
        int cropTop = Math.max(0, scaled.top - padY);
        int cropRight = Math.min(frameWidth, scaled.right + padX);
        int cropBottom = Math.min(frameHeight, scaled.bottom + padY);
        int cropW = Math.max(0, cropRight - cropLeft);
        int cropH = Math.max(0, cropBottom - cropTop);
        return new int[]{cropLeft, cropTop, cropW, cropH};
    }

    public boolean contains(int x, int y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public boolean intersects(int l, int t, int r, int b) {
        return left < r && right > l && top < b && bottom > t;
    }

    @Override
    public String toString() {
        return "Hotspot[(" + left + "," + top + "," + right + "," + bottom + ")@" + refWidth + "x" + refHeight + " hits=" + hitCount + " src=" + source + "]";
    }
}
