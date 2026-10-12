# Muerte y respawn

[← Índice Mod](INDICE.md)

**Si muere un jugador, mueren todos y se pierde todo**, porque es una sola vida y un solo inventario. No es un bug: es la semántica de "compartir". Con `/gamerule keepInventory true` no se pierden los ítems, pero igual mueren todos.

## Muerte

**Mueren los del grupo vinculado de la víctima, no todo el servidor** (ver [grupos vinculados](grupos.md)). El que recibe el golpe final muere en el momento, por el camino normal de vanilla. Los demás de su grupo mueren **al final de ese tick**, cuando `SharedVitals` ve que la vida compartida llegó a 0 y les hace `setHealth(0)` + `die()` (detalle en [vida y hambre](vida-y-hambre.md#muerte-del-grupo)). Cada uno ve su pantalla de muerte y reaparece por su cuenta.

## Dónde caen los ítems y la experiencia

**Todo cae una sola vez, al lado del jugador que recibió el golpe.** Que "el que recibió el golpe" sea el primero en morir es lo que lo garantiza.

Ítems: `Player.dropEquipment` → (si no hay `keepInventory`) `Inventory.dropAll()`. Ese método recorre la lista compartida, tira cada stack al mundo, pone `ItemStack.EMPTY` en cada slot y termina con `equipment.dropAll(player)` para la armadura y la mano secundaria. Como la lista queda vacía en el mismo paso, cuando los demás mueren el inventario ya está vacío y no tiran nada.

Experiencia: también es compartida, pero vanilla la tira por jugador según su nivel. `PlayerMixin` hace que sólo la tire el primero que muere en el tick (ver [experiencia](experiencia.md#al-morir-se-tira-una-sola-vez)).

| Cómo muere el grupo | Quién muere primero |
|---|---|
| Un golpe deja a un jugador en 0 | Ese jugador, en el momento, por vanilla. El resto al final del tick |
| Dos jugadores reciben daño en el mismo tick, ninguno llega a 0 solo pero la suma sí | Nadie muere por vanilla. `SharedVitals.killGroup` mata primero al que **más vida perdió** en el tick, y después al resto |

**`killGroup` no puede recorrer los jugadores en el orden de `PlayerList`.** Con dos golpes simultáneos, los ítems caerían al lado del que esté primero en la lista, que puede ser el que estaba a salvo en la base. La víctima la elige `syncHealth` (un jugador ya muerto gana sobre uno vivo; entre iguales, el delta más negativo), y su `getLastDamageSource()` es el mensaje de muerte de todos.

## Lo que se reinicia

Hambre: se reinicia a 20 de comida y 5 de saturación cuando muere el grupo, porque el `FoodData` compartido no se recrea con el jugador como en vanilla.

Experiencia: vuelve a 0 salvo con `keepInventory` (lo decide `SharedExperience.onGroupDeath` con la regla del mundo de la víctima).

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
- [Experiencia](experiencia.md): por qué el jugador que reaparece adopta la experiencia compartida.
- [Tests: jugadores de prueba](../tests/jugadores-de-prueba.md): por qué el test usa `setHealth(0)` + `die()` y no `kill()`.
- [Tests: inventario compartido](../tests/inventario-compartido.md)
- [Estadísticas](estadisticas.md): cada muerte del grupo termina un intento y se anota con su causa.
- [Grupos vinculados](grupos.md): lo compartido es de cada grupo, no del servidor.
