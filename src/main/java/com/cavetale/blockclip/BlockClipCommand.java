package com.cavetale.blockclip;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@RequiredArgsConstructor
final class BlockClipCommand implements TabExecutor {
    private final BlockClipPlugin plugin;
    private static final List<String> COMMANDS = Arrays.asList("copy", "paste", "show", "list", "save", "load", "meta", "info", "findorigins");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        if (args.length == 0) return false;
        if (player == null) {
            plugin.getLogger().info("Player expected");
            return true;
        }
        try {
            return onCommand(player, args[0], Arrays.copyOfRange(args, 1, args.length));
        } catch (BlockClipException bce) {
            player.sendMessage(ChatColor.RED + bce.getMessage());
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return tabComplete(args[0], COMMANDS);
        }
        if (args.length == 2 && args[0].equals("load")) {
            try {
                return tabComplete(args[1], Arrays.stream(plugin.getClipFolder().list())
                                   .filter(i -> i.endsWith(".json"))
                                   .map(i -> i.substring(0, i.length() - 5)));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private List<String> tabComplete(String arg, List<String> ls) {
        return ls.stream().filter(i -> i.startsWith(arg)).collect(Collectors.toList());
    }

    private List<String> tabComplete(String arg, Stream<String> ls) {
        return ls.filter(i -> i.startsWith(arg)).collect(Collectors.toList());
    }

    void particle(Player player, int x, int y, int z) {
        final Particle p = Particle.END_ROD;
        player.spawnParticle(p, (double) x, (double) y, (double) z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    boolean onCommand(Player player, String cmd, String[] args) throws BlockClipException {
        switch (cmd) {
        case "copy": {
            if (args.length != 0) return false;
            Selection selection = selectionOf(player);
            World world = player.getWorld();
            BlockClip clip = BlockClip.copyOf(selection.lo.toBlock(world), selection.hi.toBlock(world));
            plugin.setClip(player, clip);
            player.sendMessage("Copied " + selection.lo + "-" + selection.hi + " size " + clip.size() + " (" + clip.size().area() + ") to clipboard.");
            for (int y = selection.lo.y; y <= selection.hi.y + 1; y += 1) {
                particle(player, selection.lo.x + 0, y, selection.lo.z + 0);
                particle(player, selection.lo.x + 0, y, selection.hi.z + 1);
                particle(player, selection.hi.x + 1, y, selection.lo.z + 0);
                particle(player, selection.hi.x + 1, y, selection.hi.z + 1);
            }
            for (int x = selection.lo.x; x <= selection.hi.x + 1; x += 1) {
                particle(player, x, selection.lo.y + 0, selection.lo.z + 0);
                particle(player, x, selection.lo.y + 0, selection.hi.z + 1);
                particle(player, x, selection.hi.y + 1, selection.lo.z + 0);
                particle(player, x, selection.hi.y + 1, selection.hi.z + 1);
            }
            for (int z = selection.lo.z; z <= selection.hi.z + 1; z += 1) {
                particle(player, selection.lo.x + 0, selection.lo.y + 0, z);
                particle(player, selection.lo.x + 0, selection.hi.y + 1, z);
                particle(player, selection.hi.x + 1, selection.lo.y + 0, z);
                particle(player, selection.hi.x + 1, selection.hi.y + 1, z);
            }
            player.spawnParticle(Particle.BARRIER,
                                 (double) selection.lo.x + 0.5,
                                 (double) selection.lo.y + 0.5,
                                 (double) selection.lo.z + 0.5, 1,
                                 0.0, 0.0, 0.0, 0.0);
            return true;
        }
        case "paste": {
            if (args.length != 0) return false;
            Selection selection = selectionOf(player);
            BlockClip clip = clipOf(player);
            World world = player.getWorld();
            player.sendMessage("Pasted at " + selection.lo + " size " + clip.size() + " (" + clip.size().area() + ") from clipboard.");
            clip.paste(selection.lo.toBlock(world));
            return true;
        }
        case "show": {
            if (args.length != 0) return false;
            Selection selection = selectionOf(player);
            BlockClip clip = clipOf(player);
            World world = player.getWorld();
            clip.show(player, selection.lo.toBlock(world));
            player.sendMessage("Selection previewed at " + selection.lo + ".");
            return true;
        }
        case "save": {
            if (args.length != 1) return false;
            BlockClip clip = clipOf(player);
            File file = parseFile(plugin.getClipFolder(), args[0]);
            try {
                clip.save(file);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                throw new BlockClipException("Could not save clip to " + file + ". See console.");
            }
            player.sendMessage("Clipboard saved to " + file + ".");
            return true;
        }
        case "load": {
            if (args.length != 1) return false;
            File file = parseFile(plugin.getClipFolder(), args[0]);
            BlockClip clip;
            try {
                clip = BlockClip.load(file);
            } catch (FileNotFoundException ioe) {
                throw new BlockClipException("File not found: " + file + ".");
            } catch (Exception exception) {
                exception.printStackTrace();
                throw new BlockClipException("Could not load file: " + file + ". See console.");
            }
            plugin.setClip(player, clip);
            player.sendMessage("File " + file + " loaded to clipboard.");
            return true;
        }
        case "list": {
            if (args.length > 1) return false;
            String pattern = args.length >= 1 ? args[0] : null;
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String name: plugin.getClipFolder().list()) {
                if (!name.endsWith(".json")) continue;
                if (pattern != null && !name.contains(pattern)) continue;
                sb.append(" ").append(name.substring(0, name.length() - 5));
                count += 1;
            }
            player.sendMessage(count + " block clips:" + sb.toString());
            return true;
        }
        case "meta": {
            if (args.length < 1) return false;
            BlockClip clip = clipOf(player);
            String key = args[0];
            if (args.length >= 2) {
                Object value = jsonOf(Arrays.copyOfRange(args, 1, args.length));
                clip.getMetadata().put(key, value);
                player.sendMessage("Set clipboard meta \"" + key + "\" to " + new Gson().toJson(value) + ".");
            } else {
                if (null == clip.getMetadata().remove(key)) throw new BlockClipException("Clipboard did not set meta \"" + key + "\"!");
                player.sendMessage("Removed clipboard meta \"" + key + "\".");
            }
            return true;
        }
        case "info": {
            if (args.length != 0) return false;
            BlockClip clip = clipOf(player);
            Gson gson = new Gson();
            player.sendMessage("Clipboard size=" + clip.size() + ", " + clip.size().area() + " blocks, meta=" + gson.toJson(clip.getMetadata()));
            return true;
        }
        case "findorigins": return findOrigins(player, args);
        default: return false;
        }
    }

    static final class BlockClipException extends Exception {
        BlockClipException(final String message) {
            super(message);
        }
    }

    BlockClip clipOf(Player player) throws BlockClipException {
        BlockClip clip = plugin.getClip(player);
        if (clip == null) throw new BlockClipException("Nothing on your clipboard.");
        return clip;
    }

    Selection selectionOf(Player player) throws BlockClipException {
        Selection selection = Selection.of(player);
        if (selection == null) throw new BlockClipException("No selection made!");
        return selection;
    }

    File parseFile(File root, String arg) throws BlockClipException {
        if (arg.contains("..")) throw new BlockClipException("Invalid file name!");
        if (!arg.matches("[.a-zA-Z0-9_-]+")) throw new BlockClipException("Invalid file name!");
        return new File(root, arg + ".json");
    }

    Object jsonOf(String[] args) throws BlockClipException {
        StringBuilder sb = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i += 1) {
            sb.append(" ").append(args[i]);
        }
        try {
            return new Gson().fromJson(sb.toString(), Object.class);
        } catch (JsonSyntaxException mje) {
            throw new BlockClipException("Malformed json: " + sb.toString() + "!");
        }
    }

    enum Action {
        REPORT, DETAILS, SAVE;
    }

    boolean findOrigins(Player player, String[] args) throws BlockClipException {
        if (args.length > 2) return false;
        final double threshold;
        if (args.length >= 1) {
            threshold = Double.parseDouble(args[0]);
        } else {
            threshold = -1;
        }
        final Action action;
        if (args.length >= 2) {
            action = Action.valueOf(args[1].toUpperCase());
        } else {
            action = Action.REPORT;
        }
        final Selection selection = selectionOf(player);
        final Iterator<Vec3i> vecIter = selection.iterator();
        final World world = player.getWorld();
        List<BlockClip> clips = new ArrayList<>();
        for (File file : plugin.getClipFolder().listFiles()) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                BlockClip clip;
                try {
                    clip = BlockClip.load(file);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    player.sendMessage("Error! See console");
                    return true;
                }
                if (clip.getOrigin() == null) {
                    clips.add(clip);
                }
            }
        }
        new BukkitRunnable() {
            int count = 0;
            @Override public void run() {
                if (!player.isValid() || !player.getWorld().equals(world)) {
                    cancel();
                    return;
                }
                long now = System.currentTimeMillis();
                do {
                    if (!vecIter.hasNext() || clips.isEmpty()) {
                        player.sendMessage("[BlockClip] findOrigins done: " + count + "/" + selection.volume() + " blocks searched!");
                        player.sendMessage(clips.size() + " origins not found!");
                        cancel();
                        return;
                    }
                    Vec3i vec = vecIter.next();
                    count += 1;
                    Block block = vec.toBlock(world);
                    BlockClip foundClip = null;
                    for (BlockClip clip : clips) {
                        if (threshold > 0) {
                            double accuracy = clip.originAccuracy(block);
                            if (accuracy >= threshold) {
                                if (action == Action.SAVE) {
                                    foundClip = clip;
                                    break;
                                } else {
                                    String n = String.format("%.02f%%", accuracy * 100.0);
                                    player.sendMessage("[BlockClip] High accuracy: " + n + " " + clip.getFilename() + " at " + vec);
                                    if (action == Action.DETAILS) {
                                        clip.printDifferences(block, player);
                                    }
                                }
                            }
                        } else {
                            if (clip.isOrigin(block)) {
                                foundClip = clip;
                                break;
                            }
                        }
                    }
                    if (foundClip != null) {
                        foundClip.setOrigin(new BlockClip.Origin(block));
                        player.sendMessage("Origin found: " + foundClip.getFilename() + ": " + foundClip.getOrigin());
                        foundClip.update();
                        try {
                            foundClip.save(new File(plugin.getClipFolder(), foundClip.getFilename()));
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                            player.sendMessage("[BlockClip] Error! See console");
                            cancel();
                            return;
                        }
                        clips.remove(foundClip);
                        return;
                    }
                } while (System.currentTimeMillis() - now < 50L);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        player.sendMessage("Searching origins of " + clips.size() + " clips within " + selection + "...");
        return true;
    }
}
