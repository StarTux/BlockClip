package com.cavetale.blockclip;

// import com.google.gson.Gson;
// import java.io.File;
// import java.io.FileReader;
// import java.io.IOException;
// import java.util.ArrayList;
// import java.util.Arrays;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.ThreadLocalRandom;
// import org.bukkit.Material;
// import org.bukkit.entity.EntityType;
// import org.bukkit.potion.*;
// import org.junit.Assert;
// import org.yaml.snakeyaml.Yaml;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public final class BlockClipTest {
    // @Test
    // public void test() {
    //     BlockClip clip1 = new BlockClip();
    //     Vec3i size = new Vec3i(3, 3, 3);
    //     clip1.setSize(size.x, size.y, size.z);
    //     for (int i = 0; i < size.x * size.y * size.z; i += 1) {
    //         switch (ThreadLocalRandom.current().nextInt(10)) {
    //         case 0: clip1.getBlocks().add("minecraft:grass"); break;
    //         case 1: clip1.getBlocks().add("minecraft:diamond_ore"); break;
             // case 2: clip1.getBlocks().add(Arrays.asList("minecraft:chest",
             //                                             new Gson().fromJson("{\"LootTable\":\"cavetale.mining_world\"}",
             //                                                                 Map.class))); break;
    //         default: clip1.getBlocks().add("minecraft:cobblestone"); break;
    //         }
    //     }
    //     clip1.getMetadata().put("Hello", "World");
    //     String json1 = clip1.serialize();
    //     BlockClip clip2 = BlockClip.deserialize(json1);
    //     String json2 = clip2.serializePretty();
    //     System.out.println(json1);
    //     System.out.println(json2);
    //     Assert.assertEquals(clip1, clip2);
    // }

    // @Test
    // public void convert() {
    //     Yaml yaml = new Yaml();
    //     Gson gson = new Gson();
    //     for (File file: new File("in").listFiles()) {
    //         try (FileReader reader = new FileReader(file)) {
    //             Map<String, Object> map = (Map<String, Object>)yaml.load(reader);
    //             List<String> tags = (List<String>)map.remove("tags");
    //             String json = gson.toJson(map);
    //             BlockClip clip = BlockClip.deserialize(json);
    //             ArrayList<Object> blocks = new ArrayList<>();
    //             for (String old: (List<String>)map.get("blocks")) {
    //                 String[] ts = old.split(" ", 2);
    //                 if (ts.length == 2) {
    //                     blocks.add(Arrays.asList(ts[0], gson.fromJson(ts[1], Map.class)));
    //                 } else {
    //                     blocks.add(ts[0]);
    //                 }
    //             }
    //             clip.getMetadata().put("tags", tags);
    //             clip.setBlocks(blocks);
    //             clip.save(new File(new File("out"), file.getName().replace("yml", "json")));
    //             int sz = clip.size().x * clip.size().y * clip.size().z;
    //             if (sz != blocks.size()) {
    //                 System.out.println("Size=" + clip.size() + " Length=" + blocks.size() + "/" + sz + " | " + file.getName());
    //             }
    //         } catch (IOException ioe) {
    //             ioe.printStackTrace();
    //         }
    //     }
    // }

    String human(String in) {
        return Stream.of(in.split("_"))
            .map(i -> i.substring(0, 1).toUpperCase() + i.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    @Test
    public void test() {
    }
}
