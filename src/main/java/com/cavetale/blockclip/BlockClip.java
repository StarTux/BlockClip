package com.cavetale.blockclip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;

@Data
public final class BlockClip {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private List<Integer> size = Arrays.asList(0, 0, 0);
    // Legacy
    private List<Object> blocks = null;
    private transient List<BlockData> blockDataCache = null;
    // Structure
    private String serialized;
    // Meta
    private Origin origin;
    private String filename;
    private Map<String, Object> metadata = null;
    // Cache
    private transient Structure structure;

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

    @Deprecated
    private static BlockData parseBlockData(String in) {
        if (in.equals("grass_path")) return Material.DIRT_PATH.createBlockData();
        if (in.equals("cauldron[level=0]")) return Material.CAULDRON.createBlockData();
        if (in.startsWith("cauldron[")) {
            in = "water_" + in;
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

    @Deprecated
    private static String serializeBlockData(BlockData in) {
        if (in == null) return "air";
        String string = in.getAsString();
        if (string.startsWith("minecraft:")) string = string.substring(10);
        return string;
    }

    @Deprecated
    public void pasteLegacy(Block offset) {
        Iterator<BlockData> iter = getBlockDataCache().iterator();
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    BlockData blockData = iter.next();
                    if (blockData == null) continue;
                    Block block = offset.getRelative(x, y, z);
                    Vec3i relative = new Vec3i(x, y, z);
                    block.setBlockData(blockData, false);
                }
            }
        }
    }

    public void paste(Block offset) {
        if (serialized == null) {
            // Legacy
            pasteLegacy(offset);
            return;
        }
        getStructure().place(offset.getLocation(),
                             false, // includeEntities
                             StructureRotation.NONE, Mirror.NONE,
                             0, // palette
                             1.0f, // integrity, 1=pristine
                             ThreadLocalRandom.current());
    }

    public void show(Player player, Block offset) {
        if (serialized == null) return;
        for (Palette palette : getStructure().getPalettes()) {
            for (BlockState blockState : palette.getBlocks()) {
                // BlockState locations are stored relative to the Structure origin(?)
                int x = blockState.getX() + offset.getX();
                int y = blockState.getY() + offset.getY();
                int z = blockState.getZ() + offset.getZ();
                if (!player.getWorld().isChunkLoaded(x >> 4, z >> 4)) continue;
                player.sendBlockChange(player.getWorld().getBlockAt(x, y, z).getLocation(),
                                       blockState.getBlockData());
            }
        }
    }

    public void copy(Block offset) {
        this.origin = new Origin(offset);
        this.structure = Bukkit.getStructureManager().createStructure();
        structure.fill(offset.getLocation(),
                       offset.getRelative(size.get(0), size.get(1), size.get(1)).getLocation(),
                       false);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Bukkit.getStructureManager().saveStructure(baos, structure);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        this.serialized = Base64.getEncoder().encodeToString(baos.toByteArray());
        this.blocks = null;
        this.blockDataCache = null;
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
            PRETTY_GSON.toJson(this, writer);
        }
    }

    public void save() throws IOException {
        save(new File(BlockClipPlugin.instance.getClipFolder(), filename));
    }

    private List<BlockData> computeBlockDataCache() {
        List<BlockData> result = new ArrayList<>(blocks.size());
        Iterator<Object> iter = blocks.iterator();
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    Object b = iter.next();
                    final BlockData blockData;
                    Vec3i relativePosition = new Vec3i(x, y, z);
                    if (b instanceof String) {
                        blockData = parseBlockData((String) b);
                    } else if (b instanceof List) {
                        @SuppressWarnings("unchecked") List<Object> list = (List<Object>) b;
                        if (list.isEmpty() || !(list.get(0) instanceof String)) {
                            throw new IllegalArgumentException("Invalid list entry at " + relativePosition + ": " + list);
                        }
                        blockData = parseBlockData((String) list.get(0));
                    } else {
                        throw new IllegalArgumentException("Unknown block entry at " + relativePosition + ": " + b.getClass().getName());
                    }
                    result.add(blockData);
                }
            }
        }
        return result;
    }

    public List<BlockData> getBlockDataCache() {
        if (blockDataCache == null) {
            blockDataCache = computeBlockDataCache();
        }
        return blockDataCache;
    }

    public boolean isOrigin(Block offset) {
        Iterator<BlockData> iter = getBlockDataCache().iterator();
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    BlockData blockData = iter.next();
                    Block block = offset.getRelative(x, y, z);
                    if (blockData == null) {
                        if (!block.isEmpty()) return false;
                    } else {
                        if (blockData.getMaterial() != block.getType()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public double originAccuracy(Block offset) {
        Iterator<BlockData> iter = getBlockDataCache().iterator();
        int hits = 0;
        int count = 0;
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    count += 1;
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    BlockData blockData = iter.next();
                    Block block = offset.getRelative(x, y, z);
                    if (blockData == null) {
                        if (block.isEmpty()) hits += 1;
                    } else {
                        if (blockData.getMaterial() == block.getType()) {
                            hits += 1;
                        }
                    }
                }
            }
        }
        return (double) hits / (double) count;
    }

    public void printDifferences(Block offset, Player player) {
        Iterator<BlockData> iter = getBlockDataCache().iterator();
        for (int y = 0; y < size.get(1); y += 1) {
            for (int z = 0; z < size.get(2); z += 1) {
                for (int x = 0; x < size.get(0); x += 1) {
                    if (!iter.hasNext()) throw new IllegalStateException("Size does not match block array length!");
                    BlockData blockData = iter.next();
                    Block block = offset.getRelative(x, y, z);
                    if (blockData == null) {
                        if (!block.isEmpty()) {
                            player.sendMessage(Vec3i.of(block) + ": AIR vs " + block.getType());
                        }
                    } else {
                        if (blockData.getMaterial() != block.getType()) {
                            player.sendMessage(Vec3i.of(block) + ": " + blockData.getMaterial() + " vs " + block.getType());
                        }
                    }
                }
            }
        }
    }

    public void update() {
        List<BlockData> blockDataList = getBlockDataCache();
        if (blockDataList.size() != blocks.size()) throw new IllegalStateException("Size does not match block array length!");
        for (int i = 0; i < blockDataList.size(); i += 1) {
            BlockData blockData = blockDataList.get(i);
            blocks.set(i, serializeBlockData(blockData));
        }
    }

    public Structure getStructure() {
        if (serialized == null) return null;
        if (structure == null) {
            byte[] bytes = Base64.getDecoder().decode(serialized);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try {
                this.structure = Bukkit.getStructureManager().loadStructure(bais);
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
        return structure;
    }

    public List<BlockState> getBlockStates() {
        if (structure == null && serialized == null) return List.of();
        List<BlockState> result = new ArrayList<>();
        for (Palette palette : getStructure().getPalettes()) {
            result.addAll(palette.getBlocks());
        }
        return result;
    }

    public int getSizeX() {
        return size.get(0);
    }

    public int getSizeY() {
        return size.get(1);
    }

    public int getSizeZ() {
        return size.get(2);
    }
}
