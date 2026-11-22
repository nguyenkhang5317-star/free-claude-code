# PLAYTEST.md: Tier 1 acceptance record (§17.1)

**Build:** `minecraft/` on branch `arena/105b666b-free-claude-code`, 2026-11-22.
**Environment:** headless Linux sandbox with 2 vCPUs, Java 25 runtime, classes compiled for Java 17. The sandbox has no display.

**How this was checked:**
- Every Tier 1 item was exercised through the real code paths:
  - C2S packets into the integrated server;
  - the client `drawFrame` rendering offscreen;
  - the automated suite in `tests/`.
- Window-only behaviour could not be clicked by hand here, because the sandbox has no display. That covers mouse capture, keyboard focus and fullscreen. These rows are marked **manual** below and should be confirmed on Windows.

| # | Acceptance item | Result | Evidence |
|---|---|---|---|
| 1 | Create a world from the title screen (name, seed, mode) and spawn on dry land | ✅ | `IntegratedServer.create` takes about 1.5 s. The spiral spawn search avoids ocean, river, beach and mountains. Offscreen frames of the title, world and inventory screens were inspected. |
| 2 | Movement: walk, sprint, sneak, jump, fall damage, swimming, creative flight | ✅ | Tests `physics:*` measured walk 4.32, sprint 5.6 and sneak 1.3 b/s, a jump apex of 1.25 and 7 damage from a 10-block fall. Flight toggle via `SetFlying`. |
| 3 | Break and place blocks with correct tool times and drops | ✅ | Tests `mining times match vanilla` and `harvest tiers`. A furnace placed via `UseOn` faced the player (`facing=south`). |
| 4 | Crafting (2×2 and 3×3) | ✅ | Logs → 4 planks through the inventory GUI clicks, shown on screen. Test `recipes: vanilla spot checks` covers table, sticks, stone pickaxe and furnace. |
| 5 | Smelting with fuel, output pickup and XP | ✅ | Furnace opened via `UseOn`, then ore and coal moved in with `Click` packets. After 650 ticks: 3 iron ingots, furnace `lit=true` while burning, and 2 XP collected from orbs. |
| 6 | Day/night cycle, sleeping in a bed sets spawn and skips the night | ✅ | Bed placed as foot + head. At time 13005 the player slept. After 100 ticks: morning (time 12, day 1), "Respawn point set", and respawn beside the bed. |
| 7 | Hunger, starvation, death screen, respawn | ✅ | Food 0 on Hard gave −5 HP in 400 ticks. `/kill` set dead=true, then `Respawn` restored 20 HP and 20 food. The death screen draws in `Screens.death`. |
| 8 | Save and quit, then reopen the world with blocks, inventory, time and position intact | ✅ | Test `world save -> reopen round trip`. Corrupt chunks are quarantined and regenerated (test `corrupt region chunk...`). |
| 9 | Performance: 20 TPS server, playable frame rate | ⚠️ | The server tick takes 3–6 ms (budget 50 ms). Client frame at 640×360 render scale with 2 raster threads: about 40 ms (projection 13 + raster 27), so about 25 fps on the 2-vCPU sandbox. The rasteriser scales with cores, so a 6–8 thread desktop should reach roughly 60 fps at default settings. Lower *Render Scale* / *Render Distance* in Options if needed. |
| 10 | Mouse look capture, key bindings, F11 fullscreen, F2 screenshot | **manual** | The code paths compile and the event handling was reviewed. Needs a real window. |
| 11 | Audio output | **manual** | All 181 sounds synthesise audible, non-clipping PCM (sound audit). Device playback needs real hardware. |

**Issues found and fixed during this playtest:**
- **Walk speed:** it was 5.73 b/s because the ground acceleration cubed the wrong slipperiness. It is now vanilla's 4.317 b/s (see DECISIONS).
- **Dangling sound ids:** `block.chest.place` and `block.portal.break` were remapped to their material groups.
- **Chat:** opening chat with T or / inserted the key character into the text box. That character is now swallowed.
- **F3 overlay:** it crashed in offscreen mode (canvas size) and its columns overlapped. It now uses the GUI size and half scale.
- **Title splash:** the splash text overlapped the subtitle. It now rotates around its own centre.
