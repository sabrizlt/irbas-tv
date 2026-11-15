#!/bin/bash
# Pubblica Irbas TV su GitHub (una volta sola): crea il repository pubblico, la chiave di firma,
# i segreti, la pagina con le istruzioni, e la prima versione da scaricare. Poi GitHub compila da solo.
set -e
cd "$(dirname "$0")"
NAME="${NAME:-irbas-tv}"
VERSION="v$(grep versionName app/build.gradle | sed -E "s/.*'([^']+)'.*/\1/")"

command -v gh >/dev/null 2>&1 || brew install gh
gh auth status >/dev/null 2>&1 || gh auth login
USER_NAME="$(gh api user -q .login)"
echo "Account GitHub: $USER_NAME — repository: $NAME — versione: $VERSION"

# la chiave di firma resta solo sul Mac (e nei segreti di GitHub), mai nel repository
SECRETS="$HOME/.irbas-secrets"
mkdir -p "$SECRETS"
KS="$SECRETS/irbas-tv.jks"
PW="$SECRETS/irbas-tv.password"
brew list openjdk@17 >/dev/null 2>&1 || brew install openjdk@17
KEYTOOL="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home/bin/keytool"
if [ ! -f "$KS" ]; then
    openssl rand -hex 16 > "$PW"
    chmod 600 "$PW"
    "$KEYTOOL" -genkeypair -keystore "$KS" -alias irbas -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$(cat "$PW")" -keypass "$(cat "$PW")" -dname "CN=Irbas TV" >/dev/null
    echo "Chiave di firma creata in $KS (conservala: serve per tutti gli aggiornamenti)"
fi

if [ ! -d .git ]; then
    git init -q -b main
fi
git add -A
git commit -qm "Irbas TV $VERSION" || true

if ! gh repo view "$USER_NAME/$NAME" >/dev/null 2>&1; then
    gh repo create "$NAME" --public --description "Irbas TV: la TV semplice, con un assistente (Android TV / Google TV)" --source . --remote origin --push
else
    git remote get-url origin >/dev/null 2>&1 || git remote add origin "https://github.com/$USER_NAME/$NAME.git"
    git push -u origin main
fi

gh secret set KEYSTORE_BASE64 --repo "$USER_NAME/$NAME" --body "$(base64 -i "$KS")"
gh secret set KEYSTORE_PASSWORD --repo "$USER_NAME/$NAME" --body "$(cat "$PW")"

# la pagina con le istruzioni (GitHub Pages dalla cartella docs/)
gh api -X POST "repos/$USER_NAME/$NAME/pages" -f "source[branch]=main" -f "source[path]=/docs" >/dev/null 2>&1 || true

# la versione: GitHub la compila e la mette tra i download (ci vogliono 3-5 minuti)
if ! git rev-parse "$VERSION" >/dev/null 2>&1; then git tag "$VERSION"; fi
git push origin "$VERSION" 2>/dev/null || git push -f origin "$VERSION"

echo ""
echo "Fatto!"
echo "Pagina per tutti:  https://$USER_NAME.github.io/$NAME"
echo "Compilazione:      https://github.com/$USER_NAME/$NAME/actions"
echo "Download:          https://github.com/$USER_NAME/$NAME/releases/latest"
