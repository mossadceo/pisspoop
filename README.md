# pisspoop

A Paper/Folia 1.21 plugin with `/piss` and `/poop` commands.
Players can create configurable pee streams, poop piles, collect excrement, and throw it at blocks or players.

---

## Features
- Command `/piss` to start peeing.  
- Configurable duration, drop speed, gravity, size, and interval.  
- Puddles are created on impact and disappear after a set time.  
- Permissions support.  
- `/piss reload` command for reloading configuration without restarting the server.  
- Works with scaled players (supports different player sizes).
- Supports Paper and Folia scheduling APIs.
- Marks and cleans plugin-created display entities to avoid stuck glass after restarts.
- Command `/poop` with configurable cooldown.
- Poop piles form behind the player while movement is locked.
- Poop piles disappear after 10 seconds if they are not collected.
- Poop piles can be collected into throwable excrement items.
- Thrown excrement leaves small brown carpet stains on hit surfaces.
- Hitting a player with excrement gives short nausea.

---

## Build
```bash
./gradlew build
```

The compiled plugin jar is created in `build/libs/`.

---

## Commands
- `/piss` – Start peeing.  
- `/piss reload` – Reload plugin configuration (requires permission).  
- `/poop` – Start pooping.  

---

## Permissions
- `piss.use` – Allows using the `/piss` command.  
- `piss.reload` – Allows reloading the plugin configuration.  
- `poop.use` – Allows using the `/poop` command.  
- `poop.nocooldown` – Allows using `/poop` without cooldown.  

---

## Configuration
The `config.yml` file lets you adjust various options:

```yaml
message:
  start: "§eYou started peeing..."
  cooldown: "§cYou're already peeing!"
  no-permission: "§cYou do not have permission to use this command."
  poop:
    start: "§6You started pooping..."
    cooldown: "§cWait {seconds} seconds before pooping again."
    already: "§cYou're already pooping!"
    finish: "§6You finished pooping."
    not-ready: "§cFinish pooping first."

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

poop:
  cooldown-seconds: 60  # Cooldown for /poop
```

---

## Credits and License

This project is based on the original MIT-licensed Piss plugin. Original copyright attribution is preserved in `LICENSE`.

Folia/Paper compatibility updates, Gradle migration, and pisspoop updates by **mossadceo**.

The project is distributed under the MIT License. The original copyright notice is preserved in `LICENSE`.
