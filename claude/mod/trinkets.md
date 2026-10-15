# Trinkets compartidos

[← Índice Mod](INDICE.md)

Con **Trinkets Updated** instalado (opcional, id `trinkets_updated`, `provides: trinkets`), los slots de Trinkets (espalda, anillos, collar…) se comparten dentro del grupo igual que la armadura. Sin Trinkets el mod no cambia: los mixins de `soullink.trinkets.mixins.json` sólo se aplican si está (`TrinketsMixinPlugin`), y `SharedTrinkets` sólo se carga desde `SoulLink.onInitialize` con `isModLoaded("trinkets")`. Los clientes vanilla pueden seguir entrando a un servidor sin Trinkets.

## Cómo se comparte

Mismo invariante que el [inventario compartido](inventario-compartido.md): **los jugadores apuntan a las mismas listas, no se copian.** Cada slot de Trinkets es un `TrinketInventoryImpl` con dos `NonNullList` (`stacks` y `cosmeticStacks`). El grupo guarda un `TrinketSlotLists` por id de slot (`"chest/back"`), y `SharedTrinkets.bind` reemplaza las listas del jugador por esas (accessor `TrinketInventoryAccessor`, porque `TrinketInventoryImpl` es `final`: se castea pasando por `Object`).

| Cuándo se engancha | Por qué |
|---|---|
| `TrinketInventoryImpl.<init>` y `update()` (`TrinketInventoryImplMixin`) | Trinkets crea las listas en el constructor y las **reemplaza** en `update()` cuando cambia la cantidad de lugares |
| Final del constructor de `ServerPlayer` (`TrinketsServerPlayerMixin`) | Trinkets arma los slots en el constructor de `LivingEntity`, cuando `getGameProfile()` todavía es null y no se sabe el grupo |
| `LinkGroups.onChange` | Al cambiar de grupo |

- Si el grupo no tiene todavía ese slot, **las listas del jugador pasan a ser las compartidas**. Excepción: si esas listas ya son de otro grupo (vino con `/soullink grupo`), el grupo nuevo arranca con listas vacías; si no, los dos grupos quedarían compartiendo.
- Si cambió la cantidad de lugares del slot (datapack o modificadores), las listas se rearman con el tamaño nuevo y lo que no entra cae a los pies del jugador.
- Los efectos de cada trinket (atributos, `TrinketCallback.tick`) los aplica Trinkets por jugador: su `StackHistory` ve el cambio en la lista compartida y le avisa a cada uno.

## La carga del archivo no pisa lo compartido

Trinkets lee los trinkets guardados **dentro de `LivingEntity.readAdditionalSaveData`**, y si el archivo no tiene la clave `trinkets` llama a `clearContents()`, que vaciaría el slot de todos. Por eso `TrinketsLoadMixin` envuelve **todo `Entity.load`**: en `HEAD` guarda qué objeto había en cada lugar de las listas (`SharedTrinkets.beforeLoad`) y en `RETURN` lo devuelve (`afterLoad`).

Rescate, como con el inventario (ver [carga de datos del jugador](carga-de-datos-del-jugador.md#cofre-con-los-ítems-de-antes-del-mod)):

1. `TrinketsSaveMixin` escribe `SoulLinkTrinkets: true` en cada guardado. Es otra marca que `InventarioCompartido` porque los trinkets se pueden empezar a compartir en un mundo que ya usaba el mod: esos archivos tienen la marca del inventario pero sus trinkets son del jugador.
2. Archivo **sin** `SoulLinkTrinkets` → lo que cargó (lo que no es el mismo objeto de antes **ni un ítem igual**) va a `addRescuedItems` y termina en el cofre del JOIN.
3. `trinketsInitialized` (en `SharedInventoryState`, se marca en el JOIN) es el `initialized` de los trinkets: mientras sea false, lo que trae el primero que entra **queda** como lo compartido.

**Por qué "ni un ítem igual":** si Trinkets reconstruye los slots durante la carga (`rebuild`), copia los ítems con `copy()`. Esa copia no es el mismo objeto pero tampoco es del jugador; rescatarla duplicaría el ítem. Costo: un trinket personal idéntico al compartido no se rescata.

## Persistencia

En el `LinkGroup`, campo `trinkets`: id del slot → `{size, items, cosmeticSize, cosmetic}` (`TrinketSlotLists.CODEC`, sólo tipos de vanilla). Se guarda aunque Trinkets no esté instalado y un mundo sin el campo se lee con el mapa vacío. Ver [persistencia](persistencia.md).

## Qué no se probó

- Muerte con `keepInventory`: Trinkets copia los trinkets del jugador viejo al nuevo con `copyData` (`writeData` + `readData`), sobre las mismas listas: queda igual, con copias.
- Jugadores con distinta cantidad de lugares en el mismo slot (modificadores por jugador): el último que se engancha rearma las listas con su tamaño.

## Ver también

- [Inventario compartido](inventario-compartido.md)
- [Carga de datos del jugador](carga-de-datos-del-jugador.md)
- [Grupos vinculados](grupos.md)
- [Tests: inventario compartido](../tests/inventario-compartido.md): los pasos de Trinkets al final de la secuencia.
