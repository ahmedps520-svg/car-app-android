package io.github.ahmedps520.carbrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/**
 * Downloads files the user taps on.
 *
 * On Android 4.x-6.x it downloads by itself using {@link Tls}, because the system
 * download manager there can't open many modern HTTPS sites. On newer Android the
 * system download manager is used. APK files are offered for installation.
 */
final class Downloader implements DownloadListener {

    static final String APK_MIME = "application/vnd.android.package-archive";

    private static final Pattern CD_FILENAME_STAR =
            Pattern.compile("filename\\*\\s*=\\s*(?:UTF-8|utf-8)''([^;]+)");
    private static final Pattern CD_FILENAME =
            Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?");

    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<Long, String> systemDownloads = new HashMap<>();
    private BroadcastReceiver completeReceiver;

    // Before Android 13 the flag doesn't exist and isn't needed; from 13 on it is passed.
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    Downloader(Activity activity) {
        this.activity = activity;
        if (useSystemDownloadManager()) {
            completeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onSystemDownloadComplete(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(completeReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(completeReceiver, filter);
            }
        }
    }

    void release() {
        if (completeReceiver != null) {
            try {
                activity.unregisterReceiver(completeReceiver);
            } catch (IllegalArgumentException ignored) {
                // not registered
            }
            completeReceiver = null;
        }
    }

    private static boolean useSystemDownloadManager() {
        return Build.VERSION.SDK_INT >= 24;
    }

    @Override
    public void onDownloadStart(final String url, final String userAgent, String contentDisposition,
                                String mimetype, long contentLength) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            Toast.makeText(activity, R.string.download_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        final String name = fileName(url, contentDisposition, mimetype);
        final boolean apk = name.toLowerCase(Locale.US).endsWith(".apk") || APK_MIME.equals(mimetype);
        final String mime = apk ? APK_MIME : mimetype;

        String message = name;
        if (contentLength > 0) message += "\n" + Formatter.formatFileSize(activity, contentLength);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.download_q_title)
                .setMessage(message)
                .setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (useSystemDownloadManager()) {
                            startSystem(url, userAgent, name, mime, apk);
                        } else {
                            startOwn(url, userAgent, name, mime, apk);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------------
    // Android 7+: system DownloadManager

    private void startSystem(String url, String userAgent, String name, String mime, boolean apk) {
        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            if (userAgent != null) r.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) r.addRequestHeader("Cookie", cookie);
            if (mime != null) r.setMimeType(mime);
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            // App folder: needs no storage permission.
            r.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, name);
            long id = dm.enqueue(r);
            if (apk) systemDownloads.put(id, name);
            Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            showError(e.getMessage());
        }
    }

    private void onSystemDownloadComplete(long id) {
        String name = systemDownloads.remove(id);
        if (name == null) return;
        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
        boolean ok = false;
        try {
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                ok = status == DownloadManager.STATUS_SUCCESSFUL;
            }
        } finally {
            if (c != null) c.close();
        }
        if (!ok) {
            showError(null);
            return;
        }
        Uri uri = dm.getUriForDownloadedFile(id);
        if (uri != null) offerInstall(name, uri);
    }

    // ---------------------------------------------------------------------------------------
    // Android 4.1-6.x: our own downloader with up-to-date certificates

    private void startOwn(final String url, final String userAgent, final String name, final String mime,
                          final boolean apk) {
        File dir = Build.VERSION.SDK_INT >= 23
                ? activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) // no permission needed
                : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            showError("Storage is not available");
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        final File out = uniqueFile(dir, name);
        final String cookie = CookieManager.getInstance().getCookie(url);
        final boolean[] cancelled = {false};

        int pad = dp(20);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        final ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(true);
        box.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
        final TextView status = new TextView(activity);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, dp(10), 0, 0);
        box.addView(status);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(name)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        cancelled[0] = true;
                    }
                })
                .show();
        status.setText(R.string.downloading);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    download(url, userAgent, cookie, out, cancelled, bar, status);
                    MediaScannerConnection.scanFile(activity.getApplicationContext(),
                            new String[]{out.getAbsolutePath()}, new String[]{mime}, null);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            dismiss(dialog);
                            if (apk) {
                                offerInstall(out.getName(), Uri.fromFile(out));
                            } else {
                                offerOpen(out, mime);
                            }
                        }
                    });
                } catch (final Exception e) {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            dismiss(dialog);
                            if (!cancelled[0]) showError(e.getMessage());
                        }
                    });
                }
            }
        }, "download").start();
    }

    private void download(String url, String userAgent, String cookie, File out, boolean[] cancelled,
                          final ProgressBar bar, final TextView status) throws IOException {
        HttpURLConnection c = null;
        String current = url;
        try {
            for (int redirects = 0; ; redirects++) {
                c = (HttpURLConnection) new URL(current).openConnection();
                if (c instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) c).setSSLSocketFactory(Tls.socketFactory(activity));
                }
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                if (userAgent != null) c.setRequestProperty("User-Agent", userAgent);
                String ck = current.equals(url) ? cookie : CookieManager.getInstance().getCookie(current);
                if (ck != null) c.setRequestProperty("Cookie", ck);
                int code = c.getResponseCode();
                if (code >= 300 && code < 400 && code != 304) {
                    String location = c.getHeaderField("Location");
                    c.disconnect();
                    if (location == null || redirects >= 10) throw new IOException("Too many redirects");
                    current = new URL(new URL(current), location).toString();
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK) throw new IOException("Server answered HTTP " + code);
                break;
            }

            final long total = parseLong(c.getHeaderField("Content-Length"));
            InputStream in = c.getInputStream();
            OutputStream os = new FileOutputStream(out);
            try {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                long lastUpdate = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled[0]) throw new IOException("Cancelled");
                    os.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 250) {
                        lastUpdate = now;
                        final long d = done;
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                String text = Formatter.formatFileSize(activity, d);
                                if (total > 0) {
                                    bar.setIndeterminate(false);
                                    bar.setProgress((int) (d * 1000 / total));
                                    text += " / " + Formatter.formatFileSize(activity, total);
                                }
                                status.setText(text);
                            }
                        });
                    }
                }
                if (total > 0 && done != total) throw new IOException("Download was interrupted");
            } finally {
                os.close();
                in.close();
            }
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ---------------------------------------------------------------------------------------

    private void offerInstall(String name, final Uri uri) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.install_q_title)
                .setMessage(activity.getString(R.string.install_q, name))
                .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        install(uri);
                    }
                })
                .setNegativeButton(R.string.later, null)
                .show();
    }

    private void install(Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.install_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void offerOpen(final File file, final String mime) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.download_saved, file.getName()))
                .setPositiveButton(R.string.open, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String type = mime;
                        if (type == null || type.equals("application/octet-stream")) {
                            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension(file.getName()));
                        }
                        try {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(Uri.fromFile(file), type == null ? "*/*" : type)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(activity, R.string.no_app, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showError(String detail) {
        if (activity.isFinishing()) return;
        String msg = activity.getString(R.string.download_failed);
        if (detail != null && !detail.isEmpty()) msg += "\n\n" + detail;
        new AlertDialog.Builder(activity).setMessage(msg).setPositiveButton(R.string.ok, null).show();
    }

    private static void dismiss(AlertDialog d) {
        try {
            if (d.isShowing()) d.dismiss();
        } catch (IllegalArgumentException ignored) {
            // window already gone
        }
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    private static long parseLong(String s) {
        if (s == null) return -1;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; ; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.US);
    }

    /**
     * Picks a file name. Android's URLUtil.guessFileName mangles names on old versions
     * (e.g. "app.apk" sent as application/octet-stream became "app.bin"), so do it here.
     */
    static String fileName(String url, String contentDisposition, String mime) {
        String name = null;
        if (contentDisposition != null) {
            Matcher m = CD_FILENAME_STAR.matcher(contentDisposition);
            if (m.find()) {
                name = decode(m.group(1).trim());
            } else {
                m = CD_FILENAME.matcher(contentDisposition);
                if (m.find()) name = m.group(1).trim();
            }
        }
        if (name == null || name.isEmpty()) {
            name = Uri.parse(url).getLastPathSegment();
        }
        if (name == null || name.isEmpty()) name = "download";

        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch < 32 || "\\/:*?\"<>|".indexOf(ch) >= 0) clean.append('_');
            else clean.append(ch);
        }
        name = clean.toString().trim();
        if (name.startsWith(".")) name = "download" + name;
        if (name.length() > 120) name = name.substring(name.length() - 120);

        if (extension(name).isEmpty() && mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null) name += "." + ext;
        }
        return name;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return s;
        }
    }
}
