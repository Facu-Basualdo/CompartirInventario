# Vida y hambre compartidas

[← Índice Mod](INDICE.md)

**El hambre se comparte por referencia (como el inventario). La vida no se puede: se reparte al final de cada tick.** Si confundís los dos mecanismos, vas a buscar un objeto de "vida compartida" que no existe, o vas a intentar sincronizar a mano un `FoodData` que ya es el mismo.

## Hambre: un solo `FoodData`

`PlayerMixin` engancha el final del constructor de `Player` y, para cada `ServerPlayer`, reemplaza `Player.foodData` (campo `protected`, no `final`) por `SharedInventoryState.foodData()`. Nivel de comida, saturación, agotamiento y el timer de regeneración son uno solo.

**`FoodData.tick()` corre una sola vez por tick del servidor.** Cada jugador lo llama desde `ServerPlayer.doTick()`, así que con el objeto compartido correría una vez por jugador: la regeneración natural (que es un timer dentro de `FoodData`) y el daño por inanición irían al doble de velocidad. `FoodDataMixin` lo corta con un campo `@Unique lastTick` comparado contra `server.getTickCount()`.

**Un jugador en creativo o espectador no ejecuta el tick del `FoodData` compartido.** Si se lo quedara, la regeneración lo curaría a él (que no participa de la vida compartida) y el grupo no se curaría nunca.

El agotamiento de todos se suma en el mismo `FoodData`: si los dos corren, el hambre baja el doble de rápido. Eso es lo buscado.

## Vida: reparto en `END_SERVER_TICK`

La vida vive en los `SynchedEntityData` de cada entidad: no hay un objeto para compartir. `SharedVitals.syncHealth` corre en `ServerTickEvents.END_SERVER_TICK`:

1. Para cada jugador que ya estaba sincronizado, suma `vidaActual - vidaDelÚltimoReparto` a la vida compartida.
2. Clampa al máximo de vida de los que participan.
3. Le pone ese valor a cada participante vivo y guarda cuánto le quedó (después del clamp a su propio máximo).

**Se suman deltas, no se copia "el último que cambió".** Si A recibe 2 de daño y B recibe 3 en el mismo tick, la vida compartida baja 5. Copiar perdería uno de los golpes.

**El mapa de "vida del último reparto" (`syncedHealth`) es por objeto `ServerPlayer`, con `IdentityHashMap`.** Un jugador recién creado (login, respawn) no está en el mapa, así que **adopta** la vida compartida en vez de aportar un delta. Esto evita que entrar al servidor con 20 corazones cure a los demás.

**Hay hasta un tick (50 ms) de demora** entre el golpe y la actualización de la barra del otro jugador. El paquete de vida lo manda vanilla en `ServerPlayer.doTick` cuando nota el cambio.

| Situación | Qué pasa |
|---|---|
| Nadie sincronizado, `health` guardado > 0 | El que entra adopta la vida guardada (ej.: A se fue con 5, B entra al otro día y queda en 5) |
| Nadie sincronizado, `health` = `UNKNOWN_HEALTH` (-1) | El que entra aporta su propia vida. Pasa con el mod recién instalado o después de que murieron todos |
| Tótem de la inmortalidad | El jugador queda en 1 antes del fin del tick, así que el delta ya refleja que sobrevivió: no muere nadie |

## Muerte del grupo

Si la vida compartida llega a 0 y había jugadores sincronizados, `killGroup`:

- pone la vida compartida en `UNKNOWN_HEALTH` y reinicia el hambre (20 de comida, 5 de saturación), como un jugador recién creado;
- a cada participante todavía vivo le hace `setHealth(0)` y `die(fuente)`, con la `getLastDamageSource()` del que murió primero. El mensaje de muerte de B dice lo que mató a A.

**El jugador que muere tiene que tener la vida en 0.** El delta sale de `getHealth()`. Un `die()` llamado directo, sin bajar la vida (por ejemplo desde otro mod), mata solo a ese jugador. Con daño real, `/kill` o inanición la vida siempre llega a 0.

## Quién participa

`SharedVitals.participates(player)` = ni creativo ni espectador. Los que no participan no transmiten cambios ni los reciben. Igual comparten inventario y el objeto `FoodData`, pero no lo tickean. Al volver a supervivencia no están en `syncedHealth`, así que adoptan la vida compartida.

La absorción (corazones amarillos) **no** se comparte: es otro campo (`absorptionAmount`).

## Ver también

- [Inventario compartido](inventario-compartido.md): el mismo truco de referencia que usa el hambre.
- [Carga de datos del jugador](carga-de-datos-del-jugador.md): `FoodData.readAdditionalSaveData` también se bloquea.
- [Persistencia](persistencia.md): `health` y `food` en el `.dat`.
- [Muerte y respawn](muerte-y-respawn.md)
- [Tests: jugadores de prueba](../tests/jugadores-de-prueba.md): por qué el test no usa `makeMockServerPlayerInLevel`.
