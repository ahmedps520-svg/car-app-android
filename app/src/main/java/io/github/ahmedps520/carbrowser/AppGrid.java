package io.github.ahmedps520.carbrowser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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

import java.util.List;

/** Big icon tiles in rows, sized for a car screen. Shared by the home, apps and cleanup screens. */
final class AppGrid {

    static final class Item {
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

    private AppGrid() {
    }

    static int columns(Activity a) {
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        return Math.max(3, Math.min(8, widthDp / 120));
    }

    static void fill(Activity a, LinearLayout grid, List<Item> items, int columns) {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            row.addView(cell(a, items.get(i)),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        // Pad the last row so its cells keep the same width.
        if (row != null) {
            int rest = (columns - items.size() % columns) % columns;
            for (int i = 0; i < rest; i++) {
                row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private static View cell(Activity a, final Item item) {
        LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(a, 8);
        cell.setPadding(pad, dp(a, 12), pad, dp(a, 12));
        TypedValue tv = new TypedValue();
        if (a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            cell.setBackgroundResource(tv.resourceId);
        }

        ImageView icon = new ImageView(a);
        icon.setImageDrawable(item.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(a, 56), dp(a, 56)));

        TextView label = new TextView(a);
        label.setText(item.label);
        label.setTextColor(a.getResources().getColor(R.color.text));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, dp(a, 6), 0, 0);
        label.setShadowLayer(6f, 0f, 2f, 0xCC000000); // readable on the Car Home photo
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

    static void launch(Activity a, Intent intent) {
        try {
            a.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    static void openAppInfo(Activity a, String pkg) {
        launch(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)));
    }

    static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
