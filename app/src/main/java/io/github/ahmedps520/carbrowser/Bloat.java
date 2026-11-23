package io.github.ahmedps520.carbrowser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Sorts installed apps into "usually safe to switch off", "leave alone" and "unknown".
 *
 * Errs heavily on the side of KEEP: on a car head unit the radio, CAN-bus (climate
 * control, steering-wheel keys), reversing camera, Bluetooth and launcher are ordinary
 * apps, and switching one off can break the car's controls. Anything that looks even
 * slightly car- or system-related is hidden from the cleanup list.
 */
final class Bloat {

    enum Kind { SAFE, OTHER, KEEP }

    /** Known AOSP / phone leftovers that a car screen doesn't need. */
    private static final Set<String> SAFE_EXACT = new HashSet<>(Arrays.asList(
            "com.android.email", "com.android.exchange", "com.android.calendar",
            "com.android.deskclock", "com.android.calculator2", "com.android.soundrecorder",
            "com.android.quicksearchbox", "com.android.videoeditor", "com.android.galaxy4",
            "com.android.noisefield", "com.android.phasebeam", "com.android.magicsmoke",
            "com.android.musicvis", "com.android.wallpaper.holospiral", "com.android.wallpaper.livepicker",
            "com.android.dreams.basic", "com.android.dreams.phototable", "com.android.printspooler",
            "com.android.mms", "com.android.smspush", "com.android.stk", "com.android.cellbroadcastreceiver",
            "com.android.development", "com.android.facelock", "com.android.gesture.builder",
            "com.android.speechrecorder", "com.android.basicsmsreceiver", "com.android.voicedialer",
            "com.android.providers.partnerbookmarks", "com.android.apis", "com.android.wallpaper",
            "com.android.calendar2", "com.android.lockclock"));

    /** Chinese preinstalls that are useless outside China (still filtered by KEEP rules first). */
    private static final String[] SAFE_PREFIXES = {
            "com.baidu.", "com.tencent.", "com.qihoo", "com.ucmobile", "com.uc.", "com.sohu.",
            "com.sina.", "com.youku.", "com.iqiyi.", "com.netease.", "com.taobao.", "com.alibaba.",
            "com.jingdong.", "com.ss.android.", "com.qq.", "com.kingsoft.", "cn.wps.", "com.wandoujia.",
            "com.android.wallpaper.", "com.android.dreams."
    };

    /** Core Android pieces that must never be switched off. */
    private static final Set<String> KEEP_EXACT = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.packageinstaller", "com.android.defcontainer", "com.android.shell",
            "com.android.keychain", "com.android.certinstaller", "com.android.externalstorage",
            "com.android.documentsui", "com.android.htmlviewer", "com.android.vpndialogs",
            "com.android.proxyhandler", "com.android.backupconfirm", "com.android.sharedstoragebackup",
            "com.android.keyguard", "com.android.onetimeinitializer", "com.android.wallpapercropper",
            "com.android.webview", "com.google.android.webview", "com.android.location.fused",
            "com.android.inputdevices", "com.android.statementservice", "com.android.managedprovisioning",
            "com.android.browser", "com.estrongs.android.pop"));

    /** Anything whose package name contains one of these is treated as car- or system-critical. */
    private static final String[] KEEP_CONTAINS = {
            "launcher", "desktop", "home", "systemui", "setting", "bluetooth", "radio", "provider",
            "input", "keyboard", "pinyin", "camera", "reverse", "backcar", "tpms", "navi", "map",
            "location", "wifi", "wlan", "update", "ota", "phonelink", "carplay", "autolink", "easyconn",
            "zlink", "carlife", "mirror", "voice", "speech", "tts", "steer", "factory", "canbus", "mcu",
            "media", "music", "video", "audio", "player", "gallery", "picture", "photo", "phone",
            "dialer", "contacts", "telephony", "rockchip", "service", "server", "framework", "aux",
            "obd", "dvr", "avm", "gps", "dab", "tuner", "climate", "hvac", "vehicle", "auto"
    };

    /** Short tokens only matched as whole package segments (so "rk" doesn't hit "network"). */
    private static final Set<String> KEEP_SEGMENTS = new HashSet<>(Arrays.asList(
            "can", "car", "bt", "rk", "mcu", "swc", "eq", "nav", "ime", "mtc", "hct", "fyt", "zxw",
            "sys", "system", "core", "res", "ui", "hw", "hal"));

    private Bloat() {
    }

    /**
     * @param pkg        package name
     * @param systemUid  true if the app runs as a system user (uid below 10000)
     * @param persistent true if Android keeps the app always running
     * @param isHome     true if the app is a home screen (the car's launcher)
     * @param isSelf     true for Car Browser itself
     */
    static Kind classify(String pkg, boolean systemUid, boolean persistent, boolean isHome, boolean isSelf) {
        if (isSelf || systemUid || persistent || isHome) return Kind.KEEP;
        String p = pkg.toLowerCase(Locale.US);
        if (SAFE_EXACT.contains(p)) return Kind.SAFE;
        if (KEEP_EXACT.contains(p)) return Kind.KEEP;
        for (String k : KEEP_CONTAINS) {
            if (p.contains(k)) return Kind.KEEP;
        }
        for (String seg : p.split("[._]")) {
            if (KEEP_SEGMENTS.contains(seg) || seg.startsWith("car")) return Kind.KEEP;
        }
        for (String prefix : SAFE_PREFIXES) {
            if (p.startsWith(prefix)) return Kind.SAFE;
        }
        return Kind.OTHER;
    }
}
