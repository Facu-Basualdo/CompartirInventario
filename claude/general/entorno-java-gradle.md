# Entorno Java y Gradle

[← Hub](../../CLAUDE.md)

Síntoma: `gradlew` falla al configurar con **"Dependency requires at least JVM runtime version 25. This build uses a Java 17 JVM."**, aunque Java 25 esté instalado y en el `PATH`.

## Por qué

En esta PC, `~/.gradle/gradle.properties` tiene `org.gradle.java.home` apuntando a un JDK 17. Está ahí a propósito para otros proyectos (React Native no compila con Java 25). **No lo cambies.** Las propiedades del `GRADLE_USER_HOME` le ganan a las del `gradle.properties` del proyecto, así que no alcanza con poner `org.gradle.java.home` en el repo.

## Cómo se fija Java 25 en este proyecto

| Archivo | Qué hace |
|---|---|
| `gradle/gradle-daemon-jvm.properties` | `toolchainVersion=25`: el daemon de Gradle elige una JVM 25, y este criterio le gana a `org.gradle.java.home` |
| `~/.gradle/gradle.properties` → `org.gradle.java.installations.paths` | Ruta del JDK 25 de esta PC (`C:/Program Files/Microsoft/jdk-25.0.4.101-hotspot`). **Sin esto Gradle no lo encuentra** y falla con "No defined toolchain download url for WINDOWS" |

**La ruta va en el `gradle.properties` global, no en el del repo**, porque es de cada máquina: en otra PC, o si se actualiza el JDK, se cambia ahí (o se borra si el JDK nuevo queda en una ubicación que Gradle detecta sola). Ponerla al lado del `org.gradle.java.home` del JDK 17 no rompe React Native: sólo dice dónde buscar toolchains, no cambia el JDK del daemon.

## Versiones

| Pieza | Versión | Dónde |
|---|---|---|
| Minecraft | 26.3 | `gradle.properties` → `minecraft_version` |
| Fabric Loader | 0.19.5 | `loader_version` |
| Fabric Loom | 1.18-SNAPSHOT (resuelve 1.18.2) | `loom_version`; el plugin ahora es `net.fabricmc.fabric-loom`, no `fabric-loom` |
| Fabric API | 0.162.0+26.3 | `fabric_api_version` |
| Gradle | 9.7.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Java | 25 | `options.release = 25` en `build.gradle` |

Las versiones nuevas se consultan en https://fabricmc.net/develop o en `https://meta.fabricmc.net/v2/versions/`.

## IDE

- **IntelliJ IDEA 2025.3 o más nuevo** (Java 25). Instalado: 2026.2.3, con el plugin **Minecraft Development**.
- Si IntelliJ importa con Java 17: *Settings → Build Tools → Gradle → Gradle JVM* → JDK 25.
- En VS Code, el diagnóstico "is not on the classpath" sobre `src/gametest` es del Java Language Server, no del build.

## Ver también

- [Minecraft 26.x sin ofuscar](minecraft-26-sin-ofuscar.md)
- [Probar con dos jugadores](probar-con-dos-jugadores.md)
