# Minecraft 1.16.5 — Desktop App

App desktop native (Python Tkinter), **không phải web** — không cần browser, không cần server.

## Mô tả
Minecraft 1.16.5 voxel sandbox chạy trực tiếp trên máy tính:
- Worldgen deterministic theo seed (cùng seed = cùng world)
- Di chuyển WASD, nhảy Space, đào/đặt block bằng chuột
- Hotbar 1-9, túi đồ E, chế tạo, ăn F
- Ngày/đêm, máu/đói, chết/respawn
- Render isometric 2.5D bằng Tkinter Canvas (thuần Python, không cần cài thêm)

## Tải về
- **Source zip:** xem Releases trên GitHub (file `Minecraft-1.16.5.zip`)
- **File .exe Windows:** giải nén zip → double-click `build-exe.bat` → lấy `dist/Minecraft.exe`

## Chạy app
```
python minecraft/app.py        # chạy trực tiếp
python minecraft/launcher.py   # launcher kiểu Minecraft Launcher
```
Windows: double-click `minecraft/run-app.bat`

## Build .exe (Windows)
`.exe` phải build trên Windows (PyInstaller không cross-compile từ Linux).
1. Double-click `minecraft/build-exe.bat`
2. Lấy `minecraft/dist/Minecraft.exe` — double-click chạy ngay

## Controls
WASD di chuyển · Space nhảy · Chuột trái đào · Chuột phải đặt · 1-9 hotbar · E túi đồ · F ăn · Esc menu

## Cấu trúc
```
minecraft/
├── app.py            ← app desktop chính
├── launcher.py       ← launcher UI
├── build-exe.bat     ← build .exe Windows
├── data/             ← 103 blocks, 165 items, 69 mobs
└── tests/
```
