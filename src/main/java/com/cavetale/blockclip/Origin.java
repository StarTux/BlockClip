package com.cavetale.blockclip;

import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
public final class Origin {
    public final String world;
    public final int x;
    public final int y;
    public final int z;

    public Origin(final Block block) {
        this.world = block.getWorld().getName();
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
    }

    public Block toBlock() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return w.getBlockAt(x, y, z);
    }

    @Override
    public String toString() {
        return world + "," + x + "," + y + "," + z;
    }
}
