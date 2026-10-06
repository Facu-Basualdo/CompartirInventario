# Tests: inventario compartido

[← Índice Tests](INDICE.md)

`src/gametest/java/com/inventariocompartido/test/SharedInventoryGameTest.java`. Se corre con `gradlew runGameTest`, que levanta un servidor sin ventana, ejecuta todos los `@GameTest`, guarda el mundo en `build/run/gameTest/` y sale con error si algo falla.

## Un solo método, a propósito

**No partas `playersShareOneInventory` en varios tests.** El inventario compartido es global del servidor, y los tests de un mismo batch corren en el mismo tick: dos tests que agregan y cuentan diamantes a la vez se pisan y fallan sin razón aparente. Si hace falta aislar casos, el test tiene que limpiar el inventario (`clearContent()`) y los batches tienen que ir separados.

El log dice **"2 GAME TESTS COMPLETE"** y **"All 2 required tests passed"** aunque sólo `playersShareOneInventory` sea nuestro. No es un test duplicado.

## Qué cubre

En orden, sobre dos jugadores A y B:

| Paso | Qué verifica | Nota del mod |
|---|---|---|
| A agrega 5 diamantes, B saca 2 | Sync de la lista en los dos sentidos | [inventario-compartido](../mod/inventario-compartido.md) |
| A se pone casco, B se pone escudo en la mano secundaria | Sync del `EnumMap` de equipment | [inventario-compartido](../mod/inventario-compartido.md) |
| Se guarda A, se agregan esmeraldas, se carga el guardado viejo en B | `load()` y `setAll()` bloqueados: no se pierden las esmeraldas ni el casco | [carga-de-datos-del-jugador](../mod/carga-de-datos-del-jugador.md) |
| `PlayerList.respawn(A, true, …)` | El `ServerPlayer` nuevo sigue compartiendo | [muerte-y-respawn](../mod/muerte-y-respawn.md) |
| A muere | El inventario de B queda vacío | [muerte-y-respawn](../mod/muerte-y-respawn.md) |

## Qué NO cubre

- **Dos clientes reales.** El sync por paquetes (`broadcastChanges` → cliente) no se ejercita: los jugadores de prueba tienen una conexión falsa. Para eso está [probar con dos jugadores](../general/probar-con-dos-jugadores.md).
- **La migración del flag `initialized` con un mundo existente.** Los jugadores de prueba no tienen archivo guardado.
- **El guardado y la recarga del `.dat` entre reinicios.** El archivo se escribe (se puede ver en `build/run/gameTest/world/data/inventariocompartido/`), pero ningún test reinicia el servidor y lo lee.

## Ver también

- [Jugadores de prueba](jugadores-de-prueba.md): las trampas de `makeMockServerPlayerInLevel`.
- [Inventario compartido (mod)](../mod/inventario-compartido.md)
