package cn.local.adskip;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe persistent store for historical skip button hotspots.
 * Hotspots are indexed by app package name and sorted by hit count.
 */
public final class HotspotStore {
    private static final String PREF_KEY = "hotspots_v1";
    private static final long MAX_AGE_MS = 180L * 24 * 60 * 60 * 1000; // 180 days (~6 months)
    private static final int MAX_HOTSPOTS_PER_APP = 3;
    private static final int MERGE_TOLERANCE_PX = 80;

    private final SharedPreferences prefs;
    private final Map<String, List<Hotspot>> hotspots = new HashMap<>();

    public HotspotStore(Context context) {
        this(context.getSharedPreferences("settings", Context.MODE_PRIVATE));
    }

    public HotspotStore(SharedPreferences prefs) {
        this.prefs = prefs;
        load();
    }

    public synchronized void recordHit(String pkg, int left, int top, int right, int bottom, int refW, int refH, String source) {
        if (pkg == null || pkg.isEmpty() || refW <= 0 || refH <= 0 || left >= right || top >= bottom) return;
        reload();
        List<Hotspot> list = hotspots.get(pkg);
        if (list == null) {
            list = new ArrayList<>();
            hotspots.put(pkg, list);
        }
        long now = System.currentTimeMillis();
        Hotspot matched = null;
        for (Hotspot h : list) {
            if (h.isCloseTo(left, top, right, bottom, refW, refH, MERGE_TOLERANCE_PX)) {
                matched = h;
                break;
            }
        }
        if (matched != null) {
            matched.mergeWith(left, top, right, bottom, refW, refH, source, now);
        } else {
            list.add(new Hotspot(left, top, right, bottom, refW, refH, 1, now, source));
        }
        Collections.sort(list, (a, b) -> Integer.compare(b.hitCount, a.hitCount));
        while (list.size() > MAX_HOTSPOTS_PER_APP) {
            list.remove(list.size() - 1);
        }
        save();
    }

    public synchronized Hotspot getBestHotspot(String pkg, int targetW, int targetH) {
        if (pkg == null) return null;
        List<Hotspot> list = getHotspots(pkg);
        for(Hotspot h:list)if(h.predictive())return h.scaleTo(targetW,targetH);
        return null;
    }

    public synchronized List<Hotspot> getHotspots(String pkg) {
        if (pkg == null) return Collections.emptyList();
        List<Hotspot> list = hotspots.get(pkg);
        List<Hotspot> fresh = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (list != null) {
            for (Hotspot h : list) {
                if (h.lastSeen > 0 && Math.abs(now - h.lastSeen) < MAX_AGE_MS) fresh.add(h);
            }
        }
        return fresh;
    }

    public synchronized List<String> getAllAppPackages() {
        List<String> result = new ArrayList<>();
        for (String pkg : hotspots.keySet()) {
            if (!getHotspots(pkg).isEmpty()) {
                result.add(pkg);
            }
        }
        Collections.sort(result);
        return result;
    }

    public synchronized int getTotalHitsCount() {
        int total = 0;
        for (List<Hotspot> list : hotspots.values()) {
            for (Hotspot h : list) total += h.hitCount;
        }
        return total;
    }

    public synchronized void clear(String pkg) {
        if (pkg == null) return;
        reload();
        hotspots.remove(pkg);
        save();
    }

    public synchronized void clearAll() {
        hotspots.clear();
        save();
    }
    public synchronized void reload(){hotspots.clear();load();}

    public synchronized boolean remove(String pkg,Hotspot expected){
        reload();
        List<Hotspot> list=hotspots.get(pkg);
        if(list==null)return false;
        for(int i=0;i<list.size();i++){
            Hotspot h=list.get(i);
            if(h.left==expected.left&&h.top==expected.top&&h.right==expected.right&&h.bottom==expected.bottom
                &&h.refWidth==expected.refWidth&&h.refHeight==expected.refHeight&&h.lastSeen==expected.lastSeen&&h.hitCount==expected.hitCount){
                list.remove(i);if(list.isEmpty())hotspots.remove(pkg);save();return true;
            }
        }
        return false;
    }

    public synchronized int getAppCount() {
        return getAllAppPackages().size();
    }

    public synchronized int getTotalHotspotsCount() {
        int count = 0;
        for (String pkg : getAllAppPackages()) {
            count += getHotspots(pkg).size();
        }
        return count;
    }

    private void load() {
        String json = prefs.getString(PREF_KEY, null);
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray keys = root.names();
            if (keys == null) return;
            for (int i = 0; i < keys.length(); i++) {
                String pkg = keys.getString(i);
                JSONArray array = root.optJSONArray(pkg);
                if (array == null) continue;
                List<Hotspot> list = new ArrayList<>();
                for (int j = 0; j < array.length(); j++) {
                    JSONObject obj = array.getJSONObject(j);
                    int l = obj.getInt("l");
                    int t = obj.getInt("t");
                    int r = obj.getInt("r");
                    int b = obj.getInt("b");
                    int rw = obj.getInt("rw");
                    int rh = obj.getInt("rh");
                    int hits = obj.optInt("hits", 1);
                    long time = obj.optLong("time", 0);
                    String src = obj.optString("src", "");
                    list.add(new Hotspot(l, t, r, b, rw, rh, hits, time, src));
                }
                if (!list.isEmpty()) {
                    Collections.sort(list, (a, b) -> Integer.compare(b.hitCount, a.hitCount));
                    hotspots.put(pkg, list);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("AdSkip", "Failed to load hotspots", e);
        }
    }

    private void save() {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, List<Hotspot>> entry : hotspots.entrySet()) {
                JSONArray array = new JSONArray();
                for (Hotspot h : entry.getValue()) {
                    JSONObject obj = new JSONObject();
                    obj.put("l", h.left).put("t", h.top).put("r", h.right).put("b", h.bottom)
                       .put("rw", h.refWidth).put("rh", h.refHeight)
                       .put("hits", h.hitCount).put("time", h.lastSeen).put("src", h.source);
                    array.put(obj);
                }
                root.put(entry.getKey(), array);
            }
            prefs.edit().putString(PREF_KEY, root.toString()).apply();
        } catch (Exception e) {
            android.util.Log.e("AdSkip", "Failed to save hotspots", e);
        }
    }
}
