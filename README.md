# Soul Link

<p align="center"><img src="src/main/resources/assets/inventariocompartido/icon.png" alt="Soul Link" width="256"></p>

*(Antes se llamaba "Inventario Compartido".)*

Mod de Fabric para **Minecraft Java 26.3** en el que todos los jugadores del servidor comparten, en tiempo real:

- **un solo inventario**: inventario, barra rápida, armadura y mano secundaria;
- **una sola barra de hambre**;
- **una sola barra de vida**;
- **una sola barra de experiencia**.

Lo que agarra uno le aparece al otro, si a uno le pegan les baja la vida a los dos, si uno come se llenan los dos y si uno encanta les bajan los niveles a los dos. Si uno muere, mueren todos.

Además, con **`/intercambiar`** cambiás de lugar con tu compañero.

## Cómo funciona en el juego

| | Compartido | Por jugador |
|---|---|---|
| Inventario y barra rápida | ✅ | Slot seleccionado en la barra |
| Armadura y mano secundaria | ✅ | |
| Hambre y saturación | ✅ | |
| Vida | ✅ | Corazones de absorción (amarillos) |
| Experiencia (niveles y puntaje) | ✅ | |
| | | Ítem en el cursor, grilla de crafteo 2×2, ender chest |

- **Lo comparten todos los jugadores conectados**, no por parejas. Con dos jugadores es "un inventario para los dos"; un tercero también entra.
- **Creativo y espectador** comparten el inventario, pero no la vida, el hambre ni la experiencia.
- **Experiencia:** se suma en puntos, no en niveles, así que juntar orbes rinde lo mismo que si los juntara un solo jugador. Encantar, el yunque y `/xp` también afectan a todos.
- **Muerte:** cuando muere uno, los demás mueren en el mismo instante. Los ítems y los orbes de experiencia caen una sola vez, al lado del que recibió el golpe; el hambre vuelve a llenarse y la experiencia vuelve a 0. Con `/gamerule keepInventory true` no se pierden ni los ítems ni la experiencia, pero igual mueren todos.
- **Intercambiar lugar:** `/intercambiar` te pone donde está tu compañero y a tu compañero donde estás vos, aunque estén en otra dimensión, y cada uno queda mirando para donde miraba el otro. Si hay más de un jugador conectado, nombralo: `/intercambiar <jugador>`. No hace falta ser operador. No funciona si alguno de los dos está muerto.
- **Todo se guarda en el mundo** (`world/data/inventariocompartido/shared_inventory.dat`), no en cada jugador. Si alguien se desconecta con poca vida, el que entre después arranca con esa vida.
- **Mundo existente:** al instalar el mod en un mundo que ya tiene partida, el primer jugador que entra aporta sus ítems y su hambre al inventario compartido. La experiencia de cada uno se suma a la compartida la primera vez que entra. **Los ítems de los demás no se pierden:** la primera vez que entra cada uno, le aparecen en un cofre a su lado y un mensaje en el chat le dice dónde está. Si no hay lugar para el cofre, quedan tirados a sus pies.

## Requisitos

| | Versión |
|---|---|
| Minecraft Java | 26.3 |
| [Fabric Loader](https://fabricmc.net/use/) | 0.19.5 o más nuevo |
| [Fabric API](https://modrinth.com/mod/fabric-api) | para 26.3 |
| Java | 25 |

## Instalación

Toda la lógica corre en el servidor.

- **Servidor dedicado:** copiá `soullink-<versión>.jar` y Fabric API a la carpeta `mods/` del servidor. El mod no agrega bloques, ítems ni paquetes propios, así que en principio los clientes no lo necesitan, pero no se probó con clientes vanilla.
- **Singleplayer o LAN:** instalalo en el cliente del que abre el mundo, junto con Fabric API.
- **Si venís de una versión anterior:** borrá el `inventariocompartido-<versión>.jar` viejo de `mods/`. Si quedan los dos, Fabric no arranca porque tienen el mismo mod id. Los mundos siguen funcionando igual.

## Compilar

```bash
./gradlew build          # Windows: gradlew build
```

El jar queda en `build/libs/`.

El proyecto pide Java 25 (`gradle/gradle-daemon-jvm.properties`). Si Gradle no encuentra tu JDK 25, ajustá `org.gradle.java.installations.paths` en `gradle.properties` con la ruta donde lo tengas instalado.

## Probar

```bash
./gradlew runGameTest    # pruebas automáticas en un servidor sin ventana
```

Las pruebas crean dos jugadores y verifican que se compartan el inventario, la armadura, el hambre, la vida y la experiencia. También verifican que el respawn mantenga todo compartido, que `/intercambiar` los cambie de lugar aunque estén en distintas dimensiones y que, si muere uno, muera el otro.

Para probar a mano con dos clientes:

1. `./gradlew runServer` una vez. Después, en `run/`, poné `eula=true` en `eula.txt` (aceptando la [EULA de Minecraft](https://www.minecraft.net/eula)) y `online-mode=false` en `server.properties`.
2. `./gradlew runServer`
3. `./gradlew runClient` y `./gradlew runClient2` (este último entra como "Jugador2").
4. En los dos: *Multijugador → Conexión directa → `localhost`*.

## Estructura

```
src/main/       el mod: mixins sobre Inventory, EntityEquipment, Player y FoodData, el estado compartido (vida y experiencia se reparten al final de cada tick) y el comando /intercambiar
src/gametest/   pruebas automáticas (Fabric GameTest)
claude/         documentación técnica para agentes de IA (invariantes, decisiones y bugs ya resueltos)
```

Para entender por qué el código está hecho así, empezá por [CLAUDE.md](CLAUDE.md) y [claude/mod/INDICE.md](claude/mod/INDICE.md).

## Licencia

[MIT](LICENSE): podés usarlo, modificarlo y redistribuirlo (también en modpacks) siempre que mantengas el aviso de copyright.
