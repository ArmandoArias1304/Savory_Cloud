/**
 * ========================================
 * VISTA DE TABLA / TARJETAS PARA VENTAS
 * ========================================
 *
 * El historial de ventas se puede mirar como la tabla de siempre o como
 * tarjetas, igual que los pedidos, para leer un cobro de un vistazo desde
 * una tablet o el teléfono. La elección se guarda en localStorage y se
 * recuerda entre visitas.
 *
 * Las tarjetas NO se re-renderizan desde el servidor: se construyen a
 * partir de la fila de la tabla, que sigue siendo la única fuente de
 * verdad. Así los montos, los estados de facturación y los botones son
 * los mismos en las dos vistas, y no hay dos plantillas que se puedan
 * desincronizar (por ejemplo, al cambiar cómo se reparte la propina
 * cuando se filtra por método de pago).
 *
 * Cada dato viaja en un atributo data-sale-* escrito por el servidor, no
 * por posición de columna: la tabla de ventas tiene columnas que cambian
 * con el filtro (Subtotal vs. Monto del método) y leer por índice acaba
 * mostrando un dato en el sitio de otro.
 *
 * Uso, al final de la página:
 *   <script th:src="@{/js/sales-view.js}"></script>
 *   <script>SalesView.init({ viewKey: "salesView" });</script>
 *
 * Atributos que lee de cada fila (todos opcionales salvo data-sale-id):
 *   data-sale-id,        data-sale-number,   data-sale-when,
 *   data-sale-type,      data-sale-type-label,
 *   data-sale-payment,   data-sale-paid-by,  data-sale-waiter,
 *   data-sale-subtotal,  data-sale-subtotal-label,
 *   data-sale-tip,       data-sale-total,    data-sale-href
 *
 * Los estados de facturación y los botones se copian de la fila
 * (.sale-invoice-badge, .sale-invoice-note, .sale-actions-cell): sus
 * colores y sus reglas los decide el servidor, aquí no se reescriben.
 */
(function () {
  "use strict";

  /** Caja de la cabecera: el tipo de venta, con los colores de la tabla. */
  var TYPE_BOX = {
    DINE_IN: {
      caption: "",
      icon: "table_restaurant",
      style:
        "bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400",
    },
    TAKEOUT: {
      caption: "Recoger",
      icon: "shopping_bag",
      style:
        "bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400",
    },
    DELIVERY: {
      caption: "Domicilio",
      icon: "local_shipping",
      style:
        "bg-orange-100 dark:bg-orange-900/30 text-orange-600 dark:text-orange-400",
    },
  };

  var settings = {
    viewKey: "salesView",
    table: "salesTableView",
    wrapper: "salesCardsView",
    container: "salesCards",
    empty: "salesCardsEmpty",
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

  /** Lee un atributo data-sale-* de la fila, siempre como texto limpio. */
  function attr(row, name) {
    var value = row.getAttribute("data-sale-" + name);
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
    var table = document.getElementById(settings.table);
    var wrapper = document.getElementById(settings.wrapper);

    if (table) {
      table.classList.toggle("hidden", view === "grid");
    }
    if (wrapper) {
      wrapper.classList.toggle("hidden", view !== "grid");
    }

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

  /** Los botones de la fila se copian tal cual: son los mismos del servidor. */
  function cloneActions(row) {
    var cell = row.querySelector(".sale-actions-cell");
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

  /** Una de las tres cifras del pie: rótulo arriba, monto abajo. */
  function moneyColumn(label, value, valueClass, alignClass) {
    var column = el("div", "min-w-0 " + (alignClass || ""));
    column.appendChild(
      el(
        "p",
        "truncate text-[9px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500",
        label,
      ),
    );
    column.appendChild(
      el(
        "p",
        "truncate text-xs font-bold " + (valueClass || "text-gray-700 dark:text-gray-200"),
        value,
      ),
    );
    return column;
  }

  function buildCard(row) {
    var saleId = attr(row, "id");
    if (!saleId) {
      return null;
    }

    var number = attr(row, "number");
    var card = el(
      "article",
      "sale-card group flex cursor-pointer flex-col rounded-2xl border border-gray-200 dark:border-gray-800 " +
        "bg-white dark:bg-gray-900 shadow-md transition-all hover:-translate-y-0.5 hover:shadow-lg overflow-hidden",
    );
    card.setAttribute("data-sale-id", saleId);
    card.setAttribute("tabindex", "0");
    card.setAttribute("role", "button");
    card.setAttribute("aria-label", "Ver el detalle de la venta " + number);

    // ---- Cabecera: tipo de venta, número de pedido, fecha y facturación.
    var header = el("div", "flex items-start gap-3 px-4 pt-4");

    var typeCode = attr(row, "type");
    var typeBox = TYPE_BOX[typeCode] || TYPE_BOX.DINE_IN;
    var box = el(
      "div",
      "h-14 w-14 shrink-0 rounded-xl flex flex-col items-center justify-center gap-0.5 " +
        typeBox.style,
    );
    box.title = attr(row, "type-label");
    if (typeBox.caption) {
      box.appendChild(
        el(
          "span",
          "text-[8px] uppercase tracking-wide font-bold",
          typeBox.caption,
        ),
      );
    }
    box.appendChild(
      el("span", "material-symbols-outlined text-xl leading-none", typeBox.icon),
    );

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

    // El estado de facturación se copia del servidor para no duplicar aquí
    // sus colores ni sus reglas (facturada, pendiente, cuentas parciales).
    var badgeSource = row.querySelector(".sale-invoice-badge");
    if (badgeSource) {
      var badge = badgeSource.cloneNode(true);
      badge.classList.remove("px-2", "py-1", "text-xs");
      badge.classList.add("px-2", "py-0.5", "text-[10px]");
      when.appendChild(badge);
    }
    heading.appendChild(when);

    header.appendChild(box);
    header.appendChild(heading);

    // ---- Método de pago: un cobro mixto trae cuánto se pagó con cada uno.
    var payment = el(
      "p",
      "px-4 pt-3 text-[11px] leading-snug text-gray-600 dark:text-gray-400",
    );
    payment.appendChild(
      el("span", "font-semibold text-gray-500 dark:text-gray-500", "Pago: "),
    );
    payment.appendChild(el("span", null, attr(row, "payment")));

    // ---- Las tres cifras de la tabla: subtotal (o monto del método),
    // propina y total. Los montos llegan ya formateados por el servidor.
    var money = el(
      "div",
      "mx-4 mt-3 grid grid-cols-3 gap-2 rounded-xl border border-gray-100 dark:border-gray-800 " +
        "bg-gray-50 dark:bg-gray-800/60 px-3 py-2",
    );
    money.appendChild(
      moneyColumn(
        attr(row, "subtotal-label") || "Subtotal",
        attr(row, "subtotal"),
        "text-gray-700 dark:text-gray-200",
      ),
    );
    money.appendChild(
      moneyColumn(
        "Propina",
        attr(row, "tip"),
        "text-gray-700 dark:text-gray-200",
        "text-center",
      ),
    );
    money.appendChild(
      moneyColumn(
        "Total",
        attr(row, "total"),
        "text-primary",
        "text-right",
      ),
    );

    // ---- Quién cobró, quién atendió y el aviso de cuentas facturadas.
    var info = el(
      "div",
      "px-4 pt-2.5 pb-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-gray-500 dark:text-gray-400",
    );
    // El tipo se dice con palabras, no solo con el icono de la cabecera.
    var typeLabel = attr(row, "type-label");
    if (typeLabel) {
      info.appendChild(
        el("span", "font-semibold text-gray-600 dark:text-gray-300", typeLabel),
      );
    }
    var paidBy = attr(row, "paid-by");
    if (paidBy) {
      info.appendChild(el("span", null, "Cobrado por: " + paidBy));
    }
    var waiter = attr(row, "waiter");
    info.appendChild(el("span", null, "Mesero: " + (waiter || "—")));

    var noteSource = row.querySelector(".sale-invoice-note");
    if (noteSource && noteSource.textContent.trim()) {
      info.appendChild(
        el(
          "span",
          "rounded-full bg-amber-50 dark:bg-amber-900/30 px-2 py-0.5 font-semibold text-amber-700 dark:text-amber-300",
          noteSource.textContent.trim(),
        ),
      );
    }

    if (attr(row, "href")) {
      info.appendChild(
        el("span", "ml-auto font-semibold text-primary", "Ver detalle"),
      );
    }

    // ---- Acciones: los MISMOS botones de la fila (ver y generar link).
    var footer = el(
      "div",
      "mt-auto border-t border-gray-100 dark:border-gray-800 px-2 py-2 flex flex-wrap items-center justify-center gap-y-1",
    );
    var actions = cloneActions(row);
    if (actions) {
      footer.appendChild(actions);
    }

    card.appendChild(header);
    card.appendChild(payment);
    card.appendChild(money);
    card.appendChild(info);
    card.appendChild(footer);

    function open() {
      var href = attr(row, "href");
      if (href) {
        window.location.href = href;
      }
    }

    // La tarjeta abre la venta, salvo cuando el clic fue sobre un botón o
    // un enlace: esos siguen su propio camino.
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
    // al cambiar de vista.
    if (storedView() !== "grid") {
      return;
    }

    var table = document.getElementById(settings.table);
    var container = document.getElementById(settings.container);
    var emptyState = document.getElementById(settings.empty);
    if (!table || !container) {
      return;
    }

    var cards = [];
    table.querySelectorAll("tr[data-sale-id]").forEach(function (row) {
      var card = buildCard(row);
      if (card) {
        cards.push(card);
      }
    });

    container.replaceChildren.apply(container, cards);
    if (emptyState) {
      emptyState.classList.toggle("hidden", cards.length > 0);
    }
  }

  // ---------- arranque ----------

  function init(options) {
    var opts = options || {};
    settings.viewKey = opts.viewKey || settings.viewKey;
    settings.table = opts.table || settings.table;
    settings.wrapper = opts.wrapper || settings.wrapper;
    settings.container = opts.container || settings.container;
    settings.empty = opts.empty || settings.empty;

    document.querySelectorAll(".orders-view-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        switchView(button.getAttribute("data-orders-view"));
      });
    });

    applyView();
  }

  window.SalesView = {
    init: init,
    apply: applyView,
    render: renderCards,
    buildCard: buildCard,
    switchView: switchView,
    currentView: storedView,
  };
})();
