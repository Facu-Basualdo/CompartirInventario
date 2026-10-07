# Jugadores de prueba

[← Índice Tests](INDICE.md)

Síntomas con los que se descubrió: `kill()` dejaba al jugador vivo, y la vida compartida no se sincronizaba nunca aunque el test pasara a los jugadores a supervivencia.

## No uses `makeMockServerPlayerInLevel()`

`GameTestHelper.makeMockServerPlayerInLevel()` crea una subclase anónima de `ServerPlayer` (`GameTestHelper$3`) que **sobreescribe `gameMode()` para devolver siempre `CREATIVE`**. `setGameMode(SURVIVAL)` no cambia nada. En creativo no se comparten vida ni hambre (ver [vida y hambre](../mod/vida-y-hambre.md#quién-participa)), así que con esos jugadores el test de vida no puede pasar. Además, el método está marcado `@Deprecated(forRemoval = true)`.

**Usá `joinSurvivalPlayer(helper, nombre)` del test.** Hace lo mismo que el helper de vanilla, pero con un `ServerPlayer` común:

1. `CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), nombre), false)`
2. `new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation())`
3. `new Connection(PacketFlow.SERVERBOUND)` envuelta en un `EmbeddedChannel` de Netty
4. `playerList.placeNewPlayer(connection, player, cookie)`, el login **real**: dispara `ServerPlayConnectionEvents.JOIN` e intenta cargar su `playerdata`
5. `setGameMode(GameType.SURVIVAL)`

**Para simular un jugador que ya tenía partida, usá `joinSurvivalPlayer(helper, nombre, archivo, pos)`.** Antes del paso 4 hace `player.load(archivo)`, igual que vanilla en el login (`placeNewPlayer` no carga el archivo: eso pasa antes), y ubica al jugador en `pos`. `placeNewPlayer` lo teletransporta a donde ya está, así que la posición se respeta. Ubicalo en el área del test, por ejemplo `helper.absolutePos(new BlockPos(1, 1, 1))`, que es aire: si queda enterrado, no hay lugar para cofres.

**Quedan conectados al terminar el test**: hay que sacarlos con `playerList.remove(jugador)`.

## Son invulnerables

`ServerPlayer.isInvulnerableTo` devuelve `true` mientras `!connection.hasClientLoaded()`, y el cliente falso nunca manda el aviso de "terminé de cargar". Por eso `kill()` y `hurtServer()` no hacen nada.

| Para probar… | Hacé |
|---|---|
| Daño o curación | `setHealth(x)` / `heal(x)`: no pasan por la invulnerabilidad |
| Muerte | **`setHealth(0)` y después `die(damageSources().genericKill())`**. Las dos cosas: el reparto de vida detecta la muerte por `getHealth()`, no por `die()` |

`die()` es el punto que importa para el mod (dispara `dropEquipment` → `Inventory.dropAll()`), así que saltear el daño no le quita valor al test.

## Cargar un guardado en otro jugador

Para simular "B entra con datos viejos" el test guarda a A con `saveWithoutId` y lo carga en B con `load`. **Sacá `"UUID"` del `CompoundTag` antes de cargarlo**: si no, B queda con el UUID de A, y `PlayerList` (que indexa por UUID) se confunde en los pasos siguientes.

## Ver también

- [Tests: inventario compartido](inventario-compartido.md)
- [Muerte y respawn](../mod/muerte-y-respawn.md)
- [Vida y hambre](../mod/vida-y-hambre.md)
