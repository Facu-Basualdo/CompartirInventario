# Soul Link — mod de Minecraft

Mod de Fabric para **Minecraft Java 26.3** que hace que todos los jugadores del servidor compartan en tiempo real un único inventario (inventario, barra rápida, armadura y mano secundaria), una sola barra de hambre, una sola barra de vida y una sola barra de experiencia. Java 25, Gradle 9.7 + Fabric Loom 1.18, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3. Toda la lógica corre en el servidor.

## Cómo obtener contexto

1. Ubicá el **área** en la tabla de abajo.
2. Abrí su `INDICE.md` y leé las descripciones: muchas veces la respuesta ya está ahí.
3. Abrí **sólo** la nota que corresponde. No leas un área entera.

Si no sabés en qué nota está algo, buscá por contenido antes de abrir archivos:

```bash
grep -ril "<palabra clave>" claude/
```

El mismo tema en distintas áreas usa el mismo nombre de archivo (`claude/mod/inventario-compartido.md` ↔ `claude/tests/inventario-compartido.md`), así que el grep devuelve el clúster completo.

## Áreas

| Área | Carpeta | Stack | Índice |
|---|---|---|---|
| Mod | `src/main/` | Java 25 · Fabric API · Mixins (SpongePowered) · nombres oficiales de Mojang | [claude/mod/INDICE.md](claude/mod/INDICE.md) |
| Tests | `src/gametest/` | Fabric GameTest API · servidor sin ventana | [claude/tests/INDICE.md](claude/tests/INDICE.md) |

## Notas transversales

| Nota | De qué trata |
|---|---|
| [Entorno Java y Gradle](claude/general/entorno-java-gradle.md) | `~/.gradle/gradle.properties` fuerza JDK 17 (React Native) y gana sobre el proyecto · Java 25 se fija con `gradle/gradle-daemon-jvm.properties` + `org.gradle.java.installations.paths` · sin eso Loom falla con "requires JVM runtime version 25" |
| [Minecraft 26.x sin ofuscar](claude/general/minecraft-26-sin-ofuscar.md) | Desde 26.1 se usan los nombres de Mojang (Yarn está muerto: los tutoriales viejos tienen otros nombres) · antes de escribir un mixin, verificá firmas con `javap` sobre el jar de `.gradle/loom-cache` · `ResourceLocation` ahora es `Identifier` |
| [Probar con dos jugadores](claude/general/probar-con-dos-jugadores.md) | `runServer` necesita `eula=true` y `online-mode=false` en `run/` · `runClient2` entra como "Jugador2" desde `run-client2/` · los dos clientes comparten build: hay que reiniciarlos después de cambiar código |

## Features que cruzan áreas

| Feature | Mod | Tests |
|---|---|---|
| Inventario compartido (listas, armadura, sync) | [inventario-compartido](claude/mod/inventario-compartido.md) | [inventario-compartido](claude/tests/inventario-compartido.md) |
| Vida y hambre compartidas | [vida-y-hambre](claude/mod/vida-y-hambre.md) | [inventario-compartido](claude/tests/inventario-compartido.md) · [jugadores-de-prueba](claude/tests/jugadores-de-prueba.md) |
| Carga de datos del jugador sin pisar lo compartido, y cofre con los ítems de antes del mod | [carga-de-datos-del-jugador](claude/mod/carga-de-datos-del-jugador.md) | [inventario-compartido](claude/tests/inventario-compartido.md) · [jugadores-de-prueba](claude/tests/jugadores-de-prueba.md) |
| Experiencia compartida | [experiencia](claude/mod/experiencia.md) | [inventario-compartido](claude/tests/inventario-compartido.md) |
| Muerte grupal y respawn | [muerte-y-respawn](claude/mod/muerte-y-respawn.md) | [jugadores-de-prueba](claude/tests/jugadores-de-prueba.md) |
| Intercambiar lugar (`/intercambiar`) | [intercambiar-lugar](claude/mod/intercambiar-lugar.md) | [inventario-compartido](claude/tests/inventario-compartido.md) · [jugadores-de-prueba](claude/tests/jugadores-de-prueba.md) |

## Reglas de workflow

- **Después de cada feature corré `/docs`** para actualizar las notas según el diff, y `node scripts/verificar-docs.mjs` tiene que dar OK.
- **Si la feature se nota en el juego, actualizá el `README.md`** (la lista de qué se comparte, la tabla "Cómo funciona en el juego" y lo que verifican las pruebas): es lo que lee quien instala el mod, y `/docs` sólo toca `claude/`.
- **Antes de dar algo por terminado corré `gradlew runGameTest`**: compila no alcanza, los mixins recién se aplican en runtime.
- **Con cada cambio que se pueda notar en el juego, subí `version` en `gradle.properties`** (sale en el nombre del jar y en `fabric.mod.json`): minor (`1.1.0` → `1.2.0`) para una feature nueva, patch (`1.1.0` → `1.1.1`) para un arreglo. Si es otra versión, el jar viejo y el nuevo no se confunden en la carpeta `mods/`.
- **No commitees ni pushees salvo que te lo pidan.**
- Mensajes, comentarios y docs en **español rioplatense** (vos), como el resto del proyecto.

## Convenciones de código

- Paquete `com.inventariocompartido`; mixins en `com.inventariocompartido.mixin`, registrados en `src/main/resources/inventariocompartido.mixins.json`.
- **Métodos y accessors agregados por mixins llevan prefijo `inventariocompartido$`** para no chocar con otros mods.
- Indentación con tabs, imports agrupados: java/terceros → `net.fabricmc` → `net.minecraft`.
- Comentarios sólo para el porqué (qué rompería vanilla si no estuviera), no para parafrasear.

## Qué NO va en el grafo

Planes, investigaciones, auditorías y borradores van en `docs/` y se leen a demanda; `claude/` es sólo contexto permanente. Hoy no hay nada en `docs/`.
