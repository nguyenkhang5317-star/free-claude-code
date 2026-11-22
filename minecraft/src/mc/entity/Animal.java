package mc.entity;

import mc.content.*;
import mc.core.World;
import mc.util.Json;

import java.util.Map;

/** Passive animals (Section 8.2): breeding with breed_item, babies grow up after 20 minutes, chickens lay eggs,
 * sheep can be sheared and regrow wool by eating grass, cows can be milked. */
public class Animal extends Mob {
    public int inLove, growAge, eggTime, eatTimer;
    public boolean sheared;
    private Animal partner;

    public Animal(World w, EntityType t) {
        super(w, t);
        persistent = true;
        eggTime = 6000 + rng.nextInt(6000);
        if (t.hasGoal("eat_grass")) goals.add(new EatGrass(5));
        goals.sort((a, b) -> Integer.compare(a.priority, b.priority));
    }

    public boolean isBaby() { return growAge < 0; }

    @Override protected boolean isBabyVoice() { return isBaby(); }

    public Item breedItem() {
        String s = type.str("breed_item");
        return s == null ? null : Items.get(s);
    }

    @Override
    public void tick() {
        super.tick();
        if (dead) return;
        if (growAge < 0 && ++growAge == 0) setSize(false);
        if (growAge > 0) growAge--;
        if (inLove > 0) {
            inLove--;
            if (age % 10 == 0) world.particles("heart", x, y + height + 0.3, z, 1, 0);
            if (partner == null || partner.removed || partner.inLove <= 0) {
                partner = null;
                for (Entity e : world.entitiesIn(bbox().grow(8), this))
                    if (e instanceof Animal && e.type == type && ((Animal) e).inLove > 0 && !((Animal) e).isBaby()) { partner = (Animal) e; break; }
            }
            if (partner != null) {
                lookAt(partner.x, partner.eyeY(), partner.z);
                if (age % 10 == 0) moveTo(partner.x, partner.y, partner.z, 1.0);
                if (partner.distSq(this) < 9 && id < partner.id) {
                    Animal baby = (Animal) EntityFactory.create(world, type);
                    baby.growAge = -24000;
                    baby.setSize(true);
                    baby.setPos(x, y, z);
                    world.addEntity(baby);
                    inLove = partner.inLove = 0;
                    growAge = partner.growAge = 6000;
                    world.spawnXp(x, y, z, 1 + rng.nextInt(7));
                    partner = null;
                }
            }
        }
        if (type.flag("lays_eggs") && !isBaby() && --eggTime <= 0) {
            world.playSound("entity.chicken.egg", x, y, z, 1f, voicePitch());
            Item egg = Items.get("minecraft:egg");
            if (egg != null) world.dropItem(x, y, z, new ItemStack(egg, 1));
            eggTime = 6000 + rng.nextInt(6000);
        }
    }

    void setSize(boolean baby) {
        width = type.width * (baby ? 0.5 : 1);
        height = type.height * (baby ? 0.5 : 1);
    }

    /** Right-click interaction from a player. Returns true if consumed. */
    public boolean interact(Player p, ItemStack held) {
        if (held == null) return false;
        Item bi = breedItem();
        if (bi != null && held.item == bi) {
            if (isBaby()) {
                growAge = Math.min(0, growAge + 2400);
            } else if (growAge == 0 && inLove == 0) {
                inLove = 600;
            } else return false;
            if (!p.isCreative()) held.count--;
            return true;
        }
        String act = type.str("interact");
        if ("milk".equals(act) && held.item.id.equals("minecraft:bucket") && !isBaby()) {
            world.playSound("entity.cow.milk", x, y, z, 1f, 1f);
            p.replaceHeld(held, new ItemStack(Items.get("minecraft:milk_bucket"), 1));
            return true;
        }
        if ("shear".equals(act) && held.item.id.equals("minecraft:shears") && !sheared && !isBaby()) {
            sheared = true;
            world.playSound("entity.sheep.shear", x, y, z, 1f, 1f);
            int n = 1 + rng.nextInt(3);
            world.dropItem(x, y + 1, z, new ItemStack(Items.get("minecraft:white_wool"), n));
            p.damageHeld(1);
            return true;
        }
        return false;
    }

    @Override
    protected boolean dropAllowed(Item it) {
        if (isBaby()) return false;
        return !(sheared && it.id.endsWith("_wool"));
    }

    class EatGrass extends Goal {
        int timer;

        EatGrass(int p) { super(p, MOVE | LOOK | JUMP); }

        public boolean canUse() {
            if (rng.nextInt(isBaby() ? 50 : 1000) != 0) return false;
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            int here = world.getBlock(bx, by, bz);
            return here == Blocks.GRASS || world.getBlock(bx, by - 1, bz) == Blocks.GRASS_BLOCK;
        }

        public boolean canContinue() { return timer > 0; }

        public void start() { timer = 40; stopPath(); eatTimer = 40; }

        public void tick() {
            timer--;
            eatTimer = timer;
            if (timer == 4) {
                int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
                if (world.getBlock(bx, by, bz) == Blocks.GRASS) {
                    if (world.level.rule("mobGriefing")) world.destroyBlock(bx, by, bz, false, null, Animal.this);
                } else if (world.getBlock(bx, by - 1, bz) == Blocks.GRASS_BLOCK) {
                    if (world.level.rule("mobGriefing")) {
                        world.particles("block", bx + 0.5, by, bz + 0.5, 8, Blocks.GRASS_BLOCK);
                        world.setBlock(bx, by - 1, bz, Blocks.DIRT);
                    }
                } else return;
                sheared = false;
                if (isBaby()) growAge = Math.min(0, growAge + 1200);
            }
        }
    }

    @Override
    public int renderData() { return (isBaby() ? 1 : 0) | (sheared ? 2 : 0) | (eatTimer > 0 ? 4 : 0); }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("grow", growAge);
        m.put("sheared", sheared);
        m.put("love", inLove);
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        growAge = Json.integer(m, "grow", 0);
        sheared = Boolean.TRUE.equals(m.get("sheared"));
        inLove = Json.integer(m, "love", 0);
        setSize(growAge < 0);
    }
}
