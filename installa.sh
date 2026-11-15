#!/bin/bash
# Irbas TV dal Mac: prepara gli strumenti (solo la prima volta), compila l'app, la installa sulla TV
# e la mette come schermata principale. Per tornare alla schermata originale: ./ripristina.sh
set -e
QUI="$(cd "$(dirname "$0")" && pwd)"
SDK="$HOME/Library/Android/sdk"
cd "$QUI"

# l'indirizzo della TV: lo chiede la prima volta e se lo ricorda (in ~/.irbas-tv-box, fuori dal progetto)
BOX="${BOX:-$(cat "$HOME/.irbas-tv-box" 2>/dev/null || true)}"
if [ -z "$BOX" ]; then
    read -r -p "Indirizzo della TV (Impostazioni › Rete, es. 192.168.1.50): " BOX
fi
BOX="${BOX%:5555}:5555"
echo "$BOX" > "$HOME/.irbas-tv-box"

echo "=== 1. Java 17 ==="
# Sempre la 17: con versioni più nuove (es. 25) Gradle 8 non parte.
brew list openjdk@17 >/dev/null 2>&1 || brew install openjdk@17
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"

echo ""
echo "=== 2. Strumenti Android (la prima volta scarica circa 400 MB) ==="
brew list --cask android-commandlinetools >/dev/null 2>&1 || brew install --cask android-commandlinetools
command -v adb >/dev/null 2>&1 || brew install --cask android-platform-tools
SDKMANAGER="$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager"
mkdir -p "$SDK"
if [ ! -d "$SDK/platforms/android-34" ] || [ ! -d "$SDK/build-tools/34.0.0" ]; then
    yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
    "$SDKMANAGER" --sdk_root="$SDK" "platforms;android-34" "build-tools;34.0.0" "platform-tools"
fi
echo "sdk.dir=$SDK" > local.properties

echo ""
echo "=== 3. Gradle ==="
if [ ! -x ./gradlew ]; then
    command -v gradle >/dev/null 2>&1 || brew install gradle
    TMP="$(mktemp -d)"
    (cd "$TMP" && touch settings.gradle && gradle wrapper --gradle-version 8.10.2 -q)
    cp -R "$TMP/gradlew" "$TMP/gradle" .
    rm -rf "$TMP"
fi

echo ""
echo "=== 4. Compilo Irbas TV ==="
./gradlew --no-daemon -q assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "ERRORE: l'app non è stata creata. Manda a Claude le righe di errore qui sopra."; exit 1; }

echo ""
echo "=== 5. Installo sulla TV ($BOX) ==="
adb connect "$BOX" >/dev/null
adb -s "$BOX" install -r "$APK"

echo ""
echo "=== 6. Schermata principale, microfono, tasto Home ==="
adb -s "$BOX" shell pm grant it.irbas.tv android.permission.RECORD_AUDIO >/dev/null 2>&1 || true
adb -s "$BOX" shell appops set it.irbas.tv GET_USAGE_STATS allow >/dev/null 2>&1 || true
adb -s "$BOX" shell cmd package set-home-activity it.irbas.tv/.HomeActivity >/dev/null 2>&1 || true
adb -s "$BOX" shell cmd role add-role-holder android.app.role.HOME it.irbas.tv >/dev/null 2>&1 || true
adb -s "$BOX" shell cmd role add-role-holder android.app.role.ASSISTANT it.irbas.tv >/dev/null 2>&1 || true
SVC="it.irbas.tv/it.irbas.tv.HomeKeyService"
CUR="$(adb -s "$BOX" shell settings get secure enabled_accessibility_services | tr -d '\r')"
case "$CUR" in
    *"$SVC"*) ;;
    ""|null) adb -s "$BOX" shell settings put secure enabled_accessibility_services "$SVC" ;;
    *) adb -s "$BOX" shell settings put secure enabled_accessibility_services "$CUR:$SVC" ;;
esac
adb -s "$BOX" shell settings put secure accessibility_enabled 1
# Le schermate originali si spengono (non si cancellano): così il tasto Home porta solo a Irbas TV.
for P in timvision.launcher com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher; do
    if adb -s "$BOX" shell pm list packages "$P" | tr -d '\r' | grep -qx "package:$P"; then
        adb -s "$BOX" shell pm disable-user --user 0 "$P" >/dev/null && echo "Schermata originale spenta: $P"
    fi
done
adb -s "$BOX" shell am start -n it.irbas.tv/.HomeActivity >/dev/null
echo ""
echo "Fatto: sulla TV c'è Irbas TV."
echo "Per collegarla al server di Irbas (AI):  ./collega.sh"
echo "Per tornare alla schermata originale:     ./ripristina.sh"
