package com.novamarket.nativeapp;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.database.Cursor;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Native package delivery + Android package-installer handoff. */
public final class InstallerEngine {
    private static final String API_HOST = "novamarket-api.parshuram843121.workers.dev";
    private static long activeDownloadId = -1L;
    private static String activeAppName = "NovaMarket app";
    private static String activeVersion = "";

    private InstallerEngine() {}

    public static boolean isAllowedNovaMarketUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && API_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().matches("/apps/[0-9]+/download");
        } catch (Exception e) { return false; }
    }

    public static boolean isAllowedNovaMarketManifestUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && API_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().matches("/apps/[0-9]+/install");
        } catch (Exception e) { return false; }
    }

    public static void startFromManifest(final Context context, final String manifestUrl) {
        if (!canInstallPackages(context)) {
            openUnknownSourcesSettings(context);
            throw new IllegalStateException("Allow NovaMarket to install unknown apps, then tap Install again.");
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(manifestUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(true);

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IOException("Install manifest request failed (HTTP " + code + ").");
                }

                String json = readAll(connection.getInputStream());
                JSONObject root = new JSONObject(json);
                if (!root.optBoolean("ok", false)) {
                    throw new IOException(root.optString("error", "This app is not available for installation."));
                }

                JSONObject app = root.optJSONObject("app");
                JSONObject install = root.optJSONObject("install");
                if (app == null || install == null) throw new IOException("Invalid install manifest.");

                String packageUrl = install.optString("package_url", "");
                String name = app.optString("name", "NovaMarket app");
                String version = app.optString("version", "");
                if (!isAllowedNovaMarketUrl(packageUrl)) throw new IOException("Package source is not trusted.");

                final String finalPackageUrl = packageUrl;
                final String finalName = name;
                final String finalVersion = version;
                context.getMainExecutor().execute(() -> start(context, finalPackageUrl, finalName, finalVersion));
            } catch (Exception e) {
                context.getMainExecutor().execute(() -> notify(context, "failed", e.getMessage() == null ? "Unable to prepare installation." : e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "NovaMarket-Install-Manifest").start();
    }

    public static void start(Context context, String url, String name, String version) {
        if (!canInstallPackages(context)) {
            openUnknownSourcesSettings(context);
            throw new IllegalStateException("Allow NovaMarket to install unknown apps, then tap Install again.");
        }

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        String safe = safeName(name) + (version == null || version.isEmpty() ? "" : "-" + safeName(version)) + ".apk";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(name == null || name.isEmpty() ? "NovaMarket app" : name);
        request.setDescription("Downloading for installation");
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(false);
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safe);

        activeAppName = name == null || name.isEmpty() ? "NovaMarket app" : name;
        activeVersion = version == null ? "" : version;
        activeDownloadId = dm.enqueue(request);
        InstallReceiver.register(context, activeDownloadId);
        notify(context, "started", "Downloading " + activeAppName + "…");
    }

    static void openDownloadedPackage(Context context, long id) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
        try (Cursor c = dm.query(q)) {
            if (c == null || !c.moveToFirst()) throw new IllegalStateException("Installation package was not found.");
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status != DownloadManager.STATUS_SUCCESSFUL) throw new IllegalStateException("Package download failed.");
            String local = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            Uri source = Uri.parse(local);
            File file = new File(source.getPath());
            if (!file.exists()) throw new IllegalStateException("Downloaded package is missing.");

            Uri content = FileProvider.getUriForFile(context, "com.novamarket.nativeapp.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(content, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            notify(context, "installer", "Android is ready to install " + activeAppName + ".");
        }
    }

    static long getActiveDownloadId() { return activeDownloadId; }

    private static boolean canInstallPackages(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 26) return true;
        return context.getPackageManager().canRequestPackageInstalls();
    }

    private static void openUnknownSourcesSettings(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private static void notify(Context context, String status, String message) {
        if (!(context instanceof MainActivity)) return;
        String js = "window.novaMarketInstallResult&&window.novaMarketInstallResult({status:"
                + JSONObject.quote(status) + ",message:" + JSONObject.quote(message) + "});";
        ((MainActivity) context).runOnUiThread(() ->
                ((MainActivity) context).webView.evaluateJavascript(js, null));
    }

    private static String readAll(java.io.InputStream input) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toString("UTF-8");
    }

    private static String safeName(String value) {
        String s = value == null ? "NovaMarketApp" : value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return s.isEmpty() ? "NovaMarketApp" : s.substring(0, Math.min(60, s.length()));
    }
}
