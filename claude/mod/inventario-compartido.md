# Inventario compartido

[← Índice Mod](INDICE.md)

Si un jugador agarra un diamante y el otro no lo ve, o lo ve y después desaparece, casi seguro que alguien rompió el invariante de esta nota: **los jugadores no sincronizan copias, comparten el mismo objeto en memoria.**

## Cómo funciona

`InventoryMixin` engancha el final del constructor de `Inventory` (`@Inject(method = "<init>", at = @At("TAIL"))`). Si el dueño es un `ServerPlayer`, reemplaza sus listas por las de **su grupo** (`SharedInventoryState.groupFor(player)`, ver [grupos vinculados](grupos.md)):

| Qué | Campo en vanilla | Contenido | Se reemplaza por |
|---|---|---|---|
| Inventario + barra rápida | `Inventory.items` (`NonNullList<ItemStack>`, 36 slots, `final`) | slots 0–35 | `LinkGroup.items()` |
| Armadura + mano secundaria | `EntityEquipment.items` (`EnumMap<EquipmentSlot, ItemStack>`, `final`) | slots 36–40 de `Inventory` mapean acá | `LinkGroup.equipmentItems()` |

Los dos campos son `final`, por eso `items` lleva `@Mutable` en el `@Shadow` y el setter de `EntityEquipmentAccessor` lleva `@Mutable`.

**La mano principal no está en el `EnumMap`.** `PlayerEquipment` intercepta `MAINHAND` y devuelve `inventory.getSelectedItem()`, o sea un slot de la barra rápida (ya compartida). Por eso compartir el mapa entero es seguro.

## El invariante

**Nunca reemplaces, copies ni reasignes la lista o el mapa compartidos: mutalos.** Si en algún punto un jugador termina con una lista propia (un `new NonNullList`, un `EnumMap` copiado), deja de compartir sin dar error, y el próximo guardado decide quién "gana".

Consecuencias prácticas:

- **No hace falta código de red.** `ServerPlayer.tick()` llama a `containerMenu.broadcastChanges()`, que compara cada slot con lo último enviado al cliente. Como el slot lee de la lista compartida, cualquier cambio hecho por el otro jugador aparece como diferencia y se manda. Funciona también con un cofre abierto, porque ese menú incluye los slots del inventario del jugador. El mod sólo **adelanta** ese aviso (ver [abajo](#el-aviso-a-los-demás-clientes-se-adelanta)).
- **Mutar un `ItemStack` afecta a los dos.** `stack.shrink(1)` al poner un bloque descuenta del stack compartido; eso es lo buscado.
- **Los `inventoryTick` corren una vez por jugador.** `Inventory.tick()` y `EntityEquipment.tick()` recorren los ítems de cada jugador, así que con dos jugadores cada ítem recibe dos `inventoryTick` por tick. Con ítems vanilla no hay efecto visible. Si aparece un ítem que se gasta o anima al doble, el origen es éste.

## El aviso a los demás clientes se adelanta

Síntoma sin esto: el jugador 1 pone un bloque o tira un ítem y el jugador 2 ve la cantidad vieja durante un instante.

Los paquetes del cliente se procesan **entre ticks**, en la cola de tareas del servidor. Vanilla sólo llama a `broadcastChanges()` dentro de algunos handlers (`handleContainerClick`, `handleContainerButtonClick`, `handlePickItemFromEntity`, `handleSetCreativeModeSlot`) y sólo para **quien mandó el paquete**. Los demás se enteraban en su próximo `ServerPlayer.tick()`: hasta 50 ms más el ping.

| Dónde | Qué hace | Cubre |
|---|---|---|
| `ServerGamePacketListenerImplMixin`, `@At("RETURN")` de 14 handlers | `SharedInventorySync.broadcastToOthers(player)`: `broadcastChanges()` de todos menos el que mandó el paquete | Poner bloques, usar o tirar ítems, romper bloques (durabilidad), atacar, interactuar con entidades, clicks en menús, cerrar el inventario (la grilla 2×2 devuelve ítems), recetario, trueques, creativo, pick block, bundles |
| `END_SERVER_TICK` → `SharedInventorySync.broadcastToAll` | `broadcastChanges()` de todos | Lo que cambia **durante** el tick, por ejemplo levantar ítems del piso, si el otro jugador ya había hecho su broadcast en ese tick |

- **`RETURN` sólo se alcanza en el hilo del servidor.** Cada handler se llama primero en el hilo de red, donde `PacketUtils.ensureRunningOnSameThread` lo reencola y corta con una excepción. Igual `broadcastToOthers` chequea `server.isSameThread()`.
- **El `@Inject` lleva `require = 14`, uno por handler.** Con un array de métodos, `defaultRequire: 1` se conforma con que exista uno. Si en otra versión de Minecraft se renombra un handler, tiene que fallar al cargar y no dejar de sincronizar ese caso sin avisar. Si agregás o sacás un handler, actualizá el número.
- **Un handler nuevo que toque el inventario hay que agregarlo a la lista.** Si no, funciona igual, pero con la demora de un tick.
- **Es barato:** `broadcastChanges()` compara unos 46 slots, y vanilla ya lo hace en cada tick. No se enganchan paquetes de movimiento, que llegan muchas veces por segundo.
- **No acelera lo que el otro ve en la mano del jugador** (el modelo 3D). Eso es el equipment de la entidad, que `LivingEntity` sincroniza aparte una vez por tick.

## La herramienta del que pica no se actualiza hasta que termina

Síntoma sin esto: dos jugadores pican bloques distintos con la misma hacha y, cuando uno rompe el suyo, al otro se le reinicia el progreso.

**La causa está en el cliente de vanilla, no en el mod.** `MultiPlayerGameMode.continueDestroyBlock` llama a `sameDestroyTarget`, que compara el ítem de la mano con el que el cliente anotó al empezar (`destroyingItem`) usando `ItemStack.isSameItemSameComponents`. La durabilidad es el componente `DAMAGE`: cuando el otro rompe su bloque, el servidor le baja la durabilidad al hacha compartida, manda el slot nuevo y el cliente lo toma como "cambiaste de herramienta" y vuelve a empezar. En vanilla no pasa porque la herramienta sólo se gasta cuando su dueño termina.

**El arreglo es del servidor: a quien está picando no se le manda el slot de su mano mientras siga picando con ese mismo objeto.**

| Dónde | Qué hace |
|---|---|
| `ServerPlayerGameModeMixin`, `@At("HEAD")` de `handleBlockBreakAction` | Con `START_DESTROY_BLOCK` anota `player.getMainHandItem()`: el mismo ítem que anota el cliente |
| `ServerPlayerGameModeMixin.soullink$isMiningWith(stack)` (interfaz `BlockMiner`) | `true` si `isDestroyingBlock`, el stack es **el mismo objeto** que se anotó y no está vacío |
| `AbstractContainerMenuMixin`, `@At("HEAD")` de `synchronizeSlotToRemote`, cancelable | Si el slot es del `Inventory` de un `ServerPlayer`, es su slot seleccionado y ese jugador está picando con ese stack, no lo manda |

- **Retener funciona porque no se marca como enviado.** `synchronizeSlotToRemote` cancela antes de `RemoteSlot.force()`, así que el próximo `broadcastChanges()` vuelve a ver la diferencia. Cuando el jugador termina (`STOP_DESTROY_BLOCK`) o suelta (`ABORT_DESTROY_BLOCK`), vanilla pone `isDestroyingBlock = false` y el slot sale en el siguiente broadcast.
- **La comparación es por identidad, no por ítem.** `setDamageValue` muta el mismo `ItemStack`, así que el hacha gastada sigue siendo el objeto anotado. Si el otro jugador **saca** el hacha de ese slot o pone otra cosa, el objeto es otro y se manda en el momento: ahí sí corresponde que el cliente corte, porque ya no tiene la herramienta.
- **Si el hacha se rompe, se manda.** El stack queda con cantidad 0 (`isEmpty()`) y el chequeo lo deja pasar.
- **El que rompe su propio bloque no se retiene a sí mismo.** Vanilla pone `isDestroyingBlock = false` en `STOP_DESTROY_BLOCK` **antes** de `destroyAndAck`, que es donde se gasta la herramienta.
- **El servidor no tenía el problema.** `handleBlockBreakAction` calcula el progreso con ticks transcurridos y `getDestroyProgress` con la herramienta actual: la durabilidad no cambia la velocidad.
- **También aplica sin compartir.** Un hacha que se repara con Mending mientras picás reinicia el progreso en vanilla; con el mod se retiene igual. No rompe nada: sólo demora el número de durabilidad hasta que terminás.
- **`broadcastFullState()` y `sendAllDataToRemote()` no pasan por `synchronizeSlotToRemote`** y mandan todo igual. Se usan al abrir menús o cuando el cliente y el servidor se desincronizan en un click, cosas que no pasan mientras se pica.

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

## Un inventario por grupo

**Es un inventario por grupo vinculado, no por servidor.** Sin grupos armados, todos están en el general y comparten uno solo; con `/soullink grupo` cada grupo tiene el suyo. Cambiar de grupo con el jugador conectado reasigna `items`, el mapa de equipment y el `FoodData` en el lugar (`LinkGroups.move`, con `InventoryAccessor` y `PlayerAccessor`): detalle en [grupos vinculados](grupos.md).

## Ver también

- [Vida y hambre](vida-y-hambre.md): el hambre usa el mismo truco de referencia; la vida no puede.
- [Carga de datos del jugador](carga-de-datos-del-jugador.md): por qué hay que bloquear `load()` y `setAll()`.
- [Muerte y respawn](muerte-y-respawn.md): qué pasa cuando se crea un `ServerPlayer` nuevo.
- [Persistencia](persistencia.md): dónde vive la lista compartida.
- [Tests: inventario compartido](../tests/inventario-compartido.md): qué parte de esto está probada.
- [Grupos vinculados](grupos.md): lo compartido es de cada grupo, no del servidor.
