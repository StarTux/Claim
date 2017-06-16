package com.winthier.claim;

import java.util.Iterator;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.spigotmc.event.entity.EntityMountEvent;

@RequiredArgsConstructor
public final class ClaimListener implements Listener {
    private final ClaimPlugin plugin;
    private static final ItemStack CLAIM_TOOL = new ItemStack(Material.GOLD_SPADE);

    private static boolean isOwner(Player player, Entity entity) {
        if (!(entity instanceof Tameable)) return false;
        Tameable tameable = (Tameable)entity;
        if (!tameable.isTamed()) return false;
        AnimalTamer owner = tameable.getOwner();
        if (owner == null) return false;
        if (owner.getUniqueId().equals(player.getUniqueId())) return true;
        return false;
    }

    private static boolean isHostile(Entity e) {
        switch (e.getType()) {
        case CREEPER:
        case SKELETON:
        case SPIDER:
        case GIANT:
        case ZOMBIE:
        case SLIME:
        case GHAST:
        case PIG_ZOMBIE:
        case ENDERMAN:
        case CAVE_SPIDER:
        case SILVERFISH:
        case BLAZE:
        case MAGMA_CUBE:
        case ENDER_DRAGON:
        case WITHER:
        case WITCH:
        case ENDERMITE:
        case GUARDIAN:
            return true;
        default:
            return e instanceof Monster;
        }
    }

    private static Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player)damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile)damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player)projectile.getShooter();
            }
        }
        return null;
    }

    private boolean autoCheckAction(Player player, Block block, Trust requiredTrust, Cancellable event) {
        if (plugin.getSession(player).isIgnoreClaims()) return true;
        Claim claim = plugin.getClaimAt(block);
        if (claim == null) {
            if (!plugin.isClaimWorld(block.getWorld())) return true;
            if (plugin.isProtectUnclaimed()) {
                if (event != null) event.setCancelled(true);
                return false;
            } else {
                return true;
            }
        } else {
            Trust trust = claim.getTrust(player.getUniqueId());
            if (!trust.implies(requiredTrust)) {
                if (event != null) event.setCancelled(true);
                return false;
            }
        }
        return true;
    }

    private boolean autoCheckOption(Block block, Claim.Option requiredOption, Cancellable event) {
        Claim claim = plugin.getClaimAt(block);
        if (claim == null) {
            if (!plugin.isClaimWorld(block.getWorld())) return true;
            if (plugin.isProtectUnclaimed()) {
                if (event != null) event.setCancelled(true);
                return false;
            }
        } else {
            if (!claim.getOption(requiredOption)) {
                if (event != null) event.setCancelled(true);
                return false;
            }
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        autoCheckAction(event.getPlayer(), event.getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        autoCheckAction(event.getPlayer(), event.getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        autoCheckAction((Player)event.getEntity(), event.getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        final Entity damagee = event.getEntity();
        if (isHostile(damagee)) return;
        final Player player = getPlayerDamager(event.getDamager());
        if (player != null) {
            if (isOwner(player, damagee)) return;
            if (damagee instanceof Player) {
                autoCheckOption(damagee.getLocation().getBlock(), Claim.Option.PVP, event);
            } else {
                autoCheckAction(player, damagee.getLocation().getBlock(), Trust.BUILD, event);
            }
        } else {
            if (damagee instanceof Player) return;
            switch (event.getDamager().getType()) {
            case PRIMED_TNT:
            case MINECART_TNT:
                autoCheckOption(damagee.getLocation().getBlock(), Claim.Option.TNT_DAMAGE, event);
                break;
            case CREEPER:
                autoCheckOption(damagee.getLocation().getBlock(), Claim.Option.CREEPER_DAMAGE, event);
                break;
            default:
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof InventoryHolder) {
            autoCheckAction(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), Trust.CHEST, event);
        } else {
            autoCheckAction(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), Trust.BUILD, event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        autoCheckAction(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        if (isOwner(event.getPlayer(), event.getEntity())) return;
        autoCheckAction(event.getPlayer(), event.getEntity().getLocation().getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player)event.getEntity();
        if (isOwner(player, event.getMount())) return;
        autoCheckAction(player, event.getMount().getLocation().getBlock(), Trust.USE, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        if (isOwner(event.getPlayer(), event.getEntity())) return;
        autoCheckAction(event.getPlayer(), event.getEntity().getLocation().getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock()) return;
        final Block block = event.getClickedBlock();
        final Player player = event.getPlayer();
        // Consider soil trampling
        if (event.getAction() == Action.PHYSICAL) {
            if (block.getType() == Material.SOIL) {
                autoCheckAction(event.getPlayer(), event.getClickedBlock(), Trust.BUILD, event);
            }
        } else if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            switch (block.getType()) {
            case LEVER:
            case STONE_BUTTON:
            case WOOD_BUTTON:
            case BED_BLOCK:
            case ACACIA_DOOR:
            case BIRCH_DOOR:
            case DARK_OAK_DOOR:
            case IRON_DOOR_BLOCK:
            case IRON_TRAPDOOR:
            case JUNGLE_DOOR:
            case SPRUCE_DOOR:
            case TRAP_DOOR:
            case WOOD_DOOR: // wtf
            case WOODEN_DOOR: // wtf
            case BIRCH_FENCE_GATE:
            case DARK_OAK_FENCE_GATE:
            case FENCE_GATE:
            case JUNGLE_FENCE_GATE:
            case SPRUCE_FENCE_GATE:
                if (!autoCheckAction(player, block, Trust.USE, null)) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                }
                break;
            case ANVIL:
            case CHEST:
            case TRAPPED_CHEST:
                if (!autoCheckAction(player, block, Trust.CHEST, null)) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                }
                break;
            case WORKBENCH:
            case ENCHANTMENT_TABLE:
            case ENDER_CHEST:
                break;
            default:
                if (!autoCheckAction(player, block, Trust.BUILD, null)) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                }
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            autoCheckAction(player, block, Trust.BUILD, event);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            autoCheckAction((Player)event.getRemover(), event.getEntity().getLocation().getBlock(), Trust.BUILD, event);
        } else {
            switch (event.getRemover().getType()) {
            case PRIMED_TNT:
            case MINECART_TNT:
                autoCheckOption(event.getEntity().getLocation().getBlock(), Claim.Option.TNT_DAMAGE, event);
                break;
            case CREEPER:
                autoCheckOption(event.getEntity().getLocation().getBlock(), Claim.Option.CREEPER_DAMAGE, event);
                break;
            default:
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingPlace(HangingPlaceEvent event) {
        autoCheckAction(event.getPlayer(), event.getEntity().getLocation().getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.isClaimWorld(event.getEntity().getWorld())) return;
        switch (event.getCause()) {
        case EXPLOSION:
            event.setCancelled(true);
            break;
        default:
            return;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        autoCheckAction(event.getPlayer(), event.getBlockClicked().getLocation().getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        autoCheckAction(event.getPlayer(), event.getBlockClicked().getLocation().getBlock(), Trust.BUILD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityExplode(EntityExplodeEvent event) {
        switch (event.getEntity().getType()) {
        case CREEPER:
            for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
                if (!autoCheckOption(iter.next(), Claim.Option.CREEPER_DAMAGE, null)) iter.remove();
            }
            break;
        case PRIMED_TNT:
        case MINECART_TNT:
        default:
            for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
                if (!autoCheckOption(iter.next(), Claim.Option.TNT_DAMAGE, null)) iter.remove();
            }
            break;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Iterator<Block> iter = event.blockList().iterator(); iter.hasNext();) {
            if (!autoCheckOption(iter.next(), Claim.Option.TNT_DAMAGE, null)) iter.remove();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBurn(BlockBurnEvent event) {
        autoCheckOption(event.getBlock(), Claim.Option.FIRE_SPREAD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockIgnite(BlockIgniteEvent event) {
        switch (event.getCause()) {
        case FLINT_AND_STEEL: return;
        default: break;
        }
        autoCheckOption(event.getBlock(), Claim.Option.FIRE_SPREAD, event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Player player = getPlayerDamager(event.getCombuster());
        if (player == null) return;
        Player damagee = event.getEntity() instanceof Player ? (Player)event.getEntity() : null;
        if (damagee == null) {
            autoCheckAction(player, event.getEntity().getLocation().getBlock(), Trust.BUILD, event);
        } else { // PVP
            if (player.equals(damagee)) return;
            autoCheckOption(event.getEntity().getLocation().getBlock(), Claim.Option.PVP, event);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) return;
        Player player = (Player)projectile.getShooter();
        if (event.getHitBlock() != null) {
            if (!autoCheckAction(player, event.getHitBlock(), Trust.BUILD, null)) projectile.remove();
        }
        if (event.getHitEntity() != null) {
            Entity entity = event.getHitEntity();
            if (!isHostile(entity)) {
                if (!autoCheckAction(player, event.getHitEntity().getLocation().getBlock(), Trust.BUILD, null)) projectile.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Player player = getPlayerDamager(event.getEntity());
        if (player == null) return;
        autoCheckAction(player, event.getBlock(), Trust.BUILD, event);
    }
}
