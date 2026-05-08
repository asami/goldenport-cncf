(function () {
  "use strict";

  var STYLE_ID = "textus-calltree-style";
  var nextToolbarId = 1;

  function ensureStyle() {
    if (document.getElementById(STYLE_ID)) {
      return;
    }
    var style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = [
      ".textus-calltree-toolbar{position:sticky;top:0;z-index:3;background:var(--bs-body-bg);border:1px solid var(--bs-border-color);border-radius:.5rem;padding:.5rem;margin-bottom:.75rem}",
      ".textus-calltree-hidden{display:none!important}",
      ".textus-calltree-dim{opacity:.35}",
      ".textus-calltree-match>details>summary{outline:2px solid var(--bs-warning);outline-offset:2px;background:var(--bs-warning-bg-subtle)}",
      ".textus-calltree-node-error>details{border-left:.25rem solid var(--bs-danger);padding-left:.5rem;background:var(--bs-danger-bg-subtle)}",
      ".textus-calltree-node-real-io>details{border-left:.25rem solid var(--bs-warning);padding-left:.5rem;background:var(--bs-warning-bg-subtle)}",
      ".textus-calltree-node-sql>details{border-left:.25rem solid var(--bs-info);padding-left:.5rem;background:var(--bs-info-bg-subtle)}",
      ".textus-calltree-long summary{cursor:pointer}",
      ".textus-calltree-long pre{max-height:18rem;overflow:auto}"
    ].join("\n");
    document.head.appendChild(style);
  }

  function text(node) {
    return (node.textContent || "").toLowerCase();
  }

  function nodes(root) {
    return Array.prototype.slice.call(root.querySelectorAll("[data-calltree-node]"));
  }

  function setOpen(root, open) {
    Array.prototype.forEach.call(root.querySelectorAll("details"), function (detail) {
      detail.open = open;
    });
  }

  function openAncestors(node) {
    var current = node;
    while (current && current !== document) {
      if (current.tagName && current.tagName.toLowerCase() === "details") {
        current.open = true;
      }
      current = current.parentNode;
    }
  }

  function nodeHasIo(node) {
    var kind = node.getAttribute("data-calltree-kind") || "";
    return kind.indexOf("io") >= 0 || kind === "datastore" || node.querySelector("[data-calltree-attribute-key='sql']");
  }

  function nodeHasRealIo(node) {
    return (node.getAttribute("data-calltree-real-io") || "").toLowerCase() === "true";
  }

  function refresh(root) {
    var query = (root.querySelector("[data-calltree-search]") || {}).value || "";
    query = query.trim().toLowerCase();
    var showIoOnly = !!(root.querySelector("[data-calltree-show-io]") || {}).checked;
    var showRealIoOnly = !!(root.querySelector("[data-calltree-show-real-io]") || {}).checked;

    var allNodes = nodes(root);
    var visibleNodes = new Set();
    var matchingNodes = new Set();

    allNodes.forEach(function (node) {
      var matchesSearch = !query || text(node).indexOf(query) >= 0;
      var matchesIo = !showIoOnly || nodeHasIo(node);
      var matchesRealIo = !showRealIoOnly || nodeHasRealIo(node);
      var visible = matchesSearch && matchesIo && matchesRealIo;
      if (visible) {
        visibleNodes.add(node);
        if (query && matchesSearch) {
          matchingNodes.add(node);
        }
        openAncestors(node);
        var current = node.parentElement;
        while (current && current !== root) {
          if (current.matches && current.matches("[data-calltree-node]")) {
            visibleNodes.add(current);
          }
          current = current.parentElement;
        }
      }
    });

    allNodes.forEach(function (node) {
      var visible = visibleNodes.has(node);
      var matched = matchingNodes.has(node);
      var filtered = query || showIoOnly || showRealIoOnly;
      node.classList.toggle("textus-calltree-hidden", filtered && !visible);
      node.classList.toggle("textus-calltree-match", matched);
      node.classList.toggle("textus-calltree-dim", filtered && visible && !matched);
    });
  }

  function collapseLongAttributes(root) {
    Array.prototype.forEach.call(root.querySelectorAll("[data-calltree-long-attribute]"), function (attribute) {
      if (attribute.getAttribute("data-calltree-long-enhanced") === "true") {
        return;
      }
      attribute.setAttribute("data-calltree-long-enhanced", "true");
      var key = attribute.getAttribute("data-calltree-attribute-key") || "attribute";
      var detail = document.createElement("details");
      detail.className = "textus-calltree-long";
      var summary = document.createElement("summary");
      summary.className = "small text-secondary";
      summary.textContent = "Show " + key;
      detail.appendChild(summary);
      while (attribute.firstChild) {
        detail.appendChild(attribute.firstChild);
      }
      attribute.appendChild(detail);
    });
  }

  function classifyNodes(root) {
    nodes(root).forEach(function (node) {
      var kind = node.getAttribute("data-calltree-kind") || "";
      if (kind === "io-error") {
        node.classList.add("textus-calltree-node-error");
      }
      if (nodeHasRealIo(node)) {
        node.classList.add("textus-calltree-node-real-io");
      }
      if (kind === "datastore" || node.querySelector("[data-calltree-attribute-key='sql']")) {
        node.classList.add("textus-calltree-node-sql");
      }
    });
  }

  function toolbar(root) {
    var element = document.createElement("div");
    var searchId = "textus-calltree-search-" + nextToolbarId++;
    element.className = "textus-calltree-toolbar d-flex flex-wrap gap-2 align-items-center";
    element.innerHTML = [
      '<button type="button" class="btn btn-sm btn-outline-secondary" data-calltree-expand>Expand all</button>',
      '<button type="button" class="btn btn-sm btn-outline-secondary" data-calltree-collapse>Collapse all</button>',
      '<label class="visually-hidden" for="' + searchId + '">Search</label>',
      '<input id="' + searchId + '" type="search" class="form-control form-control-sm w-auto flex-grow-1" placeholder="Search CallTree" data-calltree-search>',
      '<button type="button" class="btn btn-sm btn-outline-secondary" data-calltree-clear>Clear</button>',
      '<label class="form-check form-check-inline mb-0 small"><input class="form-check-input" type="checkbox" data-calltree-show-io> Show I/O only</label>',
      '<label class="form-check form-check-inline mb-0 small"><input class="form-check-input" type="checkbox" data-calltree-show-real-io> Show real I/O only</label>'
    ].join("");

    element.querySelector("[data-calltree-expand]").addEventListener("click", function () {
      setOpen(root, true);
    });
    element.querySelector("[data-calltree-collapse]").addEventListener("click", function () {
      setOpen(root, false);
    });
    element.querySelector("[data-calltree-clear]").addEventListener("click", function () {
      element.querySelector("[data-calltree-search]").value = "";
      element.querySelector("[data-calltree-show-io]").checked = false;
      element.querySelector("[data-calltree-show-real-io]").checked = false;
      refresh(root);
    });
    element.querySelector("[data-calltree-search]").addEventListener("input", function () {
      refresh(root);
    });
    element.querySelector("[data-calltree-show-io]").addEventListener("change", function () {
      refresh(root);
    });
    element.querySelector("[data-calltree-show-real-io]").addEventListener("change", function () {
      refresh(root);
    });
    return element;
  }

  function enhance(root) {
    if (root.getAttribute("data-calltree-enhanced") === "true") {
      return;
    }
    root.setAttribute("data-calltree-enhanced", "true");
    ensureStyle();
    classifyNodes(root);
    collapseLongAttributes(root);
    root.insertBefore(toolbar(root), root.firstChild);
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll("[data-textus-calltree]"), enhance);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
