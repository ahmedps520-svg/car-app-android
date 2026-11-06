package io.github.ahmedps520.carbrowser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import org.conscrypt.Conscrypt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Modern HTTPS for old Android.
 *
 * Android 4.4 ships a 2014 list of trusted root certificates, so it doesn't trust
 * Let's Encrypt (ISRG Root X1) and other newer roots. Its built-in TLS code is old too.
 * This class combines the device's roots with Mozilla's current root list
 * (res/raw/cacerts.pem) and uses Conscrypt for TLS 1.2/1.3. It is used to download
 * files and to double-check certificates the old WebView rejects.
 */
final class Tls {

    private static final String TAG = "CarBrowser.Tls";

    private static SSLSocketFactory factory;
    private static X509TrustManager trustManager;

    private Tls() {
    }

    static synchronized SSLSocketFactory socketFactory(Context context) throws IOException {
        init(context);
        return factory;
    }

    static synchronized X509TrustManager trustManager(Context context) throws IOException {
        init(context);
        return trustManager;
    }

    private static void init(Context context) throws IOException {
        if (factory != null) return;
        try {
            X509TrustManager system = defaultTrustManager(null);
            X509TrustManager bundled = defaultTrustManager(bundledRoots(context.getApplicationContext()));
            trustManager = new CompositeTrustManager(system, bundled);

            SSLContext ctx = null;
            boolean modern = false;
            try {
                if (Conscrypt.isAvailable()) {
                    ctx = SSLContext.getInstance("TLS", Conscrypt.newProvider());
                    modern = true;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Conscrypt unavailable, using the platform TLS stack", t);
            }
            if (ctx == null) ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{trustManager}, null);
            factory = modern ? ctx.getSocketFactory() : new Tls12SocketFactory(ctx.getSocketFactory());
        } catch (Exception e) {
            throw new IOException("TLS setup failed: " + e, e);
        }
    }

    private static X509TrustManager defaultTrustManager(KeyStore keyStore) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) return (X509TrustManager) tm;
        }
        throw new IllegalStateException("No X509TrustManager");
    }

    private static KeyStore bundledRoots(Context context) throws Exception {
        String pem;
        InputStream in = context.getResources().openRawResource(R.raw.cacerts);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(200 * 1024);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            pem = out.toString("US-ASCII");
        } finally {
            in.close();
        }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        String begin = "-----BEGIN CERTIFICATE-----";
        String end = "-----END CERTIFICATE-----";
        int i = 0;
        int from = 0;
        while (true) {
            int b = pem.indexOf(begin, from);
            if (b < 0) break;
            int e = pem.indexOf(end, b);
            if (e < 0) break;
            from = e + end.length();
            String block = pem.substring(b, from) + "\n";
            try {
                Certificate cert = cf.generateCertificate(
                        new ByteArrayInputStream(block.getBytes("US-ASCII")));
                ks.setCertificateEntry("ca" + (i++), cert);
            } catch (CertificateException ex) {
                Log.w(TAG, "Skipping unreadable root certificate", ex);
            }
        }
        return ks;
    }

    /**
     * Connects to the page's server with the modern TLS stack and checks that it
     * proves its identity with a certificate chain we trust, for that host name.
     *
     * @param seenLeaf DER bytes of the certificate the WebView received, or null if unknown.
     * @return true only if the certificate is valid under the updated root list.
     */
    static boolean verifyServer(Context context, String pageUrl, byte[] seenLeaf) {
        HttpsURLConnection c = null;
        try {
            URL page = new URL(pageUrl);
            if (!"https".equalsIgnoreCase(page.getProtocol())) return false;
            String host = page.getHost();
            URL origin = new URL("https", host, page.getPort(), "/");
            c = (HttpsURLConnection) origin.openConnection();
            c.setSSLSocketFactory(socketFactory(context));
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("HEAD");
            c.connect(); // TLS handshake: chain + host name are verified here

            Certificate[] served = c.getServerCertificates();
            if (served.length == 0 || !(served[0] instanceof X509Certificate)) return false;
            if (seenLeaf == null || Arrays.equals(served[0].getEncoded(), seenLeaf)) return true;

            // Servers with several certificates may hand the WebView a different one.
            // Check that one too, using the intermediates the server sent us.
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate leaf = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(seenLeaf));
            List<X509Certificate> chain = new ArrayList<>();
            chain.add(leaf);
            for (int i = 1; i < served.length; i++) chain.add((X509Certificate) served[i]);
            X509Certificate[] arr = chain.toArray(new X509Certificate[0]);
            String authType = "EC".equals(leaf.getPublicKey().getAlgorithm()) ? "ECDHE_ECDSA" : "ECDHE_RSA";
            trustManager(context).checkServerTrusted(arr, authType);
            leaf.checkValidity();
            return hostMatches(leaf, host);
        } catch (Exception e) {
            Log.i(TAG, "Could not verify " + pageUrl + ": " + e);
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static boolean hostMatches(X509Certificate cert, String host) {
        String h = host.toLowerCase(Locale.US);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        try {
            Collection<List<?>> names = cert.getSubjectAlternativeNames();
            if (names == null) return false;
            for (List<?> entry : names) {
                if (entry.size() < 2 || !Integer.valueOf(2).equals(entry.get(0))) continue; // dNSName
                String pattern = String.valueOf(entry.get(1)).toLowerCase(Locale.US);
                if (pattern.equals(h)) return true;
                if (pattern.startsWith("*.")) {
                    String suffix = pattern.substring(1); // ".example.com"
                    if (h.endsWith(suffix) && h.indexOf('.') == h.length() - suffix.length()) return true;
                }
            }
        } catch (CertificateParsingException ignored) {
            // treat as no match
        }
        return false;
    }

    /**
     * Trusts a chain if either the device or the bundled Mozilla list trusts it. Both
     * checks are done by Android's own TrustManager implementations; nothing is skipped.
     */
    @SuppressLint("CustomX509TrustManager")
    private static final class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager system;
        private final X509TrustManager bundled;

        CompositeTrustManager(X509TrustManager system, X509TrustManager bundled) {
            this.system = system;
            this.bundled = bundled;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            system.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                bundled.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = system.getAcceptedIssuers();
            X509Certificate[] b = bundled.getAcceptedIssuers();
            X509Certificate[] all = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }
    }

    /** Fallback if Conscrypt can't load: turn on TLS 1.1/1.2, which Android 4.1-4.4 support but leave off. */
    private static final class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        Tls12SocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        private Socket patch(Socket s) {
            if (s instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) s;
                List<String> wanted = new ArrayList<>();
                for (String p : ssl.getSupportedProtocols()) {
                    if (p.equals("TLSv1") || p.equals("TLSv1.1") || p.equals("TLSv1.2") || p.equals("TLSv1.3")) {
                        wanted.add(p);
                    }
                }
                if (!wanted.isEmpty()) ssl.setEnabledProtocols(wanted.toArray(new String[0]));
            }
            return s;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return patch(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return patch(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return patch(delegate.createSocket(address, port, localAddress, localPort));
        }
    }
}
