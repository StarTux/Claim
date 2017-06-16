package com.winthier.claim;

import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@Getter @Setter @ToString(exclude = { "superclaim" })
public final class Claim {
    private UUID owner;
    private String world;
    private Rectangle rectangle;
    private long creationTime;
    private final Map<UUID, Trust> trusted = new HashMap<>();
    private final List<Claim> subclaims = new ArrayList<>();
    private transient Claim superclaim = null;
    private final EnumMap<Option, Boolean> options = new EnumMap<>(Option.class);
    private Home home;
    private final Home origin;
    private int claimBlocks;

    enum Option {
        PVP, FIRE_SPREAD, TNT_DAMAGE, CREEPER_DAMAGE;
        final String key;
        Option() {
            this.key = name().toLowerCase();
        }
    }

    @Value @RequiredArgsConstructor
    class Home {
        private final int x, y, z;

        List<Integer> serialize() {
            return Arrays.asList(x, y, z);
        }

        Home(Block block) {
            this(block.getX(), block.getY(), block.getZ());
        }

        Home(List<Integer> serialized) {
            this(serialized.get(0), serialized.get(1), serialized.get(2));
        }

        Block getBlock(World bukkitWorld) {
            return bukkitWorld.getBlockAt(x, y, z);
        }
    }

    Claim(UUID owner, String world, Rectangle rectangle, long creationTime) {
        this.owner = owner;
        this.world = world;
        this.rectangle = rectangle;
        this.claimBlocks = rectangle.getArea();
        this.creationTime = creationTime;
        this.home = new Home(rectangle.getCenterX(), 64, rectangle.getCenterY());
        this.origin = this.home;
    }

    Claim(Player player, Rectangle rectangle) {
        this.owner = player.getUniqueId();
        this.rectangle = rectangle;
        this.claimBlocks = rectangle.getArea();
        this.world = player.getWorld().getName();
        this.creationTime = System.currentTimeMillis();
        this.home = new Home(player.getLocation().getBlock());
        this.origin = this.home;
    }

    boolean contentEquals(Claim that) {
        boolean result = this.owner.equals(that.owner)
            && this.world.equals(that.world)
            && this.rectangle.equals(that.rectangle)
            && this.creationTime == that.creationTime
            && this.trusted.equals(that.trusted)
            && this.options.equals(that.options)
            && this.home.equals(that.home)
            && this.claimBlocks == that.claimBlocks;
        if (!result) return false;
        if (subclaims.size() != that.subclaims.size()) return false;
        for (int i = 0; i < subclaims.size(); i += 1) {
            if (!subclaims.get(i).contentEquals(that.subclaims.get(i))) return false;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    Claim(Map map) {
        owner = UUID.fromString((String)map.get("owner"));
        world = (String)map.get("world");
        rectangle = new Rectangle(map);
        creationTime = ((Number)map.get("creation_time")).longValue();
        trusted.putAll(((Map<String, String>)map.get("trusted")).entrySet().stream().collect(Collectors.toMap(e -> UUID.fromString(e.getKey()), e -> Trust.valueOf(e.getValue()))));
        ((List<Map>)map.get("subclaims")).stream().forEach(sub -> {
                Claim subclaim = new Claim(sub); subclaims.add(subclaim); subclaim.setSuperclaim(this);
            });
        for (Option option: Option.values()) {
            if (map.containsKey(option.key)) {
                options.put(option, (Boolean)map.get(option.key));
            }
        }
        home = new Home((List<Integer>)map.get("home"));
        origin = new Home((List<Integer>)map.get("origin"));
        claimBlocks = ((Number)map.get("claim_blocks")).intValue();
    }

    Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("owner", owner.toString());
        result.put("world", world);
        rectangle.serialize(result);
        result.put("creation_time", creationTime);
        result.put("trusted", trusted.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().name())));
        result.put("subclaims", subclaims.stream().map(claim -> claim.serialize()).collect(Collectors.toList()));
        for (Option option: Option.values()) {
            if (options.containsKey(option)) {
                result.put(option.key, options.get(option));
            }
        }
        result.put("home", home.serialize());
        result.put("origin", origin.serialize());
        result.put("claim_blocks", claimBlocks);
        return result;
    }

    private static Map<String, Object> mkMap(UUID uuid, Trust trust) {
        Map<String, Object> result = new HashMap<>();
        result.put("uuid", uuid.toString());
        result.put("trust", trust.name());
        return result;
    }

    public String getOwnerName() {
        return PlayerCache.nameForUuid(owner);
    }

    public Trust getTrust(UUID uuid) {
        if (uuid.equals(owner)) return Trust.OWNER;
        Trust trust = getTrusted().get(uuid);
        if (trust == null) trust = Trust.NONE;
        Claim currentClaim = this;
        while (currentClaim.getSuperclaim() != null) {
            currentClaim = currentClaim.getSuperclaim();
            if (uuid.equals(currentClaim.getOwner())) return Trust.OWNER;
            if (owner.equals(currentClaim.getOwner())) {
                Trust currentTrust = currentClaim.getTrusted().get(uuid);
                if (currentTrust.implies(trust)) {
                    trust = currentTrust;
                }
            }
        }
        return trust;
    }

    public boolean getOption(Option option) {
        Boolean setting = options.get(option);
        if (setting != null) return setting;
        if (superclaim != null) {
            return false;
        } else {
            return superclaim.getOption(option);
        }
    }

    public Claim getTopLevelClaim() {
        Claim currentClaim = this;
        while (currentClaim.superclaim != null) currentClaim = currentClaim.superclaim;
        return currentClaim;
    }

    public Block getHomeBlock() {
        World bukkitWorld = Bukkit.getServer().getWorld(world);
        if (bukkitWorld == null) return null;
        Block block = home.getBlock(bukkitWorld);
        if (block.getType().isSolid()) {
            while (block.getType().isSolid()) block = block.getRelative(0, 1, 0);
        } else {
            while (block.getY() > 0 && !block.getRelative(0, -1, 0).getType().isSolid()) block = block.getRelative(0, -1, 0);
        }
        return block;
    }

    public boolean isAdminClaim() {
        return ClaimPlugin.PUBLIC_USER.equals(owner);
    }
}
