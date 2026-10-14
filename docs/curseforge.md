# Soul Link

Linked players share, in real time:

- **one inventory**: inventory, hotbar, armor and offhand;
- **one hunger bar**;
- **one health bar**;
- **one experience bar**.

What one player picks up shows up for the others. If one gets hit, everyone loses health. If one eats, everyone fills up. If one enchants, everyone pays the levels. **If one dies, everyone dies.**

By default everyone on the server is linked. With **`/soullink group`** you can split players into separate groups: 2 and 2, 3 and 1, or however you like.

## What is shared

| | Shared | Per player |
|---|---|---|
| Inventory and hotbar | ✅ | Selected hotbar slot |
| Armor and offhand | ✅ | |
| Hunger and saturation | ✅ | |
| Health | ✅ | Absorption (yellow) hearts |
| Experience (levels and points) | ✅ (can be turned off) | |
| | | Item on the cursor, 2×2 crafting grid, ender chest |

- **Creative and spectator** players share the inventory, but not health, hunger or experience.
- **Experience** adds up in points, not levels, so collecting orbs together is as good as one player collecting them all. Enchanting, anvils and `/xp` affect everyone.
- **Death:** when one dies, everyone dies at the same moment. Items and orbs drop only once, next to whoever took the final hit. With `keepInventory` nothing is lost, but everyone still dies.
- **Everything is saved in the world**, not per player: if someone logs off with low health, whoever joins next starts with that health.
- **Adding it to an existing world:** the first player to join brings their items and hunger. Nobody else loses anything: the first time they join, their items appear in a chest next to them, and their experience is added to the shared bar.

## Groups

Everyone is linked by default. To split players up (operator commands):

```
/soullink group red Ana Beto      Ana and Beto are linked to each other
/soullink group blue Caro Dani    Caro and Dani, to each other
/soullink group remove Beto       Beto goes back to the players without a group
/soullink groups                  shows who is in each group (anyone can use it)
```

- **Each group is independent:** its own inventory, hunger, health, experience and attempts. If one group dies, the others keep playing.
- **Players without a group** are linked to each other, as if there were no groups. **A group of one** plays solo.
- **Switching groups doesn't bring anything along:** you get the new group's things, and yours stay with your old group.
- You can name up to 8 players at once, or use a selector like `@a[team=red]`.

## Swap places

**`/swap [player]`** (or `/intercambiar`) swaps your position with your partner's, even across dimensions. No operator needed.

## Stats

Every time the group dies, an **attempt** ends. The mod keeps count of the attempts and of what each player did:

| Stat | What it counts |
|---|---|
| Deaths caused | Group deaths where that player took the final hit |
| Hearts lost | Health the group lost from damage that player took |
| Close calls | Hits that left the group at 2 hearts or less without killing it |
| Experience gathered | Experience points that player brought in |
| Mobs killed | Hostile mobs that player killed |

- **`/soullink`** shows your group's current attempt, how long it has lasted, the record, one line per player and the latest deaths with their cause. **`/soullink stats <group>`** shows another group's.
- **When a group dies**, the chat says which group, which attempt ended, how long it lasted, whose fault it was and the record.
- **Sidebar:** off by default. Set `barraLateral` in the config (or in Mod Menu) to show deaths caused, or any other stat, on the right side of the screen. It uses regular scoreboard objectives, so it works without the mod on the client.
- `/soullink stats reset` (operators) sets everything back to zero.

## Configuration

`config/soullink.json` is created the first time the server starts:

| Option | What it does |
|---|---|
| `compartirExperiencia` | Off: everyone keeps their own experience |
| `anunciarMuertes` | The chat message when the group dies |
| `barraLateral` | Which stat to show on the sidebar: `culpas`, `corazones`, `sustos`, `experiencia`, `mobs` or `ninguna` (default) |
| `corazonesDeSusto` | From how many hearts a hit counts as a close call (0 to 10; 0 doesn't count them) |
| `idioma` | `en_us` or `es_ar`: for the console, the sidebar titles and players whose language the mod doesn't have |

**`/soullink reload`** (operators) applies it without restarting. With **[Mod Menu](https://www.curseforge.com/minecraft/mc-mods/modmenu)** you can also edit it in-game. Inventory, hunger and health can't be turned off: they are shared from the moment a player joins, and changing that with people online would duplicate or delete items.

## Languages

Each player sees the messages in their game language: **English or Spanish**. Commands exist in both (`/swap` and `/intercambiar`, `/soullink group` and `/soullink grupo`…).

## Installation

All the logic runs on the server: **players don't need to install anything**. On the client the mod is optional and only adds the Mod Menu settings. For singleplayer or LAN, install it on the client that opens the world.

Requires **Minecraft 26.3**, **Fabric Loader 0.19.5+**, **[Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)** and **Java 25**.

## Works well with

- **Hero's Forge** (0.14.0 or newer): stat points belong to each linked group. With an older Hero's Forge, Soul Link 3 won't start: update both.
- **Bloodmoon & Bosses** and **Mythic Loot**: no conflicts. Mythic Loot's healing affixes heal the whole group.

License: MIT. Feel free to use it in modpacks.
