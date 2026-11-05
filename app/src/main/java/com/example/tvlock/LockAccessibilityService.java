package com.example.tvlock;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * 门禁守护（无障碍服务）：这是“解锁手机/电视后自动弹出答题锁屏”的可靠机制。
 *
 * 为什么不用“悬浮窗 + 后台轮询”：
 *   Android 10+ 禁止普通应用在后台启动 Activity，所以那种方案根本弹不出真正的锁屏，
 *   只会留下一条通知/悬浮提示（也就是上一版被吐槽“只是多了个悬浮窗”的原因）。
 *   而无障碍服务被系统允许在后台启动 Activity，且不需要悬浮窗权限、不显示常驻通知。
 *
 * 行为：
 *   - 监听窗口切换 / 亮屏事件，并在解锁后周期性兜底检查；
 *   - 当设备已解锁、且“验证未通过（未答题/已过期）”、且当前前台不是本锁屏时，
 *     把全屏锁屏 MainActivity 拉到最前 —— 实现“关掉也弹回来”的硬门禁。
 */
public class LockAccessibilityService extends AccessibilityService {

    public static boolean isEnabled = false;
    private static final String PREFS = "tvlock";
    private static final String KEY_PASSED_UNTIL = "passedUntil";
    private static final long CHECK_INTERVAL = 2000;

    private Handler handler;
    private Runnable ticker;
    private String topPackage = "";

    @Override
    public void onServiceConnected() {
        isEnabled = true;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_SCREEN_ON
                | AccessibilityEvent.TYPE_SCREEN_OFF;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        handler = new Handler(Looper.getMainLooper());
        ticker = new Runnable() {
            @Override
            public void run() {
                checkAndLock();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.postDelayed(ticker, CHECK_INTERVAL);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() != null) {
            topPackage = event.getPackageName().toString();
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_SCREEN_ON
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 解锁 / 窗口切换后立即检查一次（去抖）
            if (handler != null && ticker != null) {
                handler.removeCallbacks(ticker);
                handler.postDelayed(ticker, 300);
            }
        }
    }

    private boolean isUnlocked() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        long until = sp.getLong(KEY_PASSED_UNTIL, 0);
        return System.currentTimeMillis() < until;
    }

    private void checkAndLock() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) return; // 锁屏状态不弹，避免冲突
            if (isUnlocked()) return;                         // 已通过验证 → 放行
            if (topPackage.equals(getPackageName())) return;  // 锁屏自身在前台 → 跳过

            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("fromGate", true);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        isEnabled = false;
        if (handler != null && ticker != null) handler.removeCallbacks(ticker);
        super.onDestroy();
    }
}
