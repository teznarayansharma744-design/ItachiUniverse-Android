package com.itachi.universe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

/** Keeps the app alive while videos download, and shows one progress notification. Stops itself when done. */
public class DownloadService extends Service {
    private static final String CHANNEL = "downloads";
    private static final int NOTE = 71;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            int n = Downloads.activeCount();
            if (n == 0) {
                stopForeground(true);
                stopSelf();
                return;
            }
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTE, build(n));
            h.postDelayed(this, 1000);
        }
    };

    private Notification build(int n) {
        int p = Downloads.averageProgress();
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(n == 1 ? "Downloading lecture" : "Downloading " + n + " lectures")
                .setContentText(p + "%")
                .setProgress(100, p, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(android.app.PendingIntent.getActivity(this, 0, new Intent(this, DownloadsActivity.class),
                        android.app.PendingIntent.FLAG_IMMUTABLE))
                .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(new NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW));
        }
        Notification note = build(Math.max(1, Downloads.activeCount()));
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTE, note);
        h.removeCallbacks(tick);
        h.postDelayed(tick, 1000);
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() { h.removeCallbacks(tick); super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
