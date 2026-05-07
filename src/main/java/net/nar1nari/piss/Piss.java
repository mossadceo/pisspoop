package net.nar1nari.piss;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class Piss extends JavaPlugin implements Listener {
    private static final String DISPLAY_TAG = "piss-plugin-display";
    private static final Set<Material> LEGACY_DISPLAY_MATERIALS = Set.of(
            Material.YELLOW_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS_PANE
    );

    private final Set<UUID> pissingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<ScheduledTask> scheduledTasks = ConcurrentHashMap.newKeySet();

    private NamespacedKey displayKey;

    private String startMessage;
    private String cooldownMessage;
    private String noPermissionMessage;

    private int pissDuration;
    private int dropInterval;
    private double dropSpeed;
    private double dropGravity;
    private double dropSize;
    private double dropHitbox;

    private int puddleLifetime;
    private double puddleSize;
    private double puddleThickness;
    private double puddleRandomOffset;

    @Override
    public void onEnable() {
        displayKey = new NamespacedKey(this, "display");
        saveDefaultConfig();
        loadConfigValues();
        getServer().getPluginManager().registerEvents(this, this);
        scheduleLoadedChunkCleanup();
        getLogger().info("Piss plugin has been enabled.");
    }

    private void loadConfigValues() {
        startMessage = getConfig().getString("message.start", "§eYou started peeing...");
        cooldownMessage = getConfig().getString("message.cooldown", "§cYou're already peeing!");
        noPermissionMessage = getConfig().getString("message.no-permisson", "§cYou do not have permission to use this command.");

        pissDuration = getConfig().getInt("piss.duration", 200);
        dropInterval = getConfig().getInt("piss.drop-interval", 2);
        dropSpeed = getConfig().getDouble("piss.drop-speed", 0.3);
        dropGravity = getConfig().getDouble("piss.drop-gravity", -0.02);
        dropSize = getConfig().getDouble("piss.drop-size", 0.1);
        dropHitbox = getConfig().getDouble("piss.drop-hitbox", 0.2);

        puddleLifetime = getConfig().getInt("puddle.lifetime", 100);
        puddleSize = getConfig().getDouble("puddle.size", 0.6);
        puddleThickness = getConfig().getDouble("puddle.thickness", 0.01);
        puddleRandomOffset = getConfig().getDouble("puddle.random-offset", 2.0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("piss.reload")) {
                sender.sendMessage(noPermissionMessage);
                return true;
            }
            reloadConfig();
            loadConfigValues();
            sender.sendMessage("§e[PISS] Config reloaded.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (player.hasPermission("piss.use"))
            return pissCmd(player);
        else {
            player.sendMessage(noPermissionMessage);
            return true;
        }
    }

    private boolean pissCmd(Player player) {
        if (pissingPlayers.contains(player.getUniqueId())) {
            player.sendMessage(cooldownMessage);
            return true;
        }

        pissingPlayers.add(player.getUniqueId());
        player.sendMessage(startMessage);

        int interval = Math.max(1, dropInterval);
        trackTask(player.getScheduler().runAtFixedRate(this, new Consumer<>() {
            private int ticks = 0;

            @Override
            public void accept(ScheduledTask task) {
                if (!player.isOnline() || !player.isValid()) {
                    finishPiss(player, task);
                    return;
                }

                if (ticks >= pissDuration) {
                    finishPiss(player, task);
                    return;
                }
                spawnDrop(player, player.getLocation().getDirection().normalize());
                ticks += interval;
            }
        }, () -> pissingPlayers.remove(player.getUniqueId()), 1L, interval));

        return true;
    }

    private void finishPiss(Player player, ScheduledTask task) {
        task.cancel();
        scheduledTasks.remove(task);
        pissingPlayers.remove(player.getUniqueId());
    }

    private void spawnDrop(Player player, Vector dir) {
        double crotchHeight = player.getHeight() * 0.35;
        Location spawnPos = player.getLocation().clone()
                .add(0, crotchHeight, 0);

        ItemDisplay drop = player.getWorld().spawn(spawnPos, ItemDisplay.class);
        markDisplay(drop);
        drop.setItemStack(new ItemStack(Material.YELLOW_STAINED_GLASS));
        setScale(drop, dropSize);

        Vector velocity = dir.multiply(dropSpeed).add(new Vector(0, 0.1, 0));
        startDropTask(player, drop, spawnPos.clone(), velocity);
    }

    private void startDropTask(Player player, ItemDisplay drop, Location startLoc, Vector velocity) {
        trackTask(drop.getScheduler().runAtFixedRate(this, new Consumer<>() {
            private Location loc = startLoc.clone();

            @Override
            public void accept(ScheduledTask task) {
                if (!drop.isValid()) {
                    cancelTask(task);
                    return;
                }

                velocity.add(new Vector(0, dropGravity, 0));
                loc.add(velocity);

                if (!Bukkit.isOwnedByCurrentRegion(loc)) {
                    drop.teleportAsync(loc);
                    return;
                }

                for (var entity : loc.getWorld().getNearbyEntities(loc, dropHitbox, dropHitbox, dropHitbox)) {
                    if (entity.equals(player) || entity instanceof ItemDisplay) continue;
                    onEntityHit(drop, velocity);
                    cancelTask(task);
                    return;
                }

                if (loc.getBlock().getType().isSolid()) {
                    summonPuddle(loc);
                    removeDisplay(drop);
                    cancelTask(task);
                    return;
                }

                drop.teleportAsync(loc);
            }
        }, () -> removeTrackedDisplay(drop), 1L, 1L));
    }

    private void onEntityHit(ItemDisplay drop, Vector velocity) {
        setScale(drop, dropSize * 2);
        velocity.setX(0);
        velocity.setZ(0);
        velocity.setY(-0.02);

        trackTask(drop.getScheduler().runAtFixedRate(this, new Consumer<>() {
            @Override
            public void accept(ScheduledTask task) {
                if (!drop.isValid()) {
                    cancelTask(task);
                    return;
                }
                Location dLoc = drop.getLocation().add(0, -0.05, 0);
                if (!Bukkit.isOwnedByCurrentRegion(dLoc)) {
                    drop.teleportAsync(dLoc);
                    return;
                }

                if (dLoc.getBlock().getType().isSolid()) {
                    summonPuddle(dLoc);
                    removeDisplay(drop);
                    cancelTask(task);
                    return;
                }
                drop.teleportAsync(dLoc);
            }
        }, () -> removeTrackedDisplay(drop), 1L, 1L));
    }

    public void summonPuddle(Location loc) {
        Location puddleLoc = loc.getBlock().getLocation().add(0.5, 1.0, 0.5);
        puddleLoc.add((Math.random() - 0.5) * puddleRandomOffset, 0.1, (Math.random() - 0.5) * puddleRandomOffset);

        trackTask(Bukkit.getRegionScheduler().run(this, puddleLoc, task -> {
            spawnPuddle(puddleLoc);
            scheduledTasks.remove(task);
        }));
    }

    private void spawnPuddle(Location puddleLoc) {
        ItemDisplay puddle = puddleLoc.getWorld().spawn(puddleLoc, ItemDisplay.class);
        markDisplay(puddle);
        puddle.setItemStack(new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        setScale(puddle, puddleSize, puddleSize, puddleThickness);
        Transformation t = puddle.getTransformation();
        t.getLeftRotation().x = 1f;
        puddle.setTransformation(t);

        trackTask(puddle.getScheduler().runDelayed(this, task -> {
            removeDisplay(puddle);
            scheduledTasks.remove(task);
        }, () -> removeTrackedDisplay(puddle), Math.max(1L, puddleLifetime)));
    }

    private void markDisplay(ItemDisplay display) {
        display.setPersistent(false);
        display.addScoreboardTag(DISPLAY_TAG);
        display.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isPluginDisplay(Entity entity) {
        if (!(entity instanceof ItemDisplay display)) {
            return false;
        }

        if (display.getScoreboardTags().contains(DISPLAY_TAG)
                || display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
            return true;
        }

        ItemStack item = display.getItemStack();
        return item != null && LEGACY_DISPLAY_MATERIALS.contains(item.getType());
    }

    private void removeDisplay(ItemDisplay display) {
        removeTrackedDisplay(display);
        display.remove();
    }

    private void removeTrackedDisplay(ItemDisplay display) {
        display.removeScoreboardTag(DISPLAY_TAG);
    }

    private void trackTask(ScheduledTask task) {
        scheduledTasks.add(task);
    }

    private void cancelTask(ScheduledTask task) {
        task.cancel();
        scheduledTasks.remove(task);
    }

    private void scheduleLoadedChunkCleanup() {
        trackTask(Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {
            cleanupLoadedChunks();
            scheduledTasks.remove(task);
        }, 1L));
    }

    private void cleanupLoadedChunks() {
        for (var world : getServer().getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                trackTask(Bukkit.getRegionScheduler().run(this, world, chunk.getX(), chunk.getZ(), task -> {
                    cleanupChunk(chunk);
                    scheduledTasks.remove(task);
                }));
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        cleanupChunk(event.getChunk());
    }

    private void cleanupChunk(Chunk chunk) {
        for (var entity : chunk.getEntities()) {
            if (isPluginDisplay(entity)) {
                entity.remove();
            }
        }
    }

    private void cancelAllTasks() {
        for (var task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
    }

    private void setScale(ItemDisplay display, double scale) {
        Transformation t = display.getTransformation();
        t.getScale().set(scale);
        display.setTransformation(t);
    }

    private void setScale(ItemDisplay display, double x, double y, double z) {
        Transformation t = display.getTransformation();
        t.getScale().set(x, y, z);
        display.setTransformation(t);
    }

    @Override
    public void onDisable() {
        cancelAllTasks();
        pissingPlayers.clear();
        getLogger().info("Piss plugin has been disabled.");
    }
}
