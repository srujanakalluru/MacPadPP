#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:?usage: jpackage.sh <version>}"

JP="jpackage"
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jpackage" ] && JP="$JAVA_HOME/bin/jpackage"

JAR=target/macpad.jar
[ -f "$JAR" ] || { echo "shaded jar not found at $JAR - run the Maven build first" >&2; exit 1; }

echo "==> jpackage: building MacPad++.app (with a bundled JRE)"
rm -rf target/jp && mkdir -p target/jp/input target/jp/app target/jp/out
cp "$JAR" target/jp/input/macpad.jar
ICON=""; [ -f src/main/packaging/icon.icns ] && ICON="--icon src/main/packaging/icon.icns"

"$JP" \
  --type app-image \
  --name "MacPad++" \
  --app-version "$VERSION" \
  --vendor SK \
  --input target/jp/input \
  --main-jar macpad.jar \
  --main-class com.sk.macpad.MacPad \
  $ICON \
  --mac-package-identifier com.sk.macpad \
  --mac-package-name "MacPad++" \
  --java-options -Dapple.laf.useScreenMenuBar=true \
  --java-options -Dapple.awt.application.name=MacPad++ \
  --java-options -Xdock:name=MacPad++ \
  --java-options --enable-native-access=ALL-UNNAMED \
  --dest target/jp/app

APP="target/jp/app/MacPad++.app"
PLIST="$APP/Contents/Info.plist"
PB=/usr/libexec/PlistBuddy

echo "==> Declaring document types for Finder's 'Open With'"
"$PB" -c "Delete :CFBundleDocumentTypes" "$PLIST" 2>/dev/null || true
"$PB" -c "Add :CFBundleDocumentTypes array" "$PLIST"

"$PB" -c "Add :CFBundleDocumentTypes:0 dict" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:0:CFBundleTypeName string Text Document" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:0:CFBundleTypeRole string Editor" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:0:LSHandlerRank string Default" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:0:LSItemContentTypes array" "$PLIST"
i=0
for u in public.text public.plain-text public.source-code public.shell-script public.xml public.json; do
  "$PB" -c "Add :CFBundleDocumentTypes:0:LSItemContentTypes:$i string $u" "$PLIST"; i=$((i+1))
done
"$PB" -c "Add :CFBundleDocumentTypes:0:CFBundleTypeExtensions array" "$PLIST"
i=0
for e in txt text md markdown log csv tsv json xml yaml yml html htm css js mjs cjs ts tsx jsx \
         java kt kts py pyw rb php pl pm c h cpp cc cxx hpp cs sql sh bash zsh bat cmd ini cfg \
         conf properties gradle groovy scala sc lua dart clj tex pom xsd xsl less; do
  "$PB" -c "Add :CFBundleDocumentTypes:0:CFBundleTypeExtensions:$i string $e" "$PLIST"; i=$((i+1))
done

"$PB" -c "Add :CFBundleDocumentTypes:1 dict" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:CFBundleTypeName string File" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:CFBundleTypeRole string Editor" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:LSHandlerRank string Alternate" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:LSItemContentTypes array" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:LSItemContentTypes:0 string public.data" "$PLIST"

"$PB" -c "Add :LSSupportsOpeningDocumentsInPlace bool true" "$PLIST" 2>/dev/null || true

echo "==> jpackage: wrapping the app into a DMG"
"$JP" \
  --type dmg \
  --name "MacPad++" \
  --app-version "$VERSION" \
  --app-image "$APP" \
  --dest target/jp/out

echo "==> Done:  $(ls target/jp/out/*.dmg)"
