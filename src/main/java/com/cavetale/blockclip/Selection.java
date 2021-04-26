package com.cavetale.blockclip;

import java.util.Iterator;
import java.util.NoSuchElementException;
import lombok.Data;
import org.bukkit.entity.Player;

@Data
final class Selection implements Iterable<Vec3i> {
    public final Vec3i lo;
    public final Vec3i hi;

    static Selection of(Player player) {
        return WorldEdit.getSelection(player);
    }

    private final class SelectionIterator implements Iterator<Vec3i> {
        private int x;
        private int y;
        private int z;

        SelectionIterator() {
            x = lo.x;
            y = lo.y;
            z = lo.z;
        }

        @Override
        public boolean hasNext() {
            return y <= hi.y
                && z <= hi.z
                && x <= hi.x;
        }

        @Override
        public Vec3i next() {
            if (y > hi.y) throw new NoSuchElementException();
            Vec3i result = new Vec3i(x, y, z);
            x += 1;
            if (x > hi.x) {
                x = lo.x;
                z += 1;
                if (z > hi.z) {
                    z = lo.z;
                    y += 1;
                }
            }
            return result;
        }
    }

    @Override
    public Iterator<Vec3i> iterator() {
        return new SelectionIterator();
    }

    public int volume() {
        return (hi.z - lo.z + 1)
            * (hi.x - lo.x + 1)
            * (hi.y - lo.y + 1);
    }
}
