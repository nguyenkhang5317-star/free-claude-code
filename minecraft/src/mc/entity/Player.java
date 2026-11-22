package mc.entity;

import mc.content.*;
import mc.core.*;
import mc.util.Json;

import java.util.*;

/**
 * The player (Sections 3, 6, 7): inventory, hunger/saturation/exhaustion, experience, game modes, flying,
 * mining progress, attack cooldown, item use (eating, bow), sleeping and portal timers. Movement input arrives
 * from the client via packets; the simulation is server-authoritative.
 */
public final class Player extends LivingEntity {
    public static final int SURVIVAL = 0, CREATIVE = 1, ADVENTURE = 2, SPECTATOR = 3;

    public String name = "Player";
    public final Inventory inventory = new Inventory();
    public final ItemStack[] craft2 = new ItemStack[4];
    public Menu invMenu, menu;
    public ItemStack carried;
    public int gameMode = SURVIVAL;
    public boolean flying;

    public int food = 20;
    public float saturation = 5, exhaustion;
    private int foodTimer;
    public int xpLevel, xpTotal, xpCooldown;
    public float xpProgress;

    // input from client
    public float inForward, inStrafe;
    public boolean inJump, inSneak, inSprint;

    // mining
    public boolean digging;
    public int digX, digY, digZ, digIdle, creativeBreakDelay;
    public float digProgress;

    public int attackTicker = 100;
    public ItemStack useItem;
    public int useTicks;

    public boolean sleeping;
    public int sleepTimer, bedX, bedY, bedZ;
    public boolean hasSpawn;
    public int spawnX, spawnY, spawnZ;
    public String spawnDim = "minecraft:overworld";

    public int portalTime;
    public boolean wantsPortal;
    public String deathMessage;
    public double walkDist;
    private double lastStepDist;

    public Player(World w) {
        super(EntityTypes.PLAYER, w);
        stepHeight = Registries.tune("step_height");
        speedAttr = Registries.tune("walk_speed");
        invMenu = Menu.inventory(this);
        menu = invMenu;
    }

    public boolean isCreative() { return gameMode == CREATIVE; }

    public boolean isSpectator() { return gameMode == SPECTATOR; }

    public boolean attackable() { return !dead && (gameMode == SURVIVAL || gameMode == ADVENTURE); }

    public boolean mayFly() { return gameMode == CREATIVE || gameMode == SPECTATOR; }

    public double reach() { return isCreative() ? Registries.tune("reach_creative") : Registries.tune("reach_survival"); }

    public void setGameMode(int m) {
        gameMode = m;
        if (!mayFly()) flying = false;
        if (m == SPECTATOR) flying = true;
        noClip = m == SPECTATOR;
    }

    @Override
    public double eyeY() { return y + (sneaking && !flying ? Registries.tune("player_eye") - Registries.tune("sneak_eye_drop") : Registries.tune("player_eye")); }

    @Override
    protected boolean isFlying() { return flying; }

    @Override
    protected boolean isInvulnerableTo(DamageSource src) {
        if (src == DamageSource.KILL || src == DamageSource.VOID) return gameMode == SPECTATOR || (gameMode == CREATIVE && src != DamageSource.VOID);
        return gameMode == CREATIVE || gameMode == SPECTATOR;
    }

    @Override
    public int armorValue() { return inventory.armorPoints(); }

    @Override
    public double armorToughness() { return inventory.armorToughness(); }

    @Override
    protected void damageArmor(float amount) {
        int d = Math.max(1, (int) (amount / 4));
        for (int i = 0; i < 4; i++) {
            ItemStack s = inventory.armor[i];
            if (s == null || s.item.durability <= 0) continue;
            s.damage += d;
            if (s.damage >= s.item.durability) {
                inventory.armor[i] = null;
                world.playSound("entity.item.break", x, y, z, 0.8f, 0.8f + rng.nextFloat() * 0.4f);
            }
        }
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        if (sleeping) {
            sleepTimer = Math.min(100, sleepTimer + 1);
            vx = vz = 0;
        }
        super.tick();
        if (dead) return;
        if (xpCooldown > 0) xpCooldown--;
        attackTicker++;
        if (creativeBreakDelay > 0) creativeBreakDelay--;
        if (menu != invMenu && !menu.stillValid(this)) closeMenu();
        tickFood();
        tickUse();
        tickDig();
        // pickups
        if (!isSpectator()) {
            for (Entity e : world.entitiesIn(bbox().grow(1.0).expand(0, 0.5, 0), this)) if (e instanceof ItemEntity) ((ItemEntity) e).tryPickup(this);
        }
        // portal
        int feet = world.getBlock((int) Math.floor(x), (int) Math.floor(y + 0.2), (int) Math.floor(z));
        if (Blocks.is(feet, Blocks.NETHER_PORTAL)) {
            portalTime++;
            if (portalCooldown == 0 && portalTime >= (isCreative() ? 1 : Registries.tunei("portal_ticks"))) {
                wantsPortal = true;
                portalTime = 0;
            }
        } else portalTime = Math.max(0, portalTime - 4);
        // footsteps (block family step sound)
        if (onGround && !sneaking && walkDist - lastStepDist > 1.0 / 0.6) { // vanilla: one step per 1/0.6 blocks
            lastStepDist = walkDist;
            int below = world.getBlock((int) Math.floor(x), (int) Math.floor(y - 0.2), (int) Math.floor(z));
            if (below != Blocks.AIR) world.playSound("block." + Blocks.block(below).sound + ".step", x, y, z, 0.15f, 1f);
        }
    }

    @Override
    protected void aiStep() {
        if (sleeping) { moveForward = moveStrafe = 0; jumping = false; travel(0, 0); return; }
        float f = inForward, s = inStrafe;
        sneaking = inSneak && !flying;
        sneakSafe = sneaking;
        if (sneaking) { f *= Registries.tune("sneak_multiplier"); s *= Registries.tune("sneak_multiplier"); }
        if (useItem != null) { f *= 0.2f; s *= 0.2f; }
        boolean canSprint = (food > 6 || mayFly()) && !sneaking && useItem == null && inForward > 0.8f;
        if (inSprint && canSprint) sprinting = true;
        if (!canSprint || horizontalCollision || inForward <= 0) sprinting = false;
        moveForward = f;
        moveStrafe = s;
        double ox = x, oz = z, oy = y;
        if (flying) {
            if (inJump) vy += Registries.tune("creative_fly_vertical");
            if (inSneak) vy -= Registries.tune("creative_fly_vertical");
            jumping = false;
            travel(s, f);
            if (onGround && gameMode != SPECTATOR) flying = false;
        } else {
            jumping = inJump;
            super.aiStep();
        }
        double dx = x - ox, dz = z - oz, dy = y - oy;
        double d = Math.sqrt(dx * dx + dz * dz);
        walkDist += d;
        if (inWater && !flying) exhaust((float) (Math.sqrt(d * d + dy * dy) * Registries.tune("exhaustion_swim_per_m")));
        else if (sprinting && onGround) exhaust((float) (d * Registries.tune("exhaustion_sprint_per_m")));
    }

    @Override
    public void jump() {
        super.jump();
        exhaust((float) (sprinting ? Registries.tune("exhaustion_sprint_jump") : Registries.tune("exhaustion_jump")));
    }

    public void exhaust(float a) {
        if (mayFly()) return;
        exhaustion = Math.min(40, exhaustion + a);
    }

    private void tickFood() {
        Level l = world.level;
        if (l.difficulty == 0) {
            if (age % 20 == 0 && health < maxHealth && l.rule("naturalRegeneration")) heal(1);
            if (age % 10 == 0 && food < 20) food++;
        }
        if (exhaustion > 4) {
            exhaustion -= 4;
            if (saturation > 0) saturation = Math.max(0, saturation - 1);
            else if (l.difficulty > 0) food = Math.max(0, food - 1);
        }
        boolean regen = l.rule("naturalRegeneration");
        if (regen && saturation > 0 && food >= 20 && health < maxHealth) {
            if (++foodTimer >= Registries.tunei("saturated_regen_interval")) {
                float a = Math.min(saturation, 6);
                heal(a / 6f);
                exhaust(a);
                foodTimer = 0;
            }
        } else if (regen && food >= Registries.tunei("regen_food_threshold") && health < maxHealth) {
            if (++foodTimer >= Registries.tunei("regen_interval")) {
                heal(1);
                exhaust((float) Registries.tune("exhaustion_regen"));
                foodTimer = 0;
            }
        } else if (food <= 0) {
            if (++foodTimer >= Registries.tunei("starve_interval")) {
                if (health > 10 || l.difficulty >= 3 || (health > 1 && l.difficulty == 2)) hurt(DamageSource.STARVE, 1);
                foodTimer = 0;
            }
        } else foodTimer = 0;
    }

    @Override
    protected void onHurt(DamageSource src, float amount) {
        exhaust((float) Registries.tune("exhaustion_damage"));
        if (sleeping) wake();
    }

    // ------------------------------------------------------------------ item use (eat / bow)
    public void startUse(ItemStack s) {
        if (s == null) return;
        useItem = s;
        useTicks = 0;
    }

    public void stopUse() {
        if (useItem != null && useItem.item.id.equals("minecraft:bow")) releaseBow();
        useItem = null;
        useTicks = 0;
    }

    private void tickUse() {
        if (useItem == null) return;
        if (useItem != inventory.held() && useItem != inventory.offhand()) { useItem = null; return; }
        useTicks++;
        Item it = useItem.item;
        if (it.isFood() || "drink_milk".equals(it.use)) {
            int dur = it.isFood() ? it.eatTicks : 32;
            if (useTicks % 4 == 0 && useTicks < dur) world.playSound("entity.generic.eat", x, y, z, 0.5f, 1f + (rng.nextFloat() - rng.nextFloat()) * 0.2f);
            if (useTicks >= dur) finishUse();
        }
    }

    private void finishUse() {
        ItemStack s = useItem;
        Item it = s.item;
        if ("drink_milk".equals(it.use)) {
            effects.clear();
            absorption = 0;
            replaceHeld(s, new ItemStack(Items.get("minecraft:bucket"), 1));
        } else if (it.isFood()) {
            food = Math.min(20, food + it.foodHunger);
            saturation = (float) Math.min(food, saturation + it.foodSaturation);
            for (Map<String, Object> e : it.foodEffects) {
                if (rng.nextDouble() < Json.num(e, "chance", 1)) addEffect(Json.str(e, "effect", "regeneration"), (int) (Json.num(e, "seconds", 5) * 20), Json.integer(e, "amp", 0));
            }
            world.playSound("entity.player.burp", x, y, z, 0.5f, rng.nextFloat() * 0.1f + 0.9f);
            if (!isCreative()) {
                s.count--;
                if (it.id.equals("minecraft:mushroom_stew")) replaceHeld(s, new ItemStack(Items.get("minecraft:bowl"), 1));
                cleanupHands();
            }
        }
        useItem = null;
        useTicks = 0;
    }

    private void releaseBow() {
        Item arrowItem = Items.get("minecraft:arrow");
        boolean free = isCreative();
        if (!free && inventory.count(arrowItem) <= 0) return;
        float t = useTicks / 20f;
        float power = (t * t + t * 2) / 3f;
        if (power < 0.1f) return;
        if (power > 1) power = 1;
        Arrow a = new Arrow(world, this);
        a.setPos(x, eyeY() - 0.1, z);
        LivingEntity.Vec l = look();
        a.shoot(l.x, l.y, l.z, power * 3.0, 1.0);
        a.crit = power >= 1;
        a.pickupAllowed = !free;
        a.vx += vx; a.vz += vz;
        if (!onGround) a.vy += vy;
        world.addEntity(a);
        world.playSound("entity.arrow.shoot", x, y, z, 1f, 1f / (rng.nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        if (!free) {
            for (int i = 0; i < 36; i++) {
                ItemStack st = inventory.main[i];
                if (st != null && st.item == arrowItem) { if (--st.count <= 0) inventory.main[i] = null; break; }
            }
            damageHeld(1);
        }
    }

    // ------------------------------------------------------------------ mining (Appendix CH)
    public void dig(int x, int y, int z) {
        if (isSpectator() || dead) return;
        if (digging && (x != digX || y != digY || z != digZ)) digProgress = 0;
        if (!digging) digProgress = 0;
        digging = true;
        digX = x; digY = y; digZ = z;
        digIdle = 0;
    }

    public void stopDig() { digging = false; digProgress = 0; }

    private void tickDig() {
        if (!digging) { if (++digIdle > Registries.tunei("mining_reset_ticks")) digProgress = 0; return; }
        if (++digIdle > Registries.tunei("mining_reset_ticks")) { digging = false; digProgress = 0; return; }
        int s = world.getBlock(digX, digY, digZ);
        if (s == Blocks.AIR || Blocks.fluid[s]) { digging = false; digProgress = 0; return; }
        if (distSq(digX + 0.5, digY + 0.5, digZ + 0.5) > (reach() + 1.5) * (reach() + 1.5)) { digging = false; return; }
        if (isCreative()) {
            ItemStack h = inventory.held();
            if (creativeBreakDelay == 0 && (h == null || !"sword".equals(h.item.toolKind))) {
                world.destroyBlock(digX, digY, digZ, false, null, this);
                creativeBreakDelay = 5;
            }
            digging = false;
            return;
        }
        if (gameMode == ADVENTURE) return;
        double dpt = Mining.damagePerTick(s, inventory.held(), onGround, submerged);
        if (dpt <= 0) return;
        digProgress += dpt;
        if (age % 4 == 0) world.playSound("block." + Blocks.block(s).sound + ".hit", digX + 0.5, digY + 0.5, digZ + 0.5, 0.25f, 0.5f);
        if (digProgress >= 1f) {
            ItemStack tool = inventory.held();
            boolean harvest = Mining.canHarvest(s, tool);
            world.destroyBlock(digX, digY, digZ, harvest, tool, this);
            exhaust((float) Registries.tune("exhaustion_mine"));
            if (Blocks.block(s).hardness > 0 && tool != null && tool.item.durability > 0) damageHeld(tool.item.isTool() && !"sword".equals(tool.item.toolKind) ? 1 : 2);
            digging = false;
            digProgress = 0;
        }
    }

    // ------------------------------------------------------------------ combat (Section 7.1)
    public float attackStrength() {
        ItemStack h = inventory.held();
        double speed = h != null && h.item.attackSpeed > 0 ? h.item.attackSpeed : 4.0;
        double period = 20.0 / speed;
        return (float) Math.max(0, Math.min(1, (attackTicker + 0.5) / period));
    }

    public void attack(Entity target) {
        if (isSpectator() || dead || target == null || target == this) return;
        ItemStack h = inventory.held();
        float strength = attackStrength();
        double base = h != null && h.item.attackDamage > 0 ? h.item.attackDamage : 1.0;
        float dmg = (float) (base * (0.2 + strength * strength * 0.8));
        boolean crit = strength > 0.9f && fallDistance > 0 && !onGround && !onLadder && !inWater && !sprinting;
        if (crit) dmg *= 1.5f;
        attackTicker = 0;
        swing();
        if (target instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) target;
            boolean ok = le.hurt(DamageSource.player(this), dmg);
            if (ok) {
                double kb = (sprinting && strength > 0.9f) ? 1 : 0;
                if (kb > 0) {
                    double r = Math.toRadians(yaw);
                    le.knockback(0.5, Math.sin(r), -Math.cos(r));
                    vx *= 0.6; vz *= 0.6;
                    sprinting = false;
                }
                world.playSound(crit ? "entity.player.attack.crit" : "entity.player.attack.strong", x, y, z, 1f, 1f);
                if (crit) world.particles("crit", target.x, target.y + target.height * 0.5, target.z, 8, 0);
                if (h != null && h.item.durability > 0) damageHeld(h.item.isTool() && !"sword".equals(h.item.toolKind) ? 2 : 1);
                exhaust((float) Registries.tune("exhaustion_attack"));
            }
        } else {
            target.hurt(DamageSource.player(this), dmg);
        }
    }

    public int swingTime;

    public void swing() { swingTime = 6; }

    // ------------------------------------------------------------------ inventory helpers
    public void damageHeld(int amount) {
        if (isCreative()) return;
        ItemStack h = inventory.held();
        if (h == null || h.item.durability <= 0) return;
        h.damage += amount;
        if (h.damage >= h.item.durability) {
            inventory.setHeld(null);
            world.playSound("entity.item.break", x, y, z, 0.8f, 0.8f + rng.nextFloat() * 0.4f);
        }
    }

    public void replaceHeld(ItemStack held, ItemStack with) {
        if (isCreative() && !held.item.id.equals("minecraft:bucket") && !held.item.id.endsWith("_bucket")) { giveOrDrop(with); return; }
        if (held.count <= 1) {
            if (inventory.held() == held) inventory.setHeld(with);
            else if (inventory.offhand() == held) inventory.setOffhand(with);
            else giveOrDrop(with);
        } else {
            held.count--;
            giveOrDrop(with);
        }
    }

    public void cleanupHands() {
        if (inventory.held() != null && inventory.held().count <= 0) inventory.setHeld(null);
        if (inventory.offhand() != null && inventory.offhand().count <= 0) inventory.setOffhand(null);
    }

    public void giveOrDrop(ItemStack s) {
        ItemStack rest = inventory.add(s);
        if (rest != null) dropStack(rest);
    }

    public void dropStack(ItemStack s) {
        if (ItemStack.isEmpty(s)) return;
        ItemEntity ie = new ItemEntity(world, s.copy());
        ie.setPos(x, eyeY() - 0.3, z);
        LivingEntity.Vec l = look();
        ie.vx = l.x * 0.3; ie.vy = l.y * 0.3 + 0.1; ie.vz = l.z * 0.3;
        ie.pickupDelay = 40;
        world.addEntity(ie);
    }

    public void dropHeld(boolean all) {
        ItemStack h = inventory.held();
        if (h == null) return;
        if (all || h.count <= 1) { dropStack(h); inventory.setHeld(null); }
        else { dropStack(h.copy(1)); h.count--; }
    }

    public void onPickup(ItemEntity e, int count) { }

    public void onCrafted(ItemStack r) { }

    public void openMenu(Menu m) {
        if (menu != invMenu) closeMenu();
        menu = m;
    }

    public void closeMenu() {
        menu.close(this);
        menu = invMenu;
    }

    // ------------------------------------------------------------------ experience (Section 6.7)
    public static int xpForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    public void giveXp(int amount) {
        xpTotal += amount;
        xpProgress += amount / (float) xpForLevel(xpLevel);
        while (xpProgress >= 1) {
            xpProgress = (xpProgress - 1) * xpForLevel(xpLevel);
            xpLevel++;
            xpProgress /= xpForLevel(xpLevel);
            if (xpLevel % 5 == 0) world.playSound("entity.player.levelup", x, y, z, (xpLevel > 30 ? 1f : xpLevel / 30f) * 0.75f, 1f);
        }
    }

    // ------------------------------------------------------------------ sleeping (Appendix AN)
    public String trySleep(int bx, int by, int bz) {
        if (!world.dim.bedsWork) return "explode";
        if (!Weather.canSleep(world.level)) return "You can only sleep at night or during thunderstorms";
        if (distSq(bx + 0.5, by, bz + 0.5) > 9) return "You may not rest now; the bed is too far away";
        for (Entity e : world.entitiesIn(new mc.util.AABB(bx - 8, by - 5, bz - 8, bx + 9, by + 6, bz + 9), this))
            if (e instanceof Monster && !((Monster) e).dead) return "You may not rest now; there are monsters nearby";
        setSpawn(bx, by, bz);
        sleeping = true;
        sleepTimer = 0;
        bedX = bx; bedY = by; bedZ = bz;
        setPos(bx + 0.5, by + 0.5625, bz + 0.5);
        vx = vy = vz = 0;
        return null;
    }

    public void setSpawn(int bx, int by, int bz) {
        if (!hasSpawn || spawnX != bx || spawnY != by || spawnZ != bz) world.listener.message("Respawn point set");
        hasSpawn = true;
        spawnX = bx; spawnY = by; spawnZ = bz;
        spawnDim = world.dim.id;
    }

    public void wake() {
        if (!sleeping) return;
        sleeping = false;
        sleepTimer = 0;
        setPos(bedX + 0.5, bedY + 0.6, bedZ + 0.5);
    }

    // ------------------------------------------------------------------ death
    @Override
    protected void onDeath(DamageSource src) {
        deathMessage = src.deathMessage(name);
        world.listener.message(deathMessage);
        if (sleeping) wake();
        if (menu != invMenu) closeMenu();
        if (!world.level.rule("keepInventory")) {
            for (ItemStack s : inventory.takeAll()) world.dropItem(x, y + 0.5, z, s);
            for (int i = 0; i < 4; i++) if (craft2[i] != null) { world.dropItem(x, y + 0.5, z, craft2[i]); craft2[i] = null; }
            if (carried != null) { world.dropItem(x, y + 0.5, z, carried); carried = null; }
            int xp = Math.min(100, xpLevel * 7);
            if (xp > 0) world.spawnXp(x, y + 0.5, z, xp);
            xpLevel = 0; xpProgress = 0; xpTotal = 0;
        }
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        deathTime = 0;
    }

    /** Players are never removed by the death animation; the server handles respawn. */
    @Override
    public void remove() { if (!dead) super.remove(); }

    public void respawnReset() {
        dead = false;
        removed = false;
        health = maxHealth;
        food = 20;
        saturation = 5;
        exhaustion = 0;
        fireTicks = 0;
        air = maxAir();
        effects.clear();
        absorption = 0;
        deathTime = 0;
        hurtTime = 0;
        fallDistance = 0;
        vx = vy = vz = 0;
        deathMessage = null;
        digging = false;
        useItem = null;
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("name", name);
        m.put("inventory", inventory.save());
        m.put("food", food);
        m.put("saturation", (double) saturation);
        m.put("exhaustion", (double) exhaustion);
        m.put("xp_level", xpLevel);
        m.put("xp_progress", (double) xpProgress);
        m.put("xp_total", xpTotal);
        m.put("game_mode", gameMode);
        m.put("flying", flying);
        m.put("dimension", world.dim.id);
        if (hasSpawn) m.put("spawn", Arrays.asList(spawnX, spawnY, spawnZ, spawnDim));
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        name = Json.str(m, "name", name);
        inventory.load(Json.obj(m.get("inventory")));
        food = Json.integer(m, "food", 20);
        saturation = (float) Json.num(m, "saturation", 5);
        exhaustion = (float) Json.num(m, "exhaustion", 0);
        xpLevel = Json.integer(m, "xp_level", 0);
        xpProgress = (float) Json.num(m, "xp_progress", 0);
        xpTotal = Json.integer(m, "xp_total", 0);
        setGameMode(Json.integer(m, "game_mode", SURVIVAL));
        flying = Boolean.TRUE.equals(m.get("flying")) && mayFly();
        List<Object> sp = Json.arr(m.get("spawn"));
        if (sp.size() >= 3) {
            hasSpawn = true;
            spawnX = ((Number) sp.get(0)).intValue();
            spawnY = ((Number) sp.get(1)).intValue();
            spawnZ = ((Number) sp.get(2)).intValue();
            if (sp.size() > 3) spawnDim = String.valueOf(sp.get(3));
        }
        if (health <= 0) health = maxHealth;
    }
}
