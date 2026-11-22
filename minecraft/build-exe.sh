#!/bin/bash
# Build Minecraft binary (Mac/Linux) — .exe Windows phai build tren Windows
cd "$(dirname "$0")"
pip install pyinstaller
pyinstaller --onefile --windowed --name Minecraft --add-data "data:data" app.py
echo "Xong! File o dist/Minecraft"
