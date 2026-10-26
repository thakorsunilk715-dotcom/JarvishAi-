#!/data/data/com.termux/files/usr/bin/bash
# UNTESTED. Termux (F-Droid version) me chalayein. Android SDK ka aapt2 x86 hota hai, ARM phone par nahi chalta,
# isliye aapko ARM64 aapt2 binary khud ~/aapt2 par rakhni hogi (step 3).
set -e
pkg update -y && pkg install -y openjdk-17 gradle wget unzip
export ANDROID_HOME=$HOME/android-sdk; mkdir -p $ANDROID_HOME/cmdline-tools && cd $ANDROID_HOME/cmdline-tools
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O t.zip
unzip -qo t.zip && rm -rf latest && mv cmdline-tools latest && cd -
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
[ -x "$HOME/aapt2" ] || { echo "ARM64 aapt2 ko $HOME/aapt2 par rakhein, phir dobara chalayein."; exit 1; }
gradle assembleDebug -Pandroid.aapt2FromMavenOverride=$HOME/aapt2
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
