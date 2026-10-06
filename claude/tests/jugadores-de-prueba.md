# Jugadores de prueba

[← Índice Tests](INDICE.md)

Síntoma con el que se descubrió: el test llamaba a `kill()` y el jugador **seguía vivo**, con el inventario intacto.

## `makeMockServerPlayerInLevel()`

Crea un `ServerPlayer` con un `GameProfile` aleatorio y una `Connection` sobre un `EmbeddedChannel` de Netty, y lo pasa por **`PlayerList.placeNewPlayer` real**. O sea:

- se dispara `ServerPlayConnectionEvents.JOIN` (y por lo tanto `markInitialized()`);
- se intenta cargar su `playerdata` (no tiene, así que `load()` no se llama);
- aparece en el chat: "test-mock-player joined the game".

**Está marcado `@Deprecated(forRemoval = true)`.** Compila con warning. Cuando lo saquen, hay que buscar el reemplazo en `GameTestHelper` con `javap` (ver [minecraft-26-sin-ofuscar](../general/minecraft-26-sin-ofuscar.md)).

**Quedan conectados al terminar el test**: hay que sacarlos con `playerList.remove(jugador)`.

## Las dos trampas

| Trampa | Por qué | Qué hacer |
|---|---|---|
| Arrancan en **creativo** | Observado en el servidor de tests (`gameMode()` devolvía `CREATIVE`) | `setGameMode(GameType.SURVIVAL)` si el test depende del modo |
| Son **invulnerables** | `ServerPlayer.isInvulnerableTo` devuelve `true` mientras `!connection.hasClientLoaded()`, y el cliente falso nunca manda el aviso de "terminé de cargar" | **Para probar la muerte llamá a `die(damageSources().genericKill())`**, no a `kill()` ni a `hurtServer()` |

`die()` es el punto que importa para el mod (dispara `dropEquipment` → `Inventory.dropAll()`), así que saltear el daño no le quita valor al test.

## Ver también

- [Tests: inventario compartido](inventario-compartido.md)
- [Muerte y respawn](../mod/muerte-y-respawn.md)
