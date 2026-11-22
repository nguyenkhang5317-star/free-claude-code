# Minecraft 1.16.5 — Vanilla-Faithful Rebuild (Web App)

Playable voxel sandbox implementing the Master Prompt spec (80k words, 1.16.5 Nether Update).

## Run

```bash
python minecraft/server.py
# open http://localhost:8000
```

Or via Arena preview: server binds `0.0.0.0:8000`.

## Controls

- **WASD** move, **Space** jump, **Shift** sneak, **Ctrl** sprint
- **Mouse** look (click to lock), **LMB** mine (hold), **RMB** place/use
- **1-9** hotbar, **E** inventory, **T** chat/commands, **F** fly toggle (creative), **P** pause
- Commands: `/time set day|night|noon`, `/weather clear|rain|thunder`, `/gamemode survival|creative`, `/give <item> [n]`, `/tp <x> <y> <z>`, `/spawn`, `/kill`, `/seed`, `/help`

## Features (Tier 1–4)

- Procedural Overworld (Perlin terrain, biomes, caves, ores, trees, villages, desert pyramid, stronghold portal room), Nether (5 biomes, fortresses, bastions) and End (islands, cities, dragon) with 8:1 portal linking
- Full block/item registries (data-driven), crafting + furnace smelting, enchanting table (simplified), farming, redstone-lite (lever/button/plate/piston/observer)
- Passive + hostile + Nether mobs with AI goals, animations (keyframe data), drops, breeding/taming, raids-lite, bosses (wither, ender dragon)
- Day/night cycle, weather, hunger/health/XP, status effects, advancements (92 nodes), statistics
- Synthesized audio for every `category.subject.action` event, subtitles, music discs (procedural loops)
- HUD, menus, settings, creative tabs, localization keys

## Tests

```bash
pytest minecraft/tests -v
```

## Asset policy

All textures/audio are original procedural works from Appendix F palettes. Identifiers/geometry follow the spec contract; no Mojang files.
