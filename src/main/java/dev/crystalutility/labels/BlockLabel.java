package dev.crystalutility.labels;

import net.minecraft.core.BlockPos;

public class BlockLabel {
    public int x;
    public int y;
    public int z;
    public String text = "Marker";
    public String color = "FFFFC857";

    public BlockLabel() {
    }

    public BlockLabel(BlockPos pos, String text, String color) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.text = text == null || text.trim().isEmpty() ? "Marker" : text.trim();
        this.color = color == null || color.isBlank() ? "FFFFC857" : color;
    }

    public static BlockLabel create(BlockPos pos, String text, String color) {
        return new BlockLabel(pos, text, color);
    }

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }

    public boolean matches(BlockPos pos) {
        return pos != null && pos.getX() == x && pos.getY() == y && pos.getZ() == z;
    }
}
