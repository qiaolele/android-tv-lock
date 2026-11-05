package com.example.tvlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * 监听开机完成 / 快速开机 / 亮屏 / 用户解锁：
 * - 开机、亮屏：确保后台守护服务（LockService）存活；
 * - 用户解锁（USER_PRESENT）：若尚未通过答题验证，立即把锁屏拉到前台。
 *
 * 注意：USER_PRESENT / SCREEN_ON / BOOT_COMPLETED 均为系统豁免的隐式广播，
 * 可在 Manifest 中静态注册；在 Android 10+ 上“后台启动 Activity”需要本应用拥有
 * 悬浮窗（SYSTEM_ALERT_WINDOW）权限，MainActivity 首次运行会引导授予。
 */
public class LockReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        // 开机 / 快速开机 / 亮屏：拉起守护服务
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals("android.intent.action.QUICKBOOT_POWERON")
                || action.equals(Intent.ACTION_SCREEN_ON)) {
            try {
                Intent s = new Intent(context, LockService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(s);
                else context.startService(s);
            } catch (Exception ignored) {
            }
        }

        // 用户解锁：若未通过验证，立即弹出锁屏
        if (action.equals(Intent.ACTION_USER_PRESENT)) {
            SharedPreferences sp = context.getSharedPreferences("tvlock", Context.MODE_PRIVATE);
            long passedUntil = sp.getLong("passedUntil", 0);
            boolean unlocked = System.currentTimeMillis() < passedUntil;
            if (!unlocked) {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("fromGate", true);
                context.startActivity(i);
            }
        }
    }
}
