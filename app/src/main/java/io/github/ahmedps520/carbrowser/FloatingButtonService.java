package io.github.ahmedps520.carbrowser;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * A small round Car Browser button that floats above the car's own screens.
 *
 * Car launchers often hide apps they don't know. This button gives a way to open
 * Car Browser (and, through its Apps screen, every other hidden app) from anywhere.
 * Tap to open, drag to move. It hides while Car Browser itself is on screen.
 */
public class FloatingButtonService extends Service {

    private static final String TAG = "CarBrowser.Floating";
    private static final String CHANNEL = "floating_button";

    // Cleared in onDestroy, so it never outlives the running service.
    @SuppressLint("StaticFieldLeak")
    private static FloatingButtonService instance;
    private static boolean appInForeground;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private ImageView bubble;
    private boolean attached;

    /**
     * Starts or stops the button to match the user's setting. Safe to call often.
     * (Lint wrongly reads canDrawOverlays() as "SDK < 23" and flags the 26+ branch.)
     */
    @SuppressLint("ObsoleteSdkInt")
    static void sync(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, FloatingButtonService.class);
        boolean wanted = new Prefs(app).floatingButton() && canDrawOverlays(app);
        try {
            if (!wanted) {
                app.stopService(intent);
            } else if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (RuntimeException e) {
            // Newer Android may refuse to start services from the background.
            Log.w(TAG, "Could not start the floating button", e);
        }
    }

    static boolean canDrawOverlays(Context context) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context);
    }

    static void setAppInForeground(boolean foreground) {
        appInForeground = foreground;
        if (instance != null) instance.updateAttached();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (Build.VERSION.SDK_INT >= 26) startForeground(1, notification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createBubble();
        updateAttached();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        detach();
        instance = null;
        super.onDestroy();
    }

    private void createBubble() {
        int size = dp(58);
        bubble = new ImageView(this);
        bubble.setImageResource(R.drawable.ic_launcher);
        bubble.setAlpha(0.9f);
        bubble.setContentDescription(getString(R.string.app_name));

        @SuppressWarnings("deprecation")
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;

        Prefs prefs = new Prefs(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        params.x = prefs.bubbleX() >= 0 ? prefs.bubbleX() : dm.widthPixels - size - dp(12);
        params.y = prefs.bubbleY() >= 0 ? prefs.bubbleY() : dm.heightPixels / 3;
        clamp();

        bubble.setOnTouchListener(new DragListener());
    }

    private void updateAttached() {
        if (bubble == null) return;
        if (appInForeground) detach();
        else attach();
    }

    private void attach() {
        if (attached) return;
        try {
            clamp(); // the screen may have rotated
            windowManager.addView(bubble, params);
            attached = true;
        } catch (RuntimeException e) {
            // No overlay permission, or the system refused the window.
            Log.w(TAG, "Could not show the floating button", e);
            stopSelf();
        }
    }

    private void detach() {
        if (!attached) return;
        try {
            windowManager.removeView(bubble);
        } catch (RuntimeException ignored) {
            // already gone
        }
        attached = false;
    }

    private void clamp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        params.x = Math.max(0, Math.min(params.x, dm.widthPixels - params.width));
        params.y = Math.max(0, Math.min(params.y, dm.heightPixels - params.height));
    }

    private void openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    /** Only needed (and only called) on Android 8+, where a floating button needs a foreground service. */
    @TargetApi(26)
    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                getString(R.string.floating_channel), NotificationManager.IMPORTANCE_MIN);
        nm.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_globe)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.floating_notification))
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class DragListener implements View.OnTouchListener {
        private final int slop = ViewConfiguration.get(FloatingButtonService.this).getScaledTouchSlop();
        private float downX;
        private float downY;
        private int startX;
        private int startY;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = params.x;
                    startY = params.y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (!moved && Math.abs(dx) + Math.abs(dy) > slop) moved = true;
                    if (moved && attached) {
                        params.x = startX + Math.round(dx);
                        params.y = startY + Math.round(dy);
                        clamp();
                        windowManager.updateViewLayout(bubble, params);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) new Prefs(FloatingButtonService.this).setBubblePosition(params.x, params.y);
                    else openApp();
                    return true;
                default:
                    return true;
            }
        }
    }
}
