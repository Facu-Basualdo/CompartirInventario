# Experiencia compartida

[← Índice Mod](INDICE.md)

Síntomas que esto evita: **un jugador encanta y al otro no le baja el nivel**, **el grupo muere y cada uno tira su propia copia de los orbes** (la experiencia se multiplica por la cantidad de jugadores), o **un jugador con partida de antes del mod entra y pierde sus niveles**.

## Se reparte al final del tick, como la vida

La experiencia son tres campos primitivos públicos de `Player`: `experienceLevel`, `experienceProgress` (float de 0 a 1) y `totalExperience` (el puntaje de la pantalla de muerte). No hay un objeto para compartir por referencia como el `FoodData`. **`SharedExperience.sync` corre en `END_SERVER_TICK`, registrado después de `SharedVitals.syncHealth`**, y hace lo mismo que la vida (ver [vida y hambre](vida-y-hambre.md)):

1. A cada jugador que ya estaba sincronizado le calcula cuánto cambió desde el último reparto y lo suma a lo compartido.
2. Convierte el total a nivel + progreso y se lo pone a cada participante vivo.
3. Guarda en `syncedExperience` (un `IdentityHashMap` por objeto `ServerPlayer`) lo que le quedó a cada uno.

**Se suma en puntos, no en niveles.** Un nivel 30 vale muchos más puntos que un nivel 2: sumar niveles haría que juntar orbes con nivel bajo valga lo mismo que con nivel alto. `pointsBelow(level)` usa las fórmulas cerradas de vanilla (L²+6L hasta 16, 2,5L²−40,5L+360 hasta 31, 4,5L²−162,5L+2220 después) y `pointsNeeded(level)` replica `Player.getXpNeededForNextLevel()` en `long`, porque `/xp` acepta niveles que desbordan el `int`. La conversión inversa (`levelFor`) es una búsqueda binaria sobre `pointsBelow`.

**El total compartido se guarda después de redondear el progreso a float.** Si se guardara el `double` crudo, el próximo tick leería del jugador un valor apenas distinto y sumaría una diferencia fantasma. Por lo mismo, el progreso se limita a `Math.nextDown(1.0F)`: nunca llega a 1.

Cualquier camino de vanilla que toque esos campos queda cubierto sin interceptarlo: orbes (`giveExperiencePoints`), encantar (`onEnchantmentPerformed`), yunque (`giveExperienceLevels(-costo)`), `/xp add` y `/xp set`.

**Hasta un tick (50 ms) de demora**, igual que la vida.

## Hay que forzar el reenvío de la barra

**`ServerPlayer.doTick` sólo manda `ClientboundSetExperiencePacket` cuando `totalExperience != lastSentExp`.** Encantar baja el nivel sin tocar `totalExperience`, así que si `SharedExperience.apply` sólo cambiara los campos, el cliente del otro jugador seguiría viendo el nivel viejo. `apply` pone `lastSentExp = -1` con `ServerPlayerAccessor`.

No se usa `setExperienceLevels()` para eso: tiene un `if (nivel == experienceLevel) return;` al principio y no marca nada si sólo cambió el progreso.

## Quién aporta y quién adopta

| Situación | Qué pasa |
|---|---|
| Entra o reaparece un jugador (no está en `syncedExperience`) | **Adopta** la experiencia compartida. Lo que tenía, o lo que juntó en ese mismo tick, se pierde |
| Mod recién instalado (`experience` = `UNKNOWN_EXPERIENCE`, -1) | El primero que entra aporta la suya |
| Entra con un archivo **de antes del mod** (sin la marca `SoulLink`) | **Suma** la suya a la compartida, una sola vez |
| Muere el grupo, sin `keepInventory` | La compartida vuelve a 0 (nivel y puntaje), como un jugador recién creado |
| Muere el grupo, con `keepInventory` | Se conserva |
| Creativo o espectador | No participa (mismo `SharedVitals.participates`). Al volver a supervivencia adopta la compartida |

El aporte de los archivos de antes del mod va por el mismo camino que el cofre de ítems (ver [carga de datos del jugador](carga-de-datos-del-jugador.md#cofre-con-los-ítems-de-antes-del-mod)): `PlayerMixin.readAdditionalSaveData` anota el UUID del perfil con `addExperienceContribution` y vanilla carga la experiencia del archivo en el jugador, sin bloqueo. En el primer `sync`, `takeExperienceContribution` dice que ese recién llegado suma en vez de adoptar. El conjunto vive en memoria; si el jugador no llega a sincronizarse, su archivo sigue sin la marca y el próximo login lo vuelve a anotar.

## Al morir se tira una sola vez

**`PlayerMixin` intercepta `Player.getBaseExperienceReward(ServerLevel)` y devuelve 0 a todos los que mueren en el mismo tick salvo al primero.** Vanilla tira `min(nivel × 7, 100)` puntos por jugador, y como todos tienen el nivel compartido, el grupo tiraría esa experiencia una vez por jugador.

`SharedInventoryState.claimDeathDrop(jugador, tick)` recuerda quién reclamó en qué tick (`server.getTickCount()`). Que se compare por jugador y no sólo por tick es lo que permite que el mismo jugador lo llame dos veces: con un catalizador de sculk cerca, `SculkCatalystBlockEntity` llama a `getExperienceReward` en la muerte y después `dropExperience` puede volver a llamarlo. No hay otros llamadores en vanilla (buscado en el jar).

El primero que muere es el que recibió el golpe (ver [muerte y respawn](muerte-y-respawn.md#dónde-caen-los-ítems-y-la-experiencia)), así que los orbes caen ahí, junto con los ítems.

## Ver también

- [Vida y hambre](vida-y-hambre.md): el mismo reparto por deltas, y quién participa.
- [Muerte y respawn](muerte-y-respawn.md): el orden en que muere el grupo.
- [Persistencia](persistencia.md): `experience` y `experienceScore` en el `.dat`.
- [Carga de datos del jugador](carga-de-datos-del-jugador.md): la marca que distingue un archivo de antes del mod.
- [Tests: inventario compartido](../tests/inventario-compartido.md)
- [Estadísticas](estadisticas.md): los deltas positivos son la experiencia juntada de cada uno.
- [Configuración](configuracion.md): `compartirExperiencia: false` corta el reparto.
