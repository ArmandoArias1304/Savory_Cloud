/**
 * Vista de tabla / tarjetas para proveedores.
 * Las tarjetas se construyen desde la tabla para conservar los datos,
 * etiquetas y acciones que el servidor ya determinó.
 */
(function () {
  "use strict";

  var settings = {
    viewKey: "suppliersView",
    table: "suppliersTableView",
    wrapper: "suppliersCardsView",
    container: "suppliersCards",
    empty: "suppliersCardsEmpty",
  };
  var activeElementBeforeModal = null;

  function el(tag, className) {
    var node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    return node;
  }

  function preferredView() {
    var narrow =
      window.matchMedia && window.matchMedia("(max-width: 1023px)").matches;
    return narrow ? "grid" : "table";
  }

  function storedView() {
    try {
      var value = localStorage.getItem(settings.viewKey);
      return value === "grid" || value === "table" ? value : preferredView();
    } catch (error) {
      return preferredView();
    }
  }

  function markButtons(view) {
    document
      .querySelectorAll('.orders-view-btn[data-orders-view-scope="suppliers"]')
      .forEach(function (button) {
        var active = button.getAttribute("data-orders-view") === view;
        button.classList.toggle("bg-white", active);
        button.classList.toggle("dark:bg-gray-700", active);
        button.classList.toggle("text-primary", active);
        button.classList.toggle("shadow-sm", active);
        button.classList.toggle("text-gray-600", !active);
        button.classList.toggle("dark:text-gray-400", !active);
        button.setAttribute("aria-pressed", active ? "true" : "false");
      });
  }

  function cloneCell(row, index) {
    var cell = row.cells[index];
    if (!cell) {
      return null;
    }
    var source = cell.firstElementChild || cell;
    var clone = source.cloneNode(true);
    clone.removeAttribute("id");
    clone.querySelectorAll("[id]").forEach(function (node) {
      node.removeAttribute("id");
    });
    return clone;
  }

  function buildCard(row) {
    var supplierId = row.getAttribute("data-supplier-id");
    if (!supplierId) {
      return null;
    }

    var card = el(
      "article",
      "flex flex-col overflow-hidden rounded-2xl border border-gray-200 dark:border-gray-800 " +
        "bg-white dark:bg-gray-900 shadow-md transition-shadow hover:shadow-lg",
    );
    card.setAttribute("data-supplier-id", supplierId);
    card.setAttribute("tabindex", "0");
    card.setAttribute("aria-haspopup", "dialog");
    card.setAttribute(
      "aria-label",
      "Ver información de " +
        (row.getAttribute("data-supplier-name") || "proveedor"),
    );
    card.classList.add("cursor-pointer");
    if (row.classList.contains("opacity-50")) {
      card.classList.add("opacity-70");
    }

    var header = el(
      "div",
      "flex items-start justify-between gap-3 border-b border-gray-100 dark:border-gray-800 p-4",
    );
    var identity = cloneCell(row, 0);
    if (identity) {
      identity.classList.add("min-w-0", "flex-1");
    }
    var status = row.querySelector(".supplier-status-badge");
    if (identity) {
      header.appendChild(identity);
    }
    if (status) {
      var badge = status.cloneNode(true);
      badge.classList.remove("px-3", "py-1");
      badge.classList.add("shrink-0", "px-2", "py-1");
      header.appendChild(badge);
    }

    var content = el("div", "flex-1 space-y-4 p-4");
    var contacts = cloneCell(row, 1);
    if (contacts) {
      contacts.classList.add("space-y-1");
      content.appendChild(contacts);
    }

    var categories = cloneCell(row, 2);
    if (categories) {
      categories.classList.remove("max-w-xs");
      categories.classList.add("max-w-none");
      var categorySection = el("div", "space-y-2");
      var categoryLabel = el(
        "p",
        "text-[11px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500",
      );
      categoryLabel.textContent = "Categorías";
      categorySection.appendChild(categoryLabel);
      categorySection.appendChild(categories);
      content.appendChild(categorySection);
    }

    var ratingSection = el("div", "flex items-center justify-between gap-3");
    var ratingLabel = el(
      "span",
      "text-[11px] font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500",
    );
    ratingLabel.textContent = "Valoración";
    ratingSection.appendChild(ratingLabel);
    var rating = cloneCell(row, 3);
    if (rating) {
      ratingSection.appendChild(rating);
    }
    content.appendChild(ratingSection);

    var actions = row.querySelector(".supplier-actions-cell");
    var footer = el(
      "div",
      "mt-auto border-t border-gray-100 dark:border-gray-800 px-3 py-2",
    );
    if (actions) {
      var actionButtons = actions.cloneNode(true);
      actionButtons.className =
        "flex flex-wrap items-center justify-center gap-2";
      actionButtons.querySelectorAll("[id]").forEach(function (node) {
        node.removeAttribute("id");
      });
      footer.appendChild(actionButtons);
    }

    card.appendChild(header);
    card.appendChild(content);
    card.appendChild(footer);
    card.addEventListener("click", function (event) {
      if (event.target.closest("a, button, form")) {
        return;
      }
      openDetails(row);
    });
    card.addEventListener("keydown", function (event) {
      if (
        event.target !== card ||
        (event.key !== "Enter" && event.key !== " ")
      ) {
        return;
      }
      event.preventDefault();
      openDetails(row);
    });
    return card;
  }

  function setDetailText(id, value) {
    var target = document.getElementById(id);
    if (target) {
      target.textContent = value || "No especificado";
    }
  }

  function openDetails(row) {
    var modal = document.getElementById("supplierDetailModal");
    if (!modal || storedView() !== "grid") {
      return;
    }

    setDetailText("supplierDetailTitle", row.getAttribute("data-supplier-name"));
    setDetailText(
      "supplierDetailContact",
      row.getAttribute("data-supplier-contact"),
    );
    setDetailText("supplierDetailPhone", row.getAttribute("data-supplier-phone"));
    setDetailText("supplierDetailEmail", row.getAttribute("data-supplier-email"));
    setDetailText(
      "supplierDetailAddress",
      row.getAttribute("data-supplier-address"),
    );

    var notes = row.getAttribute("data-supplier-notes") || "";
    var notesSection = document.getElementById("supplierDetailNotesSection");
    var notesContent = document.getElementById("supplierDetailNotes");
    if (notesSection && notesContent) {
      notesContent.textContent = notes;
      notesSection.classList.toggle("hidden", !notes);
    }

    var categories = document.getElementById("supplierDetailCategories");
    if (categories) {
      var source = row.cells[2] && row.cells[2].firstElementChild;
      categories.replaceChildren();
      if (source && source.querySelectorAll("span").length > 0) {
        Array.prototype.forEach.call(source.children, function (category) {
          categories.appendChild(category.cloneNode(true));
        });
      } else {
        var empty = el("span", "text-sm italic text-gray-400");
        empty.textContent = "Sin categorías";
        categories.appendChild(empty);
      }
    }

    var rating = document.getElementById("supplierDetailRating");
    if (rating) {
      var ratingCell = row.cells[3];
      rating.replaceChildren();
      if (ratingCell && ratingCell.firstElementChild) {
        rating.appendChild(ratingCell.firstElementChild.cloneNode(true));
      } else {
        var noRating = el("span", "text-sm text-gray-400");
        noRating.textContent = "Sin valoración";
        rating.appendChild(noRating);
      }
    }

    var status = document.getElementById("supplierDetailStatus");
    if (status) {
      status.replaceChildren();
      var badge = row.querySelector(".supplier-status-badge");
      if (badge) {
        status.appendChild(badge.cloneNode(true));
      }
    }

    var editLink = document.getElementById("supplierDetailEdit");
    var sourceEditLink = row.querySelector(".supplier-actions-cell a");
    if (editLink && sourceEditLink) {
      editLink.href = sourceEditLink.href;
    }

    activeElementBeforeModal = document.activeElement;
    modal.classList.remove("hidden");
    modal.classList.add("flex");
    document.body.classList.add("overflow-hidden");
    var closeButton = modal.querySelector('button[aria-label="Cerrar"]');
    if (closeButton) {
      closeButton.focus();
    }
  }

  function closeDetails() {
    var modal = document.getElementById("supplierDetailModal");
    if (!modal) {
      return;
    }
    modal.classList.add("hidden");
    modal.classList.remove("flex");
    document.body.classList.remove("overflow-hidden");
    if (activeElementBeforeModal && activeElementBeforeModal.focus) {
      activeElementBeforeModal.focus();
    }
    activeElementBeforeModal = null;
  }

  function renderCards() {
    if (storedView() !== "grid") {
      return;
    }

    var table = document.getElementById(settings.table);
    var container = document.getElementById(settings.container);
    var empty = document.getElementById(settings.empty);
    if (!table || !container) {
      return;
    }

    var cards = [];
    table.querySelectorAll("tr[data-supplier-id]").forEach(function (row) {
      var card = buildCard(row);
      if (card) {
        cards.push(card);
      }
    });
    container.replaceChildren.apply(container, cards);
    if (empty) {
      empty.classList.toggle("hidden", cards.length > 0);
    }
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
    var selected = view === "grid" ? "grid" : "table";
    try {
      localStorage.setItem(settings.viewKey, selected);
    } catch (error) {
      // Si el almacenamiento no está disponible, el cambio sigue aplicándose.
    }
    applyView();
  }

  function init(options) {
    var opts = options || {};
    settings.viewKey = opts.viewKey || settings.viewKey;
    document
      .querySelectorAll('.orders-view-btn[data-orders-view-scope="suppliers"]')
      .forEach(function (button) {
        button.addEventListener("click", function () {
          switchView(button.getAttribute("data-orders-view"));
        });
      });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape") {
        closeDetails();
      }
    });
    applyView();
  }

  window.SuppliersView = {
    init: init,
    apply: applyView,
    render: renderCards,
    buildCard: buildCard,
    switchView: switchView,
    currentView: storedView,
    closeDetails: closeDetails,
  };
})();
