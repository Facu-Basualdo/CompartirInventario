#!/usr/bin/env node
// Verifica la salud del grafo de documentacion de claude/.
//   node scripts/verificar-docs.mjs
// Sale con codigo 1 si hay algun problema, asi sirve en CI o en un hook.

import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs';
import { join, dirname, relative, resolve, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const VAULT = join(RAIZ, 'claude');

// Punteros: los CLAUDE.md por carpeta son entradas del harness, no nodos del
// grafo. Se leen para validar sus enlaces, pero no cuentan como huerfanos.
const PUNTEROS = ['src/main', 'src/gametest']
  .map((a) => join(RAIZ, a, 'CLAUDE.md'))
  .filter(existsSync);

const rel = (p) => relative(RAIZ, p).replaceAll('\\', '/');

function listarMd(dir) {
  const salida = [];
  for (const entrada of readdirSync(dir)) {
    if (entrada.startsWith('.')) continue;
    const p = join(dir, entrada);
    if (statSync(p).isDirectory()) salida.push(...listarMd(p));
    else if (entrada.endsWith('.md')) salida.push(p);
  }
  return salida;
}

// GitHub genera el ancla en minusculas, con los espacios en guiones y sin
// puntuacion. Replicarlo alcanza para detectar un ancla que dejo de existir.
function anclaDe(titulo) {
  return titulo
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replaceAll(' ', '-');
}

function sinBloquesDeCodigo(texto) {
  return texto.replace(/```[\s\S]*?```/g, '').replace(/`[^`\n]*`/g, '');
}

function titulosDe(texto) {
  const anclas = new Set();
  // Se corta con /\r?\n/, no con '\n': en un checkout CRLF la linea queda con un
  // \r al final y el `(.*)$` no cierra, asi que NINGUN titulo entra al set y
  // todas las anclas dan rotas siempre.
  for (const linea of sinBloquesDeCodigo(texto).split(/\r?\n/)) {
    const m = linea.match(/^#{1,6}\s+(.*)$/);
    if (m) anclas.add(anclaDe(m[1].replace(/[*`_]/g, '')));
  }
  return anclas;
}

const notas = listarMd(VAULT);
const archivos = [join(RAIZ, 'CLAUDE.md'), ...PUNTEROS, ...notas];

const contenido = new Map();
for (const f of archivos) contenido.set(f, readFileSync(f, 'utf8'));

const problemas = [];
const entrantes = new Map(notas.map((n) => [n, 0]));

const ENLACE = /\[([^\]]*)\]\(([^)\s]+)\)/g;

for (const archivo of archivos) {
  const texto = sinBloquesDeCodigo(contenido.get(archivo));
  for (const [, etiqueta, url] of texto.matchAll(ENLACE)) {
    if (/^(https?:|mailto:|#)/.test(url)) continue;
    const [ruta, ancla] = url.split('#');
    const destino = resolve(dirname(archivo), ruta);

    if (!existsSync(destino)) {
      problemas.push(`enlace roto      ${rel(archivo)} -> ${url}   [${etiqueta}]`);
      continue;
    }
    if (entrantes.has(destino)) entrantes.set(destino, entrantes.get(destino) + 1);

    if (ancla) {
      const textoDestino = contenido.get(destino) ?? readFileSync(destino, 'utf8');
      if (!titulosDe(textoDestino).has(ancla)) {
        problemas.push(`ancla rota       ${rel(archivo)} -> ${url}   [${etiqueta}]`);
      }
    }
  }
}

for (const [nota, n] of entrantes) {
  if (n === 0) problemas.push(`nota huerfana    ${rel(nota)}   (nadie la enlaza)`);
}

// Cada nota de un area tiene que estar en el INDICE.md de esa area.
for (const nota of notas) {
  const carpeta = dirname(nota);
  const indice = join(carpeta, 'INDICE.md');
  if (nota === indice || !existsSync(indice)) continue;

  const textoIndice = contenido.get(indice);
  if (!textoIndice.includes(`(${basename(nota)})`)) {
    problemas.push(`fuera del indice ${rel(nota)}   (agregala a ${rel(indice)})`);
  }
  if (!/^\[← .*\]\(INDICE\.md\)/m.test(contenido.get(nota))) {
    problemas.push(`sin breadcrumb   ${rel(nota)}   (falta el enlace al INDICE)`);
  }
}

const enlaces = archivos.reduce(
  (n, f) => n + [...sinBloquesDeCodigo(contenido.get(f)).matchAll(ENLACE)]
    .filter(([, , u]) => !/^(https?:|mailto:|#)/.test(u)).length,
  0,
);

console.log(`${notas.length} notas · ${enlaces} enlaces internos`);

if (problemas.length === 0) {
  console.log('OK — el grafo esta sano');
  process.exit(0);
}

console.log(`\n${problemas.length} problema(s):`);
for (const p of problemas) console.log(`  ${p}`);
process.exit(1);
