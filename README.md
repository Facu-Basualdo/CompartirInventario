# Inventario Compartido

Mod de Fabric para **Minecraft Java 26.3** en el que todos los jugadores del servidor comparten, en tiempo real:

- **un solo inventario**: inventario, barra rápida, armadura y mano secundaria;
- **una sola barra de hambre**;
- **una sola barra de vida**.

Lo que agarra uno le aparece al otro, si a uno le pegan les baja la vida a los dos, y si uno come se llenan los dos. Si uno muere, mueren todos.

## Cómo funciona en el juego

| | Compartido | Por jugador |
|---|---|---|
| Inventario y barra rápida | ✅ | Slot seleccionado en la barra |
| Armadura y mano secundaria | ✅ | |
| Hambre y saturación | ✅ | |
| Vida | ✅ | Corazones de absorción (amarillos) |
| | | Ítem en el cursor, grilla de crafteo 2×2, ender chest, experiencia |

- **Lo comparten todos los jugadores conectados**, no por parejas. Con dos jugadores es "un inventario para los dos"; un tercero también entra.
- **Creativo y espectador** comparten el inventario, pero no la vida ni el hambre.
- **Muerte:** cuando muere uno, los demás mueren en el mismo instante. Los ítems caen una sola vez, al lado del primero, y el hambre vuelve a llenarse. Con `/gamerule keepInventory true` no se pierden los ítems, pero igual mueren todos.
- **Todo se guarda en el mundo** (`world/data/inventariocompartido/shared_inventory.dat`), no en cada jugador. Si alguien se desconecta con poca vida, el que entre después arranca con esa vida.
- **Mundo existente:** al instalar el mod en un mundo que ya tiene partida, el primer jugador que entra aporta sus ítems y su hambre al inventario compartido. **Los ítems de los demás no se pierden:** la primera vez que entra cada uno, le aparecen en un cofre a su lado y un mensaje en el chat le dice dónde está. Si no hay lugar para el cofre, quedan tirados a sus pies.

## Requisitos

| | Versión |
|---|---|
| Minecraft Java | 26.3 |
| [Fabric Loader](https://fabricmc.net/use/) | 0.19.5 o más nuevo |
| [Fabric API](https://modrinth.com/mod/fabric-api) | para 26.3 |
| Java | 25 |

## Instalación

Toda la lógica corre en el servidor.

- **Servidor dedicado:** copiá `inventariocompartido-<versión>.jar` y Fabric API a la carpeta `mods/` del servidor. El mod no agrega bloques, ítems ni paquetes propios, así que en principio los clientes no lo necesitan, pero no se probó con clientes vanilla.
- **Singleplayer o LAN:** instalalo en el cliente del que abre el mundo, junto con Fabric API.

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

Las pruebas crean dos jugadores y verifican que se compartan el inventario, la armadura, el hambre y la vida. También verifican que el respawn mantenga todo compartido y que, si muere uno, muera el otro.

Para probar a mano con dos clientes:

1. `./gradlew runServer` una vez. Después, en `run/`, poné `eula=true` en `eula.txt` (aceptando la [EULA de Minecraft](https://www.minecraft.net/eula)) y `online-mode=false` en `server.properties`.
2. `./gradlew runServer`
3. `./gradlew runClient` y `./gradlew runClient2` (este último entra como "Jugador2").
4. En los dos: *Multijugador → Conexión directa → `localhost`*.

## Estructura

```
src/main/       el mod: mixins sobre Inventory, EntityEquipment, Player y FoodData, más el estado compartido
src/gametest/   pruebas automáticas (Fabric GameTest)
claude/         documentación técnica para agentes de IA (invariantes, decisiones y bugs ya resueltos)
```

Para entender por qué el código está hecho así, empezá por [CLAUDE.md](CLAUDE.md) y [claude/mod/INDICE.md](claude/mod/INDICE.md).

## Licencia

Todavía sin definir: hoy `fabric.mod.json` declara `All-Rights-Reserved`.
