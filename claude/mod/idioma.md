# Idioma de los mensajes

[← Índice Mod](INDICE.md)

**Los textos los traduce el servidor, no el cliente.** Si un mensaje le llega a un jugador como `soullink.swap.done` en vez de la frase, alguien usó `Component.translatable`: un cliente vanilla no tiene nuestros archivos de idioma y muestra la clave. Todo mensaje pasa por `Lang.tr`, que arma un `Component` literal ya traducido.

## Qué idioma ve cada uno

`Lang.of(jugador)` lee lo que el cliente informó al entrar (`ServerPlayer.clientInformation().language()`) y `Lang.resolve` lo lleva a uno que tenemos:

| Cliente | Ve |
|---|---|
| `es_ar`, `es_es`, `es_mx`, cualquier `es*` | `es_ar` |
| `en_us`, `en_gb`, cualquier `en*` | `en_us` |
| Otro (`pt_br`, `fr_fr`…) | El de la config (`idioma`, por defecto `en_us`) |
| Sin jugador: la consola, comandos desde un bloque de comandos | El de la config |

**Lo que es igual para todos va en el idioma de la config:** los títulos de los objetivos del scoreboard (uno solo por objetivo) y lo que se loguea. `StatsScoreboard.applyTitles` los reescribe al arrancar y en `/soullink recargar`, porque los objetivos quedan guardados en el mundo con el título que tenían.

**Lo que va a todo el servidor se arma una vez por jugador.** `Lang.broadcast` le manda a cada conectado el mensaje en su idioma, y a la consola en el de la config. No uses `broadcastSystemMessage` con un texto ya traducido: todos lo verían en un solo idioma.

`sendSuccess(…, true)` (lo que se avisa a los operadores) sale en el idioma de quien corrió el comando: es un solo `Component` para todos.

## Archivos

`assets/soullink/lang/en_us.json` y `es_ar.json`. Los lee `Lang` con `getResourceAsStream` al cargar la clase, no el sistema de recursos del cliente. Que estén en `assets/…/lang` sirve además para `modmenu.descriptionTranslation.soullink`: Mod Menu (en el cliente) toma de ahí la descripción del mod en el idioma del jugador, y la de `fabric.mod.json` queda en inglés.

**Los dos archivos tienen que tener las mismas claves.** Una que falta en español se ve en inglés; una que falta en los dos, como la clave. `LangGameTest` compara los dos conjuntos.

| En la plantilla | Qué hace |
|---|---|
| `%s` | El argumento siguiente |
| `%2$s` | El segundo argumento, para un idioma que necesite otro orden |
| Un argumento `Component` | Se agrega tal cual, con su estilo: el nombre de un jugador con su color de equipo, una causa de muerte (que sí es traducible de vanilla) |

Los plurales son dos claves (`.one` y `.many`): con dos idiomas alcanza, y el inglés y el español los forman igual.

`Lang.number` escribe los corazones con coma decimal en español (`17,5`) y punto en inglés (`17.5`).

## Comandos en los dos idiomas

| Español | Inglés |
|---|---|
| `/intercambiar` | `/swap` |
| `/soullink grupo`, `grupos`, `quitar` | `/soullink group`, `groups`, `remove` |
| `/soullink recargar`, `stats reiniciar` | `/soullink reload`, `stats reset` |

Son nodos duplicados, no redirects (`SoulLinkCommand.NAMES`, `SwapPlaces.NAMES`): un redirect no hereda el `executes` del nodo destino, y `/soullink reload` solo no haría nada. Los mensajes nombran el comando en el idioma del jugador (están en el archivo de idioma).

**Lo que no se traduce:** los ids (los de `barraLateral`, como `culpas`, los nombres de los objetivos `soullink_culpas`, el grupo `general`) y las claves de `config/soullink.json`. Son datos que se escriben en archivos y comandos: traducirlos rompería las configs y los mundos existentes.

Los errores de un comando se crean en cada llamada (`new SimpleCommandExceptionType(Lang.tr(…)).create()`) y no como constantes, como se suele hacer: el mensaje depende de quién lo corrió.

## Ver también

- [Configuración](configuracion.md): la clave `idioma` y los avisos, que también se traducen.
- [Estadísticas](estadisticas.md): los títulos del scoreboard.
- [Tests: configuración](../tests/configuracion.md)
