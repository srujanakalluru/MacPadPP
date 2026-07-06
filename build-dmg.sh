#!/bin/bash
# Build a self-contained MacPad++.dmg on macOS
# Run:  bash build-dmg.sh
set -euo pipefail
cd "$(dirname "$0")"

# --- Use a JDK 22+ (the app relies on the Foreign Function API) ---
jdk_major() {
  local v
  v=$("$1/bin/javac" -version 2>&1 | awk '{print $2}' | cut -d. -f1)
  case "$v" in ''|*[!0-9]*) echo 0 ;; *) echo "$v" ;; esac
}
pick_jdk() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ] && [ "$(jdk_major "$JAVA_HOME")" -ge 22 ]; then
    return 0
  fi
  for c in "$HOME/.sdkman/candidates/java/current" "$HOME/.sdkman/candidates/java"/*; do
    [ -x "$c/bin/javac" ] || continue
    if [ "$(jdk_major "$c")" -ge 22 ]; then JAVA_HOME="$c"; return 0; fi
  done
  if [ -x /usr/libexec/java_home ]; then
    for want in 25 24 23 22; do
      h=$(/usr/libexec/java_home -v "$want" 2>/dev/null) && { JAVA_HOME="$h"; return 0; }
    done
  fi
  for p in "$(brew --prefix openjdk 2>/dev/null || true)" "$(brew --prefix openjdk@25 2>/dev/null || true)"; do
    [ -n "$p" ] && [ -x "$p/libexec/openjdk.jdk/Contents/Home/bin/javac" ] && {
      JAVA_HOME="$p/libexec/openjdk.jdk/Contents/Home"; return 0; }
  done
  return 1
}
if ! pick_jdk; then
  echo "No JDK 22+ found. Install one, e.g.:  sdk install java 25-open" >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "==> Using JDK $(jdk_major "$JAVA_HOME") at $JAVA_HOME"

echo "==> Building fat jar with Maven"
if [ -x ./mvnw ]; then MVN=./mvnw
elif command -v mvn >/dev/null 2>&1; then MVN=mvn
else
  echo "Maven not found. Install it: brew install maven" >&2
  exit 1
fi
"$MVN" -q clean package

JAR=target/macpad.jar
[ -f "$JAR" ] || { echo "shaded jar not produced (check the Maven output above)"; exit 1; }

echo "==> jpackage: building MacPad++.app (with a bundled JRE)"
rm -rf target/jp && mkdir -p target/jp/input target/jp/app target/jp/out
cp "$JAR" target/jp/input/macpad.jar
ICON=""; [ -f src/main/packaging/icon.icns ] && ICON="--icon src/main/packaging/icon.icns"

jpackage \
  --type app-image \
  --name "MacPad++" \
  --app-version 1.0.0 \
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

echo "==> Declaring document types so MacPad++ shows up in Finder's 'Open With'"
# Replace whatever jpackage wrote with an explicit, UTI-based declaration.
"$PB" -c "Delete :CFBundleDocumentTypes" "$PLIST" 2>/dev/null || true
"$PB" -c "Add :CFBundleDocumentTypes array" "$PLIST"

# Entry 0 - editor for all text and source files (offered at the default rank).
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

# Entry 1 - offer MacPad++ for any other file too, but never as the default handler.
"$PB" -c "Add :CFBundleDocumentTypes:1 dict" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:CFBundleTypeName string File" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:CFBundleTypeRole string Editor" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:LSHandlerRank string Alternate" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:LSItemContentTypes array" "$PLIST"
"$PB" -c "Add :CFBundleDocumentTypes:1:LSItemContentTypes:0 string public.data" "$PLIST"

"$PB" -c "Add :LSSupportsOpeningDocumentsInPlace bool true" "$PLIST" 2>/dev/null || true

echo "==> jpackage: wrapping the app into a DMG"
jpackage \
  --type dmg \
  --name "MacPad++" \
  --app-version 1.0.0 \
  --app-image "$APP" \
  --dest target/jp/out

echo
echo "==> Done:  $(ls target/jp/out/*.dmg)"
echo "Open the DMG, drag MacPad++.app to Applications, then launch it once so macOS"
echo "registers the app. It will then appear under Finder's right-click 'Open With'."
