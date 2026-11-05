package com.example.tvlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * 前台守护服务：保持进程存活，并在“未通过验证 + 屏幕亮着 + 本应用不在前台”时，
 * 每 3 秒把锁屏 MainActivity 重新拉到前台——实现“关掉锁屏也会被弹回来”的硬门禁。
 * 同时承载一条常驻通知（便于家长知晓应用正在守护）。
 */
public class LockService extends Service {

    private static final String CHANNEL_ID = "tvlock_channel";
    private static final String PREFS = "tvlock";
    private static final String KEY_PASSED_UNTIL = "passedUntil";
    private Handler handler;
    private Runnable ticker;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("电视锁 · 答题门禁")
                .setContentText("守护中：未答题无法进入主屏")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
        startForeground(1, n);

        handler = new Handler(Looper.getMainLooper());
        ticker = new Runnable() {
            @Override
            public void run() {
                checkAndRelock();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(ticker, 3000);
    }

    private void checkAndRelock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null || !pm.isInteractive()) return;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        long passedUntil = sp.getLong(KEY_PASSED_UNTIL, 0);
        boolean unlocked = System.currentTimeMillis() < passedUntil;
        if (!unlocked && !MainActivity.isForeground) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("fromGate", true);
            startActivity(i);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "电视锁", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("答题门禁后台守护");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (handler != null && ticker != null) handler.removeCallbacks(ticker);
        super.onDestroy();
    }
}
