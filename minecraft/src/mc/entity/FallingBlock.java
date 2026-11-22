package mc.entity;

import mc.content.*;
import mc.core.Mining;
import mc.core.World;

import java.util.Map;

/** Gravity-affected block in flight (sand, gravel). Lands as a block, or drops as an item if it cannot. */
public final class FallingBlock extends Entity {
    public int state;

    public FallingBlock(World w, int state) {
        super(EntityTypes.FALLING_BLOCK, w);
        this.state = state;
    }

    @Override
    public void tick() {
        super.tick();
        vy -= 0.04;
        move(vx, vy, vz);
        vx *= 0.98; vy *= 0.98; vz *= 0.98;
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        if (onGround) {
            remove();
            int here = world.getBlock(bx, by, bz);
            if (here == Blocks.AIR || Blocks.replaceable[here] || Blocks.fluid[here]) {
                world.setBlock(bx, by, bz, state);
                world.playSound("block." + Blocks.block(state).sound + ".place", x, y, z, 1f, 0.8f);
            } else {
                for (ItemStack s : Mining.drops(state, null, rng)) world.dropItem(x, y, z, s);
            }
        } else if (age > 600 || y < -64) {
            remove();
        }
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> m = super.save();
        m.put("state", Blocks.stateName(state));
        return m;
    }

    @Override
    public void load(Map<String, Object> m) {
        super.load(m);
        int s = Blocks.parseState(String.valueOf(m.get("state")));
        state = s < 0 ? Blocks.SAND : s;
    }

    @Override
    public int renderData() { return state; }
}
