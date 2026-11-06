package io.github.ahmedps520.carbrowser;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

/**
 * The start page: a big search box and large bookmark tiles. It is written to a
 * real file so Back/Reload behave like any other page. Only plain ES5 JavaScript
 * and old-style CSS are used, because the WebView on Android 4.4 is Chrome 30-33.
 */
final class HomePage {

    /** Last Firefox build that runs on Android 4.1-4.4 (ARM), from Mozilla's official archive. */
    static final String FIREFOX_APK =
            "https://ftp.mozilla.org/pub/mobile/releases/68.11.0/android-api-16/en-US/"
                    + "fennec-68.11.0.en-US.android-arm.apk";

    private static final String ICON_APPS = "M4,8h4V4H4v4zM10,20h4v-4h-4v4zM4,20h4v-4H4v4zM4,14h4v-4H4v4z"
            + "M10,14h4v-4h-4v4zM16,4v4h4V4h-4zM10,8h4V4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z";
    private static final String ICON_STAR =
            "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z";
    private static final String ICON_DOWNLOAD = "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z";

    private static final String[] COLORS = {
            "#1e88e5", "#e53935", "#43a047", "#fb8c00", "#8e24aa", "#00897b", "#3949ab", "#d81b60"
    };

    private HomePage() {
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "home.html");
    }

    static String url(Context c) {
        return Uri.fromFile(file(c)).toString();
    }

    static boolean isHome(Context c, String url) {
        if (url == null || !url.startsWith("file:")) return false;
        String path = Uri.parse(url).getPath();
        return path != null && path.equals(file(c).getAbsolutePath());
    }

    static void write(Context c, List<Prefs.Bookmark> bookmarks, String engine) throws IOException {
        File f = file(c);
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(build(bookmarks, engine));
        } finally {
            w.close();
        }
    }

    static String build(List<Prefs.Bookmark> bookmarks, String engine) {
        StringBuilder h = new StringBuilder(8192);
        h.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Home</title><style>")
                .append("html,body{margin:0;padding:0;background:#11141a;color:#e8eaed;")
                .append("font-family:Roboto,'Droid Sans',sans-serif}")
                .append(".wrap{max-width:980px;margin:0 auto;padding:26px 16px 40px}")
                .append("h1{font-size:30px;font-weight:300;text-align:center;margin:6px 0 22px;color:#fff}")
                .append("form{display:-webkit-box;display:-webkit-flex;display:flex;max-width:760px;margin:0 auto 10px}")
                .append("#q{-webkit-box-flex:1;-webkit-flex:1;flex:1;width:100%;min-width:0;font-size:22px;")
                .append("padding:16px 22px;border:0;border-radius:32px 0 0 32px;background:#262b35;color:#fff;")
                .append("outline:none;-webkit-appearance:none}")
                .append("#go{font-size:20px;padding:0 30px;border:0;border-radius:0 32px 32px 0;")
                .append("background:#1e88e5;color:#fff;-webkit-appearance:none}")
                .append("h2{font-size:15px;font-weight:normal;color:#9aa0a6;text-transform:uppercase;")
                .append("letter-spacing:1px;margin:26px 8px 2px}")
                .append(".grid{text-align:center;font-size:0}")
                .append(".tile{display:inline-block;vertical-align:top;width:118px;margin:10px 6px;")
                .append("color:#e8eaed;text-decoration:none;font-size:16px}")
                .append(".ic{display:block;width:72px;height:72px;line-height:72px;margin:0 auto 8px;")
                .append("border-radius:20px;font-size:32px;font-weight:bold;color:#fff;text-align:center}")
                .append(".lb{display:block;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}")
                .append(".tile:active .ic{opacity:.6}")
                .append(".note{font-size:14px;color:#9aa0a6;text-align:center;margin:28px 10px 0;line-height:1.5}")
                .append("</style></head><body><div class=\"wrap\">")
                .append("<h1>Car Browser</h1>");

        // Without JavaScript the form still works: it submits straight to the search engine.
        h.append("<form id=\"f\" action=\"").append(esc(Search.formAction(engine)))
                .append("\" method=\"get\"><input id=\"q\" name=\"q\" type=\"text\" ")
                .append("placeholder=\"Search or type a web address\" autocomplete=\"off\" ")
                .append("autocapitalize=\"off\" autocorrect=\"off\" spellcheck=\"false\">")
                .append("<button id=\"go\" type=\"submit\">Go</button></form>");

        if (!bookmarks.isEmpty()) {
            h.append("<h2>Bookmarks</h2><div class=\"grid\">");
            for (Prefs.Bookmark b : bookmarks) {
                if (!b.url.startsWith("http://") && !b.url.startsWith("https://")) continue;
                tile(h, b.title, b.url);
            }
            h.append("</div>");
        }

        h.append("<h2>Tools</h2><div class=\"grid\">");
        tile(h, "Apps & Settings", "carbrowser://apps", "#546e7a", svg(ICON_APPS));
        tile(h, "Edit bookmarks", "carbrowser://bookmarks", "#546e7a", svg(ICON_STAR));
        tile(h, "Get Firefox", FIREFOX_APK, "#e66000", svg(ICON_DOWNLOAD));
        tile(h, "Help", "carbrowser://help", "#546e7a", "?");
        h.append("</div>");

        h.append("<p class=\"note\">Tip: if a big site doesn’t work here, tap <b>Get Firefox</b> ")
                .append("to install a browser with a newer engine.<br>Please only browse while parked.</p>");

        // Send the search box to the app so it can decide between "open address" and "search".
        h.append("</div><script>(function(){var f=document.getElementById('f'),")
                .append("q=document.getElementById('q');f.onsubmit=function(){")
                .append("var v=q.value.replace(/^\\s+|\\s+$/g,'');if(!v)return false;")
                .append("location.href='carbrowser://go?q='+encodeURIComponent(v);return false;};})();")
                .append("</script></body></html>");
        return h.toString();
    }

    private static void tile(StringBuilder h, String title, String url) {
        String t = title == null || title.trim().isEmpty() ? url : title.trim();
        String letter = firstLetter(t);
        String color = COLORS[(t.hashCode() & 0x7fffffff) % COLORS.length];
        tile(h, t, url, color, esc(letter));
    }

    /** Inline SVG works in every Android 4.x WebView and needs no special fonts. */
    private static String svg(String path) {
        return "<svg viewBox=\"0 0 24 24\" width=\"40\" height=\"40\" "
                + "style=\"vertical-align:middle;margin-top:-6px\"><path fill=\"#fff\" d=\""
                + path + "\"/></svg>";
    }

    /** {@code iconHtml} must already be safe HTML. */
    private static void tile(StringBuilder h, String title, String url, String color, String iconHtml) {
        h.append("<a class=\"tile\" href=\"").append(esc(url)).append("\">")
                .append("<span class=\"ic\" style=\"background:").append(color).append("\">")
                .append(iconHtml).append("</span>")
                .append("<span class=\"lb\">").append(esc(title)).append("</span></a>");
    }

    private static String firstLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetterOrDigit(ch)) return String.valueOf(Character.toUpperCase(ch));
        }
        return "•";
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&#39;"); break;
                default: b.append(ch);
            }
        }
        return b.toString();
    }
}
