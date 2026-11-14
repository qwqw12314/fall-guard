package com.fallguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    TextView logView;
    TextView statusView;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.main);
        logView = (TextView) findViewById(R.id.log);
        statusView = (TextView) findViewById(R.id.status);
        ((Button) findViewById(R.id.start)).setOnClickListener(v -> startService());
        ((Button) findViewById(R.id.stop)).setOnClickListener(v -> stopService());
        ((Button) findViewById(R.id.refresh)).setOnClickListener(v -> refreshLog());
        refreshLog();
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshLog();
    }

    void startService() {
        if (Build.VERSION.SDK_INT >= 33
            && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
               != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        askIgnoreBattery();
        Intent i = new Intent(this, FallService.class);
        i.setAction(FallService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        refreshLog();
    }

    // 保活关键一步：申请忽略电池优化，系统才不轻易杀它
    void askIgnoreBattery() {
        if (Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) { }
    }

    void stopService() {
        Intent i = new Intent(this, FallService.class);
        i.setAction(FallService.ACTION_STOP);
        startService(i);
    }

    void refreshStatus() {
        statusView.setText(FallService.serviceActive
            ? "状态：监测中（后台保活已开启）"
            : "状态：未启动，点「启动」开始监测");
    }

    void refreshLog() {
        File f = FallService.eventsFile(this);
        StringBuilder sb = new StringBuilder();
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[(int) f.length()];
                in.read(buf);
                sb.append(new String(buf, StandardCharsets.UTF_8));
            } catch (Exception e) {
                sb.append("读取失败：").append(e.getMessage());
            }
        }
        logView.setText(sb.length() == 0 ? "还没有记录" : sb.toString());
    }
}
