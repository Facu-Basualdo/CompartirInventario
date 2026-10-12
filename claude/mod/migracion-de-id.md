# Migración del id (inventariocompartido → soullink)

[← Índice Mod](INDICE.md)

Síntomas que esta nota evita: después de actualizar a la 2.0.0, el servidor arranca con el inventario compartido vacío; cada jugador que entra recibe otra vez un cofre con sus ítems de antes del mod.

## Qué cambió

**Desde la 2.0.0 el id del mod es `soullink`** (antes `inventariocompartido`): paquete `com.soullink`, `soullink.mixins.json`, prefijo `soullink$` en los mixins y la carpeta de datos del mundo.

| Dato | Antes | Ahora | Cómo se pasa |
|---|---|---|---|
| Estado compartido | `world/data/inventariocompartido/shared_inventory.dat` | `world/data/soullink/shared_inventory.dat` | `IdMigration.migrateWorldData` en `SERVER_STARTING` |
| Marca en el archivo de cada jugador | `InventarioCompartido` | **igual** | No se pasa: la clave quedó con el nombre viejo |

## El archivo del mundo

**`IdMigration.migrateWorldData` corre en `ServerLifecycleEvents.SERVER_STARTING`**, que es antes de que se carguen los mundos: nadie leyó todavía el `SavedData`. Si existe el archivo viejo y no el nuevo, lo **mueve** (`moveIfMissing`); si ya hay uno nuevo, no toca nada.

- Se mueve y no se copia: si quedaran los dos, volver a una versión vieja cargaría un estado desactualizado sin avisar.
- Si el movimiento falla, tira la excepción y el servidor no arranca. Mejor eso que arrancar vacío y pisar el archivo de verdad al guardar.

## La marca del jugador no cambió

`PlayerMixin` marca el archivo de cada jugador con `InventarioCompartido = true` para saber que el inventario que trae es una copia del compartido (ver [Carga de datos del jugador](carga-de-datos-del-jugador.md)). **Esa clave no se renombra:** con otra, todos los jugadores parecerían de antes del mod y recibirían otra vez sus ítems viejos y su experiencia. El renombre automático del id la había cambiado; se volvió atrás a mano.

## Probado

- `IdMigrationGameTest`: mueve el archivo, no pisa uno nuevo, no hace nada sin archivo viejo.
- Con una copia del mundo de un servidor real (Server3): el estado compartido quedó con lo mismo (18 ítems, la vida y la experiencia).

## Ver también

- [Persistencia](persistencia.md)
- [Carga de datos del jugador](carga-de-datos-del-jugador.md)
