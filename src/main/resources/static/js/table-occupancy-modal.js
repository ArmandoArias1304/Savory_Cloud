/**
 * Modal compartido de "mesa ocupada" para las vistas de crear pedido.
 *
 * Los controladores dejan el resumen de cada mesa en
 * window.__occupiedTables (Map<tableId, OccupiedTableInfoDTO>). Este script no
 * consulta al servidor: solo pinta ese resumen cuando el empleado pulsa una
 * tarjeta ocupada.
 */
(function () {
  "use strict";

  var STATUS_BASE =
    "mt-1 inline-flex items-center rounded-full px-2.5 py-1 text-xs font-bold ";
  var STATUS_STYLES = {
    TO_ACCEPT:
      "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300",
    PENDING:
      "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300",
    IN_PREPARATION:
      "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300",
    READY:
      "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300",
    ON_THE_WAY:
      "bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-300",
    DELIVERED:
      "bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200",
    PAID:
      "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300",
    CANCELLED:
      "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300",
    DEFAULT:
      "bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200",
  };

  var moneyFormatter = new Intl.NumberFormat("es-MX", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  var bodyHadOverflowHidden = false;
  var previouslyFocused = null;

  function byId(id) {
    return document.getElementById(id);
  }

  function setText(id, value) {
    var node = byId(id);
    if (node) {
      node.textContent = value == null || value === "" ? "—" : String(value);
    }
  }

  function formatMoney(value) {
    var amount = Number(value);
    if (!isFinite(amount)) {
      amount = 0;
    }
    return "$" + moneyFormatter.format(amount);
  }

  function formatElapsed(minutes) {
    var total = Math.max(0, Math.floor(Number(minutes) || 0));
    if (total < 1) {
      return "Hace menos de 1 min";
    }
    if (total < 60) {
      return "Hace " + total + " min";
    }
    var hours = Math.floor(total / 60);
    var rest = total % 60;
    return "Hace " + hours + " h" + (rest > 0 ? " " + rest + " min" : "");
  }

  function paintStatus(status, label) {
    var badge = byId("occupiedTableStatus");
    if (!badge) {
      return;
    }
    badge.className =
      STATUS_BASE + (STATUS_STYLES[status] || STATUS_STYLES.DEFAULT);
    badge.textContent = label || status || "—";
  }

  function open(info, tableNumber) {
    var modal = byId("occupiedTableModal");
    if (!modal) {
      return;
    }

    setText(
      "occupiedTableModalTitle",
      tableNumber != null && tableNumber !== "" ? "Mesa " + tableNumber : "Mesa",
    );
    setText("occupiedTableWaiterName", info.waiterName || "Sin mesero asignado");
    setText("occupiedTableWaiterInitials", info.waiterInitials || "?");
    setText("occupiedTableOrderNumber", info.orderNumber || "—");
    setText("occupiedTableOpenedAt", info.openedAt || "—");
    setText("occupiedTableElapsed", formatElapsed(info.occupiedMinutes));
    setText("occupiedTableItems", String(Number(info.itemCount) || 0));
    setText("occupiedTableTotal", formatMoney(info.total));
    setText(
      "occupiedTableCustomer",
      info.customerName || "Cliente no registrado",
    );
    paintStatus(info.status, info.statusDisplay);

    previouslyFocused = document.activeElement;
    bodyHadOverflowHidden = document.body.classList.contains("overflow-hidden");
    if (!bodyHadOverflowHidden) {
      document.body.classList.add("overflow-hidden");
    }
    document.body.classList.add("occupied-table-modal-open");

    modal.classList.remove("hidden");
    modal.setAttribute("aria-hidden", "false");

    var closeButton = modal.querySelector("button[data-occupied-close]");
    if (closeButton) {
      closeButton.focus({ preventScroll: true });
    }
  }

  function close() {
    var modal = byId("occupiedTableModal");
    if (!modal) {
      return;
    }
    modal.classList.add("hidden");
    modal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("occupied-table-modal-open");
    if (!bodyHadOverflowHidden) {
      document.body.classList.remove("overflow-hidden");
    }
    if (previouslyFocused && typeof previouslyFocused.focus === "function") {
      previouslyFocused.focus({ preventScroll: true });
    }
    previouslyFocused = null;
  }

  function fallback(tableNumber) {
    var message =
      "La mesa " +
      tableNumber +
      " está ocupada, pero no se pudo cargar la información del pedido. Recarga la página e inténtalo de nuevo.";
    if (window.Swal) {
      window.Swal.fire({
        icon: "warning",
        title: "Mesa no disponible",
        text: message,
        confirmButtonColor: "#38e07b",
        confirmButtonText: "Entendido",
      });
      return;
    }
    window.alert(message);
  }

  function show(tableId, tableNumber) {
    var tables = window.__occupiedTables || {};
    var info = tables[String(tableId)];
    if (!info) {
      fallback(tableNumber);
      return;
    }
    open(info, tableNumber);
  }

  function bind() {
    var modal = byId("occupiedTableModal");
    if (!modal) {
      return;
    }

    modal.querySelectorAll("[data-occupied-close]").forEach(function (node) {
      node.addEventListener("click", close);
    });

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && !modal.classList.contains("hidden")) {
        close();
      }
    });
  }

  window.TableOccupancyModal = {
    show: show,
    open: open,
    close: close,
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bind);
  } else {
    bind();
  }
})();
