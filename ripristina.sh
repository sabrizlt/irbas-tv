#!/bin/bash
# Rimette la schermata originale della TV e l'assistente Google. Con "--togli" disinstalla anche Irbas TV.
BOX="${BOX:-$(cat "$HOME/.irbas-tv-box" 2>/dev/null || true)}"
[ -n "$BOX" ] || read -r -p "Indirizzo della TV (es. 192.168.1.50): " BOX
BOX="${BOX%:5555}:5555"
adb connect "$BOX" >/dev/null
for P in timvision.launcher com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.leanbacklauncher; do
    adb -s "$BOX" shell pm enable "$P" >/dev/null 2>&1
done
adb -s "$BOX" shell cmd role remove-role-holder android.app.role.HOME it.irbas.tv >/dev/null 2>&1
adb -s "$BOX" shell cmd role remove-role-holder android.app.role.ASSISTANT it.irbas.tv >/dev/null 2>&1
SVC="it.irbas.tv/it.irbas.tv.HomeKeyService"
CUR="$(adb -s "$BOX" shell settings get secure enabled_accessibility_services | tr -d '\r')"
NEW="$(echo "$CUR" | tr ':' '\n' | grep -v "^$SVC$" | grep -v '^null$' | paste -sd ':' -)"
if [ -z "$NEW" ]; then adb -s "$BOX" shell settings delete secure enabled_accessibility_services >/dev/null; else adb -s "$BOX" shell settings put secure enabled_accessibility_services "$NEW"; fi
if [ "$1" = "--togli" ]; then adb -s "$BOX" uninstall it.irbas.tv; fi
adb -s "$BOX" shell input keyevent KEYCODE_HOME
echo "Fatto: è tornata la schermata originale."
