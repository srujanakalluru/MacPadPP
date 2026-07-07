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

if [ -x ./mvnw ]; then MVN=./mvnw
elif command -v mvn >/dev/null 2>&1; then MVN=mvn
else
  echo "Maven not found. Install it: brew install maven" >&2
  exit 1
fi

exec "$MVN" clean install "$@"
