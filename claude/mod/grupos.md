# Grupos vinculados

[← Índice Mod](INDICE.md)

**Lo compartido no es del servidor: es de un grupo.** Si un jugador ve el inventario de otro con el que no debería estar vinculado, o uno que cambió de grupo aparece con los ítems del grupo viejo, mirá acá. Cada grupo es un `LinkGroup` con sus propias listas, hambre, vida y experiencia; `SharedInventoryState` sólo guarda los grupos y quién está en cada uno.

## Quién está en qué grupo

| Jugador | Grupo |
|---|---|
| Sin grupo asignado | `general` (`SharedInventoryState.GENERAL`) |
| Asignado con `/soullink grupo <nombre> <jugadores>` | Ese grupo, por UUID, en `members` |
| `/soullink grupo quitar <jugadores>` o `/soullink grupo general …` | Vuelve al general: se borra de `members` |

**Si nadie arma grupos, todos están en el general y el mod funciona igual que antes de los grupos.** Con 4 jugadores, `grupo rojo A B` + `grupo azul C D` da 2 y 2; `grupo solo E` deja a E sin compartir con nadie.

Los ids van en minúsculas, de 1 a 16 caracteres `[a-z0-9_-]` (`LinkGroups.normalize`): se escriben en comandos, en el archivo del mundo, en los puntos de Hero's Forge y en el chat.

`groupIdOf(uuid)` se usa en todos lados, incluido el constructor del jugador: `Player` asigna `gameProfile` **antes** de crear su `Inventory` (verificado con `javap` sobre el constructor), así que `InventoryMixin` ya sabe el UUID y engancha al jugador a las listas de su grupo. Login, respawn y salir del End funcionan sin nada aparte.

## Cambiar de grupo con el jugador conectado

`LinkGroups.move` no recrea al jugador: le cambia las referencias en el lugar, igual que los mixins en el constructor.

1. `forget` en el grupo viejo y en el nuevo: saca al jugador de `syncedHealth` y `syncedExperience`.
2. Anota el grupo en `members`.
3. `Inventory.items` (con `InventoryAccessor`, `@Mutable`), el `EnumMap` de su `EntityEquipment` y `Player.foodData` (con `PlayerAccessor`) pasan a ser los del grupo nuevo.
4. `sendAllDataToRemote()` del `inventoryMenu` y del menú abierto: cambió la lista entera, y `broadcastChanges` sólo manda slots que cambiaron contra lo último enviado.
5. Avisa a los listeners (`SoulLinkApi.onGroupChange`, que usa Hero's Forge).

**No uses `PlayerList.respawn` para reenganchar.** Crea un `ServerPlayer` nuevo (el constructor lo engancha bien), pero después `restoreFrom` con `keepEverything` hace `inventory.replaceWith(viejo)`: copiaría los ítems del grupo viejo en las listas del nuevo y quedarían duplicados.

**Sin el paso 1, el cambio de grupo se cuenta como daño o curación.** El próximo `syncHealth` del grupo viejo vería la diferencia entre la vida del grupo nuevo y la última que le repartió, y se la sumaría al grupo viejo.

## Qué se lleva el que cambia de grupo

**Nada.** Todo es del grupo, así que pasa a tener lo del grupo nuevo y lo suyo se queda en el viejo. Llevarse una copia duplicaría los ítems.

| | Al cambiar de grupo |
|---|---|
| Inventario, armadura, hambre | Los del grupo nuevo, en el mismo tick |
| Vida | En el próximo tick **adopta** la del grupo nuevo. Si el grupo es nuevo (`UNKNOWN_HEALTH`), aporta la suya |
| Experiencia | En el próximo tick adopta la del grupo nuevo. **Un grupo creado con `/soullink grupo` arranca en 0, no en `UNKNOWN_EXPERIENCE`** (`LinkGroup.create(false)`): si el primero aportara la suya, la experiencia de su grupo anterior quedaría duplicada |
| Estadísticas | Las suyas van con él (son por jugador). Los intentos son del grupo |
| Ítem en el cursor, ender chest | Son de cada jugador: no cambian |

Un grupo que se queda sin nadie **no se borra**: si vuelven, sus cosas siguen ahí.

El jugador tiene que estar conectado (`EntityArgument.players()`). Con uno desconectado habría que cambiar `members` sin reenganchar nada; hoy no está.

## Cada grupo por separado

| Qué | Dónde | Por grupo porque… |
|---|---|---|
| Reparto de vida | `SharedVitals.syncHealth` → `syncGroup` por cada grupo con alguien conectado (`onlineByGroup`) | El daño de un grupo no le llega a otro |
| Muerte | `killGroup` mata sólo a los del grupo de la víctima | Si muere azul, rojo sigue |
| Experiencia | `SharedExperience.sync` → `syncGroup` | Comprar con Hero's Forge gasta la del grupo |
| Quién tira los orbes al morir | `LinkGroup.claimDeathDrop` | Dos grupos pueden morir en el mismo tick: con uno solo global, el segundo grupo no tiraría nada |
| `FoodData.tick()` una vez por tick | `FoodDataMixin` con `@Unique lastTick` en cada `FoodData` | Ya era por objeto; `isShared` reconoce el hambre de cualquier grupo |
| Bloquear la carga del archivo del jugador | `shouldBlockLoadInto` → `isShared` busca en todos los grupos | Si sólo mirara el general, entrar con un archivo pisaría las listas de rojo |
| Intentos, tiempo, récord | `GroupStats.run(grupo)` | Ver [estadísticas](estadisticas.md#el-intento) |
| `/intercambiar` sin nombre | El único compañero **de su grupo** | Con dos grupos de 2, sin nombre se cambia con su pareja |

Lo que sigue siendo del mundo: `initialized` (el primer jugador tras instalar el mod llena el general), los ítems rescatados de antes del mod y la experiencia de antes del mod (se suma al grupo en el que entra).

## Archivo

Ver [persistencia](persistencia.md). El general va en la raíz de `shared_inventory.dat` con los mismos campos que hasta la 2.1.0 (`LinkGroup.MAP_CODEC` mezclado en la raíz), y los demás en `groups`. Un mundo viejo se lee sin migrar, y lo que escribe la 3.0.0 lo sigue leyendo una versión anterior (que sólo ve el general).

## API para otros mods

`com.soullink.api.SoulLinkApi` es lo único pensado para usar desde afuera: `groupOf`, `syncHealth`, `syncExperience`, `ignoreHealthChange` y `onGroupChange`. Hero's Forge la llama por reflexión, sin depender del jar, así que **cambiar un nombre o una firma rompe Hero's Forge en runtime, no al compilar**. Por eso `fabric.mod.json` declara `"breaks": {"herosforge": "<0.14.0"}`: las versiones anteriores tocaban clases internas que ya no existen (`SharedInventoryState.syncedHealth`).

Con Trinkets Updated, `SharedTrinkets` se suscribe a `LinkGroups.onChange` y reengancha los slots de Trinkets del que se mueve (ver [trinkets](trinkets.md)).

## Ver también

- [Inventario compartido](inventario-compartido.md): el invariante de "mismo objeto", ahora por grupo.
- [Vida y hambre](vida-y-hambre.md)
- [Experiencia](experiencia.md)
- [Muerte y respawn](muerte-y-respawn.md)
- [Persistencia](persistencia.md)
- [Estadísticas](estadisticas.md)
- [Intercambiar lugar](intercambiar-lugar.md)
- [Tests: inventario compartido](../tests/inventario-compartido.md): los pasos con cuatro jugadores en dos grupos.
