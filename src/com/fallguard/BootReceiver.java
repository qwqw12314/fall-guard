package com.fallguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
            || Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
            restart(context);
        }
    }

    static void restart(Context context) {
        Intent i = new Intent(context, FallService.class);
        i.setAction(FallService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }
}
