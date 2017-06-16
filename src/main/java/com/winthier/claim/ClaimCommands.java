package com.winthier.claim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Getter @RequiredArgsConstructor
final class ClaimCommands {
    private final ClaimPlugin plugin;
    private int initialClaimRadius = 8;

    boolean claim(CommandSender sender, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (player == null) return false;
        String cmd = args.length == 0 ? null : args[0].toLowerCase();
        if (cmd == null) {
            Claim claim = plugin.getClaimAt(player.getLocation().getBlock());
            if (claim == null) {
                String world = player.getWorld().getName();
                Msg.info(player, "No claim here.");
                if (plugin.isClaimWorld(world) && plugin.getPlayerClaimsInWorld(player.getUniqueId(), world).isEmpty()) {
                    Msg.raw(player,
                            "Click here to make a ",
                            Msg.button("[New Claim]", "/claim new", "&a/claim new\n&rMake an new claim", "/claim new", ChatColor.GREEN),
                            ".");
                }
            } else {
                Msg.info(player, "This claim belongs to %s.", claim.getOwnerName());
            }
            for (Claim nearby: plugin.getNearbyClaims(player.getLocation().getBlock(), 64)) plugin.getSession(player).highlightClaim(nearby);
        } else if ("new".equals(cmd) && args.length == 1) {
            String world = player.getWorld().getName();
            if (!plugin.isClaimWorld(world)) {
                Msg.warn(player, "You cannot make claims in this world");
                return true;
            }
            if (!plugin.getPlayerClaimsInWorld(player.getUniqueId(), world).isEmpty()) {
                Msg.warn(player, "You already have a claim in this world");
                int worldIndex = plugin.getClaimWorlds().indexOf(world) + 1;
                if (worldIndex > 0) {
                    Msg.raw(player,
                            "Click here to go to your ",
                            Msg.button("[Claim Home]", "/claim home " + worldIndex, "&a/claim home " + worldIndex, "/claim home " + worldIndex, ChatColor.GREEN),
                            ".");
                }
                return true;
            }
            Block center = player.getLocation().getBlock();
            Rectangle rect = Rectangle.ofCornerBlocks(center.getRelative(-initialClaimRadius + 1, 0, -initialClaimRadius + 1),
                                                      center.getRelative(initialClaimRadius, 0, initialClaimRadius));
            if (!plugin.newClaimFits(world, rect)) {
                Msg.warn(player, "Your claim would get in the way of an existing claim");
                return true;
            }
            Claim newClaim = new Claim(player, rect);
            plugin.getClaims().add(newClaim);
            plugin.saveClaims();
            Msg.info(player, "Claim created");
            plugin.getSession(player).highlightClaim(newClaim);
        } else if ("list".equals(cmd) && args.length == 1) {
            Map<String, List<Claim>> claimMap = new LinkedHashMap<>();
            for (String world: plugin.getClaimWorlds()) claimMap.put(world, new ArrayList<>());
            List<Claim> playerClaims = plugin.getPlayerClaims(player.getUniqueId());
            int claimCount = playerClaims.size();
            for (Claim claim: playerClaims) {
                List<Claim> claimList = claimMap.get(claim.getWorld());
                if (claimList == null && plugin.isClaimWorld(claim.getWorld())) {
                    claimList = new ArrayList<>();
                    claimMap.put(claim.getWorld(), claimList);
                }
                claimList.add(claim);
            }
            if (claimCount == 0) {
                Msg.warn(player, "No claims found!");
            } else if (claimCount == 1) {
                Msg.info(player, "You have one claim");
            } else {
                Msg.info(player, "You have %d claims", claimCount);
            }
            for (String world: claimMap.keySet()) {
                List<Claim> claimList = claimMap.get(world);
                if (claimList == null || claimList.isEmpty()) continue;
                StringBuilder sb = new StringBuilder(" &9");
                String worldDisplayName = plugin.getWorldDisplayNames().get(world);
                if (worldDisplayName == null) worldDisplayName = world;
                sb.append(worldDisplayName);
                for (Claim claim: claimList) {
                    sb.append(" ").append(Msg.format("&r(&7%d&r,&7%d&r)", claim.getRectangle().getCenterX(), claim.getRectangle().getCenterY()));
                }
                int worldIndex = plugin.getClaimWorlds().indexOf(world);
                if (worldIndex < 0) {
                    Msg.raw(player, Msg.button(sb.toString(), null, null, null, ChatColor.GRAY));
                } else {
                    Msg.raw(player,
                            Msg.button(sb.toString(),
                                       null,
                                       "&a/claim home " + (worldIndex + 1),
                                       "/claim home " + (worldIndex + 1),
                                       ChatColor.GRAY));
                }
            }
        } else if ("home".equals(cmd) && args.length <= 2) {
            Claim claim;
            if (args.length >= 2) {
                int worldIndex;
                try {
                    worldIndex = Integer.parseInt(args[1]) - 1;
                } catch (NumberFormatException nfe) {
                    return false;
                }
                if (worldIndex < 0 || worldIndex >= plugin.getClaimWorlds().size()) return false;
                String worldName = plugin.getClaimWorlds().get(worldIndex);
                List<Claim> claimList = plugin.getPlayerClaimsInWorld(player.getUniqueId(), worldName);
                if (claimList.isEmpty()) return false;
                claim = claimList.get(0);
            } else {
                if (plugin.getClaimWorlds().isEmpty()) return false;
                List<Claim> claimList = plugin.getPlayerClaimsInWorld(player.getUniqueId(), plugin.getClaimWorlds().get(0));
                if (claimList.isEmpty()) return false;
                claim = claimList.get(0);
            }
            if (claim == null) return false;
            Block block = claim.getHomeBlock();
            if (block == null) return false;
            Location location = block.getLocation().add(0.5, 0.1, 0.5);
            Location playerLocation = player.getLocation();
            location.setYaw(playerLocation.getYaw());
            location.setPitch(playerLocation.getPitch());
            playerLocation.getWorld().playSound(playerLocation, Sound.ENTITY_ENDERMEN_TELEPORT, SoundCategory.PLAYERS, 0.35f, 1.2f);
            playerLocation.getWorld().spawnParticle(Particle.PORTAL, playerLocation.add(0.0, player.getHeight() * 0.5, 0.0), 32, 0, 0, 0, 0.5);
            player.teleport(location);
            location.getWorld().playSound(location, Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.1f, 1.2f);
            location.getWorld().spawnParticle(Particle.PORTAL, location.add(0.0, player.getHeight() * 0.5, 0.0), 32, 0, 0, 0, 0.5);
            Msg.info(player, "Teleported to your claim");
        } else {
            return false;
        }
        return true;
    }

    List<String> tabCompleteClaim(CommandSender sender, String[] args) {
        return null;
    }

    boolean claimAdmin(CommandSender sender, String[] args) {
        String cmd = args.length == 0 ? null : args[0].toLowerCase();
        Player player = sender instanceof Player ? (Player)sender : null;
        if (cmd == null) {
            return false;
        } else if ("reload".equals(cmd)) {
            plugin.flushCaches();
            plugin.readConfiguration();
            sender.sendMessage("Config files reloaded.");
        } else if ("save".equals(cmd)) {
            plugin.saveDefaultConfig();
            plugin.saveClaims();
            sender.sendMessage("Config files saved.");
        } else if ("near".equals(cmd) && (args.length == 1 || args.length == 2)) {
            if (player == null) return false;
            int dist;
            if (args.length >= 2) {
                dist = Integer.parseInt(args[1]);
            } else {
                dist = 128;
            }
            List<Claim> claims = plugin.getNearbyClaims(player.getLocation().getBlock(), dist);
            player.sendMessage("" + claims.size() + " claims within " + dist + " blocks:");
            for (Claim claim: claims) {
                plugin.getSession(player).highlightClaim(claim);
                player.sendMessage("- " + claim.getOwnerName());
            }
        } else if ("ignore".equals(cmd)) {
            if (player == null) return false;
            return ignoreClaims(player, Arrays.copyOfRange(args, 1, args.length));
        } else {
            return false;
        }
        return true;
    }

    boolean ignoreClaims(CommandSender sender, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (args.length > 1) return false;
        boolean newValue;
        if (args.length >= 1) {
            if ("on".equalsIgnoreCase(args[0])) {
                newValue = true;
            } else if ("off".equalsIgnoreCase(args[0])) {
                newValue = false;
            } else {
                return false;
            }
        } else {
            newValue = !plugin.getSession(player).isIgnoreClaims();
            plugin.getSession(player).setIgnoreClaims(newValue);
        }
        if (newValue) {
            player.sendMessage("Ignoring claims");
        } else {
            player.sendMessage("Respecting claims");
        }
        return true;
    }
}
