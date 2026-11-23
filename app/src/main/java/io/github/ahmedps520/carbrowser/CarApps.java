package io.github.ahmedps520.carbrowser;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.Locale;

/**
 * Apps that bring YouTube and Discord to Android 4.x, where their websites and official
 * apps no longer work. Both are open source and downloaded from their developers' own
 * release pages.
 */
final class CarApps {

    /** SmartTube: YouTube client for old Android TV boxes (Android 4.3+). Developer's short link. */
    static final String YOUTUBE_APK = "https://kutt.to/stn_stable";
    static final String[] YOUTUBE_PACKAGES = {"org.smarttube.stable", "org.smarttube.beta"};

    /** Discofi: Discord client for Android below 5.0 (text chat, DMs, notifications). */
    static final String DISCORD_APK = "https://codeberg.org/gtrxAC/Discofi-Android/releases/download/v1.0.0/discofi.apk";
    static final String[] DISCORD_PACKAGES = {"fi.gtrxac.discord"};

    private CarApps() {
    }

    /** First installed package from the list, or null. */
    static String installed(Context c, String[] packages) {
        PackageManager pm = c.getPackageManager();
        for (String p : packages) {
            if (pm.getLaunchIntentForPackage(p) != null) return p;
        }
        return null;
    }

    static boolean isYouTubeUrl(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        return host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be");
    }

    /**
     * Opens a YouTube link in SmartTube when it's installed (the website can't play on
     * Android 4.x). Returns false if SmartTube isn't installed or couldn't open it.
     */
    static boolean openYouTube(Context c, Uri uri) {
        String pkg = installed(c, YOUTUBE_PACKAGES);
        if (pkg == null) return false;
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean isVideo = uri.getQueryParameter("v") != null || "youtu.be".equalsIgnoreCase(uri.getHost())
                || path.startsWith("/shorts/") || path.startsWith("/live/");
        Intent i = isVideo
                ? new Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
                : c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            // e.g. this SmartTube version doesn't take links: fall back to its main screen
            Intent main = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (main == null) return false;
            try {
                c.startActivity(main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
    }
}
