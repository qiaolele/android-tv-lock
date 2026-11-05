package com.example.tvlock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * 全屏加载本地网页 index.html（电视锁）。
 * 作为 HOME 桌面角色注册，设为默认桌面后开机/按主页键即进入答题锁屏。
 * 通过 AndroidBridge 暴露原生能力给网页：拉起电视 App、倒计时结束弹回前台。
 */
public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏、无状态栏
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        // 允许本地文件访问（assets 内网页）
        ws.setAllowFileAccess(true);

        // 暴露原生桥接给网页：window.AndroidBridge.launchApp(pkg, name) 等
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");

        setContentView(webView);
    }

    /**
     * 原生桥接：网页通过 window.AndroidBridge 调用，实现“答题成功后拉起电视 App”。
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

        /** 把本应用（桌面）重新带到前台，用于“可看时长”倒计时结束后弹回锁屏。 */
        @JavascriptInterface
        public void bringToFront() {
            Intent i = new Intent(MainActivity.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    /**
     * 拦截返回键，防止误退出（防沉迷）。
     * 家长退出方式：长按主页键切回原桌面，或用 adb uninstall com.example.tvlock。
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
