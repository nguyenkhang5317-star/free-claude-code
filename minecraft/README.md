# Minecraft 1.16.5 — Java Desktop App

App desktop native bằng **Java** (Swing), không phải web, không cần Python.

## Mô tả
Minecraft 1.16.5 voxel sandbox chạy trực tiếp trên máy tính:
- Worldgen deterministic theo seed (cùng seed = cùng world)
- WASD di chuyển, Space nhảy, đào/đặt block bằng chuột
- Hotbar 1-9, túi đồ E, chế tạo, ăn F
- Ngày/đêm, máu/đói, chết/respawn
- Render isometric 2.5D bằng Java Swing (thuần JDK, không cần thư viện ngoài)

## Yêu cầu
- JDK 17+ (tải tại https://adoptium.net) — cần cho build .exe

## Chạy app
- Windows: double-click `minecraft/java/run.bat`
- Thủ công: `javac -d out src/MinecraftApp.java && java -cp out MinecraftApp`

## Build .exe (Windows)
1. Double-click `minecraft/java/build.bat`
2. Lấy `minecraft/java/dist/Minecraft/Minecraft.exe`

## Controls
WASD di chuyển · Space nhảy · Chuột trái đào · Chuột phải đặt · 1-9 hotbar · E túi đồ · F ăn · Esc menu

## Cấu trúc
```
minecraft/
├── java/src/MinecraftApp.java  ← app Java chính
├── java/build.bat              ← build .exe
├── data/                       ← 103 blocks, 165 items, 69 mobs
└── tests/
```
