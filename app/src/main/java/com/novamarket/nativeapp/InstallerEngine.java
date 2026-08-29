package com.novamarket.nativeapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;

public final class InstallerEngine {

    private InstallerEngine() {
        // Utility class
    }

    /**
     * Checks whether this app is allowed to request installation
     * of packages from unknown sources.
     */
    public static boolean canInstallPackages(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }

        return context.getPackageManager()
                .canRequestPackageInstalls();
    }

    /**
     * Opens Android settings so the user can allow
     * installation from this app.
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
     * Starts the Android package installer for an APK file.
     *
     * The APK must be exposed through a valid FileProvider
     * when running on Android 7.0+.
     */
    public static void installApk(Context context, Uri apkUri) {
        if (apkUri == null) {
            throw new IllegalArgumentException("APK URI cannot be null");
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
     * Convenience method for installing an APK from a File.
     *
     * This method is intended for APK files that are already
     * accessible through the app's FileProvider.
     */
    public static Uri fileUri(File apkFile) {
        if (apkFile == null) {
            throw new IllegalArgumentException("APK file cannot be null");
        }

        if (!apkFile.exists()) {
            throw new IllegalArgumentException(
                    "APK file does not exist: " + apkFile.getAbsolutePath()
            );
        }

        return Uri.fromFile(apkFile);
    }
}
