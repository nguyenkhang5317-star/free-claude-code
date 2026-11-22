"""Create an executable jar without the JDK `jar` tool: python3 tools/make_jar.py <classes_dir> <out.jar> <main.Class>"""
import os, sys, zipfile

def make_jar(classes, out, main):
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", f"Manifest-Version: 1.0\r\nMain-Class: {main}\r\nCreated-By: minecraft-rebuild tools/make_jar.py\r\n\r\n")
        for root, _, files in os.walk(classes):
            for f in sorted(files):
                p = os.path.join(root, f)
                z.write(p, os.path.relpath(p, classes).replace(os.sep, "/"))

if __name__ == "__main__":
    make_jar(*sys.argv[1:4])
    print("jar written:", sys.argv[2])
