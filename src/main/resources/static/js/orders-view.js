/**
 * ========================================
 * VISTA DE TABLA / TARJETAS PARA PEDIDOS
 * ========================================
 *
 * Dos formas de mirar la misma lista de pedidos: la tabla de siempre y
 * las tarjetas, pensadas para leer de un vistazo desde una tablet o el
 * teléfono. La elección se guarda en localStorage y se recuerda entre
 * visitas.
 *
 * Las tarjetas NO se re-renderizan desde el servidor: se construyen a
 * partir de la fila de la tabla, que sigue siendo la única fuente de
 * verdad. Así los botones, los colores del estado y los datos son los
 * mismos en las dos vistas, y no hay dos plantillas que se puedan
 * desincronizar.
 *
 * A diferencia de la primera versión (la del cajero), aquí la fila NO se
 * lee por posición de columna: cada dato viaja en un atributo data-order-*
 * escrito por el servidor. Con columnas distintas por rol —el mesero pone
 * Propina antes de Pago— leer por índice acaba mostrando un dato en el
 * sitio de otro.
 *
 * Uso:
 *   <script th:src="@{/js/orders-view.js}"></script>
 *   OrdersView.init({
 *     viewKey: "adminOrdersView",
 *     sections: {
 *       main: {
 *         table: "ordersTableView",
 *         wrapper: "ordersGridView",
 *         container: "ordersGridContainer",
 *         empty: "ordersGridEmpty",
 *       },
 *     },
 *     // Opcional: si la página sabe abrir un panel lateral, lo usa; si
 *     // no, la tarjeta navega a data-order-href.
 *     onOpenDetail: function (orderId) { ... },
 *     // Opcional: engancha los botones clonados (si la página los enlaza
 *     // por JavaScript, los del clon nacen sin listeners).
 *     bindActions: function (card) { ... },
 *     // Opcional: al pulsar una tarjeta se abre el modal de detalle, con
 *     // el cuerpo que sirve el servidor ({role} = admin | waiter) y los
 *     // MISMOS botones de la fila de ese rol.
 *     detailPanel: { role: "admin" },
 *   });
 *
 * Atributos que lee de cada fila (todos opcionales salvo data-order-id):
 *   data-order-id,  data-order-number, data-order-when,   data-order-table,
 *   data-order-type, data-order-type-label, data-order-customer,
 *   data-order-payment, data-order-status, data-order-total,
 *   data-order-taken-by, data-order-created-by, data-order-tip,
 *   data-order-accounts, data-order-href
 */
(function () {
  "use strict";

  /** Caja de la cabecera cuando el pedido no tiene mesa. */
  var TYPE_BOX = {
    DINE_IN: {
      caption: "",
      icon: "table_restaurant",
      style: "bg-gray-100 dark:bg-gray-800 text-gray-400 dark:text-gray-500",
    },
    DELIVERY: {
      caption: "Domicilio",
      icon: "local_shipping",
      style:
        "bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400",
    },
    TAKEOUT: {
      caption: "Recoger",
      icon: "shopping_bag",
      style:
        "bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400",
    },
  };

  var settings = {
    viewKey: "ordersView",
    sections: {},
    onOpenDetail: null,
    bindActions: null,
    // Sin rol no hay modal: las tarjetas navegan a data-order-href.
    detailPanel: { role: "" },
  };

  // ---------- utilidades ----------

  function el(tag, className, text) {
    var node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text !== undefined && text !== null) {
      node.textContent = text;
    }
    return node;
  }

  /** Lee un atributo data-order-* de la fila, siempre como texto limpio. */
  function attr(row, name) {
    var value = row.getAttribute("data-order-" + name);
    return value === null ? "" : value.trim();
  }

  function storedView() {
    try {
      return localStorage.getItem(settings.viewKey) === "grid"
        ? "grid"
        : "table";
    } catch (error) {
      // Sin almacenamiento (modo privado): la vista vive solo en memoria.
      return "table";
    }
  }

  // ---------- cambio de vista ----------

  function markButtons(view) {
    document.querySelectorAll(".orders-view-btn").forEach(function (button) {
      var isActive = button.getAttribute("data-orders-view") === view;
      button.classList.toggle("bg-white", isActive);
      button.classList.toggle("dark:bg-gray-700", isActive);
      button.classList.toggle("text-primary", isActive);
      button.classList.toggle("shadow-sm", isActive);
      button.classList.toggle("text-gray-600", !isActive);
      button.classList.toggle("dark:text-gray-400", !isActive);
      button.setAttribute("aria-pressed", isActive ? "true" : "false");
    });
  }

  function applyView() {
    var view = storedView();

    Object.keys(settings.sections).forEach(function (scope) {
      var section = settings.sections[scope];
      var table = document.getElementById(section.table);
      var wrapper = document.getElementById(section.wrapper);
      if (table) {
        table.classList.toggle("hidden", view === "grid");
      }
      if (wrapper) {
        wrapper.classList.toggle("hidden", view !== "grid");
      }
    });

    markButtons(view);

    if (view === "grid") {
      renderCards();
    }
  }

  function switchView(view) {
    try {
      localStorage.setItem(settings.viewKey, view === "grid" ? "grid" : "table");
    } catch (error) {
      // Sin almacenamiento: se aplica igual, solo no se recuerda.
    }
    applyView();
  }

  // ---------- tarjetas ----------

  /**
   * Cuentas por persona de un pedido: son las filas .payments-detail que
   * el fragmento de tickets por persona deja debajo de la fila del pedido.
   */
  function splitAccountCount(row) {
    var count = 0;
    var sibling = row.nextElementSibling;
    while (sibling && sibling.tagName === "TR") {
      if (sibling.classList.contains("payments-detail")) {
        count++;
      } else if (!sibling.querySelector(".payments-toggle")) {
        break;
      }
      sibling = sibling.nextElementSibling;
    }
    return count;
  }

  /** Los botones de la fila se copian tal cual: son los mismos del servidor. */
  function cloneActions(row) {
    var cell = row.querySelector(".order-actions-cell");
    if (!cell) {
      return null;
    }
    var source = cell.querySelector("div") || cell;
    var clone = source.cloneNode(true);

    // Un id duplicado rompería las etiquetas y los anclajes del original:
    // en la tarjeta se trabaja por clase, así que no hace falta.
    if (clone.removeAttribute) {
      clone.removeAttribute("id");
    }
    clone.querySelectorAll("[id]").forEach(function (node) {
      node.removeAttribute("id");
    });

    return clone;
  }

  function buildCard(row) {
    var orderId = attr(row, "id");
    if (!orderId) {
      return null;
    }

    var number = attr(row, "number");
    var card = el(
      "article",
      "order-card group cursor-pointer rounded-2xl border border-gray-200 dark:border-gray-800 " +
        "bg-white dark:bg-gray-900 shadow-md transition-all hover:-translate-y-0.5 hover:shadow-lg overflow-hidden",
    );
    card.setAttribute("data-order-id", orderId);
    card.setAttribute("tabindex", "0");
    card.setAttribute("role", "button");
    card.setAttribute("aria-label", "Ver el detalle del pedido " + number);

    // ---- Cabecera: mesa (o tipo) y número de pedido, que es lo primero
    // que se busca; estado y fecha en una línea secundaria para dejarle
    // todo el ancho al número.
    var header = el("div", "flex items-start gap-3 px-4 pt-4");

    var tableNumber = attr(row, "table").replace(/[^0-9]/g, "");
    var typeCode = attr(row, "type");
    var typeBox = TYPE_BOX[typeCode] || TYPE_BOX.DINE_IN;

    var box = el(
      "div",
      "h-14 w-14 shrink-0 rounded-xl flex flex-col items-center justify-center " +
        (tableNumber
          ? "bg-primary/10 dark:bg-primary/20 text-primary"
          : typeBox.style),
    );
    box.title = tableNumber ? "Mesa " + tableNumber : attr(row, "type-label");

    var captionText = tableNumber ? "Mesa" : typeBox.caption;
    if (captionText) {
      box.appendChild(
        el("span", "text-[8px] uppercase tracking-wide font-bold", captionText),
      );
    }
    if (tableNumber) {
      box.appendChild(
        el("span", "text-xl font-extrabold leading-none tabular-nums", tableNumber),
      );
    } else {
      box.appendChild(
        el("span", "material-symbols-outlined text-xl leading-none", typeBox.icon),
      );
    }

    var heading = el("div", "min-w-0 flex-1");
    heading.appendChild(
      el(
        "p",
        "font-mono text-lg sm:text-xl font-extrabold leading-tight text-gray-900 dark:text-white break-all",
        number,
      ),
    );

    var when = el(
      "p",
      "mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-gray-500 dark:text-gray-400",
    );
    when.appendChild(el("span", null, attr(row, "when")));

    // El estado se copia del servidor para no duplicar aquí la tabla de
    // colores por estado: la que manda es la de la plantilla.
    var badgeSource = row.querySelector(".status-badge");
    if (badgeSource) {
      var badge = badgeSource.cloneNode(true);
      badge.classList.remove("px-3", "py-1", "text-xs");
      badge.classList.add("px-2", "py-0.5", "text-[10px]");
      when.appendChild(badge);
    }
    heading.appendChild(when);

    header.appendChild(box);
    header.appendChild(heading);

    // ---- Tipo, cliente, pago y total.
    var middle = el("div", "px-4 pt-3 flex items-end justify-between gap-3");
    var meta = el("div", "min-w-0 text-xs text-gray-600 dark:text-gray-400");
    meta.appendChild(
      el(
        "p",
        "truncate",
        attr(row, "type-label") + " · " + (attr(row, "customer") || "Público general"),
      ),
    );

    var paymentSource = row.querySelector(".payment-pill");
    if (paymentSource) {
      var paymentPill = paymentSource.cloneNode(true);
      paymentPill.classList.remove("px-3", "py-1");
      paymentPill.classList.add("mt-1", "inline-flex", "text-[11px]");
      meta.appendChild(paymentPill);
    } else if (attr(row, "payment")) {
      meta.appendChild(
        el(
          "span",
          "mt-1 inline-flex rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-[11px]",
          attr(row, "payment"),
        ),
      );
    }

    middle.appendChild(meta);
    middle.appendChild(
      el(
        "p",
        "text-lg font-bold text-gray-900 dark:text-white whitespace-nowrap",
        attr(row, "total"),
      ),
    );

    // ---- Pie de datos: quién lo tomó, quién lo cobró, propina, cuentas
    // divididas y el aviso de que hay detalle.
    var info = el(
      "div",
      "px-4 pt-2 pb-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-gray-500 dark:text-gray-400",
    );
    var createdBy = attr(row, "created-by");
    if (createdBy) {
      info.appendChild(el("span", null, "Creado por: " + createdBy));
    }
    var takenBy = attr(row, "taken-by");
    if (takenBy) {
      info.appendChild(el("span", null, "Cobrado por: " + takenBy));
    }
    var tip = attr(row, "tip");
    if (tip) {
      info.appendChild(
        el(
          "span",
          "inline-flex items-center gap-1 rounded-full bg-emerald-50 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300 px-2 py-0.5 font-semibold",
          "Propina " + tip,
        ),
      );
    }

    // El número de cuentas puede venir declarado o deducirse de las filas
    // de tickets por persona, según lo que tenga la pantalla.
    var accountsDeclared = parseInt(attr(row, "accounts"), 10);
    var accounts =
      !isNaN(accountsDeclared) && accountsDeclared > 0
        ? accountsDeclared
        : splitAccountCount(row);
    if (accounts > 0) {
      var accountsChip = el(
        "span",
        "inline-flex items-center gap-1 rounded-full bg-teal-50 dark:bg-teal-900/30 text-teal-700 dark:text-teal-300 px-2 py-0.5 font-semibold",
      );
      accountsChip.appendChild(
        el("span", "material-symbols-outlined !text-xs", "group"),
      );
      accountsChip.appendChild(
        el(
          "span",
          null,
          accounts === 1
            ? "1 cuenta por persona"
            : accounts + " cuentas por persona",
        ),
      );
      info.appendChild(accountsChip);
    }

    if (settings.onOpenDetail || settings.detailPanel.role || attr(row, "href")) {
      info.appendChild(el("span", "ml-auto font-semibold text-primary", "Ver detalle"));
    }

    // ---- Acciones: los MISMOS botones de la fila.
    var footer = el(
      "div",
      "order-card-actions border-t border-gray-100 dark:border-gray-800 px-2 py-2 flex flex-wrap items-center justify-center gap-y-1",
    );
    var actions = cloneActions(row);
    if (actions) {
      footer.appendChild(actions);
    }

    card.appendChild(header);
    card.appendChild(middle);
    card.appendChild(info);
    card.appendChild(footer);

    // Los botones clonados nacen sin listeners: la página los vuelve a
    // enganchar con bindActions.
    if (settings.bindActions) {
      settings.bindActions(card);
    }

    function open() {
      // El modal es lo primero: el detalle completo, sin salir de la lista.
      if (settings.detailPanel.role) {
        openDetailPanel(orderId);
        return;
      }
      if (settings.onOpenDetail) {
        settings.onOpenDetail(orderId);
        return;
      }
      var href = attr(row, "href");
      if (href) {
        window.location.href = href;
      }
    }

    // La tarjeta abre el detalle, salvo cuando el clic fue sobre un botón
    // o un enlace: esos siguen su propio camino.
    card.addEventListener("click", function (event) {
      if (event.target.closest("a, button")) {
        return;
      }
      open();
    });
    card.addEventListener("keydown", function (event) {
      if (event.key !== "Enter" && event.key !== " ") {
        return;
      }
      event.preventDefault();
      open();
    });

    return card;
  }

  function renderCards() {
    // En vista de tabla no hay nada que construir: las tarjetas se generan
    // al cambiar de vista, con las filas ya actualizadas.
    if (storedView() !== "grid") {
      return;
    }

    Object.keys(settings.sections).forEach(function (scope) {
      var section = settings.sections[scope];
      var table = document.getElementById(section.table);
      var container = document.getElementById(section.container);
      var emptyState = document.getElementById(section.empty);
      if (!table || !container) {
        return;
      }

      var cards = [];
      table.querySelectorAll("tr[data-order-id]").forEach(function (row) {
        var card = buildCard(row);
        if (card) {
          cards.push(card);
        }
      });

      container.replaceChildren.apply(container, cards);
      if (emptyState) {
        emptyState.classList.toggle("hidden", cards.length > 0);
      }
    });
  }

  // ---------- modal de detalle ----------
  //
  // Al pulsar una tarjeta se abre el detalle del pedido sin salir de la lista. El cuerpo lo
  // sirve el servidor (fragments/order-detail-panel) y los botones que van dentro son los de
  // la fila que se pulsó: esta pantalla ya sabe qué botones le tocan a su rol, así que se
  // copian de ahí en vez de escribirlos otra vez aquí.

  var detailPanelOrderId = null;

  function detailPanelSpinner() {
    return (
      '<div class="flex items-center justify-center py-12 text-gray-400">' +
      '<span class="material-symbols-outlined animate-spin text-3xl">progress_activity</span>' +
      "</div>"
    );
  }

  /**
   * Error del panel con el motivo a la vista: un 404 (el endpoint no existe en el proceso que
   * está corriendo), un 500 (el fragmento no se pudo pintar) o un fallo de red se distinguen
   * de un vistazo, sin abrir la consola.
   */
  function detailPanelError(error) {
    var container = el("div", "py-10 text-center");
    container.appendChild(
      el(
        "p",
        "text-sm font-semibold text-gray-700 dark:text-gray-200",
        "No se pudo cargar el detalle del pedido",
      ),
    );
    container.appendChild(
      el(
        "p",
        "mt-1 text-xs text-gray-500 dark:text-gray-400",
        (error && error.message ? error.message : "error desconocido") +
          ' · el enlace "Ver pedido completo" sigue disponible',
      ),
    );
    return container;
  }

  /**
   * Los botones de la fila, dentro de la caja de acciones del panel: el mismo aspecto que
   * tienen en el modal del cajero, para que las tres pantallas se lean igual.
   */
  function rowActionsBox(orderId) {
    var row = document.querySelector('tr[data-order-id="' + orderId + '"]');
    if (!row) {
      return null;
    }

    var actions = cloneActions(row);
    if (!actions) {
      return null;
    }

    var box = el(
      "div",
      "order-detail-actions-box rounded-xl border border-gray-200 dark:border-gray-800 " +
        "bg-gray-50/60 dark:bg-gray-800/30 px-3 py-3 sm:px-4 sm:py-4",
    );
    box.appendChild(
      el(
        "p",
        "text-[11px] uppercase tracking-wide font-semibold text-gray-400 dark:text-gray-500 mb-3",
        "Acciones del pedido",
      ),
    );
    box.appendChild(actions);
    return box;
  }

  function openDetailPanel(orderId) {
    var modal = document.getElementById("orderDetailModal");
    if (!modal) {
      // Sin modal en la página, el enlace de siempre antes que quedarse sin hacer nada.
      var row = document.querySelector('tr[data-order-id="' + orderId + '"]');
      var href = row ? attr(row, "href") : "";
      if (href) {
        window.location.href = href;
      }
      return;
    }

    detailPanelOrderId = String(orderId);

    var fullLink = document.getElementById("orderDetailFullLink");
    if (fullLink) {
      fullLink.setAttribute(
        "href",
        "/" + settings.detailPanel.role + "/orders/view/" + orderId,
      );
    }

    modal.classList.remove("hidden");
    document.body.classList.add("overflow-hidden");
    loadDetailPanel(orderId);
  }

  function closeDetailPanel() {
    var modal = document.getElementById("orderDetailModal");
    if (modal) {
      modal.classList.add("hidden");
    }
    document.body.classList.remove("overflow-hidden");
    detailPanelOrderId = null;
  }

  function loadDetailPanel(orderId) {
    var body = document.getElementById("orderDetailModalBody");
    if (!body) {
      return Promise.resolve(false);
    }

    body.innerHTML = detailPanelSpinner();
    return fetch(
      "/" + settings.detailPanel.role + "/orders/" + orderId + "/detail-panel",
      { headers: { "X-Requested-With": "XMLHttpRequest" } },
    )
      .then(function (response) {
        if (!response.ok) {
          throw new Error("HTTP " + response.status);
        }
        return response.text();
      })
      .then(function (html) {
        body.innerHTML = html;

        // El panel de este rol llega SIN botones a propósito (los suyos traen reglas que el
        // servidor de la lista no conoce, como la transferencia del mesero): se le añaden los
        // de la fila y se vuelven a enganchar, porque los copiados nacen sin listeners.
        var box = rowActionsBox(orderId);
        if (box) {
          body.appendChild(box);
        }
        if (settings.bindActions) {
          settings.bindActions(body);
        }
        return true;
      })
      .catch(function (error) {
        console.error("No se pudo cargar el detalle del pedido:", error);
        body.replaceChildren(detailPanelError(error));
        return false;
      });
  }

  /**
   * El pedido puede cambiar mientras el modal está abierto (cocina, reparto, otro empleado):
   * solo se vuelve a pedir al servidor si el detalle abierto es el de ESE pedido.
   */
  function refreshDetailPanel(orderId) {
    if (detailPanelOrderId === null) {
      return;
    }
    if (String(orderId) !== detailPanelOrderId) {
      return;
    }
    loadDetailPanel(orderId);
  }

  // ---------- arranque ----------

  function init(options) {
    var opts = options || {};
    settings.viewKey = opts.viewKey || settings.viewKey;
    settings.sections = opts.sections || {};
    settings.onOpenDetail = opts.onOpenDetail || null;
    settings.bindActions = opts.bindActions || null;
    settings.detailPanel = opts.detailPanel || { role: "" };

    document.querySelectorAll(".orders-view-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        switchView(button.getAttribute("data-orders-view"));
      });
    });

    if (settings.detailPanel.role) {
      document.addEventListener("click", function (event) {
        if (
          event.target.closest &&
          event.target.closest("[data-order-detail-close]")
        ) {
          closeDetailPanel();
        }
      });
      document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && detailPanelOrderId !== null) {
          closeDetailPanel();
        }
      });
    }

    applyView();
  }

  window.OrdersView = {
    init: init,
    apply: applyView,
    render: renderCards,
    buildCard: buildCard,
    switchView: switchView,
    currentView: storedView,
    splitAccountCount: splitAccountCount,
    openDetailPanel: openDetailPanel,
    closeDetailPanel: closeDetailPanel,
    refreshDetailPanel: refreshDetailPanel,
    isDetailPanelOpen: function () {
      return detailPanelOrderId !== null;
    },
  };
})();
