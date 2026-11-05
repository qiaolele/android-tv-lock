package com.example.tvlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * 全屏加载本地网页 index.html（电视锁）。
 * 作为 HOME 桌面角色注册，设为默认桌面后开机/按主页键即进入答题锁屏（电视场景）。
 * 同时配合 LockReceiver + LockService，实现“解锁手机后自动弹出锁屏、答完题才能进主屏”的门禁闭环（手机场景）。
 * 通过 AndroidBridge 暴露原生能力给网页：拉起 App、回主屏、记录/清除验证状态。
 */
public class MainActivity extends Activity {

    private WebView webView;
    public static boolean isForeground = false;
    private static final String PREFS = "tvlock";
    private static final String KEY_PASSED_UNTIL = "passedUntil";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 保证后台守护服务存活（负责解锁后重新弹锁屏）
        ensureLockServiceRunning();

        // 若本次是因“解锁/开机”被网关拉起，且验证仍在有效期内，直接放行，不展示锁屏
        if (getIntent().getBooleanExtra("fromGate", false) && isUnlocked()) {
            finish();
            return;
        }

        hideSystemUI();

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setAllowFileAccess(true);

        // 暴露原生桥接给网页：window.AndroidBridge.launchApp / launchHome / setUnlocked / clearUnlock
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");

        setContentView(webView);

        // 引导授予必要权限：悬浮窗（Android 10+ 后台弹 Activity 必需）+ 通知（Android 13+）
        requestNeededPermissions();
    }

    private void ensureLockServiceRunning() {
        try {
            Intent s = new Intent(this, LockService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(s);
            else startService(s);
        } catch (Exception ignored) {
            // 个别 ROM 限制后台启动前台服务时静默失败，不影响“解锁弹锁屏”主流程（依赖悬浮窗权限）
        }
    }

    private boolean isUnlocked() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        long passedUntil = sp.getLong(KEY_PASSED_UNTIL, 0);
        return System.currentTimeMillis() < passedUntil;
    }

    private void requestNeededPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("需要一项权限")
                    .setMessage("为了让本应用在“解锁手机”后自动弹出答题锁屏，请在设置里开启「显示在其他应用上层 / 悬浮窗」权限；否则系统不允许应用在后台自动打开。")
                    .setPositiveButton("去设置", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    })
                    .setNegativeButton("稍后", null)
                    .show();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    /**
     * 原生桥接：网页通过 window.AndroidBridge 调用。
     */
    public class AndroidBridge {
        /** 按包名拉起对应电视 App；未安装则提示。 */
        @JavascriptInterface
        public void launchApp(String packageName, String appName) {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                final String msg = "未安装：" + (appName != null ? appName : packageName) + "，请先安装";
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
            }
        }

        /** 把本应用重新带到前台，用于“可看时长”倒计时结束后弹回锁屏。 */
        @JavascriptInterface
        public void bringToFront() {
            Intent i = new Intent(MainActivity.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }

        /** 回到系统主屏/桌面（HOME）。用于“系统主屏”入口与解锁后退出电视锁。 */
        @JavascriptInterface
        public void launchHome() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish(); // 退出锁屏，把真桌面让出来（手机场景）
        }

        /** 记录“已通过验证”的截止时间（分钟）。minutes<=0 表示不限时。 */
        @JavascriptInterface
        public void setUnlocked(float minutes) {
            long until = (minutes <= 0) ? Long.MAX_VALUE : System.currentTimeMillis() + (long) (minutes * 60_000L);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_PASSED_UNTIL, until).apply();
        }

        /** 清除验证状态（立即重新锁定）。 */
        @JavascriptInterface
        public void clearUnlock() {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_PASSED_UNTIL, 0).apply();
        }
    }

    /**
     * 拦截返回键，防止误退出（防沉迷）。
     * 家长退出方式：长按主页键切回原桌面 / 关闭悬浮窗权限，或用 adb uninstall com.example.tvlock。
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** 沉浸式全屏：隐藏状态栏 + 导航栏，并让内容延伸到系统栏区域之下。 */
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** 窗口重新获得焦点时（如从其他 App 返回）再次隐藏系统栏，避免遮挡回归。 */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        hideSystemUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
