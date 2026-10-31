package cn.local.adskip;

import java.util.regex.Pattern;

/** Android-independent matching policy, exercised by host tests. */
public final class Matcher {
    private static final Pattern COUNTDOWN = Pattern.compile("(?<!\\d)(\\d{1,2})(?!\\d)");
    private static final Pattern SKIP = Pattern.compile(
        "(?i)^(?:(?:点击|点此)?\\s*跳过(?:广告)?|skip(?:\\s+ad)?)" +
        "(?:\\s*[（(]?\\s*(?:\\d{1,2}:\\d{2}|\\d{1,2})\\s*(?:s|秒)?\\s*(?:后)?\\s*(?:自动)?\\s*[）)]?)?" +
        "(?:\\s*[>»›])?$|" +
        "^(?:\\d{1,2}:\\d{2}|\\d{1,2})\\s*(?:s|秒)?\\s*(?:后)?\\s*(?:自动)?\\s*(?:[|｜]\\s*)?" +
        "(?:(?:点击|点此)?\\s*跳过(?:广告)?|skip(?:\\s+ad)?)(?:\\s*[>»›])?$"
    );
    private Matcher() {}

    public static boolean isFuzzySkip(String text) {
        if (text == null || text.length() > 60) return false;
        String s = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC);
        s = s.replaceFirst("(?i)^(?:开\\s*VIP\\s*免广告|会员去广告)\\s*[|｜Il丨1]?\\s*", "");
        s = s.replaceFirst("^[|｜丨]+\\s*", "");
        s = s.replaceFirst("^(?:(?:\\d{1,2}:\\d{2}|\\d{1,2})\\s*(?:s|秒)?\\s*(?:后)?\\s*(?:自动)?\\s*(?:[|｜]\\s*)?)?(?:点击|点此)?\\s*", "");
        s = s.replaceFirst("\\s*(?:广告)?\\s*(?:[（(]?\\s*(?:\\d{1,2}:\\d{2}|\\d{1,2})\\s*(?:s|秒)?\\s*(?:后)?\\s*(?:自动)?\\s*[）)]?)?\\s*(?:[>»›])?$", "");
        s = s.replaceAll("[\\s\\p{Z}]+", "").trim();
        if (s.length() == 2) {
            char c1 = s.charAt(0), c2 = s.charAt(1);
            boolean first = (c1 == '跳' || c1 == '桃' || c1 == '姚' || c1 == '佻' || c1 == '兆');
            boolean second = (c2 == '过' || c2 == '寸' || c2 == '辽' || c2 == '辻' || c2 == '迈');
            if (first && second) return true;
        }
        return false;
    }

    public static boolean isSkip(String text) {
        if (text == null || text.length() > 60) return false;
        String value = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
            .replaceAll("[\\s\\p{Z}]+", " ").trim();
        // Music sometimes exposes the VIP link and skip action as one description.
        value = value.replaceFirst("(?i)^(?:开\\s*VIP\\s*免广告|会员去广告)\\s*[|｜Il丨1]?\\s*", "");
        value = value.replaceFirst("^[|｜丨]+\\s*", "");
        return SKIP.matcher(value).matches() || isFuzzySkip(value);
    }

    /** OCR can join Tencent's interactive-ad label, advertiser and skip into one line. */
    public static String ocrSkipText(String text){
        if(isSkip(text))return text;
        if(text==null||text.length()>60)return "";
        String value=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+"," ").trim();
        int start=value.indexOf("跳过");
        if(start<0)start=value.indexOf("跳");
        if(start<=0)return "";
        String label=value.substring(0,start).trim(),action=value.substring(start);
        if(label.matches("互动广告\\s*[\\p{IsHan}A-Za-z0-9·]{0,12}")&&!label.contains("不")&&isSkip(action))return action;
        return "";
    }

    public static int skipCountdown(String text){
        String action=ocrSkipText(text);
        if(action.isEmpty())return -1;
        java.util.regex.Matcher digits=COUNTDOWN.matcher(java.text.Normalizer.normalize(action,java.text.Normalizer.Form.NFKC));
        int value=-1;
        while(digits.find())value=Integer.parseInt(digits.group(1));
        return value;
    }

    /** Close words are ambiguous; callers must also establish an ad and corner. */
    public static boolean isClose(String text) {
        if(text==null)return false;
        String value=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).trim();
        return value.equals("关闭")||value.equals("关闭广告")||value.equalsIgnoreCase("X")
            ||value.equals("×")||value.equals("✕")||value.equals("✖")||value.equals("╳")||value.equals("❌");
    }

    /** OCR may merge the small ad badge and advertiser, e.g. 广告淘宝. */
    public static boolean adBadge(String text,int left,int top,int right,int bottom,int width,int height){
        if(text==null||width<=0||height<=0)return false;
        String value=text.replaceAll("[\\s\\p{Z}]+","");
        return value.matches("广告[\\p{IsHan}A-Za-z0-9·]{1,10}")&&left>=0&&left<width*.3
            &&top>=height*.9&&bottom<=height&&right>left&&right-left<=width*.4&&bottom>top&&bottom-top<=height*.05;
    }

    public static boolean adSignal(String text) {
        if (text == null || text.length() > 60) return false;
        String s = text.trim();
        return s.equals("广告") || s.equalsIgnoreCase("ad") || s.equals("推广")
            || s.contains("摇一摇") || s.contains("扭一扭") || s.contains("点击跳转")
            || s.contains("跳过广告") || s.contains("扭转手机") || s.contains("跳转至第三方应用");
    }

    /** Unknown apps require a compact skip action at a splash corner. */
    public static boolean splashCorner(int left,int top,int right,int bottom,int width,int height) {
        return width>0&&height>0&&left>=width*.55&&(bottom<=height*.30||top>=height*.78);
    }

    public static boolean accept(String text, boolean verifiedId, boolean adContext,
                                 int left, int top, int right, int bottom,
                                 int width, int height, long elapsed) {
        return accept(text, verifiedId, adContext, left, top, right, bottom, width, height, elapsed, "");
    }
    public static boolean accept(String text, boolean verifiedId, boolean adContext,
                                 int left, int top, int right, int bottom,
                                 int width, int height, long elapsed, String pkg) {
        if (!LaunchPolicy.active(elapsed, pkg) || width <= 0 || height <= 0) return false;
        if (right <= left || bottom <= top || left < 0 || top < 0 || right > width || bottom > height) return false;
        if (right - left > width * .42 || bottom - top > height * .12) return false;
        // Text is the primary condition; no ad marker, timer or resource ID required.
        // An ID can also occur in cached or non-ad pages. Without text, require
        // independent ad evidence rather than treating the ID alone as a skip.
        return isSkip(text) || (verifiedId && adContext)
            || (isClose(text) && adContext && splashCorner(left,top,right,bottom,width,height));
    }
}
