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
      ".textus-calltree-collapsed-hidden{display:none!important}",
      ".textus-calltree-dim{opacity:.35}",
      ".textus-calltree-row{position:relative}",
      ".textus-calltree-row[data-calltree-kind='operation']>details{background:rgba(var(--bs-primary-rgb),.06)}",
      ".textus-calltree-row[data-calltree-kind='uow']>details{background:rgba(var(--bs-success-rgb),.07)}",
      ".textus-calltree-row[data-calltree-kind='space']>details{background:rgba(var(--bs-info-rgb),.08)}",
      ".textus-calltree-row[data-calltree-kind='io']>details,.textus-calltree-row[data-calltree-kind='io-error']>details{background:rgba(var(--bs-warning-rgb),.10)}",
      ".textus-calltree-row[data-calltree-kind='datastore']>details{background:rgba(var(--bs-warning-rgb),.08)}",
      ".textus-calltree-toggle{width:1.75rem;height:1.75rem;line-height:1;padding:0}",
      ".textus-calltree-lane{position:absolute;top:0;bottom:0;left:.45rem;border-left:2px solid var(--bs-border-color)}",
      ".textus-calltree-row[data-calltree-depth='1']{background:rgba(var(--bs-secondary-rgb),.03)}",
      ".textus-calltree-row [data-calltree-parent]{border-left:1px solid var(--bs-border-color);padding-left:.5rem}",
      ".textus-calltree-row-highlight{background:var(--bs-primary-bg-subtle)!important}",
      ".textus-calltree-match>details>summary{outline:2px solid var(--bs-warning);outline-offset:2px;background:var(--bs-warning-bg-subtle)}",
      ".textus-calltree-node-error>details{border-left:.25rem solid var(--bs-danger);padding-left:.5rem;background:var(--bs-danger-bg-subtle)}",
      ".textus-calltree-node-real-io>details{border-left:.25rem solid var(--bs-warning);padding-left:.5rem;background:var(--bs-warning-bg-subtle)}",
      ".textus-calltree-node-sql>details{border-left:.25rem solid var(--bs-info);padding-left:.5rem;background:var(--bs-info-bg-subtle)}",
      ".textus-calltree-duration{white-space:nowrap}",
      ".textus-calltree-long summary{cursor:pointer}",
      ".textus-calltree-long pre{max-height:18rem;overflow:auto}",
      ".textus-calltree-payload>code{white-space:normal}",
      ".textus-calltree-payload-body code{white-space:pre}",
      ".textus-calltree-payload-toggle{display:inline-block}",
      ".textus-calltree-payload-body{max-height:18rem;overflow:auto}"
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
    var boundary = node.closest ? node.closest("[data-textus-calltree]") : null;
    var current = node;
    while (current && current !== document && current !== boundary) {
      if (current.tagName && current.tagName.toLowerCase() === "details") {
        current.open = true;
      }
      current = current.parentNode;
    }
  }

  function openRow(node) {
    var detail = directDetails(node);
    if (detail) {
      detail.open = true;
    }
  }

  function directDetails(node) {
    return node.querySelector(":scope > details");
  }

  function directSummary(node) {
    var detail = directDetails(node);
    return detail ? detail.querySelector(":scope > summary") : null;
  }

  function directChildContainer(node) {
    var detail = directDetails(node);
    if (!detail) {
      return null;
    }
    var containers = detail.querySelectorAll("[data-calltree-children]");
    for (var i = 0; i < containers.length; i += 1) {
      if (containers[i].closest("[data-calltree-node]") === node) {
        return containers[i];
      }
    }
    return null;
  }

  function directChildNodes(node) {
    var container = directChildContainer(node);
    if (!container) {
      return [];
    }
    return Array.prototype.filter.call(container.children, function (child) {
      return child.matches && child.matches("[data-calltree-node]");
    });
  }

  function nodeHasIo(node) {
    var kind = node.getAttribute("data-calltree-kind") || "";
    return kind.indexOf("io") >= 0 ||
      kind === "datastore" ||
      node.querySelector("[data-calltree-observation-kind='io'],[data-calltree-observation-kind='io-error'],[data-calltree-observation-kind='datastore'],[data-calltree-attribute-key='sql']");
  }

  function nodeOwnQuery(node, selector) {
    var detail = directDetails(node);
    if (!detail) {
      return null;
    }
    var candidates = detail.querySelectorAll(selector);
    for (var i = 0; i < candidates.length; i += 1) {
      if (candidates[i].closest("[data-calltree-node]") === node) {
        return candidates[i];
      }
    }
    return null;
  }

  function nodeOwnAttribute(node, key) {
    return nodeOwnQuery(node, "[data-calltree-attributes] [data-calltree-attribute-key='" + key + "']");
  }

  function nodeOwnObservation(node, selector) {
    return nodeOwnQuery(node, "[data-calltree-observations] " + selector);
  }

  function nodeOwnHasIo(node) {
    var kind = node.getAttribute("data-calltree-kind") || "";
    return kind.indexOf("io") >= 0 ||
      kind === "datastore" ||
      !!nodeOwnObservation(node, "[data-calltree-observation-kind='io'],[data-calltree-observation-kind='io-error'],[data-calltree-observation-kind='datastore']") ||
      !!nodeOwnAttribute(node, "sql");
  }

  function nodeHasRealIo(node) {
    return (node.getAttribute("data-calltree-real-io") || "").toLowerCase() === "true" ||
      !!node.querySelector("[data-calltree-observation-real-io='true']");
  }

  function nodeOwnHasRealIo(node) {
    var kind = node.getAttribute("data-calltree-kind") || "";
    var ownRealIo = (node.getAttribute("data-calltree-real-io") || "").toLowerCase() === "true";
    return ((kind.indexOf("io") >= 0 || kind === "datastore") && ownRealIo) ||
      !!nodeOwnObservation(node, "[data-calltree-observation-real-io='true']");
  }

  function pairOf(node) {
    return node.getAttribute("data-calltree-pair") || "";
  }

  function depthOf(node) {
    var value = parseInt(node.getAttribute("data-calltree-depth") || "0", 10);
    return isNaN(value) ? 0 : value;
  }

  function isPairPrefix(parent, child) {
    return !!parent && !!child && (parent === child || child.indexOf(parent + "-") === 0);
  }

  function isDescendantPair(parent, child) {
    return !!parent && !!child && child.indexOf(parent + "-") === 0;
  }

  function nodeByPair(root, pair) {
    return root.querySelector("[data-calltree-node][data-calltree-pair='" + pair + "']");
  }

  function ancestorPairs(pair) {
    var parts = pair.split("-");
    var result = [];
    for (var i = 1; i < parts.length; i += 1) {
      result.push(parts.slice(0, i).join("-"));
    }
    return result;
  }

  function setNodeExpanded(node, expanded) {
    node.setAttribute("data-calltree-expanded", expanded ? "true" : "false");
    if (expanded) {
      openRow(node);
      openAncestors(node);
      directChildNodes(node).forEach(openRow);
    }
    var button = directSummary(node) ? directSummary(node).querySelector("[data-calltree-toggle]") : null;
    if (button) {
      button.textContent = expanded ? "\u25be" : "\u25b8";
      button.setAttribute("aria-expanded", expanded ? "true" : "false");
      button.title = expanded ? "Collapse child steps" : "Expand child steps";
    }
    var childContainer = directChildContainer(node);
    if (childContainer) {
      childContainer.classList.toggle("textus-calltree-collapsed-hidden", !expanded);
    }
  }

  function expandAncestorsByPair(root, pair) {
    ancestorPairs(pair).forEach(function (ancestorPair) {
      var ancestor = nodeByPair(root, ancestorPair);
      if (ancestor) {
        setNodeExpanded(ancestor, true);
      }
    });
  }

  function hasChildNodes(root, pair) {
    var node = nodeByPair(root, pair);
    return !!(node && directChildContainer(node));
  }

  function refreshExpansion(root) {
    nodes(root).forEach(function (node) {
      var childContainer = directChildContainer(node);
      if (childContainer) {
        childContainer.classList.toggle("textus-calltree-collapsed-hidden", node.getAttribute("data-calltree-expanded") !== "true");
      }
    });
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
      var matchesIo = !showIoOnly || nodeOwnHasIo(node);
      var matchesRealIo = !showRealIoOnly || nodeOwnHasRealIo(node);
      var visible = matchesSearch && matchesIo && matchesRealIo;
      if (visible) {
        visibleNodes.add(node);
        if (query && matchesSearch) {
          matchingNodes.add(node);
        }
        expandAncestorsByPair(root, pairOf(node));
        openRow(node);
        openAncestors(node);
        var pair = pairOf(node);
        allNodes.forEach(function (candidate) {
          if (isPairPrefix(pairOf(candidate), pair)) {
            visibleNodes.add(candidate);
            openRow(candidate);
            openAncestors(candidate);
          }
        });
      }
    });

    allNodes.forEach(function (node) {
      var visible = visibleNodes.has(node);
      var matched = matchingNodes.has(node);
      var filtered = query || showIoOnly || showRealIoOnly;
      node.classList.toggle("textus-calltree-hidden", filtered && !visible);
      node.classList.toggle("textus-calltree-match", matched);
      node.classList.toggle("textus-calltree-dim", !!query && visible && !matched);
    });
    refreshExpansion(root);
  }

  function setupNodeExpansion(root) {
    nodes(root).forEach(function (node) {
      var pair = pairOf(node);
      if (!hasChildNodes(root, pair)) {
        return;
      }
      var summary = directSummary(node);
      if (!summary || summary.querySelector("[data-calltree-toggle]")) {
        return;
      }
      var button = document.createElement("button");
      button.type = "button";
      button.className = "btn btn-sm btn-outline-secondary textus-calltree-toggle";
      button.setAttribute("data-calltree-toggle", "");
      button.setAttribute("aria-label", "Toggle child steps");
      button.addEventListener("click", function (event) {
        event.preventDefault();
        event.stopPropagation();
        setNodeExpanded(node, node.getAttribute("data-calltree-expanded") !== "true");
        refreshExpansion(root);
      });
      summary.insertBefore(button, summary.firstChild);
      setNodeExpanded(node, depthOf(node) === 0);
      if (depthOf(node) <= 1) {
        openRow(node);
      }
    });
    refreshExpansion(root);
  }

  function setAllNodeExpansion(root, expanded) {
    nodes(root).forEach(function (node) {
      var summary = directSummary(node);
      if (summary && summary.querySelector("[data-calltree-toggle]")) {
        setNodeExpanded(node, expanded || depthOf(node) === 0);
      }
    });
    refreshExpansion(root);
    refresh(root);
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

  function parseAttributeJson(text) {
    if (!text) {
      return null;
    }
    try {
      var parsed = JSON.parse(text);
      if (typeof parsed === "string") {
        var trimmed = parsed.trim();
        if (trimmed.charAt(0) === "{" || trimmed.charAt(0) === "[") {
          return JSON.parse(trimmed);
        }
      }
      return parsed;
    } catch (_) {
      return null;
    }
  }

  function payloadPrettyText(parsed, raw) {
    if (parsed == null) {
      return raw;
    }
    return JSON.stringify(sanitizeValue(parsed), null, 2);
  }

  function compactText(value) {
    value = String(value == null ? "" : value);
    return value.length <= 120 ? value : value.slice(0, 117) + "...";
  }

  function payloadScalar(value) {
    if (value == null) {
      return null;
    }
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      return String(value);
    }
    return null;
  }

  function payloadSummary(value) {
    if (value && typeof value === "object" && !Array.isArray(value)) {
      var parts = [];
      if (value.kind) parts.push(String(value.kind));
      if (value.record_count != null) parts.push("records=" + String(value.record_count));
      if (value.field_count != null) parts.push("fields=" + String(value.field_count));
      if (value.size_bytes != null) parts.push(String(value.size_bytes) + " bytes");
      if (value.char_count != null) parts.push(String(value.char_count) + " chars");
      if (value.inline != null && value.inline !== false && typeof value.inline !== "object") {
        parts.push("inline=" + compactText(value.inline));
      }
      return parts.length ? parts.join(" ") : compactText(JSON.stringify(value));
    }
    return compactText(payloadScalar(value) || JSON.stringify(value));
  }

  function payloadOneLine(value) {
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return ["void", "unit", "null", "none"].indexOf(String(value.kind || "")) >= 0;
    }
    return payloadScalar(value) != null && String(value).length <= 120;
  }

  function payloadExternal(value) {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      return null;
    }
    var hrefKeys = ["external_href", "external_url", "payload_href", "payload_url", "href", "url"];
    for (var i = 0; i < hrefKeys.length; i += 1) {
      if (value[hrefKeys[i]]) {
        return { value: String(value[hrefKeys[i]]), href: true };
      }
    }
    var refKeys = ["external_path", "payload_path", "file_path", "path", "file", "ref"];
    for (var j = 0; j < refKeys.length; j += 1) {
      if (value[refKeys[j]]) {
        return { value: String(value[refKeys[j]]), href: false };
      }
    }
    return null;
  }

  function appendText(element, className, textValue) {
    var child = document.createElement("span");
    if (className) {
      child.className = className;
    }
    child.textContent = textValue;
    element.appendChild(child);
    return child;
  }

  function enhancePayloadAttributes(root) {
    Array.prototype.forEach.call(root.querySelectorAll("dd[data-calltree-attribute][data-calltree-attribute-key='result'],dd[data-calltree-attribute][data-calltree-attribute-key='response']"), function (attribute) {
      if (attribute.getAttribute("data-calltree-payload-enhanced") === "true") {
        return;
      }
      if (attribute.querySelector("[data-calltree-payload]")) {
        attribute.setAttribute("data-calltree-payload-enhanced", "true");
        attribute.setAttribute("data-calltree-long-enhanced", "true");
        return;
      }
      attribute.setAttribute("data-calltree-payload-enhanced", "true");
      attribute.setAttribute("data-calltree-long-enhanced", "true");
      var key = attribute.getAttribute("data-calltree-attribute-key") || "result";
      var raw = (attribute.textContent || "").trim();
      var parsed = parseAttributeJson(raw);
      var payload = parsed == null ? raw : parsed;
      var external = payloadExternal(payload);
      while (attribute.firstChild) {
        attribute.removeChild(attribute.firstChild);
      }
      var wrapper = document.createElement("div");
      wrapper.className = "textus-calltree-payload";
      wrapper.setAttribute("data-calltree-payload", key);
      var code = document.createElement("code");
      code.textContent = payloadSummary(payload);
      wrapper.appendChild(code);
      if (external) {
        if (external.href) {
          var link = document.createElement("a");
          link.className = "btn btn-sm btn-outline-secondary ms-2";
          link.href = external.value;
          link.textContent = "Open external " + key;
          wrapper.appendChild(link);
        } else {
          appendText(wrapper, "badge text-bg-light ms-2", "external: " + external.value);
        }
      }
      if (!payloadOneLine(payload) || raw.length > 120 || raw.indexOf("\n") >= 0) {
        var payloadId = "textus-calltree-payload-" + Math.random().toString(36).slice(2);
        var controls = document.createElement("div");
        controls.className = "mt-1";
        var toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "btn btn-sm btn-link p-0 textus-calltree-payload-toggle";
        toggle.setAttribute("data-calltree-payload-toggle", "");
        toggle.setAttribute("aria-controls", payloadId);
        toggle.setAttribute("aria-expanded", "false");
        toggle.textContent = "Show " + key;
        var pre = document.createElement("pre");
        pre.id = payloadId;
        pre.className = "bg-light border rounded p-2 mt-1 mb-1 textus-calltree-payload-body";
        pre.hidden = true;
        var codeBlock = document.createElement("code");
        codeBlock.textContent = payloadPrettyText(parsed, raw);
        pre.appendChild(codeBlock);
        toggle.addEventListener("click", function () {
          var opening = pre.hidden;
          pre.hidden = !opening;
          toggle.setAttribute("aria-expanded", opening ? "true" : "false");
          toggle.textContent = (opening ? "Hide " : "Show ") + key;
        });
        controls.appendChild(toggle);
        wrapper.appendChild(controls);
        wrapper.appendChild(pre);
      }
      attribute.appendChild(wrapper);
    });
  }

  function attributeValue(container, key) {
    var element = container.querySelector("dd[data-calltree-attribute][data-calltree-attribute-key='" + key + "']");
    return element ? (element.textContent || "").trim() : "";
  }

  function removeAttributePair(container, key) {
    Array.prototype.forEach.call(container.querySelectorAll("[data-calltree-attribute-key='" + key + "']"), function (element) {
      element.remove();
    });
  }

  function compactDurationAttributes(root) {
    Array.prototype.forEach.call(root.querySelectorAll("[data-calltree-attributes]"), function (container) {
      if (container.getAttribute("data-calltree-duration-compacted") === "true") {
        return;
      }
      container.setAttribute("data-calltree-duration-compacted", "true");
      var millis = attributeValue(container, "duration_millis");
      var micros = attributeValue(container, "duration_micros");
      var nanos = attributeValue(container, "duration_nanos");
      if (!millis && !micros && !nanos) {
        return;
      }
      removeAttributePair(container, "duration_millis");
      removeAttributePair(container, "duration_micros");
      removeAttributePair(container, "duration_nanos");

      var primary = millis ? millis + " ms" : (micros ? micros + " \u00b5s" : nanos + " ns");
      var details = [];
      if (micros) {
        details.push(micros + " \u00b5s");
      }
      if (nanos) {
        details.push(nanos + " ns");
      }

      var dt = document.createElement("dt");
      dt.className = "col-sm-3";
      dt.setAttribute("data-calltree-attribute-key", "duration");
      dt.textContent = "duration";
      var dd = document.createElement("dd");
      dd.className = "col-sm-9 textus-calltree-duration";
      dd.setAttribute("data-calltree-attribute", "");
      dd.setAttribute("data-calltree-attribute-key", "duration");
      var code = document.createElement("code");
      code.textContent = details.length ? primary + " (" + details.join(" / ") + ")" : primary;
      dd.appendChild(code);
      container.insertBefore(dd, container.firstChild);
      container.insertBefore(dt, dd);
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

  function bindPairHighlight(root) {
    nodes(root).forEach(function (node) {
      var pair = pairOf(node);
      if (!pair) {
        return;
      }
      node.addEventListener("mouseenter", function () {
        nodes(root).forEach(function (candidate) {
          candidate.classList.toggle("textus-calltree-row-highlight", pairOf(candidate) === pair);
        });
      });
      node.addEventListener("mouseleave", function () {
        nodes(root).forEach(function (candidate) {
          candidate.classList.remove("textus-calltree-row-highlight");
        });
      });
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
      setAllNodeExpansion(root, true);
    });
    element.querySelector("[data-calltree-collapse]").addEventListener("click", function () {
      setAllNodeExpansion(root, false);
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
    setupNodeExpansion(root);
    compactDurationAttributes(root);
    enhancePayloadAttributes(root);
    collapseLongAttributes(root);
    bindPairHighlight(root);
    root.insertBefore(toolbar(root), root.firstChild);
  }

  function init() {
    Array.prototype.forEach.call(document.querySelectorAll("[data-textus-calltree]"), enhance);
  }

  window.TextusCallTree = window.TextusCallTree || {};
  window.TextusCallTree.enhance = enhance;
  window.TextusCallTree.enhanceAll = init;

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
