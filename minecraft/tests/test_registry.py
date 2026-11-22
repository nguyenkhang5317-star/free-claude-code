import json, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
def test_blocks():
    d = json.loads((ROOT/"data/blocks.json").read_text())
    assert len(d["blocks"]) >= 100
    ids = [b["id"] for b in d["blocks"]]
    assert "minecraft:grass_block" in ids and "minecraft:bedrock" in ids
    assert all(b["id"].startswith("minecraft:") for b in d["blocks"])
def test_items():
    d = json.loads((ROOT/"data/items.json").read_text())
    assert len(d["items"]) >= 50
def test_mobs():
    d = json.loads((ROOT/"data/mobs.json").read_text())
    assert len(d["mobs"]) >= 40
    assert any(m["id"]=="minecraft:cow" for m in d["mobs"])
def test_app_exists():
    assert (ROOT/"app.py").exists()
    assert (ROOT/"launcher.py").exists()
    assert (ROOT/"build-exe.bat").exists()
    # web mode removed — app desktop only
    assert not (ROOT/"server.py").exists()
    assert not (ROOT/"client").exists()
