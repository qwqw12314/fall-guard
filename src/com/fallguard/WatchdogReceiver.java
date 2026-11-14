package com.fallguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WatchdogReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (FallService.ACTION_WATCHDOG.equals(intent.getAction())) {
            // 幂等：服务还在跑就什么都不做，被杀了就拉回来
            BootReceiver.restart(context);
        }
    }
}
