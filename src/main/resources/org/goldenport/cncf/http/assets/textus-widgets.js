/*! Textus widget baseline */
(() => {
  "use strict";

  const interactiveSelector = "a, button, input, select, textarea, label, summary, [role='button']";

  function isInteractiveTarget(target) {
    return target && target.closest && target.closest(interactiveSelector);
  }

  function openRow(row) {
    const href = row.getAttribute("data-textus-row-href");
    if (href) {
      window.location.href = href;
    }
  }

  document.addEventListener("click", event => {
    const row = event.target.closest && event.target.closest("[data-textus-row-href]");
    if (!row || isInteractiveTarget(event.target)) {
      return;
    }
    openRow(row);
  });

  document.addEventListener("keydown", event => {
    if (event.key !== "Enter" && event.key !== " ") {
      return;
    }
    const row = event.target.closest && event.target.closest("[data-textus-row-href]");
    if (!row || isInteractiveTarget(event.target)) {
      return;
    }
    event.preventDefault();
    openRow(row);
  });

  document.documentElement.dataset.textusWidgets = "ready";
})();
