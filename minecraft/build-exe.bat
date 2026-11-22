@echo off
REM Build Minecraft.exe (Windows) — chay file nay tren Windows
cd /d %~dp0
echo [1/3] Cai PyInstaller...
pip install pyinstaller
echo [2/3] Build Minecraft.exe...
pyinstaller --onefile --windowed --name Minecraft --add-data "data;data" app.py
echo [3/3] Xong! File o thu muc dist\Minecraft.exe
pause
