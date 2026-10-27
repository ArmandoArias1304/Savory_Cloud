/**
 * ============================================================
 * ANUNCIOS DE PROMOCIONES
 * ============================================================
 *
 * Abre los anuncios de promociones al entrar a la landing y los
 * recorre como carrusel cuando hay más de uno.
 *
 * El marcado lo pinta el fragmento landing-promotions.html; aquí solo
 * se muestra, se cambia de anuncio y se cierra.
 *
 * Reglas:
 * - Si no hay anuncios en el documento, no se hace nada.
 * - Se abre una sola vez por carga de página.
 * - Los controles de navegación solo aparecen cuando hay 2 o más.
 * - Cerrar con la X, con Escape, tocando fuera del anuncio o con el
 *   enlace "Ver todas las promociones". Al cerrar se libera el scroll
 *   y el foco vuelve a donde estaba.
 */
(function () {
  'use strict';

  var modal = document.querySelector('[data-promo-announcements]');
  if (!modal) {
    return;
  }

  var dialog = modal.querySelector('[data-promo-modal-dialog]');
  var slides = Array.prototype.slice.call(
    modal.querySelectorAll('[data-promo-slide]')
  );

  // Sin anuncios no hay nada que mostrar (el controller no pinta el modal
  // si no hay promociones, pero esto cubre un HTML editado a mano).
  if (slides.length === 0) {
    modal.remove();
    return;
  }

  var closeButton = modal.querySelector('[data-promo-close]');
  var nav = modal.querySelector('[data-promo-nav]');
  var prevButton = modal.querySelector('[data-promo-prev]');
  var nextButton = modal.querySelector('[data-promo-next]');
  var dotsContainer = modal.querySelector('[data-promo-dots]');
  var counter = modal.querySelector('[data-promo-counter]');
  var current = 0;
  var dots = [];
  var lastFocused = null;
  var touchStartX = null;

  function show(index) {
    if (index < 0 || index >= slides.length) {
      return;
    }

    current = index;

    slides.forEach(function (slide, position) {
      var active = position === current;
      slide.classList.toggle('is-active', active);
      slide.setAttribute('aria-hidden', active ? 'false' : 'true');
    });

    dots.forEach(function (dot, position) {
      var active = position === current;
      dot.classList.toggle('is-active', active);
      dot.setAttribute('aria-selected', active ? 'true' : 'false');
    });

    if (counter) {
      // El texto del contador se reescribe entero; el número de anuncios
      // sale de los propios slides, no de un atributo aparte.
      counter.textContent =
        'Promoción ' + (current + 1) + ' de ' + slides.length;
    }
  }

  function previous() {
    show(current === 0 ? slides.length - 1 : current - 1);
  }

  function next() {
    show(current === slides.length - 1 ? 0 : current + 1);
  }

  function buildDots() {
    if (!dotsContainer) {
      return;
    }

    dotsContainer.innerHTML = '';
    dots.length = 0;

    slides.forEach(function (slide, position) {
      var dot = document.createElement('button');
      dot.type = 'button';
      dot.className = 'promo-announcements__dot';
      dot.setAttribute(
        'aria-label',
        'Ver promoción ' + (position + 1) + ' de ' + slides.length
      );
      dotsContainer.appendChild(dot);
      dots.push(dot);
    });
  }

  function close() {
    modal.classList.add('is-closing');

    window.setTimeout(function () {
      modal.hidden = true;
      modal.setAttribute('aria-hidden', 'true');
      modal.classList.remove('is-closing');
      document.body.classList.remove('promo-modal-open');

      if (lastFocused && typeof lastFocused.focus === 'function') {
        lastFocused.focus();
      }
    }, 180);
  }

  function open() {
    lastFocused = document.activeElement;
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('promo-modal-open');
    show(0);

    if (closeButton) {
      closeButton.focus();
    }
  }

  // Con un solo anuncio no tiene sentido un carrusel: se esconde la
  // navegación y el contador, y el anuncio se queda como aviso simple.
  if (slides.length > 1) {
    // Thymeleaf ya pinta un punto por promoción; solo se construyen si
    // faltan (por ejemplo, en una página de prueba escrita a mano).
    dots = Array.prototype.slice.call(
      modal.querySelectorAll('[data-promo-dot]')
    );

    if (dots.length !== slides.length) {
      buildDots();
    }

    show(0);
  } else if (nav) {
    nav.hidden = true;
  }

  if (counter && slides.length === 1) {
    counter.hidden = true;
  }

  dots.forEach(function (dot, position) {
    dot.addEventListener('click', function () {
      show(position);
    });
  });

  if (closeButton) {
    closeButton.addEventListener('click', close);
  }

  if (prevButton) {
    prevButton.addEventListener('click', previous);
  }

  if (nextButton) {
    nextButton.addEventListener('click', next);
  }

  // Los enlaces "Ver todas las promociones" también cierran el anuncio.
  Array.prototype.forEach.call(
    modal.querySelectorAll('[data-promo-close]'),
    function (element) {
      if (element !== closeButton) {
        element.addEventListener('click', close);
      }
    }
  );

  modal.addEventListener('click', function (event) {
    // Clic en el fondo (fuera del diálogo) cierra el anuncio.
    if (dialog && !dialog.contains(event.target)) {
      close();
    }
  });

  // Deslizar con el dedo en móvil.
  if (dialog) {
    dialog.addEventListener(
      'touchstart',
      function (event) {
        touchStartX = event.changedTouches[0].clientX;
      },
      { passive: true }
    );

    dialog.addEventListener(
      'touchend',
      function (event) {
        if (touchStartX === null || slides.length < 2) {
          return;
        }

        var distance = event.changedTouches[0].clientX - touchStartX;
        touchStartX = null;

        if (Math.abs(distance) < 48) {
          return;
        }

        if (distance > 0) {
          previous();
        } else {
          next();
        }
      },
      { passive: true }
    );
  }

  document.addEventListener('keydown', function (event) {
    if (modal.hidden) {
      return;
    }

    if (event.key === 'Escape') {
      event.preventDefault();
      close();
      return;
    }

    if (slides.length > 1 && event.key === 'ArrowLeft') {
      event.preventDefault();
      previous();
    }

    if (slides.length > 1 && event.key === 'ArrowRight') {
      event.preventDefault();
      next();
    }
  });

  // En las landings hay una pantalla de bienvenida (splash) que tapa la
  // página durante un par de segundos. Los anuncios esperan a que termine
  // para no reemplazar la intro; si no hay splash, se abren de inmediato.
  function openWhenLandingIsReady() {
    var splash = document.getElementById('splashScreen');
    if (!splash || splash.hidden || getComputedStyle(splash).display === 'none') {
      open();
      return;
    }

    var opened = false;

    function finish() {
      if (opened) {
        return;
      }
      opened = true;
      observer.disconnect();
      window.clearTimeout(fallback);
      open();
    }

    var observer = new MutationObserver(function () {
      if (splash.hidden || splash.style.display === 'none') {
        finish();
      }
    });
    observer.observe(splash, {
      attributes: true,
      attributeFilter: ['style', 'class', 'hidden'],
    });

    // Nunca dejamos al cliente esperando más de cuatro segundos por un
    // splash que no termina de irse.
    var fallback = window.setTimeout(finish, 4000);
  }

  openWhenLandingIsReady();
})();
