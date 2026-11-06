package io.github.ahmedps520.carbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.SslErrorHandler;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles certificate errors from the WebView.
 *
 * Most errors on old car screens are "untrusted" errors caused by the outdated root
 * list, not real attacks. For those, the server is checked again with an up-to-date
 * root list ({@link Tls}); if it passes, the page loads with no prompt. Anything else
 * asks the user. A wrong clock gets its own explanation.
 */
final class SslHelper {

    interface ClockProblemListener {
        void onClockProblem();
    }

    private final Activity activity;
    private final ClockProblemListener clockListener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Set<String> allowedHosts = new HashSet<>();
    private final Map<String, List<SslErrorHandler>> pending = new HashMap<>();

    SslHelper(Activity activity, ClockProblemListener clockListener) {
        this.activity = activity;
        this.clockListener = clockListener;
    }

    void clear() {
        allowedHosts.clear();
    }

    void handle(SslErrorHandler handler, SslError error) {
        String url = error.getUrl();
        final String host = url == null ? null : Uri.parse(url).getHost();
        if (host == null) {
            handler.cancel();
            return;
        }
        if (allowedHosts.contains(host)) {
            handler.proceed();
            return;
        }
        List<SslErrorHandler> waiting = pending.get(host);
        if (waiting != null) {
            waiting.add(handler); // a check or prompt for this host is already running
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(handler);
        pending.put(host, waiting);

        boolean dateError = error.hasError(SslError.SSL_DATE_INVALID)
                || error.hasError(SslError.SSL_EXPIRED)
                || error.hasError(SslError.SSL_NOTYETVALID);
        if (dateError && MainActivity.clockLooksWrong()) {
            resolve(host, false);
            clockListener.onClockProblem();
            return;
        }

        boolean onlyUntrusted = error.hasError(SslError.SSL_UNTRUSTED)
                && !dateError
                && !error.hasError(SslError.SSL_IDMISMATCH)
                && !error.hasError(SslError.SSL_INVALID);
        if (onlyUntrusted && url.startsWith("https:")) {
            final String pageUrl = url;
            final byte[] seenLeaf = leafBytes(error.getCertificate());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final boolean ok = Tls.verifyServer(activity.getApplicationContext(), pageUrl, seenLeaf);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (ok) resolve(host, true);
                            else ask(host);
                        }
                    });
                }
            }, "verify-" + host).start();
        } else {
            ask(host);
        }
    }

    private void ask(final String host) {
        if (activity.isFinishing()) {
            resolve(host, false);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.ssl_title)
                .setMessage(activity.getString(R.string.ssl_msg, host))
                .setPositiveButton(R.string.ssl_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        resolve(host, true);
                    }
                })
                .setNegativeButton(R.string.ssl_back, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        resolve(host, false);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        resolve(host, false);
                    }
                })
                .show();
    }

    private void resolve(String host, boolean proceed) {
        if (proceed) allowedHosts.add(host);
        List<SslErrorHandler> waiting = pending.remove(host);
        if (waiting == null) return;
        for (SslErrorHandler h : waiting) {
            if (proceed) h.proceed();
            else h.cancel();
        }
    }

    /** DER bytes of the certificate the WebView saw, when the platform exposes them. */
    private static byte[] leafBytes(SslCertificate cert) {
        if (cert == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                X509Certificate x = cert.getX509Certificate();
                return x == null ? null : x.getEncoded();
            }
            Bundle b = SslCertificate.saveState(cert);
            return b == null ? null : b.getByteArray("x509-certificate");
        } catch (Exception e) {
            return null;
        }
    }
}
