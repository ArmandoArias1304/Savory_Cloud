/**
 * ========================================
 * DIÁLOGO DEL PLATILLO CON SUS TAMAÑOS
 * ========================================
 *
 * Pieza compartida por las pantallas que enseñan la carta: el menú público del
 * local y el menú visual de mesero, cajero, cocina y reparto. Antes cada una
 * tenía su propia copia del diálogo y ninguna sabía de los tamaños: al pulsar un
 * platillo con tamaños se veía sólo el item padre, sin forma de llegar a los
 * demás, porque las variantes no son tarjetas aparte (el servidor manda un
 * recuadro por platillo).
 *
 * La página aporta dos cosas:
 *   - las tarjetas, con sus atributos data-item-* (id, name, price, description,
 *     image, available, size-name);
 *   - `sizeItemsMapping`, el mapa que escribe el servidor con los tamaños de cada
 *     item padre: { idItemMenu: [ {id, name, sizeName, price, available, …} ] }.
 *
 * Con eso el diálogo muestra el platillo y, si tiene tamaños, deja cambiar entre
 * ellos: flechas para pasar al anterior o al siguiente (dan la vuelta en los
 * extremos) y un botón por tamaño para ir directo al que se quiere ver. El
 * contador dice en cuál vamos y el tamaño sin stock se anuncia agotado en lugar de
 * esconder el resto.
 *
 * Uso en la plantilla:
 *   <script th:src="@{/js/menu-item-dialog.js}"></script>
 *   <div class="menu-item-card" data-item-id="…" onclick="openItemModal(this)">
 *
 * Expone:
 *   openItemModal(card), changePresentation(step), showPresentation(index)
 *   y menuItemSearchText(card) para la búsqueda de la página.
 */
(function () {
  "use strict";

  /** El diálogo abierto: las formas del platillo y cuál se está viendo. */
  let openPresentations = [];
  let openPresentationIndex = 0;
  let currentItem = null;

  /**
   * Los tamaños que escribió el servidor en la página. Se lee en cada apertura
   * porque la página los declara con `const`: existen (y valen) después de que su
   * script haya corrido. Si la página no los trae, el diálogo se ve como siempre.
   */
  function sizeMapping() {
    try {
      return typeof sizeItemsMapping === "undefined" || !sizeItemsMapping
        ? {}
        : sizeItemsMapping;
    } catch (error) {
      return {};
    }
  }

  function isDarkMode() {
    return document.documentElement.classList.contains("dark");
  }

  /**
   * Las formas de ver un mismo platillo: el item de la tarjeta y, si los tiene,
   * sus tamaños —los mismos que usan las pantallas de pedido—, ordenados por
   * precio para que las flechas vayan de la presentación más barata a la más cara.
   */
  function itemPresentations(card) {
    const parentId = parseInt(card.dataset.itemId, 10);
    const parentDescription = card.dataset.itemDescription || "";
    const parentImage = card.dataset.itemImage || "";
    const presentations = [
      {
        id: parentId,
        label: card.dataset.sizeName || card.dataset.itemName,
        name: card.dataset.itemName,
        price: parseFloat(card.dataset.itemPrice) || 0,
        description: parentDescription,
        image: parentImage,
        available: card.dataset.itemAvailable === "true",
      },
    ];

    (sizeMapping()[parentId] || []).forEach(function (child) {
      presentations.push({
        id: child.id,
        label: child.sizeName || child.name,
        name: child.name,
        price: parseFloat(child.price) || 0,
        description: child.description || parentDescription,
        image: child.imageUrl || parentImage,
        available: child.available !== false,
      });
    });

    return presentations.sort(function (a, b) {
      return a.price - b.price;
    });
  }

  /** El cuerpo del diálogo para la forma que se está viendo. */
  function presentationHtml(presentations, shown, isDark) {
    const presentation = presentations[shown];
    const muted = isDark ? "#9ca3af" : "#4b5563";
    const strong = isDark ? "#f9fafb" : "#111827";
    const imageUrl =
      presentation.image &&
      presentation.image !== "null" &&
      presentation.image !== ""
        ? presentation.image
        : "https://placehold.co/400x300/f3f4f6/9ca3af?text=Sin+Imagen";

    // Con una sola forma no hay nada que elegir: el diálogo se ve como siempre.
    const variantNav =
      presentations.length > 1
        ? `
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px;">
              <button type="button" data-variant-step="-1" onclick="changePresentation(-1)" aria-label="Tamaño anterior" style="cursor:pointer;border:none;border-radius:9999px;width:34px;height:34px;font-size:20px;line-height:1;font-weight:900;background:${isDark ? "#374151" : "#e5e7eb"};color:${strong};">‹</button>
              <span style="flex:1;text-align:center;font-size:0.7rem;font-weight:800;letter-spacing:0.06em;text-transform:uppercase;color:${muted};">${presentation.label} · ${shown + 1} de ${presentations.length}</span>
              <button type="button" data-variant-step="1" onclick="changePresentation(1)" aria-label="Tamaño siguiente" style="cursor:pointer;border:none;border-radius:9999px;width:34px;height:34px;font-size:20px;line-height:1;font-weight:900;background:${isDark ? "#374151" : "#e5e7eb"};color:${strong};">›</button>
            </div>
            <div style="display:flex;flex-wrap:wrap;gap:6px;margin-bottom:14px;">
              ${presentations
                .map(function (option, index) {
                  const isCurrent = index === shown;
                  return `<button type="button" data-variant-index="${index}" onclick="showPresentation(${index})" style="cursor:pointer;border-radius:9999px;padding:6px 12px;font-size:0.75rem;font-weight:700;border:2px solid ${isCurrent ? "#38e07b" : isDark ? "#4b5563" : "#e5e7eb"};background:${isCurrent ? "#38e07b" : "transparent"};color:${isCurrent ? "#ffffff" : muted};">${option.label} · $${option.price.toFixed(2)}</button>`;
                })
                .join("")}
            </div>`
        : "";

    return `
            <div style="display:flex;flex-wrap:wrap;gap:24px;text-align:left;">
                <div style="flex:1;min-width:200px;" class="rounded-xl">
                    <img src="${imageUrl}" alt="${presentation.name}" style="width:100%;height:280px;object-fit:cover;border-radius:12px;${presentation.available ? "" : "filter:grayscale(1);"}" />
                </div>
                <div style="flex:1;min-width:200px;display:flex;flex-direction:column;">
                    ${variantNav}
                    <h2 style="font-size:1.875rem;font-weight:900;color:${strong};margin-bottom:8px;">${presentation.name}</h2>
                    <p style="font-size:2.5rem;font-weight:900;color:#38e07b;margin-bottom:16px;">$${presentation.price.toFixed(2)}</p>
                    <p style="color:${muted};margin-bottom:24px;flex:1;">${presentation.description || "Sin descripción disponible"}</p>
                    ${presentation.available ? "" : '<p style="font-weight:800;color:#dc2626;font-size:0.8rem;margin:0;">AGOTADO por el momento</p>'}
                </div>
            </div>
          `;
  }

  /** Salta a una forma concreta del platillo. */
  function showPresentation(index) {
    if (index < 0 || index >= openPresentations.length) return;
    openPresentationIndex = index;
    currentItem = openPresentations[index];
    paintPresentation();
  }

  /** Pasa a la forma anterior o a la siguiente, dando la vuelta en los extremos. */
  function changePresentation(step) {
    const total = openPresentations.length;
    if (total === 0) return;
    showPresentation((openPresentationIndex + step + total) % total);
  }

  /** Vuelve a dibujar el diálogo con la forma que toca. */
  function paintPresentation() {
    const body = document.getElementById("item-presentation");
    if (!body) return;
    body.innerHTML = presentationHtml(openPresentations, openPresentationIndex, isDarkMode());
  }

  /** Abre el diálogo de la tarjeta pulsada, con sus tamaños si los tiene. */
  function openItemModal(card) {
    const isDark = isDarkMode();

    if (card.dataset.itemAvailable !== "true") {
      Swal.fire({
        icon: "error",
        title: "Producto Agotado",
        text: "Este producto no está disponible por el momento debido a falta de stock.",
        confirmButtonColor: "#38e07b",
        confirmButtonText: "Entendido",
        background: isDark ? "#1f2937" : "#ffffff",
        color: isDark ? "#f3f4f6" : "#1f2937",
      });
      return;
    }

    // Todas las formas del platillo, empezando por la que anuncia la tarjeta.
    openPresentations = itemPresentations(card);
    openPresentationIndex = openPresentations.findIndex(function (presentation) {
      return String(presentation.id) === String(card.dataset.itemId);
    });
    if (openPresentationIndex < 0) openPresentationIndex = 0;
    currentItem = openPresentations[openPresentationIndex];

    Swal.fire({
      html: `<div id="item-presentation">${presentationHtml(
        openPresentations,
        openPresentationIndex,
        isDark,
      )}</div>`,
      background: isDark ? "#1f2937" : "#ffffff",
      color: isDark ? "#f3f4f6" : "#1f2937",
      confirmButtonColor: "#38e07b",
      confirmButtonText: "Cerrar",
      width: "900px",
      showCloseButton: true,
      customClass: {
        popup: "rounded-2xl",
        htmlContainer: "p-0",
        confirmButton: "rounded-xl px-6 py-3 font-bold",
        actions: "mt-6",
      },
      backdrop: "rgba(0,0,0,0.6)",
    });
  }

  /**
   * Nombre del platillo junto con los de sus tamaños, para la búsqueda: escribir
   * "grande" tiene que encontrar la pizza, aunque el tamaño no sea una tarjeta.
   */
  function menuItemSearchText(card) {
    const parts = [card.dataset.itemName || ""];
    (sizeMapping()[parseInt(card.dataset.itemId, 10)] || []).forEach(function (child) {
      parts.push(child.sizeName || child.name || "");
    });
    return parts.join(" ").toLowerCase();
  }

  window.openItemModal = openItemModal;
  window.showPresentation = showPresentation;
  window.changePresentation = changePresentation;
  window.menuItemSearchText = menuItemSearchText;
})();
