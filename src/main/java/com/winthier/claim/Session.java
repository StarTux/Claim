package com.winthier.claim;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@Getter @Setter
final class Session {
    private final ClaimPlugin plugin;
    private final Player player;
    private final IdentityHashMap<Claim, Highlight> highlights = new IdentityHashMap<>();
    private boolean ignoreClaims;
    private static final int VIEW_DIST = 96;

    Session(ClaimPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Value
    static class Coordinate {
        private final int x, y, z;
        private final BlockFace face;
        private final boolean dot;
        Coordinate(Block block, BlockFace face, boolean dot) {
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
            this.face = face;
            this.dot = dot;
        }
        Block getBlock(World world) {
            return world.getBlockAt(x, y, z);
        }
    }

    void highlightClaim(Claim claim) {
        Highlight highlight = highlights.get(claim);
        if (highlight == null) {
            highlight = new Highlight(claim);
            highlights.put(claim, highlight);
            highlight.showBlocks();
            highlight.showParticles();
            highlight.runTaskTimer(plugin, 1, 1);
        } else {
            highlight.countdown = Highlight.COUNTDOWN;
        }
    }

    class Highlight extends BukkitRunnable {
        static final int COUNTDOWN = 20 * 10;
        private final ArrayList<Coordinate> solidBlocks = new ArrayList<>();
        private Trust trust;
        private Claim claim;
        private Rectangle rectangle;
        private int countdown = COUNTDOWN;
        private int ticksLived;

        Highlight(Claim claim) {
            this.claim = claim;
            this.rectangle = claim.getRectangle();
            trust = claim.getTrust(player.getUniqueId());
            int north = claim.getRectangle().getNorthBorder();
            int east = claim.getRectangle().getEastBorder();
            int south = claim.getRectangle().getSouthBorder();
            int west = claim.getRectangle().getWestBorder();
            World world = player.getWorld();
            Block playerBlock = player.getLocation().getBlock();
            int minX = Math.max(playerBlock.getX() - VIEW_DIST, west);
            int maxX = Math.min(playerBlock.getX() + VIEW_DIST, east);
            int minZ = Math.max(playerBlock.getZ() - VIEW_DIST, north);
            int maxZ = Math.min(playerBlock.getZ() + VIEW_DIST, south);
            int cx = claim.getRectangle().getCenterX();
            int cz = claim.getRectangle().getCenterY();
            int y = playerBlock.getY();
            if (minZ <= north && north <= maxZ) {
                for (int x = minX + 1; x < maxX; x += 1) {
                    boolean dot = x < cx ? (x & 1) == (west & 1) : (x & 1) == (east & 1);
                    solidBlocks.add(new Coordinate(findSolidBlock(world, x, y, north), BlockFace.NORTH, dot));
                }
                // Corners
                if (minX <= west && west <= maxX) {
                    solidBlocks.add(new Coordinate(findSolidBlock(world, west, y, north), BlockFace.NORTH_WEST, true));
                }
                if (minX <= east && east <= maxX) {
                    solidBlocks.add(new Coordinate(findSolidBlock(world, east, y, north), BlockFace.NORTH_EAST, true));
                }
            }
            if (minZ <= south && south <= maxZ) {
                for (int x = minX + 1; x < maxX; x += 1) {
                    boolean dot = x < cx ? (x & 1) == (west & 1) : (x & 1) == (east & 1);
                    solidBlocks.add(new Coordinate(findSolidBlock(world, x, y, south), BlockFace.SOUTH, dot));
                }
                // Corners
                if (minX <= west && west <= maxX) {
                    solidBlocks.add(new Coordinate(findSolidBlock(world, west, y, south), BlockFace.SOUTH_WEST, true));
                }
                if (minX <= east && east <= maxX) {
                    solidBlocks.add(new Coordinate(findSolidBlock(world, east, y, south), BlockFace.SOUTH_EAST, true));
                }
            }
            if (minX <= west && west <= maxX) {
                for (int z = minZ + 1; z < maxZ; z += 1) {
                    boolean dot = z < cz ? (z & 1) == (north & 1) : (z & 1) == (south & 1);
                    solidBlocks.add(new Coordinate(findSolidBlock(world, west, y, z), BlockFace.WEST, dot));
                }
            }
            if (minX <= east && east <= maxX) {
                for (int z = minZ + 1; z < maxZ; z += 1) {
                    boolean dot = z < cz ? (z & 1) == (north & 1) : (z & 1) == (south & 1);
                    solidBlocks.add(new Coordinate(findSolidBlock(world, east, y, z), BlockFace.EAST, dot));
                }
            }
        }

        Block findSolidBlock(World world, int x, int y, int z) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() || block.isLiquid()) {
                Block newBlock = block.getRelative(0, 1, 0);
                while (newBlock.getType().isSolid() || newBlock.isLiquid()) {
                    block = newBlock;
                    newBlock = block.getRelative(0, 1, 0);
                }
            } else {
                while (block.getY() > 0 && !block.getType().isSolid() && !block.isLiquid()) block = block.getRelative(0, -1, 0);
            }
            return block;
        }

        void showParticles() {
            World world = player.getWorld();
            Block playerBlock = player.getLocation().getBlock();
            for (Coordinate coord: solidBlocks) {
                if (plugin.getRandom().nextBoolean()) continue;
                double dx = 0.5 + (double)coord.face.getModX() * 0.5;
                double dz = 0.5 + (double)coord.face.getModZ() * 0.5;
                if (trust.implies(Trust.OWNER)) {
                    player.spawnParticle(Particle.FIREWORKS_SPARK, coord.getBlock(world).getLocation().add(dx, 3.5, dz), 1, 0, 0, 0, 0);
                } else if (trust.implies(Trust.BUILD)) {
                    player.spawnParticle(Particle.VILLAGER_HAPPY, coord.getBlock(world).getLocation().add(dx, 2.5, dz), 1, 0, 0, 0, 0);
                } else {
                    player.spawnParticle(Particle.REDSTONE, coord.getBlock(world).getLocation().add(dx, 2.5, dz), 1, 0, 0, 0, 0);
                }
            }
        }

        void showBlocks() {
            World world = player.getWorld();
            Block playerBlock = player.getLocation().getBlock();
            Material mat;
            if (!trust.implies(Trust.BUILD)) {
                mat = Material.REDSTONE_BLOCK;
            } else if (!trust.implies(Trust.OWNER)) {
                mat = Material.EMERALD_BLOCK;
            } else {
                mat = Material.GOLD_BLOCK;
            }
            for (Coordinate coord: solidBlocks) {
                if (!coord.dot) continue;
                switch (coord.face) {
                case NORTH: case SOUTH: case EAST: case WEST:
                    player.sendBlockChange(coord.getBlock(world).getLocation(), mat, (byte)0);
                    break;
                default:
                    player.sendBlockChange(coord.getBlock(world).getLocation(), Material.IRON_BLOCK, (byte)0);
                    break;
                }
            }
        }

        void restoreBlocks() {
            World world = player.getWorld();
            Block playerBlock = player.getLocation().getBlock();
            for (Coordinate coord: solidBlocks) {
                if (Math.abs(playerBlock.getX() - coord.x) > VIEW_DIST) continue;
                if (Math.abs(playerBlock.getY() - coord.y) > VIEW_DIST) continue;
                if (Math.abs(playerBlock.getZ() - coord.z) > VIEW_DIST) continue;
                if (!coord.dot) continue;
                Block block = coord.getBlock(world);
                player.sendBlockChange(block.getLocation(), block.getType(), (byte)block.getData());
            }
        }

        @Override
        public void run() {
            if (!player.isValid() || !claim.getWorld().equals(player.getWorld().getName())) {
                cancel();
                highlights.remove(claim);
                return;
            }
            countdown -= 1;
            if (countdown <= 0 || !rectangle.equals(claim.getRectangle())) {
                cancel();
                restoreBlocks();
                highlights.remove(claim);
                return;
            } else {
                ticksLived += 1;
                if (ticksLived % 20 == 0) showParticles();
            }
        }
    }
}
