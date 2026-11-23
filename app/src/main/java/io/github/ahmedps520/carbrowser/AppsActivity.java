package io.github.ahmedps520.carbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
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

    private int columns;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        prefs = new Prefs(this);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        columns = AppGrid.columns(this);
        AppGrid.fill(this, (LinearLayout) findViewById(R.id.settings_grid), settingsShortcuts(), columns);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppGrid.Item> apps = loadLaunchableApps();
                final List<AppGrid.Item> disabled = loadDisabledApps();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        ((TextView) findViewById(R.id.apps_hint)).setText(R.string.apps_hint);
                        AppGrid.fill(AppsActivity.this, (LinearLayout) findViewById(R.id.apps_grid), apps, columns);
                        if (!disabled.isEmpty()) {
                            findViewById(R.id.disabled_title).setVisibility(View.VISIBLE);
                            findViewById(R.id.disabled_hint).setVisibility(View.VISIBLE);
                            AppGrid.fill(AppsActivity.this, (LinearLayout) findViewById(R.id.disabled_grid),
                                    disabled, columns);
                        }
                    }
                });
            }
        }, "load-apps").start();
    }

    // ---------------------------------------------------------------------------------------

    private List<AppGrid.Item> settingsShortcuts() {
        Drawable icon;
        try {
            icon = getPackageManager().getApplicationIcon("com.android.settings");
        } catch (PackageManager.NameNotFoundException e) {
            icon = getResources().getDrawable(R.drawable.ic_settings);
        }
        List<AppGrid.Item> list = new ArrayList<>();
        list.add(new AppGrid.Item(getString(R.string.cleanup_title), getResources().getDrawable(R.drawable.ic_launcher),
                new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(AppsActivity.this, CleanupActivity.class));
                    }
                }, null));
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

    private void addSetting(List<AppGrid.Item> list, Drawable icon, int label, final Intent intent) {
        list.add(new AppGrid.Item(getString(label), icon, new Runnable() {
            @Override
            public void run() {
                AppGrid.launch(AppsActivity.this, intent);
            }
        }, null));
    }

    private List<AppGrid.Item> loadLaunchableApps() {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        List<AppGrid.Item> items = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            final String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            final ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
            final CharSequence label = ri.loadLabel(pm);
            items.add(new AppGrid.Item(label, ri.loadIcon(pm), new Runnable() {
                @Override
                public void run() {
                    AppGrid.launch(AppsActivity.this, launchIntent(cn));
                }
            }, new Runnable() {
                @Override
                public void run() {
                    showAppOptions(cn, label);
                }
            }));
        }
        sort(items);
        return items;
    }

    static Intent launchIntent(ComponentName cn) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    private void showAppOptions(final ComponentName cn, CharSequence label) {
        final boolean pinned = prefs.isPinned(cn.flattenToString());
        String[] options = {
                getString(pinned ? R.string.home_unpin : R.string.home_pin),
                getString(R.string.app_info)
        };
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            if (pinned) prefs.unpin(cn.flattenToString());
                            else prefs.pin(cn.flattenToString());
                            Toast.makeText(AppsActivity.this,
                                    pinned ? R.string.home_unpinned : R.string.home_pinned, Toast.LENGTH_SHORT).show();
                        } else {
                            AppGrid.openAppInfo(AppsActivity.this, cn.getPackageName());
                        }
                    }
                })
                .show();
    }

    private List<AppGrid.Item> loadDisabledApps() {
        PackageManager pm = getPackageManager();
        List<AppGrid.Item> items = new ArrayList<>();
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
                    AppGrid.openAppInfo(AppsActivity.this, pkg);
                }
            };
            items.add(new AppGrid.Item(ai.loadLabel(pm), ai.loadIcon(pm), info, info));
        }
        sort(items);
        return items;
    }

    static void sort(List<AppGrid.Item> items) {
        final Collator collator = Collator.getInstance();
        Collections.sort(items, new Comparator<AppGrid.Item>() {
            @Override
            public int compare(AppGrid.Item a, AppGrid.Item b) {
                return collator.compare(a.label.toString(), b.label.toString());
            }
        });
    }
}
