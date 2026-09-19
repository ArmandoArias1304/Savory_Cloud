/**
 * mixed-payment.js — Collect an order with one or more payment methods.
 *
 * The collector toggles the enabled methods and, when more than one is
 * selected, types the amount of each. The mix must cover the order total
 * (without tip) exactly. A single method auto-fills the whole total.
 *
 * Hidden fields kept in sync:
 *   #paymentMethodInput  — primary method (largest amount)
 *   #paymentTendersInput — JSON [{method, amount}, ...]
 *
 * Depends on window.SPLIT_CONFIG.methods / methodLabels / zeroCashTips
 * and, when present, getEffectiveOrderTotal() from the payment form.
 */
(function (global) {
  "use strict";

  var selected = [];
  var amounts = {};
  var METHOD_NAMES = {
    CASH: "Efectivo",
    CREDIT_CARD: "Tarjeta de Crédito",
    DEBIT_CARD: "Tarjeta de Débito",
    TRANSFER: "Transferencia",
  };

  function cfg() {
    return global.SPLIT_CONFIG || {};
  }

  function methods() {
    return cfg().methods || [];
  }

  function methodLabel(m) {
    var ms = methods();
    var labels = cfg().methodLabels || [];
    var i = ms.indexOf(m);
    if (i >= 0 && labels[i]) return labels[i];
    return METHOD_NAMES[m] || m;
  }

  function round2(n) {
    return Math.round((Number(n) || 0) * 100) / 100;
  }

  function expectedTotal() {
    if (typeof global.getEffectiveOrderTotal === "function") {
      return round2(global.getEffectiveOrderTotal());
    }
    if (global.ORDER_DATA && global.ORDER_DATA.total != null) {
      return round2(global.ORDER_DATA.total);
    }
    return 0;
  }

  function zeroCashTips() {
    return cfg().zeroCashTips !== false;
  }

  function el(id) {
    return document.getElementById(id);
  }

  function tendersList() {
    var target = expectedTotal();
    if (selected.length === 1) {
      return [{ method: selected[0], amount: target }];
    }
    var list = [];
    for (var i = 0; i < selected.length; i++) {
      var m = selected[i];
      var amt = round2(amounts[m]);
      if (amt > 0) list.push({ method: m, amount: amt });
    }
    return list;
  }

  function sumSelected() {
    if (selected.length === 1) return expectedTotal();
    var s = 0;
    for (var i = 0; i < selected.length; i++) {
      s += round2(amounts[selected[i]]);
    }
    return round2(s);
  }

  function primaryMethod() {
    if (!selected.length) return "";
    if (selected.length === 1) return selected[0];
    var best = selected[0];
    for (var i = 1; i < selected.length; i++) {
      if (round2(amounts[selected[i]]) > round2(amounts[best])) {
        best = selected[i];
      }
    }
    return best;
  }

  function isCashOnly() {
    return selected.length > 0 && selected.every(function (m) {
      return m === "CASH";
    });
  }

  function usesCash() {
    return selected.indexOf("CASH") >= 0;
  }

  function cashAmount() {
    if (isCashOnly()) return expectedTotal();
    return round2(amounts.CASH);
  }

  function label() {
    var list = tendersList();
    if (!list.length) return "No seleccionado";
    return list
      .map(function (t) {
        return methodLabel(t.method) + " $" + Number(t.amount).toFixed(2);
      })
      .join(" + ");
  }

  function syncHidden() {
    var methodInput = el("paymentMethodInput");
    if (methodInput) methodInput.value = primaryMethod();
    var tendersInput = el("paymentTendersInput");
    if (tendersInput) tendersInput.value = JSON.stringify(tendersList());
  }

  function paintButtons() {
    document.querySelectorAll(".payment-method-btn").forEach(function (btn) {
      var method = btn.getAttribute("data-method");
      var on = selected.indexOf(method) >= 0;
      btn.classList.toggle("active", on);
      btn.classList.toggle("text-gray-600", !on);
      btn.classList.toggle("dark:text-gray-400", !on);
      btn.setAttribute("aria-pressed", on ? "true" : "false");
    });
  }

  function renderAmounts() {
    var panel = el("paymentTenderPanel");
    var wrap = el("paymentTenderAmounts");
    var mixed = selected.length > 1;
    if (panel) panel.style.display = mixed ? "" : "none";
    if (!mixed) {
      if (selected.length === 1) {
        amounts[selected[0]] = expectedTotal();
      }
      syncHidden();
      paintChangeCalculator();
      return;
    }
    if (wrap) {
      var html = "";
      for (var i = 0; i < selected.length; i++) {
        var m = selected[i];
        var val = amounts[m] != null ? round2(amounts[m]) : 0;
        var shown = val > 0 ? val.toFixed(2) : "";
        html +=
          '<div class="flex items-center gap-3">' +
          '<label class="w-36 shrink-0 text-sm font-semibold text-gray-700 dark:text-gray-300">' +
          escapeHtml(methodLabel(m)) +
          "</label>" +
          '<div class="relative flex-1">' +
          '<span class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-gray-400 font-bold">$</span>' +
          '<input type="number" min="0" step="0.01" max="999999.99" inputmode="decimal" data-method="' +
          escapeHtml(m) +
          '" value="' +
          shown +
          '" placeholder="0.00" class="mixed-tender-amount input-field w-full min-w-[10rem] rounded-xl border-2 border-gray-300 bg-white dark:bg-gray-700 p-3 pl-8 text-lg font-bold text-gray-900 dark:text-white focus:outline-none" />' +
          "</div>" +
          "</div>";
      }
      wrap.innerHTML = html;
    }
    updateBalance();
    syncHidden();
    paintChangeCalculator();
  }

  function updateBalance() {
    var balance = el("paymentTenderBalance");
    if (!balance || selected.length < 2) return;
    var remaining = round2(expectedTotal() - sumSelected());
    if (Math.abs(remaining) < 0.005) {
      balance.textContent = "Cubierto: $" + expectedTotal().toFixed(2);
      balance.className = "text-xs font-semibold text-primary";
    } else if (remaining > 0) {
      balance.textContent = "Falta asignar $" + remaining.toFixed(2);
      balance.className = "text-xs font-semibold text-amber-600 dark:text-amber-400";
    } else {
      balance.textContent = "Sobra $" + Math.abs(remaining).toFixed(2) + " — la suma no puede superar el total";
      balance.className = "text-xs font-semibold text-red-600 dark:text-red-400";
    }
  }

  function paintChangeCalculator() {
    var section = el("changeCalculatorSection");
    if (section) section.style.display = "";
  }

  function escapeHtml(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function toggle(button) {
    if (!button) return;
    var method = button.getAttribute("data-method");
    if (!method) return;
    var allowed = methods();
    if (allowed.length && allowed.indexOf(method) < 0) return;
    var idx = selected.indexOf(method);
    if (idx >= 0) {
      if (selected.length === 1) return;
      selected.splice(idx, 1);
      delete amounts[method];
    } else {
      selected.push(method);
      amounts[method] = 0;
    }
    if (selected.length === 1) {
      amounts[selected[0]] = expectedTotal();
    }
    paintButtons();
    renderAmounts();
    if (typeof global.syncTipSection === "function") global.syncTipSection();
    if (typeof global.calculateTotalWithTip === "function") global.calculateTotalWithTip();
    else if (typeof global.calculateChange === "function") global.calculateChange();
  }

  function onAmountInput(input) {
    var method = input.getAttribute("data-method");
    if (!method) return;
    var raw = String(input.value || "");
    if (raw.indexOf(".") >= 0) {
      var parts = raw.split(".");
      if (parts[1] && parts[1].length > 2) {
        input.value = parts[0] + "." + parts[1].slice(0, 2);
        raw = input.value;
      }
    }
    if (raw === "" || raw === "." || raw === "-") {
      amounts[method] = 0;
    } else {
      var v = parseFloat(raw);
      if (isNaN(v) || v < 0) v = 0;
      if (v > 999999.99) {
        v = 999999.99;
        input.value = "999999.99";
      }
      amounts[method] = round2(v);
    }
    updateBalance();
    syncHidden();
    if (typeof global.calculateChange === "function") global.calculateChange();
  }

  function validate() {
    if (!selected.length) {
      return { ok: false, message: "Debe seleccionar al menos un método de pago" };
    }
    var target = expectedTotal();
    if (target <= 0) {
      return { ok: false, message: "El total a cobrar debe ser mayor a cero" };
    }
    var list = tendersList();
    if (!list.length) {
      return { ok: false, message: "Debe indicar el monto de cada método de pago" };
    }
    if (selected.length > 1) {
      var sum = sumSelected();
      if (Math.abs(round2(sum - target)) > 0.01) {
        return {
          ok: false,
          message:
            "La suma de los métodos de pago ($" +
            sum.toFixed(2) +
            ") debe ser exactamente el total ($" +
            target.toFixed(2) +
            ")",
        };
      }
    }
    syncHidden();
    return { ok: true, tenders: list, label: label() };
  }

  function sync() {
    if (selected.length === 1) {
      amounts[selected[0]] = expectedTotal();
    }
    paintButtons();
    var wrap = el("paymentTenderAmounts");
    var existing = wrap ? wrap.querySelectorAll(".mixed-tender-amount") : [];
    if (selected.length > 1 && existing.length === selected.length) {
      updateBalance();
      syncHidden();
      paintChangeCalculator();
      return;
    }
    renderAmounts();
  }

  function init() {
    var hidden = el("paymentMethodInput");
    var start = hidden && hidden.value ? hidden.value : "";
    var allowed = methods();
    if (start && allowed.length && allowed.indexOf(start) < 0) {
      start = allowed[0] || "";
    }
    if (!start && allowed.length) start = allowed[0];
    selected = start ? [start] : [];
    amounts = {};
    if (start) amounts[start] = expectedTotal();
    var tendersInput = el("paymentTendersInput");
    if (!tendersInput) {
      var form = el("paymentForm");
      if (form) {
        tendersInput = document.createElement("input");
        tendersInput.type = "hidden";
        tendersInput.name = "paymentTenders";
        tendersInput.id = "paymentTendersInput";
        form.appendChild(tendersInput);
      }
    }
    var wrap = el("paymentTenderAmounts");
    if (wrap && !wrap.dataset.bound) {
      wrap.dataset.bound = "1";
      wrap.addEventListener("input", function (e) {
        if (e.target && e.target.classList.contains("mixed-tender-amount")) {
          onAmountInput(e.target);
        }
      });
      wrap.addEventListener("focusout", function (e) {
        if (!e.target || !e.target.classList.contains("mixed-tender-amount")) return;
        var method = e.target.getAttribute("data-method");
        var v = round2(amounts[method]);
        if (v > 0) e.target.value = v.toFixed(2);
      });
    }
    paintButtons();
    renderAmounts();
    var form = el("paymentForm");
    if (form) form.noValidate = true;
  }

  global.MIXED_PAYMENT = {
    init: init,
    toggle: toggle,
    sync: sync,
    validate: validate,
    syncHidden: syncHidden,
    isCashOnly: isCashOnly,
    usesCash: usesCash,
    cashAmount: cashAmount,
    label: label,
    selected: function () {
      return selected.slice();
    },
    tenders: tendersList,
    expectedTotal: expectedTotal,
    zeroCashTips: zeroCashTips,
  };

  global.togglePaymentMethod = function (button) {
    toggle(button);
  };
})(window);
