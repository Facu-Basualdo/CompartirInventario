# Muerte y respawn

[← Índice Mod](INDICE.md)

**Si muere un jugador, mueren todos y se pierde todo**, porque es una sola vida y un solo inventario. No es un bug: es la semántica de "compartir". Con `/gamerule keepInventory true` no se pierden los ítems, pero igual mueren todos.

## Muerte

El que recibe el golpe final muere en el momento, por el camino normal de vanilla. Los demás mueren **al final de ese tick**, cuando `SharedVitals` ve que la vida compartida llegó a 0 y les hace `setHealth(0)` + `die()` (detalle en [vida y hambre](vida-y-hambre.md#muerte-del-grupo)). Cada uno ve su pantalla de muerte y reaparece por su cuenta.

Ítems: `Player.dropEquipment` → (si no hay `keepInventory`) `Inventory.dropAll()`. Ese método recorre la lista compartida, tira cada stack al mundo, pone `ItemStack.EMPTY` en cada slot y termina con `equipment.dropAll(player)` para la armadura y la mano secundaria.

Como la lista queda vacía en el mismo paso en que se tiran los ítems, **cada ítem cae una sola vez**, al lado del primero que murió. Cuando los demás mueren, el inventario ya está vacío y no tiran nada. Sí tiran su propia experiencia, que no se comparte.

Hambre: se reinicia a 20 de comida y 5 de saturación cuando muere el grupo, porque el `FoodData` compartido no se recrea con el jugador como en vanilla.

## Respawn

Reaparecer (morir, o salir del End por el portal) **crea un `ServerPlayer` nuevo** con `PlayerList.respawn`. Ese jugador construye un `Inventory` nuevo, y `InventoryMixin` lo vuelve a conectar a las listas compartidas en el constructor. `PlayerMixin` hace lo mismo con el `FoodData`. No hace falta ningún hook específico de respawn.

Vida: el jugador nuevo no está en `syncedHealth`, así que en el siguiente tick **adopta** la vida compartida. El primero en reaparecer después de una muerte grupal aporta la suya (20), porque la vida compartida quedó en `UNKNOWN_HEALTH`. Los que reaparecen después adoptan lo que haya, aunque el primero ya haya recibido daño.

Después vanilla llama a `ServerPlayer.restoreFrom(viejo, keepEverything)`:

| Caso | Qué hace con el inventario | Efecto con el mod |
|---|---|---|
| `keepEverything = true` (salir del End) o `keepInventory` activo | `transferInventoryXpAndScore` → `inventory.replaceWith(viejo.inventory)` | No-op: `setItem(i, getItem(i))` sobre la misma lista |
| Muerte normal | No toca el inventario | La lista ya quedó vacía por `dropAll()` |

**No "arregles" `replaceWith`.** Parece que copia, pero sobre la misma lista no hace nada. Y `EntityEquipment.setAll`, que sí rompería (hace `clear()` antes de `putAll()` del mismo mapa), no se llama en `restoreFrom`: sólo en la carga desde disco, y ahí está bloqueado (ver [carga de datos del jugador](carga-de-datos-del-jugador.md)).

## Ver también

- [Inventario compartido](inventario-compartido.md): por qué el constructor alcanza.
- [Vida y hambre](vida-y-hambre.md): cómo se detecta la muerte del grupo.
- [Tests: jugadores de prueba](../tests/jugadores-de-prueba.md): por qué el test usa `setHealth(0)` + `die()` y no `kill()`.
- [Tests: inventario compartido](../tests/inventario-compartido.md)
