# FallGuard

Android 手机跌落检测 App：后台监测加速度计，检测到「自由落体 + 撞击」时弹通知并写日志，带一整套保活机制。

纯 Java，无第三方依赖，targetSdk 29（兼容 Android 7.0+，安卓 10 实测可装）。

## 功能

- **跌落检测**：加速度计 FASTEST 采样（通常 100–200Hz），
  读数掉到 < 0.55g 持续 ≥ 100ms 视为自由落体；
  落体后 0.8s 内出现 ≥ 3.5g 撞击尖峰（连续 3 个采样）判定为「跌落」；
  无自由落体但 ≥ 4.5g 记为「剧烈撞击」。
- **事件日志**：时间 + 峰值加速度（g）写入 App 内部 `fall_events.log`，界面可刷新查看。
- **保活**：
  - 前台服务（常驻小通知）
  - 启动时申请「忽略电池优化」
  - PARTIAL_WAKE_LOCK 锁屏后持续采样
  - 15 分钟看门狗广播自动拉起
  - BOOT_COMPLETED / USER_PRESENT 开机自启
  - 每次事件后自动重新布防，无需手动重启

## 安装

- 直接装预编译包：`dist/FallGuard.apk`（v1+v2+v3 签名，v1.1 / versionCode 2）
- 安装后打开 → 点「启动」→ 允许「忽略电池优化」对话框
- 小米 / 华为等国产 ROM 建议再在系统「自启动管理」里放行

## 从源码构建

需要：JDK 8+、Android SDK（build-tools 34、platform android-29）。
ARM 主机上 aapt2 / zipalign 是 x86_64 二进制，需用 `qemu-x86_64` 前缀执行。

```bash
./build.sh            # 产出 dist/FallGuard.apk
```

`build.sh` 默认找 `android-29` 和 `build-tools` 目录，可用 `AJ`、`BT` 环境变量覆盖。

> 说明：签名密钥 `keystore.jks` 不在仓库里（私有保存）。全新克隆后 `build.sh` 会自动生成一个新密钥；若要覆盖安装官方发布版，请直接下载 Release 里附的 `FallGuard.apk`。

## 目录

```
AndroidManifest.xml
res/layout/main.xml
src/com/fallguard/
  MainActivity.java   界面（启动/停止/刷新 + 状态）
  FallService.java    前台服务 + 传感器算法 + 保活
  BootReceiver.java   开机自启
  WatchdogReceiver.java 看门狗
```

## 阈值调整

灵敏度都在 `FallService.java` 顶部常量里：`FREEFALL_MAX`、`IMPACT`、`HARD_IMPACT`、`COOLDOWN_MS` 等，改完重新编译即可。

## License

MIT
