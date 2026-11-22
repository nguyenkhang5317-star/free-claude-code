@echo off
REM Build Minecraft.exe bang Java (can cai JDK 17+ tai https://adoptium.net)
cd /d %~dp0
echo [1/3] Compile...
javac -d out src\MinecraftApp.java
if errorlevel 1 (echo LOI compile. Hay cai JDK 17+ & kiem tra javac. & pause & exit)
echo [2/3] Dong goi JAR...
jar cfe Minecraft.jar MinecraftApp -C out .
echo [3/3] Tao .exe bang jpackage...
jpackage --input . --main-jar Minecraft.jar --main-class MinecraftApp --name Minecraft --type app-image --dest dist
echo Xong! Mo thu muc dist\Minecraft\Minecraft.exe de choi.
pause
