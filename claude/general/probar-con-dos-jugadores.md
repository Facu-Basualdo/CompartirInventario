# Probar con dos jugadores

[← Hub](../../CLAUDE.md)

El test automático ([tests: inventario compartido](../tests/inventario-compartido.md)) no ejercita el sync por red. Para ver el inventario compartido en dos clientes reales hacen falta un servidor dedicado y dos clientes.

## Pasos

1. `gradlew runServer` una vez. Crea `run/` y se cierra pidiendo la EULA.
2. En `run/eula.txt` poné `eula=true` (es aceptar la EULA de Mojang: lo hace la persona, no el agente).
3. En `run/server.properties` poné **`online-mode=false`**: los clientes de desarrollo no tienen cuenta autenticada y sin esto el servidor los rechaza.
4. `gradlew runServer` de nuevo.
5. `gradlew runClient` y `gradlew runClient2`, cada uno en su terminal o desde las run configs de IntelliJ.
6. En los dos: *Multijugador → Conexión directa → `localhost`*.

## Los dos clientes

| Tarea | Usuario | Carpeta |
|---|---|---|
| `runClient` | `Player` + número al azar (lo pone Loom) | `run/` |
| `runClient2` | `Jugador2` | `run-client2/` |

`runClient2` se define en `loom { runs { client2 { … } } }` de `build.gradle`. **Tiene su propia `runDir`** para que los dos clientes no compartan `options.txt`, logs ni guardados con el otro cliente y el servidor, que usan `run/`.

**Después de cambiar código hay que reiniciar los clientes y el servidor:** no hay hot reload de mixins.

`run/` y `run-client2/` están en `.gitignore`.

## Qué mirar

- Que lo que agarra uno aparezca en el otro **sin abrir y cerrar el inventario**.
- Abrir un cofre en un cliente mientras el otro agarra ítems: el inventario de abajo del cofre también tiene que actualizarse.
- Ponerse armadura en uno: el otro la ve puesta en su pantalla de inventario y en el modelo.

## Ver también

- [Entorno Java y Gradle](entorno-java-gradle.md)
- [Inventario compartido](../mod/inventario-compartido.md)
