# Persistencia

[← Índice Mod](INDICE.md)

**El inventario compartido se guarda en el mundo, no en los jugadores:** `world/data/inventariocompartido/shared_inventory.dat`. Vanilla sigue escribiendo el inventario en cada `playerdata/<uuid>.dat` (porque lee de la lista compartida), pero esos datos **se ignoran al cargar** (ver [carga de datos del jugador](carga-de-datos-del-jugador.md)).

## `SharedInventoryState`

Extiende `SavedData` y se obtiene con `server.getDataStorage().computeIfAbsent(TYPE)`. Es el almacenamiento global del servidor que existe desde 26.x, no el de un `ServerLevel`.

| Campo | Codec | Notas |
|---|---|---|
| `items` | `ItemStackWithSlot.CODEC.listOf()` | Sólo los slots no vacíos, con su índice. Al cargar se valida con `isValidInContainer`. |
| `equipment` | `EntityEquipment.CODEC` | Un `EntityEquipment` real; el mapa compartido sale de su campo `items` por `EntityEquipmentAccessor`. El codec de vanilla ya descarta los stacks vacíos al codificar. |
| `initialized` | `Codec.BOOL` | Ver la migración en [carga de datos del jugador](carga-de-datos-del-jugador.md#migración-el-flag-initialized). |

## Gotchas

**`isDirty()` devuelve siempre `true`.** Los `ItemStack` se mutan directamente (`shrink`, daño de herramientas, componentes) sin pasar por ningún método que pueda marcar el estado como sucio. El archivo es chico y sólo se escribe cuando el mundo se guarda, así que guardarlo siempre cuesta nada y evita perder cambios.

**`DataFixTypes` va en `null` y eso sólo funciona con Fabric API.** `SavedDataStorage.readTagFromDisk` de vanilla llama a `dataFixType.update(...)` sin chequear null. `fabric-object-builder-api-v1` tiene un `SavedDataStorageMixin` (`handleNullDataFixType`) que lo evita. **Si algún día se saca la dependencia de Fabric API, el mundo crashea al cargar.**

**No uses objetos mutables como default en un codec.** La primera versión usaba `optionalFieldOf("equipment", new EntityEquipment())`. Ese default se crea **una sola vez** al armar el codec, así que dos mundos sin el campo (en singleplayer, en la misma sesión) habrían compartido el mismo `EntityEquipment`. Hoy es `optionalFieldOf("equipment")` + `orElseGet(EntityEquipment::new)`.

## Estado activo estático

`SharedInventoryState.active` guarda el estado del servidor que está corriendo, porque `EntityEquipmentMixin.setAll` no tiene forma de llegar al servidor. **Se limpia en `ServerLifecycleEvents.SERVER_STOPPED`**: en singleplayer se puede salir de un mundo y entrar a otro sin cerrar el juego, y sin limpiarlo el chequeo de bloqueo compararía contra la lista del mundo anterior.

`active` se asigna en cada `get(server)`, y `get` se llama en el constructor de cada `Inventory` de servidor, así que siempre está puesto antes de cualquier `load()`.

## Ver también

- [Carga de datos del jugador](carga-de-datos-del-jugador.md)
- [Inventario compartido](inventario-compartido.md)
- [Minecraft 26.x sin ofuscar](../general/minecraft-26-sin-ofuscar.md): cómo se verificó lo de `readTagFromDisk` con `javap`.
