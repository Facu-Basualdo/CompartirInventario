# Soul Link

<p align="center"><img src="src/main/resources/assets/soullink/icon.png" alt="Soul Link" width="256"></p>

Mod de Fabric para **Minecraft Java 26.3** en el que los jugadores vinculados comparten, en tiempo real:

- **un solo inventario**: inventario, barra rápida, armadura y mano secundaria (y los slots de Trinkets, si está instalado);
- **una sola barra de hambre**;
- **una sola barra de vida**;
- **una sola barra de experiencia**.

Lo que agarra uno le aparece al otro, si a uno le pegan les baja la vida a todos, si uno come se llenan todos y si uno encanta les bajan los niveles a todos. Si uno muere, mueren todos.

Por defecto están vinculados todos los del servidor. Con **`/soullink grupo`** podés armar grupos separados: 2 y 2, 3 y 1, o como quieras.

Los mensajes salen en el idioma de cada jugador (español o inglés), y los comandos existen en los dos. Además, con **`/intercambiar`** (o `/swap`) cambiás de lugar con tu compañero, y **`/soullink`** lleva la cuenta de cuántas veces murieron y de quién fue la culpa.

## Cómo funciona

| | Compartido | Por jugador |
|---|---|---|
| Inventario y barra rápida | ✅ | Slot seleccionado en la barra |
| Armadura y mano secundaria | ✅ | |
| Hambre y saturación | ✅ | |
| Vida | ✅ | Corazones de absorción (amarillos) |
| Experiencia (niveles y puntaje) | ✅ (se puede apagar) | |
| | | Ítem en el cursor, grilla de crafteo 2×2, ender chest |

- **Sin grupos armados, lo comparten todos los jugadores conectados.** Con grupos, cada uno comparte sólo con los de su grupo (ver [Grupos](#grupos)).
- **Creativo y espectador** comparten el inventario, pero no la vida, el hambre ni la experiencia.
- **Experiencia:** se suma en puntos, no en niveles, así que juntar orbes rinde lo mismo que si los juntara uno solo. Encantar, el yunque y `/xp` afectan a todos.
- **Muerte:** cuando muere uno, mueren todos en el mismo instante. Los ítems y los orbes caen una sola vez, al lado del que recibió el golpe. Con `/gamerule keepInventory true` no se pierde nada, pero igual mueren todos.
- **`/intercambiar [jugador]`:** te pone donde está tu compañero y a él donde estás vos, aunque estén en otra dimensión. Con más de un jugador conectado, nombralo. No hace falta ser operador.
- **Todo se guarda en el mundo**, no en cada jugador: si alguien se desconecta con poca vida, el que entre después arranca con esa vida.
- **En un mundo que ya tiene partida:** el primer jugador que entra aporta sus ítems y su hambre. A los demás no se les pierde nada: la primera vez que entran, sus ítems aparecen en un cofre a su lado, y su experiencia se suma a la compartida.

## Idiomas

Cada jugador ve los mensajes en el idioma que tiene puesto en su juego: español o inglés (cualquier variante, como `es_mx` o `en_gb`). Con otro idioma, o en la consola, se usa el de la opción `idioma` de la config. Los comandos existen en los dos:

| Español | Inglés |
|---|---|
| `/intercambiar` | `/swap` |
| `/soullink grupo`, `grupos`, `quitar` | `/soullink group`, `groups`, `remove` |
| `/soullink recargar`, `stats reiniciar` | `/soullink reload`, `stats reset` |

## Grupos

Por defecto, todos los jugadores están vinculados entre sí. Para separarlos (comandos para operadores):

```
/soullink grupo rojo Ana Beto      Ana y Beto quedan vinculados entre ellos
/soullink grupo azul Caro Dani     Caro y Dani, entre ellos
/soullink grupo quitar Beto        Beto vuelve con los que no tienen grupo
/soullink grupos                   muestra quién está en cada grupo (cualquiera lo puede usar)
```

- **Cada grupo es independiente:** tiene su inventario, su hambre, su vida, su experiencia y sus intentos. Si muere un grupo, los demás siguen.
- **Los que no tienen grupo** están vinculados entre ellos, como si no hubiera grupos.
- **Un grupo de uno** juega solo, sin compartir nada.
- **Al cambiar de grupo no te llevás nada:** pasás a tener las cosas del grupo nuevo, y las tuyas se quedan con tu grupo anterior. Un grupo nuevo arranca vacío, con la vida de quien entra y sin experiencia.
- Se pueden nombrar hasta 8 jugadores seguidos, o usar un selector como `@a[team=rojo]`. Los jugadores tienen que estar conectados.
- El nombre del grupo va en minúsculas, sin tildes ni espacios (hasta 16 letras, números, `-` o `_`).
- **`/intercambiar`** sin nombre te cambia con tu compañero de grupo.
- Los grupos se guardan en el mundo: al reconectar seguís en el tuyo.

## Estadísticas

Cada vez que muere el grupo termina un **intento**. El mod lleva la cuenta de los intentos y de lo que hizo cada jugador, sin importar cuántos sean:

| Estadística | Qué cuenta |
|---|---|
| Culpas | Muertes del grupo en las que ese jugador recibió el golpe final |
| Corazones perdidos | Vida que le costó al grupo el daño que recibió ese jugador |
| Sustos | Golpes que dejaron al grupo con 2 corazones o menos, sin matarlo |
| Experiencia juntada | Puntos de experiencia que aportó (gastar niveles no resta) |
| Mobs matados | Mobs hostiles que mató |

- **`/soullink`** muestra el intento actual de tu grupo, cuánto lleva, el récord, una línea por jugador y las últimas muertes con su causa. **`/soullink stats <grupo>`** muestra el de otro grupo. Cualquiera puede usarlos.
- **Barra lateral:** por defecto no muestra nada. Para ver las culpas (u otra estadística) a la derecha de la pantalla, poné `barraLateral` en la [configuración](#configuración). Son objetivos comunes del scoreboard (`soullink_culpas`, `soullink_corazones`, `soullink_sustos`, `soullink_experiencia`, `soullink_mobs`), así que no hace falta nada en el cliente. Si la barra ya muestra otro objetivo (de un datapack, por ejemplo), el mod no la pisa.
- **Al morir un grupo** sale en el chat qué grupo era, qué intento terminó, cuánto duró, de quién fue la culpa y el récord.
- El reloj del intento sólo corre mientras hay alguien jugando.
- **`/soullink stats reiniciar`** (operadores) pone todo en cero.

## Configuración

`config/soullink.json` se crea solo la primera vez que arranca el servidor:

```json
{
  "compartirExperiencia": true,
  "anunciarMuertes": true,
  "barraLateral": "ninguna",
  "corazonesDeSusto": 2,
  "idioma": "en_us"
}
```

| Opción | Qué hace |
|---|---|
| `compartirExperiencia` | Con `false`, cada uno tiene su propia experiencia. Al volver a `true`, todos adoptan la compartida que había quedado guardada y pierden la que juntaron por separado |
| `anunciarMuertes` | El mensaje en el chat cuando muere el grupo |
| `barraLateral` | Qué estadística mostrar en la barra lateral: `culpas`, `corazones`, `sustos`, `experiencia`, `mobs` o `ninguna` (por defecto) |
| `corazonesDeSusto` | Desde cuántos corazones un golpe cuenta como susto (de 0 a 10; con 0 no se cuentan) |
| `idioma` | `en_us` o `es_ar`: el de la consola, los títulos de la barra lateral y los jugadores con un idioma que el mod no tiene. Cada jugador ve los mensajes en el idioma de su juego |

Después de editarlo, **`/soullink recargar`** (operadores) lo aplica sin reiniciar. Un valor mal escrito usa el valor por defecto y avisa en el chat y en el log, sin descartar el resto del archivo.

**Desde el juego, con [Mod Menu](https://modrinth.com/mod/modmenu):** en la lista de mods, el botón de configuración de Soul Link abre una pantalla con estas opciones. Al guardar se aplica enseguida, como con `/soullink recargar`. Si un valor no sirve, no se guarda nada y la pantalla dice por qué. Edita el archivo de tu instalación: sirve en singleplayer, en un mundo abierto en LAN desde tu juego y para preparar la config antes de crear un mundo. El de un servidor dedicado se sigue editando en el servidor.

El inventario, el hambre y la vida no se pueden apagar: se comparten desde que el jugador entra, y cambiarlo con gente adentro duplicaría o borraría ítems.

## Requisitos

| | Versión |
|---|---|
| Minecraft Java | 26.3 |
| [Fabric Loader](https://fabricmc.net/use/) | 0.19.5 o más nuevo |
| [Fabric API](https://modrinth.com/mod/fabric-api) | para 26.3 |
| Java | 25 |

## Instalación

- **Servidor dedicado:** copiá `soullink-<versión>.jar` y Fabric API a la carpeta `mods/` del servidor. Los jugadores no necesitan instalar nada.
- **Singleplayer o LAN:** instalalo en el cliente del que abre el mundo, junto con Fabric API.

### Actualizar a la 3.2.0

La barra lateral ahora arranca apagada, pero **sólo en un `config/soullink.json` nuevo**: si ya tenías uno, sigue con lo que diga (`culpas`, si nunca lo cambiaste). Para sacarla, poné `"barraLateral": "ninguna"` o elegí *Ninguna* desde Mod Menu.

### Actualizar a la 3.0.0

No hay que hacer nada: el mundo se lee igual y todos quedan en un solo grupo, como antes, hasta que armes grupos. Si usás Hero's Forge, actualizalo a la 0.14.0 o más nuevo.

### Actualizar desde una versión anterior a la 2.0.0

Hasta la 1.2.1 el mod se llamaba **Inventario Compartido** y su id era `inventariocompartido`. Borrá el jar viejo de `mods/` y poné el nuevo: **el inventario compartido se pasa solo** al id nuevo la primera vez que arranca el servidor. Por las dudas, hacé una copia del mundo antes.

## Funciona con

- **Hero's Forge** (0.14.0 o más nuevo): los puntos de estadísticas pasan a ser de cada grupo. Con una versión anterior de Hero's Forge, Soul Link 3.0.0 no arranca: actualizá los dos.
- **Bloodmoon & Bosses** y **Mythic Loot:** no se tocan.
- **[Trinkets Updated](https://modrinth.com/mod/trinkets-updated)** (opcional, desde la 3.3.0): sus slots (espalda, anillos, collar…) también se comparten dentro del grupo, como la armadura. La primera vez, el primero que entra aporta los suyos y a los demás se les devuelven en el cofre, como el inventario. Sin Trinkets no cambia nada.
- **[Mod Menu](https://modrinth.com/mod/modmenu)** (opcional, en el cliente): la configuración se edita desde el juego. Ver [Configuración](#configuración).

## Desarrollo

```bash
./gradlew build          # compila y corre las pruebas; el jar queda en build/libs/
./gradlew runGameTest    # sólo las pruebas automáticas, en un servidor sin ventana
./gradlew runClientGameTest   # la pantalla de configuración en un cliente de verdad (abre una ventana)
```

Pide Java 25: si Gradle no encuentra tu JDK, agregá `org.gradle.java.installations.paths=<ruta a tu JDK 25>` a tu `~/.gradle/gradle.properties`. La documentación técnica está en [CLAUDE.md](CLAUDE.md).

## Licencia

[MIT](LICENSE).
