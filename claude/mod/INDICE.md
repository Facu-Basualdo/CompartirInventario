# Índice — Mod (`src/main`)

Java 25 · Fabric API 0.162.0+26.3 · Mixins sobre Minecraft 26.3 (nombres de Mojang).

[← Hub](../../CLAUDE.md)

## Protocolo antes de tocar un mixin

1. Leé [inventario-compartido](inventario-compartido.md): el invariante de "mismo objeto" condiciona todo lo demás.
2. Si el cambio toca carga, guardado, muerte o respawn, abrí la nota de ese tema.
3. Verificá la firma real del método con `javap` (ver [minecraft-26-sin-ofuscar](../general/minecraft-26-sin-ofuscar.md)) en vez de confiar en tutoriales o en memoria.
4. Corré `gradlew runGameTest`.

No lances agentes de exploración sobre el código del mod: son 28 clases y todo lo que no dicen está en estas notas.

## Fundamentos

| Nota | De qué trata |
|---|---|
| [Inventario compartido](inventario-compartido.md) | Los jugadores comparten **la misma instancia** de `NonNullList` y del `EnumMap` de equipment, no copias · se asignan en el `<init>` de `Inventory` sólo para `ServerPlayer` · no hay código de red: el sync de menús de vanilla ya manda los slots cambiados · el mod sólo lo adelanta (`broadcastChanges()` de los demás al volver de 14 handlers de paquetes y en `END_SERVER_TICK`): sin eso tardaba hasta 1 tick · `require = 14`: si agregás un handler, subí el número · a quien está picando no se le manda el slot de su mano hasta que termina (`AbstractContainerMenuMixin`): el cliente de vanilla reinicia el progreso si la herramienta pierde durabilidad · slot seleccionado, cursor y ender chest siguen siendo por jugador |
| [Experiencia](experiencia.md) | Son campos primitivos: se reparte por deltas **en puntos** en `END_SERVER_TICK`, después de la vida · vanilla sólo reenvía la barra si cambia `totalExperience`: hay que poner `lastSentExp = -1` (encantar no lo cambia) · quien entra o reaparece adopta; suma la suya sólo el primero con el mod recién instalado y quien trae un archivo de antes del mod · al morir el grupo, `getBaseExperienceReward` da 0 a todos menos al primero que muere en el tick · se pierde al morir salvo con `keepInventory` |
| [Vida y hambre](vida-y-hambre.md) | El hambre es un solo `FoodData` por referencia, pero la vida **no**: se reparte sumando deltas en `END_SERVER_TICK` (hasta 1 tick de demora) · `FoodData.tick()` se corta a una vez por tick o la regeneración va al doble · quien entra o reaparece **adopta** la vida, no la aporta · creativo/espectador no participan |

## Datos

| Nota | De qué trata |
|---|---|
| [Carga de datos del jugador](carga-de-datos-del-jugador.md) | `Inventory.load()` hace `clear()`, `EntityEquipment.setAll()` hace `clear()+putAll()` y `FoodData.readAdditionalSaveData()` pisa el hambre: sin bloquearlos, cada login borra lo del otro · se bloquean sólo con `initialized=true` · el primer jugador tras instalar el mod aporta sus ítems y su hambre · los ítems del resto van a un **cofre al lado suyo**, y la marca `InventarioCompartido` en su archivo (con el nombre viejo a propósito) es lo único que evita duplicarlos en cada login · al poner el cofre se fuerza `saveAllChunks` para que esté en disco antes que la marca |
| [Persistencia](persistencia.md) | Se guarda en `data/soullink/shared_inventory.dat` del mundo (ítems, armadura, hambre y vida), no en el NBT de cada jugador · `isDirty()` devuelve siempre `true` porque los `ItemStack` y el hambre se mutan sin avisar · `syncedHealth` no se guarda: es por objeto `ServerPlayer` · `DataFixTypes` null sólo funciona porque Fabric API lo parchea (vanilla da NPE) · el default de un codec es una instancia única: no uses objetos mutables |

## Ciclo de vida

| Nota | De qué trata |
|---|---|
| [Muerte y respawn](muerte-y-respawn.md) | Si muere uno, **mueren todos** al final del tick, y `dropAll()` tira los ítems una sola vez · ítems y experiencia caen donde está **el que recibió el golpe**: `killGroup` mata primero a la víctima (con dos golpes simultáneos, la que más vida perdió), no en el orden de `PlayerList` · el hambre se reinicia y la vida queda en `UNKNOWN_HEALTH` hasta que reaparece alguien · el respawn crea un `ServerPlayer` nuevo que se reengancha en el constructor · `replaceWith()` sobre la misma lista es un no-op, no lo "arregles" |

## Grupos

| Nota | De qué trata |
|---|---|
| [Grupos vinculados](grupos.md) | Lo compartido es de un `LinkGroup`, no del servidor · sin grupo = `general` (como antes) · el constructor ya conoce el UUID (`gameProfile` se asigna antes del `Inventory`) · `LinkGroups.move` reasigna `items`, equipment y `foodData` en el lugar y hace `forget` en los dos grupos · **no uses `PlayerList.respawn`**: `restoreFrom` copiaría el inventario viejo en el nuevo · el que cambia de grupo no se lleva nada · grupo nuevo con experiencia en 0, no `UNKNOWN` · `claimDeathDrop` y `shouldBlockLoadInto` son por grupo · el general va en la raíz del `.dat` · `SoulLinkApi` es la única API externa: Hero's Forge la llama por reflexión |

## Estadísticas y configuración

| Nota | De qué trata |
|---|---|
| [Estadísticas](estadisticas.md) | Sin listeners propios: salen de los **mismos deltas** de `syncHealth` y `SharedExperience.sync` · el daño se suma **antes** de `killGroup` (el golpe final cuenta) · culpa = la víctima que ya elige `syncHealth` · el reloj del intento sólo corre con un participante vivo conectado, y va después de la vida en `END_SERVER_TICK` · la causa se guarda como `Component` (traducible), no como texto · `data/soullink/stats.dat` aparte del inventario · jugadores por UUID · el scoreboard es una copia: se reescribe en `SERVER_STARTED` · la barra lateral no se pisa si la ocupa un objetivo ajeno |
| [Configuración](configuracion.md) | `config/soullink.json` con 4 claves · **inventario, hambre y vida no se pueden apagar**: duplicaría ítems (cada `playerdata` tiene una copia) o los perdería (la marca impide rescatarlos) · campo inválido = default + aviso, JSON roto = se queda la config anterior · con avisos no se reescribe el archivo · al volver a prender la experiencia, todos adoptan la guardada y pierden la propia |

## Comandos

| Nota | De qué trata |
|---|---|
| [Intercambiar lugar](intercambiar-lugar.md) | `/intercambiar [jugador]`, sin permisos: cambian posición, dimensión y mirada · sin argumento elige solo si hay un único compañero **en su grupo** · no intercambia con un jugador muerto (sigue en `PlayerList`) · se leen las dos ubicaciones antes de mover a nadie · `teleportTo` de `/tp` ya cambia de dimensión, baja del vehículo y despierta · `resetFallDistance()` para que el que venía cayendo no se lastime al aparecer en el piso (la vida es compartida) |
| [Migración del id](migracion-de-id.md) | Desde la 2.0.0 el id es `soullink` (antes `inventariocompartido`) · `IdMigration` mueve `data/inventariocompartido/shared_inventory.dat` a `data/soullink/` en `SERVER_STARTING`, antes de cargar los mundos; si ya hay uno nuevo no lo toca, y si falla no arranca · **la marca `InventarioCompartido` del archivo de cada jugador no se renombra**: con otra clave, todos recibirían otra vez sus ítems viejos |
