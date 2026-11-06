package io.github.ahmedps520.carbrowser;

/** User-agent strings for "mobile" and "desktop site" modes. */
final class UserAgent {

    private UserAgent() {
    }

    /**
     * The WebView's own user agent without the "; wv" marker, which makes some
     * sites (e.g. Google sign-in) treat the browser as an embedded app view.
     */
    static String mobile(String webViewUa) {
        return webViewUa.replace("; wv)", ")");
    }

    /** Same engine and version, but claims a desktop Linux PC so sites send the full layout. */
    static String desktop(String ua) {
        String d = ua.replaceFirst("\\([^)]*\\)", "(X11; Linux x86_64)");
        d = d.replaceFirst(" Version/[0-9.]+", "");
        d = d.replace(" Mobile Safari/", " Safari/").replace(" Mobile/", " ");
        return d;
    }
}
