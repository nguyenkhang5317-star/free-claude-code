@echo off
rem ============================================================================
rem  Minecraft 1.16.5 rebuild - Windows build script
rem  Needs a JDK 17 or newer (javac, jar; jpackage for the .exe).
rem  Produces:
rem    build\Minecraft.jar            runnable jar (data pack embedded)
rem    dist\Minecraft\Minecraft.exe   self-contained app image (via jpackage)
rem ============================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"
where javac >nul 2>nul || (echo [build] javac not found. Install a JDK 17+ ^(e.g. Temurin^) and re-run. & exit /b 1)

echo [build] cleaning
if exist build rmdir /s /q build
mkdir build\classes build\tests build\jar

echo [build] compiling sources
dir /s /b src\*.java > build\sources.txt
javac --release 17 -encoding UTF-8 -nowarn -d build\classes @build\sources.txt || exit /b 1
xcopy /e /i /q data build\classes\data >nul

echo [build] packaging jar
echo Main-Class: mc.Main> build\manifest.txt
jar --create --file build\Minecraft.jar --manifest build\manifest.txt -C build\classes . || exit /b 1

echo [build] running verification suite
dir /s /b tests\*.java > build\tests.txt
javac --release 17 -encoding UTF-8 -nowarn -cp build\classes -d build\tests @build\tests.txt || exit /b 1
java -Djava.awt.headless=true -cp build\classes;build\tests -Dmc.data=data mc.tests.TestRunner || (echo [build] TESTS FAILED & exit /b 1)

where jpackage >nul 2>nul || (echo [build] jpackage not found - skipping .exe. Run with: run.bat & exit /b 0)
echo [build] creating dist\Minecraft\Minecraft.exe with jpackage
if exist dist\Minecraft rmdir /s /q dist\Minecraft
copy build\Minecraft.jar build\jar\ >nul
xcopy /e /i /q data build\jar\data >nul
java -cp build\classes -Dmc.data=data mc.Main --make-icon build\icon.ico >nul
jpackage --type app-image --name Minecraft --app-version 1.16.5 --vendor "Minecraft Rebuild" ^
  --input build\jar --main-jar Minecraft.jar --main-class mc.Main --icon build\icon.ico ^
  --java-options "-Xms256m -Xmx2g -Dsun.java2d.uiScale=1" --dest dist || exit /b 1
echo.
echo [build] done:  dist\Minecraft\Minecraft.exe
endlocal
