package com.cavetale.blockclip;

import lombok.Value;
import org.bukkit.World;
import org.bukkit.block.Block;

@Value
public final class Vec3i {
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    public static final Vec3i ONE = new Vec3i(1, 1, 1);
    public final int x;
    public final int y;
    public final int z;

    @Override
    public String toString() {
        return String.format("%d,%d,%d", x, y, z);
    }

    public Block toBlock(World world) {
        return world.getBlockAt(x, y, z);
    }

    public static Vec3i of(Block block) {
        return new Vec3i(block.getX(), block.getY(), block.getZ());
    }

    Vec3i subtract(Vec3i other) {
        return new Vec3i(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    Vec3i add(Vec3i other) {
        return new Vec3i(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    int area() {
        return x * y * z;
    }
}
