# Persistencia

[← Índice Mod](INDICE.md)

**El inventario compartido se guarda en el mundo, no en los jugadores:** `world/data/soullink/shared_inventory.dat`. Vanilla sigue escribiendo el inventario en cada `playerdata/<uuid>.dat` (porque lee de la lista compartida), pero esos datos **se ignoran al cargar** (ver [carga de datos del jugador](carga-de-datos-del-jugador.md)).

## `SharedInventoryState`

Extiende `SavedData` y se obtiene con `server.getDataStorage().computeIfAbsent(TYPE)`. Es el almacenamiento global del servidor que existe desde 26.x, no el de un `ServerLevel`. Guarda los grupos vinculados (ver [grupos vinculados](grupos.md)):

| Clave | Qué es |
|---|---|
| Raíz (`items`, `equipment`, `food`, `health`, `experience`, `experienceScore`) | **El grupo general**, con `LinkGroup.MAP_CODEC` mezclado en la raíz. Son los mismos campos que hasta la 2.1.0, cuando había un solo grupo: un mundo viejo se lee sin migrar y una versión anterior puede leer uno nuevo (sólo ve el general) |
| `groups` | Los demás grupos, por id, con `LinkGroup.CODEC` |
| `members` | UUID → grupo y último nombre. Sólo los que tienen grupo asignado: el resto está en el general |
| `initialized` | Del mundo, no de cada grupo |
| `trinketsInitialized` | Como `initialized`, para los slots de Trinkets (ver [trinkets](trinkets.md)) |

`OldSaveGameTest` decodifica un archivo con el formato de la 2.1.0 y verifica que todo queda en el general.

Cada `LinkGroup` tiene estos campos:

| Campo | Codec | Notas |
|---|---|---|
| `items` | `ItemStackWithSlot.CODEC.listOf()` | Sólo los slots no vacíos, con su índice. Al cargar se valida con `isValidInContainer`. |
| `equipment` | `EntityEquipment.CODEC` | Un `EntityEquipment` real; el mapa compartido sale de su campo `items` por `EntityEquipmentAccessor`. El codec de vanilla ya descarta los stacks vacíos al codificar. |
| `food` | `FOOD_CODEC` propio (`level`, `saturation`, `exhaustion`) | `FoodData` no tiene codec en vanilla. `exhaustionLevel` es privado: se lee y escribe con `FoodDataAccessor`. El timer de regeneración no se guarda. |
| `health` | `Codec.FLOAT` | `-1` (`UNKNOWN_HEALTH`) = nadie la fijó o murió el grupo. Ver [vida y hambre](vida-y-hambre.md). |
| `experience` | `Codec.DOUBLE` | En **puntos**, no niveles. `-1` (`UNKNOWN_EXPERIENCE`) = mod recién instalado; el primero que entra aporta la suya. Ver [experiencia](experiencia.md). |
| `experienceScore` | `Codec.INT` | El `totalExperience` compartido (puntaje de la pantalla de muerte). |
| `trinkets` | `Codec.unboundedMap(STRING, TrinketSlotLists.CODEC)` | Slots de Trinkets por id (`"chest/back"`): tamaño, ítems y cosméticos. Vacío sin Trinkets. Ver [trinkets](trinkets.md). |

Las estadísticas van en otro archivo, `data/soullink/stats.dat`, con su propio `SavedData` (`GroupStats`): ver [estadísticas](estadisticas.md#persistencia).

## Gotchas

**`isDirty()` devuelve siempre `true`.** Los `ItemStack` se mutan directamente (`shrink`, daño de herramientas, componentes) sin pasar por ningún método que pueda marcar el estado como sucio. El archivo es chico y sólo se escribe cuando el mundo se guarda, así que guardarlo siempre cuesta nada y evita perder cambios.

**`DataFixTypes` va en `null` y eso sólo funciona con Fabric API.** `SavedDataStorage.readTagFromDisk` de vanilla llama a `dataFixType.update(...)` sin chequear null. `fabric-object-builder-api-v1` tiene un `SavedDataStorageMixin` (`handleNullDataFixType`) que lo evita. **Si algún día se saca la dependencia de Fabric API, el mundo crashea al cargar.**

**No uses objetos mutables como default en un codec.** La primera versión usaba `optionalFieldOf("equipment", new EntityEquipment())`. Ese default se crea **una sola vez** al armar el codec, así que dos mundos sin el campo (en singleplayer, en la misma sesión) habrían compartido el mismo `EntityEquipment`. Hoy es `optionalFieldOf("equipment")` + `orElseGet(EntityEquipment::new)`, y lo mismo para `food`.

**`syncedHealth` y `syncedExperience` no se guardan.** Son `IdentityHashMap` por `ServerPlayer`, uno por grupo, con lo que le quedó a cada jugador en el último reparto. Al reiniciar el servidor arrancan vacíos y los que entran adoptan `health` y `experience`. Tampoco se guardan `experienceContributions` ni quién tiró la experiencia en la última muerte: duran un login o un tick.

## Estado activo estático

`SharedInventoryState.active` guarda el estado del servidor que está corriendo, porque `EntityEquipmentMixin.setAll` no tiene forma de llegar al servidor. **Se limpia en `ServerLifecycleEvents.SERVER_STOPPED`**: en singleplayer se puede salir de un mundo y entrar a otro sin cerrar el juego, y sin limpiarlo el chequeo de bloqueo compararía contra la lista del mundo anterior.

`active` se asigna en cada `get(server)`, y `get` se llama en el constructor de cada `Inventory` de servidor, así que siempre está puesto antes de cualquier `load()`.

## Ver también

- [Carga de datos del jugador](carga-de-datos-del-jugador.md)
- [Estadísticas](estadisticas.md)
- [Inventario compartido](inventario-compartido.md)
- [Experiencia](experiencia.md)
- [Minecraft 26.x sin ofuscar](../general/minecraft-26-sin-ofuscar.md): cómo se verificó lo de `readTagFromDisk` con `javap`.
- [Grupos vinculados](grupos.md): lo compartido es de cada grupo, no del servidor.
