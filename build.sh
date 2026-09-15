#!/bin/bash
set -e

SDK=/home/runner/android-sdk
mkdir -p $SDK/cmdline-tools
cd $SDK/cmdline-tools
curl -sL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o o.zip
unzip -q o.zip
mv cmdline-tools latest
rm o.zip
yes | $SDK/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null 2>&1 || true
$SDK/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

cd $GITHUB_WORKSPACE
mkdir -p apk/app/src/main/assets
cp index.html moteur.js dxf.js exports.js manifest.json icone.svg icone-192.png icone-512.png apk/app/src/main/assets/

cd apk/app/src/main/res
mkdir -p mipmap-mdpi mipmap-hdpi mipmap-xhdpi mipmap-xxhdpi mipmap-xxxhdpi
for d in mipmap-*; do cp $GITHUB_WORKSPACE/icone-192.png $d/ic_launcher.png; done

cd /home/runner
curl -sL https://services.gradle.org/distributions/gradle-8.7-bin.zip -o g.zip
unzip -q g.zip
rm g.zip

cd $GITHUB_WORKSPACE/apk
/home/runner/gradle-8.7/bin/gradle assembleDebug --no-daemon --stacktrace
cp app/build/outputs/apk/debug/app-debug.apk FASTCUT.apk
ls -la FASTCUT.apk
