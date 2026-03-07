/**
 * SISTEMA DE TOGGLE DE TEMA - EL GRAN SAZÓN
 *
 * Este script maneja el cambio entre modo claro y oscuro
 * usando la clase "dark" que Tailwind CSS reconoce automáticamente
 */

(function () {
  "use strict";

  // Nombre de la clave en localStorage
  const THEME_KEY = "elgransazon-theme";
  const DARK_MODE_CLASS = "dark";

  /**
   * Obtiene el tema guardado en localStorage
   * @returns {string} 'dark' o 'light'
   */
  function getSavedTheme() {
    return localStorage.getItem(THEME_KEY) || "light";
  }

  /**
   * Guarda el tema en localStorage
   * @param {string} theme - 'dark' o 'light'
   */
  function saveTheme(theme) {
    localStorage.setItem(THEME_KEY, theme);
  }

  /**
   * Aplica el tema al HTML element (Tailwind usa la clase en <html>)
   * @param {string} theme - 'dark' o 'light'
   */
  function applyTheme(theme) {
    const htmlElement = document.documentElement;

    if (!htmlElement) {
      return;
    }

    if (theme === "dark") {
      htmlElement.classList.add(DARK_MODE_CLASS);
    } else {
      htmlElement.classList.remove(DARK_MODE_CLASS);
    }
  }

  /**
   * Alterna entre modo claro y oscuro
   */
  function toggleTheme() {
    const htmlElement = document.documentElement;

    if (!htmlElement) {
      return;
    }

    const currentTheme = htmlElement.classList.contains(DARK_MODE_CLASS)
      ? "dark"
      : "light";
    const newTheme = currentTheme === "dark" ? "light" : "dark";

    applyTheme(newTheme);
    saveTheme(newTheme);

    // Disparar evento personalizado
    const event = new CustomEvent("themeChanged", {
      detail: { theme: newTheme },
    });
    document.dispatchEvent(event);

    console.log(`✅ Tema cambiado a: ${newTheme}`);
  }

  /**
   * Configura el botón de toggle
   */
  function setupToggleButton() {
    const toggleBtn = document.getElementById("theme-toggle-btn");

    if (toggleBtn) {
      toggleBtn.addEventListener("click", toggleTheme);
      console.log("🔘 Botón de toggle configurado");
    }
  }

  /**
   * Inicialización
   */
  function initialize() {
    // El tema ya se aplicó en el script inline del head
    // Solo necesitamos configurar el botón
    setupToggleButton();
    console.log("🎨 Sistema de temas listo");
  }

  // Esperar a que el DOM esté listo
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initialize);
  } else {
    initialize();
  }

  // Exponer funciones globalmente
  window.themeToggle = {
    toggle: toggleTheme,
    setTheme: function (theme) {
      applyTheme(theme);
      saveTheme(theme);
    },
    getTheme: function () {
      const htmlElement = document.documentElement;
      if (!htmlElement) return getSavedTheme();
      return htmlElement.classList.contains(DARK_MODE_CLASS) ? "dark" : "light";
    },
  };
})();
