# Intercambiar lugar

[← Índice Mod](INDICE.md)

**`/intercambiar [jugador]` deja a cada uno donde estaba el otro: posición, dimensión y hacia dónde mira.** Lo puede usar cualquiera, sin permisos de operador. Está en `SwapPlaces` y se registra con `CommandRegistrationCallback` en `SoulLink`. Es un comando de brigadier con un `EntityArgument.player()`, así que un cliente vanilla lo autocompleta sin tener el mod.

## A quién elige

| Uso | Con quién intercambia |
|---|---|
| `/intercambiar` con un solo jugador más conectado **en su grupo** | Con ése, aunque haya jugadores de otros grupos |
| `/intercambiar` con cero o con varios más en su grupo | Falla con un mensaje: con varios hay que nombrarlo |
| `/intercambiar <jugador>` | Con el nombrado, sea o no de su grupo. Los selectores (`@p`, `@r`) siguen la regla de vanilla, que los reserva a operadores; el nombre no pide nada |

**No intercambia si alguno de los dos está muerto.** Un jugador en la pantalla de muerte sigue en `PlayerList` y `isDeadOrDying()` da `true`; teletransportarlo no lo revive y el otro terminaría en el lugar donde murió. Creativo y espectador sí pueden intercambiar.

## Cómo mueve

**Las dos ubicaciones se leen antes de mover a nadie** (`Place.of`). Si no, el segundo teleport usaría la posición del primero, que ya se movió, y los dos terminarían en el mismo lugar.

Cada uno se mueve con `ServerPlayer.teleportTo(ServerLevel, x, y, z, Set.of(), yRot, xRot, true)`, lo mismo que usa `/tp`. Ese método ya:

- cambia de dimensión si el destino está en otro `ServerLevel` (el `ServerPlayer` es el mismo objeto: no pasa por `PlayerList.respawn`, así que lo compartido no se toca);
- baja al jugador del vehículo (`removeVehicle()` en `ServerPlayer.teleport`);
- lo despierta si estaba durmiendo.

Después, igual que `TeleportCommand.performTeleport`, si no está planeando con élitros se le anula la velocidad vertical y se lo marca en el piso. Además se hace **`resetFallDistance()`**, que `/tp` no hace: si uno venía cayendo y el otro estaba parado, el que cae aparece en el piso con la caída acumulada y se lastima, y como la vida es compartida, se lastiman los dos.

## Ver también

- [Muerte y respawn](muerte-y-respawn.md): por qué cambiar de dimensión no rompe lo compartido y reaparecer sí crea otro `ServerPlayer`.
- [Tests: inventario compartido](../tests/inventario-compartido.md): el paso que intercambia entre el Overworld y el Nether.
- [Grupos vinculados](grupos.md): sin nombre, elige sólo dentro del grupo.
