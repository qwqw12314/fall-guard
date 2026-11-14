#!/bin/sh
# FallGuard 构建脚本
# 需要: JDK(带 javac/keytool)、Android build-tools(aapt2/d8/apksigner/zipalign)、platform android-29
# ARM 主机上 aapt2/zipalign 是 x86_64，用 QEMU=x86_64 前缀(默认 qemu-x86_64)
set -e
cd "$(dirname "$0")"

AJ="${AJ:-$(ls -d android-29*/android.jar ../android-*/android.jar 2>/dev/null | head -1)}"
[ -n "$AJ" ] || { echo "找不到 android-29 的 android.jar，设置 AJ=/path/to/android.jar"; exit 1; }
BT="${BT:-$(ls -d ../build-tools* ../android-14 2>/dev/null | head -1)}"
[ -n "$BT" ] || { echo "找不到 build-tools 目录，设置 BT=/path/to/build-tools"; exit 1; }
QEMU="${QEMU:-qemu-x86_64}"

rm -rf out
mkdir -p out/dex out/java dist

# 1. 资源
$QEMU "$BT/aapt2" compile --dir res -o out/compiled.zip
$QEMU "$BT/aapt2" link -o out/base.apk -I "$AJ" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 29 \
  --java out/java out/compiled.zip

# 2. 字节码 + dex
javac -cp "$AJ" -d out/classes out/java/com/fallguard/R.java src/com/fallguard/*.java
"$BT/d8" --lib "$AJ" --min-api 24 --output out/dex $(find out/classes -name '*.class')

# 3. 打包(把 classes.dex 塞进 apk)
python3 - <<'EOF'
import zipfile
zin = zipfile.ZipFile('out/base.apk')
items = [(i, zin.read(i.filename)) for i in zin.infolist()]
zin.close()
zout = zipfile.ZipFile('out/base_new.apk','w')
for i, data in items:
    zi = zipfile.ZipInfo(i.filename, date_time=(2024,1,1,0,0,0))
    zi.compress_type = zipfile.ZIP_STORED
    zi.external_attr = i.external_attr
    zout.writestr(zi, data)
zout.write('out/dex/classes.dex','classes.dex')
zout.close()
EOF
mv out/base_new.apk out/base.apk

# 4. 对齐 + 签名
$QEMU "$BT/zipalign" -f 4 out/base.apk out/aligned.apk
KS=dist/keystore.jks
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" \
  -storepass fall123 -keypass fall123 -alias fallguard \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=FallGuard, OU=Dev, O=Dev, L=BJ, ST=BJ, C=CN"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:fall123 --key-pass pass:fall123 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out dist/FallGuard.apk out/aligned.apk
"$BT/apksigner" verify dist/FallGuard.apk
echo "OK -> dist/FallGuard.apk"
