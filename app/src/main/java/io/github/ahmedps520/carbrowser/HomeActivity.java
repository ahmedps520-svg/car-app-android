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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
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

    private final Handler handler = new Handler();
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

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getResources().getColor(R.color.bg));
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

        setContentView(scroll);
    }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getResources().getColor(color));
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
