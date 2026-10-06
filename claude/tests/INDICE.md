# Índice — Tests (`src/gametest`)

Fabric GameTest API · `gradlew runGameTest` levanta un servidor sin ventana, corre los tests y sale con error si alguno falla.

[← Hub](../../CLAUDE.md)

| Nota | De qué trata |
|---|---|
| [Inventario compartido](inventario-compartido.md) | Hay **un solo método de test a propósito**: el inventario es global del servidor y tests en paralelo se pisan · qué cubre (sync en ambos sentidos, armadura, carga de datos viejos, respawn, muerte) y qué no (dos clientes reales) · el log dice "2 tests" aunque haya uno nuestro |
| [Jugadores de prueba](jugadores-de-prueba.md) | `makeMockServerPlayerInLevel()` pasa por `placeNewPlayer` real (dispara JOIN y la carga de datos) · arrancan en **creativo** e **invulnerables** hasta `hasClientLoaded()`, que nunca llega: `kill()` no hace nada, usá `die()` · el método está deprecado para remoción |
