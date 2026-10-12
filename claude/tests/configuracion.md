# Tests: configuración

[← Índice Tests](INDICE.md)

`src/gametest/java/com/soullink/test/ConfigGameTest.java`, método `configFallsBackPerField`. **Va en su propia clase** porque sólo lee y escribe archivos en una carpeta temporal (`Files.createTempDirectory`): no toca el estado compartido, así que puede correr en el mismo batch que el test grande sin pisarlo. Como `IdMigrationGameTest`.

**No usa `config/soullink.json` del servidor de prueba.** `SoulLinkConfig.load` recibe la ruta, así que el test no cambia la config con la que corre el otro test.

## Qué cubre

| Archivo | Qué verifica |
|---|---|
| No existe | Se crea, y lo leído es `SoulLinkConfig.DEFAULT` |
| `compartirExperiencia: false`, `anunciarMuertes: "sí"`, `barraLateral: "mobs"`, `corazonesDeSusto: 50` | Se respetan los dos válidos; los dos inválidos usan el default; hay exactamente 2 avisos; el archivo **no** se reescribe (sigue diciendo `"sí"`) |
| Sólo `barraLateral: "ninguna"` | La barra queda vacía, y las claves que faltan se agregan al archivo |
| `{ roto` | `ok()` da false |

## Qué NO cubre

- `/soullink recargar` aplicando la config a un servidor que está corriendo, ni que la barra lateral cambie.
- Que `compartirExperiencia: false` corte el reparto. El test grande corre con la config por defecto.

## Ver también

- [Configuración (mod)](../mod/configuracion.md)
- [Inventario compartido](inventario-compartido.md): el test grande, donde están las aserciones de estadísticas.
