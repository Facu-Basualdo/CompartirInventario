# Carga de datos del jugador

[← Índice Mod](INDICE.md)

Síntoma que esto evita: **entra el jugador B y al jugador A se le vacía el inventario**, o se le reemplaza por lo que B tenía la última vez que se desconectó.

## Por qué pasa

Cuando un jugador entra, vanilla lee su archivo `playerdata/<uuid>.dat` y llama a:

| Método | Llamado desde | Qué hace |
|---|---|---|
| `Inventory.load(TypedInputList<ItemStackWithSlot>)` | `Player.readAdditionalSaveData` | `items.clear()` y después `setItem` por cada ítem guardado |
| `EntityEquipment.setAll(EntityEquipment)` | `LivingEntity.readAdditionalSaveData` | `items.clear()` + `items.putAll(otro.items)` |

Como la lista y el mapa son los compartidos (ver [inventario compartido](inventario-compartido.md)), ese `clear()` borra lo que tiene el otro jugador conectado. Y lo que se carga son los datos personales de B, que pueden ser viejos.

## La regla

**`InventoryMixin` cancela `load()` y `EntityEquipmentMixin` cancela `setAll()` cuando apuntan a la lista o al mapa compartidos y el estado ya está `initialized`.** Las dos usan `SharedInventoryState.shouldBlockLoadInto(storage)`, que compara por identidad (`==`) contra el estado activo.

Que el chequeo sea por identidad y no por "es un jugador" es lo que hace seguro bloquear `setAll`: cualquier otra entidad (un zombie, un armor stand) tiene su propio `EnumMap` y carga normal.

## Migración: el flag initialized

Sin el flag, instalar el mod en un mundo existente arrancaría con el inventario compartido vacío y **todos perderían sus ítems**.

1. Mundo con el mod recién instalado: `initialized = false`.
2. Entra el primer jugador. `load()` y `setAll()` **no** se bloquean: sus ítems personales se cargan en la lista compartida.
3. Se dispara `ServerPlayConnectionEvents.JOIN` → `markInitialized()`. El flag se guarda con el estado (ver [persistencia](persistencia.md)).
4. Desde ahí, todas las cargas se bloquean.

**Los ítems personales del segundo jugador se descartan** en la primera conexión después de instalar el mod. Es una limitación conocida, no un bug. Si alguna vez se quiere fusionar, el lugar es el `load()` del mixin: con `initialized = true` y la lista de entrada sin aplicar.

**`initialized` se marca en JOIN y no al terminar `load()`** porque un jugador nuevo (sin archivo) nunca pasa por `load()`. Si el flag dependiera de eso, en un mundo nuevo el primer reingreso de A volvería a cargar sus datos personales, posiblemente viejos, y pisaría lo que B hizo mientras A no estaba.

## `replaceWith()` no se bloquea

`ServerPlayer.restoreFrom` → `transferInventoryXpAndScore` → `Inventory.replaceWith(viejo)` hace `setItem(i, viejo.getItem(i))` para cada slot. Los dos inventarios apuntan a la misma lista, así que es un no-op. Ver [muerte y respawn](muerte-y-respawn.md).

## Ver también

- [Inventario compartido](inventario-compartido.md)
- [Persistencia](persistencia.md): dónde se guarda `initialized`.
- [Tests: inventario compartido](../tests/inventario-compartido.md): el test guarda a A, cambia el inventario y carga el guardado viejo en B.
