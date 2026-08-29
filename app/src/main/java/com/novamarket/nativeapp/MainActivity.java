package com.novamarket.nativeapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;
    private FrameLayout root;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Android 15/16 edge-to-edge handling
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(247, 245, 252));

        webView = new WebView(this);

        FrameLayout.LayoutParams webParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        root.addView(webView, webParams);
        setContentView(root);

        // Apply system-bar insets around the WebView.
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {

            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) webView.getLayoutParams();

            params.leftMargin = bars.left;
            params.topMargin = bars.top;
            params.rightMargin = bars.right;
            params.bottomMargin = bars.bottom;

            webView.setLayoutParams(params);

            return insets;
        });

        ViewCompat.requestApplyInsets(root);

        // System bar appearance
        WindowCompat.getInsetsController(getWindow(), root)
                .setAppearanceLightStatusBars(true);

        WindowCompat.getInsetsController(getWindow(), root)
                .setAppearanceLightNavigationBars(true);

        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // WebView settings
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        webView.setBackgroundColor(Color.rgb(247, 245, 252));
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(
                new NovaMarketBridge(this),
                "NovaMarketNative"
        );

        webView.loadUrl(
                "https://novamarket-15r.pages.dev/"
        );
    }

    public static class NovaMarketBridge {

        private final Activity activity;

        NovaMarketBridge(Activity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void installApp(String json) {

            try {

                JSONObject data = new JSONObject(json);

                String packageUrl =
                        data.optString("packageUrl", "");

                String name =
                        data.optString("name", "NovaMarket app");

                String version =
                        data.optString("version", "");

                if (!InstallerEngine.isAllowedNovaMarketUrl(packageUrl)) {

                    notifyWeb(
                            "failed",
                            "Install source is not trusted."
                    );

                    return;
                }

                InstallerEngine.start(
                        activity,
                        packageUrl,
                        name,
                        version
                );

            } catch (Exception e) {

                notifyWeb(
                        "failed",
                        e.getMessage() == null
                                ? "Invalid install request."
                                : e.getMessage()
                );
            }
        }

        private void notifyWeb(
                String status,
                String message
        ) {

            String js =
                    "window.novaMarketInstallResult && " +
                    "window.novaMarketInstallResult({" +
                    "status:" +
                    JSONObject.quote(status) +
                    ",error:" +
                    JSONObject.quote(message) +
                    "});";

            activity.runOnUiThread(() -> {

                if (activity instanceof MainActivity) {

                    ((MainActivity) activity)
                            .webView
                            .evaluateJavascript(js, null);
                }
            });
        }
    }
}
