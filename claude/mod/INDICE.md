# Índice — Mod (`src/main`)

Java 25 · Fabric API 0.162.0+26.3 · Mixins sobre Minecraft 26.3 (nombres de Mojang).

[← Hub](../../CLAUDE.md)

## Protocolo antes de tocar un mixin

1. Leé [inventario-compartido](inventario-compartido.md): el invariante de "mismo objeto" condiciona todo lo demás.
2. Si el cambio toca carga, guardado, muerte o respawn, abrí la nota de ese tema.
3. Verificá la firma real del método con `javap` (ver [minecraft-26-sin-ofuscar](../general/minecraft-26-sin-ofuscar.md)) en vez de confiar en tutoriales o en memoria.
4. Corré `gradlew runGameTest`.

No lances agentes de exploración sobre el código del mod: son 10 clases y todo lo que no dicen está en estas notas.

## Fundamentos

| Nota | De qué trata |
|---|---|
| [Inventario compartido](inventario-compartido.md) | Los jugadores comparten **la misma instancia** de `NonNullList` y del `EnumMap` de equipment, no copias · se asignan en el `<init>` de `Inventory` sólo para `ServerPlayer` · no hay código de red: el sync de menús de vanilla ya manda los slots cambiados · slot seleccionado, cursor y ender chest siguen siendo por jugador |
| [Vida y hambre](vida-y-hambre.md) | El hambre es un solo `FoodData` por referencia, pero la vida **no**: se reparte sumando deltas en `END_SERVER_TICK` (hasta 1 tick de demora) · `FoodData.tick()` se corta a una vez por tick o la regeneración va al doble · quien entra o reaparece **adopta** la vida, no la aporta · creativo/espectador no participan |

## Datos

| Nota | De qué trata |
|---|---|
| [Carga de datos del jugador](carga-de-datos-del-jugador.md) | `Inventory.load()` hace `clear()`, `EntityEquipment.setAll()` hace `clear()+putAll()` y `FoodData.readAdditionalSaveData()` pisa el hambre: sin bloquearlos, cada login borra lo del otro · se bloquean sólo con `initialized=true` · el primer jugador tras instalar el mod aporta sus ítems y su hambre · los ítems del resto van a un **cofre al lado suyo**, y la marca `InventarioCompartido` en su archivo es lo único que evita duplicarlos en cada login · al poner el cofre se fuerza `saveAllChunks` para que esté en disco antes que la marca |
| [Persistencia](persistencia.md) | Se guarda en `data/inventariocompartido/shared_inventory.dat` del mundo (ítems, armadura, hambre y vida), no en el NBT de cada jugador · `isDirty()` devuelve siempre `true` porque los `ItemStack` y el hambre se mutan sin avisar · `syncedHealth` no se guarda: es por objeto `ServerPlayer` · `DataFixTypes` null sólo funciona porque Fabric API lo parchea (vanilla da NPE) · el default de un codec es una instancia única: no uses objetos mutables |

## Ciclo de vida

| Nota | De qué trata |
|---|---|
| [Muerte y respawn](muerte-y-respawn.md) | Si muere uno, **mueren todos** al final del tick, y `dropAll()` tira los ítems una sola vez · el hambre se reinicia y la vida queda en `UNKNOWN_HEALTH` hasta que reaparece alguien · el respawn crea un `ServerPlayer` nuevo que se reengancha en el constructor · `replaceWith()` sobre la misma lista es un no-op, no lo "arregles" |
