# Configuración

[← Índice Mod](INDICE.md)

**Sólo se puede configurar lo que se puede cambiar en caliente.** Puede parecer que falta un `compartirInventario`, pero no es un olvido. El inventario y el hambre se comparten **por referencia** desde el constructor del jugador (ver [inventario compartido](inventario-compartido.md)), y la vida es lo que hace que mueran todos.

## Por qué no se puede apagar el inventario

| Si se apagara… | Qué pasaría |
|---|---|
| Inventario, con jugadores que ya jugaron con el mod | Cada `playerdata/<uuid>.dat` tiene una **copia** del inventario compartido (vanilla lo guarda leyendo de la lista compartida). Al cargarlos sin bloquear, cada uno recibe la copia entera: los ítems se duplican |
| Inventario, y después se vuelve a prender | Los archivos tienen la marca `InventarioCompartido`, así que los ítems personales no se rescatan en un cofre: se pierden (ver [carga de datos del jugador](carga-de-datos-del-jugador.md)) |
| Vida, con el hambre todavía compartida | `FoodDataMixin` corre `FoodData.tick()` una vez por tick para un solo jugador: la regeneración curaría sólo al primero de la lista |

Para ofrecerlo habría que dejar de escribir la marca mientras está apagado y repartir el inventario compartido sin duplicarlo. Hoy no está hecho.

## El archivo

`config/soullink.json` (`SoulLink.configFile()`, en `FabricLoader.getConfigDir()`). Se lee en `onInitialize`, antes de que arranque el servidor, y con `/soullink recargar`.

| Clave | Tipo | Default | Lo usa |
|---|---|---|---|
| `compartirExperiencia` | bool | `true` | `SharedExperience.sync` y `shouldDropExperience` |
| `anunciarMuertes` | bool | `true` | `SharedVitals.recordGroupDeath` |
| `barraLateral` | `culpas` · `corazones` · `sustos` · `experiencia` · `mobs` · `ninguna` | `ninguna` | `StatsScoreboard.applySidebar` |
| `corazonesDeSusto` | número de 0 a 10 | `2` | `SharedVitals.syncHealth` |
| `idioma` | `en_us` · `es_ar` | `en_us` | `Lang`: la consola, los títulos del scoreboard y los clientes con un idioma que no tenemos (ver [idioma](idioma.md)) |

**`barraLateral` arranca en `ninguna` desde la 3.2.0** (antes `culpas`): la barra a la vista todo el tiempo molestaba a quien no la buscaba. Cambiar `DEFAULT` no toca un archivo que ya existe, que sigue con lo que tenga escrito; sólo los archivos nuevos arrancan sin barra.

Las claves están en español y en camelCase, como en Hero's Forge, Bloodmoon y Mythic Loot. Los ids de `barraLateral` son los de `GroupStats.Stat`, y el nombre del objetivo del scoreboard sale de ahí mismo (`soullink_<id>`).

## Un error no tira abajo todo el archivo

Mismo patrón que `ArchivoDeConfiguracion` de los otros mods:

- **Un campo inválido** usa su valor por defecto y deja un aviso. Los demás se respetan.
- **JSON roto**: `Reading.config()` es null. Al arrancar se usan los defaults; con `/soullink recargar` **se sigue con la config anterior**.
- **Falta una clave** (por ejemplo, después de actualizar el mod): se reescribe el archivo con lo leído más la clave nueva, para que el dueño vea que existe. **Con avisos no se reescribe**: se perdería el valor mal escrito en vez de dejárselo para que lo corrija.
- `corazonesDeSusto` entero se escribe como `2`, no `2.0` (Gson escribe los `double` con decimal).

Los avisos son `SoulLinkConfig.Warning` (clave y argumentos), no texto: se traducen al mostrarlos. Van al log con `[config]` en el idioma de la config recién leída y, con `/soullink recargar`, también al chat del que lo corrió, en su idioma.

## Apagar la experiencia compartida

Con `compartirExperiencia: false`:

- `SharedExperience.sync` vacía `syncedExperience` y vuelve sin tocar a nadie.
- `shouldDropExperience` devuelve `true` para todos: cada uno tira sus orbes al morir, como en vanilla.
- No se cuenta "experiencia juntada" en las estadísticas.

**Al volver a prenderla, todos adoptan la compartida guardada y pierden la propia.** Con `syncedExperience` vacío todos son recién llegados, y como `experience` ya no es `UNKNOWN_EXPERIENCE`, ninguno aporta la suya (ver [experiencia](experiencia.md#quién-aporta-y-quién-adopta)). Está avisado en el README.

## Ver también

- [Estadísticas](estadisticas.md): qué cuentan los objetivos de la barra lateral.
- [Experiencia](experiencia.md)
- [Inventario compartido](inventario-compartido.md): por qué el reparto por referencia no se puede apagar en caliente.
- [Tests: configuración](../tests/configuracion.md)
- [Idioma](idioma.md): cómo se elige el idioma de cada mensaje.
- [Pantalla de configuración](pantalla-de-configuracion.md): editar este archivo desde Mod Menu.
