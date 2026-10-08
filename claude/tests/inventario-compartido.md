# Tests: inventario compartido

[← Índice Tests](INDICE.md)

`src/gametest/java/com/inventariocompartido/test/SharedInventoryGameTest.java`, método `playersShareInventoryHungerAndHealth`. Se corre con `gradlew runGameTest`, que levanta un servidor sin ventana, ejecuta todos los `@GameTest`, guarda el mundo en `build/run/gameTest/` y sale con error si algo falla.

## Un solo método, a propósito

**No partas el test en varios.** Lo compartido es global del servidor, y los tests de un mismo batch corren en el mismo tick: dos tests que agregan diamantes o se sacan vida a la vez se pisan y fallan sin razón aparente. Si hace falta aislar casos, el test tiene que limpiar el estado (`clearContent()`) y los batches tienen que ir separados.

El log dice **"2 GAME TESTS COMPLETE"** y **"All 2 required tests passed"** aunque el nuestro sea uno solo. No hay otro test nuestro duplicado.

## Es una secuencia con ticks en el medio

La vida se reparte en `END_SERVER_TICK` (ver [vida y hambre](../mod/vida-y-hambre.md)), así que el test usa `helper.startSequence()` con `.thenIdle(1)` entre "cambiar la vida" y "verificar". `GameTestServer.tickServer` llama a `super.tickServer` (donde Fabric dispara `END_SERVER_TICK`) antes de correr los tests, así que **un `thenIdle(1)` alcanza**.

Una aserción que falla dentro de `thenExecute` marca el test como fallido (`executeWithoutFail` → `GameTestInfo.fail`): **no se reintenta**. Para esperar a que algo pase está `thenWaitUntil`.

**Pero la secuencia sigue con los pasos siguientes.** Si un paso corta antes de asignar algo que usa el siguiente (por ejemplo `veteran[0]`), el siguiente tira un `NullPointerException`, el servidor crashea y **el mensaje de la aserción original no aparece en el log**. Ante un crash del test en un paso que no tocaste, sospechá del paso anterior.

`maxTicks = 100` en el `@GameTest`, porque la secuencia dura varios ticks.

## Qué cubre

En orden, sobre dos jugadores A y B en supervivencia:

| Paso | Qué verifica | Nota del mod |
|---|---|---|
| A tira un ítem con `handlePlayerAction(DROP_ITEM)` | B recibe el slot nuevo **en el mismo tick**, antes de su propio `tick()` | [inventario-compartido](../mod/inventario-compartido.md#el-aviso-a-los-demás-clientes-se-adelanta) |
| B empieza a picar un tronco con el hacha compartida (`b.gameMode.handleBlockBreakAction(START_DESTROY_BLOCK)`) y A le baja la durabilidad | B **no** recibe el hacha gastada mientras pica; sí la recibe después del `ABORT_DESTROY_BLOCK` | [inventario-compartido](../mod/inventario-compartido.md#la-herramienta-del-que-pica-no-se-actualiza-hasta-que-termina) |
| A agrega 5 diamantes, B saca 2 | Sync de la lista en los dos sentidos | [inventario-compartido](../mod/inventario-compartido.md) |
| A se pone casco, B se pone escudo en la mano secundaria | Sync del `EnumMap` de equipment | [inventario-compartido](../mod/inventario-compartido.md) |
| Se guarda A, cambian ítems y hambre, se carga el guardado viejo en B | `load()`, `setAll()` y `readAdditionalSaveData()` bloqueados | [carga-de-datos-del-jugador](../mod/carga-de-datos-del-jugador.md) |
| Entra C con un archivo **sin marca** (3 manzanas doradas y botas de diamante) | No entran a lo compartido; aparece **un** cofre al lado de C con todo | [carga-de-datos-del-jugador](../mod/carga-de-datos-del-jugador.md#cofre-con-los-ítems-de-antes-del-mod) |
| Se guarda a C (ahora con la marca) y entra otro jugador con ese archivo | No aparece un segundo cofre | [carga-de-datos-del-jugador](../mod/carga-de-datos-del-jugador.md#cofre-con-los-ítems-de-antes-del-mod) |
| `a.getFoodData() == b.getFoodData()` y se cambia la comida de A | El hambre es el mismo objeto | [vida-y-hambre](../mod/vida-y-hambre.md) |
| A baja a 10 → B en 10; B se cura 4 → A en 14 | Reparto de vida en los dos sentidos | [vida-y-hambre](../mod/vida-y-hambre.md) |
| A pierde 2 y B pierde 3 en el mismo tick → los dos en 9 | Se suman deltas, no se copia el último | [vida-y-hambre](../mod/vida-y-hambre.md) |
| `PlayerList.respawn(A, true, …)` | El `ServerPlayer` nuevo comparte inventario y hambre, adopta la vida y no cura a B | [muerte-y-respawn](../mod/muerte-y-respawn.md) |
| A muere | El inventario queda vacío, B muere en el tick siguiente y el hambre vuelve a 20 | [muerte-y-respawn](../mod/muerte-y-respawn.md) |

## Qué NO cubre

- **Dos clientes reales.** La conexión es falsa: el paquete de vida hacia el cliente no se ejercita, y `broadcastChanges` sólo se verifica para tirar un ítem y para el hacha retenida mientras se pica, cambiándole a B el `ContainerSynchronizer` por un `RecordingSynchronizer` que anota los slots que se mandarían. Que el cliente real ya no reinicie el progreso de picar no tiene aserción: sólo se verifica que el slot no salga. Los otros 13 handlers y el broadcast de `END_SERVER_TICK` no tienen aserción. Para eso está [probar con dos jugadores](../general/probar-con-dos-jugadores.md).
- **Daño real** (mobs, caída, inanición): los jugadores de prueba son invulnerables. Ver [jugadores de prueba](jugadores-de-prueba.md).
- **Que `FoodData.tick()` corra una sola vez por tick.** No hay aserción sobre la velocidad de regeneración.
- **El primer jugador tras instalar el mod** (con `initialized = false`, sus ítems pasan a ser los compartidos). Cuando corre el test, A y B ya marcaron el estado como inicializado.
- **El caso sin lugar para el cofre** (ítems tirados a los pies).
- **Que el cofre quede en disco.** El guardado forzado (`saveAllChunks`) se verificó a mano pasando `suppressLog = false`: aparecen los "Saving chunks for level…" justo después del log del cofre. Ninguna aserción lo comprueba.
- **El guardado y la recarga del `.dat` entre reinicios.** El archivo se escribe (en `build/run/gameTest/world/data/inventariocompartido/`), pero ningún test reinicia el servidor y lo lee.

## Ver también

- [Jugadores de prueba](jugadores-de-prueba.md): por qué no se usa `makeMockServerPlayerInLevel` y cómo simular la muerte.
- [Inventario compartido (mod)](../mod/inventario-compartido.md)
- [Vida y hambre (mod)](../mod/vida-y-hambre.md)
