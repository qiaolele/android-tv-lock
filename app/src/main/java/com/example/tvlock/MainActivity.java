package com.example.tvlock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * 全屏加载本地网页 index.html（电视锁）。
 *
 * 两条“自动开门禁”的可靠路线（二选一即可，也可同时用）：
 *   1) 设为默认桌面（HOME 角色）——电视/盒子开机、按主页键即进锁屏，最稳；
 *   2) 开启无障碍（LockAccessibilityService）——手机/电视解锁后自动弹出锁屏，无需替换桌面、无需悬浮窗。
 *
 * 通过 AndroidBridge 暴露原生能力给网页：拉起 App、回真实主屏、记录/清除验证状态。
 */
public class MainActivity extends Activity {

    private WebView webView;
    public static boolean isForeground = false;
    private static final String PREFS = "tvlock";
    private static final String KEY_PASSED_UNTIL = "passedUntil";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 若本次是被门禁拉起、且验证仍在有效期，直接放行（不展示锁屏）
        if (getIntent().getBooleanExtra("fromGate", false) && isUnlocked()) {
            finish();
            return;
        }

        // 允许锁屏显示在其他应用之上（解锁后弹出门禁时，能盖住当前界面）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        hideSystemUI();

        webView = new WebView(this);
        // 关键：必须显式设为 MATCH_PARENT，否则 WebView 在某些设备上不是全屏，
        // 页面里 height:100% 会塌缩，导致内容被裁切（兼容性问题的根因之一）。
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setAllowFileAccess(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);

        // 引导开启“无障碍”权限（解锁自动弹锁屏的可靠机制，无需悬浮窗）
        requestAccessibility();
    }

    private boolean isUnlocked() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        long passedUntil = sp.getLong(KEY_PASSED_UNTIL, 0);
        return System.currentTimeMillis() < passedUntil;
    }

    private void requestAccessibility() {
        if (!LockAccessibilityService.isEnabled) {
            new AlertDialog.Builder(this)
                    .setTitle("开启一项权限")
                    .setMessage("为实现「解锁手机/电视后自动弹出答题锁屏」，请在「设置 → 无障碍」里开启「电视锁」。开启后无需悬浮窗，体验更干净。")
                    .setPositiveButton("去设置", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    })
                    .setNegativeButton("稍后", null)
                    .show();
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

        /** 回到系统真实主屏（排除本应用自身，避免循环回到锁屏）。 */
        @JavascriptInterface
        public void launchHome() {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            PackageManager pm = getPackageManager();
            java.util.List<ResolveInfo> homes = pm.queryIntentActivities(home, 0);
            for (ResolveInfo ri : homes) {
                String pkg = ri.activityInfo.packageName;
                if (!pkg.equals(getPackageName())) {
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_HOME);
                    i.setPackage(pkg);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                    return;
                }
            }
            // 没有找到其它桌面：退回系统默认 home
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            finish();
        }

        /** 记录“已通过验证”的截止时间（分钟）。minutes<=0 表示不限时。 */
        @JavascriptInterface
        public void setUnlocked(float minutes) {
            long until = (minutes <= 0) ? Long.MAX_VALUE
                    : System.currentTimeMillis() + (long) (minutes * 60_000L);
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
     * 家长退出方式：长按主页键切回原桌面 / 在设置里关闭无障碍，或用 adb uninstall。
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
