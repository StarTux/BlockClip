package com.cavetale.blockclip;

import com.cavetale.dirty.Dirty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

@Data
public final class BlockClip {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private List<Integer> size = Arrays.asList(0, 0, 0);
    private List<Object> blocks = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();
    private Origin origin;

    @FunctionalInterface
    public interface BlockSetter {
        boolean accept(Block block, Vec3i vec, BlockData blockData, Map<String, Object> blockTag);
    }

    public static final class Origin {
        String world;
        int x;
        int y;
        int z;

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
    }

    public void setSize(int x, int y, int z) {
        this.size = Arrays.asList(x, y, z);
    }

    public Vec3i size() {
        return new Vec3i(this.size.get(0), this.size.get(1), this.size.get(2));
    }

    public String serialize() {
        return GSON.toJson(this);
    }

    public String serializePretty() {
        return PRETTY_GSON.toJson(this);
    }

    public static BlockClip deserialize(String json) {
        return GSON.fromJson(json, BlockClip.class);
    }

    BlockData parseBlockData(String in) {
        in = in.replace("minecraft:sign", "minecraft:oak_sign");
        in = in.replace("minecraft:wall_sign", "minecraft:oak_wall_sign");
        if (in.startsWith("minecraft:") && in.contains("_wall[")) {
            in = in
                .replace("north=true", "north=low")
                .replace("east=true", "east=low")
                .replace("south=true", "south=low")
                .replace("west=true", "west=low")
                .replace("north=false", "north=none")
                .replace("east=false", "east=none")
                .replace("south=false", "south=none")
                .replace("west=false", "west=none");
        }
        try {
            return Bukkit.getServer().createBlockData(in);
        } catch (IllegalArgumentException iae) {
            BlockClipPlugin.instance.getLogger().log(Level.SEVERE, in, in);
            in = in.split("\\[")[0];
        }
        try {
            return Bukkit.getServer().createBlockData(in);
        } catch (IllegalArgumentException iae) {
            BlockClipPlugin.instance.getLogger().log(Level.SEVERE, in, in);
        }
        return null;
    }

    public void paste(Block offset, BlockSetter setter) {
        Iterator<Object> iter = this.blocks.iterator();
        for (int y = 0; y < this.size.get(1); y += 1) {
            for (int z = 0; z < this.size.get(2); z += 1) {
                for (int x = 0; x < this.size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    Object b = iter.next();
                    Block block = offset.getRelative(x, y, z);
                    final BlockData blockData;
                    final Map<String, Object> blockTag;
                    Vec3i relativePosition = new Vec3i(x, y, z);
                    if (b instanceof String) {
                        blockData = parseBlockData((String) b);
                        blockTag = null;
                    } else if (b instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) b;
                        if (list.size() != 2 || !(list.get(0) instanceof String) || !(list.get(1) instanceof Map)) {
                            throw new IllegalArgumentException("Invalid list entry at " + relativePosition + ": " + list);
                        }
                        blockData = parseBlockData((String) list.get(0));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) list.get(1);
                        blockTag = map;
                        blockTag.put("x", block.getX());
                        blockTag.put("y", block.getY());
                        blockTag.put("z", block.getZ());
                    } else {
                        throw new IllegalArgumentException("Unknown block entry at " + relativePosition + ": " + b.getClass().getName());
                    }
                    if (blockData == null) continue;
                    if (setter == null || setter.accept(block, relativePosition, blockData, blockTag)) {
                        block.setBlockData(blockData, false);
                        if (blockTag != null) Dirty.setBlockTag(block, blockTag);
                    }
                }
            }
        }
    }

    public void paste(Block offset) {
        paste(offset, null);
    }

    public void show(Player player, Block offset) {
        paste(offset, (block, vec, data, tag) -> {
                player.sendBlockChange(block.getLocation(), data);
                return false;
            });
    }

    public void copy(Block offset) {
        List<Object> bs = new ArrayList<>();
        for (int y = 0; y < this.size.get(1); y += 1) {
            for (int z = 0; z < this.size.get(2); z += 1) {
                for (int x = 0; x < this.size.get(0); x += 1) {
                    // Vec3i relativePosition = new Vec3i(x, y, z);
                    Block block = offset.getRelative(x, y, z);
                    BlockData blockData = block.getBlockData();
                    Map<String, Object> blockTag = Dirty.getBlockTag(block);
                    if (blockTag == null) {
                        bs.add(blockData.getAsString());
                    } else {
                        blockTag.remove("x");
                        blockTag.remove("y");
                        blockTag.remove("z");
                        bs.add(Arrays.asList(blockData.getAsString(), blockTag));
                    }
                }
            }
        }
        this.blocks = bs;
    }

    public static BlockClip copyOf(Block ba, Block bb) {
        if (!ba.getWorld().equals(bb.getWorld())) throw new IllegalArgumentException("Blocks cannot be in different worlds");
        Vec3i a = new Vec3i(Math.min(ba.getX(), bb.getX()),
                            Math.min(ba.getY(), bb.getY()),
                            Math.min(ba.getZ(), bb.getZ()));
        Vec3i b = new Vec3i(Math.max(ba.getX(), bb.getX()),
                            Math.max(ba.getY(), bb.getY()),
                            Math.max(ba.getZ(), bb.getZ()));
        Block offset = ba.getWorld().getBlockAt(a.x, a.y, a.z);
        Vec3i sz = b.subtract(a).add(Vec3i.ONE);
        BlockClip result = new BlockClip();
        result.setSize(sz.x, sz.y, sz.z);
        result.copy(offset);
        result.origin = new Origin(ba);
        return result;
    }

    public static BlockClip load(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, BlockClip.class);
        }
    }

    public void save(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
        }
    }
}
