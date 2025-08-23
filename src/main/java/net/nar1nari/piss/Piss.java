package net.nar1nari.piss;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Piss extends JavaPlugin {
    private final Set<UUID> pissingPlayers = new HashSet<>();

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
        saveDefaultConfig();
        loadConfigValues();
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

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= pissDuration) {
                    cancel();
                    pissingPlayers.remove(player.getUniqueId());
                    return;
                }
                spawnDrop(player, player.getLocation().getDirection().normalize());
                ticks += dropInterval;
            }
        }.runTaskTimer(this, 0L, dropInterval);

        return true;
    }

    private void spawnDrop(Player player, Vector dir) {
        double crotchHeight = player.getHeight() * 0.35;
        Location spawnPos = player.getLocation().clone()
                .add(0, crotchHeight, 0);

        ItemDisplay drop = player.getWorld().spawn(spawnPos, ItemDisplay.class);
        drop.setItemStack(new ItemStack(Material.YELLOW_STAINED_GLASS));
        setScale(drop, dropSize);

        Vector velocity = dir.multiply(dropSpeed).add(new Vector(0, 0.1, 0));
        startDropTask(player, drop, spawnPos.clone(), velocity);
    }

    private void startDropTask(Player player, ItemDisplay drop, Location startLoc, Vector velocity) {
        new BukkitRunnable() {
            Location loc = startLoc.clone();

            @Override
            public void run() {
                velocity.add(new Vector(0, dropGravity, 0));
                loc.add(velocity);

                for (var entity : loc.getWorld().getNearbyEntities(loc, dropHitbox, dropHitbox, dropHitbox)) {
                    if (entity.equals(player) || entity instanceof ItemDisplay) continue;
                    onEntityHit(drop, velocity);
                    cancel();
                    return;
                }

                if (loc.getBlock().getType().isSolid()) {
                    summonPuddle(loc);
                    drop.remove();
                    cancel();
                    return;
                }

                drop.teleport(loc);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void onEntityHit(ItemDisplay drop, Vector velocity) {
        setScale(drop, dropSize * 2);
        velocity.setX(0);
        velocity.setZ(0);
        velocity.setY(-0.02);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!drop.isValid()) {
                    cancel();
                    return;
                }
                Location dLoc = drop.getLocation().add(0, -0.05, 0);
                if (dLoc.getBlock().getType().isSolid()) {
                    summonPuddle(dLoc);
                    drop.remove();
                    cancel();
                    return;
                }
                drop.teleport(dLoc);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    public void summonPuddle(Location loc) {
        Location puddleLoc = loc.getBlock().getLocation().add(0.5, 1.0, 0.5);
        puddleLoc.add((Math.random() - 0.5) * puddleRandomOffset, 0.1, (Math.random() - 0.5) * puddleRandomOffset);

        ItemDisplay puddle = loc.getWorld().spawn(puddleLoc, ItemDisplay.class);
        puddle.setItemStack(new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        setScale(puddle, puddleSize, puddleSize, puddleThickness);
        Transformation t = puddle.getTransformation();
        t.getLeftRotation().x = 1f;
        puddle.setTransformation(t);

        new BukkitRunnable() {
            @Override
            public void run() {
                puddle.remove();
            }
        }.runTaskLater(this, puddleLifetime);
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
        getLogger().info("Piss plugin has been disabled.");
    }
}
