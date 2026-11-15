#!/bin/bash
# Collega Irbas TV a un server Irbas (per le risposte dell'AI e i consigli). Facoltativo.
BOX="${BOX:-$(cat "$HOME/.irbas-tv-box" 2>/dev/null || true)}"
[ -n "$BOX" ] || read -r -p "Indirizzo della TV (es. 192.168.1.50): " BOX
BOX="${BOX%:5555}:5555"
SERVER="${SERVER:-$(cat "$HOME/.irbas-tv-server" 2>/dev/null || true)}"
[ -n "$SERVER" ] || read -r -p "Indirizzo del server Irbas (es. http://100.x.x.x:8765): " SERVER
echo "$SERVER" > "$HOME/.irbas-tv-server"
adb connect "$BOX" >/dev/null

read -r -p "Il tuo nome (per il saluto, Invio per saltare): " NOME
read -r -s -p "Codice del server (non si vede mentre lo scrivi): " CODICE
echo ""
[ -n "$CODICE" ] || { echo "Serve il codice del server."; exit 1; }

EXTRA=()
[ -n "$NOME" ] && EXTRA=(--es name "${NOME// /_}")
adb -s "$BOX" shell am start -n it.irbas.tv/.HomeActivity --es server "$SERVER" --es code "$CODICE" "${EXTRA[@]}" >/dev/null
echo "Fatto. Sulla TV, in alto, deve comparire «Irbas collegato» (serve che la TV raggiunga il server, per esempio con Tailscale)."
