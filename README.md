# Piss Plugin

A fun Paper/Spigot plugin that allows players to **pee** in Minecraft using the `/piss` command.  
When activated, the player will emit a small yellow stained glass stream forward, which falls with gravity and creates puddles when hitting blocks.

---

## Features
- Command `/piss` to start peeing.  
- Configurable duration, drop speed, gravity, size, and interval.  
- Puddles are created on impact and disappear after a set time.  
- Permissions support.  
- `/piss reload` command for reloading configuration without restarting the server.  
- Works with scaled players (supports different player sizes).

---

## Commands
- `/piss` – Start peeing.  
- `/piss reload` – Reload plugin configuration (requires permission).  

---

## Permissions
- `piss.use` – Allows using the `/piss` command.  
- `piss.reload` – Allows reloading the plugin configuration.  

---

## Configuration
The `config.yml` file lets you adjust various options:

```yaml
message:
  start: "§eYou started peeing..."
  cooldown: "§cYou're already peeing!"
  no-permisson: "§cYou do not have permission to use this command."

piss:
  duration: 200         # How long peeing lasts (ticks)
  drop-interval: 2      # Interval between drops
  drop-speed: 0.3       # Initial drop speed
  drop-gravity: -0.02   # Gravity per tick
  drop-size: 0.1        # Drop scale size
  drop-hitbox: 0.2      # Collision hitbox

puddle:
  lifetime: 100         # Lifetime of puddles (ticks)
  size: 0.6             # Puddle size
  thickness: 0.01       # Puddle thickness (flatness)
  random-offset: 2.0    # Random offset for puddle spread
