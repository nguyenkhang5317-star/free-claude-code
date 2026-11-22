# DECISIONS.md — Minecraft 1.16.5 Rebuild

> Append-only decision log per Appendix L.2. Format: date, milestone, section, ambiguity/conflict, decision, rationale, affected test.

## Platform Decisions

- **2026-11-22, M1, §1.4.1 — Stack: Python (server + data validators) + JavaScript/Three.js (client).**
  Rationale: Python matches repo runtime (3.14, pytest) and keeps data pipeline testable; JS/Three.js gives immediate browser playability and LIVE PREVIEW without native builds. Hot loop (tick + meshing) runs in JS with fixed 20 TPS; Python owns registries, validation, and static serving. No asset copying — all textures procedurally generated from Appendix F palettes; all audio synthesized via WebAudio.
  Affected: `minecraft/server.py`, `minecraft/client/js/*`, `minecraft/tests/*`.

- **2026-11-22, M1, §2.1 — Layering: data-first registries in `minecraft/data/*.json`, frozen at boot.**
  Rationale: Spec §2.4 requires data-driven content. Client loads JSON registries; server validates them. Unknown identifiers fail loudly.
  Affected: registry validators.

- **2026-11-22, M1, §2.3 — Chunk storage: 16×16 columns, 16³ sections, palette-encoded in JS (Map + Uint16).**
  Rationale: Full palette bit-packing deferred; JS Map per section is simpler and fast enough for 8-chunk render distance. Documented as deviation from §3.2 packed arrays; memory still < 2 GB at default distance.
  Affected: `world.js`.

- **2026-11-22, M1, §1.4.5 — Fixed tick 20 TPS with render interpolation.**
  Rationale: Server tick decoupled from rAF; player physics stepped at fixed dt.
  Affected: `main.js`.

- **2026-11-22, M1, §1.2 Asset policy — Original procedural textures/audio only.**
  Rationale: No Mojang assets. Textures generated on canvas from Appendix F hex ramps; sounds synthesized (oscillator + noise) per Appendix S durations.
  Affected: `blocks.js`, `audio.js`.

## Ambiguities Resolved to Vanilla 1.16.5

- **2026-11-22, M1, §4.1 — Noise: value-gradient Perlin with quintic interpolation, per-field salts.**
  Rationale: Spec allows FastSimplex or improved Perlin. Chose seeded value noise for determinism and simplicity.
  Affected: `worldgen.js` determinism test.

- **2026-11-22, M1, §5.4 — Portal search radius 128 blocks (Overworld→Nether scaled 8:1).**
  Rationale: Spec gives 17×17 column search; widened to 128 for playability, noted as deviation.
  Affected: `dimensions.js`.

- **2026-11-22, M1, §12 — Animations as data (keyframes in `mobs.json`), interpolated in JS.**
  Rationale: Spec §1.2 requires data, not hardcoded loops.
  Affected: `mobs.js` animation validator.

## Deviations (permitted where spec says best-effort)

- Particle fidelity and shaders (§14) best-effort: simple Points-based particles, no post-processing.
- Multiplayer beyond local integrated server (§1.4.3 Tier 4): single-player only; client-server split simulated in-process.
- Region file persistence (§2.6): localStorage chunk snapshots, not Anvil format.

## Version Contract

- Target: Minecraft Java Edition 1.16.5 only. Post-1.16.5 content (deepslate, copper, frogs, etc.) omitted per Appendix AF.2. Registry manifest hash in `data/manifest.json`.

## Implementation status 2026-11-22
- server.py: ThreadingHTTPServer, serves client/, /data/*.json, POST /api/save + /api/health. Port 8000.
- client/js/world.js: seeded value-gradient Perlin (quintic), heightmap SEA=28, caves, ores, trees, village grid 34/8, raycast DDA, serialize.
- client/js/main.js: Three.js voxel meshing (face culling, vertex colors), fixed tick 20 TPS decoupled render, player physics gravity 0.08/damping 0.98, mining/placing, inventory 36 + hotbar 9, crafting recipes, day/night, hunger/health, commands (/seed /time /give /tp /spawn /gamemode /kill /weather /help), death/respawn.
- client/js/mobs.js: 15 spawn types, wander/hostile AI, box models, drops/XP.
- client/js/audio.js: WebAudio synth, sound IDs block.*.break/place, entity.*.hurt, etc.
- client/js/textures.js: procedural canvas atlas (original, Appendix F palettes).
- tests/test_registry.py: validates 103 blocks / 165 items / 69 mobs + file existence.
- Deviations: HEIGHT=64 (not 256) for browser perf; render distance 4 chunks; texture atlas fallback to stone for unmapped tiles; no Anvil persistence (localStorage/API save only).

## 2026-11-22 — Remove web mode
- User requested app-only, no web. Deleted minecraft/client/, minecraft/server.py, run-launcher scripts.
- Kept: app.py (native Tkinter), launcher.py (native), data/, tests/, build-exe scripts.
- Download via GitHub Release zip; .exe built on Windows via build-exe.bat.
