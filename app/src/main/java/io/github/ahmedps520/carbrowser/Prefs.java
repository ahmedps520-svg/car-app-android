package io.github.ahmedps520.carbrowser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Small wrapper around SharedPreferences: bookmarks and browser settings. */
final class Prefs {

    static final class Bookmark {
        final String title;
        final String url;

        Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_TEXT_ZOOM = "text_zoom";
    private static final String KEY_DESKTOP = "desktop";
    private static final String KEY_ENGINE = "engine";
    private static final String KEY_MIGRATION = "migration";
    private static final String KEY_FLOATING = "floating_button";
    private static final String KEY_FLOATING_ASKED = "floating_button_asked";
    private static final String KEY_BUBBLE_X = "bubble_x";
    private static final String KEY_BUBBLE_Y = "bubble_y";

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("carbrowser", Context.MODE_PRIVATE);
        if (!sp.contains(KEY_BOOKMARKS)) {
            saveBookmarks(defaultBookmarks());
            sp.edit().putInt(KEY_MIGRATION, 1).apply();
        }
        migrate();
    }

    /** Lightweight, works without modern JavaScript, finds the location by itself. */
    static final String WEATHER = "https://wttr.in/?0";
    static final String BING_HOME = "https://www.bing.com/";
    private static final String OLD_GOOGLE_HOME = "https://www.google.com/";
    private static final String OLD_GOOGLE_WEATHER = "https://www.google.com/search?q=weather";

    /**
     * Version 1.0/1.1 shipped Google tiles. Google Search no longer works on Android 4.x
     * WebViews, so swap those two untouched default tiles for working ones, once.
     */
    private void migrate() {
        if (sp.getInt(KEY_MIGRATION, 0) >= 1) return;
        List<Bookmark> list = bookmarks();
        for (int i = 0; i < list.size(); i++) {
            Bookmark b = list.get(i);
            if (b.url.equals(OLD_GOOGLE_WEATHER)) {
                list.set(i, new Bookmark(b.title, WEATHER));
            } else if (b.url.equals(OLD_GOOGLE_HOME) && b.title.equals("Google") && !containsUrl(list, BING_HOME)) {
                list.set(i, new Bookmark("Bing", BING_HOME));
            }
        }
        saveBookmarks(list);
        sp.edit().putInt(KEY_MIGRATION, 1).apply();
    }

    private static boolean containsUrl(List<Bookmark> list, String url) {
        for (Bookmark b : list) {
            if (b.url.equals(url)) return true;
        }
        return false;
    }

    private static List<Bookmark> defaultBookmarks() {
        List<Bookmark> list = new ArrayList<>();
        list.add(new Bookmark("Bing", BING_HOME));
        list.add(new Bookmark("YouTube", "https://m.youtube.com/"));
        list.add(new Bookmark("Maps", "https://www.google.com/maps"));
        list.add(new Bookmark("Weather", WEATHER));
        list.add(new Bookmark("News", "https://news.google.com/"));
        list.add(new Bookmark("Wikipedia", "https://en.m.wikipedia.org/"));
        list.add(new Bookmark("Translate", "https://translate.google.com/"));
        return list;
    }

    // ---- Bookmarks ----

    List<Bookmark> bookmarks() {
        List<Bookmark> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(KEY_BOOKMARKS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Bookmark(o.optString("t"), o.optString("u")));
            }
        } catch (JSONException ignored) {
            // Corrupt data: behave as if there are no bookmarks.
        }
        return list;
    }

    private void saveBookmarks(List<Bookmark> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Bookmark b : list) {
                arr.put(new JSONObject().put("t", b.title).put("u", b.url));
            }
        } catch (JSONException ignored) {
            // put() only throws for non-finite numbers.
        }
        sp.edit().putString(KEY_BOOKMARKS, arr.toString()).apply();
    }

    boolean isBookmarked(String url) {
        for (Bookmark b : bookmarks()) {
            if (b.url.equals(url)) return true;
        }
        return false;
    }

    void addBookmark(String title, String url) {
        if (isBookmarked(url)) return;
        List<Bookmark> list = bookmarks();
        list.add(new Bookmark(title, url));
        saveBookmarks(list);
    }

    void removeBookmark(String url) {
        List<Bookmark> list = bookmarks();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).url.equals(url)) list.remove(i);
        }
        saveBookmarks(list);
    }

    // ---- Settings ----

    int textZoom() {
        return sp.getInt(KEY_TEXT_ZOOM, 100);
    }

    void setTextZoom(int zoom) {
        sp.edit().putInt(KEY_TEXT_ZOOM, zoom).apply();
    }

    boolean desktop() {
        return sp.getBoolean(KEY_DESKTOP, false);
    }

    void setDesktop(boolean desktop) {
        sp.edit().putBoolean(KEY_DESKTOP, desktop).apply();
    }

    String engine() {
        return sp.getString(KEY_ENGINE, Search.DEFAULT);
    }

    void setEngine(String engine) {
        sp.edit().putString(KEY_ENGINE, engine).apply();
    }

    // ---- Floating button ----

    boolean floatingButton() {
        return sp.getBoolean(KEY_FLOATING, false);
    }

    void setFloatingButton(boolean on) {
        sp.edit().putBoolean(KEY_FLOATING, on).putBoolean(KEY_FLOATING_ASKED, true).apply();
    }

    boolean floatingButtonAsked() {
        return sp.getBoolean(KEY_FLOATING_ASKED, false);
    }

    /** Saved position of the floating button, or -1 if never moved. */
    int bubbleX() {
        return sp.getInt(KEY_BUBBLE_X, -1);
    }

    int bubbleY() {
        return sp.getInt(KEY_BUBBLE_Y, -1);
    }

    void setBubblePosition(int x, int y) {
        sp.edit().putInt(KEY_BUBBLE_X, x).putInt(KEY_BUBBLE_Y, y).apply();
    }
}
