package com.winthier.claim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class ClaimPlugin extends JavaPlugin implements Listener {
    public static final UUID PUBLIC_USER = new UUID(0L, 0L);
    private List<Claim> claims;
    private final HashMap<UUID, Session> sessions = new HashMap<>();
    private final Random random = new Random(System.currentTimeMillis());
    // Settings
    private final List<String> claimWorlds = new ArrayList<>();
    private final Map<String, String> worldDisplayNames = new HashMap<>();
    private boolean protectUnclaimed;
    private int minimumClaimDistance;
    private int claimBlocksPerMinute;
    // Outsourced class instances
    private ClaimListener claimListener;
    private ClaimCommands claimCommands;

    @Override
    public void onEnable() {
        readConfiguration();
        getServer().getPluginManager().registerEvents(this, this);
        claimListener = new ClaimListener(this);
        claimCommands = new ClaimCommands(this);
        getServer().getPluginManager().registerEvents(claimListener, this);
        getCommand("claim").setExecutor((snd, cmd, als, arg) -> claimCommands.claim(snd, arg));
        getCommand("claim").setTabCompleter((snd, cmd, als, arg) -> claimCommands.tabCompleteClaim(snd, arg));
        getCommand("claimadmin").setExecutor((snd, cmd, als, arg) -> claimCommands.claimAdmin(snd, arg));
        getCommand("ignoreclaims").setExecutor((snd, cmd, als, arg) -> claimCommands.ignoreClaims(snd, arg));
        getServer().getScheduler().runTaskTimer(this, () -> rewardClaimBlocks(), 20 * 60, 20 * 60);
    }

    @Override
    public void onDisable() {
        flushCaches();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    void flushCaches() {
        sessions.clear();
        claims = null;
    }

    void readConfiguration() {
        claimWorlds.clear();
        claimWorlds.addAll(getConfig().getStringList("ClaimWorlds"));
        worldDisplayNames.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("WorldDisplayNames");
        for (String key: section.getKeys(false)) worldDisplayNames.put(key, section.getString(key));
        protectUnclaimed = getConfig().getBoolean("ProtectUnclaimed");
        minimumClaimDistance = getConfig().getInt("MinimumClaimDistance");
    }

    Session getSession(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            session = new Session(this, player);
            sessions.put(player.getUniqueId(), session);
        }
        return session;
    }

    List<Claim> getClaims() {
        if (claims == null) {
            YamlConfiguration serialized = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "claims.yml"));
            claims = serialized.getMapList("claims").stream().map(map -> new Claim(map)).collect(Collectors.toList());
        }
        return claims;
    }

    void saveClaims() {
        if (claims == null) return;
        YamlConfiguration serialized = new YamlConfiguration();
        serialized.set("claims", claims.stream().map(claim -> claim.serialize()).collect(Collectors.toList()));
        try {
            serialized.save(new File(getDataFolder(), "claims.yml"));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    boolean isClaimWorld(World world) {
        return claimWorlds.contains(world.getName());
    }

    boolean isClaimWorld(String worldName) {
        return claimWorlds.contains(worldName);
    }

    public Claim getTopLevelClaimAt(Block block) {
        for (Claim claim: getClaims()) {
            if (!claim.getWorld().equals(block.getWorld().getName())) continue;
            if (claim.getRectangle().contains(block)) return claim;
        }
        return null;
    }

    public Claim getClaimAt(Block block) {
        Claim claim = getTopLevelClaimAt(block);
        if (claim == null) return null;
        Claim superclaim = null;
        while (superclaim != claim) {
            superclaim = claim;
            for (Claim subclaim: claim.getSubclaims()) {
                if (subclaim.getRectangle().contains(block)) {
                    claim = subclaim;
                }
            }
        }
        return claim;
    }

    public List<Claim> getPlayerClaims(UUID uuid) {
        return getClaims().stream().filter(claim -> claim.getOwner().equals(uuid)).collect(Collectors.toList());
    }

    public List<Claim> getPlayerClaimsInWorld(UUID uuid, String worldName) {
        return getClaims().stream().filter(claim -> claim.getOwner().equals(uuid) && claim.getWorld().equals(worldName)).collect(Collectors.toList());
    }

    public boolean newClaimFits(String worldName, Rectangle newRectangle) {
        newRectangle = newRectangle.outset(minimumClaimDistance);
        for (Claim claim: getClaims()) {
            if (!claim.getWorld().equals(worldName)) continue;
            if (claim.getRectangle().intersects(newRectangle)) return false;
        }
        return true;
    }

    public boolean resizedClaimFits(Claim existingClaim, Rectangle newRectangle) {
        for (Claim claim: getClaims()) {
            if (claim == existingClaim) continue;
            if (!claim.getWorld().equals(existingClaim.getWorld())) continue;
            if (claim.getRectangle().intersects(newRectangle)) return false;
        }
        return true;
    }

    public List<Claim> getNearbyClaims(Block block, int distance) {
        return getClaims().stream().filter(claim -> claim.getRectangle().isNear(block, distance)).collect(Collectors.toList());
    }

    void rewardClaimBlocks() {
        HashSet<Claim> occupiedClaims = new HashSet<>();
        for (Player player: getServer().getOnlinePlayers()) {
            Claim claim = getTopLevelClaimAt(player.getLocation().getBlock());
            if (claim != null && claim.getTrust(player.getUniqueId()).implies(Trust.BUILD)) occupiedClaims.add(claim);
        }
        if (occupiedClaims.isEmpty()) return;
        for (Claim claim: occupiedClaims) {
            claim.setClaimBlocks(claim.getClaimBlocks() + claimBlocksPerMinute);
        }
        saveClaims();
    }
}
