# Tests: configuración

[← Índice Tests](INDICE.md)

`src/gametest/java/com/soullink/test/ConfigGameTest.java`, método `configFallsBackPerField`. **Va en su propia clase** porque sólo lee y escribe archivos en una carpeta temporal (`Files.createTempDirectory`): no toca el estado compartido, así que puede correr en el mismo batch que el test grande sin pisarlo. Como `IdMigrationGameTest`.

**No usa `config/soullink.json` del servidor de prueba.** `SoulLinkConfig.load` recibe la ruta, así que el test no cambia la config con la que corre el otro test.

## Qué cubre

| Archivo | Qué verifica |
|---|---|
| No existe | Se crea, y lo leído es `SoulLinkConfig.DEFAULT` |
| `compartirExperiencia: false`, `anunciarMuertes: "sí"`, `barraLateral: "mobs"`, `corazonesDeSusto: 50` | Se respetan los dos válidos; los dos inválidos usan el default; hay exactamente 2 avisos; el archivo **no** se reescribe (sigue diciendo `"sí"`) |
| `idioma: "es_ar"` y después `"klingon"` | Se respeta el primero; el segundo usa `en_us` con un aviso |
| Sólo `barraLateral: "ninguna"` | La barra queda vacía, y las claves que faltan se agregan al archivo |
| `{ roto` | `ok()` da false |

## Idioma, aparte

`LangGameTest` verifica que `en_us.json` y `es_ar.json` tengan las mismas claves (una que falta se vería en el otro idioma), que `es_MX` y `es_es` vayan a `es_ar`, `en_gb` a `en_us` y `fr_fr` al de la config, que los `%s` se reemplacen en orden con un `Component` como argumento, y la coma decimal. No toca el estado del servidor. Los jugadores de prueba informan `en_us` (`ClientInformation.createDefault()`), así que el test grande corre en inglés, pero no compara textos.

El test grande usa `/swap` y `/soullink group` en un paso cada uno, para cubrir los nombres en inglés.

## Qué NO cubre

- `/soullink recargar` aplicando la config a un servidor que está corriendo, ni que la barra lateral cambie.
- Que `compartirExperiencia: false` corte el reparto. El test grande corre con la config por defecto.

## Ver también

- [Configuración (mod)](../mod/configuracion.md)
- [Inventario compartido](inventario-compartido.md): el test grande, donde están las aserciones de estadísticas.
- [Idioma (mod)](../mod/idioma.md)
- [Tests de la pantalla de configuración](pantalla-de-configuracion.md)
