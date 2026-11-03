/**
 * Selección de mesas para crear un solo pedido con varias mesas físicas.
 * Las tarjetas se seleccionan con clic; las filas de tabla usan checkbox.
 */
(function () {
  "use strict";

  function init() {
    var cards = Array.prototype.slice.call(
      document.querySelectorAll('.table-card[data-can-use="true"]'),
    );
    var rows = Array.prototype.slice.call(
      document.querySelectorAll('tr[data-table-id][data-can-use="true"]'),
    );
    var selectableElements = cards.concat(rows).filter(function (element) {
      return element.getAttribute("data-reserved-now") !== "true";
    });

    if (selectableElements.length < 2) {
      return;
    }

    var selected = [];
    var selecting = false;
    var style = document.createElement("style");
    style.textContent =
      ".union-table-candidate{outline:3px solid #22c55e;outline-offset:-3px;" +
      "animation:union-table-pulse 1.2s ease-in-out infinite}" +
      ".union-table-selected{outline:4px solid #00c366;outline-offset:-4px;" +
      "box-shadow:0 0 0 5px rgba(0,195,102,.24)!important}" +
      "tr.union-table-candidate>td{background-color:rgba(34,197,94,.12)}" +
      "tr.union-table-selected>td{background-color:rgba(0,195,102,.16)}" +
      "@keyframes union-table-pulse{50%{outline-color:#86efac;box-shadow:0 0 0 5px rgba(34,197,94,.2)}}";
    document.head.appendChild(style);

    var bar = document.createElement("div");
    bar.id = "unionTablesBar";
    bar.style.cssText =
      "display:flex;align-items:center;gap:8px;margin-left:auto;flex-shrink:0;" +
      "font-family:inherit;max-width:100%;";

    var count = document.createElement("span");
    count.id = "unionTablesCount";
    count.style.cssText = "font-size:13px;font-weight:600;white-space:nowrap;display:none;";

    var startButton = document.createElement("button");
    startButton.type = "button";
    startButton.id = "unionTablesButton";
    startButton.textContent = "Unir mesas";
    startButton.style.cssText =
      "border:0;border-radius:11px;background:#38e07b;color:#052e16;font-weight:800;" +
      "font-size:13px;padding:10px 16px;cursor:pointer;white-space:nowrap;";

    var cancelButton = document.createElement("button");
    cancelButton.type = "button";
    cancelButton.id = "unionTablesCancel";
    cancelButton.textContent = "Cancelar";
    cancelButton.style.cssText =
      "display:none;border:0;background:transparent;color:#cbd5e1;font-size:13px;" +
      "font-weight:600;cursor:pointer;padding:8px;";

    bar.appendChild(count);
    bar.appendChild(startButton);
    bar.appendChild(cancelButton);

    var sectionTitle = Array.prototype.slice
      .call(document.querySelectorAll("h3"))
      .find(function (heading) {
        return heading.textContent.trim() === "Selecciona una Mesa";
      });
    if (sectionTitle && sectionTitle.parentElement) {
      var header = sectionTitle.parentElement;
      var titleRow = document.createElement("div");
      titleRow.style.cssText =
        "display:flex;align-items:center;justify-content:space-between;gap:12px;" +
        "flex-wrap:wrap;margin-bottom:16px;";
      header.insertBefore(titleRow, sectionTitle);
      sectionTitle.style.marginBottom = "0";
      titleRow.appendChild(sectionTitle);
      titleRow.appendChild(bar);
    }

    function updateSelection() {
      count.textContent = selected.length + " mesas seleccionadas";

      selectableElements.forEach(function (element) {
        var id = element.getAttribute("data-table-id");
        var isSelected = selected.indexOf(id) !== -1;
        element.classList.toggle("union-table-candidate", selecting);
        element.classList.toggle("union-table-selected", selecting && isSelected);
        if (element.classList.contains("table-card")) {
          element.setAttribute("aria-pressed", String(isSelected));
        }

        var checkbox = element.querySelector(".union-table-checkbox");
        if (checkbox) {
          checkbox.checked = isSelected;
          checkbox.disabled = !selecting;
          checkbox.closest("label").style.display = selecting ? "inline-flex" : "none";
        }
      });

      count.style.display = selecting ? "inline" : "none";
      startButton.textContent = selecting
        ? selected.length < 2
          ? "Selecciona " + (2 - selected.length) + " mesa(s) más"
          : "Continuar con " + selected.length + " mesas"
        : "Unir mesas";
      startButton.disabled = selecting && selected.length < 2;
      startButton.style.opacity = startButton.disabled ? "0.55" : "1";
      startButton.style.cursor = startButton.disabled ? "not-allowed" : "pointer";
      cancelButton.style.display = selecting ? "inline-block" : "none";
    }

    function toggleSelection(element) {
      var id = element.getAttribute("data-table-id");
      if (!id) {
        return;
      }

      if (selected.indexOf(id) === -1) {
        selected.push(id);
      } else {
        selected = selected.filter(function (value) {
          return value !== id;
        });
      }
      updateSelection();
    }

    cards.forEach(function (card) {
      card.addEventListener(
        "click",
        function (event) {
          if (!selecting) {
            return;
          }
          event.preventDefault();
          event.stopImmediatePropagation();
          toggleSelection(card);
        },
        true,
      );
    });

    rows.forEach(function (row) {
      var cell = row.querySelector("td");
      if (!cell) {
        return;
      }

      var label = document.createElement("label");
      label.className = "union-table-checkbox-label";
      label.style.cssText =
        "display:none;align-items:center;justify-content:center;min-width:36px;";
      var checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.className = "union-table-checkbox";
      checkbox.setAttribute("aria-label", "Seleccionar mesa para unir");
      checkbox.disabled = true;
      label.appendChild(checkbox);
      cell.insertBefore(label, cell.firstChild);
      checkbox.addEventListener("click", function (event) {
        event.stopPropagation();
      });
      checkbox.addEventListener("change", function () {
        if (selecting) {
          toggleSelection(row);
        }
      });
    });

    function goToUnion() {
      var role = window.location.pathname.split("/").filter(Boolean)[0] || "waiter";
      var url =
        "/" + role + "/orders/customer-info?orderType=DINE_IN&tableIds=" +
        encodeURIComponent(selected.join(","));
      window.location.href = url;
    }

    startButton.addEventListener("click", function () {
      if (!selecting) {
        selecting = true;
        updateSelection();
        return;
      }
      if (selected.length >= 2) {
        goToUnion();
      }
    });

    cancelButton.addEventListener("click", function () {
      selecting = false;
      selected = [];
      updateSelection();
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
