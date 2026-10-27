/**
 * ========================================
 * VISTA DE TABLA / TARJETAS PARA CLIENTES
 * ========================================
 *
 * Los clientes registrados se pueden mirar como la tabla de siempre o como
 * tarjetas, con la misma forma que las de ventas y pedidos: la elección se
 * guarda en localStorage y se recuerda entre visitas.
 *
 * Las tarjetas NO se pintan en el servidor: se construyen a partir de la
 * fila de la tabla, que sigue siendo la única fuente de verdad. Así el
 * estado, la verificación y los botones son los mismos en las dos vistas,
 * y no hay dos plantillas que se puedan desincronizar.
 *
 * Los filtros de esta pantalla son del navegador (applyFilters oculta
 * filas); por eso render() solo dibuja las filas visibles y la página
 * vuelve a llamarlo cada vez que se filtra.
 *
 * Uso, al final de la página:
 *   <script th:src="@{/js/customers-view.js}"></script>
 *   <script>CustomersView.init({ viewKey: "customersView" });</script>
 *
 * Atributos que lee de cada fila (todos opcionales salvo data-customer-id):
 *   data-customer-id,       data-customer-name,      data-customer-username,
 *   data-customer-email,    data-customer-phone,     data-customer-since,
 *   data-customer-last-access
 *
 * El estado y la verificación se copian de la fila (.customer-status-badge,
 * .customer-verified-badge) y los botones de .customer-actions-cell: sus
 * colores y sus reglas los decide el servidor, aquí no se reescriben.
 */
(function () {
  "use strict";

  var settings = {
    viewKey: "customersView",
    table: "customersTableView",
    wrapper: "customersCardsView",
    container: "customersCards",
    empty: "customersCardsEmpty",
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

  /** Lee un atributo data-customer-* de la fila, siempre como texto limpio. */
  function attr(row, name) {
    var value = row.getAttribute("data-customer-" + name);
    return value === null ? "" : value.trim();
  }

  /**
   * La tabla solo se veía en pantallas grandes, así que en el teléfono la
   * vista natural sigue siendo la de tarjetas; en escritorio, la tabla.
   * Es solo el valor inicial: en cuanto el usuario elige, manda su elección.
   */
  function preferredView() {
    var narrow =
      window.matchMedia && window.matchMedia("(max-width: 1023px)").matches;
    return narrow ? "grid" : "table";
  }

  function storedView() {
    try {
      var value = localStorage.getItem(settings.viewKey);
      if (value === "grid" || value === "table") {
        return value;
      }
      return preferredView();
    } catch (error) {
      // Sin almacenamiento (modo privado): la vista vive solo en memoria.
      return preferredView();
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
    var cell = row.querySelector(".customer-actions-cell");
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

  /** Copia una etiqueta de la fila (estado, verificación) sin sus tamaños de tabla. */
  function cloneBadge(row, selector, extraClasses) {
    var source = row.querySelector(selector);
    if (!source) {
      return null;
    }
    var badge = source.cloneNode(true);
    badge.classList.remove("px-2.5", "py-1", "text-xs");
    badge.classList.add("px-2", "py-0.5", "text-[10px]");
    (extraClasses || []).forEach(function (name) {
      badge.classList.add(name);
    });
    return badge;
  }

  /** Una de las tres cifras del pie: rótulo arriba, dato abajo. */
  function detailColumn(label, value, alignClass) {
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
        "truncate text-xs font-semibold text-gray-700 dark:text-gray-200",
        value,
      ),
    );
    return column;
  }

  function buildCard(row) {
    var customerId = attr(row, "id");
    if (!customerId) {
      return null;
    }

    var name = attr(row, "name");
    var status = attr(row, "status");
    var initial = name ? name.charAt(0).toUpperCase() : "?";

    var card = el(
      "article",
      "customer-card group flex cursor-pointer flex-col rounded-2xl border border-gray-200 dark:border-gray-800 " +
        "bg-white dark:bg-gray-900 shadow-md transition-all hover:-translate-y-0.5 hover:shadow-lg overflow-hidden",
    );
    card.setAttribute("data-customer-id", customerId);
    card.setAttribute("tabindex", "0");
    card.setAttribute("role", "button");
    card.setAttribute("aria-label", "Ver el detalle del cliente " + name);

    // ---- Cabecera: inicial del cliente y su nombre.
    var header = el("div", "flex items-start gap-3 px-4 pt-4");

    var avatar = el(
      "div",
      "h-14 w-14 shrink-0 flex items-center justify-center rounded-xl text-lg font-extrabold text-white shadow-md " +
        (status === "inactive"
          ? "bg-gradient-to-br from-red-400 to-red-600"
          : "bg-gradient-to-br from-primary to-primary-dark"),
      initial,
    );
    avatar.title = status === "inactive" ? "Cliente inactivo" : "Cliente activo";

    var heading = el("div", "min-w-0 flex-1");
    heading.appendChild(
      el(
        "p",
        "truncate text-lg font-bold leading-tight text-gray-900 dark:text-white",
        name,
      ),
    );
    heading.appendChild(
      el(
        "p",
        "mt-0.5 truncate text-[11px] text-gray-500 dark:text-gray-400",
        attr(row, "email") || "Sin correo",
      ),
    );

    // Estado y verificación: las etiquetas que ya pinta el servidor.
    var badges = el("div", "mt-2 flex flex-wrap items-center gap-1.5");
    var statusBadge = cloneBadge(row, ".customer-status-badge");
    if (statusBadge) {
      badges.appendChild(statusBadge);
    }
    var verifiedBadge = cloneBadge(row, ".customer-verified-badge");
    if (verifiedBadge) {
      badges.appendChild(verifiedBadge);
    }
    heading.appendChild(badges);

    header.appendChild(avatar);
    header.appendChild(heading);

    // ---- Teléfono, alta y último acceso: las columnas de fecha de la tabla.
    var details = el(
      "div",
      "mx-4 mt-3 grid grid-cols-3 gap-2 rounded-xl border border-gray-100 dark:border-gray-800 " +
        "bg-gray-50 dark:bg-gray-800/60 px-3 py-2",
    );
    details.appendChild(detailColumn("Teléfono", attr(row, "phone") || "—"));
    details.appendChild(
      detailColumn("Registro", attr(row, "since") || "N/A", "text-center"),
    );
    details.appendChild(
      detailColumn(
        "Último acceso",
        attr(row, "last-access") || "Sin acceso",
        "text-right",
      ),
    );

    // ---- Usuario y la pista de que la tarjeta abre la ficha.
    var info = el(
      "div",
      "px-4 pt-2.5 pb-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-gray-500 dark:text-gray-400",
    );
    info.appendChild(
      el(
        "span",
        "rounded-md bg-gray-100 dark:bg-gray-800 px-2 py-0.5 font-mono",
        "@" + (attr(row, "username") || "—"),
      ),
    );
    info.appendChild(el("span", "ml-auto font-semibold text-primary", "Ver detalle"));

    // ---- Acciones: los MISMOS botones de la fila (ficha y activar/desactivar).
    var footer = el(
      "div",
      "mt-auto border-t border-gray-100 dark:border-gray-800 px-2 py-2 flex flex-wrap items-center justify-center gap-y-1",
    );
    var actions = cloneActions(row);
    if (actions) {
      footer.appendChild(actions);
    }

    card.appendChild(header);
    card.appendChild(details);
    card.appendChild(info);
    card.appendChild(footer);

    function open() {
      // La ficha del cliente ya existe como modal: se abre, no se navega.
      if (typeof window.viewCustomer === "function") {
        window.viewCustomer(customerId);
      }
    }

    // La tarjeta abre la ficha, salvo cuando el clic fue sobre un botón.
    card.addEventListener("click", function (event) {
      if (event.target.closest("button, a")) {
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

  /** ¿La fila la dejó visible el filtro del navegador? */
  function isVisible(row) {
    return row.style.display !== "none";
  }

  function renderCards() {
    // En vista de tabla no hay nada que construir: las tarjetas se generan
    // al cambiar de vista o al filtrar.
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
    table.querySelectorAll("tr[data-customer-id]").forEach(function (row) {
      if (!isVisible(row)) {
        return;
      }
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

  window.CustomersView = {
    init: init,
    apply: applyView,
    render: renderCards,
    buildCard: buildCard,
    switchView: switchView,
    currentView: storedView,
  };
})();
