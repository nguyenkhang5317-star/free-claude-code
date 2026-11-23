@echo off
rem Runs the game from build\Minecraft.jar (run build.bat first). Needs Java 17+.
cd /d "%~dp0"
if not exist build\Minecraft.jar call build.bat || exit /b 1
start "" javaw -Xms256m -Xmx2g -Dsun.java2d.uiScale=1 -Dmc.data=data -jar build\Minecraft.jar %*
