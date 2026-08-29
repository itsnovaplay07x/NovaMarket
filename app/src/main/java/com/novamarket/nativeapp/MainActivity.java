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
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
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

        // Android 15+ enforces edge-to-edge for targetSdk 35.
        // Keep the web content visually inside the status/navigation bars
        // by applying the real system-bar insets to the WebView.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        WindowCompat.getInsetsController(getWindow(), webView)
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), webView)
                .setAppearanceLightNavigationBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );

            // Do not let the web UI sit underneath the phone's
            // status icons or gesture/navigation area.
            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
            );

            return insets;
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setBackgroundColor(android.graphics.Color.rgb(247, 245, 252));
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
