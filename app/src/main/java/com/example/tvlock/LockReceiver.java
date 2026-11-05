package com.example.tvlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机 / 亮屏 / 解锁广播：设备启动、点亮屏幕或解锁后拉起锁屏。
 *
 * - 若本应用已设为默认桌面（HOME），系统会直接把它当作 home 启动，无需此处；
 * - 亮屏 / 解锁后此处尝试立即拉起锁屏，提供更及时的门禁；
 * - 若个别机型在后台不允许启动 Activity（Android 10+ 限制），仍由
 *   LockAccessibilityService 的 2 秒兜底 ticker 负责弹出，保证“关掉也弹回来”。
 */
public class LockReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals("android.intent.action.QUICKBOOT_POWERON")
                || action.equals(Intent.ACTION_SCREEN_ON)
                || action.equals(Intent.ACTION_USER_PRESENT)) {
            try {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.putExtra("fromGate", true);
                context.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }
}
