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
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

@Data
public final class BlockClip {
    private List<Integer> size = Arrays.asList(0, 0, 0);
    private List<Object> blocks = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();

    public interface BlockSetter {
        boolean accept(Block block, Vec3i vec, BlockData blockData, Map<String, Object> blockTag);
    }

    public void setSize(int x, int y, int z) {
        this.size = Arrays.asList(x, y, z);
    }

    public Vec3i size() {
        return new Vec3i(this.size.get(0), this.size.get(1), this.size.get(2));
    }

    public String serialize() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public String serializePretty() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    static BlockClip deserialize(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, BlockClip.class);
    }

    void paste(Block origin, BlockSetter setter) {
        Iterator<Object> iter = this.blocks.iterator();
        for (int y = 0; y <= this.size.get(1); y += 1) {
            for (int z = 0; z <= this.size.get(2); z += 1) {
                for (int x = 0; x <= this.size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    Object b = iter.next();
                    Block block = origin.getRelative(x, y, z);
                    BlockData blockData;
                    Map<String, Object> blockTag = null;
                    Vec3i relativePosition = new Vec3i(x, y, z);
                    if (b instanceof String) {
                        blockData = Bukkit.getServer().createBlockData((String)b);
                    } else if (b instanceof List) {
                        List<Object> list = (List<Object>)b;
                        if (list.size() != 2 || !(list.get(0) instanceof String) || !(list.get(1) instanceof Map)) {
                            throw new IllegalArgumentException("Invalid list entry at " + relativePosition + ": " + list);
                        }
                        blockData = Bukkit.getServer().createBlockData((String)list.get(0));
                        blockTag = (Map<String, Object>)list.get(1);
                        blockTag.put("x", block.getX());
                        blockTag.put("y", block.getY());
                        blockTag.put("z", block.getZ());
                    } else {
                        throw new IllegalArgumentException("Unknown block entry at " + relativePosition + ": " + b.getClass().getName());
                    }
                    if (setter == null || setter.accept(block, relativePosition, blockData, blockTag)) {
                        block.setBlockData(blockData, false);
                        if (blockTag != null) Dirty.setBlockTag(block, blockTag);
                    }
                }
            }
        }
    }

    public void paste(Block origin) {
        paste(origin, null);
    }

    public void show(Player player, Block origin) {
        paste(origin, (block, vec, data, tag) -> {
                player.sendBlockChange(block.getLocation(), data);
                return false;
            });
    }

    public void copy(Block origin) {
        List<Object> bs = new ArrayList<>();
        for (int y = 0; y <= this.size.get(1); y += 1) {
            for (int z = 0; z <= this.size.get(2); z += 1) {
                for (int x = 0; x <= this.size.get(0); x += 1) {
                    // Vec3i relativePosition = new Vec3i(x, y, z);
                    Block block = origin.getRelative(x, y, z);
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
        Block origin = ba.getWorld().getBlockAt(a.x, a.y, a.z);
        Vec3i sz = b.subtract(a).add(Vec3i.ONE);
        BlockClip result = new BlockClip();
        result.setSize(sz.x, sz.y, sz.z);
        result.copy(origin);
        return result;
    }

    public static BlockClip load(File file) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, BlockClip.class);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return null;
        }
    }

    public boolean save(File file) {
        Gson gson = new Gson();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(this, writer);
            return true;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
    }
}
