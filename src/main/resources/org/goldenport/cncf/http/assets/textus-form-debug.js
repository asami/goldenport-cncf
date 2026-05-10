(function () {
  if (window.__textusFormDebugInstalled) return;
  window.__textusFormDebugInstalled = true;

  const originalFetch = window.fetch;
  if (!originalFetch) return;
  const carryoverKey = "textus.form.debug.carryover.v1";
  const carryoverTtlMs = 2 * 60 * 1000;
  const redactedValue = "[redacted]";
  const debugStyleId = "textus-form-debug-style";

  function ensureDebugPanelStyle() {
    if (document.getElementById(debugStyleId)) return;
    const style = document.createElement("style");
    style.id = debugStyleId;
    style.textContent = [
      ".textus-execution-debug-panel details:not([open]) > :not(summary) { display: none !important; }",
      "[data-textus-form-debug-panel] details:not([open]) > :not(summary) { display: none !important; }",
      ".textus-calltree-payload > code { white-space: normal; }",
      ".textus-calltree-payload-body code { white-space: pre; }",
      ".textus-calltree-payload-toggle { display: inline-block; }",
      ".textus-calltree-payload-body { max-height: 18rem; overflow: auto; }"
    ].join("\n");
    document.head.appendChild(style);
  }

  function isFormApiUrl(input) {
    try {
      const url = new URL(typeof input === "string" ? input : input.url, window.location.href);
      return url.pathname.indexOf("/form-api/") === 0;
    } catch (_) {
      return false;
    }
  }

  function requestUrl(input) {
    try {
      const url = new URL(typeof input === "string" ? input : input.url, window.location.href);
      for (const key of Array.from(url.searchParams.keys())) {
        if (isSensitiveKey(key)) url.searchParams.set(key, redactedValue);
      }
      return url.toString();
    } catch (_) {
      return redactText(String(input || ""));
    }
  }

  function operationName(input) {
    try {
      const url = new URL(typeof input === "string" ? input : input.url, window.location.href);
      const parts = url.pathname.split("/").filter(Boolean);
      if (parts.length >= 4 && parts[0] === "form-api") {
        return parts.slice(1, 4).join(".");
      }
      return url.pathname;
    } catch (_) {
      return "form-api";
    }
  }

  function isSensitiveKey(key) {
    const normalized = String(key || "").toLowerCase().replace(/[^a-z0-9]/g, "");
    return normalized.indexOf("password") >= 0 ||
      normalized.indexOf("passwd") >= 0 ||
      normalized.indexOf("secret") >= 0 ||
      normalized.indexOf("token") >= 0 ||
      normalized.indexOf("session") >= 0 ||
      normalized.indexOf("authorization") >= 0 ||
      normalized.indexOf("cookie") >= 0 ||
      normalized.indexOf("credential") >= 0 ||
      normalized.indexOf("apikey") >= 0 ||
      normalized.indexOf("privatekey") >= 0;
  }

  function sanitizeValue(value) {
    if (Array.isArray(value)) return value.map(sanitizeValue);
    if (value && typeof value === "object") {
      const out = {};
      Object.keys(value).forEach(function (key) {
        out[key] = isSensitiveKey(key) ? redactedValue : sanitizeValue(value[key]);
      });
      return out;
    }
    return value;
  }

  function sanitizeNamedValue(key, value) {
    return isSensitiveKey(key) ? redactedValue : sanitizeValue(value);
  }

  function redactText(text) {
    if (!text) return text;
    const sensitive = "(password|passwd|secret|token|access[-_]?session[-_]?id|refresh[-_]?session[-_]?id|session[-_]?id|session|authorization|cookie|credential|api[-_]?key|private[-_]?key)";
    const jsonLike = new RegExp('("' + sensitive + '"\\s*:\\s*)"[^"]*"', "gi");
    const formLike = new RegExp('(^|[?&\\s,;])(' + sensitive + ')(\\s*[=:]\\s*)([^&\\s,;]+)', "gi");
    return String(text)
      .replace(jsonLike, "$1\"" + redactedValue + "\"")
      .replace(formLike, "$1$2$3" + redactedValue);
  }

  function parseBody(body) {
    if (!body) return {};
    if (body instanceof URLSearchParams) {
      const out = {};
      for (const [key, value] of body.entries()) out[key] = sanitizeNamedValue(key, value);
      return out;
    }
    if (body instanceof FormData) {
      const out = {};
      for (const [key, value] of body.entries()) {
        const display = value && value.name ? "[file:" + value.name + "]" : String(value);
        out[key] = sanitizeNamedValue(key, display);
      }
      return out;
    }
    if (typeof body === "string") {
      try {
        const params = new URLSearchParams(body);
        const out = {};
        for (const [key, value] of params.entries()) out[key] = sanitizeNamedValue(key, value);
        return Object.keys(out).length ? out : redactText(body);
      } catch (_) {
        return redactText(body);
      }
    }
    return redactText(String(body));
  }

  function requestBody(input, init) {
    if (init && init.body) return parseBody(init.body);
    if (input && typeof input !== "string" && input.bodyUsed === false) return "[Request body not inspected]";
    return {};
  }

  function requestHeader(input, init, name) {
    const key = String(name || "").toLowerCase();
    function get(headers) {
      if (!headers) return "";
      try {
        if (typeof headers.get === "function") return headers.get(name) || headers.get(key) || "";
        if (Array.isArray(headers)) {
          const found = headers.find(entry => Array.isArray(entry) && String(entry[0]).toLowerCase() === key);
          return found ? String(found[1] || "") : "";
        }
        for (const headerName of Object.keys(headers)) {
          if (headerName.toLowerCase() === key) return String(headers[headerName] || "");
        }
      } catch (_) {
        return "";
      }
      return "";
    }
    return get(init && init.headers) || get(input && typeof input !== "string" && input.headers);
  }

  function requestMetadata(input, init) {
    return {
      kind: requestHeader(input, init, "x-textus-debug-request-kind") || "interactive",
      label: requestHeader(input, init, "x-textus-debug-label"),
      optional: requestHeader(input, init, "x-textus-debug-optional"),
      display: requestHeader(input, init, "x-textus-debug-display")
    };
  }

  function shouldShowSuccess(input, init) {
    const display = requestMetadata(input, init).display.toLowerCase();
    return display === "always" || display === "success" || display === "page";
  }

  function shouldCarryOver(metadata) {
    return (metadata.kind || "interactive") === "interactive";
  }

  function shouldInspect(input, init, response) {
    const metadata = requestMetadata(input, init);
    return !response.ok || shouldShowSuccess(input, init) || shouldCarryOver(metadata);
  }

  function shouldRender(input, init, response) {
    return !response.ok || shouldShowSuccess(input, init);
  }

  function hasServerExecutionPanel() {
    return Boolean(document.querySelector(".textus-execution-debug-panel"));
  }

  function closeDiagnosticsDetails(container) {
    if (!container) return;
    const details = container.matches && container.matches("details")
      ? container
      : container.querySelector("details");
    if (details) details.open = false;
  }

  function closeExistingDiagnosticsPanels() {
    document.querySelectorAll(".textus-execution-debug-panel, [data-textus-form-debug-panel]").forEach(closeDiagnosticsDetails);
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function parseJson(text) {
    if (!text) return null;
    try {
      const parsed = JSON.parse(text);
      if (typeof parsed === "string") {
        const trimmed = parsed.trim();
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
    if (parsed == null) return raw;
    return JSON.stringify(sanitizeValue(parsed), null, 2);
  }

  function extractCallTree(text) {
    const parsed = parseJson(text);
    const calltree = parsed && parsed.debug && parsed.debug.calltree;
    if (!calltree) return null;
    if (Array.isArray(calltree)) return calltree;
    if (Array.isArray(calltree.nodes)) return calltree.nodes;
    if (Array.isArray(calltree.calltree)) return calltree.calltree;
    return null;
  }

  function pretty(text) {
    if (!text) return "";
    const parsed = parseJson(text);
    if (parsed) {
      const sanitized = sanitizeValue(parsed);
      if (sanitized && sanitized.debug && sanitized.debug.calltree) {
        sanitized.debug.calltree = "[shown in CallTree panel]";
      }
      return JSON.stringify(sanitized, null, 2);
    }
    return redactText(text);
  }

  function callTreeKind(node, attrs) {
    attrs = attrs || {};
    return String(node && node.kind || attrs.calltree_kind || attrs.kind || "step");
  }

  function callTreeDisplayLabel(node, attrs, label) {
    attrs = attrs || {};
    return String(node && node.display_label || attrs.display_label || label || "CallTree node");
  }

  function callTreeHighlights(attrs) {
    attrs = attrs || {};
    return String(attrs.highlights || "")
      .split(/[,\s]+/)
      .map(function (x) { return x.trim(); })
      .filter(Boolean);
  }

  function callTreeHasHighlight(attrs, name) {
    return callTreeHighlights(attrs).indexOf(name) >= 0;
  }

  function callTreeBadges(attrs, kind) {
    attrs = attrs || {};
    const highlightBadges = callTreeHighlights(attrs).map(function (highlight) {
      let variant = "text-bg-secondary";
      if (highlight === "real_io") variant = "text-bg-warning";
      else if (highlight === "cache_hit") variant = "text-bg-info";
      return '<span class="badge ' + variant + ' ms-2" data-calltree-badge data-calltree-highlight="' + escapeHtml(highlight) + '">' + escapeHtml(highlight) + '</span>';
    });
    const keys = ["outcome", "duration_millis", "cache_layer", "source", "datastore"];
    return highlightBadges.concat(keys.map(function (key) {
      const value = attrs[key];
      if (value == null || String(value) === "") return "";
      let variant = "text-bg-secondary";
      if (key === "outcome" && /failure|failed|error/i.test(String(value))) variant = "text-bg-danger";
      else if (key === "outcome" && /success|succeeded/i.test(String(value))) variant = "text-bg-success";
      else if (key === "outcome" && String(value) === "start") variant = "text-bg-primary";
      else if (key === "cache_layer") variant = "text-bg-info";
      else if (key === "duration_millis") variant = "text-bg-light";
      const label = key === "duration_millis" ? String(value) + "ms" : key + "=" + String(value);
      return '<span class="badge ' + variant + ' ms-2" data-calltree-badge>' + escapeHtml(label) + '</span>';
    })).join("");
  }

  function compactText(value) {
    value = String(value == null ? "" : value);
    return value.length <= 120 ? value : value.slice(0, 117) + "...";
  }

  function payloadScalar(value) {
    if (value == null) return null;
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return String(value);
    return null;
  }

  function payloadSummary(value) {
    if (value && typeof value === "object" && !Array.isArray(value)) {
      const parts = [];
      if (value.kind) parts.push(String(value.kind));
      if (value.record_count != null) parts.push("records=" + String(value.record_count));
      if (value.field_count != null) parts.push("fields=" + String(value.field_count));
      if (value.size_bytes != null) parts.push(String(value.size_bytes) + " bytes");
      if (value.char_count != null) parts.push(String(value.char_count) + " chars");
      if (value.inline != null && value.inline !== false && typeof value.inline !== "object") parts.push("inline=" + compactText(value.inline));
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
    if (!value || typeof value !== "object" || Array.isArray(value)) return null;
    const hrefKeys = ["external_href", "external_url", "payload_href", "payload_url", "href", "url"];
    for (let i = 0; i < hrefKeys.length; i += 1) {
      if (value[hrefKeys[i]]) return { value: String(value[hrefKeys[i]]), href: true };
    }
    const refKeys = ["external_path", "payload_path", "file_path", "path", "file", "ref"];
    for (let j = 0; j < refKeys.length; j += 1) {
      if (value[refKeys[j]]) return { value: String(value[refKeys[j]]), href: false };
    }
    return null;
  }

  function callTreePayloadHtml(key, value) {
    const parsed = parseJson(value);
    const payload = parsed == null ? value : parsed;
    const external = payloadExternal(payload);
    const externalHtml = external
      ? (external.href
        ? '<a class="btn btn-sm btn-outline-secondary ms-2" href="' + escapeHtml(external.value) + '">Open external ' + escapeHtml(key) + '</a>'
        : '<span class="badge text-bg-light ms-2">external: ' + escapeHtml(external.value) + '</span>')
      : "";
    const detail = (!payloadOneLine(payload) || String(value).length > 120 || String(value).indexOf("\n") >= 0)
      ? [
        '<div class="mt-1">',
        '<button type="button" class="btn btn-sm btn-link p-0 textus-calltree-payload-toggle" data-calltree-payload-toggle aria-expanded="false">Show ' + escapeHtml(key) + '</button>',
        '</div>',
        '<pre class="bg-light border rounded p-2 mt-1 mb-1 textus-calltree-payload-body" hidden><code>' + escapeHtml(payloadPrettyText(parsed, value)) + '</code></pre>'
      ].join("")
      : "";
    return [
      '<div class="textus-calltree-payload" data-calltree-payload="' + escapeHtml(key) + '">',
      '<code>' + escapeHtml(payloadSummary(payload)) + '</code>',
      externalHtml,
      detail,
      '</div>'
    ].join("");
  }

  function callTreeAttributesHtml(attrs) {
    attrs = attrs || {};
    const attributeOrder = { component: 0, service: 1, operation: 2 };
    const entries = Object.keys(attrs)
      .filter(key => key !== "started_at_nanos" && key !== "ended_at_nanos" && key !== "calltree_kind" && key !== "display_label" && key !== "highlights" && key !== "real_io")
      .sort(function (a, b) {
        const ax = Object.prototype.hasOwnProperty.call(attributeOrder, a) ? attributeOrder[a] : 100;
        const bx = Object.prototype.hasOwnProperty.call(attributeOrder, b) ? attributeOrder[b] : 100;
        if (ax !== bx) return ax - bx;
        return a < b ? -1 : (a > b ? 1 : 0);
      })
      .map(function (key) {
        const value = redactText(String(attrs[key] == null ? "" : attrs[key]));
        const long = value.length > 120 || ["sql", "query", "request", "resolved_parameters", "response", "result"].indexOf(key) >= 0;
        const body = long
          ? (["response", "result"].indexOf(key) >= 0
            ? callTreePayloadHtml(key, value)
            : '<pre class="bg-light border rounded p-2 mb-1"><code>' + escapeHtml(value) + '</code></pre>')
          : '<code>' + escapeHtml(value) + '</code>';
        const longAttr = long && ["response", "result"].indexOf(key) < 0 ? ' data-calltree-long-attribute="true"' : "";
        return '<dt class="col-sm-3" data-calltree-attribute-key="' + escapeHtml(key) + '">' + escapeHtml(key) + '</dt>' +
          '<dd class="col-sm-9" data-calltree-attribute data-calltree-attribute-key="' + escapeHtml(key) + '"' + longAttr + '>' + body + '</dd>';
      }).join("");
    return entries ? '<dl class="row small mb-2" data-calltree-attributes>' + entries + '</dl>' : "";
  }

  function callTreeObservationsHtml(observations) {
    if (!Array.isArray(observations) || observations.length === 0) return "";
    const items = observations.map(function (observation) {
      const label = observation && (observation.label || observation.name) || "observation";
      const attrs = callTreeNodeAttributes(observation || {});
      const kind = callTreeKind(observation, attrs);
      const realIo = callTreeHasHighlight(attrs, "real_io") ? "true" : "";
      const source = attrs.source || attrs.cache_layer || attrs.datastore || "";
      return [
        '<div class="border-start border-2 ps-2 py-1 mb-1 textus-calltree-observation" data-calltree-observation data-calltree-observation-kind="' + escapeHtml(kind) + '" data-calltree-observation-real-io="' + escapeHtml(realIo) + '" data-calltree-observation-source="' + escapeHtml(source) + '">',
        '<div class="d-flex flex-wrap align-items-center gap-2">',
        '<span class="badge text-bg-secondary">observation</span>',
        '<span class="fw-semibold" data-calltree-observation-label>' + escapeHtml(callTreeDisplayLabel(observation, attrs, label)) + '</span>',
        callTreeBadges(attrs, kind),
        '</div>',
        '<div class="mt-1">' + callTreeAttributesHtml(attrs) + '</div>',
        '</div>'
      ].join("");
    }).join("");
    return [
      '<details class="mt-2" data-calltree-observations>',
      '<summary class="small text-secondary fw-semibold">Step observations (' + observations.length + ')</summary>',
      '<div class="mt-2">' + items + '</div>',
      '</details>'
    ].join("");
  }

  function callTreeValueString(value) {
    if (value == null) return "";
    if (typeof value === "object") return JSON.stringify(sanitizeValue(value), null, 2);
    return String(value);
  }

  function callTreeNodeAttributes(node) {
    if (node && node.attributes && typeof node.attributes === "object") {
      return sanitizeValue(node.attributes);
    }
    const attrs = {};
    const reserved = {
      label: true,
      name: true,
      kind: true,
      display_label: true,
      calltree_kind: true,
      flow: true,
      children: true,
      observations: true,
      attributes: true,
      enter_attributes: true,
      leave_attributes: true
    };
    Object.keys(node || {}).forEach(function (key) {
      if (!reserved[key]) attrs[key] = callTreeValueString(node[key]);
    });
    return sanitizeValue(attrs);
  }

  function callTreeChildren(node) {
    const flow = Array.isArray(node && node.flow) ? node.flow : (Array.isArray(node && node.children) ? node.children : []);
    return flow.filter(function (child) {
      return !isCallTreeObservation(child);
    });
  }

  function callTreeObservations(node) {
    const explicit = Array.isArray(node && node.observations) ? node.observations : [];
    const flow = Array.isArray(node && node.flow) ? node.flow : (Array.isArray(node && node.children) ? node.children : []);
    return explicit.concat(flow.filter(isCallTreeObservation));
  }

  function isCallTreeObservation(node) {
    const kind = String(node && node.kind || "").toLowerCase();
    return kind === "metric" || kind === "observation";
  }

  function callTreeLineHtml(line, childrenHtml) {
    const attrs = line.attrs || {};
    const kind = line.kind;
    const style = "padding-left:" + (line.depth === 0 ? 0.75 : 1.0) + "rem";
    const lane = line.depth === 0 ? "" : '<span class="textus-calltree-lane" aria-hidden="true"></span>';
    const variant = line.role === "step" ? "text-bg-secondary" : line.role === "enter" ? "text-bg-primary" : line.role === "leave" ? "text-bg-dark" : "text-bg-secondary";
    const open = line.depth <= 1 ? " open" : "";
    const realIo = callTreeHasHighlight(attrs, "real_io") ? "true" : "";
    const source = attrs.source || attrs.cache_layer || attrs.datastore || "";
    const observations = callTreeObservationsHtml(line.observations);
    const childBlock = childrenHtml
      ? '<div class="list-group mt-2 textus-calltree-children" data-calltree-children>' + childrenHtml + '</div>'
      : "";
    return [
      '<div class="list-group-item py-2 textus-calltree-row textus-calltree-row-' + escapeHtml(line.role) + '" style="' + style + '" data-calltree-node data-calltree-row data-calltree-' + escapeHtml(line.role) + '="true" data-calltree-pair="' + escapeHtml(line.pair) + '" data-calltree-depth="' + line.depth + '" data-calltree-kind="' + escapeHtml(kind) + '" data-calltree-real-io="' + escapeHtml(realIo) + '" data-calltree-source="' + escapeHtml(source) + '">',
      lane,
      '<details' + open + '>',
      '<summary class="d-flex flex-wrap align-items-center gap-2">',
      '<span class="badge ' + variant + '" data-calltree-role>' + escapeHtml(kind) + '</span>',
      '<span class="fw-semibold" data-calltree-label>' + escapeHtml(line.displayLabel) + '</span>',
      callTreeBadges(attrs, kind),
      '</summary>',
      '<div class="mt-2">' + callTreeAttributesHtml(attrs) + observations + childBlock + '</div>',
      '</details>',
      '</div>'
    ].join("");
  }

  function callTreeNodeHtml(node, depth, path, parentDisplayLabel) {
    const label = node && (node.label || node.name) || "CallTree node";
    const attrs = callTreeNodeAttributes(node);
    const children = callTreeChildren(node);
    const observations = callTreeObservations(node);
    const kind = callTreeKind(node, attrs);
    const pair = path.join("-");
    const displayLabel = callTreeDisplayLabel(node, attrs, label);
    const childHtml = children.map(function (child, index) {
      return callTreeNodeHtml(child, depth + 1, path.concat([index + 1]), displayLabel);
    }).join("");
    return callTreeLineHtml({ role: "step", label, displayLabel, kind, attrs, depth, pair, parentDisplayLabel, observations }, childHtml);
  }

  function callTreeHtml(calltree) {
    if (!Array.isArray(calltree) || calltree.length === 0) return "";
    const nodes = calltree.map(function (node, index) {
      return callTreeNodeHtml(node, 0, [index + 1]);
    });
    return [
      '<h2 class="h6 mt-3">CallTree</h2>',
      '<div class="textus-calltree-tree" data-textus-calltree>',
      '<div class="list-group border rounded bg-white textus-calltree-outline" data-calltree-node-list>',
      nodes.join(""),
      '</div>',
      '</div>'
    ].join("");
  }

  function ensurePanel() {
    if (hasServerExecutionPanel()) return null;
    let panel = document.querySelector("[data-textus-form-debug-panel]");
    if (panel) {
      closeDiagnosticsDetails(panel);
      return panel;
    }
    panel = document.createElement("section");
    panel.className = "container-fluid px-4 pb-5";
    panel.setAttribute("data-textus-form-debug-panel", "true");
    panel.innerHTML = [
      '<details class="card border-secondary-subtle bg-body-tertiary">',
      '<summary class="card-header fw-semibold">Development execution diagnostics</summary>',
      '<div class="card-body">',
      '<p class="text-secondary small">CNCF development-only diagnostics for Form API requests made by this page.</p>',
      '<div class="d-flex flex-column gap-3" data-debug-events></div>',
      '</div>',
      '</details>'
    ].join("");
    closeDiagnosticsDetails(panel);
    document.body.appendChild(panel);
    return panel;
  }

  function eventRecord(input, init, response, text) {
    const metadata = requestMetadata(input, init);
    const ok = Boolean(response.ok);
    return {
      ok,
      status: response.status || "network-error",
      operation: operationName(input),
      kind: metadata.kind,
      label: metadata.label,
      optional: metadata.optional || "false",
      url: requestUrl(input),
      timestamp: new Date().toISOString(),
      arguments: requestBody(input, init),
      body: pretty(text),
      calltree: extractCallTree(text)
    };
  }

  function networkErrorRecord(input, init, error) {
    const metadata = requestMetadata(input, init);
    const message = error && error.name === "AbortError"
      ? "Request timed out or was aborted."
      : (error && error.message ? error.message : "Network request failed.");
    return {
      ok: false,
      status: "network-error",
      operation: operationName(input),
      kind: metadata.kind,
      label: metadata.label,
      optional: metadata.optional || "false",
      url: requestUrl(input),
      timestamp: new Date().toISOString(),
      arguments: requestBody(input, init),
      body: redactText(message),
      calltree: null
    };
  }

  function storeCarryover(record) {
    try {
      const now = Date.now();
      const raw = sessionStorage.getItem(carryoverKey);
      const current = raw ? JSON.parse(raw) : [];
      const kept = Array.isArray(current)
        ? current.filter(item => item && item.storedAt && now - item.storedAt < carryoverTtlMs)
        : [];
      kept.push({ storedAt: now, record });
      sessionStorage.setItem(carryoverKey, JSON.stringify(kept.slice(-10)));
    } catch (_) {}
  }

  function takeCarryover() {
    try {
      const raw = sessionStorage.getItem(carryoverKey);
      sessionStorage.removeItem(carryoverKey);
      const now = Date.now();
      const parsed = raw ? JSON.parse(raw) : [];
      if (!Array.isArray(parsed)) return [];
      return parsed
        .filter(item => item && item.record && item.storedAt && now - item.storedAt < carryoverTtlMs)
        .map(item => item.record);
    } catch (_) {
      return [];
    }
  }

  function bindPayloadToggles(container) {
    const buttons = container.querySelectorAll("[data-calltree-payload-toggle]");
    buttons.forEach(button => {
      if (button.dataset.calltreePayloadBound === "true") return;
      button.dataset.calltreePayloadBound = "true";
      const payload = button.closest("[data-calltree-payload]");
      const body = payload ? payload.querySelector(".textus-calltree-payload-body") : null;
      if (!body) return;
      if (!body.id) body.id = "textus-calltree-payload-" + Math.random().toString(36).slice(2);
      button.setAttribute("aria-controls", body.id);
      button.addEventListener("click", () => {
        const opening = body.hidden;
        body.hidden = !opening;
        button.setAttribute("aria-expanded", opening ? "true" : "false");
        button.textContent = (opening ? "Hide " : "Show ") + button.textContent.replace(/^(Show|Hide)\s+/, "");
      });
    });
  }

  function debugRecordKey(record) {
    return [
      record.kind || "interactive",
      record.operation || "form-api",
      record.label || ""
    ].join("\u001f");
  }

  function shouldReplaceRecord(record) {
    const kind = record.kind || "interactive";
    return kind === "page-render" || kind === "background";
  }

  function replaceExistingRecord(events, key) {
    if (!key) return;
    events.querySelectorAll("[data-debug-event-key]").forEach(item => {
      if (item.getAttribute("data-debug-event-key") === key) item.remove();
    });
  }

  function renderRecord(record) {
    const panel = ensurePanel();
    if (!panel) return;
    const events = panel.querySelector("[data-debug-events]");
    const ok = Boolean(record.ok);
    const variant = ok ? "success" : "danger";
    const body = record.body || "";
    const args = JSON.stringify(record.arguments || {}, null, 2);
    const calltree = callTreeHtml(record.calltree);
    const key = debugRecordKey(record);
    if (shouldReplaceRecord(record)) replaceExistingRecord(events, key);
    const event = document.createElement("details");
    event.className = "card border-" + variant + "-subtle bg-" + variant + "-subtle";
    event.open = false;
    event.setAttribute("data-debug-event", ok ? "success" : "failure");
    event.setAttribute("data-debug-event-key", key);
    event.innerHTML = [
      '<summary class="card-header fw-semibold d-flex flex-wrap gap-2 align-items-center">',
      '<span class="badge text-bg-' + variant + '">' + (ok ? "success" : "failure") + '</span>',
      '<code>' + escapeHtml(record.operation || "form-api") + '</code>',
      '<span class="text-secondary small">' + escapeHtml(record.label || record.kind || "Form API") + '</span>',
      '</summary>',
      '<div class="card-body">',
      '<dl class="row mb-3">',
      '<dt class="col-sm-3">Operation</dt><dd class="col-sm-9"><code>' + escapeHtml(record.operation || "form-api") + '</code></dd>',
      '<dt class="col-sm-3">Request kind</dt><dd class="col-sm-9">' + escapeHtml(record.kind || "interactive") + '</dd>',
      '<dt class="col-sm-3">Request label</dt><dd class="col-sm-9">' + escapeHtml(record.label || "-") + '</dd>',
      '<dt class="col-sm-3">Optional</dt><dd class="col-sm-9">' + escapeHtml(record.optional || "false") + '</dd>',
      '<dt class="col-sm-3">HTTP status</dt><dd class="col-sm-9">' + escapeHtml(String(record.status || "network-error")) + '</dd>',
      '<dt class="col-sm-3">Timestamp</dt><dd class="col-sm-9"><code>' + escapeHtml(record.timestamp || "-") + '</code></dd>',
      '<dt class="col-sm-3">URL</dt><dd class="col-sm-9"><code>' + escapeHtml(record.url || "") + '</code></dd>',
      '</dl>',
      '<h2 class="h6">Operation arguments</h2>',
      '<pre class="bg-white border rounded p-3 small overflow-auto">' + escapeHtml(args) + '</pre>',
      '<h2 class="h6 mt-3">Result body</h2>',
      '<pre class="bg-white border rounded p-3 small overflow-auto">' + escapeHtml(body) + '</pre>',
      calltree,
      '</div>',
      '</details>'
    ].join("");
    events.appendChild(event);
    bindPayloadToggles(event);
    closeDiagnosticsDetails(panel);
    if (record.calltree && window.TextusCallTree && typeof window.TextusCallTree.enhanceAll === "function") {
      window.TextusCallTree.enhanceAll();
    }
  }

  function showResponse(input, init, response, text) {
    const record = eventRecord(input, init, response, text);
    if (shouldCarryOver(requestMetadata(input, init))) storeCarryover(record);
    renderRecord(record);
  }

  ensureDebugPanelStyle();
  closeExistingDiagnosticsPanels();
  if (!hasServerExecutionPanel()) takeCarryover().forEach(renderRecord);

  window.fetch = async function (input, init) {
    let response;
    try {
      response = await originalFetch.apply(this, arguments);
    } catch (error) {
      if (isFormApiUrl(input)) {
        const record = networkErrorRecord(input, init, error);
        if (!hasServerExecutionPanel() && shouldCarryOver(requestMetadata(input, init))) storeCarryover(record);
        if (!hasServerExecutionPanel()) renderRecord(record);
      }
      throw error;
    }
    if (isFormApiUrl(input) && shouldInspect(input, init, response)) {
      try {
        const text = await response.clone().text();
        const record = eventRecord(input, init, response, text);
        if (!hasServerExecutionPanel() && shouldCarryOver(requestMetadata(input, init))) storeCarryover(record);
        if (!hasServerExecutionPanel() && shouldRender(input, init, response)) renderRecord(record);
      } catch (_) {
        const record = eventRecord(input, init, response, "[Unable to read response body]");
        if (!hasServerExecutionPanel() && shouldCarryOver(requestMetadata(input, init))) storeCarryover(record);
        if (!hasServerExecutionPanel() && shouldRender(input, init, response)) renderRecord(record);
      }
    }
    return response;
  };
})();
