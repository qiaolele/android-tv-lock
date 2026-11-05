package com.example.tvlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机 / 快速开机广播：设备启动后拉起锁屏。
 *
 * - 若本应用已设为默认桌面（HOME），系统会直接把它当作 home 启动，无需此处；
 * - 若未设为桌面，此处尝试拉起锁屏，但解锁后真正“稳定弹出门禁”由
 *   LockAccessibilityService 接管（无障碍服务可在后台启动 Activity）。
 */
public class LockReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals("android.intent.action.QUICKBOOT_POWERON")) {
            try {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("fromGate", true);
                context.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }
}
