package com.novamarket.nativeapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives DownloadManager completion and opens Android's installer. */
public class InstallReceiver extends BroadcastReceiver {
    private static long expectedId = -1L;

    public static void register(Context context, long id) { expectedId = id; }

    @Override public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (id <= 0 || id != expectedId) return;
        try { InstallerEngine.openDownloadedPackage(context, id); }
        catch (Exception ignored) { }
    }
}
