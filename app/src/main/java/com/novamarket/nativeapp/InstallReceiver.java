package com.novamarket.nativeapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class InstallReceiver extends BroadcastReceiver {

    private static final String TAG = "NovaMarketInstall";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            return;
        }

        String action = intent.getAction();

        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {

            String packageName = null;

            if (intent.getData() != null) {
                packageName = intent.getData().getSchemeSpecificPart();
            }

            Log.d(
                    TAG,
                    "Package installed: " +
                            (packageName != null ? packageName : "unknown")
            );

        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {

            String packageName = null;

            if (intent.getData() != null) {
                packageName = intent.getData().getSchemeSpecificPart();
            }

            Log.d(
                    TAG,
                    "Package removed: " +
                            (packageName != null ? packageName : "unknown")
            );
        }
    }
}
