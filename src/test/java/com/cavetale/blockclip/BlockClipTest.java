package com.cavetale.blockclip;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Assert;
import org.junit.Test;

public final class BlockClipTest {
    @Test
    public void test() {
        BlockClip clip1 = new BlockClip();
        Vec3i size = new Vec3i(3, 3, 3);
        clip1.setSize(size.x, size.y, size.z);
        for (int i = 0; i < size.x * size.y * size.z; i += 1) {
            switch (ThreadLocalRandom.current().nextInt(10)) {
            case 0: clip1.getBlocks().add("minecraft:grass"); break;
            case 1: clip1.getBlocks().add("minecraft:diamond_ore"); break;
            case 2: clip1.getBlocks().add(Arrays.asList("minecraft:chest", new Gson().fromJson("{\"LootTable\":\"cavetale.mining_world\"}", Map.class))); break;
            default: clip1.getBlocks().add("minecraft:cobblestone"); break;
            }
        }
        clip1.getMetadata().put("Hello", "World");
        String json1 = clip1.serialize();
        BlockClip clip2 = BlockClip.deserialize(json1);
        String json2 = clip2.serializePretty();
        System.out.println(json1);
        System.out.println(json2);
        Assert.assertEquals(clip1, clip2);
    }
}
