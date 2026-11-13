package io.github.ahmedps520.carbrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SslHelper.ClockProblemListener {

    /** Anything before 2025-01-01 means the head unit's clock was reset. */
    private static final long MIN_SANE_TIME = 1735689600000L;

    private static final int REQ_LOCATION = 2;

    private static final int[] TEXT_ZOOMS = {75, 90, 100, 115, 130, 150, 175, 200};

    private static final int M_BOOKMARK = 1;
    private static final int M_BOOKMARKS = 2;
    private static final int M_DESKTOP = 3;
    private static final int M_TEXT = 4;
    private static final int M_ENGINE = 5;
    private static final int M_DOWNLOADS = 6;
    private static final int M_APPS = 7;
    private static final int M_CLEAR = 8;
    private static final int M_HELP = 9;
    private static final int M_EXIT = 10;
    private static final int M_FLOATING = 11;

    private WebView web;
    private EditText urlBar;
    private ProgressBar progress;
    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnReload;
    private ImageButton btnMenu;
    private FrameLayout fullscreenHolder;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private Prefs prefs;
    private SslHelper ssl;
    private Downloader downloader;

    private boolean pageLoading;
    private boolean clockDialogShown;
    private String mobileUserAgent;
    private String desktopUserAgent;

    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    static boolean clockLooksWrong() {
        return System.currentTimeMillis() < MIN_SANE_TIME;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < 21) {
            //noinspection deprecation
            CookieSyncManager.createInstance(this);
        }
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        ssl = new SslHelper(this, this);
        downloader = new Downloader(this);

        web = findViewById(R.id.web);
        urlBar = findViewById(R.id.url);
        progress = findViewById(R.id.progress);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnReload = findViewById(R.id.btn_reload);
        btnMenu = findViewById(R.id.btn_menu);
        fullscreenHolder = findViewById(R.id.fullscreen);

        setUpToolbar();
        setUpWebView();

        // Load the certificate list in the background so the first check is quick.
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Tls.socketFactory(app);
                } catch (IOException ignored) {
                    // reported again when actually used
                }
            }
        }, "tls-init").start();

        boolean restored = savedInstanceState != null && web.restoreState(savedInstanceState) != null;
        if (!restored) {
            String url = urlFromIntent(getIntent());
            if (url != null) web.loadUrl(url);
            else loadHome();
        }
        if (clockLooksWrong()) onClockProblem();
        if (!prefs.floatingButtonAsked()) askFloatingButton();
    }

    // ---------------------------------------------------------------------------------------
    // Setup

    private void setUpToolbar() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web.canGoBack()) web.goBack();
            }
        });
        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web.canGoForward()) web.goForward();
            }
        });
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pageLoading) web.stopLoading();
                else web.reload();
            }
        });
        findViewById(R.id.btn_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadHome();
            }
        });
        findViewById(R.id.btn_apps).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openApps();
            }
        });
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        urlBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
                if (enter && event.getAction() != KeyEvent.ACTION_DOWN) return true;
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                    go(urlBar.getText().toString());
                    return true;
                }
                return false;
            }
        });
        urlBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    hideKeyboard();
                    showUrl(web.getUrl());
                }
            }
        });
        updateNavButtons();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        //noinspection deprecation
        s.setDatabaseEnabled(true);
        if (Build.VERSION.SDK_INT < 19) {
            //noinspection deprecation
            s.setDatabasePath(getDir("webdb", MODE_PRIVATE).getPath());
        }
        s.setAllowFileAccess(true); // the home page is a local file
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false); // target="_blank" opens in the same view
        s.setJavaScriptCanOpenWindowsAutomatically(false); // block pop-ups
        s.setGeolocationEnabled(true);
        s.setTextZoom(prefs.textZoom());
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        mobileUserAgent = UserAgent.mobile(s.getUserAgentString());
        desktopUserAgent = UserAgent.desktop(mobileUserAgent);
        applyUserAgent();

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cookies.setAcceptThirdPartyCookies(web, true);

        web.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                // The page doesn't change for a download; put the bar back to the current page.
                pageLoading = false;
                progress.setVisibility(View.INVISIBLE);
                showUrl(web.getUrl());
                updateNavButtons();
                downloader.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });
    }

    private void applyUserAgent() {
        web.getSettings().setUserAgentString(prefs.desktop() ? desktopUserAgent : mobileUserAgent);
    }

    // ---------------------------------------------------------------------------------------
    // Navigation

    private void go(String text) {
        String url = Search.toUrl(text, prefs.engine());
        if (url == null) return;
        hideKeyboard();
        urlBar.clearFocus();
        web.requestFocus();
        web.loadUrl(url);
    }

    private void loadHome() {
        try {
            HomePage.write(this, prefs.bookmarks(), prefs.engine());
        } catch (IOException e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
        }
        web.loadUrl(HomePage.url(this));
    }

    private void refreshHomeIfShown() {
        if (HomePage.isHome(this, web.getUrl())) loadHome();
    }

    private void showUrl(String url) {
        if (urlBar.hasFocus()) return;
        if (url == null || HomePage.isHome(this, url) || url.equals("about:blank")) urlBar.setText("");
        else urlBar.setText(url);
    }

    private void updateNavButtons() {
        setEnabled(btnBack, web.canGoBack());
        setEnabled(btnForward, web.canGoForward());
        btnReload.setImageResource(pageLoading ? R.drawable.ic_stop : R.drawable.ic_reload);
        btnReload.setContentDescription(getString(pageLoading ? R.string.stop : R.string.reload));
    }

    private static void setEnabled(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.35f);
    }

    private String urlFromIntent(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            if (scheme == null) return null;
            scheme = scheme.toLowerCase(Locale.US);
            if (scheme.equals("http") || scheme.equals("https") || scheme.equals("file")
                    || scheme.equals("content")) {
                return intent.getDataString();
            }
        } else if (Intent.ACTION_WEB_SEARCH.equals(action) || Intent.ACTION_SEARCH.equals(action)) {
            return Search.toUrl(intent.getStringExtra("query"), prefs.engine());
        }
        return null;
    }

    /** Returns true if the URL was handled here and the WebView must not load it. */
    private boolean handleSpecialUrl(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase(Locale.US);
        switch (scheme) {
            case "http":
            case "https":
            case "file":
            case "about":
            case "data":
            case "javascript":
            case "blob":
            case "content":
                return false;
            case "carbrowser":
                handleInternal(uri);
                return true;
            case "intent":
                openIntentUri(url);
                return true;
            default:
                openExternal(new Intent(Intent.ACTION_VIEW, uri));
                return true;
        }
    }

    /** Links used by the home page. */
    private void handleInternal(Uri uri) {
        String what = uri.getHost() == null ? "" : uri.getHost();
        switch (what) {
            case "go":
                go(uri.getQueryParameter("q"));
                break;
            case "apps":
                openApps();
                break;
            case "bookmarks":
                showBookmarks();
                break;
            case "help":
                showHelp();
                break;
            default:
                break;
        }
    }

    /** intent:// links (used by e.g. Google Maps). Use the app if installed, else the web fallback. */
    private void openIntentUri(String url) {
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            if (getPackageManager().resolveActivity(intent, 0) != null) {
                startActivity(intent);
                return;
            }
            String fallback = intent.getStringExtra("browser_fallback_url");
            if (fallback != null && (fallback.startsWith("http://") || fallback.startsWith("https://"))) {
                web.loadUrl(fallback);
                return;
            }
        } catch (URISyntaxException | ActivityNotFoundException | SecurityException ignored) {
            // fall through to the message
        }
        Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
    }

    private void openExternal(Intent intent) {
        try {
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void openApps() {
        startActivity(new Intent(this, AppsActivity.class));
    }

    // ---------------------------------------------------------------------------------------
    // Menu & dialogs

    private void showMenu() {
        PopupMenu popup = new PopupMenu(this, btnMenu);
        Menu m = popup.getMenu();
        final String url = web.getUrl();
        boolean onWebPage = url != null && (url.startsWith("http://") || url.startsWith("https://"));
        if (onWebPage) {
            m.add(0, M_BOOKMARK, 0, prefs.isBookmarked(url) ? R.string.menu_unbookmark : R.string.menu_bookmark);
        }
        m.add(0, M_BOOKMARKS, 0, R.string.menu_bookmarks);
        m.add(0, M_DESKTOP, 0, R.string.menu_desktop).setCheckable(true).setChecked(prefs.desktop());
        m.add(0, M_TEXT, 0, R.string.menu_text_size);
        m.add(0, M_ENGINE, 0, R.string.menu_search_engine);
        m.add(0, M_DOWNLOADS, 0, R.string.menu_downloads);
        m.add(0, M_APPS, 0, R.string.menu_apps);
        m.add(0, M_CLEAR, 0, R.string.menu_clear);
        m.add(0, M_FLOATING, 0, R.string.menu_floating).setCheckable(true).setChecked(prefs.floatingButton());
        m.add(0, M_HELP, 0, R.string.menu_help);
        m.add(0, M_EXIT, 0, R.string.menu_exit);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case M_BOOKMARK:
                        toggleBookmark(url);
                        return true;
                    case M_BOOKMARKS:
                        showBookmarks();
                        return true;
                    case M_DESKTOP:
                        prefs.setDesktop(!prefs.desktop());
                        applyUserAgent();
                        web.reload();
                        return true;
                    case M_TEXT:
                        showTextSize();
                        return true;
                    case M_ENGINE:
                        showSearchEngine();
                        return true;
                    case M_DOWNLOADS:
                        openDownloads();
                        return true;
                    case M_APPS:
                        openApps();
                        return true;
                    case M_CLEAR:
                        confirmClear();
                        return true;
                    case M_FLOATING:
                        setFloatingButton(!prefs.floatingButton());
                        return true;
                    case M_HELP:
                        showHelp();
                        return true;
                    case M_EXIT:
                        finish();
                        return true;
                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    private void toggleBookmark(String url) {
        if (url == null) return;
        if (prefs.isBookmarked(url)) {
            prefs.removeBookmark(url);
            Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show();
        } else {
            String title = web.getTitle();
            if (title == null || title.trim().isEmpty()) title = Uri.parse(url).getHost();
            prefs.addBookmark(title, url);
            Toast.makeText(this, R.string.bookmark_added, Toast.LENGTH_SHORT).show();
        }
    }

    private void showBookmarks() {
        final List<Prefs.Bookmark> list = prefs.bookmarks();
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.bookmarks_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            Prefs.Bookmark b = list.get(i);
            names[i] = b.title.isEmpty() ? b.url : b.title;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.bookmarks_title)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        web.loadUrl(list.get(which).url);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .create();
        dialog.show();
        dialog.getListView().setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                final Prefs.Bookmark b = list.get(pos);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(getString(R.string.bookmark_remove_q, b.title))
                        .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                prefs.removeBookmark(b.url);
                                dialog.dismiss();
                                refreshHomeIfShown();
                                showBookmarks();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            }
        });
    }

    private void showTextSize() {
        String[] labels = new String[TEXT_ZOOMS.length];
        int checked = 2;
        for (int i = 0; i < TEXT_ZOOMS.length; i++) {
            labels[i] = TEXT_ZOOMS[i] + "%";
            if (TEXT_ZOOMS[i] == prefs.textZoom()) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.text_size_title)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        prefs.setTextZoom(TEXT_ZOOMS[which]);
                        web.getSettings().setTextZoom(TEXT_ZOOMS[which]);
                        d.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSearchEngine() {
        int checked = 0;
        for (int i = 0; i < Search.KEYS.length; i++) {
            if (Search.KEYS[i].equals(prefs.engine())) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.search_engine_title)
                .setSingleChoiceItems(Search.NAMES, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        prefs.setEngine(Search.KEYS[which]);
                        d.dismiss();
                        refreshHomeIfShown();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openDownloads() {
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.clear_q)
                .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        clearBrowsingData();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private void clearBrowsingData() {
        web.clearCache(true);
        web.clearFormData();
        web.clearHistory();
        CookieManager cm = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= 21) {
            cm.removeAllCookies(null);
            cm.flush();
        } else {
            cm.removeAllCookie();
        }
        WebStorage.getInstance().deleteAllData();
        GeolocationPermissions.getInstance().clearAll();
        ssl.clear();
        loadHome();
        Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show();
    }

    private void showHelp() {
        String version = "?";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // can't happen for our own package
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(getString(R.string.help_text, version))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /** Asked once: car launchers often hide new apps, and this button is a way back in. */
    private void askFloatingButton() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.floating_title)
                .setMessage(R.string.floating_msg)
                .setPositiveButton(R.string.floating_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        setFloatingButton(true);
                    }
                })
                .setNegativeButton(R.string.floating_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        setFloatingButton(false);
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void setFloatingButton(boolean on) {
        prefs.setFloatingButton(on);
        if (on && !FloatingButtonService.canDrawOverlays(this)) {
            // Android 6+: the user has to allow "draw over other apps" first.
            // The button starts in onResume once they come back.
            Toast.makeText(this, R.string.floating_permission, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        FloatingButtonService.sync(this);
        if (on) Toast.makeText(this, R.string.floating_on, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onClockProblem() {
        if (clockDialogShown || isFinishing()) return;
        clockDialogShown = true;
        String now = DateFormat.getDateFormat(this).format(new Date());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.clock_title)
                .setMessage(getString(R.string.clock_msg, now))
                .setPositiveButton(R.string.clock_fix, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(MainActivity.this, R.string.not_available, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.later, null)
                .create();
        // Builder.setOnDismissListener needs Android 4.2; Dialog's works everywhere.
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                clockDialogShown = false;
            }
        });
        dialog.show();
    }

    private void askGeolocation(final String origin, final GeolocationPermissions.Callback callback) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_title)
                .setMessage(getString(R.string.location_msg, origin))
                .setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (Build.VERSION.SDK_INT >= 23
                                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED) {
                            pendingGeoOrigin = origin;
                            pendingGeoCallback = callback;
                            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                        } else {
                            callback.invoke(origin, true, true);
                        }
                    }
                })
                .setNegativeButton(R.string.block, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        callback.invoke(origin, false, false);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        callback.invoke(origin, false, false);
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_LOCATION && pendingGeoCallback != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, granted);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    // ---------------------------------------------------------------------------------------
    // Full-screen video

    private void enterVideoFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        fullscreenHolder.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenHolder.setVisibility(View.VISIBLE);
        setSystemBarsHidden(true);
    }

    private void exitVideoFullscreen() {
        if (customView == null) return;
        setSystemBarsHidden(false);
        fullscreenHolder.removeView(customView);
        fullscreenHolder.setVisibility(View.GONE);
        customView = null;
        WebChromeClient.CustomViewCallback cb = customViewCallback;
        customViewCallback = null;
        if (cb != null) cb.onCustomViewHidden();
    }

    private void setSystemBarsHidden(boolean hidden) {
        Window w = getWindow();
        View decor = w.getDecorView();
        if (hidden) {
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
            if (Build.VERSION.SDK_INT >= 19) flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decor.setSystemUiVisibility(flags);
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lifecycle

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = urlFromIntent(intent);
        if (url != null) web.loadUrl(url);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onResume() {
        super.onResume();
        FloatingButtonService.setAppInForeground(true);
        FloatingButtonService.sync(this);
        if (Build.VERSION.SDK_INT < 21) CookieSyncManager.getInstance().startSync();
    }

    // The WebView is deliberately not paused when the app goes to the background,
    // so music or a video's sound keeps playing while you use the car's other apps.
    @Override
    @SuppressWarnings("deprecation")
    protected void onPause() {
        super.onPause();
        FloatingButtonService.setAppInForeground(false);
        flushCookies();
        if (Build.VERSION.SDK_INT < 21) CookieSyncManager.getInstance().stopSync();
    }

    @SuppressWarnings("deprecation")
    private static void flushCookies() {
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().flush();
        else CookieSyncManager.getInstance().sync();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (customView != null) {
            exitVideoFullscreen();
        } else if (urlBar.hasFocus()) {
            urlBar.clearFocus();
            web.requestFocus();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        downloader.release();
        exitVideoFullscreen();
        web.stopLoading();
        web.setWebChromeClient(null);
        ViewGroup parent = (ViewGroup) web.getParent();
        if (parent != null) parent.removeView(web);
        web.destroy();
        super.onDestroy();
    }

    // ---------------------------------------------------------------------------------------
    // WebView callbacks

    private class Client extends WebViewClient {
        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleSpecialUrl(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            pageLoading = true;
            showUrl(url);
            updateNavButtons();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            pageLoading = false;
            progress.setVisibility(View.INVISIBLE);
            showUrl(url);
            updateNavButtons();
            flushCookies();
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            showUrl(url);
            updateNavButtons();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            ssl.handle(handler, error);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT) {
                Toast.makeText(MainActivity.this, R.string.no_internet, Toast.LENGTH_LONG).show();
            }
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.INVISIBLE);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            enterVideoFullscreen(view, callback);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
            enterVideoFullscreen(view, callback);
        }

        @Override
        public void onHideCustomView() {
            exitVideoFullscreen();
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            // Some WebView versions crash if this returns null.
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            askGeolocation(origin, callback);
        }
    }
}
