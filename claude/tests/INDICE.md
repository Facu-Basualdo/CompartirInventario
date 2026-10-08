# Índice — Tests (`src/gametest`)

Fabric GameTest API · `gradlew runGameTest` levanta un servidor sin ventana, corre los tests y sale con error si alguno falla.

[← Hub](../../CLAUDE.md)

| Nota | De qué trata |
|---|---|
| [Inventario compartido](inventario-compartido.md) | Hay **un solo método de test a propósito**: lo compartido es global del servidor y tests en paralelo se pisan · es una secuencia con `thenIdle(1)` porque la vida se reparte al final del tick · qué cubre (inventario, armadura, carga, cofre con ítems de antes del mod, hambre, vida, experiencia, respawn, muerte con drops al lado de la víctima) y qué no · al jugador recién recreado no le des experiencia en el mismo tick: adopta la compartida y la pierde (dos clientes reales, daño real, primer jugador tras instalar) · si un paso falla la secuencia sigue: un `null` en el paso siguiente crashea el servidor y tapa el mensaje · el sync inmediato y el hacha retenida mientras B pica se verifican con un `RecordingSynchronizer` en B · para picar se llama a `b.gameMode.handleBlockBreakAction` directo, sin pasar por el paquete · el log dice "2 tests" aunque el nuestro sea uno |
| [Jugadores de prueba](jugadores-de-prueba.md) | **No uses `makeMockServerPlayerInLevel()`**: su subclase fuerza `gameMode()` = CREATIVE y la vida no se comparte; usá `joinSurvivalPlayer` · son invulnerables (`hasClientLoaded()` nunca llega): para matar, `setHealth(0)` + `die()` · para simular partida previa, la sobrecarga con `archivo` y `pos` (ubicalo en aire o no hay lugar para el cofre) · los handlers de paquetes los ignoran por lo mismo: `handleAcceptPlayerLoad` los habilita, pero el jugador deja de ser invulnerable · sacá `"UUID"` de un guardado antes de cargarlo en otro jugador |
