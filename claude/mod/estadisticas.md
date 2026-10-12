# Estadísticas del grupo

[← Índice Mod](INDICE.md)

**Las estadísticas no tienen eventos propios: se calculan con los mismos deltas que reparten la vida y la experiencia.** Si buscás un listener de daño, no hay. `SharedVitals.syncHealth` y `SharedExperience.sync` ya saben cuánto cambió cada jugador en el tick, y le pasan eso a `GroupStats`. Si cambiás cómo se reparte la vida, cambiás también qué cuenta como daño.

## Qué se cuenta y dónde

| Estadística | Dónde se suma | Regla |
|---|---|---|
| Corazones perdidos | `SharedVitals.syncHealth` | Los deltas **negativos** de cada jugador sincronizado. Curarse no descuenta. Al jugador que adopta la vida compartida no se le cuenta nada: no está en `syncedHealth` |
| Culpa | `SharedVitals.recordGroupDeath`, desde `killGroup` | Una por muerte del grupo, para la **víctima** que ya elige `syncHealth` (ver [muerte y respawn](muerte-y-respawn.md#dónde-caen-los-ítems-y-la-experiencia)) |
| Susto | `SharedVitals.syncHealth` | La vida compartida estaba **por encima** de `corazonesDeSusto × 2` y quedó en o por debajo, sin llegar a 0. Se le cuenta a la misma víctima, sólo si su delta fue negativo |
| Experiencia juntada | `SharedExperience.sync` | Los deltas **positivos** en puntos. Encantar o el yunque restan y no cuentan. Lo que aporta un recién llegado (archivo de antes del mod) tampoco: no es delta |
| Mobs matados | `ServerLivingEntityEvents.AFTER_DEATH` en `SoulLink` | `entity instanceof Enemy` y `source.getEntity()` es un `ServerPlayer` que participa. Con flechas, `getEntity()` es el que disparó |

**El daño se suma antes de `killGroup`.** El golpe que mata al grupo cuenta como daño del que lo recibió, además de la culpa. Si moviste el `forEach(stats::addDamage)` debajo del `return` de la muerte, el golpe final se pierde.

**Con `compartirExperiencia: false` no se cuenta experiencia.** `SharedExperience.sync` corta antes de calcular deltas. Ver [configuración](configuracion.md).

## El intento

| Campo | Qué es |
|---|---|
| `attempt` | El intento en curso, desde 1. Las muertes del grupo son `attempt - 1` |
| `attemptTicks` | Ticks jugados en este intento. `GroupStats.tick` lo suma **sólo si hay algún participante vivo conectado** |
| `bestTicks` | El intento más largo ya terminado. `bestTicks()` devuelve el máximo entre éste y el actual |
| `deaths` | Las últimas 10 muertes: intento, duración, nombre de la víctima y causa |

**El reloj no es el tiempo del mundo.** Un servidor dedicado sigue haciendo ticks sin nadie conectado, y contar eso inflaría el récord. Tampoco corre con todos en la pantalla de muerte.

**`GroupStats.tick` va después de `syncHealth` en `END_SERVER_TICK`.** En el tick en que muere el grupo, `recordGroupDeath` pone `attemptTicks = 0` y, como ya están todos muertos, el tick no lo vuelve a 1. El test verifica el 0.

**La causa se guarda como `Component`, no como texto.** `DamageSource.getLocalizedDeathMessage` devuelve un traducible: guardado con `ComponentSerialization.CODEC`, cada cliente lo ve en su idioma. Con `getString()` el servidor lo resolvería en inglés (el servidor sólo tiene `en_us`).

## Persistencia

Se guarda en `data/soullink/stats.dat`, **aparte de `shared_inventory.dat`**: reiniciar las estadísticas o un archivo de estadísticas corrupto no pueden tocar el inventario. Es otro `SavedData` con su propio `SavedDataType` (`SoulLink.id("stats")`) y `DataFixTypes` en null (ver [persistencia](persistencia.md)).

**Los jugadores van por UUID, con el último nombre conocido.** Funciona igual con 2 que con 10, y el que se desconecta conserva su fila. El nombre se actualiza cada vez que suma algo.

`setDirty()` se llama en cada cambio (incluido cada tick del reloj), a diferencia de `SharedInventoryState`, que devuelve siempre `true`: acá todo cambio pasa por un método propio.

## Scoreboard

`StatsScoreboard` copia cada estadística a un objetivo `dummy` del scoreboard de vanilla: `soullink_culpas`, `soullink_corazones`, `soullink_sustos`, `soullink_experiencia`, `soullink_mobs`. Así se ven en la barra lateral **sin mod en el cliente**.

**La fuente de verdad es `GroupStats`, no el scoreboard.** En `SERVER_STARTED`, `setUp` crea los objetivos que falten y reescribe todos los puntajes. Si alguien borra un objetivo con `/scoreboard`, vuelve al reiniciar. Cada vez que un jugador suma algo se reescriben sus cinco puntajes con `ScoreHolder.forNameOnly(nombre)`.

**La barra lateral no se pisa si la ocupa otro.** `applySidebar` sólo la cambia si está vacía o muestra uno de los nuestros. Un datapack o minijuego que ya la usa queda como está y se deja un `info` en el log. Con `barraLateral: ninguna` se saca la nuestra, pero no la de otro.

Los corazones van redondeados a entero en el scoreboard (sólo enteros) y con medio corazón de precisión en `/soullink`.

## Comando

`SoulLinkCommand`: `/soullink` y `/soullink stats` para cualquiera; `/soullink stats reiniciar` y `/soullink recargar` con `LEVEL_GAMEMASTERS`. Reiniciar borra y recrea los objetivos, que es la forma de vaciar todos los puntajes de una vez, y después vuelve a aplicar la barra lateral.

## Ver también

- [Configuración](configuracion.md): `barraLateral`, `corazonesDeSusto`, `anunciarMuertes`.
- [Vida y hambre](vida-y-hambre.md): de dónde salen los deltas de daño.
- [Experiencia](experiencia.md): de dónde salen los deltas de experiencia.
- [Muerte y respawn](muerte-y-respawn.md): cómo se elige la víctima.
- [Persistencia](persistencia.md)
- [Tests: inventario compartido](../tests/inventario-compartido.md)
