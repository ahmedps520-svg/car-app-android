package io.github.ahmedps520.carbrowser;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helps switch off preinstalled apps the car doesn't need, to free memory.
 *
 * Android only lets the system (root / ADB) disable apps, so each row opens Android's own
 * App info page, where the user presses "Disable". Apps that look car- or system-critical
 * (see {@link Bloat}) are hidden so they can't be switched off by mistake, and anything
 * switched off is listed at the bottom so it can be turned back on.
 */
public class CleanupActivity extends Activity {

    private static final class Row {
        final String pkg;
        final CharSequence label;
        final Drawable icon;

        Row(String pkg, CharSequence label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private LinearLayout list;
    private TextView memory;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.bg));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(getResources().getColor(R.color.toolbar));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription(getString(R.string.back));
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            back.setBackgroundResource(tv.resourceId);
        }
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.tool_button),
                getResources().getDimensionPixelSize(R.dimen.toolbar_height)));
        TextView title = text(20, R.color.text);
        title.setText(R.string.cleanup_title);
        title.setPadding(AppGrid.dp(this, 8), 0, 0, 0);
        bar.addView(title);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = AppGrid.dp(this, 16);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        memory = text(18, R.color.text);
        col.addView(memory);
        TextView intro = text(15, R.color.text_dim);
        intro.setText(R.string.cleanup_intro);
        intro.setPadding(0, AppGrid.dp(this, 8), 0, AppGrid.dp(this, 8));
        col.addView(intro);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showMemory();
        reload(); // the user may have just switched something off or on
    }

    private void showMemory() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        memory.setText(getString(R.string.cleanup_memory,
                Formatter.formatShortFileSize(this, mi.availMem),
                Formatter.formatShortFileSize(this, mi.totalMem)));
    }

    private void reload() {
        if (loading) return;
        loading = true;
        if (list.getChildCount() == 0) {
            TextView t = text(15, R.color.text_dim);
            t.setText(R.string.loading);
            list.addView(t);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Row> safe = new ArrayList<>();
                final List<Row> other = new ArrayList<>();
                final List<Row> off = new ArrayList<>();
                final int[] kept = {0};
                classifyAll(safe, other, off, kept);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (isFinishing()) return;
                        show(safe, other, off, kept[0]);
                    }
                });
            }
        }, "cleanup-scan").start();
    }

    private void classifyAll(List<Row> safe, List<Row> other, List<Row> off, int[] kept) {
        PackageManager pm = getPackageManager();
        Set<String> homes = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            homes.add(ri.activityInfo.packageName);
        }
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(PackageManager.GET_DISABLED_COMPONENTS);
        } catch (RuntimeException e) {
            return;
        }
        for (ApplicationInfo ai : apps) {
            if (!ai.enabled) {
                off.add(new Row(ai.packageName, ai.loadLabel(pm), ai.loadIcon(pm)));
                continue;
            }
            Bloat.Kind kind = Bloat.classify(ai.packageName,
                    ai.uid < Process.FIRST_APPLICATION_UID,
                    (ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0,
                    homes.contains(ai.packageName),
                    ai.packageName.equals(getPackageName()));
            if (kind == Bloat.Kind.KEEP) {
                kept[0]++;
                continue;
            }
            Row row = new Row(ai.packageName, ai.loadLabel(pm), ai.loadIcon(pm));
            if (kind == Bloat.Kind.SAFE) safe.add(row);
            else other.add(row);
        }
        sort(safe);
        sort(other);
        sort(off);
    }

    private void show(List<Row> safe, List<Row> other, List<Row> off, int kept) {
        list.removeAllViews();
        section(getString(R.string.cleanup_safe, safe.size()));
        if (safe.isEmpty()) note(getString(R.string.cleanup_none));
        for (Row r : safe) list.addView(row(r, 0));

        section(getString(R.string.cleanup_other, other.size()));
        note(getString(R.string.cleanup_other_hint));
        for (Row r : other) list.addView(row(r, 1));

        if (!off.isEmpty()) {
            section(getString(R.string.cleanup_off, off.size()));
            note(getString(R.string.cleanup_off_hint));
            for (Row r : off) list.addView(row(r, 2));
        }
        note(getString(R.string.cleanup_kept, kept));
    }

    /** @param group 0 = safe, 1 = unknown (asks first), 2 = switched off (turn back on) */
    private View row(final Row r, final int group) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = AppGrid.dp(this, 10);
        row.setPadding(pad, pad, pad, pad);
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            row.setBackgroundResource(tv.resourceId);
        }
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(r.icon);
        row.addView(icon, new LinearLayout.LayoutParams(AppGrid.dp(this, 44), AppGrid.dp(this, 44)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(AppGrid.dp(this, 14), 0, 0, 0);
        TextView name = text(18, R.color.text);
        name.setText(r.label);
        texts.addView(name);
        TextView pkg = text(12, R.color.text_dim);
        pkg.setText(r.pkg);
        texts.addView(pkg);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (group == 1) {
                    confirmUnknown(r);
                } else {
                    open(r, group == 2 ? R.string.cleanup_toast_enable : R.string.cleanup_toast_disable);
                }
            }
        });
        return row;
    }

    private void confirmUnknown(final Row r) {
        new AlertDialog.Builder(this)
                .setTitle(r.label)
                .setMessage(R.string.cleanup_unknown_warning)
                .setPositiveButton(R.string.cleanup_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        open(r, R.string.cleanup_toast_disable);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void open(Row r, int toast) {
        Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
        AppGrid.openAppInfo(this, r.pkg);
    }

    private void section(String s) {
        TextView t = text(15, R.color.text_dim);
        t.setAllCaps(true);
        t.setText(s);
        t.setPadding(0, AppGrid.dp(this, 18), 0, AppGrid.dp(this, 4));
        list.addView(t);
    }

    private void note(String s) {
        TextView t = text(14, R.color.text_dim);
        t.setText(s);
        t.setPadding(0, AppGrid.dp(this, 4), 0, AppGrid.dp(this, 6));
        list.addView(t);
    }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getResources().getColor(color));
        return t;
    }

    private static void sort(List<Row> rows) {
        final Collator collator = Collator.getInstance();
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return collator.compare(a.label.toString(), b.label.toString());
            }
        });
    }
}
