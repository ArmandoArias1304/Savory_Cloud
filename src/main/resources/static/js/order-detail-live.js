/**
 * Live refresh for the order detail views (admin | cashier | waiter).
 *
 * The detail page is rendered by the server, but the status of the order and of
 * every one of its items changes while the page is open (the kitchen prepares
 * items, the cashier collects the order, the repartidor advances it...). This
 * client keeps those badges up to date WITHOUT the user reloading the page.
 *
 * It listens to two STOMP topics of the company, the same way the order lists
 * do:
 *
 *   /topic/admin/kitchen/{companyId}  -> order level events (status change,
 *                                        items added/deleted, cancellation)
 *   /topic/orders/detail/{companyId}  -> item level snapshots emitted when an
 *                                        item changes status but the order's
 *                                        overall status does not, so the lists
 *                                        and the kitchen views are not
 *                                        re-rendered for a no-op.
 *
 * Both payloads are OrderNotificationDTO, which now carries the id and the
 * status of every item, so a single event resyncs the whole page.
 *
 * The page must define, before loading this script:
 *   var ORDER_VIEW_ID;             // id of the order being displayed
 *   var ORDER_VIEW_COMPANY_ID;     // tenant id
 *   var ORDER_VIEW_SHOW_TO_ACCEPT; // whether this role renders the
 *                                  // "Por aceptar" item badge
 *   var ORDER_VIEW_TO_ACCEPT_ICON; // optional glyph for that badge (the
 *                                  // views do not all use the same one)
 */
(function () {
  "use strict";

  var ORDER_STATUS_BASE =
    "inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold rounded-xl";

  var ORDER_STATUS_INFO = {
    TO_ACCEPT: [
      "bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400 ring-2 ring-amber-400/40",
      "Por aceptar",
    ],
    PENDING: [
      "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400",
      "Pendiente",
    ],
    IN_PREPARATION: [
      "bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400",
      "En preparación",
    ],
    READY: [
      "bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400",
      "Listo",
    ],
    ON_THE_WAY: [
      "bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400",
      "En camino",
    ],
    DELIVERED: [
      "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
      "Entregado",
    ],
    PAID: ["bg-primary/20 text-primary", "Pagado"],
    CANCELLED: [
      "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400",
      "Cancelado",
    ],
  };

  var ORDER_STATUS_FALLBACK = [
    "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400",
  ];

  var ITEM_STATUS_BASE =
    "inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-semibold";

  var ITEM_STATUS_INFO = {
    TO_ACCEPT: [
      "bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-300 ring-1 ring-amber-400/40",
      window.ORDER_VIEW_TO_ACCEPT_ICON || "hourglass_top",
      "Por aceptar",
    ],
    PENDING: [
      "bg-orange-100 dark:bg-orange-900/30 text-orange-800 dark:text-orange-300",
      "schedule",
      "Pendiente",
    ],
    IN_PREPARATION: [
      "bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300",
      "cooking",
      "Preparando",
    ],
    READY: [
      "bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300",
      "check_circle",
      "Listo",
    ],
    DELIVERED: [
      "bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300",
      "done_all",
      "Entregado",
    ],
  };

  // Events that change the shape of the order (rows, totals): the page is
  // rebuilt by the server instead of patched in place.
  var STRUCTURAL_TYPES = [
    "NEW_ORDER",
    "ITEMS_ADDED",
    "ITEM_DELETED",
    "ORDER_CANCELLED",
    "ORDER_DELETED",
  ];

  var reloadTimer = null;

  function scheduleReload(delayMs) {
    if (reloadTimer) return;
    reloadTimer = window.setTimeout(function () {
      reloadTimer = null;
      // Never yank the page out from under the user: a confirm dialog (delete item,
      // print...) or an in-flight action would be lost.
      if (
        window.Swal &&
        typeof window.Swal.isVisible === "function" &&
        window.Swal.isVisible()
      ) {
        scheduleReload(2000);
        return;
      }
      window.location.reload();
    }, delayMs || 1500);
  }

  function orderStatusBadge(status) {
    // The waiter view does not highlight TO_ACCEPT in amber: its server-side
    // markup falls through to the red default, so the live badge matches it.
    if (status === "TO_ACCEPT" && window.ORDER_VIEW_SHOW_TO_ACCEPT === false) {
      return {
        className: ORDER_STATUS_BASE + " " + ORDER_STATUS_FALLBACK[0],
        label: ORDER_STATUS_INFO.TO_ACCEPT[1],
      };
    }
    var info = ORDER_STATUS_INFO[status];
    if (!info) {
      return {
        className: ORDER_STATUS_BASE + " " + ORDER_STATUS_FALLBACK[0],
        label: status || "",
      };
    }
    return { className: ORDER_STATUS_BASE + " " + info[0], label: info[1] };
  }

  function itemStatusBadgeHtml(status) {
    // Some roles (waiter) never show the "Por aceptar" badge: the server does
    // not render it either, so the live update must match that rule.
    if (status === "TO_ACCEPT" && window.ORDER_VIEW_SHOW_TO_ACCEPT === false) {
      return "";
    }
    var info = ITEM_STATUS_INFO[status];
    if (!info) return "";
    return (
      '<span class="' +
      ITEM_STATUS_BASE +
      " " +
      info[0] +
      '"><span class="material-symbols-outlined !text-xs">' +
      info[1] +
      "</span>" +
      info[2] +
      "</span>"
    );
  }

  function refreshOrderStatusBadge(status) {
    var badge = document.getElementById("order-status-badge");
    if (!badge || !status) return;
    var info = orderStatusBadge(status);
    badge.className = info.className;
    badge.textContent = info.label;
  }

  function refreshItemStatusBadges(items) {
    (items || []).forEach(function (item) {
      if (item.idOrderDetail == null) return;
      var cell = document.getElementById(
        "item-status-cell-" + item.idOrderDetail,
      );
      // Combo parents deliberately show no per-item badge.
      if (!cell || cell.getAttribute("data-combo-parent") === "true") return;
      cell.innerHTML = itemStatusBadgeHtml(item.itemStatus);
    });
  }

  function handleNotification(notification) {
    if (!notification) return;
    if (String(notification.orderId) !== String(window.ORDER_VIEW_ID)) return;

    if (STRUCTURAL_TYPES.indexOf(notification.notificationType) !== -1) {
      scheduleReload(1500);
      return;
    }

    refreshOrderStatusBadge(notification.status);
    refreshItemStatusBadges(notification.items);

    // Payment and cancellation change the available actions (server side).
    if (notification.status === "PAID" || notification.status === "CANCELLED") {
      scheduleReload(2000);
    }
  }

  function connect() {
    if (typeof SockJS === "undefined" || typeof Stomp === "undefined") return;

    var socket = new SockJS("/ws");
    var client = Stomp.over(socket);
    client.debug = null;

    client.connect(
      {},
      function () {
        client.subscribe(
          "/topic/admin/kitchen/" + window.ORDER_VIEW_COMPANY_ID,
          function (message) {
            handleNotification(JSON.parse(message.body));
          },
        );
        client.subscribe(
          "/topic/orders/detail/" + window.ORDER_VIEW_COMPANY_ID,
          function (message) {
            handleNotification(JSON.parse(message.body));
          },
        );
      },
      function () {
        // Reconnect quietly; the page still shows the last rendered state.
        window.setTimeout(connect, 5000);
      },
    );
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", connect);
  } else {
    connect();
  }
})();
