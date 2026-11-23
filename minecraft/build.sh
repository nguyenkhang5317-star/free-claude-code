#!/usr/bin/env bash
# Minecraft 1.16.5 rebuild - Linux/macOS build script (needs JDK 17+).
set -euo pipefail
cd "$(dirname "$0")"
JAVAC=${JAVAC:-javac}; JAVA=${JAVA:-java}
rm -rf build && mkdir -p build/classes build/tests
echo "[build] compiling sources"
$JAVAC --release 17 -encoding UTF-8 -nowarn -d build/classes $(find src third_party/jorbis/src -name '*.java')
cp -r data build/classes/data
echo "[build] packaging jar"
if command -v jar >/dev/null; then
  printf 'Main-Class: mc.Main\n' > build/manifest.txt
  jar --create --file build/Minecraft.jar --manifest build/manifest.txt -C build/classes .
else
  python3 tools/make_jar.py build/classes build/Minecraft.jar mc.Main
fi
echo "[build] running verification suite"
$JAVAC --release 17 -encoding UTF-8 -nowarn -cp build/classes -d build/tests $(find tests -name '*.java')
$JAVA -Djava.awt.headless=true -cp build/classes:build/tests -Dmc.data=data mc.tests.TestRunner
echo "[build] done: build/Minecraft.jar  (run: ./run.sh)"
