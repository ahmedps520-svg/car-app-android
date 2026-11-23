package io.github.ahmedps520.carbrowser;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Pattern;

/** Turns what the user typed into a URL: either the address itself or a web search. */
final class Search {

    static final String GOOGLE = "google";
    static final String DUCKDUCKGO = "duckduckgo";
    static final String BING = "bing";

    /**
     * Default engine. Since 2025 Google Search needs modern JavaScript, so on Android 4.x
     * WebViews (Chrome 30-33) it only shows a "having trouble?" page. Bing still returns
     * server-rendered results there.
     */
    static final String DEFAULT = BING;

    static final String[] KEYS = {BING, DUCKDUCKGO, GOOGLE};
    static final String[] NAMES = {"Bing", "DuckDuckGo", "Google (needs a newer screen)"};

    private static final Pattern HAS_SCHEME =
            Pattern.compile("^(https?|ftp|file|content)://.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern IP_OR_LOCAL =
            Pattern.compile("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d{1,5})?([/?#].*)?$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern DOMAIN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*"
                    + "\\.[a-z]{2,}\\.?(:\\d{1,5})?([/?#].*)?$", Pattern.CASE_INSENSITIVE);

    private Search() {
    }

    /** Base URL of the search page (used as the home page form action). */
    static String formAction(String engine) {
        if (DUCKDUCKGO.equals(engine)) return "https://html.duckduckgo.com/html/";
        if (BING.equals(engine)) return "https://www.bing.com/search";
        return "https://www.google.com/search";
    }

    static String searchUrl(String engine, String query) {
        try {
            return formAction(engine) + "?q=" + URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e); // UTF-8 is always available
        }
    }

    /** Returns the URL to load for the typed text, or null if there is nothing to load. */
    static String toUrl(String input, String engine) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        boolean hasSpace = false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                hasSpace = true;
                break;
            }
        }
        if (!hasSpace) {
            if (HAS_SCHEME.matcher(s).matches()) return s;
            if (s.toLowerCase(Locale.US).startsWith("about:")) return s;
            if (IP_OR_LOCAL.matcher(s).matches()) return "http://" + s;
            if (DOMAIN.matcher(s).matches()) return "https://" + s;
        }
        return searchUrl(engine, s);
    }
}
