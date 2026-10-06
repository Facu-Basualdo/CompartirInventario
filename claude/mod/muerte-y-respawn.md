# Muerte y respawn

[← Índice Mod](INDICE.md)

**Si muere un jugador, los dos pierden todo**, porque es un único inventario. No es un bug: es la semántica de "compartir". Con `/gamerule keepInventory true` no se pierde nada.

## Muerte

`Player.dropEquipment` → (si no hay `keepInventory`) `Inventory.dropAll()`. Ese método recorre la lista compartida, tira cada stack al mundo, pone `ItemStack.EMPTY` en cada slot y termina con `equipment.dropAll(player)` para la armadura y la mano secundaria.

Como la lista queda vacía en el mismo paso en que se tiran los ítems, **cada ítem cae una sola vez**: no hay duplicación. El otro jugador queda con el inventario vacío en el siguiente tick.

## Respawn

Reaparecer (morir, o salir del End por el portal) **crea un `ServerPlayer` nuevo** con `PlayerList.respawn`. Ese jugador construye un `Inventory` nuevo, y `InventoryMixin` lo vuelve a conectar a las listas compartidas en el constructor. No hace falta ningún hook específico de respawn.

Después vanilla llama a `ServerPlayer.restoreFrom(viejo, keepEverything)`:

| Caso | Qué hace con el inventario | Efecto con el mod |
|---|---|---|
| `keepEverything = true` (salir del End) o `keepInventory` activo | `transferInventoryXpAndScore` → `inventory.replaceWith(viejo.inventory)` | No-op: `setItem(i, getItem(i))` sobre la misma lista |
| Muerte normal | No toca el inventario | La lista ya quedó vacía por `dropAll()` |

**No "arregles" `replaceWith`.** Parece que copia, pero sobre la misma lista no hace nada. Y `EntityEquipment.setAll`, que sí rompería (hace `clear()` antes de `putAll()` del mismo mapa), no se llama en `restoreFrom`: sólo en la carga desde disco, y ahí está bloqueado (ver [carga de datos del jugador](carga-de-datos-del-jugador.md)).

## Ver también

- [Inventario compartido](inventario-compartido.md): por qué el constructor alcanza.
- [Tests: jugadores de prueba](../tests/jugadores-de-prueba.md): por qué el test usa `die()` y no `kill()`.
- [Tests: inventario compartido](../tests/inventario-compartido.md)
