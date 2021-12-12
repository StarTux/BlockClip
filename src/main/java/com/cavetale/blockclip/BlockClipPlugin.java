package com.cavetale.blockclip;

import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlockClipPlugin extends JavaPlugin {
    private static final String METADATA_CLIP = "blockclip.clip";
    private BlockClipCommand blockClipCommand;
    protected static BlockClipPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        this.blockClipCommand = new BlockClipCommand(this);
        getCommand("blockclip").setExecutor(blockClipCommand);
    }

    @Override
    public void onDisable() {
        for (Player player: getServer().getOnlinePlayers()) {
            player.removeMetadata(METADATA_CLIP, this);
        }
    }

    File getClipFolder() {
        File folder = new File(getDataFolder(), "clips");
        folder.mkdirs();
        return folder;
    }

    void setClip(Metadatable player, BlockClip clip) {
        player.setMetadata(METADATA_CLIP, new FixedMetadataValue(this, clip));
    }

    BlockClip getClip(Metadatable player) {
        for (MetadataValue mv: player.getMetadata(METADATA_CLIP)) {
            if (mv.getOwningPlugin() == this) {
                return (BlockClip) mv.value();
            }
        }
        return null;
    }
}
