package net.mossadceo.piss;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class Piss extends JavaPlugin implements Listener {
    private static final String DISPLAY_TAG = "piss-plugin-display";
    private static final String POOP_PILE_TAG = "piss-plugin-poop-pile";
    private static final int POOP_DURATION = 80;
    private static final int POOP_INTERVAL = 5;
    private static final int POOP_PILE_LIFETIME = 200;
    private static final int POOP_STAIN_LIFETIME = 60;
    private static final double POOP_DROP_SPEED = 0.35;
    private static final double POOP_DROP_SIZE = 0.18;
    private static final double POOP_COLLECT_RADIUS = 1.6;
    private static final double POOP_PILE_RADIUS = 0.35;
    private static final Set<Material> POOP_BLOCKS = Set.of(
            Material.BROWN_CONCRETE,
            Material.BROWN_CONCRETE_POWDER,
            Material.BROWN_TERRACOTTA,
            Material.BROWN_WOOL,
            Material.BROWN_MUSHROOM_BLOCK
    );
    private static final Set<Material> LEGACY_DISPLAY_MATERIALS = Set.of(
            Material.YELLOW_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS_PANE
    );

    private final Set<UUID> pissingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> poopingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> readyPoopPiles = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> poopCooldowns = new ConcurrentHashMap<>();
    private final Set<ScheduledTask> scheduledTasks = ConcurrentHashMap.newKeySet();

    private NamespacedKey displayKey;
    private NamespacedKey excrementKey;
    private NamespacedKey poopPileKey;
    private NamespacedKey poopProjectileKey;

    private String startMessage;
    private String cooldownMessage;
    private String noPermissionMessage;
    private String poopStartMessage;
    private String poopCooldownMessage;
    private String poopAlreadyMessage;
    private String poopFinishMessage;
    private String poopNotReadyMessage;

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

    private long poopCooldownMillis;

    @Override
    public void onEnable() {
        displayKey = new NamespacedKey(this, "display");
        excrementKey = new NamespacedKey(this, "excrement");
        poopPileKey = new NamespacedKey(this, "poop_pile");
        poopProjectileKey = new NamespacedKey(this, "poop_projectile");
        saveDefaultConfig();
        loadConfigValues();
        getServer().getPluginManager().registerEvents(this, this);
        scheduleLoadedChunkCleanup();
        getLogger().info("Piss plugin has been enabled.");
    }

    private void loadConfigValues() {
        startMessage = getConfig().getString("message.start", "§eYou started peeing...");
        cooldownMessage = getConfig().getString("message.cooldown", "§cYou're already peeing!");
        noPermissionMessage = getConfig().getString("message.no-permission", "§cYou do not have permission to use this command.");
        poopStartMessage = getConfig().getString("message.poop.start", "§6You started pooping...");
        poopCooldownMessage = getConfig().getString("message.poop.cooldown", "§cWait {seconds} seconds before pooping again.");
        poopAlreadyMessage = getConfig().getString("message.poop.already", "§cYou're already pooping!");
        poopFinishMessage = getConfig().getString("message.poop.finish", "§6You finished pooping.");
        poopNotReadyMessage = getConfig().getString("message.poop.not-ready", "§cFinish pooping first.");

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

        poopCooldownMillis = Math.max(0L, getConfig().getLong("poop.cooldown-seconds", 60L)) * 1000L;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String commandName = cmd.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("piss") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
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

        if (commandName.equals("poop")) {
            if (player.hasPermission("poop.use")) {
                return poopCmd(player);
            }
        } else if (player.hasPermission("piss.use")) {
            return pissCmd(player);
        }

        player.sendMessage(noPermissionMessage);
        return true;
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

    private boolean poopCmd(Player player) {
        UUID playerId = player.getUniqueId();
        if (poopingPlayers.contains(playerId)) {
            player.sendMessage(poopAlreadyMessage);
            return true;
        }

        long now = System.currentTimeMillis();
        long lastPoop = poopCooldowns.getOrDefault(playerId, 0L);
        long remaining = poopCooldownMillis - (now - lastPoop);
        if (!player.hasPermission("poop.nocooldown") && remaining > 0) {
            long seconds = (remaining + 999L) / 1000L;
            player.sendMessage(poopCooldownMessage.replace("{seconds}", String.valueOf(seconds)));
            return true;
        }

        poopingPlayers.add(playerId);
        if (!player.hasPermission("poop.nocooldown")) {
            poopCooldowns.put(playerId, now);
        }
        player.sendMessage(poopStartMessage);

        Vector behind = getFlatDirection(player).multiply(-1);
        Location pileCenter = getPoopPileCenter(player, behind);
        String pileId = UUID.randomUUID().toString();

        trackTask(player.getScheduler().runAtFixedRate(this, new Consumer<>() {
            private int ticks = 0;

            @Override
            public void accept(ScheduledTask task) {
                if (!player.isOnline() || !player.isValid()) {
                    finishPoop(player, task);
                    return;
                }

                if (ticks >= POOP_DURATION) {
                    markPoopPileReady(pileCenter, pileId);
                    finishPoop(player, task);
                    return;
                }

                spawnPoopDrop(player, pileCenter, pileId);
                ticks += POOP_INTERVAL;
            }
        }, () -> {
            poopingPlayers.remove(playerId);
            markPoopPileReady(pileCenter, pileId);
        }, 1L, POOP_INTERVAL));

        return true;
    }

    private void finishPoop(Player player, ScheduledTask task) {
        task.cancel();
        scheduledTasks.remove(task);
        poopingPlayers.remove(player.getUniqueId());
        player.sendMessage(poopFinishMessage);
    }

    private Vector getFlatDirection(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) {
            return new Vector(0, 0, 1);
        }
        return direction.normalize();
    }

    private Location getPoopPileCenter(Player player, Vector behind) {
        Location center = player.getLocation().clone().add(behind.clone().multiply(1.1));
        center.setY(player.getLocation().getY() + 0.08);
        return center;
    }

    private void spawnPoopDrop(Player player, Location pileCenter, String pileId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector behind = getFlatDirection(player).multiply(-1);
        Location spawnPos = player.getLocation().clone()
                .add(behind.clone().multiply(0.35))
                .add(0, player.getHeight() * 0.35, 0);
        Location target = pileCenter.clone().add(
                random.nextDouble(-POOP_PILE_RADIUS, POOP_PILE_RADIUS),
                random.nextDouble(0.0, 0.35),
                random.nextDouble(-POOP_PILE_RADIUS, POOP_PILE_RADIUS)
        );

        ItemDisplay poop = player.getWorld().spawn(spawnPos, ItemDisplay.class);
        markDisplay(poop);
        poop.setItemStack(new ItemStack(randomPoopBlock()));
        setScale(poop, POOP_DROP_SIZE + random.nextDouble(0.0, 0.08));

        Vector velocity = target.toVector().subtract(spawnPos.toVector());
        if (velocity.lengthSquared() < 0.0001) {
            velocity = behind;
        }
        velocity.normalize().multiply(POOP_DROP_SPEED);

        startPoopDropTask(poop, spawnPos.clone(), target, velocity, pileId);
    }

    private void startPoopDropTask(ItemDisplay poop, Location startLoc, Location target, Vector velocity, String pileId) {
        trackTask(poop.getScheduler().runAtFixedRate(this, new Consumer<>() {
            private Location loc = startLoc.clone();
            private int ticks = 0;

            @Override
            public void accept(ScheduledTask task) {
                if (!poop.isValid()) {
                    cancelTask(task);
                    return;
                }

                ticks++;
                loc.add(velocity);
                if (!Bukkit.isOwnedByCurrentRegion(loc)) {
                    poop.teleportAsync(loc);
                    return;
                }

                if (ticks > 50 || loc.distanceSquared(target) <= 0.08) {
                    spawnPoopPilePiece(target, pileId);
                    removeDisplay(poop);
                    cancelTask(task);
                    return;
                }

                poop.teleportAsync(loc);
            }
        }, () -> removeTrackedDisplay(poop), 1L, 1L));
    }

    private void spawnPoopPilePiece(Location loc, String pileId) {
        trackTask(Bukkit.getRegionScheduler().run(this, loc, task -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            ItemDisplay piece = loc.getWorld().spawn(loc, ItemDisplay.class);
            markDisplay(piece);
            piece.addScoreboardTag(POOP_PILE_TAG);
            piece.getPersistentDataContainer().set(poopPileKey, PersistentDataType.STRING, pileId);
            piece.setItemStack(new ItemStack(randomPoopBlock()));
            setScale(
                    piece,
                    random.nextDouble(0.16, 0.32),
                    random.nextDouble(0.12, 0.28),
                    random.nextDouble(0.16, 0.32)
            );
            scheduledTasks.remove(task);
        }));
    }

    private void markPoopPileReady(Location pileCenter, String pileId) {
        if (!readyPoopPiles.add(pileId)) {
            return;
        }

        trackTask(Bukkit.getRegionScheduler().runDelayed(this, pileCenter, task -> {
            if (readyPoopPiles.contains(pileId)) {
                removePoopPileAt(pileCenter, pileId);
            }
            scheduledTasks.remove(task);
        }, POOP_PILE_LIFETIME));
    }

    private Material randomPoopBlock() {
        int index = ThreadLocalRandom.current().nextInt(POOP_BLOCKS.size());
        int current = 0;
        for (Material material : POOP_BLOCKS) {
            if (current == index) {
                return material;
            }
            current++;
        }
        return Material.BROWN_CONCRETE;
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

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!poopingPlayers.contains(event.getPlayer().getUniqueId()) || !event.hasExplicitlyChangedPosition()) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Location locked = event.getFrom().clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    @EventHandler
    public void onPoopPileInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isPoopPileDisplay(event.getRightClicked())) {
            return;
        }

        event.setCancelled(true);
        ItemDisplay pilePiece = (ItemDisplay) event.getRightClicked();
        String pileId = pilePiece.getPersistentDataContainer().get(poopPileKey, PersistentDataType.STRING);
        if (pileId == null || !readyPoopPiles.contains(pileId)) {
            event.getPlayer().sendMessage(poopNotReadyMessage);
            return;
        }

        event.getPlayer().getInventory().addItem(createExcrementItem(1));
        removePoopPile(pilePiece);
    }

    @EventHandler
    public void onExcrementUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !isRightClick(event.getAction())) {
            return;
        }

        if (!isExcrementItem(event.getItem())) {
            if (tryCollectPoopPile(event.getPlayer())) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        removeOneExcrement(player);
        Snowball snowball = player.launchProjectile(
                Snowball.class,
                player.getLocation().getDirection().normalize().multiply(1.2)
        );
        snowball.setItem(createExcrementItem(1));
        snowball.getPersistentDataContainer().set(poopProjectileKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler
    public void onPoopProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)
                || !snowball.getPersistentDataContainer().has(poopProjectileKey, PersistentDataType.BYTE)) {
            return;
        }

        if (event.getHitEntity() instanceof Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, true, true, true));
        } else if (event.getHitBlock() != null) {
            spawnPoopStain(snowball.getLocation(), event.getHitBlockFace());
        }

        snowball.remove();
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private ItemStack createExcrementItem(int amount) {
        ItemStack item = new ItemStack(Material.BROWN_DYE, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Экскременты", NamedTextColor.GOLD));
            meta.getPersistentDataContainer().set(excrementKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isExcrementItem(ItemStack item) {
        if (item == null || item.getType() != Material.BROWN_DYE || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(excrementKey, PersistentDataType.BYTE);
    }

    private void removeOneExcrement(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }

        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item);
    }

    private boolean isPoopPileDisplay(Entity entity) {
        return entity instanceof ItemDisplay display
                && display.getScoreboardTags().contains(POOP_PILE_TAG)
                && display.getPersistentDataContainer().has(poopPileKey, PersistentDataType.STRING);
    }

    private void removePoopPile(ItemDisplay clickedPiece) {
        String pileId = clickedPiece.getPersistentDataContainer().get(poopPileKey, PersistentDataType.STRING);
        removePoopPileAt(clickedPiece.getLocation(), pileId);

        if (clickedPiece.isValid()) {
            removeDisplay(clickedPiece);
        }
    }

    private void removePoopPileAt(Location loc, String pileId) {
        if (pileId == null) {
            return;
        }

        for (var entity : loc.getWorld().getNearbyEntities(loc, POOP_COLLECT_RADIUS, POOP_COLLECT_RADIUS, POOP_COLLECT_RADIUS)) {
            if (!(entity instanceof ItemDisplay display) || !Bukkit.isOwnedByCurrentRegion(entity)) {
                continue;
            }
            String otherPileId = display.getPersistentDataContainer().get(poopPileKey, PersistentDataType.STRING);
            if (pileId.equals(otherPileId)) {
                removeDisplay(display);
            }
        }

        readyPoopPiles.remove(pileId);
    }

    private boolean tryCollectPoopPile(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && item.getType() != Material.AIR) {
            return false;
        }

        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        ItemDisplay bestPile = null;
        double bestDistance = Double.MAX_VALUE;

        for (var entity : player.getWorld().getNearbyEntities(player.getLocation(), 3.0, 2.0, 3.0)) {
            if (!(entity instanceof ItemDisplay display) || !isPoopPileDisplay(display)) {
                continue;
            }

            String pileId = display.getPersistentDataContainer().get(poopPileKey, PersistentDataType.STRING);
            if (pileId == null || !readyPoopPiles.contains(pileId)) {
                continue;
            }

            Vector toPile = display.getLocation().toVector().subtract(eye.toVector());
            double distance = toPile.length();
            if (distance > 3.0 || distance < 0.001 || look.dot(toPile.normalize()) < 0.55) {
                continue;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestPile = display;
            }
        }

        if (bestPile == null) {
            return false;
        }

        player.getInventory().addItem(createExcrementItem(1));
        removePoopPile(bestPile);
        return true;
    }

    private void spawnPoopStain(Location loc, BlockFace face) {
        BlockFace surface = face == null ? BlockFace.UP : face;
        Vector normal = surface.getDirection();
        Location stainLoc = loc.clone().add(normal.clone().multiply(0.03));

        trackTask(Bukkit.getRegionScheduler().run(this, stainLoc, task -> {
            ItemDisplay stain = stainLoc.getWorld().spawn(stainLoc, ItemDisplay.class);
            markDisplay(stain);
            stain.setItemStack(new ItemStack(Material.BROWN_CARPET));
            setScale(stain, 0.32, 0.32, 0.02);
            Transformation transformation = stain.getTransformation();
            transformation.getLeftRotation().set(surfaceRotation(surface));
            stain.setTransformation(transformation);
            trackTask(stain.getScheduler().runDelayed(this, delayedTask -> {
                removeDisplay(stain);
                scheduledTasks.remove(delayedTask);
            }, () -> removeTrackedDisplay(stain), POOP_STAIN_LIFETIME));
            scheduledTasks.remove(task);
        }));
    }

    private Quaternionf surfaceRotation(BlockFace face) {
        Vector normal = face.getDirection();
        return new Quaternionf().rotationTo(
                0f,
                1f,
                0f,
                (float) normal.getX(),
                (float) normal.getY(),
                (float) normal.getZ()
        );
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
        poopingPlayers.clear();
        readyPoopPiles.clear();
        getLogger().info("Piss plugin has been disabled.");
    }
}
