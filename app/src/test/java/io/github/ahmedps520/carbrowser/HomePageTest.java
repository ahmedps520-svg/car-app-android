package io.github.ahmedps520.carbrowser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class HomePageTest {

    private static List<Prefs.Bookmark> sample() {
        List<Prefs.Bookmark> list = new ArrayList<>();
        list.add(new Prefs.Bookmark("Google", "https://www.google.com/"));
        list.add(new Prefs.Bookmark("YouTube", "https://m.youtube.com/"));
        list.add(new Prefs.Bookmark("Maps", "https://www.google.com/maps"));
        list.add(new Prefs.Bookmark("Weather", "https://www.google.com/search?q=weather"));
        list.add(new Prefs.Bookmark("News", "https://news.google.com/"));
        list.add(new Prefs.Bookmark("Wikipedia", "https://en.m.wikipedia.org/"));
        list.add(new Prefs.Bookmark("Translate", "https://translate.google.com/"));
        return list;
    }

    @Test
    public void escapesBookmarkTitlesAndUrls() {
        List<Prefs.Bookmark> list = new ArrayList<>();
        list.add(new Prefs.Bookmark("<script>alert(1)</script>", "https://a.example/?x=\"><img src=x>"));
        String html = HomePage.build(list, Search.GOOGLE);
        assertFalse(html.contains("<script>alert"));
        assertFalse(html.contains("\"><img"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    public void skipsNonWebBookmarks() {
        List<Prefs.Bookmark> list = new ArrayList<>();
        list.add(new Prefs.Bookmark("evil", "javascript:alert(1)"));
        String html = HomePage.build(list, Search.GOOGLE);
        assertFalse(html.contains("javascript:alert"));
    }

    @Test
    public void searchFormFollowsEngine() {
        assertTrue(HomePage.build(sample(), Search.BING).contains("action=\"https://www.bing.com/search\""));
        assertTrue(HomePage.build(sample(), Search.GOOGLE).contains("action=\"https://www.google.com/search\""));
    }

    @Test
    public void writesPreviewForManualCheck() throws Exception {
        File out = new File("build/home-preview.html");
        Writer w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
        try {
            w.write(HomePage.build(sample(), Search.GOOGLE));
        } finally {
            w.close();
        }
        assertTrue(out.length() > 1000);
    }
}
