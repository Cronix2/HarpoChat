#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#
# Met à jour le noyau réseau `harpochat-core` embarqué dans l'app.
#
#   scripts/update-core.sh            # pré-release `latest` (développement)
#   scripts/update-core.sh v0.3.0     # version numérotée (à préférer pour une release)
#
# Télécharge `harpochat-core-android.zip` depuis les releases de Cronix2/HarpoChat_Serveur,
# vérifie son SHA-256 contre `SHA256SUMS`, puis remplace :
#   app/src/main/jniLibs/<abi>/*.so                              (bibliothèque native du noyau)
#   app/src/main/java/app/harpochat/core/*.kt                    (bindings générés, ne pas modifier)
#   app/src/androidTest/assets/core-vectors/v1.json                (vecteurs rejoués sur appareil)
# et note la version dans app/core-version.txt.
# Prérequis : gh (authentifié), unzip, sha256sum.
set -euo pipefail

TAG="${1:-latest}"
REPO="Cronix2/HarpoChat_Serveur"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

gh release download "$TAG" -R "$REPO" -D "$WORK" -p harpochat-core-android.zip -p SHA256SUMS
(cd "$WORK" && sha256sum --ignore-missing -c SHA256SUMS)

mkdir -p "$WORK/x"
unzip -q "$WORK/harpochat-core-android.zip" -d "$WORK/x"

rm -rf "$ROOT/app/src/main/jniLibs"
cp -r "$WORK/x/jniLibs" "$ROOT/app/src/main/jniLibs"
mkdir -p "$ROOT/app/src/main/java/app/harpochat/core" "$ROOT/app/src/androidTest/assets/core-vectors"
rm -f "$ROOT/app/src/main/java/app/harpochat/core/"*.kt
cp "$WORK/x/kotlin/app/harpochat/core/"*.kt "$ROOT/app/src/main/java/app/harpochat/core/"
cp "$WORK/x/test-vectors/v1.json" "$ROOT/app/src/androidTest/assets/core-vectors/v1.json"

NAME="$(gh release view "$TAG" -R "$REPO" --json name -q .name)"
SUM="$(cd "$WORK" && sha256sum harpochat-core-android.zip | cut -d' ' -f1)"
cat > "$ROOT/app/core-version.txt" <<EOF
tag: $TAG
release: $NAME
sha256: $SUM
EOF
echo "Noyau mis à jour : $TAG ($NAME)"
