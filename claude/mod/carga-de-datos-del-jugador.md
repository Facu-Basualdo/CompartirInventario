# Carga de datos del jugador

[← Índice Mod](INDICE.md)

Síntoma que esto evita: **entra el jugador B y al jugador A se le vacía el inventario**, o se le reemplaza por lo que B tenía la última vez que se desconectó.

## Por qué pasa

Cuando un jugador entra, vanilla lee su archivo `playerdata/<uuid>.dat` y llama a:

| Método | Llamado desde | Qué hace |
|---|---|---|
| `Inventory.load(TypedInputList<ItemStackWithSlot>)` | `Player.readAdditionalSaveData` | `items.clear()` y después `setItem` por cada ítem guardado |
| `EntityEquipment.setAll(EntityEquipment)` | `LivingEntity.readAdditionalSaveData` | `items.clear()` + `items.putAll(otro.items)` |
| `FoodData.readAdditionalSaveData(ValueInput)` | `Player.readAdditionalSaveData` | Pisa comida, saturación y agotamiento con los guardados |

Como la lista, el mapa y el `FoodData` son los compartidos (ver [inventario compartido](inventario-compartido.md) y [vida y hambre](vida-y-hambre.md)), cada carga pisa lo que tiene el otro jugador conectado. Y lo que se carga son los datos personales de B, que pueden ser viejos.

## La regla

**`InventoryMixin` cancela `load()`, `EntityEquipmentMixin` cancela `setAll()` y `FoodDataMixin` cancela `readAdditionalSaveData()` cuando apuntan al objeto compartido y el estado ya está `initialized`.** Los tres usan `SharedInventoryState.shouldBlockLoadInto(storage)`, que compara por identidad (`==`) contra el estado activo.

La vida guardada del jugador **sí** se carga (`setHealth` en `LivingEntity.readAdditionalSaveData`), pero da igual: el jugador todavía no está en la lista de jugadores, y en el primer tick adopta la vida compartida (ver [vida y hambre](vida-y-hambre.md)).

Que el chequeo sea por identidad y no por "es un jugador" es lo que hace seguro bloquear `setAll`: cualquier otra entidad (un zombie, un armor stand) tiene su propio `EnumMap` y carga normal.

## Migración: el flag initialized

Sin el flag, instalar el mod en un mundo existente arrancaría con el inventario compartido vacío y **todos perderían sus ítems**.

1. Mundo con el mod recién instalado: `initialized = false`.
2. Entra el primer jugador. Las tres cargas **no** se bloquean: sus ítems y su hambre se cargan en lo compartido.
3. Se dispara `ServerPlayConnectionEvents.JOIN` → `markInitialized()`. El flag se guarda con el estado (ver [persistencia](persistencia.md)).
4. Desde ahí, todas las cargas se bloquean.

Los ítems personales de los demás jugadores no entran a lo compartido, pero **tampoco se pierden**: van a un cofre (ver la sección siguiente). Su hambre sí se descarta, y su vida la reemplaza la compartida.

**`initialized` se marca en JOIN y no al terminar `load()`** porque un jugador nuevo (sin archivo) nunca pasa por `load()`. Si el flag dependiera de eso, en un mundo nuevo el primer reingreso de A volvería a cargar sus datos personales, posiblemente viejos, y pisaría lo que B hizo mientras A no estaba.

## Cofre con los ítems de antes del mod

Síntoma que esto evita: instalás el mod en una partida en curso, entra el segundo jugador y **su inventario desaparece para siempre**.

**El archivo de cada jugador siempre trae un inventario; la marca `InventarioCompartido` dice si es suyo o una copia del compartido.** `PlayerMixin.addAdditionalSaveData` escribe `InventarioCompartido: true` en cada guardado hecho con el mod. Sin esa marca no hay forma de distinguir: después de instalar el mod, vanilla sigue guardando en `playerdata/<uuid>.dat` el inventario que el jugador ve (o sea, el compartido). Si se rescataran ítems en cada login, se **duplicaría** el inventario compartido en un cofre cada vez que alguien entra.

Flujo:

1. `PlayerMixin.readAdditionalSaveData` (HEAD, antes de que vanilla y los bloqueos corran) ve un archivo **sin la marca** con `initialized = true`. Lee él mismo `"Inventory"` (`ItemStackWithSlot`) y `"equipment"` (`EntityEquipment.CODEC`) del `ValueInput` y los guarda en `SharedInventoryState.addRescuedItems(uuidDelPerfil, ítems)`.
2. Vanilla sigue y los bloqueos de arriba descartan esa carga como siempre.
3. En `ServerPlayConnectionEvents.JOIN`, `PersonalItemsChest.placeRescuedItems` saca los ítems pendientes y los pone en cofres de 27 slots. El máximo es 41 ítems (36 + 4 de armadura + mano secundaria), o sea hasta 2 cofres. Le avisa al jugador por chat con las coordenadas.
4. El próximo guardado ya lleva la marca, así que el siguiente login no rescata nada.

**Usá el UUID del perfil (`getGameProfile().id()`), no `getUUID()`.** En el HEAD de `readAdditionalSaveData` el UUID de la entidad puede venir del archivo y todavía no es el del perfil, que `Player` recién asigna en esa misma carga.

**Dónde va el cofre:** `findFreeSpot` busca de adentro hacia afuera (radio 1 a 3), primero a la altura de los pies y después una más arriba. Nunca usa la columna del jugador, porque lo encerraría, y solo reemplaza bloques que `canBeReplaced()` (aire, pasto, agua). **Si no hay lugar, los ítems quedan tirados a sus pies** con `Containers.dropItemStack`: nunca se pierden. Si los agarra, entran al inventario compartido.

Dos cofres simples puestos uno al lado del otro no se unen en uno doble: `ChestBlock` solo une al colocarlos con `getStateForPlacement`, y `setBlock` con `defaultBlockState()` no pasa por ahí.

**Los ítems rescatados viven en memoria (`rescuedItems`) entre la carga y el JOIN, que son parte del mismo login.** Si el servidor se cae en ese intervalo no se pierde nada: el archivo del jugador todavía no se volvió a guardar (sigue sin la marca y con sus ítems), y el próximo login los rescata de nuevo. En el autosave, el archivo del jugador y el chunk del cofre se guardan juntos.

**Hueco conocido:** si el jugador se desconecta (su archivo se guarda con la marca) y el servidor se cae antes del siguiente guardado del mundo, el chunk vuelve a la versión sin cofre y esos ítems se pierden. Para cerrarlo habría que guardar el chunk o el mundo al poner el cofre.

**Limitación:** un mundo que ya usaba el mod antes de que existiera la marca (la versión del primer commit) tiene archivos sin marca con copias del compartido. En ese mundo, el primer login de cada jugador después de actualizar le daría un cofre con una copia, o sea ítems duplicados. Esa versión nunca se publicó.

## `replaceWith()` no se bloquea

`ServerPlayer.restoreFrom` → `transferInventoryXpAndScore` → `Inventory.replaceWith(viejo)` hace `setItem(i, viejo.getItem(i))` para cada slot. Los dos inventarios apuntan a la misma lista, así que es un no-op. Ver [muerte y respawn](muerte-y-respawn.md).

## Ver también

- [Inventario compartido](inventario-compartido.md)
- [Persistencia](persistencia.md): dónde se guarda `initialized`.
- [Tests: inventario compartido](../tests/inventario-compartido.md): el test guarda a A, cambia el inventario y carga el guardado viejo en B, y hace entrar a un jugador con un archivo sin marca.
- [Tests: jugadores de prueba](../tests/jugadores-de-prueba.md): cómo hacer entrar a un jugador con un archivo guardado.
