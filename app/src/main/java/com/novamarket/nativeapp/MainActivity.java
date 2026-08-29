package com.novamarket.nativeapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;

/**
 * Native NovaMarket shell.
 *
 * The web UI owns marketplace navigation. This activity exposes only
 * the installation bridge. Android's own package installer remains
 * responsible for the final install/update confirmation.
 */
public class MainActivity extends Activity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NovaMarketBridge(this), "NovaMarketNative");

        // Point this at the deployed NovaMarket Pages site.
        webView.loadUrl("https://novamarket-15r.pages.dev/");
    }

    public static class NovaMarketBridge {
        private final Activity activity;
        NovaMarketBridge(Activity activity) { this.activity = activity; }

        @JavascriptInterface
        public void installApp(String json) {
            try {
                JSONObject data = new JSONObject(json);
                String packageUrl = data.optString("packageUrl", "");
                String name = data.optString("name", "NovaMarket app");
                String version = data.optString("version", "");

                if (!InstallerEngine.isAllowedNovaMarketUrl(packageUrl)) {
                    notifyWeb("failed", "Install source is not trusted.");
                    return;
                }
                InstallerEngine.start(activity, packageUrl, name, version);
            } catch (Exception e) {
                notifyWeb("failed", e.getMessage() == null ? "Invalid install request." : e.getMessage());
            }
        }

        private void notifyWeb(String status, String message) {
            String js = "window.novaMarketInstallResult && window.novaMarketInstallResult({status:" +
                    JSONObject.quote(status) + ",error:" + JSONObject.quote(message) + "});";
            activity.runOnUiThread(() -> {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).webView.evaluateJavascript(js, null);
                }
            });
        }
    }
}
