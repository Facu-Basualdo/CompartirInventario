# Índice — Mod (`src/main`)

Java 25 · Fabric API 0.162.0+26.3 · Mixins sobre Minecraft 26.3 (nombres de Mojang).

[← Hub](../../CLAUDE.md)

## Protocolo antes de tocar un mixin

1. Leé [inventario-compartido](inventario-compartido.md): el invariante de "mismo objeto" condiciona todo lo demás.
2. Si el cambio toca carga, guardado, muerte o respawn, abrí la nota de ese tema.
3. Verificá la firma real del método con `javap` (ver [minecraft-26-sin-ofuscar](../general/minecraft-26-sin-ofuscar.md)) en vez de confiar en tutoriales o en memoria.
4. Corré `gradlew runGameTest`.

No lances agentes de exploración sobre el código del mod: son 5 clases y todo lo que no dicen está en estas notas.

## Fundamentos

| Nota | De qué trata |
|---|---|
| [Inventario compartido](inventario-compartido.md) | Los jugadores comparten **la misma instancia** de `NonNullList` y del `EnumMap` de equipment, no copias · se asignan en el `<init>` de `Inventory` sólo para `ServerPlayer` · no hay código de red: el sync de menús de vanilla ya manda los slots cambiados · slot seleccionado, cursor y ender chest siguen siendo por jugador |

## Datos

| Nota | De qué trata |
|---|---|
| [Carga de datos del jugador](carga-de-datos-del-jugador.md) | `Inventory.load()` hace `clear()` y `EntityEquipment.setAll()` hace `clear()+putAll()`: sin bloquearlos, cada login borra el inventario del otro · se bloquean sólo con `initialized=true` · el primer jugador tras instalar el mod aporta sus ítems, los del resto se pierden |
| [Persistencia](persistencia.md) | Se guarda en `data/inventariocompartido/shared_inventory.dat` del mundo, no en el NBT de cada jugador · `isDirty()` devuelve siempre `true` porque los `ItemStack` se mutan sin avisar · `DataFixTypes` null sólo funciona porque Fabric API lo parchea (vanilla da NPE) · el default de un codec es una instancia única: no uses objetos mutables |

## Ciclo de vida

| Nota | De qué trata |
|---|---|
| [Muerte y respawn](muerte-y-respawn.md) | Si muere uno, `dropAll()` vacía la lista compartida: los ítems caen una vez y el otro jugador queda vacío · el respawn crea un `ServerPlayer` nuevo que se reengancha en el constructor · `replaceWith()` sobre la misma lista es un no-op, no lo "arregles" |
