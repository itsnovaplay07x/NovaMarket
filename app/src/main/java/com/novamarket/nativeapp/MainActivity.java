package com.novamarket.nativeapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

/**
 * NovaMarket Android client.
 *
 * The web store remains the UI. Install/update requests are bridged to
 * Android so the package is downloaded with DownloadManager and handed to
 * Android's own package installer when the download completes.
 */
public class MainActivity extends Activity {
    private static final String STORE_URL = "https://novamarket-15r.pages.dev/";
    private static final String API_ORIGIN = "https://novamarket-api.parshuram843121.workers.dev";
    WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);

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
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setBackgroundColor(android.graphics.Color.rgb(247, 245, 252));
        webView.setWebViewClient(new NovaWebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NovaMarketBridge(this), "NovaMarketNative");
        webView.loadUrl(STORE_URL);
    }

    private void injectNativeInstallBridge() {
        String script = "javascript:(function(){"
                + "if(window.__novaNativeBridgeInstalled)return;window.__novaNativeBridgeInstalled=true;"
                + "const API='" + API_ORIGIN + "';"
                + "document.addEventListener('click',function(e){"
                + " let el=e.target&&e.target.closest?e.target.closest('a,button,[role=button]'):null;if(!el)return;"
                + " let text=(el.innerText||el.textContent||'').trim().toLowerCase();"
                + " if(!/^(install|update|install now|update now)$/.test(text))return;"
                + " let id=el.getAttribute('data-app-id')||el.dataset&&el.dataset.appId||'';"
                + " if(!id){let n=el;for(let i=0;i<6&&n;i++,n=n.parentElement){let h=n.getAttribute&&n.getAttribute('href');let q=h&&h.match(/\\/apps\\/(\\d+)/);if(q){id=q[1];break}}}"
                + " if(id&&/^\\d+$/.test(id)&&window.NovaMarketNative&&window.NovaMarketNative.startInstall){e.preventDefault();NovaMarketNative.startInstall(API+'/apps/'+id+'/install');}"
                + "},true);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private final class NovaWebViewClient extends WebViewClient {
        @Override public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            injectNativeInstallBridge();
        }

        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl());
        }

        @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(Uri.parse(url));
        }

        private boolean handleUrl(Uri uri) {
            if (uri == null) return false;
            String value = uri.toString();
            if ("novamarket-native".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                if (path.matches("/install/\\d+")) {
                    getBridge().startInstall(API_ORIGIN + path.replace("/install/", "/apps/") + "/install");
                }
                return true;
            }
            if (InstallerEngine.isAllowedNovaMarketManifestUrl(value)) {
                getBridge().startInstall(value);
                return true;
            }
            if (InstallerEngine.isAllowedNovaMarketUrl(value)) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("^/apps/(\\d+)/download$")
                        .matcher(path);
                if (matcher.find()) {
                    getBridge().startInstall(API_ORIGIN + "/apps/" + matcher.group(1) + "/install");
                } else {
                    getBridge().startDirectDownload(value, "NovaMarket app", "");
                }
                return true;
            }
            return false;
        }
    }

    private NovaMarketBridge getBridge() {
        return new NovaMarketBridge(this);
    }

    void notifyInstallFailure(String message) {
        String js = "window.novaMarketInstallResult&&window.novaMarketInstallResult({status:\"failed\",error:"
                + JSONObject.quote(message == null ? "Installation failed." : message) + "});";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    public static class NovaMarketBridge {
        private final Activity activity;
        NovaMarketBridge(Activity activity) { this.activity = activity; }

        @JavascriptInterface
        public void startInstall(String manifestUrl) {
            try {
                if (!InstallerEngine.isAllowedNovaMarketManifestUrl(manifestUrl)) {
                    notifyWeb("failed", "Install source is not trusted.");
                    return;
                }
                InstallerEngine.startFromManifest(activity, manifestUrl);
            } catch (Exception e) {
                notifyWeb("failed", message(e, "Unable to start installation."));
            }
        }

        @JavascriptInterface
        public void startDirectDownload(String packageUrl, String name, String version) {
            try {
                if (!InstallerEngine.isAllowedNovaMarketUrl(packageUrl)) {
                    notifyWeb("failed", "Package source is not trusted.");
                    return;
                }
                InstallerEngine.start(activity, packageUrl, name, version);
            } catch (Exception e) {
                notifyWeb("failed", message(e, "Unable to start download."));
            }
        }

        @JavascriptInterface
        public void installApp(String json) {
            try {
                JSONObject data = new JSONObject(json);
                String manifestUrl = data.optString("manifestUrl", "");
                if (!manifestUrl.isEmpty()) {
                    startInstall(manifestUrl);
                    return;
                }
                String packageUrl = data.optString("packageUrl", "");
                String name = data.optString("name", "NovaMarket app");
                String version = data.optString("version", "");
                startDirectDownload(packageUrl, name, version);
            } catch (Exception e) {
                notifyWeb("failed", message(e, "Invalid install request."));
            }
        }

        private void notifyWeb(String status, String message) {
            String js = "window.novaMarketInstallResult&&window.novaMarketInstallResult({status:"
                    + JSONObject.quote(status) + ",error:" + JSONObject.quote(message) + "});";
            activity.runOnUiThread(() -> {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).webView.evaluateJavascript(js, null);
                }
            });
        }

        private static String message(Exception e, String fallback) {
            return e.getMessage() == null || e.getMessage().trim().isEmpty() ? fallback : e.getMessage();
        }
    }
}
