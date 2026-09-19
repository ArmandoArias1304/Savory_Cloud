/**
 * split-bill.js — Real "Dividir Cuenta" logic for the payment forms.
 *
 * The form injects window.SPLIT_CONFIG (order totals + assignable items +
 * enabled payment methods + role), then this module renders the per-person
 * account editor (per-person item assignment), validates the plan and
 * serializes it as JSON for the backend.
 *
 * SAT rule: a product is never divided. Each person's account holds WHOLE
 * units only, so every generated ticket keeps complete products. "Partes
 * iguales" (fractional quantities per person) was removed entirely; the
 * forms only offer a visual per-person conversion that never splits the
 * payment (see the quick-split widget at the bottom).
 *
 * Assignment model (ITEMS / "Por persona"):
 *   An order line holds WHOLE units (e.g. the waiter rings 4 Cokes as a single
 *   line of qty 4). Each person's account therefore lists, in whole units, the
 *   items they actually consumed (1 × Coca-Cola, 2 × Hamburguesa...). The sum
 *   of every unit across all accounts must equal the line quantity — the
 *   editor shows what is still unassigned and the backend enforces the same
 *   rule.
 *
 * Expected SPLIT_CONFIG:
 * {
 *   total: <order total>,
 *   items: [{ id, name, qty, price (effective per-unit), comps (complement total) }],
 *   methods: ["CASH", "CREDIT_CARD", ...],   // enabled for this role
 *   methodLabels: ["Efectivo", ...],         // Spanish label per method (same order)
 *   fixedMethod: "CASH" | null,              // delivery: method fixed per order
 *   role: "admin" | "cashier" | "waiter" | "delivery"
 * }
 */
(function () {
  "use strict";

  var cfg = window.SPLIT_CONFIG || {};
  var items = cfg.items || [];
  var methods = cfg.methods || [];
  // Spanish labels injected by the server (PaymentMethodType.getDisplayName),
  // aligned by index with `methods`.
  var methodLabels = cfg.methodLabels || [];
  var fixedMethod = cfg.fixedMethod || null;
  var orderTotal = parseFloat(cfg.total) || 0;
  var MAX_ACCOUNTS = 10;

  // Restaurant rule: a tip paid in CASH goes straight to the waiter, so no tip
  // is captured for a CASH account. The delivery flow allows cash tips, so the
  // server injects zeroCashTips=false there. Defaults to true (restaurant).
  var zeroCashTips = cfg.zeroCashTips !== false;

  // First person number for this order (injected by the server): numbering
  // continues from the last "Persona N" already charged on the order, so
  // labels never repeat across partial collections / splits.
  var startPerson = parseInt(cfg.nextPersonNumber, 10) || 1;

  // Editor semantics injected by the server:
  //   departure  — order still open with items pending: charge ONLY the
  //                ENTREGADO items of the person(s) leaving; the order stays
  //                open, so it is OK to leave units unassigned.
  // The editor is always per-person (whole items): "Partes iguales" was
  // removed because a product cannot be divided (SAT).
  var departureMode = !!cfg.departure;

  // Any line with fewer units owed than its original quantity means someone
  // was already charged (partial collection) and the editor must work only
  // on the remaining units.
  var hasPartials = items.some(function (it) {
    return (
      it &&
      (parseFloat(it.lineQty) || 0) - (parseFloat(it.qty) || 0) > 0.000001
    );
  });

  function currentMinAccounts() {
    // One leaving guest is enough in a departure collection; a full-order
    // settlement into per-person accounts needs at least 2 people.
    return departureMode ? 1 : 2;
  }

  var state = {
    enabled: false,
    // A departing-guest collection starts with ONE person (use "+ Agregar
    // persona" when several leave and each wants their own ticket); a full
    // settlement into per-person accounts starts with 2 people.
    count: departureMode ? 1 : 2,
    accounts: [], // [{ methods: [..], tenders: {METHOD: amount}, method, tip, tipPct, items }]
  };

  // ---------- helpers ----------

  function el(id) {
    return document.getElementById(id);
  }

  function fmt(n) {
    return "$" + (Math.round(n * 100) / 100).toFixed(2);
  }

  function round4(n) {
    return Math.round(n * 10000) / 10000;
  }

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  /** Whole units for integer order lines; halves only for fractional lines. */
  function unitStep(item) {
    return Math.round(item.qty) === item.qty ? 1 : 0.5;
  }

  /** Display a quantity without trailing zeros: 2, 1.5, 0.25... */
  function fmtQty(q) {
    var n = round4(q);
    return String(Math.round(n) === n ? Math.round(n) : n);
  }

  var METHOD_NAMES = {
    CASH: "Efectivo",
    CREDIT_CARD: "Tarjeta de Crédito",
    DEBIT_CARD: "Tarjeta de Débito",
    TRANSFER: "Transferencia",
  };

  /**
   * Spanish name of a payment method. The server-injected label wins (single
   * source of truth: the PaymentMethodType enum); the local map is only a
   * fallback for a method that is not part of the enabled list.
   */
  function methodDisplay(m) {
    var i = methods.indexOf(m);
    if (i >= 0 && methodLabels[i]) return methodLabels[i];
    return METHOD_NAMES[m] || m;
  }

  /** Whether a person paying with this mix can register a tip here. */
  function tipsAllowedForMethod(method) {
    return !(zeroCashTips && method === "CASH");
  }

  function accountMethods(acc) {
    if (acc && acc.methods && acc.methods.length) return acc.methods.slice();
    if (acc && acc.method) return [acc.method];
    return [];
  }

  function tipsAllowedForAccount(acc) {
    var ms = accountMethods(acc);
    if (!ms.length) return true;
    return !zeroCashTips || !ms.every(function (m) {
      return m === "CASH";
    });
  }

  function round2(n) {
    return Math.round((Number(n) || 0) * 100) / 100;
  }

  function emptyAccount() {
    var method = defaultMethod();
    return {
      method: method,
      methods: method ? [method] : [],
      tenders: {},
      tip: 0,
      tipPct: null,
      items: {},
    };
  }

  function methodMixLabel(acc) {
    var ms = accountMethods(acc);
    if (!ms.length) return "Sin método";
    var total = accountDiscountedTotal(acc);
    if (ms.length === 1) return methodDisplay(ms[0]);
    return ms
      .map(function (m) {
        var amt = acc.tenders && acc.tenders[m] != null ? round2(acc.tenders[m]) : 0;
        return methodDisplay(m) + " " + fmt(amt);
      })
      .join(" + ");
  }

  function accountTendersPayload(acc) {
    var ms = accountMethods(acc);
    var total = round2(accountDiscountedTotal(acc));
    if (!ms.length) return [];
    if (ms.length === 1) {
      return [{ method: ms[0], amount: total }];
    }
    var list = [];
    for (var i = 0; i < ms.length; i++) {
      var amt = round2(acc.tenders && acc.tenders[ms[i]]);
      if (amt > 0) list.push({ method: ms[i], amount: amt });
    }
    return list;
  }

  function accountTendersSum(acc) {
    var payload = accountTendersPayload(acc);
    var s = 0;
    for (var i = 0; i < payload.length; i++) s += Number(payload[i].amount) || 0;
    return round2(s);
  }

  function defaultMethod() {
    return fixedMethod || (methods.length ? methods[0] : "");
  }

  /** Captured percentage (0 when the discount is a fixed amount or absent). */
  function discountPercent() {
    var typeEl = document.getElementById("discountType");
    var mode = typeEl ? String(typeEl.value).toUpperCase() : "MONTO";
    if (mode !== "PORCENTAJE") return 0;
    var pctEl = document.getElementById("orderDiscountPercent");
    if (!pctEl) return 0;
    var pct = parseFloat(pctEl.value);
    if (isNaN(pct) || pct <= 0) return 0;
    return Math.min(pct, 100);
  }

  /** Order-level discount from the form (admin/cashier only). */
  function orderDiscount() {
    var base = splitBaseTotal();
    var pct = discountPercent();
    if (pct > 0) {
      return Math.min(base, (base * pct) / 100);
    }
    var input = document.querySelector('[name="orderDiscount"]');
    if (!input) return 0;
    var v = parseFloat(input.value);
    if (isNaN(v) || v < 0) return 0;
    // After partial charges the discount can never exceed what is still owed.
    return Math.min(v, base);
  }

  /** Gross value of the units still owed (items + prorated complements). */
  function remainingGross() {
    var t = 0;
    for (var i = 0; i < items.length; i++) {
      var it = items[i];
      if (!it) continue;
      var q = parseFloat(it.qty) || 0;
      if (q <= 0) continue;
      var lineQty = parseFloat(it.lineQty) > 0 ? parseFloat(it.lineQty) : q;
      var comps = parseFloat(it.comps) || 0;
      t += q * (parseFloat(it.price) || 0) + (comps > 0 ? (comps * q) / lineQty : 0);
    }
    return t;
  }

  /**
   * Total the editor must collect: the full order, or — after partial
   * (departing-guest) charges — only the units still owed.
   */
  function splitBaseTotal() {
    return hasPartials ? Math.max(0, remainingGross()) : orderTotal;
  }

  function itemById(detailId) {
    for (var i = 0; i < items.length; i++) {
      if (String(items[i].id) === String(detailId)) return items[i];
    }
    return null;
  }

  /**
   * Lines the editor can assign right now. In a departing-guest collection only
   * ENTREGADO lines with units still owed are offered (never items still
   * pending); paid-off lines (qty 0) are skipped everywhere.
   */
  function offeredItems() {
    var out = [];
    for (var i = 0; i < items.length; i++) {
      var it = items[i];
      if (!it) continue;
      if (!(parseFloat(it.qty) > 0.000001)) continue;
      if (departureMode && !it.delivered) continue;
      out.push(it);
    }
    return out;
  }

  function applyDepartureLabels() {
    if (!departureMode) return;
    var toggle = el("splitToggle");
    if (!toggle) return;
    var label = toggle.closest("label");
    var span = label ? label.querySelector("span") : null;
    if (span) {
      span.textContent = "Cobrar a la(s) persona(s) que se va(n) (solo ítems ya entregados)";
    }
  }

  function qtyOf(account, detailId) {
    var q = account.items[String(detailId)];
    return q == null ? 0 : q;
  }

  /** Units of this line already assigned to any account. */
  function assignedOf(detailId) {
    var sum = 0;
    for (var i = 0; i < state.accounts.length; i++) {
      sum += qtyOf(state.accounts[i], detailId);
    }
    return round4(sum);
  }

  /** Units of this line that still need an owner. */
  function leftoverOf(detailId) {
    var it = itemById(detailId);
    if (!it) return 0;
    return round4(it.qty - assignedOf(detailId));
  }

  /** Preview line total for one account + item (price×qty + comps share). */
  function lineTotal(account, item) {
    var q = qtyOf(account, item.id);
    if (q <= 0) return 0;
    var pricePart = item.price * q;
    // Complements are prorated over the ORIGINAL line quantity (same as the
    // backend), not over the units still owed after partial charges.
    var compsBase = parseFloat(item.lineQty) > 0 ? parseFloat(item.lineQty) : parseFloat(item.qty);
    var compsPart = item.comps > 0 ? (item.comps * q) / compsBase : 0;
    return pricePart + compsPart;
  }

  function accountItemsTotal(account) {
    var sum = 0;
    for (var i = 0; i < items.length; i++) {
      sum += lineTotal(account, items[i]);
    }
    return sum;
  }

  /**
   * Person total after the global discount. A captured percentage is applied
   * to each account individually (same as the backend); a fixed amount is
   * prorated by the server, so the preview keeps the gross total there.
   */
  function accountDiscountedTotal(account) {
    var gross = accountItemsTotal(account);
    var pct = discountPercent();
    if (pct <= 0) return gross;
    return Math.round(gross * ((100 - pct) / 100) * 100) / 100;
  }

  // ---------- state ----------

  function initAccounts() {
    var n = state.count;
    var accs = [];
    for (var i = 0; i < n; i++) {
      accs.push(emptyAccount());
    }
    state.accounts = accs;
  }

  function addAccount() {
    if (state.count >= MAX_ACCOUNTS) return;
    state.count++;
    state.accounts.push(emptyAccount());
    render();
  }

  function removeAccount(idx) {
    if (state.count <= currentMinAccounts()) return;
    state.accounts.splice(idx, 1);
    state.count--;
    render();
  }

  // ---------- rendering ----------

  function methodSelectHtml(acc, idx) {
    if (fixedMethod) {
      return (
        '<span class="text-sm font-semibold text-gray-600 dark:text-gray-400">' +
        esc(methodDisplay(fixedMethod)) +
        "</span>"
      );
    }
    var selected = accountMethods(acc);
    var chips = "";
    for (var i = 0; i < methods.length; i++) {
      var m = methods[i];
      var on = selected.indexOf(m) >= 0;
      chips +=
        '<button type="button" class="split-method-chip rounded-lg border-2 px-2.5 py-1.5 text-xs font-bold transition ' +
        (on
          ? "bg-primary/10 text-primary border-primary"
          : "bg-white dark:bg-gray-700 text-gray-600 dark:text-gray-300 border-gray-300 dark:border-gray-600") +
        '" data-account="' +
        idx +
        '" data-method="' +
        esc(m) +
        '" aria-pressed="' +
        (on ? "true" : "false") +
        '">' +
        esc(methodDisplay(m)) +
        "</button>";
    }
    return '<div class="flex flex-wrap gap-1.5">' + chips + "</div>";
  }

  function methodAmountsHtml(acc, idx) {
    var selected = accountMethods(acc);
    if (selected.length < 2) return "";
    var rows = "";
    var total = round2(accountDiscountedTotal(acc));
    var sum = 0;
    for (var j = 0; j < selected.length; j++) {
      var sm = selected[j];
      var amt = acc.tenders && acc.tenders[sm] != null ? round2(acc.tenders[sm]) : 0;
      sum += amt;
      rows +=
        '<div class="flex items-center gap-3 mt-2">' +
        '<label class="w-40 shrink-0 text-sm font-semibold text-gray-700 dark:text-gray-300">' +
        esc(methodDisplay(sm)) +
        "</label>" +
        '<div class="relative min-w-0 flex-1">' +
        '<span class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-gray-400 font-bold">$</span>' +
        '<input type="number" min="0" step="0.01" max="999999.99" inputmode="decimal" value="' +
        amt.toFixed(2) +
        '" class="split-tender-amount input-field w-full min-w-[10rem] rounded-xl border-2 border-gray-300 bg-white dark:bg-gray-700 dark:text-white p-3 pl-8 text-lg font-bold focus:outline-none focus:border-primary" data-account="' +
        idx +
        '" data-method="' +
        esc(sm) +
        '" />' +
        "</div></div>";
    }
    var remaining = round2(total - sum);
    var balClass =
      Math.abs(remaining) < 0.005
        ? "text-primary"
        : remaining > 0
          ? "text-amber-600 dark:text-amber-400"
          : "text-red-600 dark:text-red-400";
    var balText =
      Math.abs(remaining) < 0.005
        ? "Cubierto " + fmt(total)
        : remaining > 0
          ? "Falta " + fmt(remaining)
          : "Sobra " + fmt(Math.abs(remaining));
    return (
      '<div class="mt-3">' +
      '<p class="text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1">Montos por método</p>' +
      rows +
      '<p class="split-tender-balance text-xs font-semibold mt-2 ' +
      balClass +
      '">' +
      balText +
      "</p></div>"
    );
  }

  var TIP_PERCENTS = [0, 10, 15, 20];

  /**
   * Per-person tip picker: percentage buttons (0/10/15/20%) plus a manual
   * amount — the same options as the normal (non-split) Propina section.
   * Percentages are computed over the person's own items total.
   */
  function tipPickerHtml(acc, idx) {
    var btns = "";
    for (var i = 0; i < TIP_PERCENTS.length; i++) {
      var p = TIP_PERCENTS[i];
      var active = acc.tipPct != null && acc.tipPct === p;
      btns +=
        '<button type="button" class="split-tip-pct rounded-lg border-2 py-1.5 text-xs font-bold transition ' +
        (active
          ? "bg-primary text-white border-primary"
          : "bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 border-gray-200 dark:border-gray-600 hover:bg-primary hover:text-white hover:border-primary") +
        '" data-account="' +
        idx +
        '" data-percent="' +
        p +
        '">' +
        p +
        "%</button>";
    }
    return (
      '<div class="grid grid-cols-4 gap-1.5">' +
      btns +
      "</div>" +
      '<label class="block text-[11px] font-semibold text-gray-500 dark:text-gray-400 mt-2 mb-1">Propina personalizada</label>' +
      '<div class="relative">' +
      '<span class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-2.5 text-gray-400 text-xs font-bold">$</span>' +
      '<input type="number" min="0" max="999999.99" step="0.01" value="' +
      (acc.tip || 0).toFixed(2) +
      '" class="split-tip-custom w-full rounded-lg border-2 border-gray-300 bg-white dark:bg-gray-700 dark:text-white pl-6 pr-3 py-1.5 text-sm font-bold focus:outline-none focus:border-primary" data-account="' +
      idx +
      '" />' +
      "</div>"
    );
  }

  /** Recompute percentage-based tips whenever item assignment changes. */
  function reapplyPercentTips() {
    for (var i = 0; i < state.accounts.length; i++) {
      var acc = state.accounts[i];
      // Cash accounts always carry a $0 tip (restaurant rule).
      if (!tipsAllowedForAccount(acc)) {
        acc.tip = 0;
        acc.tipPct = null;
        continue;
      }
      if (acc.tipPct != null) {
        acc.tip = round4((accountDiscountedTotal(acc) * acc.tipPct) / 100);
      }
    }
  }

  function personMetaHtml(acc, idx) {
    // CASH accounts never register a tip on the order; it is recorded later
    // as a cash-register (caja) movement.
    var tipHtml = tipsAllowedForAccount(acc)
      ? tipPickerHtml(acc, idx)
      : '<p class="text-[11px] text-gray-400 dark:text-gray-500 italic leading-snug">La propina en efectivo se registra en movimientos de caja.</p>';
    return (
      '<div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-3">' +
      "<div>" +
      '<label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1">Métodos de pago</label>' +
      methodSelectHtml(acc, idx) +
      "</div>" +
      "<div>" +
      '<label class="block text-xs font-semibold text-gray-500 dark:text-gray-400 mb-1">Propina</label>' +
      tipHtml +
      "</div>" +
      "</div>" +
      methodAmountsHtml(acc, idx)
    );
  }

  /**
   * One consumed line inside a person's account, e.g.:
   *   [−]  2 × Coca-Cola      $40.00  [+]
   */
  function consumedRowHtml(acc, item, idx) {
    var q = qtyOf(acc, item.id);
    if (q <= 0) return "";
    var step = unitStep(item);
    var canInc = leftoverOf(item.id) >= step - 0.000001;
    return (
      '<div class="flex items-center justify-between gap-2 py-1.5 border-b border-gray-100 dark:border-gray-700 last:border-0" data-detail="' +
      item.id +
      '" data-account="' +
      idx +
      '">' +
      '<div class="flex-1 min-w-0">' +
      '<p class="text-sm font-medium text-gray-700 dark:text-gray-300 truncate" title="' +
      esc(item.name) +
      '">' +
      '<span class="font-bold text-gray-900 dark:text-white">' +
      fmtQty(q) +
      " ×</span> " +
      esc(item.name) +
      "</p>" +
      "</div>" +
      '<div class="flex items-center gap-1 shrink-0">' +
      '<button type="button" class="split-qty-minus w-7 h-7 rounded-lg bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 font-bold hover:bg-primary hover:text-white disabled:opacity-40 disabled:cursor-not-allowed" data-account="' +
      idx +
      '" data-detail="' +
      item.id +
      '"' +
      (q < step ? " disabled" : "") +
      ">−</button>" +
      '<span class="split-qty text-sm font-bold text-gray-900 dark:text-white w-8 text-center">' +
      fmtQty(q) +
      "</span>" +
      '<button type="button" class="split-qty-plus w-7 h-7 rounded-lg bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 font-bold hover:bg-primary hover:text-white disabled:opacity-40 disabled:cursor-not-allowed" data-account="' +
      idx +
      '" data-detail="' +
      item.id +
      '"' +
      (canInc ? "" : " disabled") +
      ">+</button>" +
      "</div>" +
      '<span class="split-line-total text-sm font-bold text-gray-900 dark:text-white w-20 text-right shrink-0">' +
      fmt(lineTotal(acc, item)) +
      "</span>" +
      "</div>"
    );
  }

  /** Dropdown listing the lines that still have units without an owner. */
  function addItemHtml(idx) {
    var opts = '<option value="">+ Agregar producto a esta persona</option>';
    var any = false;
    var offered = offeredItems();
    for (var i = 0; i < offered.length; i++) {
      var left = leftoverOf(offered[i].id);
      if (left < 0.000001) continue;
      any = true;
      opts +=
        '<option value="' +
        offered[i].id +
        '">' +
        esc(offered[i].name) +
        " (quedan " +
        fmtQty(left) +
        ")</option>";
    }
    if (!any) {
      opts = '<option value="">Todos los productos ya están asignados</option>';
    }
    return (
      '<select class="split-add-select w-full rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 dark:text-white px-3 py-1.5 text-xs font-semibold focus:outline-none focus:border-primary" data-account="' +
      idx +
      '"' +
      (any ? "" : " disabled") +
      ">" +
      opts +
      "</select>"
    );
  }

  function render() {
    var list = el("splitAccountList");
    if (!list) return;
    reapplyPercentTips();
    var n = state.accounts.length;
    var html = "";
    for (var i = 0; i < n; i++) {
      var acc = state.accounts[i];
      var total = accountDiscountedTotal(acc);
      var consumed = offeredItems()
          .map(function (it) { return consumedRowHtml(acc, it, i); })
          .join("");
      var hasConsumed = offeredItems().some(function (it) { return qtyOf(acc, it.id) > 0; });

      html +=
        '<div class="split-person rounded-xl border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 p-4">' +
        '<div class="flex items-center justify-between">' +
        '<p class="font-bold text-gray-900 dark:text-white text-sm">Persona ' +
        (startPerson + i) +
        "</p>" +
        '<div class="flex items-center gap-2">' +
        '<span class="text-xs font-semibold text-gray-500 dark:text-gray-400">Total: </span>' +
        '<span class="split-person-total text-sm font-black text-primary">' +
        fmt(total + (acc.tip || 0)) +
        "</span>" +
        (n > currentMinAccounts()
          ? '<button type="button" class="split-remove-person ml-2 w-6 h-6 rounded-full bg-red-50 text-red-500 text-xs font-bold hover:bg-red-100" data-account="' +
            i +
            '" title="Quitar persona">✕</button>'
          : "") +
        "</div>" +
        "</div>" +
        '<div class="split-items mt-3">' +
        (hasConsumed
          ? consumed
          : '<p class="text-xs text-gray-400 italic py-1">Sin productos asignados — agrega lo que consumió esta persona.</p>') +
        '<div class="mt-2">' +
        addItemHtml(i) +
        "</div>" +
        "</div>" +
        personMetaHtml(acc, i) +
        "</div>";
    }
    list.innerHTML = html;

    // Only the add-person button exists now (no parts-equal stepper)
    var countRow = el("splitCountRow");
    var addRow = el("splitAddPersonRow");
    if (countRow) countRow.style.display = "none";
    if (addRow) addRow.style.display = "";

    renderSummary();
  }

  function renderSummary() {
    var modeLabel = departureMode ? "Persona(s) que se va(n)" : "Por persona";
    var n = state.accounts.length;
    var sum = state.accounts.reduce(function (s, a) { return s + accountItemsTotal(a); }, 0);
    var parts = [];
    var offered = offeredItems();
    for (var j = 0; j < offered.length; j++) {
      var left = leftoverOf(offered[j].id);
      if (left > 0.000001) {
        parts.push(fmtQty(left) + " × " + offered[j].name);
      }
    }
    var summary = el("splitSummary");
    if (summary) {
      var txt =
        "Modo: " + modeLabel + " · " + n + " cuenta(s) · a cobrar ahora: " + fmt(sum);
      if (departureMode) {
        if (parts.length) {
          txt += " · quedan sin cobrar en la cuenta abierta: " + parts.join(", ");
        } else {
          txt += " · todos los ítems entregados quedaron cubiertos";
        }
        txt += " — la cuenta continúa abierta para los ítems pendientes";
      } else if (parts.length) {
        txt += " · faltan por asignar: " + parts.join(", ");
      } else {
        txt += " · todos los productos asignados ✓";
      }
      if (!departureMode && orderDiscount() > 0) {
        txt += " (el descuento se reparte proporcionalmente)";
      }
      summary.textContent = txt;
    }
  }

  // ---------- events ----------

  /**
   * While the split editor is open every person manages their own tip, so the
   * global Propina section (and its CASH note / summary line) stays hidden;
   * it only reappears when charging normally (split off).
   */
  function syncTipSectionVisibility() {
    var section = el("tipSection");
    var note = el("tipCashNote");
    var line = el("tip-line");
    if (state.enabled) {
      if (section) section.style.display = "none";
      if (note) note.style.display = "none";
      if (line) line.style.display = "none";
    } else if (typeof syncTipSection === "function") {
      // Normal charge: the form's own logic restores the section per method.
      syncTipSection();
    } else {
      if (section) section.style.display = "";
      if (note) note.style.display = "";
      if (line) line.style.display = "";
    }
  }

  function wire() {
    var toggle = el("splitToggle");
    var panel = el("splitPanel");
    if (!toggle || !panel) return;

    toggle.addEventListener("change", function () {
      state.enabled = toggle.checked;
      panel.style.display = state.enabled ? "" : "none";
      syncTipSectionVisibility();
      if (state.enabled) {
        if (!state.accounts.length) initAccounts();
        render();
      }
    });

    var addBtn = el("splitAddPerson");
    if (addBtn) addBtn.addEventListener("click", addAccount);

    // delegation for dynamic content
    var list = el("splitAccountList");
    if (list) {
      list.addEventListener("click", function (e) {
        var t = e.target;
        if (t.classList.contains("split-qty-minus") || t.classList.contains("split-qty-plus")) {
          var accIdx = parseInt(t.getAttribute("data-account"), 10);
          var detailId = t.getAttribute("data-detail");
          var acc = state.accounts[accIdx];
          var item = itemById(detailId);
          if (!acc || !item) return;
          var cur = qtyOf(acc, detailId);
          var step = unitStep(item);
          var isPlus = t.classList.contains("split-qty-plus");
          var newQ;
          if (isPlus) {
            // Cannot take more units than the line still has available
            var free = leftoverOf(detailId);
            newQ = round4(Math.min(cur + step, cur + free));
          } else {
            newQ = round4(cur - step);
          }
          newQ = Math.max(0, newQ);
          acc.items[String(detailId)] = round4(newQ);
          if (newQ === 0) delete acc.items[String(detailId)];
          render();
        }
        if (t.classList.contains("split-remove-person")) {
          var idx = parseInt(t.getAttribute("data-account"), 10);
          removeAccount(idx);
        }
        if (t.classList.contains("split-tip-pct")) {
          var pctIdx = parseInt(t.getAttribute("data-account"), 10);
          var pct = parseInt(t.getAttribute("data-percent"), 10);
          var accPct = state.accounts[pctIdx];
          if (!accPct || isNaN(pct)) return;
          accPct.tipPct = pct;
          accPct.tip = round4((accountDiscountedTotal(accPct) * pct) / 100);
          render();
        }
        if (t.classList.contains("split-method-chip") || t.closest(".split-method-chip")) {
          var chip = t.classList.contains("split-method-chip") ? t : t.closest(".split-method-chip");
          var chipIdx = parseInt(chip.getAttribute("data-account"), 10);
          var chipMethod = chip.getAttribute("data-method");
          var chipAcc = state.accounts[chipIdx];
          if (!chipAcc || !chipMethod) return;
          if (!chipAcc.methods) chipAcc.methods = chipAcc.method ? [chipAcc.method] : [];
          if (!chipAcc.tenders) chipAcc.tenders = {};
          var at = chipAcc.methods.indexOf(chipMethod);
          if (at >= 0) {
            if (chipAcc.methods.length === 1) return;
            chipAcc.methods.splice(at, 1);
            delete chipAcc.tenders[chipMethod];
          } else {
            chipAcc.methods.push(chipMethod);
            chipAcc.tenders[chipMethod] = 0;
          }
          chipAcc.method = chipAcc.methods[0];
          if (!tipsAllowedForAccount(chipAcc)) {
            chipAcc.tip = 0;
            chipAcc.tipPct = null;
          }
          render();
        }
      });

      list.addEventListener("change", function (e) {
        var t = e.target;
        if (t.classList.contains("split-add-select")) {
          var accIdx = parseInt(t.getAttribute("data-account"), 10);
          var acc = state.accounts[accIdx];
          var item = t.value ? itemById(t.value) : null;
          if (!acc || !item) return;
          var cur = qtyOf(acc, item.id);
          var step = unitStep(item);
          var free = leftoverOf(item.id);
          if (free >= step - 0.000001) {
            acc.items[String(item.id)] = round4(cur + step);
          }
          render();
          return;
        }
        if (t.classList.contains("split-method")) {
          var card = t.closest(".split-person");
          var idx = Array.prototype.indexOf.call(list.children, card);
          if (idx >= 0) {
            state.accounts[idx].method = t.value;
            state.accounts[idx].methods = t.value ? [t.value] : [];
            state.accounts[idx].tenders = {};
            if (!tipsAllowedForAccount(state.accounts[idx])) {
              state.accounts[idx].tip = 0;
              state.accounts[idx].tipPct = null;
            }
            render();
          }
        }
        if (t.classList.contains("split-tip-custom")) {
          var card2 = t.closest(".split-person");
          var idx2 = Array.prototype.indexOf.call(list.children, card2);
          var v = parseFloat(t.value);
          state.accounts[idx2].tip = isNaN(v) || v < 0 ? 0 : v;
          state.accounts[idx2].tipPct = null;
          render();
        }
      });

      list.addEventListener("input", function (e) {
        var t = e.target;
        if (!t.classList.contains("split-tender-amount")) return;
        var tenderIdx = parseInt(t.getAttribute("data-account"), 10);
        var tenderMethod = t.getAttribute("data-method");
        var tenderAcc = state.accounts[tenderIdx];
        if (!tenderAcc || !tenderMethod) return;
        if (!tenderAcc.tenders) tenderAcc.tenders = {};
        var tv = parseFloat(t.value);
        tenderAcc.tenders[tenderMethod] = isNaN(tv) || tv < 0 ? 0 : round2(tv);
        var card = t.closest(".split-person");
        var bal = card ? card.querySelector(".split-tender-balance") : null;
        if (bal) {
          var total = round2(accountDiscountedTotal(tenderAcc));
          var remaining = round2(total - accountTendersSum(tenderAcc));
          if (Math.abs(remaining) < 0.005) {
            bal.textContent = "Cubierto " + fmt(total);
            bal.className = "split-tender-balance text-[11px] font-semibold mt-1 text-primary";
          } else if (remaining > 0) {
            bal.textContent = "Falta " + fmt(remaining);
            bal.className = "split-tender-balance text-[11px] font-semibold mt-1 text-amber-600 dark:text-amber-400";
          } else {
            bal.textContent = "Sobra " + fmt(Math.abs(remaining));
            bal.className = "split-tender-balance text-[11px] font-semibold mt-1 text-red-600 dark:text-red-400";
          }
        }
      });
    }

    // Re-render when the order-level discount ($ monto / % porcentaje) changes (admin/cashier)
    var discountInput = document.querySelector('[name="orderDiscount"]');
    if (discountInput) {
      discountInput.addEventListener("input", function () {
        if (state.enabled) render();
      });
    }
    // The $/% toggle and the percentage input both refresh the preview through
    // the form's calculateTotalWithTip() -> SPLIT_BILL.refresh() call.
  }

  // ---------- validation / serialization ----------

  function buildPlan() {
    if (!state.enabled) return null;
    if (state.accounts.length < currentMinAccounts()) {
      Swal.fire({
        icon: "error",
        title: departureMode ? "Cobro de la persona que se va" : "División de cuenta",
        text: "Se necesita al menos " + currentMinAccounts() + " persona(s).",
        confirmButtonColor: "#38e07b",
      });
      return null;
    }

    var plan = [];
    for (var i = 0; i < state.accounts.length; i++) {
      var acc = state.accounts[i];
      var mix = accountMethods(acc);
      var method = mix.length ? mix[0] : defaultMethod();
      if (!fixedMethod && methods.length) {
        for (var mi = 0; mi < mix.length; mi++) {
          if (methods.indexOf(mix[mi]) < 0) {
            Swal.fire({ icon: "error", title: "División de cuenta", text: "Persona " + (startPerson + i) + ": seleccione un método de pago válido.", confirmButtonColor: "#38e07b" });
            return null;
          }
        }
      }
      if (!mix.length) {
        Swal.fire({ icon: "error", title: "División de cuenta", text: "Persona " + (startPerson + i) + ": seleccione un método de pago.", confirmButtonColor: "#38e07b" });
        return null;
      }
      var personTotal = round2(accountDiscountedTotal(acc));
      var tenders = accountTendersPayload(acc);
      if (mix.length > 1) {
        var tenderSum = accountTendersSum(acc);
        if (Math.abs(round2(tenderSum - personTotal)) > 0.01) {
          Swal.fire({
            icon: "error",
            title: "División de cuenta",
            text:
              "Persona " +
              (startPerson + i) +
              ": la suma de los métodos de pago (" +
              fmt(tenderSum) +
              ") debe ser exactamente el total (" +
              fmt(personTotal) +
              ").",
            confirmButtonColor: "#38e07b",
          });
          return null;
        }
      }
      var itemsArr = [];
      var hasItems = false;
      var offered = offeredItems();
      for (var j = 0; j < offered.length; j++) {
        var q = qtyOf(acc, offered[j].id);
        if (q > 0) {
          hasItems = true;
          if (Math.round(offered[j].qty) === offered[j].qty && Math.round(q) !== q) {
            Swal.fire({
              icon: "error",
              title: "División de cuenta",
              text: "Persona " + (startPerson + i) + ": la cantidad de '" + offered[j].name + "' debe ser en unidades enteras.",
              confirmButtonColor: "#38e07b",
            });
            return null;
          }
          itemsArr.push({ orderDetailId: offered[j].id, quantity: round4(q) });
        }
      }
      if (!hasItems) {
        Swal.fire({
          icon: "error",
          title: departureMode ? "Cobro de la persona que se va" : "División de cuenta",
          text:
            "Persona " +
            (startPerson + i) +
            " no tiene productos asignados: agrega lo que consumió de los ítems ya entregados.",
          confirmButtonColor: "#38e07b",
        });
        return null;
      }
      plan.push({
        index: startPerson + i,
        personLabel: "Persona " + (startPerson + i),
        paymentMethod: method,
        paymentTenders: tenders,
        // Cash-only accounts never register a tip (restaurant rule).
        tip: tipsAllowedForAccount(acc)
          ? Math.round((acc.tip || 0) * 100) / 100
          : 0,
        items: itemsArr,
      });
    }

    // For a settlement every line must be fully assigned (whole units). In a
    // departing-guest collection leaving units is intentional — the rest of
    // the party pays them when they settle the open order.
    if (!departureMode) {
      var missing = [];
      var offered = offeredItems();
      for (var j = 0; j < offered.length; j++) {
        var left = leftoverOf(offered[j].id);
        if (left > 0.001) {
          missing.push(fmtQty(left) + " × " + offered[j].name);
        }
      }
      if (missing.length) {
        Swal.fire({
          icon: "error",
          title: "División de cuenta",
          text: "Faltan por asignar: " + missing.join(", ") + ". Cada producto debe quedar asignado a la persona que lo consumió.",
          confirmButtonColor: "#38e07b",
        });
        return null;
      }
    }
    return plan;
  }

  /** Small "2 × Coca-Cola · 1 × Pizza" summary for a person in the modal. */
  function personItemsText(planPerson) {
    if (!planPerson.items || !planPerson.items.length) return "";
    var labels = planPerson.items.map(function (it) {
      var item = itemById(it.orderDetailId);
      var name = item ? item.name : "Producto";
      return fmtQty(it.quantity) + " × " + name;
    });
    return labels.join(" · ");
  }

  function moneyRow(label, value, opts) {
    opts = opts || {};
    var color = opts.color || "inherit";
    var weight = opts.bold ? "700" : opts.muted ? "500" : "600";
    var size = opts.small ? "13px" : "14px";
    var pad = opts.padTop ? "padding-top:8px;margin-top:8px;border-top:1px solid #e5e7eb;" : "";
    return (
      "<div style='display:flex;justify-content:space-between;align-items:baseline;gap:12px;line-height:1.45;font-size:" +
      size +
      ";" +
      pad +
      "'>" +
      "<span style='color:" +
      (opts.muted ? "#6b7280" : "inherit") +
      ";font-weight:" +
      (opts.bold ? "700" : "500") +
      ";text-align:left'>" +
      esc(label) +
      "</span>" +
      "<span style='font-weight:" +
      weight +
      ";white-space:nowrap;font-variant-numeric:tabular-nums;color:" +
      color +
      "'>" +
      esc(value) +
      "</span></div>"
    );
  }

  function summaryHtml(plan) {
    var cards = "";
    var tips = 0;
    var consumptionSum = 0;
    for (var i = 0; i < plan.length; i++) {
      var p = plan[i];
      var acc = state.accounts[i];
      var consumption = round2(accountDiscountedTotal(acc));
      var tip = round2(p.tip || 0);
      var toCollect = round2(consumption + tip);
      consumptionSum += consumption;
      tips += tip;
      var consumed = personItemsText(p);
      var tenders = accountTendersPayload(acc);
      var tenderRows = "";
      for (var t = 0; t < tenders.length; t++) {
        tenderRows += moneyRow(methodDisplay(tenders[t].method), fmt(tenders[t].amount), {
          muted: true,
        });
      }
      if (!tenderRows) {
        tenderRows = moneyRow(methodMixLabel(acc), fmt(consumption), { muted: true });
      }
      cards +=
        "<div style='text-align:left;border:1px solid #e5e7eb;border-radius:12px;padding:12px 14px;margin-bottom:10px'>" +
        "<div style='display:flex;justify-content:space-between;align-items:baseline;gap:12px;margin-bottom:4px'>" +
        "<span style='font-weight:800'>" +
        esc(p.personLabel) +
        "</span>" +
        "<span style='font-weight:800;white-space:nowrap;color:#16a34a'>" +
        esc(fmt(toCollect)) +
        "</span></div>" +
        (consumed
          ? "<p style='margin:0 0 8px;font-size:12px;color:#6b7280;line-height:1.4'>" +
            esc(consumed) +
            "</p>"
          : "") +
        tenderRows +
        moneyRow("Consumo", fmt(consumption), { padTop: true, muted: true }) +
        moneyRow("Propina", tip > 0 ? fmt(tip) : "$0.00", { muted: true }) +
        moneyRow("A cobrar", fmt(toCollect), { bold: true }) +
        "</div>";
    }
    var orderTotalLabel = departureMode
      ? "Consumo a cobrar ahora"
      : hasPartials
        ? "Consumo restante"
        : "Consumo de la orden";
    var orderTotalValue = departureMode
      ? consumptionSum
      : Math.max(0, splitBaseTotal() - orderDiscount());
    return (
      "<div style='text-align:left;max-width:28rem;margin:0 auto'>" +
      cards +
      (departureMode
        ? "<p style='font-size:12px;color:#6b7280;margin:0 0 8px'>Solo se cobran los ítems ya ENTREGADOS de esta(s) persona(s). El resto de la cuenta sigue abierta.</p>"
        : "") +
      (orderDiscount() > 0
        ? moneyRow(
            "Descuento" + (discountPercent() > 0 ? " (" + discountPercent() + "%)" : ""),
            "-" + fmt(orderDiscount()),
            { color: "#ea580c", small: true }
          )
        : "") +
      moneyRow(orderTotalLabel, fmt(orderTotalValue), { muted: true }) +
      moneyRow("Propinas", fmt(tips), { muted: true }) +
      moneyRow("Total a cobrar", fmt(round2(orderTotalValue + tips)), {
        bold: true,
        padTop: true,
        color: "#16a34a",
      }) +
      "</div>"
    );
  }

  function confirmTitle() {
    return departureMode
      ? "¿Confirmar cobro de la persona que se va?"
      : "¿Confirmar división de cuenta?";
  }

  function appendToForm(form) {
    var plan = buildPlan();
    if (!plan) return false;
    var set = function (name, value) {
      var h = document.createElement("input");
      h.type = "hidden";
      h.name = name;
      h.value = value;
      form.appendChild(h);
    };
    set("splitEnabled", "true");
    set("splitMode", "ITEMS");
    set("splitDeparture", departureMode ? "true" : "");
    set("splitAccounts", JSON.stringify(plan));
    // Keep the single-method field consistent with the first account
    var methodInput = document.getElementById("paymentMethodInput");
    if (methodInput && plan.length) methodInput.value = plan[0].paymentMethod;
    return true;
  }

  function enabled() {
    return state.enabled;
  }

  // ---------- quick split (visual reference only — never splits the payment) ----------

  var QUICK_MIN = 1;
  var QUICK_MAX = 99;
  var quickSplitCount = 2;

  /**
   * Live "total a pagar" shown in the form (already includes tip and, where
   * applicable, the order discount applied by admin/cashier).
   */
  function quickSplitBase() {
    var display = el("displayTotal");
    if (display) {
      var parsed = parseFloat(String(display.textContent).replace(/[^0-9.\-]/g, ""));
      if (!isNaN(parsed) && parsed >= 0) return parsed;
    }
    if (window.ORDER_DATA && window.ORDER_DATA.total != null) {
      return Math.max(0, parseFloat(window.ORDER_DATA.total) || 0);
    }
    return Math.max(0, orderTotal);
  }

  function renderQuickSplit() {
    var countEl = el("quickSplitCount");
    var perPersonEl = el("quickSplitPerPerson");
    if (!countEl && !perPersonEl) return;
    quickSplitCount = Math.max(QUICK_MIN, Math.min(QUICK_MAX, quickSplitCount));
    if (countEl) countEl.textContent = quickSplitCount;
    if (perPersonEl) {
      perPersonEl.textContent = fmt(quickSplitBase() / quickSplitCount);
    }
  }

  function wireQuickSplit() {
    var dec = el("quickSplitDec");
    var inc = el("quickSplitInc");
    if (!dec && !inc) return;
    if (dec) {
      dec.addEventListener("click", function () {
        quickSplitCount = Math.max(QUICK_MIN, quickSplitCount - 1);
        renderQuickSplit();
      });
    }
    if (inc) {
      inc.addEventListener("click", function () {
        quickSplitCount = Math.min(QUICK_MAX, quickSplitCount + 1);
        renderQuickSplit();
      });
    }
    renderQuickSplit();
  }

  /**
   * The "¿Entre cuántas personas?" converter is an accordion, collapsed by
   * default, so it only takes space when actually needed.
   */
  function wireQuickSplitCollapse() {
    var toggle = el("quickSplitToggle");
    var body = el("quickSplitBody");
    var chevron = el("quickSplitChevron");
    if (!toggle || !body) return;
    function setOpen(open) {
      body.style.display = open ? "" : "none";
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
      if (chevron) chevron.style.transform = open ? "rotate(180deg)" : "";
      if (open) renderQuickSplit();
    }
    toggle.addEventListener("click", function () {
      setOpen(body.style.display === "none");
    });
  }

  // ---------- boot ----------

  /**
   * The "Dividir cuenta por persona" action in the orders list links here with
   * ?split=items so the split editor opens already enabled and ready to
   * assign what the person who is leaving consumed.
   */
  function urlSplitHint() {
    var m = (window.location.search || "").match(/[?&]split=([^&]+)/);
    return m ? m[1].toUpperCase() : null;
  }

  function boot() {
    var toggle = el("splitToggle");
    if (!toggle) return;
    wire();
    applyDepartureLabels();
    initAccounts();
    var hint = urlSplitHint();
    var openHint = hint === "ITEMS" || hint === "EQUAL";
    if (openHint) {
      state.enabled = true;
      toggle.checked = true;
    }
    if (state.enabled) {
      var panel = el("splitPanel");
      if (panel) {
        panel.style.display = "";
        if (panel.scrollIntoView) {
          panel.scrollIntoView({ behavior: "smooth", block: "start" });
        }
      }
    }
    render();
    syncTipSectionVisibility();
    wireQuickSplit();
    wireQuickSplitCollapse();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }

  window.SPLIT_BILL = {
    enabled: enabled,
    buildPlan: buildPlan,
    summaryHtml: summaryHtml,
    confirmTitle: confirmTitle,
    appendToForm: appendToForm,
    refresh: function () {
      render();
      renderQuickSplit();
    },
  };
})();
