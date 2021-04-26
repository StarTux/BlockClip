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
import lombok.Value;
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
    private String filename;
    private transient List<ParsedBlock> parsedCache = null;

    @FunctionalInterface
    public interface BlockSetter {
        boolean accept(Block block, Vec3i vec, BlockData blockData, Map<String, Object> blockTag);
    }

    @Value
    public static final class ParsedBlock {
        BlockData blockData;
        Map<String, Object> blockTag;
    }

    @Value
    public static final class Origin {
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
    }

    public void setSize(int x, int y, int z) {
        size = Arrays.asList(x, y, z);
    }

    public Vec3i size() {
        return new Vec3i(size.get(0), size.get(1), size.get(2));
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

    private static BlockData parseBlockData(String in) {
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

    private static String serializeBlockData(BlockData in) {
        if (in == null) return "air";
        String string = in.getAsString();
        if (string.startsWith("minecraft:")) string = string.substring(10);
        return string;
    }

    public void paste(Block offset, BlockSetter setter) {
        Iterator<ParsedBlock> iter = getParsedCache().iterator();
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    ParsedBlock it = iter.next();
                    if (it.blockData == null) continue;
                    Block block = offset.getRelative(x, y, z);
                    Vec3i relative = new Vec3i(x, y, z);
                    BlockData blockData = it.blockData.clone();
                    Map<String, Object> blockTag = it.blockTag; // careful: not a copy!
                    if (blockTag != null) {
                        blockTag.put("x", block.getX());
                        blockTag.put("y", block.getY());
                        blockTag.put("z", block.getZ());
                    }
                    if (setter == null || setter.accept(block, relative, blockData, blockTag)) {
                        block.setBlockData(blockData, false);
                        if (blockTag != null) Dirty.setBlockTag(block, blockTag);
                    }
                    if (blockTag != null) {
                        blockTag.remove("x");
                        blockTag.remove("y");
                        blockTag.remove("z");
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
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    // Vec3i relativePosition = new Vec3i(x, y, z);
                    Block block = offset.getRelative(x, y, z);
                    BlockData blockData = block.getBlockData();
                    Map<String, Object> blockTag = Dirty.getBlockTag(block);
                    if (blockTag == null) {
                        bs.add(serializeBlockData(blockData));
                    } else {
                        blockTag.remove("x");
                        blockTag.remove("y");
                        blockTag.remove("z");
                        bs.add(Arrays.asList(serializeBlockData(blockData), blockTag));
                    }
                }
            }
        }
        blocks = bs;
        parsedCache = null;
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
        BlockClip result;
        try (FileReader reader = new FileReader(file)) {
            result = GSON.fromJson(reader, BlockClip.class);
        }
        if (result != null) {
            result.filename = file.getName();
        }
        return result;
    }

    public void save(File file) throws IOException {
        filename = file.getName();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
        }
    }

    private List<ParsedBlock> computeParsedCache() {
        List<ParsedBlock> result = new ArrayList<>(blocks.size());
        Iterator<Object> iter = blocks.iterator();
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    Object b = iter.next();
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
                    } else {
                        throw new IllegalArgumentException("Unknown block entry at " + relativePosition + ": " + b.getClass().getName());
                    }
                    result.add(new ParsedBlock(blockData, blockTag));
                }
            }
        }
        return result;
    }

    public List<ParsedBlock> getParsedCache() {
        if (parsedCache == null) {
            parsedCache = computeParsedCache();
        }
        return parsedCache;
    }

    public boolean isOrigin(Block offset) {
        Iterator<ParsedBlock> iter = getParsedCache().iterator();
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    ParsedBlock it = iter.next();
                    Block block = offset.getRelative(x, y, z);
                    if (it.blockData == null) {
                        if (!block.isEmpty()) return false;
                    } else {
                        if (it.blockData.getMaterial() != block.getType()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public double originAccuracy(Block offset) {
        Iterator<ParsedBlock> iter = getParsedCache().iterator();
        int hits = 0;
        int count = 0;
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    count += 1;
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    ParsedBlock it = iter.next();
                    Block block = offset.getRelative(x, y, z);
                    if (it.blockData == null) {
                        if (block.isEmpty()) hits += 1;
                    } else {
                        if (it.blockData.getMaterial() == block.getType()) {
                            hits += 1;
                        }
                    }
                }
            }
        }
        return (double) hits / (double) count;
    }

    public void printDifferences(Block offset, Player player) {
        Iterator<ParsedBlock> iter = getParsedCache().iterator();
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    ParsedBlock it = iter.next();
                    Block block = offset.getRelative(x, y, z);
                    if (it.blockData == null) {
                        if (!block.isEmpty()) {
                            player.sendMessage(Vec3i.of(block) + ": AIR vs " + block.getType());
                        }
                    } else {
                        if (it.blockData.getMaterial() != block.getType()) {
                            player.sendMessage(Vec3i.of(block) + ": " + it.blockData.getMaterial() + " vs " + block.getType());
                        }
                    }
                }
            }
        }
    }

    public void update() {
        List<ParsedBlock> parsed = getParsedCache();
        if (parsed.size() != blocks.size()) throw new IllegalStateException("Size does not match block array length!");
        for (int i = 0; i < parsed.size(); i += 1) {
            ParsedBlock it = parsed.get(i);
            blocks.set(i, it.blockTag == null
                       ? serializeBlockData(it.blockData)
                       : Arrays.asList(serializeBlockData(it.blockData), it.blockTag));
        }
    }
}
