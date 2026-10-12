# Soul Link

<p align="center"><img src="src/main/resources/assets/soullink/icon.png" alt="Soul Link" width="256"></p>

Mod de Fabric para **Minecraft Java 26.3** en el que todos los jugadores del servidor comparten, en tiempo real:

- **un solo inventario**: inventario, barra rápida, armadura y mano secundaria;
- **una sola barra de hambre**;
- **una sola barra de vida**;
- **una sola barra de experiencia**.

Lo que agarra uno le aparece al otro, si a uno le pegan les baja la vida a todos, si uno come se llenan todos y si uno encanta les bajan los niveles a todos. Si uno muere, mueren todos.

Además, con **`/intercambiar`** cambiás de lugar con tu compañero, y **`/soullink`** lleva la cuenta de cuántas veces murieron y de quién fue la culpa.

## Cómo funciona

| | Compartido | Por jugador |
|---|---|---|
| Inventario y barra rápida | ✅ | Slot seleccionado en la barra |
| Armadura y mano secundaria | ✅ | |
| Hambre y saturación | ✅ | |
| Vida | ✅ | Corazones de absorción (amarillos) |
| Experiencia (niveles y puntaje) | ✅ (se puede apagar) | |
| | | Ítem en el cursor, grilla de crafteo 2×2, ender chest |

- **Lo comparten todos los jugadores conectados**, no por parejas.
- **Creativo y espectador** comparten el inventario, pero no la vida, el hambre ni la experiencia.
- **Experiencia:** se suma en puntos, no en niveles, así que juntar orbes rinde lo mismo que si los juntara uno solo. Encantar, el yunque y `/xp` afectan a todos.
- **Muerte:** cuando muere uno, mueren todos en el mismo instante. Los ítems y los orbes caen una sola vez, al lado del que recibió el golpe. Con `/gamerule keepInventory true` no se pierde nada, pero igual mueren todos.
- **`/intercambiar [jugador]`:** te pone donde está tu compañero y a él donde estás vos, aunque estén en otra dimensión. Con más de un jugador conectado, nombralo. No hace falta ser operador.
- **Todo se guarda en el mundo**, no en cada jugador: si alguien se desconecta con poca vida, el que entre después arranca con esa vida.
- **En un mundo que ya tiene partida:** el primer jugador que entra aporta sus ítems y su hambre. A los demás no se les pierde nada: la primera vez que entran, sus ítems aparecen en un cofre a su lado, y su experiencia se suma a la compartida.

## Estadísticas

Cada vez que muere el grupo termina un **intento**. El mod lleva la cuenta de los intentos y de lo que hizo cada jugador, sin importar cuántos sean:

| Estadística | Qué cuenta |
|---|---|
| Culpas | Muertes del grupo en las que ese jugador recibió el golpe final |
| Corazones perdidos | Vida que le costó al grupo el daño que recibió ese jugador |
| Sustos | Golpes que dejaron al grupo con 2 corazones o menos, sin matarlo |
| Experiencia juntada | Puntos de experiencia que aportó (gastar niveles no resta) |
| Mobs matados | Mobs hostiles que mató |

- **`/soullink`** muestra el intento actual, cuánto lleva, el récord, una línea por jugador y las últimas muertes con su causa. Cualquiera puede usarlo.
- **Barra lateral:** por defecto muestra las culpas. Son objetivos comunes del scoreboard (`soullink_culpas`, `soullink_corazones`, `soullink_sustos`, `soullink_experiencia`, `soullink_mobs`), así que no hace falta nada en el cliente. Si la barra ya muestra otro objetivo (de un datapack, por ejemplo), el mod no la pisa.
- **Al morir el grupo** sale en el chat qué intento terminó, cuánto duró, de quién fue la culpa y el récord.
- El reloj del intento sólo corre mientras hay alguien jugando.
- **`/soullink stats reiniciar`** (operadores) pone todo en cero.

## Configuración

`config/soullink.json` se crea solo la primera vez que arranca el servidor:

```json
{
  "compartirExperiencia": true,
  "anunciarMuertes": true,
  "barraLateral": "culpas",
  "corazonesDeSusto": 2
}
```

| Opción | Qué hace |
|---|---|
| `compartirExperiencia` | Con `false`, cada uno tiene su propia experiencia. Al volver a `true`, todos adoptan la compartida que había quedado guardada y pierden la que juntaron por separado |
| `anunciarMuertes` | El mensaje en el chat cuando muere el grupo |
| `barraLateral` | Qué estadística mostrar: `culpas`, `corazones`, `sustos`, `experiencia`, `mobs` o `ninguna` |
| `corazonesDeSusto` | Desde cuántos corazones un golpe cuenta como susto (de 0 a 10; con 0 no se cuentan) |

Después de editarlo, **`/soullink recargar`** (operadores) lo aplica sin reiniciar. Un valor mal escrito usa el valor por defecto y avisa en el chat y en el log, sin descartar el resto del archivo.

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

### Actualizar desde una versión anterior a la 2.0.0

Hasta la 1.2.1 el mod se llamaba **Inventario Compartido** y su id era `inventariocompartido`. Borrá el jar viejo de `mods/` y poné el nuevo: **el inventario compartido se pasa solo** al id nuevo la primera vez que arranca el servidor. Por las dudas, hacé una copia del mundo antes.

## Funciona con

- **Hero's Forge:** los puntos de estadísticas pasan a ser de todo el grupo.
- **Bloodmoon & Bosses** y **Mythic Loot:** no se tocan.

## Desarrollo

```bash
./gradlew build          # compila y corre las pruebas; el jar queda en build/libs/
./gradlew runGameTest    # sólo las pruebas automáticas, en un servidor sin ventana
```

Pide Java 25: si Gradle no encuentra tu JDK, agregá `org.gradle.java.installations.paths=<ruta a tu JDK 25>` a tu `~/.gradle/gradle.properties`. La documentación técnica está en [CLAUDE.md](CLAUDE.md).

## Licencia

[MIT](LICENSE).
