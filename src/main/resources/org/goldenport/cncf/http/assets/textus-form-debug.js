(function () {
  if (window.__textusFormDebugInstalled) return;
  window.__textusFormDebugInstalled = true;

  const originalFetch = window.fetch;
  if (!originalFetch) return;
  const carryoverKey = "textus.form.debug.carryover.v1";
  const carryoverTtlMs = 2 * 60 * 1000;
  const redactedValue = "[redacted]";

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

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function pretty(text) {
    if (!text) return "";
    try {
      return JSON.stringify(sanitizeValue(JSON.parse(text)), null, 2);
    } catch (_) {
      return redactText(text);
    }
  }

  function ensurePanel() {
    let panel = document.querySelector("[data-textus-form-debug-panel]");
    if (panel) return panel;
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
      body: pretty(text)
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

  function renderRecord(record) {
    const panel = ensurePanel();
    const events = panel.querySelector("[data-debug-events]");
    const ok = Boolean(record.ok);
    const variant = ok ? "success" : "danger";
    const body = record.body || "";
    const args = JSON.stringify(record.arguments || {}, null, 2);
    const event = document.createElement("details");
    event.className = "card border-" + variant + "-subtle bg-" + variant + "-subtle";
    event.open = true;
    event.setAttribute("data-debug-event", ok ? "success" : "failure");
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
      '</div>',
      '</details>'
    ].join("");
    events.appendChild(event);
  }

  function showResponse(input, init, response, text) {
    const record = eventRecord(input, init, response, text);
    if (shouldCarryOver(requestMetadata(input, init))) storeCarryover(record);
    renderRecord(record);
  }

  takeCarryover().forEach(renderRecord);

  window.fetch = async function (input, init) {
    const response = await originalFetch.apply(this, arguments);
    if (isFormApiUrl(input) && shouldInspect(input, init, response)) {
      try {
        const text = await response.clone().text();
        const record = eventRecord(input, init, response, text);
        if (shouldCarryOver(requestMetadata(input, init))) storeCarryover(record);
        if (shouldRender(input, init, response)) renderRecord(record);
      } catch (_) {
        const record = eventRecord(input, init, response, "[Unable to read response body]");
        if (shouldCarryOver(requestMetadata(input, init))) storeCarryover(record);
        if (shouldRender(input, init, response)) renderRecord(record);
      }
    }
    return response;
  };
})();
