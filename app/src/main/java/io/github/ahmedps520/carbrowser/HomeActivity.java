package io.github.ahmedps520.carbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * "Car Home": an optional, light home screen. When chosen as the car's home app it's what
 * Android shows at start-up and whenever Home is pressed, so it can't be hidden by the car's
 * own launcher. It only shows what you pin, plus Car Browser, All apps and Clean up.
 *
 * It is switched on and off from Car Browser's menu ({@link #setEnabled}), which turns the
 * HOME entry ({@code .HomeAlias}) on or off. The car's own home screen is always one tap
 * away via the "Car's home screen" tile.
 */
public class HomeActivity extends Activity {

    private static final String ALIAS = ".HomeAlias";

    /** Intro plays once per app start, and again after the car has been asleep. */
    private static boolean introShownThisProcess;
    private static boolean sdCheckDone;
    private static long stoppedElapsed = -1;
    private static long stoppedUptime = -1;
    private static final long SLEEP_FOR_INTRO_MS = 60 * 1000;

    private final Handler handler = new Handler();
    private View intro;
    private ImageView introLogo;
    private TextView introTitle;
    private TextView introGreeting;
    private TextView clock;
    private TextView date;
    private LinearLayout grid;
    private Prefs prefs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clock.setText(DateFormat.getTimeFormat(HomeActivity.this).format(now));
            date.setText(DateFormat.getLongDateFormat(HomeActivity.this).format(now));
            handler.postDelayed(this, 10000);
        }
    };

    // ---- switching Car Home on/off ----

    static ComponentName alias(Context c) {
        return new ComponentName(c.getPackageName(), c.getPackageName() + ALIAS);
    }

    static boolean isEnabled(Context c) {
        return c.getPackageManager().getComponentEnabledSetting(alias(c))
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    static void setEnabled(Context c, boolean on) {
        c.getPackageManager().setComponentEnabledSetting(alias(c),
                on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** The car's own home screen (any HOME app that isn't us), or null. */
    static ComponentName originalHome(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(home, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ai.packageName.equals(c.getPackageName())) continue;
            if (ai.packageName.equals("com.android.settings")) continue; // 4.4's fallback picker
            return new ComponentName(ai.packageName, ai.name);
        }
        return null;
    }

    // ---- screen ----

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        int pad = AppGrid.dp(this, 20);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getResources().getColor(R.color.bg));
        ImageView wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setImageBitmap(loadWallpaper());
        root.addView(wallpaper, matchParent());
        // Darker toward the bottom so white text stays readable on the photo.
        View shade = new View(this);
        shade.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x55000000, 0x99000000}));
        root.addView(shade, matchParent());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);

        clock = text(56, R.color.text);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(clock);
        date = text(18, R.color.text_dim);
        date.setGravity(Gravity.CENTER_HORIZONTAL);
        date.setPadding(0, 0, 0, AppGrid.dp(this, 20));
        col.addView(date);

        final EditText search = new EditText(this);
        search.setBackgroundResource(R.drawable.url_bg);
        search.setHint(R.string.url_hint);
        search.setHintTextColor(getResources().getColor(R.color.text_dim));
        search.setTextColor(getResources().getColor(R.color.text));
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        search.setSingleLine(true);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        search.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        int sp = AppGrid.dp(this, 18);
        search.setPadding(sp, AppGrid.dp(this, 14), sp, AppGrid.dp(this, 14));
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) return true;
                String q = search.getText().toString().trim();
                if (q.isEmpty()) return true;
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.setText("");
                startActivity(new Intent(Intent.ACTION_WEB_SEARCH)
                        .setClass(HomeActivity.this, MainActivity.class)
                        .putExtra("query", q));
                return true;
            }
        });
        col.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, AppGrid.dp(this, 16), 0, 0);
        col.addView(grid);

        TextView hint = text(13, R.color.text_dim);
        hint.setText(R.string.home_hint);
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        hint.setPadding(0, AppGrid.dp(this, 24), 0, 0);
        col.addView(hint);

        root.addView(scroll, matchParent());
        intro = buildIntro();
        root.addView(intro, matchParent());
        setContentView(root);
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    /** The photo, decoded at 16-bit colour to keep memory low on old head units. */
    private Bitmap loadWallpaper() {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        o.inScaled = false;
        try {
            return BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper, o);
        } catch (OutOfMemoryError e) {
            return null; // plain dark background instead
        }
    }

    // ---- intro ----

    private View buildIntro() {
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(0xE60B0D12);
        box.setClickable(true); // swallow taps; a tap skips the intro
        box.setVisibility(View.GONE);
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideIntro();
            }
        });
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        introLogo = new ImageView(this);
        introLogo.setImageResource(R.drawable.ic_launcher);
        col.addView(introLogo, new LinearLayout.LayoutParams(AppGrid.dp(this, 128), AppGrid.dp(this, 128)));
        introTitle = text(40, R.color.text);
        introTitle.setText(R.string.car_home_name);
        introTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        introTitle.setPadding(0, AppGrid.dp(this, 20), 0, 0);
        col.addView(introTitle);
        introGreeting = text(22, R.color.text_dim);
        introGreeting.setGravity(Gravity.CENTER_HORIZONTAL);
        introGreeting.setPadding(0, AppGrid.dp(this, 10), 0, 0);
        col.addView(introGreeting);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        box.addView(col, lp);
        return box;
    }

    private void showIntro() {
        introShownThisProcess = true;
        handler.removeCallbacks(hideIntroLater);
        intro.animate().cancel();
        intro.setAlpha(1f);
        intro.setVisibility(View.VISIBLE);

        introGreeting.setText(greeting());
        introLogo.setAlpha(0f);
        introLogo.setScaleX(0.6f);
        introLogo.setScaleY(0.6f);
        introTitle.setAlpha(0f);
        introTitle.setTranslationY(AppGrid.dp(this, 18));
        introGreeting.setAlpha(0f);

        introLogo.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(700)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
        introTitle.animate().alpha(1f).translationY(0).setStartDelay(350).setDuration(600)
                .setInterpolator(new DecelerateInterpolator()).start();
        introGreeting.animate().alpha(1f).setStartDelay(800).setDuration(600).start();
        handler.postDelayed(hideIntroLater, 2900);
    }

    private final Runnable hideIntroLater = new Runnable() {
        @Override
        public void run() {
            hideIntro();
        }
    };

    private void hideIntro() {
        handler.removeCallbacks(hideIntroLater);
        if (intro.getVisibility() != View.VISIBLE) return;
        intro.animate().cancel();
        intro.animate().alpha(0f).setStartDelay(0).setDuration(500).withEndAction(new Runnable() {
            @Override
            public void run() {
                intro.setVisibility(View.GONE);
            }
        }).start();
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour >= 5 && hour < 12) res = R.string.intro_morning;
        else if (hour >= 12 && hour < 17) res = R.string.intro_afternoon;
        else if (hour >= 17 && hour < 22) res = R.string.intro_evening;
        else res = R.string.intro_night;
        return getString(res);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Car head units "sleep" when the car is off. Deep sleep shows up as a gap between
        // elapsed time (counts sleep) and uptime (doesn't), so replay the intro after one.
        boolean slept = false;
        if (stoppedElapsed >= 0) {
            long elapsed = SystemClock.elapsedRealtime() - stoppedElapsed;
            long awake = SystemClock.uptimeMillis() - stoppedUptime;
            slept = elapsed - awake > SLEEP_FOR_INTRO_MS;
        }
        if (!introShownThisProcess || slept) showIntro();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stoppedElapsed = SystemClock.elapsedRealtime();
        stoppedUptime = SystemClock.uptimeMillis();
    }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getResources().getColor(color));
        t.setShadowLayer(6f, 0f, 2f, 0xCC000000);
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        FloatingButtonService.setAppInForeground(true);
        FloatingButtonService.sync(this);
        handler.removeCallbacks(tick);
        tick.run();
        AppGrid.fill(this, grid, tiles(), AppGrid.columns(this));
        if (!sdCheckDone) {
            sdCheckDone = true;
            // After the intro, so the two don't overlap.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) warnAboutAppsOnSdCard();
                }
            }, 3600);
        }
    }

    /**
     * Apps on "SD card" storage vanish after a restart on these car screens (until that
     * storage loads, which may be never). Offer to move each one to the car's own storage.
     */
    private void warnAboutAppsOnSdCard() {
        final PackageManager pm = getPackageManager();
        final List<String> pkgs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_EXTERNAL_STORAGE) == 0) continue;
            pkgs.add(ai.packageName);
            names.add(String.valueOf(ai.loadLabel(pm)));
        }
        if (pkgs.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.sd_apps_title)
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Toast.makeText(HomeActivity.this, R.string.sd_apps_toast, Toast.LENGTH_LONG).show();
                        AppGrid.openAppInfo(HomeActivity.this, pkgs.get(which));
                    }
                })
                .setNegativeButton(R.string.later, null)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        FloatingButtonService.setAppInForeground(false);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // A home screen has nowhere to go back to.
    }

    private List<AppGrid.Item> tiles() {
        PackageManager pm = getPackageManager();
        List<AppGrid.Item> list = new ArrayList<>();
        Drawable self = getResources().getDrawable(R.drawable.ic_launcher);

        list.add(new AppGrid.Item(getString(R.string.app_name), self, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(HomeActivity.this, MainActivity.class));
            }
        }, null));

        for (final String flat : prefs.pinned()) {
            final ComponentName cn = ComponentName.unflattenFromString(flat);
            if (cn == null) continue;
            final CharSequence label;
            final Drawable icon;
            try {
                ActivityInfo ai = pm.getActivityInfo(cn, 0);
                label = ai.loadLabel(pm);
                icon = ai.loadIcon(pm);
            } catch (PackageManager.NameNotFoundException e) {
                continue; // uninstalled or switched off
            }
            list.add(new AppGrid.Item(label, icon, new Runnable() {
                @Override
                public void run() {
                    AppGrid.launch(HomeActivity.this, AppsActivity.launchIntent(cn));
                }
            }, new Runnable() {
                @Override
                public void run() {
                    confirmUnpin(flat, label);
                }
            }));
        }

        // YouTube (SmartTube) and Discord (Discofi) show up by themselves once installed.
        for (String[] pkgs : new String[][]{CarApps.YOUTUBE_PACKAGES, CarApps.DISCORD_PACKAGES}) {
            final String pkg = CarApps.installed(this, pkgs);
            if (pkg == null) continue;
            final Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null || launch.getComponent() == null
                    || prefs.isPinned(launch.getComponent().flattenToString())) continue;
            try {
                ApplicationInfoHolder info = new ApplicationInfoHolder(pm, pkg);
                list.add(new AppGrid.Item(info.label, info.icon, new Runnable() {
                    @Override
                    public void run() {
                        AppGrid.launch(HomeActivity.this, new Intent(launch));
                    }
                }, null));
            } catch (PackageManager.NameNotFoundException ignored) {
                // just uninstalled
            }
        }

        list.add(new AppGrid.Item(getString(R.string.home_all_apps), getResources().getDrawable(R.drawable.ic_apps),
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(HomeActivity.this, AppsActivity.class));
                    }
                }, null));
        list.add(new AppGrid.Item(getString(R.string.cleanup_title), getResources().getDrawable(R.drawable.ic_settings),
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(HomeActivity.this, CleanupActivity.class));
                    }
                }, null));

        final ComponentName original = originalHome(this);
        if (original != null) {
            Drawable icon;
            try {
                icon = pm.getActivityIcon(original);
            } catch (PackageManager.NameNotFoundException e) {
                icon = getResources().getDrawable(R.drawable.ic_home);
            }
            list.add(new AppGrid.Item(getString(R.string.home_car_original), icon, new Runnable() {
                @Override
                public void run() {
                    AppGrid.launch(HomeActivity.this, new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME).setComponent(original));
                }
            }, null));
        }

        Drawable settingsIcon;
        try {
            settingsIcon = pm.getApplicationIcon("com.android.settings");
        } catch (PackageManager.NameNotFoundException e) {
            settingsIcon = getResources().getDrawable(R.drawable.ic_settings);
        }
        list.add(new AppGrid.Item(getString(R.string.s_all), settingsIcon, new Runnable() {
            @Override
            public void run() {
                AppGrid.launch(HomeActivity.this, new Intent(Settings.ACTION_SETTINGS));
            }
        }, null));
        return list;
    }

    private static final class ApplicationInfoHolder {
        final CharSequence label;
        final Drawable icon;

        ApplicationInfoHolder(PackageManager pm, String pkg) throws PackageManager.NameNotFoundException {
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = ai.loadLabel(pm);
            icon = ai.loadIcon(pm);
        }
    }

    private void confirmUnpin(final String flat, CharSequence label) {
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(new String[]{getString(R.string.home_unpin), getString(R.string.app_info)},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                ComponentName cn = ComponentName.unflattenFromString(flat);
                                if (which == 0) {
                                    prefs.unpin(flat);
                                    AppGrid.fill(HomeActivity.this, grid, tiles(), AppGrid.columns(HomeActivity.this));
                                    Toast.makeText(HomeActivity.this, R.string.home_unpinned, Toast.LENGTH_SHORT).show();
                                } else if (cn != null) {
                                    AppGrid.openAppInfo(HomeActivity.this, cn.getPackageName());
                                }
                            }
                        })
                .show();
    }
}
