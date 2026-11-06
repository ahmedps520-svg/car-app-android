package io.github.ahmedps520.carbrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lists every launchable app (car launchers often hide some, e.g. the stock browser,
 * file manager or Settings), apps that were disabled, and shortcuts deep into
 * Android's Settings, such as "Unknown sources" and Developer options.
 */
public class AppsActivity extends Activity {

    private static final class Item {
        final CharSequence label;
        final Drawable icon;
        final Runnable onClick;
        final Runnable onLongClick;

        Item(CharSequence label, Drawable icon, Runnable onClick, Runnable onLongClick) {
            this.label = label;
            this.icon = icon;
            this.onClick = onClick;
            this.onLongClick = onLongClick;
        }
    }

    private int columns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        columns = Math.max(3, Math.min(8, widthDp / 120));

        fillGrid((LinearLayout) findViewById(R.id.settings_grid), settingsShortcuts());

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Item> apps = loadLaunchableApps();
                final List<Item> disabled = loadDisabledApps();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        ((TextView) findViewById(R.id.apps_hint)).setText(R.string.apps_hint);
                        fillGrid((LinearLayout) findViewById(R.id.apps_grid), apps);
                        if (!disabled.isEmpty()) {
                            findViewById(R.id.disabled_title).setVisibility(View.VISIBLE);
                            findViewById(R.id.disabled_hint).setVisibility(View.VISIBLE);
                            fillGrid((LinearLayout) findViewById(R.id.disabled_grid), disabled);
                        }
                    }
                });
            }
        }, "load-apps").start();
    }

    // ---------------------------------------------------------------------------------------

    private List<Item> settingsShortcuts() {
        Drawable icon;
        try {
            icon = getPackageManager().getApplicationIcon("com.android.settings");
        } catch (PackageManager.NameNotFoundException e) {
            icon = getResources().getDrawable(R.drawable.ic_settings);
        }
        List<Item> list = new ArrayList<>();
        addSetting(list, icon, R.string.s_all, new Intent(Settings.ACTION_SETTINGS));
        addSetting(list, icon, R.string.s_wifi, new Intent(Settings.ACTION_WIFI_SETTINGS));
        addSetting(list, icon, R.string.s_security, new Intent(Settings.ACTION_SECURITY_SETTINGS));
        addSetting(list, icon, R.string.s_developer, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        addSetting(list, icon, R.string.s_apps, new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));
        addSetting(list, icon, R.string.s_date, new Intent(Settings.ACTION_DATE_SETTINGS));
        addSetting(list, icon, R.string.s_language, new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        addSetting(list, icon, R.string.s_display, new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        addSetting(list, icon, R.string.s_sound, new Intent(Settings.ACTION_SOUND_SETTINGS));
        addSetting(list, icon, R.string.s_bluetooth, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        addSetting(list, icon, R.string.s_storage, new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        addSetting(list, icon, R.string.s_about, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        addSetting(list, icon, R.string.s_downloads, new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        return list;
    }

    private void addSetting(List<Item> list, Drawable icon, int label, final Intent intent) {
        list.add(new Item(getString(label), icon, new Runnable() {
            @Override
            public void run() {
                launch(intent);
            }
        }, null));
    }

    private List<Item> loadLaunchableApps() {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        List<Item> items = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            final String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            final ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            items.add(new Item(ri.loadLabel(pm), ri.loadIcon(pm), new Runnable() {
                @Override
                public void run() {
                    launch(new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(cn)
                            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                }
            }, new Runnable() {
                @Override
                public void run() {
                    openAppInfo(pkg);
                }
            }));
        }
        sort(items);
        return items;
    }

    private List<Item> loadDisabledApps() {
        PackageManager pm = getPackageManager();
        List<Item> items = new ArrayList<>();
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(PackageManager.GET_DISABLED_COMPONENTS);
        } catch (RuntimeException e) {
            return items; // very large package lists can overflow the binder on old Android
        }
        for (ApplicationInfo ai : apps) {
            if (ai.enabled) continue;
            final String pkg = ai.packageName;
            Runnable info = new Runnable() {
                @Override
                public void run() {
                    openAppInfo(pkg);
                }
            };
            items.add(new Item(ai.loadLabel(pm), ai.loadIcon(pm), info, info));
        }
        sort(items);
        return items;
    }

    private static void sort(List<Item> items) {
        final Collator collator = Collator.getInstance();
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return collator.compare(a.label.toString(), b.label.toString());
            }
        });
    }

    private void openAppInfo(String pkg) {
        launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)));
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------------------------------

    private void fillGrid(LinearLayout grid, List<Item> items) {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            row.addView(cell(items.get(i)), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        // Pad the last row so its cells keep the same width.
        if (row != null) {
            int rest = (columns - items.size() % columns) % columns;
            for (int i = 0; i < rest; i++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private View cell(final Item item) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(8);
        cell.setPadding(pad, dp(12), pad, dp(12));
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            cell.setBackgroundResource(tv.resourceId);
        }

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(item.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView label = new TextView(this);
        label.setText(item.label);
        label.setTextColor(getResources().getColor(R.color.text));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, dp(6), 0, 0);
        cell.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                item.onClick.run();
            }
        });
        if (item.onLongClick != null) {
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    item.onLongClick.run();
                    return true;
                }
            });
        }
        return cell;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
