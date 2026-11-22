"""
Release packager - builds the downloadable artefacts from any OS (used to make the prebuilt Windows zip).

    python3 tools/package_release.py [--javac "javac"] [--javac-flags "--release 17"] [--java java]

Produces in dist/:
    Minecraft-1.16.5.jar                 cross-platform runnable jar (needs Java 17+), data pack embedded
    Minecraft-1.16.5-Windows/            self-contained Windows app:
        Minecraft.exe                    native launcher (cross-compiled with zig from tools/launcher/launcher.c)
        runtime/                         bundled Java runtime (from the jdk4py win_amd64 wheel on PyPI)
        app/Minecraft.jar, app/data/     game + editable data pack
    Minecraft-1.16.5-Windows.zip         the above, zipped (the direct download)

Tools fetched automatically from PyPI when not given: ziglang (C cross-compiler), jdk4py (Windows JRE).
"""
import argparse, glob, os, shlex, shutil, subprocess, sys, tempfile, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from make_jar import make_jar  # noqa: E402

VERSION = "1.16.5"


def run(cmd, **kw):
    print("  $", cmd if isinstance(cmd, str) else " ".join(cmd))
    subprocess.run(cmd, check=True, shell=isinstance(cmd, str), **kw)


def pip_wheel(pkg, platform, dest):
    os.makedirs(dest, exist_ok=True)
    found = glob.glob(os.path.join(dest, pkg + "-*.whl"))
    if not found:
        run([sys.executable, "-m", "pip", "download", pkg, "--no-deps", "--only-binary=:all:", "--platform", platform,
             "--python-version", "3.12", "-d", dest])
        found = glob.glob(os.path.join(dest, pkg + "-*.whl"))
    out = os.path.join(dest, pkg + "_x")
    if not os.path.isdir(out):
        with zipfile.ZipFile(found[0]) as z:
            z.extractall(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--javac", default="javac")
    ap.add_argument("--javac-flags", default="--release 17")
    ap.add_argument("--java", default="java")
    ap.add_argument("--zig", default=None)
    ap.add_argument("--cache", default=os.path.join(tempfile.gettempdir(), "mc-release-cache"))
    a = ap.parse_args()
    os.chdir(ROOT)
    build, dist = os.path.join(ROOT, "build"), os.path.join(ROOT, "dist")
    shutil.rmtree(build, ignore_errors=True)
    shutil.rmtree(dist, ignore_errors=True)
    classes = os.path.join(build, "classes")
    os.makedirs(classes)
    os.makedirs(dist)

    print("[1/5] compiling")
    sources = [os.path.join(r, f) for d in ("src", "third_party/jorbis/src") for r, _, fs in os.walk(d) for f in fs if f.endswith(".java")]
    with open(os.path.join(build, "sources.txt"), "w") as f:
        f.write("\n".join(sources))
    run(shlex.split(a.javac) + shlex.split(a.javac_flags) + ["-encoding", "UTF-8", "-nowarn", "-d", classes] + sources)
    shutil.copytree("data", os.path.join(classes, "data"))

    print("[2/5] verification suite")
    tclasses = os.path.join(build, "tests")
    tests = [os.path.join(r, f) for r, _, fs in os.walk("tests") for f in fs if f.endswith(".java")]
    run(shlex.split(a.javac) + shlex.split(a.javac_flags) + ["-encoding", "UTF-8", "-nowarn", "-cp", classes, "-d", tclasses] + tests)
    run(shlex.split(a.java) + ["-Djava.awt.headless=true", "-cp", classes + os.pathsep + tclasses, "-Dmc.data=data", "mc.tests.TestRunner"])

    print("[3/5] jar")
    jar = os.path.join(dist, f"Minecraft-{VERSION}.jar")
    make_jar(classes, jar, "mc.Main")

    print("[4/5] Windows launcher + runtime")
    zig = a.zig or os.path.join(pip_wheel("ziglang", "manylinux_2_12_x86_64", os.path.join(a.cache, "zig")), "ziglang", "zig")
    os.chmod(zig, 0o755)
    ldir = os.path.join(ROOT, "tools", "launcher")
    run(shlex.split(a.java) + ["-cp", classes, "-Dmc.data=data", "mc.Main", "--make-icon", os.path.join(ldir, "icon.ico")])
    win = os.path.join(dist, f"Minecraft-{VERSION}-Windows")
    os.makedirs(os.path.join(win, "app"))
    run([zig, "cc", "-target", "x86_64-windows-gnu", "-O2", "-municode", "-Wl,--subsystem,windows",
         "launcher.c", "launcher.rc", "-o", os.path.join(win, "Minecraft.exe")], cwd=ldir)
    for junk in glob.glob(os.path.join(win, "Minecraft.pdb")):
        os.remove(junk)
    jre = os.path.join(pip_wheel("jdk4py", "win_amd64", os.path.join(a.cache, "jre")), "jdk4py", "java-runtime")
    shutil.copytree(jre, os.path.join(win, "runtime"), ignore=shutil.ignore_patterns("include", "*.lib"))
    keep_exe = {"java.exe", "javaw.exe"}
    for exe in glob.glob(os.path.join(win, "runtime", "bin", "*.exe")):
        if os.path.basename(exe) not in keep_exe:
            os.remove(exe)
    shutil.copy(jar, os.path.join(win, "app", "Minecraft.jar"))
    shutil.copytree("data", os.path.join(win, "app", "data"))
    os.makedirs(os.path.join(win, "resourcepacks"))
    with open(os.path.join(win, "resourcepacks", "README.txt"), "w", newline="\r\n") as f:
        f.write("Put resource packs (.zip or folders containing assets/minecraft/sounds.json) here.\n"
                "Sound event ids match Minecraft Java 1.16.5, so sound packs made for Minecraft work as-is.\n")
    lic = os.path.join(win, "licenses")
    os.makedirs(lic)
    shutil.copy(os.path.join("third_party", "jorbis", "COPYING.LIB"), os.path.join(lic, "JOrbis-LGPL-2.1.txt"))
    shutil.copy(os.path.join("third_party", "jorbis", "PROVENANCE.md"), os.path.join(lic, "JOrbis-README.md"))
    with open(os.path.join(win, "README.txt"), "w", newline="\r\n") as f:
        f.write(f"""Minecraft {VERSION} - original Java rebuild (desktop app)
==========================================================

1. Extract this whole folder (do not run from inside the zip).
2. Double-click Minecraft.exe.  No Java installation is needed - a Java runtime is bundled in runtime\\.

Saves, options.txt and screenshots go to %APPDATA%\\.minecraft-rebuild\\
The data pack (blocks, items, recipes, mobs, biomes, sounds, textures, tuning) is in app\\data\\ - edit the JSON and restart.

Controls: WASD move, mouse look, Space jump (double-tap = fly in creative), Left Shift sneak, Left Ctrl sprint,
left click break/attack, right click use/place, middle click pick block, 1-9 / wheel hotbar, E inventory,
Q drop (Ctrl+Q stack), F swap hands, T chat, / command, F1 hide HUD, F2 screenshot, F3 debug, F5 camera, F11 fullscreen.

SOUNDS - original Minecraft audio
  This game ships no Mojang files. If Minecraft Java Edition is installed on this PC (official launcher or any
  launcher using %APPDATA%\\.minecraft) and you have played 1.16.x or newer once, the game automatically uses the
  original sounds and music from your installation (Options -> "Sounds: Original Minecraft").
  You can also drop resource packs into the resourcepacks\\ folder next to Minecraft.exe.
  Minecraft installed somewhere else? Set the environment variable MC_ASSETS to that .minecraft folder.
  Without either, built-in synthesised sounds are used.

If Windows SmartScreen warns about an unknown publisher: More info -> Run anyway (the exe is unsigned).
""")

    print("[5/5] zip")
    zpath = os.path.join(dist, f"Minecraft-{VERSION}-Windows.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for r, _, fs in os.walk(win):
            for f in fs:
                p = os.path.join(r, f)
                z.write(p, os.path.join(os.path.basename(win), os.path.relpath(p, win)))
    print(f"done: {zpath} ({os.path.getsize(zpath) / 1e6:.1f} MB), {jar} ({os.path.getsize(jar) / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
