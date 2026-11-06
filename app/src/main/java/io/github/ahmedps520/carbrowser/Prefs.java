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

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getSharedPreferences("carbrowser", Context.MODE_PRIVATE);
        if (!sp.contains(KEY_BOOKMARKS)) {
            saveBookmarks(defaultBookmarks());
        }
    }

    private static List<Bookmark> defaultBookmarks() {
        List<Bookmark> list = new ArrayList<>();
        list.add(new Bookmark("Google", "https://www.google.com/"));
        list.add(new Bookmark("YouTube", "https://m.youtube.com/"));
        list.add(new Bookmark("Maps", "https://www.google.com/maps"));
        list.add(new Bookmark("Weather", "https://www.google.com/search?q=weather"));
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
        return sp.getString(KEY_ENGINE, Search.GOOGLE);
    }

    void setEngine(String engine) {
        sp.edit().putString(KEY_ENGINE, engine).apply();
    }
}
