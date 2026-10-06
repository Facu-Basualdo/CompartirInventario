# Minecraft 26.x sin ofuscar

[← Hub](../../CLAUDE.md)

**Desde Minecraft 26.1 el jar viene sin ofuscar y Fabric usa los nombres oficiales de Mojang.** Yarn dejó de mantenerse. Casi todos los tutoriales y respuestas de foros anteriores a 2026 usan nombres de Yarn (`PlayerInventory`, `main`, `ServerPlayerEntity`…) que acá no existen.

| Yarn (viejo) | Mojang (este proyecto) |
|---|---|
| `PlayerInventory` | `Inventory` |
| `ServerPlayerEntity` | `ServerPlayer` |
| `PersistentState` | `SavedData` |
| `Identifier` (Yarn) / `ResourceLocation` (Mojang viejo) | `Identifier` |

**Tampoco confíes en lo que "sabés" de 1.21.x:** entre versiones cambian firmas. Ejemplos que afectaron a este mod:

- Armadura y mano secundaria ya no son listas de `Inventory`: viven en `EntityEquipment` (un `EnumMap`).
- La carga y el guardado usan `ValueInput` / `ValueOutput`, no `CompoundTag` directo.
- `SavedData` se registra con `SavedDataType(Identifier, Supplier, Codec, DataFixTypes)` y se obtiene de `MinecraftServer.getDataStorage()`.

## Verificar antes de escribir un mixin

Un mixin con un nombre o descriptor mal puesto **compila igual y explota recién al cargar la clase** en runtime. Antes de escribir uno, mirá la clase real:

```bash
J=.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.3/minecraft-merged-*-26.3.jar
JP="/c/Program Files/Microsoft/jdk-25.0.4.101-hotspot/bin/javap"

"$JP" -p -cp $J net.minecraft.world.entity.player.Inventory          # campos y firmas
"$JP" -c -p -cp $J net.minecraft.world.entity.EntityEquipment \
  | sed -n '/public void setAll/,/^$/p'                               # bytecode de un método
```

El jar aparece después del primer `gradlew compileJava`. Con `-c` se ve el bytecode, que alcanza para saber si un método hace `clear()` antes de cargar, si chequea null, a quién llama, etc. Así se encontró lo de [carga de datos del jugador](../mod/carga-de-datos-del-jugador.md) y lo del `DataFixTypes` null en [persistencia](../mod/persistencia.md).

Para las clases de Fabric API (eventos, gametest), los jars están en `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`.

## Ver también

- [Entorno Java y Gradle](entorno-java-gradle.md)
- [Inventario compartido](../mod/inventario-compartido.md)
