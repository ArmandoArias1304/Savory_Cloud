(function () {
  "use strict";

  var shortcuts = [
    { path: "/admin/dashboard", key: "D", label: "Dashboard" },
    { path: "/admin/orders/select-table", key: "N", label: "Crear pedido" },
    { path: "/admin/orders", key: "P", label: "Pedidos" },
    { path: "/admin/tables", key: "M", label: "Mesas" },
    { path: "/admin/reservations", key: "R", label: "Reservaciones" },
    { path: "/admin/cash-register", key: "C", label: "Caja" },
    { path: "/admin/sales", key: "V", label: "Ventas" },
    { path: "/admin/inventory", key: "I", label: "Inventario" },
    { path: "/admin/employees", key: "E", label: "Empleados" },
    { path: "/admin/loyalty", key: "F", label: "Fidelización" },
    { path: "/admin/reports", key: "S", label: "Reportes" },
    { path: "/admin/kitchen", key: "K", label: "Cocina" },
    { path: "/admin/menu-items", key: "T", label: "Menú" },
    { path: "/admin/customers", key: "U", label: "Clientes" },
  ];
  var cashierShortcuts = [
    { path: "/cashier/orders/select-table", key: "N", label: "Crear pedidos", icon: "table_restaurant" },
    { path: "/cashier/orders", key: "P", label: "Ver pedidos", icon: "receipt_long" },
    { path: "/cashier/menu/view", key: "M", label: "Menú", icon: "restaurant_menu" },
    { path: "/cashier/reports/view", key: "R", label: "Reportes", icon: "bar_chart" },
    { path: "/cashier/ranking/view", key: "K", label: "Ranking", icon: "emoji_events" },
    { path: "/cashier/cash-register", key: "C", label: "Caja", icon: "point_of_sale" },
    { path: "/cashier/profile", key: "F", label: "Perfil", icon: "account_circle" },
    { path: "/printer-agent", key: "I", label: "Agente de impresión", icon: "computer" },
    { path: "/loyalty", key: "L", label: "Canjear", icon: "redeem" },
  ];
  var waiterShortcuts = [
    { path: "/waiter/orders/select-table", key: "N", label: "Crear pedidos", icon: "add_shopping_cart" },
    { path: "/waiter/orders", key: "P", label: "Mis pedidos", icon: "receipt_long" },
    { path: "/waiter/menu/view", key: "M", label: "Menú", icon: "restaurant_menu" },
    { path: "/waiter/ranking/view", key: "K", label: "Ranking", icon: "emoji_events" },
    { path: "/waiter/reports/view", key: "R", label: "Reportes", icon: "bar_chart" },
    { path: "/waiter/tip/view", key: "T", label: "Propinas", icon: "payments" },
    { path: "/loyalty", key: "L", label: "Canjear", icon: "redeem" },
    { path: "/waiter/profile", key: "F", label: "Perfil", icon: "account_circle" },
  ];

  function isTypingTarget(target) {
    if (!(target instanceof HTMLElement)) {
      return false;
    }

    return (
      target.isContentEditable ||
      /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName) ||
      target.closest("[contenteditable='true']") !== null
    );
  }

  function createShortcutPanel(availableShortcuts) {
    var trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "keyboard-shortcuts-trigger";
    trigger.setAttribute("aria-label", "Mostrar atajos de teclado");
    trigger.setAttribute("aria-controls", "keyboardShortcutsPanel");
    trigger.setAttribute("aria-expanded", "false");
    trigger.title = "Atajos de teclado";
    trigger.innerHTML =
      '<span class="material-symbols-outlined" aria-hidden="true">keyboard_arrow_left</span>';

    var backdrop = document.createElement("div");
    backdrop.className = "keyboard-shortcuts-backdrop";
    backdrop.setAttribute("aria-hidden", "true");

    var panel = document.createElement("aside");
    panel.id = "keyboardShortcutsPanel";
    panel.className = "keyboard-shortcuts-panel";
    panel.setAttribute("aria-label", "Atajos de teclado");
    panel.setAttribute("aria-modal", "true");
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-hidden", "true");
    panel.inert = true;

    var listItems = availableShortcuts
      .map(function (shortcut) {
        return (
          '<li class="flex items-center justify-between gap-4 rounded-xl border border-gray-200 px-3 py-2.5 dark:border-gray-700">' +
          '<span class="text-sm font-medium">' +
          shortcut.label +
          "</span>" +
          '<span class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">' +
          '<kbd class="rounded-md border border-gray-300 bg-gray-50 px-2 py-1 font-semibold text-gray-800 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100">A</kbd>' +
          '<span aria-hidden="true">+</span>' +
          '<kbd class="rounded-md border border-gray-300 bg-gray-50 px-2 py-1 font-semibold text-gray-800 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100">' +
          shortcut.key +
          "</kbd></span></li>"
        );
      })
      .join("");

    panel.innerHTML =
      '<div class="flex items-start justify-between gap-4 border-b border-gray-200 p-5 dark:border-gray-700">' +
      '<div><p class="text-xs font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">Navegación rápida</p>' +
      '<h2 class="mt-1 text-xl font-bold">Atajos de teclado</h2>' +
      '<p class="mt-1 text-sm text-gray-500 dark:text-gray-400">Presiona A y luego la tecla indicada.</p></div>' +
      '<button type="button" data-shortcuts-close class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 hover:text-gray-900 dark:hover:bg-gray-800 dark:hover:text-white" aria-label="Cerrar atajos">' +
      '<span class="material-symbols-outlined" aria-hidden="true">close</span></button></div>' +
      '<ul class="flex-1 space-y-2 overflow-y-auto p-5">' +
      listItems +
      "</ul>" +
      '<p class="border-t border-gray-200 px-5 py-4 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">Los atajos no se activan mientras escribes en un campo. Presiona Esc para cerrar este panel.</p>';

    document.body.appendChild(trigger);
    document.body.appendChild(backdrop);
    document.body.appendChild(panel);

    function closePanel() {
      trigger.setAttribute("aria-expanded", "false");
      panel.setAttribute("aria-hidden", "true");
      panel.inert = true;
      panel.classList.remove("is-open");
      backdrop.classList.remove("is-open");
      trigger.focus();
    }

    function openPanel() {
      trigger.setAttribute("aria-expanded", "true");
      panel.setAttribute("aria-hidden", "false");
      panel.inert = false;
      panel.classList.add("is-open");
      backdrop.classList.add("is-open");
      panel.querySelector("[data-shortcuts-close]").focus();
    }

    trigger.addEventListener("click", openPanel);
    backdrop.addEventListener("click", closePanel);
    panel
      .querySelector("[data-shortcuts-close]")
      .addEventListener("click", closePanel);

    return {
      close: closePanel,
      isOpen: function () {
        return panel.classList.contains("is-open");
      },
    };
  }

  function createNavigationWheel(
    availableShortcuts,
    systemLogoUrl,
    systemName,
    profile,
    linksByPath,
  ) {
    var roleName = profile === "cashier" ? "cajero" : "mesero";
    var preferenceKey = "elgransazon-" + profile + "-navigation-enabled";
    var trigger = document.createElement("button");
    trigger.type = "button";
    trigger.className = "keyboard-shortcuts-trigger";
    trigger.setAttribute(
      "aria-label",
      "Mantén pulsado para mostrar opciones de navegación",
    );
    trigger.setAttribute("aria-controls", "waiterNavigationWheel");
    trigger.setAttribute("aria-expanded", "false");
    trigger.title = "Mantén pulsado y arrastra para navegar";
    trigger.hidden = localStorage.getItem(preferenceKey) === "false";
    trigger.innerHTML = systemLogoUrl
      ? '<img class="waiter-wheel-trigger-logo" src="' +
        systemLogoUrl +
        '" alt="" /><span class="material-symbols-outlined waiter-wheel-trigger-arrow" aria-hidden="true">keyboard_arrow_left</span>'
      : '<span class="material-symbols-outlined" aria-hidden="true">keyboard_arrow_left</span>';
    if (systemLogoUrl) {
      var triggerLogo = trigger.querySelector("img");
      triggerLogo.draggable = false;
      triggerLogo.addEventListener("error", function () {
        this.remove();
      });
      triggerLogo.addEventListener("dragstart", function (event) {
        event.preventDefault();
      });
    }

    var backdrop = document.createElement("div");
    backdrop.className = "keyboard-shortcuts-backdrop waiter-wheel-backdrop";
    backdrop.setAttribute("aria-hidden", "true");

    var wheel = document.createElement("section");
    wheel.id = "waiterNavigationWheel";
    wheel.className = "waiter-navigation-wheel";
    wheel.tabIndex = -1;
    wheel.setAttribute("role", "dialog");
    wheel.setAttribute("aria-modal", "true");
    wheel.setAttribute("aria-label", "Opciones del " + roleName);
    wheel.setAttribute("aria-hidden", "true");
    wheel.inert = true;

    var currentPath = window.location.pathname.replace(/\/$/, "") || "/";
    var currentShortcutIndex = availableShortcuts.findIndex(function (shortcut) {
      var shortcutPath = shortcut.path.replace(/\/$/, "") || "/";
      if (currentPath === shortcutPath) {
        return true;
      }
      if (
        shortcutPath === "/waiter/orders" ||
        shortcutPath === "/cashier/orders"
      ) {
        return (
          currentPath.indexOf(shortcutPath + "/") === 0 &&
          currentPath.indexOf(shortcutPath + "/select-table") !== 0
        );
      }
      if (
        shortcutPath === "/cashier/cash-register" &&
        currentPath.indexOf(shortcutPath + "/") === 0
      ) {
        return true;
      }
      if (
        shortcutPath === "/cashier/orders/select-table" &&
        currentPath.indexOf(shortcutPath) === 0
      ) {
        return true;
      }
      return false;
    });

    var keycapEnabled = profile === "cashier";
    var items = availableShortcuts
      .map(function (shortcut, index) {
        var keycap =
          keycapEnabled && shortcut.key
            ? '<kbd class="waiter-wheel-key" aria-hidden="true">' +
              shortcut.key +
              "</kbd>"
            : "";
        var shortcutTitle =
          keycapEnabled && shortcut.key
            ? ' title="Atajo: A + ' + shortcut.key + '"'
            : "";
        return (
          '<button type="button" class="waiter-wheel-option' +
          (index === currentShortcutIndex ? " is-current" : "") +
          '" data-wheel-index="' +
          index +
          '" style="--wheel-index: ' +
          index +
          '" aria-label="' +
          shortcut.label +
          '"' +
          shortcutTitle +
          (index === currentShortcutIndex ? ' aria-current="page"' : "") +
          ">" +
          keycap +
          '<span class="material-symbols-outlined" aria-hidden="true">' +
          shortcut.icon +
          "</span><span>" +
          shortcut.label +
          "</span></button>"
        );
      })
      .join("");

    wheel.innerHTML = items;

    var header = document.createElement("div");
    header.className = "waiter-wheel-header";
    var headerIcon = document.createElement("span");
    headerIcon.className = "waiter-wheel-header-icon material-symbols-outlined";
    headerIcon.setAttribute("aria-hidden", "true");
    headerIcon.textContent = "explore";
    var heading = document.createElement("div");
    heading.className = "waiter-wheel-heading";
    var headingTitle = document.createElement("p");
    headingTitle.className = "waiter-wheel-title";
    headingTitle.textContent = "Navegación rápida";
    var headingSubtitle = document.createElement("p");
    headingSubtitle.className = "waiter-wheel-subtitle";
    var roleLabel = roleName.charAt(0).toUpperCase() + roleName.slice(1);
    headingSubtitle.textContent = systemName
      ? roleLabel + " · " + systemName
      : roleLabel;
    heading.appendChild(headingTitle);
    heading.appendChild(headingSubtitle);
    header.appendChild(headerIcon);
    header.appendChild(heading);
    wheel.appendChild(header);

    var hint = document.createElement("p");
    hint.id = "waiterWheelHint";
    hint.className = "waiter-wheel-hint";
    var hintIcon = document.createElement("span");
    hintIcon.className = "material-symbols-outlined";
    hintIcon.setAttribute("aria-hidden", "true");
    hintIcon.textContent = "swipe_left";
    var hintText = document.createElement("span");
    hintText.textContent = "Desliza y suelta sobre una opción";
    hint.appendChild(hintIcon);
    hint.appendChild(hintText);
    wheel.appendChild(hint);
    wheel.setAttribute("aria-describedby", "waiterWheelHint");

    document.body.appendChild(trigger);
    document.body.appendChild(backdrop);
    document.body.appendChild(wheel);

    var optionNodes = Array.prototype.slice.call(
      wheel.querySelectorAll(".waiter-wheel-option"),
    );
    var activePointerId = null;
    var highlightedOption = null;

    function arrangeOptions() {
      var triggerBounds = trigger.getBoundingClientRect();
      var centerX = triggerBounds.left + triggerBounds.width / 2;
      var centerY = triggerBounds.top + triggerBounds.height / 2;
      var availableRadius = Math.min(
        190,
        centerX - 42,
        centerY - 42,
        window.innerHeight - centerY - 42,
      );
      var optionSize = optionNodes.length
        ? Math.max(
            optionNodes[0].getBoundingClientRect().width,
            optionNodes[0].getBoundingClientRect().height,
          )
        : 0;
      var minimumRadius =
        optionNodes.length > 1
          ? (optionSize + 12) /
            (2 * Math.sin(Math.PI / (2 * (optionNodes.length - 1))))
          : 0;
      var radius = Math.max(
        54,
        Math.min(190, minimumRadius, availableRadius),
      );
      wheel.style.setProperty("--wheel-center-x", centerX + "px");
      wheel.style.setProperty("--wheel-center-y", centerY + "px");
      wheel.style.setProperty("--wheel-radius", radius + "px");
      optionNodes.forEach(function (option, index) {
        var progress =
          optionNodes.length === 1 ? 0.5 : index / (optionNodes.length - 1);
        var angle = Math.PI * progress;
        var offsetX = -38 - Math.sin(angle) * radius;
        var offsetY = -Math.cos(angle) * radius;
        option.style.setProperty("--wheel-x", offsetX + "px");
        option.style.setProperty("--wheel-y", offsetY + "px");
      });
    }

    function navigate(shortcut) {
      if (shortcut) {
        var link = linksByPath.get(shortcut.path.replace(/\/$/, ""));
        if (link) {
          link.click();
        } else {
          window.location.assign(shortcut.path);
        }
      }
    }

    function closeWheel() {
      trigger.setAttribute("aria-expanded", "false");
      wheel.setAttribute("aria-hidden", "true");
      wheel.inert = true;
      wheel.classList.remove("is-open");
      backdrop.classList.remove("is-open");
      trigger.classList.remove("is-wheel-open");
      document.body.classList.remove("waiter-wheel-dragging");
      activePointerId = null;
      highlightedOption = null;
      optionNodes.forEach(function (option) {
        option.classList.remove("is-highlighted");
      });
      trigger.focus();
    }

    function openWheel() {
      trigger.setAttribute("aria-expanded", "true");
      wheel.setAttribute("aria-hidden", "false");
      wheel.inert = false;
      wheel.classList.add("is-open");
      backdrop.classList.add("is-open");
      trigger.classList.add("is-wheel-open");
      arrangeOptions();
      wheel.focus();
    }

    function setEnabled(enabled) {
      if (!enabled && wheel.classList.contains("is-open")) {
        closeWheel();
      }
      trigger.hidden = !enabled;
    }

    trigger.addEventListener("pointerdown", function (event) {
      if (event.button !== 0 && event.pointerType === "mouse") {
        return;
      }
      event.preventDefault();
      openWheel();
      activePointerId = event.pointerId;
      document.body.classList.add("waiter-wheel-dragging");
      window.getSelection().removeAllRanges();
      trigger.setPointerCapture(event.pointerId);
    });

    trigger.addEventListener("click", function (event) {
      if (event.detail === 0) {
        openWheel();
      }
    });

    backdrop.addEventListener("click", closeWheel);

    optionNodes.forEach(function (option, index) {
      option.addEventListener("click", function () {
        navigate(availableShortcuts[index]);
      });
    });

    document.addEventListener("pointermove", function (event) {
      if (event.pointerId !== activePointerId) {
        return;
      }
      event.preventDefault();
      window.getSelection().removeAllRanges();
      var target = document.elementFromPoint(event.clientX, event.clientY);
      var option = target && target.closest(".waiter-wheel-option");
      if (option && wheel.contains(option)) {
        highlightedOption = option;
      } else {
        highlightedOption = null;
      }
      optionNodes.forEach(function (node) {
        node.classList.toggle("is-highlighted", node === highlightedOption);
      });
    });

    document.addEventListener("pointerup", function (event) {
      if (event.pointerId !== activePointerId) {
        return;
      }
      event.preventDefault();
      var target = document.elementFromPoint(event.clientX, event.clientY);
      var option = target && target.closest(".waiter-wheel-option");
      var index = option ? Number(option.dataset.wheelIndex) : -1;
      activePointerId = null;
      document.body.classList.remove("waiter-wheel-dragging");
      if (availableShortcuts[index]) {
        option.click();
      } else {
        closeWheel();
      }
    }, { passive: false });

    document.addEventListener("pointercancel", function (event) {
      if (event.pointerId === activePointerId) {
        closeWheel();
      }
    });

    trigger.addEventListener("contextmenu", function (event) {
      event.preventDefault();
      event.stopPropagation();
    });

    trigger.addEventListener("dragstart", function (event) {
      event.preventDefault();
    });

    window.addEventListener("resize", arrangeOptions);
    window.addEventListener("navigation-tool-preference-change", function (event) {
      if (event.detail.profile === profile) {
        setEnabled(event.detail.enabled);
      }
    });
    window.addEventListener("storage", function (event) {
      if (event.key === preferenceKey) {
        setEnabled(event.newValue !== "false");
      }
    });

    return {
      close: closeWheel,
      isOpen: function () {
        return wheel.classList.contains("is-open");
      },
    };
  }

  function init() {
    var sidebar = document.getElementById("sidebar");
    var waiterConfig = document.getElementById("waiterShortcutConfig");
    var cashierConfig = document.getElementById("cashierShortcutConfig");
    var profile =
      document.body.dataset.shortcutsProfile ||
      (waiterConfig ? "waiter" : "admin");
    var isCashier = profile === "cashier";
    var isWaiter = profile === "waiter";
    var dashboard = isCashier
      ? document.getElementById("cashierDashboard")
      : isWaiter
        ? document.getElementById("waiterDashboard")
        : null;
    if (
      (!sidebar && !dashboard && !isWaiter && !isCashier) ||
      document.getElementById("keyboardShortcutsPanel") ||
      document.getElementById("waiterNavigationWheel")
    ) {
      return;
    }

    var linksByPath = new Map();
    var linkContainer = isCashier ? dashboard : sidebar;
    if (linkContainer) {
      var linkSelector =
        isCashier || isWaiter
          ? ".dashboard-card[href]"
          : ".nav-item[href]";
      linkContainer.querySelectorAll(linkSelector).forEach(function (link) {
        var url = new URL(link.href, window.location.origin);
        linksByPath.set(url.pathname.replace(/\/$/, ""), link);
      });
    }

    var configuredShortcuts = isWaiter
      ? waiterShortcuts
      : isCashier
        ? cashierShortcuts
        : shortcuts;
    var availableShortcuts = configuredShortcuts.filter(function (shortcut) {
      if (isWaiter) {
        if (
          dashboard &&
          shortcut.path === "/waiter/orders/select-table" &&
          !linksByPath.has(shortcut.path)
        ) {
          return false;
        }
        return (
          shortcut.path !== "/loyalty" ||
          waiterConfig.dataset.loyaltyEnabled === "true"
        );
      }
      if (isCashier && !dashboard) {
        return (
          (shortcut.path !== "/loyalty" ||
            cashierConfig.dataset.loyaltyEnabled === "true") &&
          (shortcut.path !== "/cashier/orders/select-table" ||
            cashierConfig.dataset.restaurantOpen === "true")
        );
      }
      var link = linksByPath.get(shortcut.path);
      return link && !link.closest("[aria-hidden='true']");
    });
    if (availableShortcuts.length === 0) {
      return;
    }

    var panel = isWaiter || isCashier
      ? createNavigationWheel(
          availableShortcuts,
          (isWaiter ? waiterConfig : cashierConfig).dataset.systemLogoUrl,
          (isWaiter ? waiterConfig : cashierConfig).dataset.systemName,
          profile,
          linksByPath,
        )
      : createShortcutPanel(availableShortcuts);
    var awaitingSecondKey = false;
    var sequenceTimeout;

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && panel.isOpen()) {
        panel.close();
        return;
      }

      if (
        isWaiter ||
        panel.isOpen() ||
        (isCashier &&
          localStorage.getItem("elgransazon-cashier-navigation-enabled") ===
            "false")
      ) {
        return;
      }

      if (
        event.defaultPrevented ||
        event.altKey ||
        event.ctrlKey ||
        event.metaKey ||
        event.shiftKey ||
        isTypingTarget(event.target)
      ) {
        return;
      }

      var pressedKey = event.key.toUpperCase();
      if (!awaitingSecondKey && pressedKey === "A") {
        event.preventDefault();
        awaitingSecondKey = true;
        window.clearTimeout(sequenceTimeout);
        sequenceTimeout = window.setTimeout(function () {
          awaitingSecondKey = false;
        }, 1500);
        return;
      }

      if (!awaitingSecondKey) {
        return;
      }

      awaitingSecondKey = false;
      window.clearTimeout(sequenceTimeout);
      var shortcut = availableShortcuts.find(function (item) {
        return item.key === pressedKey;
      });
      if (!shortcut) {
        return;
      }

      event.preventDefault();
      var link = linksByPath.get(shortcut.path);
      if (link) {
        link.click();
      } else if (isCashier) {
        window.location.assign(shortcut.path);
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init, { once: true });
  } else {
    init();
  }
})();
