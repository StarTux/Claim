package com.winthier.claim;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

@Value @RequiredArgsConstructor
public final class Rectangle {
    private final int x, y, width, height;

    Rectangle(Map map) {
        x = ((Number)map.get("x")).intValue();
        y = ((Number)map.get("y")).intValue();
        width = Math.max(1, ((Number)map.get("width")).intValue());
        height = Math.max(1, ((Number)map.get("height")).intValue());
    }

    @SuppressWarnings("unchecked")
    void serialize(Map map) {
        map.put("x", x);
        map.put("y", y);
        map.put("width", width);
        map.put("height", height);
    }

    public static Rectangle ofCornerBlocks(Block corner1, Block corner2) {
        return new Rectangle(Math.min(corner1.getX(), corner2.getX()),
                             Math.min(corner1.getZ(), corner2.getZ()),
                             Math.abs(corner2.getX() - corner1.getX()) + 1,
                             Math.abs(corner2.getZ() - corner1.getZ()) + 1);
    }

    public static Rectangle ofNorthEastSouthWest(int north, int east, int south, int west) {
        return new Rectangle(west, north, east - west + 1, south - north + 1);
    }

    public boolean intersects(Rectangle other) {
        if (x + width - 1 < other.x || x >= other.x + other.width) return false;
        if (y + height - 1 < other.y || y >= other.y + other.height) return false;
        return true;
    }

    public boolean contains(Block block) {
        final int bx = block.getX(), by = block.getZ();
        if (bx < this.x || bx >= this.x + this.width) return false;
        if (by < this.y || by >= this.y + this.height) return false;
        return true;
    }

    public boolean isNear(Block block, int distance) {
        final int bx = block.getX(), by = block.getZ();
        if (bx < this.x - distance || bx >= this.x + this.width + distance) return false;
        if (by < this.y - distance || by >= this.y + this.height + distance) return false;
        return true;
    }

    public boolean contains(Rectangle other) {
        if (other.x < x || other.x >= x + width) return false;
        if (other.y < y || other.y >= y + height) return false;
        if (other.x + other.width - 1 >= x + width) return false;
        if (other.y + other.height - 1 >= y + height) return false;
        return true;
    }

    public int getCenterX() {
        return x + (width / 2);
    }

    public int getCenterY() {
        return y + (height / 2);
    }

    public int getArea() {
        return width * height;
    }

    public int getWestBorder() {
        return x;
    }

    public int getEastBorder() {
        return x + width - 1;
    }

    public int getNorthBorder() {
        return y;
    }

    public int getSouthBorder() {
        return y + height - 1;
    }

    public Rectangle expand(int amount, BlockFace face) {
        int nx = this.x;
        int ny = this.y;
        int nwidth = this.width;
        int nheight = this.height;
        switch (face) {
        case NORTH:
            ny -= amount;
        case SOUTH:
            nheight += amount;
            break;
        case WEST:
            nx -= amount;
        case EAST:
            nwidth += amount;
            break;
        default:
            throw new IllegalArgumentException("Invalid direction: " + face);
        }
        return new Rectangle(nx, ny, nwidth, nheight);
    }

    public Rectangle outset(int amount) {
        int nx = this.x - amount;
        int ny = this.y - amount;
        int nwidth = this.width + amount + amount;
        int nheight = this.height + amount + amount;
        return new Rectangle(nx, ny, nwidth, nheight);
    }

    public Rectangle inset(int amount) {
        int nx = this.x + amount;
        int ny = this.y + amount;
        int nwidth = Math.max(1, this.width - amount - amount);
        int nheight = Math.max(1, this.height - amount - amount);
        return new Rectangle(nx, ny, nwidth, nheight);
    }
}
