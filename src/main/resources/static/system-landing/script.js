/* ============================================================
   SAVORY CLOUD — Landing Page Scripts
   ============================================================ */

/* ─── FPS Monitor + Performance Diagnostics ─── */
(function initFPSMonitor() {
  let frameCount = 0;
  let lastTime = performance.now();
  const FPS_WARN_THRESHOLD = 50;
  const SAMPLE_INTERVAL = 1000; // ms

  function measure() {
    frameCount++;
    const now = performance.now();
    const elapsed = now - lastTime;

    if (elapsed >= SAMPLE_INTERVAL) {
      const fps = Math.round((frameCount * 1000) / elapsed);
      if (fps < FPS_WARN_THRESHOLD) {
      }
      frameCount = 0;
      lastTime = now;
    }
    requestAnimationFrame(measure);
  }
  requestAnimationFrame(measure);
})();

/* ─── Scroll event frequency monitor ─── */
(function initScrollDiag() {
  let scrollEvents = 0;
  let lastReport = performance.now();

  window.addEventListener(
    "scroll",
    () => {
      scrollEvents++;
      const now = performance.now();
      if (now - lastReport >= 3000) {
        const rate = Math.round(scrollEvents / ((now - lastReport) / 1000));
        if (rate > 120) {
        }
        scrollEvents = 0;
        lastReport = now;
      }
    },
    { passive: true },
  );
})();

/* ─── GPU acceleration audit (one-time log) ─── */
(function gpuAudit() {
  window.addEventListener("load", () => {
    const auditSelectors = [
      ".anim-fade-in-up",
      ".titulo-revelado .word",
      ".pinned-section",
      ".text-track",
      ".intro-hero__char",
    ];
    auditSelectors.forEach((sel) => {
      const el = document.querySelector(sel);
      if (!el) return;
      const cs = getComputedStyle(el);
      const transform = cs.transform || cs.webkitTransform || "none";
      const willChange = cs.willChange || "auto";
    });
  });
})();

document.addEventListener("DOMContentLoaded", () => {
  /* ─── Intro Hero: letter split + sequential glow + auto-dismiss ─── */
  const introHero = document.getElementById("introHero");
  const introTitle = document.getElementById("introTitle");

  if (introHero && introTitle) {
    const text = introTitle.textContent;
    introTitle.textContent = "";

    // Split into per-char spans with staggered glow delay
    const GLOW_STAGGER = 0.09; // seconds between each letter's glow start
    const GLOW_DURATION = 1.6; // seconds — matches CSS animation duration
    let lastCharIndex = 0;

    [...text].forEach((char, i) => {
      const span = document.createElement("span");
      if (char === " ") {
        span.innerHTML = "&nbsp;";
        span.classList.add("intro-hero__char", "intro-hero__char--space");
      } else {
        span.textContent = char;
        span.classList.add("intro-hero__char");
        span.style.setProperty("--glow-delay", `${i * GLOW_STAGGER}s`);
        lastCharIndex = i;
      }
      introTitle.appendChild(span);
    });

    // Auto-dismiss: last letter delay + animation duration + small buffer
    const totalGlowTime =
      (lastCharIndex * GLOW_STAGGER + GLOW_DURATION + 0.3) * 1000;

    function dismissIntro() {
      introHero.classList.add("is-leaving");

      introHero.addEventListener("transitionend", function onEnd(e) {
        if (e.propertyName !== "opacity") return;
        introHero.removeEventListener("transitionend", onEnd);
        introHero.classList.add("is-hidden");
        window.scrollTo({ top: 0, behavior: "instant" });

        // Trigger green swipe animation
        const greenSwipe = document.getElementById("greenSwipe");
        if (greenSwipe) {
          greenSwipe.classList.add("is-active");
          // After animation, reveal main content
          greenSwipe.addEventListener("animationend", function onSwipeEnd() {
            greenSwipe.classList.remove("is-active");
            greenSwipe.style.display = "none";
            greenSwipe.removeEventListener("animationend", onSwipeEnd);
            // Reveal main content
            const mainContent = document.getElementById("mainContent");
            if (mainContent) {
              mainContent.style.opacity = "1";
              mainContent.style.pointerEvents = "auto";
            }
          });
        }
      });
    }

    setTimeout(dismissIntro, totalGlowTime);
  }

  /* ─── Rolling Text: split links into per-letter spans ─── */
  const STAGGER = 0.03; // seconds between each letter
  document.querySelectorAll(".navbar__links a.rolling-text").forEach((link) => {
    const text = link.textContent;
    link.textContent = "";
    [...text].forEach((char, i) => {
      const span = document.createElement("span");
      if (char === " ") {
        span.innerHTML = "&nbsp;";
        span.classList.add("char", "char-space");
      } else {
        span.textContent = char;
        span.classList.add("char");
      }
      span.style.setProperty("--char-delay", `${i * STAGGER}s`);
      link.appendChild(span);
    });
  });

  /* ─── Theme Toggle ─── */
  const html = document.documentElement;
  const themeToggle = document.getElementById("themeToggle");
  const STORAGE_KEY = "savory-theme";

  // Load saved theme or default to dark
  const savedTheme = localStorage.getItem(STORAGE_KEY) || "dark";
  html.setAttribute("data-theme", savedTheme);

  themeToggle.addEventListener("click", () => {
    const current = html.getAttribute("data-theme");
    const next = current === "dark" ? "light" : "dark";
    html.setAttribute("data-theme", next);
    localStorage.setItem(STORAGE_KEY, next);
  });

  /* ─── Navbar scroll effect ─── */
  const navbar = document.getElementById("navbar");
  const scrollThreshold = 40;

  function handleNavScroll() {
    if (window.scrollY > scrollThreshold) {
      navbar.classList.add("is-scrolled");
    } else {
      navbar.classList.remove("is-scrolled");
    }
  }

  window.addEventListener("scroll", handleNavScroll, { passive: true });
  handleNavScroll(); // run on init

  /* ─── Mobile nav toggle → Wheel Picker ─── */
  const navToggle = document.getElementById("navToggle");
  const wheelMenu = document.getElementById("wheelMenu");
  const wheelTrack = document.getElementById("wheelTrack");
  const wheelItems = wheelTrack
    ? Array.from(wheelTrack.querySelectorAll(".wheel-menu__item"))
    : [];

  // Dynamic item height — reads the actual rendered size (adapts to S/M/L CSS)
  function getItemH() {
    return wheelItems[0] ? wheelItems[0].offsetHeight : 64;
  }

  // Detect which section the user is currently viewing
  function getCurrentSectionIndex() {
    // Use getBoundingClientRect for reliable position regardless of nesting
    const vpMid = window.innerHeight / 2;
    let bestIdx = 0;
    let bestDist = Infinity;

    for (let i = 0; i < wheelItems.length; i++) {
      const href = wheelItems[i].getAttribute("href");
      const sec = href ? document.querySelector(href) : null;
      if (!sec) continue;

      const rect = sec.getBoundingClientRect();
      // Section is "current" if the viewport middle is inside it
      if (rect.top <= vpMid && rect.bottom >= vpMid) {
        return i;
      }
      // Otherwise find the closest section top to viewport middle
      const dist = Math.abs(rect.top - vpMid);
      if (dist < bestDist) {
        bestDist = dist;
        bestIdx = i;
      }
    }
    return bestIdx;
  }

  function openWheel() {
    navToggle.classList.add("is-active");
    wheelMenu.classList.add("is-open");
    document.body.style.overflow = "hidden";
    // Scroll to the item matching the section the user is currently on
    const idx = getCurrentSectionIndex();
    wheelTrack.scrollTop = idx * getItemH();
    updateWheelFocus();
  }

  function closeWheel() {
    navToggle.classList.remove("is-active");
    wheelMenu.classList.remove("is-open");
    document.body.style.overflow = "";
  }

  function updateWheelFocus() {
    const trackRect = wheelTrack.getBoundingClientRect();
    const centerY = trackRect.top + trackRect.height / 2;
    const itemH = getItemH();

    wheelItems.forEach((item) => {
      const rect = item.getBoundingClientRect();
      const itemCenterY = rect.top + rect.height / 2;
      const dist = Math.abs(itemCenterY - centerY);

      item.classList.remove("is-centered", "is-adjacent");

      if (dist < itemH * 0.5) {
        item.classList.add("is-centered");
      } else if (dist < itemH * 1.5) {
        item.classList.add("is-adjacent");
      }
    });
  }

  if (navToggle && wheelMenu && wheelTrack) {
    navToggle.addEventListener("click", () => {
      if (wheelMenu.classList.contains("is-open")) {
        closeWheel();
      } else {
        openWheel();
      }
    });

    // Update focus on scroll
    wheelTrack.addEventListener("scroll", updateWheelFocus, { passive: true });

    // Click on centered item → navigate & close
    wheelItems.forEach((item) => {
      item.addEventListener("click", (e) => {
        if (!item.classList.contains("is-centered")) {
          e.preventDefault();
          // Scroll so clicked item becomes centered
          const idx = wheelItems.indexOf(item);
          wheelTrack.scrollTo({
            top: idx * getItemH(),
            behavior: "smooth",
          });
        } else {
          // Item is centered — navigate
          closeWheel();
        }
      });
    });

    // Close on backdrop click
    wheelMenu
      .querySelector(".wheel-menu__backdrop")
      .addEventListener("click", closeWheel);

    // Close button
    var wheelCloseBtn = document.getElementById("wheelClose");
    if (wheelCloseBtn) wheelCloseBtn.addEventListener("click", closeWheel);
  }

  /* ─── SpotlightCard: cursor-following green glow on feature cards ─── */
  document.querySelectorAll(".feature-card").forEach((card) => {
    const spotlight = card.querySelector(".feature-card__spotlight");
    if (!spotlight) return;

    let spotRAF = false;
    card.addEventListener(
      "mousemove",
      (e) => {
        if (spotRAF) return;
        spotRAF = true;
        requestAnimationFrame(() => {
          const rect = card.getBoundingClientRect();
          const x = e.clientX - rect.left;
          const y = e.clientY - rect.top;
          spotlight.style.setProperty("--spot-x", `${x}px`);
          spotlight.style.setProperty("--spot-y", `${y}px`);
          spotRAF = false;
        });
      },
      { passive: true },
    );
  });

  /* ─── Device Carousel: staggered slide transitions ─── */
  const dcSection = document.querySelector(".device-carousel");
  if (dcSection) {
    const slides = dcSection.querySelectorAll(".dc__slide");
    const dots = dcSection.querySelectorAll(".dc__dot");
    const prevBtn = dcSection.querySelector(".dc__arrow--prev");
    const nextBtn = dcSection.querySelector(".dc__arrow--next");
    let dcCurrent = 0;
    let dcAnimating = false;
    const EASE = "cubic-bezier(0.4, 0, 0.2, 1)";
    const DURATION = 520; // ms per element
    const STAGGER_DELAY = 150; // ms text lags behind image

    /* ── Swoosh sound from MP3 file ── */
    const swooshSound = new Audio("/system-landing/sound/swoosh.mp3");
    swooshSound.volume = 0.35;

    function playSwoosh() {
      swooshSound.currentTime = 0;
      swooshSound.play().catch(() => {});
    }

    function dcGo(to, direction) {
      if (dcAnimating || to === dcCurrent || to < 0 || to >= slides.length)
        return;
      dcAnimating = true;
      playSwoosh(direction);

      const outSlide = slides[dcCurrent];
      const inSlide = slides[to];
      const outVisual = outSlide.querySelector(".dc__visual");
      const outText = outSlide.querySelector(".dc__text");
      const inVisual = inSlide.querySelector(".dc__visual");
      const inText = inSlide.querySelector(".dc__text");

      // Direction multiplier: 1 = going right (next), -1 = going left (prev)
      const dir = direction === "prev" ? 1 : -1;

      // Show incoming slide (keep absolute, make visible)
      inSlide.style.opacity = "1";
      inSlide.style.pointerEvents = "none";
      inSlide.classList.remove("is-active");

      // Set incoming elements start position (from opposite side)
      inVisual.style.transition = "none";
      inText.style.transition = "none";
      inVisual.style.transform = `translateX(${-dir * 100}%)`;
      inVisual.style.opacity = "0";
      inText.style.transform = `translateX(${-dir * 100}%)`;
      inText.style.opacity = "0";

      // Force reflow so transitions pick up from the set position
      void inVisual.offsetWidth;

      // ── Phase 1: Outgoing visual exits ──
      outVisual.style.transition = `transform ${DURATION}ms ${EASE}, opacity ${DURATION}ms ${EASE}`;
      outVisual.style.transform = `translateX(${dir * 100}%)`;
      outVisual.style.opacity = "0";

      // ── Phase 2: Outgoing text exits (staggered) ──
      setTimeout(() => {
        outText.style.transition = `transform ${DURATION}ms ${EASE}, opacity ${DURATION}ms ${EASE}`;
        outText.style.transform = `translateX(${dir * 100}%)`;
        outText.style.opacity = "0";
      }, STAGGER_DELAY);

      // ── Phase 3: Incoming visual enters ──
      setTimeout(() => {
        inVisual.style.transition = `transform ${DURATION}ms ${EASE}, opacity ${DURATION}ms ${EASE}`;
        inVisual.style.transform = "translateX(0)";
        inVisual.style.opacity = "1";
      }, DURATION * 0.5);

      // ── Phase 4: Incoming text enters (staggered) ──
      setTimeout(
        () => {
          inText.style.transition = `transform ${DURATION}ms ${EASE}, opacity ${DURATION}ms ${EASE}`;
          inText.style.transform = "translateX(0)";
          inText.style.opacity = "1";
        },
        DURATION * 0.5 + STAGGER_DELAY,
      );

      // ── Cleanup after all transitions done ──
      const totalTime = DURATION * 1.5 + STAGGER_DELAY + 50;
      setTimeout(() => {
        // Reset outgoing slide
        outSlide.classList.remove("is-active");
        outSlide.style.opacity = "";
        outSlide.style.pointerEvents = "";
        outVisual.style.cssText = "";
        outText.style.cssText = "";

        // Activate incoming slide
        inSlide.classList.add("is-active");
        inSlide.style.opacity = "";
        inSlide.style.pointerEvents = "";
        inVisual.style.cssText = "";
        inText.style.cssText = "";

        dcCurrent = to;
        dcAnimating = false;
      }, totalTime);

      // Update dots
      dots.forEach((d, i) => d.classList.toggle("is-active", i === to));
    }

    prevBtn.addEventListener("click", () => {
      const prev = dcCurrent === 0 ? slides.length - 1 : dcCurrent - 1;
      dcGo(prev, "prev");
    });

    nextBtn.addEventListener("click", () => {
      const next = dcCurrent === slides.length - 1 ? 0 : dcCurrent + 1;
      dcGo(next, "next");
    });

    dots.forEach((dot) => {
      dot.addEventListener("click", () => {
        const idx = parseInt(dot.dataset.index, 10);
        const dir = idx > dcCurrent ? "next" : "prev";
        dcGo(idx, dir);
      });
    });
  }

  /* ─── Scroll-triggered animations (Intersection Observer) ─── */
  const animElements = document.querySelectorAll(".anim-fade-in-up");

  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            observer.unobserve(entry.target);
          }
        });
      },
      {
        threshold: 0.12,
        rootMargin: "0px 0px -40px 0px",
      },
    );

    animElements.forEach((el) => observer.observe(el));
  } else {
    // Fallback: show all immediately
    animElements.forEach((el) => el.classList.add("is-visible"));
  }

  /* ─── Word Reveal: split titles into per-word spans ─── */
  const WORD_STAGGER = 0.18; // seconds between each word

  document.querySelectorAll(".titulo-revelado").forEach((el) => {
    // Walk through all child nodes (text + elements like <span>, <br>)
    const fragment = document.createDocumentFragment();
    let wordIndex = 0;

    function wrapTextWords(node) {
      if (node.nodeType === Node.TEXT_NODE) {
        // Split text by whitespace, keeping separators
        const parts = node.textContent.split(/(\s+)/);
        parts.forEach((part) => {
          if (/^\s*$/.test(part)) {
            // Preserve whitespace as-is
            if (part) fragment.appendChild(document.createTextNode(part));
          } else {
            const span = document.createElement("span");
            span.classList.add("word");
            span.textContent = part;
            span.style.setProperty(
              "--word-delay",
              `${wordIndex * WORD_STAGGER}s`,
            );
            wordIndex++;
            fragment.appendChild(span);
          }
        });
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        // Clone the element (e.g. <span class="text-accent-serif">, <br>)
        const clone = node.cloneNode(false);
        if (node.tagName === "BR") {
          fragment.appendChild(clone);
          return;
        }
        // Wrap inner text of this child element in .word spans too
        const inner = document.createDocumentFragment();
        const prevFragment = fragment; // save ref
        node.childNodes.forEach((child) => {
          if (child.nodeType === Node.TEXT_NODE) {
            const parts = child.textContent.split(/(\s+)/);
            parts.forEach((part) => {
              if (/^\s*$/.test(part)) {
                if (part) inner.appendChild(document.createTextNode(part));
              } else {
                const span = document.createElement("span");
                span.classList.add("word");
                span.textContent = part;
                span.style.setProperty(
                  "--word-delay",
                  `${wordIndex * WORD_STAGGER}s`,
                );
                wordIndex++;
                inner.appendChild(span);
              }
            });
          } else {
            inner.appendChild(child.cloneNode(true));
          }
        });
        clone.appendChild(inner);
        fragment.appendChild(clone);
      }
    }

    // Process all child nodes of the title
    Array.from(el.childNodes).forEach(wrapTextWords);
    el.textContent = "";
    el.appendChild(fragment);
  });

  // Observe each .titulo-revelado, add .is-revealed once visible
  const revealObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-revealed");
          revealObserver.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.2, rootMargin: "0px 0px -30px 0px" },
  );

  document.querySelectorAll(".titulo-revelado").forEach((el) => {
    revealObserver.observe(el);
  });

  /* ─── Smooth scroll for anchor links (enhancement) ─── */
  document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
    anchor.addEventListener("click", function (e) {
      const target = document.querySelector(this.getAttribute("href"));
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: "smooth", block: "start" });
      }
    });
  });

  /* ─── Active nav link highlight on scroll ─── */
  const sections = document.querySelectorAll("section[id]");
  const navAnchors = document.querySelectorAll('.navbar__links a[href^="#"]');

  function highlightNav() {
    const scrollY = window.scrollY + 120;
    let activeId = null;

    // Walk backwards so we find the LAST section whose top is above scrollY
    for (let i = sections.length - 1; i >= 0; i--) {
      const section = sections[i];
      if (scrollY >= section.offsetTop) {
        activeId = section.getAttribute("id");
        break;
      }
    }

    if (activeId) {
      navAnchors.forEach((a) => {
        a.classList.toggle(
          "is-active-link",
          a.getAttribute("href") === "#" + activeId,
        );
      });
    }
  }

  window.addEventListener("scroll", highlightNav, { passive: true });

  /* ─── GSAP ScrollTrigger: Pinned Text Transition ─── */
  if (typeof gsap !== "undefined" && typeof ScrollTrigger !== "undefined") {
    gsap.registerPlugin(ScrollTrigger);

    // Global GSAP perf settings
    gsap.config({ force3D: true });
    ScrollTrigger.config({ limitCallbacks: true });

    const pinSection = document.getElementById("pinnedMessages");
    const textFirst = pinSection.querySelector(".pinned-section__text--first");
    const textSecond = pinSection.querySelector(
      ".pinned-section__text--second",
    );
    const textThird = pinSection.querySelector(".pinned-section__text--third");

    // Create a timeline controlled by scroll position (scrub).
    // Using scrub: 0.6 for a slight smoothing lag instead of true (instant).
    const tl = gsap.timeline({
      scrollTrigger: {
        trigger: pinSection,
        pin: true,
        start: "top top",
        end: "+=350%",
        scrub: 0.8, // smoother scrub (was 0.6)
        anticipatePin: 1,
        fastScrollEnd: true, // snap completion on fast scroll
      },
    });

    // ── Text 1 → fade out ──
    tl.to(textFirst, {
      opacity: 0,
      y: -50,
      duration: 1,
      ease: "power2.inOut",
      force3D: true,
    });

    // ── Text 2 → fade in ──
    tl.to(textSecond, {
      opacity: 1,
      y: 0,
      duration: 1,
      ease: "power2.inOut",
      force3D: true,
    });

    // ── Hold text 2 ──
    tl.to(textSecond, { opacity: 1, duration: 0.5 });

    // ── Text 2 → fade out ──
    tl.to(textSecond, {
      opacity: 0,
      y: -50,
      duration: 1,
      ease: "power2.inOut",
      force3D: true,
    });

    // ── Text 3 → fade in ──
    tl.to(textThird, {
      opacity: 1,
      y: 0,
      duration: 1,
      ease: "power2.inOut",
      force3D: true,
    });

    // ── Hold text 3 ──
    tl.to(textThird, { opacity: 1, duration: 0.6 });

    // ── Falling phase: each word in text 3 falls with physics-like trajectory ──
    const ftWords = textThird.querySelectorAll(".ft-word");
    const fallData = [];
    ftWords.forEach((word, i) => {
      const seed = (i + 1) * 137.5;
      const randX = Math.sin(seed) * 100 + (i % 2 === 0 ? -50 : 50);
      const randY = 180 + Math.abs(Math.cos(seed) * 150);
      const randRot = Math.sin(seed * 0.7) * 40;
      fallData.push({ x: randX, y: randY, rotation: randRot });
    });

    ftWords.forEach((word, i) => {
      tl.to(
        word,
        {
          y: fallData[i].y,
          x: fallData[i].x,
          rotation: fallData[i].rotation,
          opacity: 0,
          duration: 0.8,
          ease: "power2.in",
          force3D: true,
        },
        tl.duration() + i * 0.05, // stagger the falling
      );
    });

    /* ─── GSAP Horizontal Scroll Section ─── */
    const hSection = document.getElementById("horizontalScroll");
    if (hSection) {
      const track = hSection.querySelector(".text-track");
      const items = hSection.querySelectorAll(".text-track__item");

      /* ── Split accent words into per-letter spans ── */
      items.forEach((item) => {
        const accents = item.querySelectorAll(".text-accent-serif");
        accents.forEach((accent) => {
          const text = accent.textContent;
          accent.textContent = "";
          [...text].forEach((ch, i) => {
            const span = document.createElement("span");
            span.classList.add("hs-char");
            if (ch === " ") {
              span.innerHTML = "&nbsp;";
            } else {
              span.textContent = ch;
              // Mark ~every 3rd letter for glow effect (not all)
              if (i % 3 === 0) span.classList.add("hs-char-glow");
            }
            accent.appendChild(span);
          });
        });
      });

      // Animation presets for each data-anim type
      const animPresets = {
        drop: {
          from: { y: -60, opacity: 0, rotation: -8 },
          to: { y: 0, opacity: 1, rotation: 0 },
        },
        scale: { from: { scale: 0, opacity: 0 }, to: { scale: 1, opacity: 1 } },
        flip: {
          from: { rotationX: 90, opacity: 0, y: 20 },
          to: { rotationX: 0, opacity: 1, y: 0 },
        },
        build: {
          from: { y: 50, opacity: 0, scaleY: 0.3 },
          to: { y: 0, opacity: 1, scaleY: 1 },
        },
        wave: { from: { y: 30, opacity: 0 }, to: { y: 0, opacity: 1 } },
      };

      // Track which item is currently active to avoid re-triggering
      let currentActive = null;
      let rafPending = false;

      function updateActiveItem() {
        const center = window.innerWidth / 2;
        let closest = null;
        let closestDist = Infinity;

        items.forEach((item) => {
          const rect = item.getBoundingClientRect();
          const itemCenter = rect.left + rect.width / 2;
          const dist = Math.abs(itemCenter - center);
          if (dist < closestDist) {
            closestDist = dist;
            closest = item;
          }
        });

        items.forEach((item) => {
          if (item === closest) {
            item.classList.add("is-active");
          } else {
            item.classList.remove("is-active");
          }
        });

        if (closest !== currentActive) {
          if (currentActive) resetItem(currentActive);
          currentActive = closest;
          if (closest) animateItemIn(closest);
        }
        rafPending = false;
      }

      function animateItemIn(item) {
        const chars = item.querySelectorAll(".hs-char");
        if (!chars.length) return;
        const type = item.dataset.anim || "drop";
        const preset = animPresets[type] || animPresets.drop;

        // Kill any running tweens on these chars
        gsap.killTweensOf(chars);

        // Set initial state
        gsap.set(chars, preset.from);

        // Stagger animation in
        const staggerOpts = { each: 0.05, from: "start" };
        if (type === "wave") staggerOpts.each = 0.07;

        gsap.to(chars, {
          ...preset.to,
          duration: type === "wave" ? 0.6 : 0.5,
          stagger: staggerOpts,
          ease:
            type === "flip"
              ? "back.out(1.4)"
              : type === "wave"
                ? "elastic.out(1.2, 0.5)"
                : "power3.out",
          overwrite: true,
        });
      }

      function resetItem(item) {
        const chars = item.querySelectorAll(".hs-char");
        if (!chars.length) return;
        gsap.killTweensOf(chars);
        gsap.set(chars, { clearProps: "all" });
      }

      // Calculate how far the track needs to move:
      // total track width minus one viewport width
      function getScrollDistance() {
        return track.scrollWidth - window.innerWidth;
      }

      // Horizontal movement tween — GSAP handles ALL pinning
      gsap.to(track, {
        x: () => -getScrollDistance(),
        ease: "none",
        force3D: true,
        scrollTrigger: {
          trigger: hSection,
          start: "top top",
          end: () => `+=${getScrollDistance()}`,
          pin: true,
          scrub: 0.5, // smooth scrub (was true = instant)
          invalidateOnRefresh: true,
          anticipatePin: 1,
          fastScrollEnd: true,
          onUpdate: () => {
            // Throttle active-item detection to 1 per frame
            if (!rafPending) {
              rafPending = true;
              requestAnimationFrame(updateActiveItem);
            }
          },
        },
      });
    }
  }

  /* ─── Data en Vivo: organic equalizer bars (paused off-screen) ─── */
  const eqContainer = document.getElementById("dlEqualizer");
  if (eqContainer) {
    const bars = eqContainer.querySelectorAll(".dl__bar");
    let eqIntervalId = null;

    function randomizeBar(bar) {
      const h = Math.random() * 80 + 10; // 10% – 90%
      const op = 0.5 + Math.random() * 0.5; // 0.5 – 1.0
      bar.style.height = h + "%";
      bar.style.opacity = op;
    }

    function startEqualizer() {
      if (eqIntervalId) return;
      eqIntervalId = setInterval(() => {
        const count = Math.floor(bars.length * 0.4) + 1;
        for (let i = 0; i < count; i++) {
          const idx = Math.floor(Math.random() * bars.length);
          randomizeBar(bars[idx]);
        }
      }, 180);
    }

    function stopEqualizer() {
      if (eqIntervalId) {
        clearInterval(eqIntervalId);
        eqIntervalId = null;
      }
    }

    // Only run the equalizer when the section is visible
    const eqSection = eqContainer.closest("section") || eqContainer;
    const eqObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            startEqualizer();
          } else {
            stopEqualizer();
          }
        });
      },
      { threshold: 0.0, rootMargin: "50px" },
    );
    eqObserver.observe(eqSection);
  }

  /* ─── Gallery Carousel: Device Mockup Swiper ─── */
  const galleryEl = document.querySelector(".gallery__swiper");
  if (galleryEl && typeof Swiper !== "undefined") {
    new Swiper(galleryEl, {
      slidesPerView: 1,
      spaceBetween: 20,
      grabCursor: true,
      speed: 600,
      loop: true,

      // Responsive breakpoints
      breakpoints: {
        // ≥ 768px → 1 slide (tablet)
        768: {
          slidesPerView: 1,
          spaceBetween: 24,
        },
        // ≥ 1024px → 2 slides (desktop: paired mockups, advance 2 at a time)
        1024: {
          slidesPerView: 2,
          slidesPerGroup: 2,
          spaceBetween: 32,
        },
      },

      // Navigation arrows
      navigation: {
        nextEl: ".gallery__nav-next",
        prevEl: ".gallery__nav-prev",
      },

      // Pagination dots
      pagination: {
        el: ".gallery__pagination",
        clickable: true,
      },
    });
  }
  /* ─── Features Carousel (fc) ─── */
  (function initFeaturesCarousel() {
    const track = document.getElementById("fcTrack");
    if (!track) return;

    const cards = Array.from(track.querySelectorAll(".fc__card"));
    const dotsContainer = document.getElementById("fcDots");
    const prevBtn = document.getElementById("fcPrev");
    const nextBtn = document.getElementById("fcNext");
    const TOTAL = cards.length;
    let current = 0;
    let fcAnimating = false;
    const ANIM_DURATION = 620;

    /* Crear dots */
    cards.forEach((_, i) => {
      const dot = document.createElement("button");
      dot.className = "fc__dot" + (i === 0 ? " is-active" : "");
      dot.setAttribute("aria-label", `Función ${i + 1}`);
      dot.addEventListener("click", () => goTo(i));
      dotsContainer.appendChild(dot);
    });

    function getDots() {
      return Array.from(dotsContainer.querySelectorAll(".fc__dot"));
    }

    function applyState(idx) {
      cards.forEach((card, i) => {
        if (i === idx) {
          card.classList.remove("fc--inactive");
        } else {
          card.classList.add("fc--inactive");
        }
      });
      getDots().forEach((d, i) => d.classList.toggle("is-active", i === idx));
    }

    function goTo(idx) {
      if (fcAnimating || idx === current || idx < 0 || idx >= TOTAL) return;
      fcAnimating = true;
      current = idx;
      applyState(current);

      // --- Scroll automático en móvil para centrar la tarjeta activa ---
      // Solo aplica si el ancho de la pantalla es menor a 768px
      if (window.innerWidth < 768) {
        const activeCard = cards[current];
        const trackRect = track.getBoundingClientRect();
        const cardRect = activeCard.getBoundingClientRect();
        // Calcula el scroll para centrar la tarjeta activa
        const scrollLeft =
          activeCard.offsetLeft - trackRect.width / 2 + cardRect.width / 2;
        track.scrollTo({ left: scrollLeft, behavior: "smooth" });
      }

      setTimeout(() => {
        fcAnimating = false;
      }, ANIM_DURATION);
    }

    /* Hover en inactiva → no cambia el estado del carrusel, solo CSS */
    cards.forEach((card, i) => {
      card.addEventListener("click", () => {
        if (card.classList.contains("fc--inactive")) goTo(i);
      });
    });

    prevBtn.addEventListener("click", () =>
      goTo(current === 0 ? TOTAL - 1 : current - 1),
    );
    nextBtn.addEventListener("click", () =>
      goTo(current === TOTAL - 1 ? 0 : current + 1),
    );

    /* Autoplay: avanza cada 5s si el usuario no interactúa */
    let autoplayTimer = setInterval(autoplay, 5000);
    function autoplay() {
      goTo(current === TOTAL - 1 ? 0 : current + 1);
    }
    function resetAutoplay() {
      clearInterval(autoplayTimer);
      autoplayTimer = setInterval(autoplay, 5000);
    }
    [prevBtn, nextBtn].forEach((b) =>
      b.addEventListener("click", resetAutoplay),
    );
    cards.forEach((c) => c.addEventListener("click", resetAutoplay));

    /* Activar al entrar en viewport (IntersectionObserver) */
    const fcSection = document.getElementById("features");
    const fcObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            applyState(current);
            fcObserver.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.25 },
    );
    fcObserver.observe(fcSection);

    /* Teclado: flechas cuando el foco está en la sección */
    track.setAttribute("tabindex", "0");
    track.addEventListener("keydown", (e) => {
      if (e.key === "ArrowRight" || e.key === "ArrowDown") {
        e.preventDefault();
        goTo(current === TOTAL - 1 ? 0 : current + 1);
        resetAutoplay();
      }
      if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
        e.preventDefault();
        goTo(current === 0 ? TOTAL - 1 : current - 1);
        resetAutoplay();
      }
    });

    /* Swipe táctil (desktop también lo soporta con pointer events) */
    let touchStartX = 0;
    track.addEventListener("pointerdown", (e) => {
      touchStartX = e.clientX;
    });
    track.addEventListener("pointerup", (e) => {
      const diff = touchStartX - e.clientX;
      if (Math.abs(diff) > 50) {
        diff > 0
          ? goTo(current === TOTAL - 1 ? 0 : current + 1)
          : goTo(current === 0 ? TOTAL - 1 : current - 1);
        resetAutoplay();
      }
    });
  })();
  /* ── About Us cards: tap en móvil activa animación ── */
  document.querySelectorAll(".about-us__icon-item").forEach((card) => {
    card.addEventListener("click", () => {
      const isActive = card.classList.contains("is-active");
      document
        .querySelectorAll(".about-us__icon-item")
        .forEach((c) => c.classList.remove("is-active"));
      if (!isActive) card.classList.add("is-active");
    });
  });
});
