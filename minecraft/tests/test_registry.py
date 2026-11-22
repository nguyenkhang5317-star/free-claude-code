import json, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
def test_blocks():
    d = json.loads((ROOT/"data/blocks.json").read_text())
    assert len(d["blocks"]) >= 100
def test_items():
    d = json.loads((ROOT/"data/items.json").read_text())
    assert len(d["items"]) >= 50
def test_mobs():
    d = json.loads((ROOT/"data/mobs.json").read_text())
    assert len(d["mobs"]) >= 40
def test_java_app():
    assert (ROOT/"java/src/MinecraftApp.java").exists()
    assert (ROOT/"java/build.bat").exists()
    assert not (ROOT/"app.py").exists()
