---
description: Actualiza las notas de claude/ segun los cambios de la rama actual
argument-hint: "[rama base, default main]"
allowed-tools: Bash(git diff:*), Bash(git log:*), Bash(git status:*), Bash(git merge-base:*), Bash(node scripts/verificar-docs.mjs), Read, Edit, Write, Grep, Glob
---

Actualizá el grafo de documentación `claude/` según lo que cambió en esta rama. Rama base: `$ARGUMENTS` (si está vacío, `main`).

## 1. Mirá qué cambió

- `git merge-base HEAD <base>` para el punto de partida.
- `git diff --stat <base>...HEAD` y `git log --oneline <base>..HEAD`.
- `git status` por si hay cambios sin commitear que también cuenten.
- Después **leé el diff real** de lo sustantivo (`src/main/java`, `src/main/resources/*.json`, `build.gradle`, `gradle.properties`, `src/gametest`). No te guíes por los nombres de archivo.

## 2. Decidí qué merece documentarse

Criterio literal: **¿alguien que toque esto dentro de seis meses se va a equivocar si no lo sabe?**

SÍ documentar:
- Decisiones con un porqué no obvio, sobre todo si ya hubo un bug.
- Invariantes y orden de operaciones (por ejemplo, qué método de vanilla vacía una lista antes de cargarla).
- Contratos entre el mod y vanilla: qué método se intercepta, con qué firma y por qué ése.
- Formato y ubicación de lo que se guarda en disco.
- Cambios de versión de Minecraft / Fabric que obligaron a cambiar código.
- Comportamientos visibles para el jugador que no son obvios (qué se comparte y qué no).

NO documentar:
- Lo que el código ya dice solo.
- Refactors sin efecto observable.
- La historia del cambio, ni nada que se deduzca de `git log`.

**"No hay nada que documentar" es un resultado válido y común.** No inventes notas para justificar haber corrido.

## 3. Ubicá dónde va cada cosa

- `grep -ril "<palabra clave>" claude/` antes de crear nada.
- **Preferí editar sobre crear**: una nota por tema.
- Si el tema ya existe en otra área, **reusá el mismo nombre de archivo** (`claude/mod/x.md` ↔ `claude/tests/x.md`).
- Áreas: `claude/mod/` (`src/main`), `claude/tests/` (`src/gametest`), `claude/general/` (transversales, enlazadas desde el hub).

## 4. Escribí en el estilo del vault

Leé una nota existente del área antes de escribir. Reglas:
- Arrancá por el síntoma o el bug, no por la definición.
- Negrita para la regla, prosa para el porqué.
- Nombrá clases, métodos y archivos reales.
- Tablas para los repartos.
- Describí cómo es hoy, no cómo se llegó.
- Español rioplatense (vos).
- 100–250 líneas por nota; si pasa de ~400, son dos temas.
- **No pongas código entre backticks en los títulos** (`##`): el verificador los borra al calcular anclas y el enlace da roto.

## 5. Cerrá el grafo

No es opcional:
- Agregá la nota a la tabla del `INDICE.md` de su área, con una descripción que **cargue el gotcha** (afirmaciones separadas por ` · `, no un rótulo).
- Breadcrumb `[← Índice <Área>](INDICE.md)` debajo del título.
- Actualizá los `## Ver también` de las dos puntas.
- Fijate si corresponde una fila nueva en "Features que cruzan áreas" del `CLAUDE.md` raíz.

## 6. Verificá

`node scripts/verificar-docs.mjs` tiene que dar OK.

## 7. Informá

Qué notas creaste o editaste, y qué dejaste sin documentar a propósito y por qué. **No commitees salvo que te lo pidan.**
