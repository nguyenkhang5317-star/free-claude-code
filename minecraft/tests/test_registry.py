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
def test_server_exists():
    assert (ROOT/"server.py").exists()
    assert (ROOT/"client/index.html").exists()
    assert (ROOT/"client/js/main.js").exists()
