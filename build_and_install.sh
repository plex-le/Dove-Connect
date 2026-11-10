#!/bin/zsh
set -e

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

SDK_DIR="$HOME/Library/Android/sdk"
BUILD_TOOLS="$SDK_DIR/build-tools/36.1.0"
ANDROID_JAR="$SDK_DIR/platforms/android-36/android.jar"

PROJ_DIR="/Users/wangle/.gemini/antigravity/scratch/DoveConnect"
BUILD_DIR="$PROJ_DIR/build"
SRC_DIR="$PROJ_DIR/app/src/main"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/apk"

echo "[1/5] Compiling resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$SRC_DIR/res" -o "$BUILD_DIR/res.zip"
"$BUILD_TOOLS/aapt2" link \
    -I "$ANDROID_JAR" \
    --manifest "$SRC_DIR/AndroidManifest.xml" \
    -o "$BUILD_DIR/unaligned.apk" \
    --java "$BUILD_DIR/gen" \
    "$BUILD_DIR/res.zip"

echo "[2/5] Compiling Java sources..."
javac -source 11 -target 11 \
    -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/obj" \
    "$BUILD_DIR/gen/com/plexle/doveconnect/R.java" \
    "$SRC_DIR/java/com/plexle/doveconnect/"*.java

echo "[3/5] Converting bytecode to DEX with d8..."
"$BUILD_TOOLS/d8" "$BUILD_DIR/obj/com/plexle/doveconnect/"*.class \
    --lib "$ANDROID_JAR" \
    --min-api 29 \
    --output "$BUILD_DIR"

echo "[4/5] Packaging and aligning APK..."
cd "$BUILD_DIR"
zip -q -j unaligned.apk classes.dex
"$BUILD_TOOLS/zipalign" -f 4 unaligned.apk aligned.apk

KEYSTORE="$PROJ_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass android \
        -keypass android \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$PROJ_DIR/DoveConnect.apk" \
    aligned.apk

echo "[5/5] Installing APK to Pixel..."
adb install -r -g "$PROJ_DIR/DoveConnect.apk"
echo "Done! APK is at $PROJ_DIR/DoveConnect.apk"
