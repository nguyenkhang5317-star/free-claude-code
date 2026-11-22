/*
 * Minecraft.exe - tiny native Windows launcher for the Java rebuild.
 * Layout next to the exe:  runtime\bin\javaw.exe   app\Minecraft.jar   app\data\
 * Falls back to javaw.exe on PATH when the bundled runtime is missing.
 * Build (cross-compile from any OS):  zig cc -target x86_64-windows-gnu -O2 -municode -Wl,--subsystem,windows launcher.c launcher.rc -o Minecraft.exe
 */
#define WIN32_LEAN_AND_MEAN
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <wchar.h>

static int exists(const wchar_t *p) { return GetFileAttributesW(p) != INVALID_FILE_ATTRIBUTES; }

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR args, int show) {
    (void)inst; (void)prev; (void)show;
    wchar_t dir[MAX_PATH], java[MAX_PATH], jar[MAX_PATH], data[MAX_PATH];
    GetModuleFileNameW(NULL, dir, MAX_PATH);
    wchar_t *slash = wcsrchr(dir, L'\\');
    if (slash) *slash = 0;
    swprintf(java, MAX_PATH, L"%ls\\runtime\\bin\\javaw.exe", dir);
    swprintf(jar, MAX_PATH, L"%ls\\app\\Minecraft.jar", dir);
    swprintf(data, MAX_PATH, L"%ls\\app\\data", dir);
    if (!exists(jar)) {
        MessageBoxW(NULL, L"app\\Minecraft.jar was not found next to Minecraft.exe.\nPlease extract the whole zip before running.", L"Minecraft 1.16.5 Rebuild", MB_ICONERROR);
        return 1;
    }
    int bundled = exists(java);
    static wchar_t cmd[32768];
    swprintf(cmd, 32768, L"\"%ls\" -Xms256m -Xmx2g -XX:+UseG1GC -Dsun.java2d.d3d=true -Dsun.java2d.uiScale=1 -Dmc.data=\"%ls\" -jar \"%ls\" %ls",
             bundled ? java : L"javaw.exe", data, jar, args ? args : L"");
    STARTUPINFOW si; PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof si); si.cb = sizeof si; ZeroMemory(&pi, sizeof pi);
    if (!CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        MessageBoxW(NULL, bundled ? L"Could not start the bundled Java runtime (runtime\\bin\\javaw.exe)."
                                  : L"Java was not found. Use the full download (with the runtime folder) or install Java 17 or newer.",
                    L"Minecraft 1.16.5 Rebuild", MB_ICONERROR);
        return 1;
    }
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
