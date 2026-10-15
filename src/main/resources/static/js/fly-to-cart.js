/**
 * ========================================
 * VUELO DEL PLATILLO AL CARRITO
 * ========================================
 *
 * Pieza compartida por todos los menús con carrito (cliente, mesero,
 * cajero, admin). Cuando se agrega un platillo, una copia de su imagen
 * viaja hasta el carrito para confirmar de un vistazo QUÉ se agregó y
 * DÓNDE quedó, sin obligar a leer un mensaje.
 *
 * Las clases CSS (.fly-clone, .cart-bounce, .card-add-pulse) viven en
 * /css/theme.css, junto al resto de la librería de movimiento.
 *
 * Uso:
 *   <script th:src="@{/js/fly-to-cart.js}"></script>
 *   flyToCart(cardElement);            // destino detectado solo
 *   flyToCart(cardElement, { dest: "#cart-sidebar .sticky" });
 *
 * Opciones:
 *   dest        selector CSS del destino. Si no se encuentra, o no se
 *               indica, se detecta el carrito de la página.
 *   image       selector de la imagen dentro de la tarjeta (por
 *               defecto "img").
 *   counters    ids de los contadores que rebotan al llegar.
 *   size        tamaño máximo (px) del clon en vuelo.
 *   duration    duración del vuelo en ms.
 *
 * Devuelve true si el clon llegó a volar, false si no había imagen o
 * el usuario pidió menos movimiento (en ambos casos el contador del
 * carrito ya se actualizó, que es la retroalimentación importante).
 */
(function () {
  "use strict";

  var DEFAULT_COUNTERS = ["cartItemCount", "floatingCartCount"];
  var MOBILE_MAX_WIDTH = 1024; // breakpoint lg de Tailwind

  function prefersReducedMotion() {
    return (
      typeof window.matchMedia === "function" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
    );
  }

  /**
   * Encuentra el carrito de la página.
   * Móvil: el botón flotante que contiene al contador.
   * Escritorio: el encabezado del panel lateral del carrito.
   */
  function findDestination(explicitSelector) {
    if (explicitSelector) {
      var explicit = document.querySelector(explicitSelector);
      if (explicit) {
        return explicit;
      }
    }

    var floatingCounter = document.getElementById("floatingCartCount");
    if (floatingCounter && window.innerWidth < MOBILE_MAX_WIDTH) {
      return (
        floatingCounter.closest("button") ||
        floatingCounter.parentElement ||
        floatingCounter
      );
    }

    var cartSidebar = document.getElementById("cart-sidebar");
    if (cartSidebar) {
      return cartSidebar.querySelector(".sticky") || cartSidebar;
    }

    return floatingCounter;
  }

  /** Rebota los contadores del carrito para rematar la llegada. */
  function bounceCounters(ids) {
    (ids || DEFAULT_COUNTERS).forEach(function (id) {
      var el = document.getElementById(id);
      if (!el) {
        return;
      }
      el.classList.remove("cart-bounce");
      void el.offsetWidth; // reflow: reinicia la animación si se repite
      el.classList.add("cart-bounce");
      el.addEventListener(
        "animationend",
        function () {
          el.classList.remove("cart-bounce");
        },
        { once: true },
      );
    });
  }

  /** Pulso verde en la tarjeta de origen: confirma CUÁL se agregó. */
  function pulseSource(card) {
    if (!card) {
      return;
    }
    card.classList.add("card-add-pulse");
    card.addEventListener(
      "animationend",
      function () {
        card.classList.remove("card-add-pulse");
      },
      { once: true },
    );
  }

  /**
   * @param {Element} sourceCard tarjeta del platillo (o cualquier
   *        elemento que contenga la imagen a volar).
   * @param {Object} [options] ver cabecera del archivo.
   * @returns {boolean} si el clon voló de verdad.
   */
  function flyToCart(sourceCard, options) {
    var opts = options || {};
    var counters = opts.counters || DEFAULT_COUNTERS;

    if (prefersReducedMotion()) {
      return false;
    }

    if (!sourceCard) {
      bounceCounters(counters);
      return false;
    }

    var img = sourceCard.querySelector(opts.image || "img");
    if (!img || !img.getBoundingClientRect) {
      // Sin imagen (platillo sin foto): el rebote del contador hace el trabajo.
      bounceCounters(counters);
      pulseSource(sourceCard);
      return false;
    }

    var destination = findDestination(opts.dest);
    if (!destination || !destination.getBoundingClientRect) {
      bounceCounters(counters);
      return false;
    }

    var srcRect = img.getBoundingClientRect();
    var destRect = destination.getBoundingClientRect();
    var size = Math.min(
      srcRect.width,
      srcRect.height,
      opts.size || 70,
    );
    var duration = opts.duration || 650;

    var clone = img.cloneNode();
    clone.className = "fly-clone";
    clone.style.width = size + "px";
    clone.style.height = size + "px";
    clone.style.left = srcRect.left + srcRect.width / 2 - size / 2 + "px";
    clone.style.top = srcRect.top + srcRect.height / 2 - size / 2 + "px";
    document.body.appendChild(clone);

    var destX = destRect.left + destRect.width / 2 - size / 2;
    var destY = destRect.top + destRect.height / 2 - size / 2;

    // requestAnimationFrame para que el navegador registre la posición
    // inicial antes de animar (si no, el clon aparece ya en el destino).
    requestAnimationFrame(function () {
      clone.style.transition = "all " + duration + "ms cubic-bezier(0.25, 0.46, 0.45, 0.94)";
      clone.style.left = destX + "px";
      clone.style.top = destY + "px";
      clone.style.width = "20px";
      clone.style.height = "20px";
      clone.style.opacity = "0.7";
      clone.style.transform = "scale(0.2)";
    });

    window.setTimeout(function () {
      if (clone.parentNode) {
        clone.remove();
      }
      bounceCounters(counters);
      pulseSource(sourceCard);
    }, duration - 50);

    return true;
  }

  window.flyToCart = flyToCart;

  // API para pruebas y ajustes en caliente.
  window.FlyToCart = {
    fly: flyToCart,
    findDestination: findDestination,
    prefersReducedMotion: prefersReducedMotion,
    defaultCounters: DEFAULT_COUNTERS.slice(),
  };
})();
