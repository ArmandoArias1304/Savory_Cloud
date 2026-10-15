/**
 * Prueba de comportamiento de /js/fly-to-cart.js con un DOM mínimo falso.
 * No forma parte de la aplicación: se usa para verificar la pieza antes de
 * probarla en el navegador. Ejecutar con: node tools/fly-to-cart.check.js
 */
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const SRC = path.join(
  __dirname,
  "..",
  "src",
  "main",
  "resources",
  "static",
  "js",
  "fly-to-cart.js",
);

let fallos = 0;
function check(nombre, condicion) {
  if (condicion) {
    console.log("  ok   " + nombre);
  } else {
    fallos++;
    console.log("  FALLA " + nombre);
  }
}

function makeClassList() {
  const clases = new Set();
  return {
    add: (c) => clases.add(c),
    remove: (c) => clases.delete(c),
    contains: (c) => clases.has(c),
    _all: () => Array.from(clases),
  };
}

function makeElement(tag, extra = {}) {
  const el = {
    tagName: tag,
    classList: makeClassList(),
    offsetWidth: 100,
    style: {},
    dataset: {},
    children: [],
    removed: 0,
    listeners: [],
    parentNode: null,
    remove() {
      this.removed++;
      this.parentNode = null;
    },
    addEventListener(nombre, fn) {
      this.listeners.push(nombre);
      // animationend no se dispara solo: lo dejamos pendiente.
    },
    appendChild(hijo) {
      hijo.parentNode = this;
      this.children.push(hijo);
      return hijo;
    },
    cloneNode() {
      return makeElement(tag, extra);
    },
    getBoundingClientRect() {
      return this.rect || { left: 0, top: 0, width: 40, height: 40 };
    },
    ...extra,
  };
  return el;
}

function run(caso) {
  const eventos = [];
  const body = makeElement("body");
  body.appendChild = (hijo) => {
    eventos.push("append");
    hijo.parentNode = body;
    body.children.push(hijo);
    return hijo;
  };

  const cartItemCount = makeElement("span");
  const floatingCartCount = makeElement("span");
  const destino = makeElement("button");
  destino.rect = { left: 900, top: 10, width: 50, height: 50 };
  floatingCartCount.parentElement = destino;

  const img = makeElement("img");
  img.rect = { left: 100, top: 200, width: 120, height: 120 };

  const card = makeElement("div");
  card.querySelector = (sel) => (caso.sinImagen ? null : img);

  const sandbox = {
    console,
    events: eventos,
    requestAnimationFrame: (fn) => fn(),
    document: {
      body,
      getElementById: (id) =>
        ({ cartItemCount, floatingCartCount })[id] || null,
      querySelector: () => destino,
    },
  };
  sandbox.window = {
    innerWidth: caso.movil ? 500 : 1400,
    matchMedia: (q) => ({ matches: q.includes("reduce") && caso.reduced }),
    setTimeout: (fn) => {
      // Se ejecuta al instante: simula el final del vuelo.
      fn();
      return 0;
    },
    document: sandbox.document,
  };

  vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(SRC, "utf8"), sandbox);

  const resultado = sandbox.window.flyToCart(
    caso.sinTarjeta ? null : card,
    caso.opciones,
  );

  return { resultado, eventos, body, cartItemCount, floatingCartCount, card, destino };
}

console.log("fly-to-cart.js — prueba de comportamiento\n");

console.log("1. Usuario que pidió menos movimiento");
{
  const r = run({ reduced: true });
  check("no vuela", r.resultado === false);
  check("no se agrega ningún clon al body", r.eventos.length === 0);
  check("no rebota el contador", r.cartItemCount.classList._all().length === 0);
}

console.log("\n2. Sin tarjeta de origen");
{
  const r = run({ sinTarjeta: true });
  // Sin tarjeta: el contador igual rebota (la retroalimentación mínima).
  check("devuelve false", r.resultado === false);
  check("no vuela", r.eventos.length === 0);
  check("rebota el contador del carrito", r.cartItemCount.classList.contains("cart-bounce"));
}

console.log("\n3. Platillo sin foto");
{
  const r = run({ sinImagen: true });
  check("no vuela", r.resultado === false);
  check("pulsa la tarjeta de origen", r.card.classList.contains("card-add-pulse"));
  check("rebota el contador", r.cartItemCount.classList.contains("cart-bounce"));
}

console.log("\n4. Camino feliz (escritorio)");
{
  const r = run({});
  check("vuela: devuelve true", r.resultado === true);
  check("el clon llegó al body", r.eventos.includes("append"));
  check("el clon se retira al llegar", r.body.children.every((c) => c.removed > 0));
  check("rebota el contador del carrito", r.cartItemCount.classList.contains("cart-bounce"));
  check("rebota el contador flotante", r.floatingCartCount.classList.contains("cart-bounce"));
  check("pulsa la tarjeta de origen", r.card.classList.contains("card-add-pulse"));
}

console.log("\n5. Destino explícito y contadores personalizados");
{
  const r = run({ opciones: { dest: "#cart-sidebar .sticky", counters: ["cartItemCount"] } });
  check("vuela con destino indicado", r.resultado === true);
  check("solo rebota el contador indicado", r.floatingCartCount.classList._all().length === 0);
}

console.log(
  "\n" + (fallos === 0 ? "TODO OK" : fallos + " comprobación(es) fallaron"),
);
process.exit(fallos === 0 ? 0 : 1);
