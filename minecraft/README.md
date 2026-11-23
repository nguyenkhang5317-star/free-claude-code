# Minecraft 1.16.5 — original Java rebuild (desktop app)

This is a vanilla-faithful rebuild of Minecraft Java Edition 1.16.5, written from scratch in pure Java 17. It uses only the JDK plus one small vendored decoder (JOrbis, LGPL, `third_party/jorbis/`): no OpenGL bindings, no Mojang code or assets. It follows `MASTER_PROMPT_FULL.md`.
Every texture and the built-in sounds are generated procedurally from the data pack in `data/`.

## Original Minecraft sounds

No Mojang audio is bundled. Sound event ids are identical to 1.16.5, so the game reads real sounds at runtime from:

1. resource packs (`.zip` or folder with `assets/minecraft/sounds.json`) in `resourcepacks/` next to `Minecraft.exe` or in the game directory;
2. the player's own Minecraft Java installation: `%APPDATA%\.minecraft\assets` (official launcher, TLauncher…), `~/.minecraft/assets`, the macOS folder, or `MC_ASSETS` / `-Dmc.assets=`. The `1.16` asset index is preferred, otherwise the newest one.

With original sounds the mixer follows vanilla's OpenAL setup (linear roll-off to `attenuation_distance × max(1, volume)`, pitch clamped to 0.5–2, no filters), and the client runs vanilla's ambience: the music ticker (menu, game, creative, underwater, nether biomes, End), cave and nether moods, nether biome loops and additions, underwater loop, rain near the camera, and the fire, furnace, portal, lava and water display ticks. Options → "Sounds" switches between original and built-in sounds; there is also a Music slider. Without a pack, every id falls back to the procedural `Sfx` engine.

## Download & play

| Download | What it is | Needs |
|---|---|---|
| **`Minecraft-1.16.5-Windows.zip`** | Self-contained Windows app: `Minecraft.exe` + bundled Java runtime | Nothing. Extract the zip and double-click `Minecraft.exe` |
| `Minecraft-1.16.5.jar` | Cross-platform jar with the data pack embedded | Java 17+ (`java -jar Minecraft-1.16.5.jar`) |

Saves, `options.txt` and screenshots go to `%APPDATA%\.minecraft-rebuild\` (Windows) or `~/.minecraft-rebuild/`.
You can change this with `-Dmc.dir=...`.
Windows SmartScreen may warn that the exe is unsigned. Click *More info → Run anyway*.

## Build from source

```bat
build.bat        :: Windows, JDK 17+: compile → tests → build\Minecraft.jar → dist\Minecraft\Minecraft.exe (jpackage)
run.bat          :: launch build\Minecraft.jar
```
```sh
./build.sh && ./run.sh                      # Linux / macOS
python3 tools/package_release.py            # release artefacts: jar + self-contained Windows zip (from any OS)
python3 tools/gen_data.py                   # regenerate data/*.json from the authoritative tables
```

Other entry points:
- `java -jar Minecraft.jar --headless <seed> <ticks>` generates and ticks a world with null render and audio sinks (§2.8).
- `--make-icon out.ico` writes the app icon.

## Controls (§14 / Appendix keys)

| Key | Action |
|---|---|
| W A S D / mouse | Move / look |
| Space | Jump. Double-tap to fly in creative |
| Left Shift / Left Ctrl | Sneak / sprint. Double-tap W also sprints |
| Left click | Break block / attack |
| Right click | Use item / place block |
| Middle click | Pick block |
| 1–9, mouse wheel | Hotbar |
| E | Inventory (with creative palette in creative mode) |
| Q / Ctrl+Q | Drop one / drop stack |
| F | Swap hands |
| T, / | Chat, commands |
| Esc | Pause menu |
| F1 | Hide HUD |
| F2 | Screenshot |
| F3 | Debug overlay with server stage timers and frame chart |
| F5 | Camera mode |
| F11 | Fullscreen |

Commands available in chat:
- `/time`, `/weather`, `/gamemode`, `/difficulty`, `/give`
- `/tp`, `/kill`, `/seed`, `/summon`, `/spawnpoint`, `/setworldspawn`
- `/effect`, `/xp`, `/clear`, `/gamerule`, `/help`

## What is implemented

- **World:**
  - Deterministic overworld generator, a pure function of (seed, cx, cz). It produces 37 biomes, density terrain, cheese/noodle caves, ravines, ores, trees, plants and dungeons.
  - Nether and End generators, plus Nether portals.
  - Palette-encoded 16³ sections.
- **Simulation:**
  - The integrated server runs at 20 TPS, with at most 2 catch-up ticks and interpolated rendering.
  - Sky and block light with incremental BFS updates.
  - Fluids, fire, random ticks (crops, saplings, grass, leaf decay, cactus, cane), weather and lightning.
  - Day/night cycle, beds and sleeping.
- **Gameplay:**
  - Survival, Creative, Adventure and Spectator modes.
  - Mining with vanilla tool tiers and break times; loot.
  - 2×2 and 3×3 crafting (143 recipes), furnace smelting with fuel, chests.
  - Hunger, saturation and exhaustion; fall damage, drowning, lava, death and respawn; XP.
  - Bows and arrows, TNT, falling blocks.
- **Mobs:** cow, pig, sheep, chicken, zombie, skeleton, creeper and spider. They have A* pathfinding, natural spawning with caps and light rules, despawning, and data-driven models and walk animations.
- **Client:**
  - Multi-threaded software rasteriser with greedy meshing, smooth lighting and AO, cutout and translucent passes, fog, a sky with sun, moon, stars and clouds, rain and snow, particles, and a block-breaking overlay.
  - Vanilla-style HUD and F3 overlay; title, world list, create-world, options, pause, death, chat and container GUIs.
  - 44.1 kHz stereo mixer: original sounds from resource packs or the player's Minecraft assets (Ogg Vorbis, streamed music and loops), with procedural fallbacks.
- **Persistence:** region files with CRC32 per chunk. A corrupt chunk is quarantined and then regenerated. Saves are atomic `level.json` writes with a 45 s autosave.

## Repository layout (§0 required layout)

```
src/      game source (mc.util, mc.content, mc.core, mc.gen, mc.entity, mc.net, mc.server, mc.client, mc.Main)
data/     data pack: blocks, items, recipes, entities, biomes, sounds, textures, tuning, worldgen (JSON)
assets/   original assets: none are stored, because all textures/sounds are generated from data/ at startup
tests/    verification suite (mc.tests.TestRunner) + golden chunk hashes
third_party/jorbis/  JOrbis Ogg Vorbis decoder (LGPL 2.1, unmodified source + licence)
tools/    gen_data.py (data source of truth), package_release.py, make_jar.py, launcher/ (Minecraft.exe source)
DECISIONS.md   decision log        PLAYTEST.md   Tier 1 acceptance record
```

## Verification suite

Run it with `build.bat`, `./build.sh`, or `java -cp build/classes:build/tests -Dmc.data=data mc.tests.TestRunner`. It has 28 checks:
- **Registry completeness:** textures, loot, items, recipes, entities and biomes.
- **Seed determinism:**
  - Chunk hashes are compared across runs and across generation orders.
  - Golden hashes in `tests/golden_hashes.json` are checked for 4 seeds × 7 chunks.
- **Persistence:** codec round trip, save → reopen, and corrupt-region recovery.
- **Mining and harvest:** break times are checked against vanilla, e.g. stone 7.5 s by hand and 1.15 s with a wooden pickaxe, obsidian 9.4 s with a diamond pickaxe.
- **Physics:**
  - Speeds match vanilla: walk 4.317 b/s, sprint 5.612 b/s, sneak 1.295 b/s.
  - Jump apex is 1.2522 blocks; falling 10 blocks deals 7 damage.
- **Animation validator.**
- **Sound audit:**
  - Every referenced sound id exists.
  - Every sound synthesises audible, non-clipping PCM.
  - Resource-pack sounds: `sounds.json` semantics (weights, event references, volume/pitch, stream) and Ogg Vorbis decoding are checked against a generated test pack in `tests/fixtures/soundpack/`.
  - Broken packs are safe: corrupt `.ogg` files fail without leaking file handles, and cyclic event references resolve to silence.
- **Robustness:**
  - Commands reject bad arguments (huge `/give` counts, NaN/∞/out-of-range `/tp`, unknown effects, overflowing durations, invalid gamerule values).
  - The server ignores hostile packets (NaN look/move input, digging out of reach, bad hotbar/menu slots, creative give in survival).
  - XP removal stops at level 0, and the level-up sound is rate-limited like vanilla.
