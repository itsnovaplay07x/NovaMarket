package com.novamarket.nativeapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class InstallerEngine {

    private static final String TRUSTED_HOST = "novamarket-15r.pages.dev";

    private InstallerEngine() {
        // Utility class
    }

    /**
     * Allows APK installation from the trusted NovaMarket website only.
     */
    public static boolean isAllowedNovaMarketUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }

        try {
            URL url = new URL(urlString);

            String protocol = url.getProtocol();
            String host = url.getHost();

            if (!"https".equalsIgnoreCase(protocol)) {
                return false;
            }

            return TRUSTED_HOST.equalsIgnoreCase(host);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Starts the NovaMarket APK installation process.
     */
    public static void start(
            Activity activity,
            String packageUrl,
            String name,
            String version
    ) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity cannot be null");
        }

        if (!isAllowedNovaMarketUrl(packageUrl)) {
            throw new SecurityException(
                    "Install source is not a trusted NovaMarket URL."
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !canInstallPackages(activity)) {

            openInstallPermission(activity);

            throw new IllegalStateException(
                    "Please allow NovaMarket to install apps, then try again."
            );
        }

        downloadAndInstall(activity, packageUrl, name, version);
    }

    /**
     * Checks whether Android allows this app to request APK installation.
     */
    public static boolean canInstallPackages(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }

        return context.getPackageManager()
                .canRequestPackageInstalls();
    }

    /**
     * Opens Android's unknown-app installation permission screen.
     */
    public static void openInstallPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName())
            );

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * Downloads the APK and opens Android's package installer.
     */
    private static void downloadAndInstall(
            Activity activity,
            String packageUrl,
            String name,
            String version
    ) {

        new Thread(() -> {

            File apkFile = null;

            try {
                URL url = new URL(packageUrl);

                HttpURLConnection connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);

                int responseCode = connection.getResponseCode();

                // Do not follow redirects to an untrusted domain.
                if (responseCode >= 300 && responseCode < 400) {
                    String location = connection.getHeaderField("Location");

                    if (!isAllowedNovaMarketUrl(location)) {
                        throw new SecurityException(
                                "APK redirect destination is not trusted."
                        );
                    }

                    connection.disconnect();

                    url = new URL(location);

                    connection =
                            (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setInstanceFollowRedirects(false);

                    responseCode = connection.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception(
                            "APK download failed. HTTP " + responseCode
                    );
                }

                String safeName = name == null || name.trim().isEmpty()
                        ? "NovaMarket-App"
                        : name.trim();

                safeName = safeName.replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );

                if (!safeName.toLowerCase().endsWith(".apk")) {
                    safeName += ".apk";
                }

                File downloadDir =
                        new File(activity.getCacheDir(), "novamarket");

                if (!downloadDir.exists()
                        && !downloadDir.mkdirs()) {

                    throw new Exception(
                            "Unable to create download directory."
                    );
                }

                apkFile = new File(downloadDir, safeName);

                try (InputStream input =
                             connection.getInputStream();
                     FileOutputStream output =
                             new FileOutputStream(apkFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }

                    output.flush();
                }

                connection.disconnect();

                if (!apkFile.exists() || apkFile.length() == 0) {
                    throw new Exception("Downloaded APK is empty.");
                }

                File finalApkFile = apkFile;

                activity.runOnUiThread(() ->
                        installApk(activity, finalApkFile)
                );

            } catch (Exception e) {

                if (apkFile != null && apkFile.exists()) {
                    // Remove incomplete APK.
                    //noinspection ResultOfMethodCallIgnored
                    apkFile.delete();
                }

                activity.runOnUiThread(() -> {
                    throw new RuntimeException(
                            "NovaMarket installation failed: "
                                    + (e.getMessage() == null
                                    ? "Unknown error"
                                    : e.getMessage())
                    );
                });
            }

        }).start();
    }

    /**
     * Opens Android's native package installer.
     */
    private static void installApk(
            Context context,
            File apkFile
    ) {

        Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                apkFile
        );

        Intent intent = new Intent(Intent.ACTION_VIEW);

        intent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
        );

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(intent);
    }

    /**
     * Legacy helper retained for compatibility.
     */
    public static void installApk(
            Context context,
            Uri apkUri
    ) {

        if (apkUri == null) {
            throw new IllegalArgumentException(
                    "APK URI cannot be null"
            );
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);

        intent.setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
        );

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(intent);
    }

    /**
     * Convenience method for FileProvider-based APK URI.
     */
    public static Uri fileUri(
            Context context,
            File apkFile
    ) {

        if (apkFile == null) {
            throw new IllegalArgumentException(
                    "APK file cannot be null"
            );
        }

        if (!apkFile.exists()) {
            throw new IllegalArgumentException(
                    "APK file does not exist: "
                            + apkFile.getAbsolutePath()
            );
        }

        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                apkFile
        );
    }
                   }
