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

  function cssEscape(value) {
    if (window.CSS && CSS.escape) {
      return CSS.escape(value);
    }
    return String(value || "").replace(/\\/g, "\\\\").replace(/"/g, "\\\"");
  }

  function replaceAllText(value, from, to) {
    return String(value || "").split(from).join(to);
  }

  function nextEditableRowIndex(listName) {
    const selector = `[data-textus-list="${cssEscape(listName)}"]:not([data-textus-template])`;
    return document.querySelectorAll(selector).length + 1;
  }

  function replaceEditableRowIndex(element, key) {
    const walk = document.createTreeWalker(element, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
    if (element.nodeType === Node.ELEMENT_NODE) {
      replaceEditableElementIndex(element, key);
    }
    while (walk.nextNode()) {
      const node = walk.currentNode;
      if (node.nodeType === Node.TEXT_NODE) {
        node.nodeValue = replaceAllText(node.nodeValue, "__new_index__", key);
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        replaceEditableElementIndex(node, key);
      }
    }
  }

  function replaceEditableElementIndex(element, key) {
    Array.from(element.attributes || []).forEach(attribute => {
      if (attribute.value.indexOf("__new_index__") >= 0) {
        element.setAttribute(attribute.name, replaceAllText(attribute.value, "__new_index__", key));
      }
    });
    if ("value" in element && typeof element.value === "string") {
      element.value = replaceAllText(element.value, "__new_index__", key);
    }
  }

  function addEditableRow(listName) {
    const template = document.querySelector(`[data-textus-template="${cssEscape(listName)}"]`);
    if (!template) {
      return;
    }
    const key = `new_${nextEditableRowIndex(listName)}`;
    const row = template.cloneNode(true);
    row.hidden = false;
    row.removeAttribute("hidden");
    row.removeAttribute("data-textus-template");
    row.removeAttribute("data-textus-action");
    row.setAttribute("data-textus-row", key);
    replaceEditableRowIndex(row, key);
    const controls = document.querySelector(`[data-textus-add-row-controls="${cssEscape(listName)}"]`);
    if (controls && controls.parentNode) {
      controls.parentNode.insertBefore(row, controls);
    } else {
      template.parentNode.insertBefore(row, template.nextSibling);
    }
  }

  document.addEventListener("click", event => {
    const add = event.target.closest && event.target.closest("[data-textus-add-row]");
    if (add) {
      event.preventDefault();
      addEditableRow(add.getAttribute("data-textus-add-row") || "");
      return;
    }
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
