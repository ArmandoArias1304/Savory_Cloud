/**
 * Prevents the mouse wheel from changing a focused/hovered <input type="number">.
 * Native number inputs increment on wheel, which silently corrupts amounts
 * while the user is scrolling the page.
 */
(function () {
  if (window.__preventNumberWheel) return;
  window.__preventNumberWheel = true;

  function isNumberInput(el) {
    return (
      el &&
      el.tagName === "INPUT" &&
      String(el.type || "").toLowerCase() === "number"
    );
  }

  document.addEventListener(
    "wheel",
    function (e) {
      if (isNumberInput(e.target)) {
        e.preventDefault();
      }
    },
    { passive: false, capture: true }
  );
})();
