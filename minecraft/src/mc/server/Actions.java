package mc.server;

import mc.content.*;
import mc.core.*;
import mc.entity.*;
import mc.gen.Features;
import mc.net.C2S;
import mc.util.AABB;

/** Server-side handling of client actions (Section 3.4 interaction rules, Section 6 item use). */
public final class Actions {
    private Actions() { }

    public static void handle(IntegratedServer s, C2S.Packet pk) {
        Player p = s.player;
        if (pk instanceof C2S.Respawn) { s.respawn(); return; }
        if (pk instanceof C2S.Chat) { Commands.chat(s, ((C2S.Chat) pk).text()); return; }
        if (pk instanceof C2S.SetViewDistance) { s.viewDistance = Math.max(2, Math.min(16, ((C2S.SetViewDistance) pk).chunks())); return; }
        if (pk instanceof C2S.SetPaused) { s.paused = ((C2S.SetPaused) pk).paused(); return; }
        if (p.dead) return;
        if (pk instanceof C2S.OpenInventory) { if (p.menu == p.invMenu) s.playerInventoryOpen = true; return; }
        if (pk instanceof C2S.Input) {
            C2S.Input in = (C2S.Input) pk;
            p.inForward = clamp(in.forward());
            p.inStrafe = clamp(in.strafe());
            p.inJump = in.jump();
            p.inSneak = in.sneak();
            p.inSprint = in.sprint();
            p.yaw = in.yaw();
            p.headYaw = in.yaw();
            p.pitch = Math.max(-90, Math.min(90, in.pitch()));
            if (p.sleeping && (in.sneak() || in.jump())) p.wake();
        } else if (pk instanceof C2S.Dig) {
            C2S.Dig d = (C2S.Dig) pk;
            if (p.menu == p.invMenu || true) p.dig(d.x(), d.y(), d.z());
            if (p.digProgress == 0 && p.age % 5 == 0) p.swing();
            p.swingTime = Math.max(p.swingTime, 2);
        } else if (pk instanceof C2S.StopDig) {
            p.stopDig();
        } else if (pk instanceof C2S.UseOn) {
            C2S.UseOn u = (C2S.UseOn) pk;
            useOn(s, p, u.x(), u.y(), u.z(), u.face(), u.hx(), u.hy(), u.hz());
        } else if (pk instanceof C2S.UseItem) {
            useItem(s, p);
        } else if (pk instanceof C2S.ReleaseUse) {
            p.stopUse();
        } else if (pk instanceof C2S.Attack) {
            Entity e = p.world.entityById(((C2S.Attack) pk).entityId());
            if (e != null && p.distSq(e) < 36) p.attack(e);
        } else if (pk instanceof C2S.Interact) {
            Entity e = p.world.entityById(((C2S.Interact) pk).entityId());
            if (e instanceof Animal && p.distSq(e) < 36) {
                ItemStack h = p.inventory.held();
                if (((Animal) e).interact(p, h)) { p.swing(); p.cleanupHands(); return; }
            }
            useItem(s, p);
        } else if (pk instanceof C2S.Select) {
            int sl = ((C2S.Select) pk).slot();
            if (sl >= 0 && sl < 9) { p.inventory.selected = sl; p.stopUse(); }
        } else if (pk instanceof C2S.Drop) {
            if (!p.isSpectator()) p.dropHeld(((C2S.Drop) pk).all());
        } else if (pk instanceof C2S.SwapHands) {
            ItemStack h = p.inventory.held();
            p.inventory.setHeld(p.inventory.offhand());
            p.inventory.setOffhand(h);
        } else if (pk instanceof C2S.Click) {
            C2S.Click c = (C2S.Click) pk;
            Menu m = c.windowId() == 0 ? p.invMenu : p.menu;
            if (m.windowId == c.windowId()) m.click(p, c.slot(), c.button(), c.shift());
        } else if (pk instanceof C2S.CloseMenu) {
            if (p.menu != p.invMenu) p.closeMenu();
            else p.invMenu.close(p);
            s.playerInventoryOpen = false;
        } else if (pk instanceof C2S.PickBlock) {
            C2S.PickBlock pb = (C2S.PickBlock) pk;
            pickBlock(p, p.world.getBlock(pb.x(), pb.y(), pb.z()));
        } else if (pk instanceof C2S.SetFlying) {
            if (p.mayFly()) p.flying = ((C2S.SetFlying) pk).flying() || p.isSpectator();
        } else if (pk instanceof C2S.CreativeGive) {
            if (!p.isCreative()) return;
            C2S.CreativeGive g = (C2S.CreativeGive) pk;
            Item it = Items.get(g.itemId());
            if (it != null) {
                ItemStack st = new ItemStack(it, Math.max(1, Math.min(it.maxStack, g.count())));
                if (p.inventory.held() == null) p.inventory.setHeld(st);
                else p.giveOrDrop(st);
            }
        }
    }

    public static void openInventory(IntegratedServer s) { s.playerInventoryOpen = true; }

    private static float clamp(float v) { return Math.max(-1, Math.min(1, v)); }

    private static void pickBlock(Player p, int state) {
        if (state == Blocks.AIR) return;
        Item it = Items.forBlockState(state);
        if (it == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.inventory.main[i];
            if (st != null && st.item == it) { p.inventory.selected = i; return; }
        }
        if (p.isCreative()) {
            int slot = p.inventory.selected;
            for (int i = 0; i < 9; i++) if (p.inventory.main[i] == null) { slot = i; break; }
            p.inventory.main[slot] = new ItemStack(it, 1);
            p.inventory.selected = slot;
        } else {
            for (int i = 9; i < 36; i++) {
                ItemStack st = p.inventory.main[i];
                if (st != null && st.item == it) {
                    ItemStack h = p.inventory.held();
                    p.inventory.setHeld(st);
                    p.inventory.main[i] = h;
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ right click on a block
    static void useOn(IntegratedServer srv, Player p, int x, int y, int z, int face, double hx, double hy, double hz) {
        World w = p.world;
        if (p.isSpectator()) return;
        if (p.distSq(x + 0.5, y + 0.5, z + 0.5) > (p.reach() + 2) * (p.reach() + 2)) return;
        int s = w.getBlock(x, y, z);
        Block b = Blocks.block(s);
        ItemStack held = p.inventory.held();
        String inter = b.extraStr("interact");
        boolean bypass = p.sneaking && held != null;
        if (inter != null && !bypass) {
            if (interactBlock(srv, p, w, x, y, z, s, inter, held)) { p.swing(); return; }
        }
        if (held == null) return;
        Item it = held.item;
        // tools acting on blocks
        if ("hoe".equals(it.toolKind) && (s == Blocks.GRASS_BLOCK || s == Blocks.DIRT || b.id.equals("minecraft:coarse_dirt")) && face != 0
                && w.getBlock(x, y + 1, z) == Blocks.AIR) {
            w.setBlock(x, y, z, b.id.equals("minecraft:coarse_dirt") ? Blocks.DIRT : Blocks.FARMLAND);
            w.playSound("block.gravel.step", x + 0.5, y + 0.5, z + 0.5, 1f, 1f);
            p.damageHeld(1);
            p.swing();
            return;
        }
        if (it.id.equals("minecraft:bone_meal")) {
            if (boneMeal(w, x, y, z, s)) {
                if (!p.isCreative()) held.count--;
                p.cleanupHands();
                w.particles("happy", x + 0.5, y + 0.5, z + 0.5, 10, 0);
                p.swing();
            }
            return;
        }
        if ("ignite".equals(it.use)) {
            if (s == Blocks.TNT) { primeTnt(w, x, y, z, p); p.damageHeld(1); return; }
            int ax = adj(x, face, 4, 5), ay = adj(y, face, 0, 1), az = adj(z, face, 2, 3);
            if (w.getBlock(ax, ay, az) == Blocks.AIR) {
                w.playSound("item.flintandsteel.use", ax + 0.5, ay + 0.5, az + 0.5, 1f, 1f);
                if (!w.tryCreatePortal(ax, ay, az)) w.setBlock(ax, ay, az, Blocks.FIRE);
                p.damageHeld(1);
                p.swing();
            }
            return;
        }
        if ("bucket".equals(it.use)) { useBucket(p, w, held); return; }
        if (it.spawns != null) {
            EntityType t = EntityTypes.get(it.spawns);
            if (t != null) {
                Entity e = EntityFactory.create(w, t);
                if (e != null) {
                    e.setPos(adj(x, face, 4, 5) + 0.5, adj(y, face, 0, 1), adj(z, face, 2, 3) + 0.5);
                    e.yaw = p.yaw + 180;
                    if (e instanceof Mob) ((Mob) e).persistent = true;
                    w.addEntity(e);
                    if (!p.isCreative()) held.count--;
                    p.cleanupHands();
                }
            }
            return;
        }
        if (it.placesState >= 0) {
            if (place(p, w, x, y, z, face, hx, hy, hz, held)) p.swing();
            return;
        }
        useItem(srv, p);
    }

    private static int adj(int v, int face, int neg, int pos) { return v + (face == neg ? -1 : face == pos ? 1 : 0); }

    private static boolean interactBlock(IntegratedServer srv, Player p, World w, int x, int y, int z, int s, String inter, ItemStack held) {
        switch (inter) {
            case "crafting":
                p.openMenu(Menu.crafting(p, x, y, z));
                return true;
            case "chest": {
                BlockEntity be = w.blockEntity(x, y, z);
                if (be == null) return false;
                if (Blocks.opaqueCube[w.getBlock(x, y + 1, z)]) return true;
                p.openMenu(Menu.chest(p, be));
                w.playSound("block.wood.place", x + 0.5, y + 0.5, z + 0.5, 0.5f, 1.1f);
                return true;
            }
            case "furnace": {
                BlockEntity be = w.blockEntity(x, y, z);
                if (be == null) return false;
                p.openMenu(Menu.furnace(p, be));
                return true;
            }
            case "tnt":
                if (held != null && "ignite".equals(held.item.use)) {
                    primeTnt(w, x, y, z, p);
                    p.damageHeld(1);
                    return true;
                }
                return false;
            case "bed": {
                int hx = x, hz = z;
                if ("foot".equals(Blocks.value(s, "part"))) {
                    int[] d = World.facingVec(Blocks.value(s, "facing"));
                    hx += d[0];
                    hz += d[1];
                }
                String r = p.trySleep(hx, y, hz);
                if ("explode".equals(r)) {
                    w.setBlock(x, y, z, Blocks.AIR);
                    w.setBlock(hx, y, hz, Blocks.AIR);
                    w.explode(null, hx + 0.5, y + 0.5, hz + 0.5, (float) Registries.tune("bed_explosion_strength"), true);
                } else if (r != null) srv.message(r);
                return true;
            }
            default:
                return false;
        }
    }

    private static void primeTnt(World w, int x, int y, int z, Entity by) {
        w.setBlock(x, y, z, Blocks.AIR);
        PrimedTnt t = new PrimedTnt(w, by);
        t.setPos(x + 0.5, y, z + 0.5);
        w.addEntity(t);
    }

    private static boolean boneMeal(World w, int x, int y, int z, int s) {
        Block b = Blocks.block(s);
        if (b.id.endsWith("_sapling")) {
            if (w.rng.nextFloat() < 0.45f) {
                String wood = b.id.replace("minecraft:", "").replace("_sapling", "");
                w.setBlock(x, y, z, Blocks.AIR, World.SEND);
                if (!Features.growTree(w, wood, x, y, z, w.rng)) w.setBlock(x, y, z, s, World.SEND);
            }
            return true;
        }
        if (b.shape == Block.SHAPE_CROP && b.propIndex("age") >= 0) {
            int age = Blocks.prop(s, "age");
            if (age >= 7) return false;
            w.setBlock(x, y, z, Blocks.with(s, "age", Math.min(7, age + 2 + w.rng.nextInt(4))));
            return true;
        }
        if (s == Blocks.GRASS_BLOCK) {
            for (int i = 0; i < 48; i++) {
                int px = x + w.rng.nextInt(7) - 3, pz = z + w.rng.nextInt(7) - 3, py = y + w.rng.nextInt(3) - 1;
                if (w.getBlock(px, py, pz) == Blocks.GRASS_BLOCK && w.getBlock(px, py + 1, pz) == Blocks.AIR)
                    w.setBlock(px, py + 1, pz, w.rng.nextInt(8) == 0 ? Blocks.base(w.rng.nextBoolean() ? "minecraft:dandelion" : "minecraft:poppy") : Blocks.GRASS);
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ buckets
    private static void useBucket(Player p, World w, ItemStack held) {
        double ex = p.x, ey = p.eyeY(), ez = p.z;
        LivingEntity.Vec l = p.look();
        boolean empty = held.item.id.equals("minecraft:bucket");
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0, px = 0, py = 0, pz = 0;
        for (double t = 0; t < p.reach(); t += 0.05) {
            int bx = (int) Math.floor(ex + l.x * t), by = (int) Math.floor(ey + l.y * t), bz = (int) Math.floor(ez + l.z * t);
            if (bx == lx && by == ly && bz == lz) continue;
            px = lx; py = ly; pz = lz;
            lx = bx; ly = by; lz = bz;
            int s = w.getBlock(bx, by, bz);
            if (empty && Blocks.fluid[s] && Blocks.prop(s, "level") == 0) {
                w.setBlock(bx, by, bz, Blocks.AIR);
                String filled = Blocks.isWater(s) ? "minecraft:water_bucket" : "minecraft:lava_bucket";
                w.playSound("item.bucket.fill", bx + 0.5, by + 0.5, bz + 0.5, 1f, 1f);
                p.replaceHeld(held, new ItemStack(Items.get(filled), 1));
                p.swing();
                return;
            }
            if (!empty && (Blocks.solidCollision[s] || (Blocks.fluid[s] && Blocks.prop(s, "level") == 0))) {
                if (px == Integer.MIN_VALUE) return;
                int tx = Blocks.replaceable[s] && !Blocks.fluid[s] ? bx : px, ty = Blocks.replaceable[s] && !Blocks.fluid[s] ? by : py,
                        tz = Blocks.replaceable[s] && !Blocks.fluid[s] ? bz : pz;
                int target = w.getBlock(tx, ty, tz);
                if (!(target == Blocks.AIR || Blocks.replaceable[target] || Blocks.fluid[target])) return;
                int fluid = held.item.placesState;
                if (w.dim.ultrawarm && fluid == Blocks.WATER) {
                    w.playSound("block.fire.extinguish", tx + 0.5, ty + 0.5, tz + 0.5, 0.5f, 2.6f);
                } else {
                    if (target != Blocks.AIR && !Blocks.fluid[target]) w.destroyBlock(tx, ty, tz, true, null, p);
                    w.setBlock(tx, ty, tz, fluid);
                    w.schedule(tx, ty, tz, 1);
                    w.playSound("item.bucket.empty", tx + 0.5, ty + 0.5, tz + 0.5, 1f, 1f);
                }
                if (!p.isCreative()) p.replaceHeld(held, new ItemStack(Items.get("minecraft:bucket"), 1));
                p.swing();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ placement
    static boolean place(Player p, World w, int x, int y, int z, int face, double hx, double hy, double hz, ItemStack held) {
        if (p.gameMode == Player.ADVENTURE) return false;
        int clicked = w.getBlock(x, y, z);
        int state = held.item.placesState;
        Block nb = Blocks.block(state);
        int tx = x, ty = y, tz = z;
        if (!(Blocks.replaceable[clicked] && Blocks.block(clicked) != nb && !Blocks.fluid[clicked] || clicked == Blocks.AIR)) {
            tx = adj(x, face, 4, 5);
            ty = adj(y, face, 0, 1);
            tz = adj(z, face, 2, 3);
        }
        if (ty < 0 || ty > 255) return false;
        int target = w.getBlock(tx, ty, tz);
        if (!(target == Blocks.AIR || Blocks.replaceable[target] || Blocks.fluid[target])) return false;
        if (Blocks.fluid[target] && nb.shape != Block.SHAPE_CUBE && nb.shape != Block.SHAPE_SLAB) {
            if (nb.has("support")) return false;
        }
        // orientation
        String facing = horizontalFacing(p.yaw);
        if (nb.propIndex("axis") >= 0 && nb.shape != Block.SHAPE_PORTAL) state = Blocks.with(state, "axis", face <= 1 ? "y" : face <= 3 ? "z" : "x");
        if (nb.propIndex("facing") >= 0) {
            if (nb.shape == Block.SHAPE_LADDER) {
                if (face <= 1) return false;
                state = Blocks.with(state, "facing", face == 2 ? "north" : face == 3 ? "south" : face == 4 ? "west" : "east");
            } else if (nb.shape == Block.SHAPE_BED) {
                state = Blocks.with(state, "facing", facing);
            } else {
                state = Blocks.with(state, "facing", opposite(facing));
            }
        }
        if (!w.isSupported(tx, ty, tz, state)) return false;
        if (nb.shape == Block.SHAPE_BED) {
            int[] d = World.facingVec(facing);
            int hx2 = tx + d[0], hz2 = tz + d[1];
            int ht = w.getBlock(hx2, ty, hz2);
            if (!(ht == Blocks.AIR || Blocks.replaceable[ht])) return false;
            if (!Blocks.solidCollision[w.getBlock(tx, ty - 1, tz)] || !Blocks.solidCollision[w.getBlock(hx2, ty - 1, hz2)]) return false;
            w.setBlock(tx, ty, tz, Blocks.with(state, "part", "foot"));
            w.setBlock(hx2, ty, hz2, Blocks.with(state, "part", "head"));
        } else {
            // entity collision check
            if (Blocks.solidCollision[state]) {
                java.util.List<AABB> boxes = new java.util.ArrayList<>();
                Physics.boxes(state, tx, ty, tz, boxes);
                for (AABB bb : boxes)
                    for (Entity e : w.entitiesIn(bb, null))
                        if (e instanceof LivingEntity && !((LivingEntity) e).dead && !(e instanceof Player && ((Player) e).isSpectator())) return false;
            }
            if (Blocks.fluid[target] && !Blocks.fluid[state] && !nb.has("fluid")) { /* displace fluid */ }
            w.setBlock(tx, ty, tz, state);
        }
        w.playSound("block." + nb.sound + ".place", tx + 0.5, ty + 0.5, tz + 0.5, 1f, 0.8f);
        if (!p.isCreative()) {
            held.count--;
            p.cleanupHands();
        }
        return true;
    }

    public static String horizontalFacing(float yaw) {
        int d = Math.floorMod(Math.round(yaw / 90f), 4);
        return d == 0 ? "south" : d == 1 ? "west" : d == 2 ? "north" : "east";
    }

    private static String opposite(String f) {
        switch (f) {
            case "north": return "south";
            case "south": return "north";
            case "west": return "east";
            default: return "west";
        }
    }

    // ------------------------------------------------------------------ right click in air
    static void useItem(IntegratedServer srv, Player p) {
        ItemStack held = p.inventory.held();
        if (held == null || p.isSpectator()) return;
        Item it = held.item;
        if (it.isFood()) {
            boolean always = it.id.contains("golden_apple");
            if (p.food < 20 || always || p.isCreative()) p.startUse(held);
            return;
        }
        if ("drink_milk".equals(it.use)) { p.startUse(held); return; }
        if (it.id.equals("minecraft:bow")) {
            if (p.isCreative() || p.inventory.count(Items.get("minecraft:arrow")) > 0) p.startUse(held);
            return;
        }
        if ("bucket".equals(it.use)) { useBucket(p, p.world, held); return; }
        if (it.isArmor()) {
            int slot = it.armorSlot;
            if (slot >= 0 && slot < 4 && p.inventory.armor[slot] == null) {
                p.inventory.armor[slot] = held.copy(1);
                held.count--;
                p.cleanupHands();
                p.world.playSound("block.metal.place", p.x, p.y, p.z, 0.6f, 1.2f);
            }
        }
    }
}
