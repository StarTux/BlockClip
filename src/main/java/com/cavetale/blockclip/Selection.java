package com.cavetale.blockclip;

import lombok.Data;
import org.bukkit.entity.Player;

@Data
final class Selection {
    public final Vec3i lo;
    public final Vec3i hi;

    static Selection of(Player player) {
        return WorldEdit.getSelection(player);
    }

    Selection order() {
        return new Selection(new Vec3i(Math.min(lo.x, hi.x),
                                       Math.min(lo.y, hi.y),
                                       Math.min(lo.z, hi.z)),
                             new Vec3i(Math.max(lo.x, hi.x),
                                       Math.max(lo.y, hi.y),
                                       Math.max(lo.z, hi.z)));
    }
}
