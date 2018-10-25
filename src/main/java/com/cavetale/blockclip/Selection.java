package com.cavetale.blockclip;

import lombok.Data;
import org.bukkit.entity.Player;

@Data
final class Selection {
    public final Vec3i lo, hi;

    static Selection of(Player player) {
        int ax, ay, az, bx, by, bz;
        try {
            ax = player.getMetadata("SelectionAX").get(0).asInt();
            ay = player.getMetadata("SelectionAY").get(0).asInt();
            az = player.getMetadata("SelectionAZ").get(0).asInt();
            bx = player.getMetadata("SelectionBX").get(0).asInt();
            by = player.getMetadata("SelectionBY").get(0).asInt();
            bz = player.getMetadata("SelectionBZ").get(0).asInt();
        } catch (Exception e) {
            return null;
        }
        return new Selection(new Vec3i(Math.min(ax, bx),
                                       Math.min(ay, by),
                                       Math.min(az, bz)),
                             new Vec3i(Math.max(ax, bx),
                                       Math.max(ay, by),
                                       Math.max(az, bz)));
    }
}
