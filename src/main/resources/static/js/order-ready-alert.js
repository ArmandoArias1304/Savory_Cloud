/**
 * ========================================
 * AVISO DE "PEDIDO LISTO PARA RECOGER"
 * ========================================
 *
 * Cuando la cocina, la barra o la parrilla marcan un pedido como listo, el
 * servidor recalcula el estado y lo avisa por WebSocket. Esta pieza decide si
 * ese aviso es TUYO y, si lo es, lo pone en pantalla como una alerta que
 * parpadea y suena, con un botón "Entendido" para cerrarla.
 *
 * Comprueba dos cosas antes de molestar a nadie:
 *
 *  1. Que el aviso sea un cambio de estado que dejó el pedido en LISTO.
 *     No reacciona a las fotos de ítems (que llegan con el pedido sin
 *     cambiar de estado) ni a los pedidos que ya estaban listos antes de
 *     entrar a la pantalla: solo al momento en que se vuelve listo.
 *
 *  2. Que el pedido sea de quien está mirando la pantalla:
 *
 *       · MESERO: el pedido que atiende AHORA. Es la misma regla que usa la
 *         lista para mostrarle sus botones (Order.isOperatedBy), así que si
 *         el pedido se traspasó, el aviso le llega a quien lo va a recoger y
 *         no a quien lo levantó y ya no lo atiende.
 *       · ADMIN/GERENTE y CAJERO: solo los que ellos levantaron. El cajero
 *         tiene delante el tablero global y no debe recibir avisos de esos
 *         pedidos, que no son suyos.
 *
 * El sonido vive en static/sounds/notification.mp3 (el mismo que ya usa la
 * cocina) y suena UNA vez, al aparecer la alerta.
 *
 * Uso, en una lista de pedidos:
 *
 *   <script th:src="@{/js/order-ready-alert.js}"></script>
 *   <script>
 *     OrderReadyAlert.init({ username: "...", role: "waiter" });
 *   </script>
 *
 * Y dentro del manejador del WebSocket que la página ya tiene:
 *
 *   OrderReadyAlert.handle(notification);
 */
(function () {
  "use strict";

  var STYLE_ID = "order-ready-alert-styles";
  var DEFAULT_SOUND = "/sounds/notification.mp3";
  var READY = "READY";
  var STATUS_CHANGE = "STATUS_CHANGE";

  var settings = {
    username: "",
    role: "",
    soundUrl: DEFAULT_SOUND,
  };

  /** Pedidos ya avisados en esta sesión de pantalla (para no repetir el aviso). */
  var alerted = {};

  // Una sola instancia de audio: el navegador solo deja sonar después de la primera
  // interacción del usuario, así que se "desbloquea" con el primer clic y luego se reutiliza.
  var sound = null;
  var soundUnlocked = false;

  // ---------- estilos ----------

  function ensureStyles() {
    if (document.getElementById(STYLE_ID)) {
      return;
    }

    var style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent =
      /* La alerta parpadea hasta que le den "Entendido": el halo late y el título titila. */ "" +
      "@keyframes order-ready-pulse {" +
      "  0%, 100% { box-shadow: 0 0 0 0 rgba(56, 224, 123, 0.55); }" +
      "  50% { box-shadow: 0 0 0 16px rgba(56, 224, 123, 0); }" +
      "}" +
      "@keyframes order-ready-blink {" +
      "  0%, 100% { opacity: 1; }" +
      "  50% { opacity: 0.35; }" +
      "}" +
      /*
       * El latido va SOLO mientras la alerta está en pantalla. SweetAlert se lleva la alerta con
       * SU propia animación (la clase swal2-hide) sobre este mismo elemento: si el latido siguiera
       * puesto, ganaría la cascada y la animación de cierre no correría nunca — la alerta se
       * quedaría pegada en pantalla, con el botón apagado y la capa de encima bloqueando todo. Por
       * eso el latido no aplica cuando la clase de cierre está puesta.
       */
      ".swal2-popup.order-ready-popup:not(.swal2-hide) { animation: order-ready-pulse 1.1s ease-in-out infinite; }" +
      ".order-ready-popup { border: 2px solid #38e07b; }" +
      ".order-ready-title { animation: order-ready-blink 1.1s steps(1, end) infinite; }" +
      /* Quien pidió reducir el movimiento en su sistema no debería ver nada latiendo.
         Misma especificidad que la regla del latido, y va después: así es esta la que manda. */
      "@media (prefers-reduced-motion: reduce) {" +
      "  .swal2-popup.order-ready-popup:not(.swal2-hide), .order-ready-title { animation: none; }" +
      "}";
    document.head.appendChild(style);
  }

  /**
   * Las clases de la alerta, sobre las del modo oscuro que ya usa la página: se añaden a las
   * suyas en vez de reemplazarlas, para no perder el tema al parpadear.
   */
  function swalClasses() {
    var base =
      typeof window.getSwalDarkModeClasses === "function"
        ? window.getSwalDarkModeClasses() || {}
        : {};

    var classes = {};
    Object.keys(base).forEach(function (key) {
      classes[key] = base[key];
    });

    classes.popup = (classes.popup ? classes.popup + " " : "") + "order-ready-popup";
    classes.title = (classes.title ? classes.title + " " : "") + "order-ready-title";
    return classes;
  }

  // ---------- sonido ----------

  function audio() {
    if (!sound) {
      try {
        sound = new Audio(settings.soundUrl);
        sound.volume = 0.6;
      } catch (error) {
        sound = null;
      }
    }
    return sound;
  }

  /** Una sola vez: el aviso suena al aparecer y no vuelve a sonar hasta que lo cierren. */
  function playSound() {
    var player = audio();
    if (!player) {
      return false;
    }
    try {
      player.currentTime = 0;
      player.play().catch(function () {
        // El navegador bloqueó el audio (todavía no hubo ninguna interacción en la página):
        // el aviso visual sigue estando, que es lo que no se puede perder.
      });
      return true;
    } catch (error) {
      return false;
    }
  }

  /**
   * El primer clic del usuario deja el audio listo para sonar (política de autoplay de los
   * navegadores). Sin esto, el primer pedido listo de una pestaña recién abierta se vería pero
   * podría no oírse.
   */
  function unlockSoundOnFirstGesture() {
    var unlock = function () {
      if (soundUnlocked) {
        return;
      }
      soundUnlocked = true;
      var player = audio();
      if (!player) {
        return;
      }
      try {
        player.muted = true;
        var attempt = player.play();
        var finish = function () {
          player.pause();
          player.currentTime = 0;
          player.muted = false;
        };
        if (attempt && attempt.then) {
          attempt.then(finish).catch(function () {
            player.muted = false;
          });
        } else {
          finish();
        }
      } catch (error) {
        player.muted = false;
      }
    };

    document.addEventListener("click", unlock);
    document.addEventListener("keydown", unlock);
  }

  // ---------- ¿es mío? ----------

  function sameUser(left, right) {
    return (
      !!left &&
      !!right &&
      String(left).toLowerCase() === String(right).toLowerCase()
    );
  }

  /** Espeja Order.isOperatedBy: manda el mesero asignado; si no hay, el que levantó el pedido. */
  function isOperatedByMe(notification) {
    if (notification.operatedBy) {
      return sameUser(notification.operatedBy, settings.username);
    }
    return sameUser(notification.createdBy, settings.username);
  }

  function isMine(notification) {
    if (settings.role === "waiter") {
      return isOperatedByMe(notification);
    }
    return sameUser(notification.createdBy, settings.username);
  }

  // ---------- el aviso ----------

  function isOrderReady(notification) {
    return !!notification && notification.status === READY;
  }

  function escapeHtml(text) {
    return String(text)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  /**
   * De qué pedido se trata: la mesa cuando la hay y, si no, el tipo rotulado por el servidor
   * ("Entrega a domicilio", "Para pasar a recoger"). Un pedido de reparto no tiene mesa: su tipo
   * es lo único que lo identifica, y es lo que la persona que lo va a sacar necesita leer.
   */
  function where(notification) {
    if (notification.tableNumber != null && notification.tableNumber !== "") {
      return "Mesa " + notification.tableNumber;
    }
    return notification.orderTypeLabel || "Para recoger";
  }

  /** Cómo termina el aviso, según lo que haya que hacer con el pedido. */
  function closingLine(notification) {
    switch (notification.orderType) {
      case "TAKEOUT":
        return "Ya está listo: el cliente puede pasar a recogerlo.";
      case "DELIVERY":
        return "Ya está listo para salir a reparto.";
      default:
        return "Ya está listo para pasar a recoger.";
    }
  }

  function detailHtml(notification) {
    return (
      '<p class="text-lg font-bold">Pedido ' +
      escapeHtml(notification.orderNumber || "") +
      "</p>" +
      '<p class="text-base mt-1">' +
      escapeHtml(where(notification)) +
      "</p>" +
      '<p class="text-sm mt-2 font-semibold text-emerald-600 dark:text-emerald-400">' +
      escapeHtml(closingLine(notification)) +
      "</p>"
    );
  }

  /**
   * Retira la capa de una alerta que se quedó en pantalla ya cerrada. En ese estado la capa no se
   * ve, pero se come todos los clics: la pantalla queda muerta sin poder hacer nada. La regla del
   * latido ya evita que pase; esto es la red de seguridad, por si otra versión de SweetAlert se
   * lleva la alerta de otra manera. Solo retira SU capa, nunca la de otra alerta de la página.
   */
  function removeStuckAlert() {
    var containers = document.querySelectorAll(".swal2-container");
    for (var i = 0; i < containers.length; i++) {
      if (containers[i].querySelector(".swal2-popup.order-ready-popup.swal2-hide")) {
        containers[i].parentNode.removeChild(containers[i]);
      }
    }
  }

  function show(notification) {
    ensureStyles();

    var swal = window.Swal;
    if (!swal || typeof swal.fire !== "function") {
      // Sin SweetAlert en la página no hay alerta posible: mejor no romper el resto.
      return false;
    }

    // El sonido va antes de abrir: el aviso visual no debe esperar a que el audio arranque.
    playSound();

    var closing = swal.fire({
      icon: "success",
      title: "¡Pedido listo!",
      html: detailHtml(notification),
      confirmButtonText: "Entendido",
      confirmButtonColor: "#38e07b",
      // Se cierra solo con "Entendido": es un aviso que hay que reconocer, no un adorno.
      allowOutsideClick: false,
      allowEscapeKey: false,
      customClass: swalClasses(),
    });

    // Al cerrarla se comprueba, un momento después, que su capa se haya ido de verdad: si se
    // quedara puesta dejaría la pantalla bloqueada y sin poder tocar nada.
    if (closing && typeof closing.then === "function") {
      closing.then(function () {
        setTimeout(removeStuckAlert, 700);
      });
    }

    return true;
  }

  // ---------- API ----------

  function init(options) {
    var opts = options || {};
    settings.username = opts.username || "";
    settings.role = (opts.role || "").toLowerCase();
    settings.soundUrl = opts.soundUrl || DEFAULT_SOUND;
    ensureStyles();
    unlockSoundOnFirstGesture();
  }

  /**
   * Recibe el aviso del WebSocket y muestra la alerta solo si el pedido acaba de quedar listo y
   * es de quien está mirando la pantalla.
   *
   * @return true si se mostró la alerta
   */
  function handle(notification) {
    if (!notification || notification.notificationType !== STATUS_CHANGE) {
      // Las fotos de ítems y los avisos de otra clase no dicen "acabo de quedar listo".
      return false;
    }

    var orderId = String(notification.orderId);
    if (!isOrderReady(notification)) {
      // El pedido se movió de LISTO: si vuelve a estarlo (le agregaron más cosas), es otra
      // ronda y hay que volver a avisar.
      delete alerted[orderId];
      return false;
    }

    if (!isMine(notification)) {
      return false;
    }

    if (alerted[orderId]) {
      return false;
    }

    // Se marca ANTES de mostrar: si el servidor repitiera el aviso, no vuelve a saltar.
    alerted[orderId] = true;
    return show(notification);
  }

  window.OrderReadyAlert = {
    init: init,
    handle: handle,
    isMine: isMine,
    hasAlerted: function (orderId) {
      return alerted[String(orderId)] === true;
    },
    /** Solo para pruebas y para la página de demostración. */
    reset: function () {
      alerted = {};
    },
  };
})();
