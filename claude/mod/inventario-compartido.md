# Inventario compartido

[← Índice Mod](INDICE.md)

Si un jugador agarra un diamante y el otro no lo ve, o lo ve y después desaparece, casi seguro que alguien rompió el invariante de esta nota: **los jugadores no sincronizan copias, comparten el mismo objeto en memoria.**

## Cómo funciona

`InventoryMixin` engancha el final del constructor de `Inventory` (`@Inject(method = "<init>", at = @At("TAIL"))`). Si el dueño es un `ServerPlayer`, reemplaza:

| Qué | Campo en vanilla | Contenido | Se reemplaza por |
|---|---|---|---|
| Inventario + barra rápida | `Inventory.items` (`NonNullList<ItemStack>`, 36 slots, `final`) | slots 0–35 | `SharedInventoryState.items()` |
| Armadura + mano secundaria | `EntityEquipment.items` (`EnumMap<EquipmentSlot, ItemStack>`, `final`) | slots 36–40 de `Inventory` mapean acá | `SharedInventoryState.equipmentItems()` |

Los dos campos son `final`, por eso `items` lleva `@Mutable` en el `@Shadow` y el setter de `EntityEquipmentAccessor` lleva `@Mutable`.

**La mano principal no está en el `EnumMap`.** `PlayerEquipment` intercepta `MAINHAND` y devuelve `inventory.getSelectedItem()`, o sea un slot de la barra rápida (ya compartida). Por eso compartir el mapa entero es seguro.

## El invariante

**Nunca reemplaces, copies ni reasignes la lista o el mapa compartidos: mutalos.** Si en algún punto un jugador termina con una lista propia (un `new NonNullList`, un `EnumMap` copiado), deja de compartir sin dar error, y el próximo guardado decide quién "gana".

Consecuencias prácticas:

- **No hace falta código de red.** `ServerPlayer.tick()` llama a `containerMenu.broadcastChanges()`, que compara cada slot con lo último enviado al cliente. Como el slot lee de la lista compartida, cualquier cambio hecho por el otro jugador aparece como diferencia y se manda. Funciona también con un cofre abierto, porque ese menú incluye los slots del inventario del jugador.
- **Mutar un `ItemStack` afecta a los dos.** `stack.shrink(1)` al poner un bloque descuenta del stack compartido; eso es lo buscado.
- **Los `inventoryTick` corren una vez por jugador.** `Inventory.tick()` y `EntityEquipment.tick()` recorren los ítems de cada jugador, así que con dos jugadores cada ítem recibe dos `inventoryTick` por tick. Con ítems vanilla no hay efecto visible. Si aparece un ítem que se gasta o anima al doble, el origen es éste.

## Qué es por jugador

| Cosa | Por qué no se comparte |
|---|---|
| Slot seleccionado de la barra (`Inventory.selected`) | Es un `int` del `Inventory`, no está en la lista. Cada uno puede tener otro slot en la mano. |
| Ítem en el cursor (`carried` del menú) | Vive en el `AbstractContainerMenu` de cada jugador. |
| Grilla de crafteo 2×2 | Es del `InventoryMenu`, no del `Inventory`. |
| Ender chest | Es otro `Container` (`PlayerEnderChestContainer`). No se tocó. |

## Sólo servidor

`SharedInventoryState.forPlayer()` devuelve `null` si el jugador no es `ServerPlayer` o si `level().isClientSide()`. **En singleplayer el cliente (`LocalPlayer`) y el servidor integrado (`ServerPlayer`) corren en el mismo proceso**: sin ese chequeo, el inventario del cliente también apuntaría a la lista del servidor y el sync de vanilla se volvería loco.

Durante el constructor de `Inventory`, el campo `ServerPlayer.server` todavía es `null` (los campos de la subclase se asignan después de `super()`). Por eso se usa `player.level().getServer()`: `level` ya lo asignó el constructor de `Entity`.

## Todos los jugadores comparten

No hay emparejamiento: **es un único inventario para todo el servidor.** Un tercer jugador también entra al mismo inventario. Si se agrega emparejamiento (por ejemplo un comando `/compartir <jugador>`), `SharedInventoryState` tiene que pasar a ser un mapa de grupos y `shouldBlockLoadInto` tiene que buscar en todos.

## Ver también

- [Vida y hambre](vida-y-hambre.md): el hambre usa el mismo truco de referencia; la vida no puede.
- [Carga de datos del jugador](carga-de-datos-del-jugador.md): por qué hay que bloquear `load()` y `setAll()`.
- [Muerte y respawn](muerte-y-respawn.md): qué pasa cuando se crea un `ServerPlayer` nuevo.
- [Persistencia](persistencia.md): dónde vive la lista compartida.
- [Tests: inventario compartido](../tests/inventario-compartido.md): qué parte de esto está probada.
