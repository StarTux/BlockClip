package com.cavetale.blockclip;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class BlockClipCommand implements TabExecutor {
    private final BlockClipPlugin plugin;
    private static final List<String> COMMANDS = Arrays.asList("copy", "paste", "show", "list", "save", "load");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
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
        return null;
    }

    private List<String> tabComplete(String arg, List<String> ls) {
        return ls.stream().filter(i -> i.startsWith(arg)).collect(Collectors.toList());
    }

    boolean onCommand(Player player, String cmd, String[] args) throws BlockClipException {
        switch (cmd) {
        case "copy": {
            if (args.length != 0) return false;
            Selection selection = selectionOf(player);
            World world = player.getWorld();
            BlockClip clip = BlockClip.copyOf(selection.lo.toBlock(world), selection.hi.toBlock(world));
            plugin.setClip(player, clip);
            player.sendMessage("Selection with size " + clip.size() + " stored in your clipboard.");
            return true;
        }
        case "paste": {
            if (args.length != 0) return false;
            Selection selection = selectionOf(player);
            BlockClip clip = clipOf(player);
            World world = player.getWorld();
            clip.paste(selection.lo.toBlock(world));
            player.sendMessage("Selection pasted at " + selection.lo + ".");
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
            if (!clip.save(file)) throw new BlockClipException("Could not save clip to " + file + ". See console.");
            player.sendMessage("Clipboard saved to " + file + ".");
            return true;
        }
        case "load": {
            if (args.length != 1) return false;
            File file = parseFile(plugin.getClipFolder(), args[0]);
            BlockClip clip = BlockClip.load(file);
            if (clip == null) throw new BlockClipException("Could not load file: " + file + ". See console.");
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
                sb.append(" ").append(name.substring(name.length() - 4, name.length()));
                count += 1;
            }
            player.sendMessage(count + " block clips:" + sb.toString());
            return true;
        }
        default: return false;
        }
    }

    static final class BlockClipException extends Exception {
        BlockClipException(String message) {
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
        if (!arg.matches("[a-zA-Z0-9_-]+")) throw new BlockClipException("Invalid file name!");
        return new File(root, arg + ".json");
    }
}
