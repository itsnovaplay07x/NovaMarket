package com.novamarket.nativeapp;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import androidx.core.content.FileProvider;
import java.io.File;

/** Handles package delivery and hands the package to Android. */
public final class InstallerEngine {
    private static long activeDownloadId = -1L;

    private InstallerEngine() {}

    public static boolean isAllowedNovaMarketUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                   "novamarket-api.parshuram843121.workers.dev".equalsIgnoreCase(uri.getHost()) &&
                   uri.getPath() != null && uri.getPath().matches("/apps/[0-9]+/download");
        } catch (Exception e) { return false; }
    }

    public static void start(Context context, String url, String name, String version) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        String safe = safeName(name) + (version.isEmpty() ? "" : "-" + safeName(version)) + ".apk";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(name);
        request.setDescription("Preparing " + name + " for installation");
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safe);
        activeDownloadId = dm.enqueue(request);
        InstallReceiver.register(context, activeDownloadId);
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
            Uri content = FileProvider.getUriForFile(context, "com.novamarket.nativeapp.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(content, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private static String safeName(String value) {
        String s = value == null ? "NovaMarketApp" : value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return s.isEmpty() ? "NovaMarketApp" : s.substring(0, Math.min(60, s.length()));
    }
}
