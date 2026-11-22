@echo off
cd /d %~dp0
javac -d out src\MinecraftApp.java && java -cp out MinecraftApp
pause
