package com.fallguard;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FallService extends Service implements SensorEventListener {
    public static final String ACTION_START  = "com.fallguard.START";
    public static final String ACTION_STOP   = "com.fallguard.STOP";
    public static final String ACTION_WATCHDOG = "com.fallguard.WATCHDOG";

    static final float G = 9.81f;
    static final float FREEFALL_MAX = 0.55f * G;
    static final float FREEFALL_END = 0.85f * G;
    static final long  FREEFALL_MS  = 100;
    static final float IMPACT       = 3.5f * G;
    static final float HARD_IMPACT  = 4.5f * G;
    static final long  PENDING_MS   = 800;
    static final long  COOLDOWN_MS  = 3000;

    static volatile boolean serviceActive = false;

    SensorManager sm;
    Sensor sensor;
    PowerManager wakeLockManager;
    PowerManager.WakeLock wakeLock;
    boolean running = false;
    long freeStart = -1;
    boolean pendingFreefall = false;
    long pendingUntil = 0;
    long lastEvent = 0;
    int impactRun = 0;
    float peakImpact = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("monitor",
            "FallGuard 监测", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "monitor")
            .setContentTitle("FallGuard")
            .setContentText("正在监测手机跌落")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            cancelWatchdog();
            stopSelf();
            return START_NOT_STICKY;
        }
        // 启动 / 系统粘性重建 / 看门狗拉起：都要重新布防
        startMonitoring();
        scheduleWatchdog();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }

    void startMonitoring() {
        if (running) return;
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) { stopSelf(); return; }
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
        // 唤醒锁：屏幕灭了 CPU 也保持运行，传感器不停采
        wakeLockManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (wakeLockManager != null) {
            wakeLock = wakeLockManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "fallguard::monitor");
            wakeLock.acquire();
        }
        running = true;
        serviceActive = true;
    }

    void stopMonitoring() {
        running = false;
        serviceActive = false;
        if (sm != null && sensor != null) sm.unregisterListener(this);
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) {}
        }
    }

    // 看门狗：每 15 分钟广播一次，进程被杀也能自动拉回来
    void scheduleWatchdog() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent it = new Intent(ACTION_WATCHDOG).setPackage(getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, it,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        long now = System.currentTimeMillis();
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, now + 15 * 60 * 1000,
            15 * 60 * 1000, pi);
    }

    void cancelWatchdog() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent it = new Intent(ACTION_WATCHDOG).setPackage(getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, it,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }

    @Override public void onSensorChanged(SensorEvent e) {
        long now = System.currentTimeMillis();
        float m = (float) Math.sqrt(
            e.values[0] * e.values[0] +
            e.values[1] * e.values[1] +
            e.values[2] * e.values[2]);

        if (m < FREEFALL_MAX) {
            if (freeStart < 0) freeStart = now;
        } else if (freeStart >= 0 && m >= FREEFALL_END) {
            if (now - freeStart >= FREEFALL_MS) {
                pendingFreefall = true;
                pendingUntil = now + PENDING_MS;
            }
            freeStart = -1;
        }
        if (pendingFreefall && now > pendingUntil) pendingFreefall = false;

        if (m >= IMPACT) {
            impactRun++;
            if (m > peakImpact) peakImpact = m;
            if (impactRun >= 3 && now - lastEvent > COOLDOWN_MS) {
                if (pendingFreefall) {
                    logEvent("检测到手机跌落（摔落）！峰值 "
                        + String.format(Locale.US, "%.1f", peakImpact / G) + "g", true);
                } else if (m >= HARD_IMPACT) {
                    logEvent("剧烈撞击 峰值 "
                        + String.format(Locale.US, "%.1f", peakImpact / G) + "g", false);
                }
                pendingFreefall = false;
                lastEvent = now;
                impactRun = 0;
                peakImpact = 0;
                // 触发完立即重新布防，下一跤不用重新点启动
                rearm();
            }
        } else if (m < IMPACT * 0.8f) {
            impactRun = 0;
            peakImpact = 0;
        }
    }

    // 每次事件后确保监测仍在跑（万一传感器被系统回收，重新注册）
    void rearm() {
        if (!running && sm != null) startMonitoring();
        scheduleWatchdog();
    }

    void logEvent(String msg, boolean fell) {
        try {
            PrintWriter pw = new PrintWriter(new FileWriter(eventsFile(this), true));
            pw.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date()) + "  " + msg);
            pw.close();
        } catch (Exception ignored) { }
        Notification n = new Notification.Builder(this, "monitor")
            .setContentTitle(fell ? "FallGuard：检测到手机跌落！"
                                   : "FallGuard：剧烈撞击")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify(fell ? 2 : 3, n);
    }

    static File eventsFile(Context c) {
        return new File(c.getFilesDir(), "fall_events.log");
    }
}
