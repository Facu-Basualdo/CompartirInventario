# Pantalla de configuración (Mod Menu)

[← Índice Mod](INDICE.md)

Síntomas que esta nota evita: guardar desde Mod Menu un valor fuera de rango y que el mod arranque la próxima vez con el valor por defecto sin que nadie lo note; un decimal que la pantalla escribe como `4` y que la próxima vez sólo acepta enteros; aplicar la config desde el hilo del cliente mientras el servidor integrado la está usando; arreglar algo en una sola de las cuatro copias de la pantalla.

## Qué es

Con **Mod Menu** instalado, el botón de configuración de la lista de mods abre una pantalla que edita `config/soullink.json` **de esta instalación**. Mod Menu es opcional:

- `build.gradle` lo tiene como `compileOnly "maven.modrinth:modmenu:${modmenu_version}"` (repositorio de Modrinth). No viaja en el jar ni es dependencia.
- `fabric.mod.json` declara el entrypoint `modmenu` → `com.soullink.client.ModMenuIntegration`, que devuelve `SoulLinkConfigScreen::new`. **Sin Mod Menu nadie carga esa clase**, así que no hay `NoClassDefFoundError`.
- El código va en `src/client` (`splitEnvironmentSourceSets()`): un servidor dedicado nunca ve estas clases.

| Dónde se abre | Qué archivo cambia | Cómo se aplica |
|---|---|---|
| Menú principal | El de esta instalación | `recargar(null)`: la config en memoria, que usa el próximo mundo |
| Un mundo propio (singleplayer o LAN abierto desde acá) | El mismo: el servidor integrado lee esta carpeta `config/` | `recargar(servidor)` en el hilo del servidor |
| Conectado a otro servidor | El de esta instalación, **no el del servidor** | Igual que en el menú principal, con un cartel amarillo que lo avisa |

## Una clase para los cuatro mods

**`PantallaDeConfiguracion` es idéntica en Hero's Forge, Soul Link, Bloodmoon & Bosses y Mythic Loot, salvo la línea del `package`.** No conoce los campos: recorre el JSON y arma un control por valor. Un arreglo se hace en una copia y se pasa a las otras tres; para verificar que siguen iguales:

```bash
for f in */src/client/java/*/*/*/PantallaDeConfiguracion.java; do sed 1d $f | md5sum; done
```

Lo de cada mod va en la subclase (`SoulLinkConfigScreen`):

| Gancho | Para qué | En este mod |
|---|---|---|
| `traducir(clave, args)` | El texto en el idioma del cliente (`Idioma.resolver`/`Lang.resolve` del idioma elegido), o null si la clave no existe | Con las tablas del propio mod, no con las de vanilla: así `es_mx` también ve español |
| `prefijo()` | El comienzo de las claves: `<prefijo>.pantalla.*`, `.campo.*`, `.ayuda.*`, `.valor.*` | `soullink.config` |
| `recargar(servidor)` | Volver a leer el archivo y aplicarlo; devuelve lo rechazado | `SoulLink.reloadConfig(server)`: con servidor también `StatsScoreboard.applySidebar`; sin servidor sólo la config en memoria (la barra se pone en `SERVER_STARTED` del próximo mundo). Los `Warning` se traducen al idioma del cliente |
| `opciones(ruta)` | Los textos que se eligen de una lista | `idioma` → `Lang.AVAILABLE`; `barraLateral` → `ninguna` más los ids de `GroupStats.Stat` |
| `admiteDecimales(ruta)` | Un número escrito como entero que acepta decimales | `corazonesDeSusto`: `SoulLinkConfig.write` escribe `2` y no `2.0`, así que sin esto la pantalla lo tomaría como entero y no dejaría poner 1,5 |

- **La pantalla está en español aunque el resto del código de Soul Link esté en inglés**: es la copia compartida (ver abajo). La subclase y la integración sí van en inglés.

## Qué control le toca a cada valor

| En el JSON | Control |
|---|---|
| `true` / `false` | `CycleButton.onOffBuilder`: cambia el árbol en memoria al tocarlo |
| Texto con `opciones(ruta)` | `CycleButton` con esas opciones, nombradas con `<prefijo>.valor.<opción>` (`""` usa `.valor.ninguno`). **Uno que no está en la lista (mal escrito a mano) se agrega**, para no perderlo al guardar |
| Número | `EditBox`. **Es entero si en el archivo no tiene punto ni exponente** (Gson escribe los `double` con `.0`), salvo `admiteDecimales`. Acepta coma decimal. Inválido = texto rojo y **Guardar** apagado |
| Otro texto | `EditBox` libre |
| Objeto | Un título dorado; sus valores van con sangría |
| Lista o `null` | «(editalo en el archivo)»: hoy no hay ninguno |

- **Un decimal se escribe con decimales** (`4.0`, no `4`): si no, la próxima vez la pantalla lo tomaría como entero.
- **Lo que no se tocó queda con el formato del archivo** (`8.0E-4`), aunque la pantalla lo muestre como `0.0008`: se compara contra el texto original.
- **Lo escrito sobrevive a rearmar la pantalla** (cambiar el tamaño de la ventana, o volver después de un rechazo): los textos se guardan por ruta en `textos`, no en los widgets.

**El nombre de cada fila** sale, en orden, de: `nombre()` de la subclase → `<prefijo>.campo.<ruta>` → `<prefijo>.campo.<clave>` → si la clave es un id (`minecraft:zombie`), la traducción de vanilla `entity.minecraft.zombie` → la clave en palabras (`bloquesPorNivel` → «Bloques por nivel»). **Un campo nuevo en la config aparece solo**, con su clave en palabras: agregale `campo.` y `ayuda.` en `en_us.json` y `es_ar.json`. El tooltip de la etiqueta tiene la ayuda, si es entero o decimal, y la ruta en el JSON. **No va en el campo:** el primer `EditBox` toma el foco al abrir y su tooltip taparía la fila de abajo.

## Guardar

1. Valida los textos y escribe el JSON entero (Gson con sangría, sin escapar HTML) sobre `config/soullink.json`.
2. `recargar`: **con un mundo abierto, en el hilo del servidor integrado** (`servidor.submit`), porque recargar toca cosas del mundo (jugadores, scoreboard). La pantalla pausa el juego y el servidor igual atiende la tarea: lo prueba el test.
3. **Si `recargar` devuelve algo** (un error o un valor que el mod reemplazó por el de por defecto), **vuelve a escribir el archivo como estaba y recarga otra vez**. Si no, el archivo quedaría con un valor que el mod ignora y arrancaría con el de por defecto sin que nadie lo vea. La pantalla queda abierta, con los avisos en rojo arriba y lo escrito todavía en los campos.
4. Si no hubo nada, vuelve a la pantalla anterior (la lista de Mod Menu).

`terminar` corre en el hilo del cliente (`whenCompleteAsync(…, minecraft)`) y no hace nada si la pantalla ya se cerró mientras el servidor recargaba.

## Ver también

- [Configuración](configuracion.md): las claves del archivo y cómo se validan.
- [Idioma](idioma.md): de dónde salen los textos.
- [Tests: pantalla de configuración](../tests/pantalla-de-configuracion.md)
