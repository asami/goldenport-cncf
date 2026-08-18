# Phase 68 Checklist - Legacy and Modern MCP Protocol Coexistence

status=planned
phase=[Phase 68 - Legacy and Modern MCP Protocol Coexistence](phase-68.md)

This checklist is the authoritative Phase 68 state ledger after Phase 68
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 67 closes.

## MCP68-01: Inventory and Target Freeze

Stage Status:
- Current status: PLANNED
- Owner: CNCF MCP server, client, HTTP, security, and downstream maintainers
- Entry rule: Phase 67 is closed.
- Completion rule: The exact official target, compatibility matrix, current
  implementation gaps, consumers, and failing-first acceptance identities are
  frozen before implementation.

- [ ] Revalidate the latest stable official MCP specification and freeze the
  exact modern revision implemented by this phase; use `2026-07-28` as the
  planning baseline rather than silently following a moving latest alias.
- [ ] Inventory the accepted legacy revisions `2025-03-26`, `2025-06-18`, and
  `2025-11-25`, including initialize, initialized notification, session,
  protocol header, JSON/SSE response, pagination, expiry, and close behavior.
- [ ] Inventory modern discovery, request metadata, HTTP method/status/header,
  result disposition, structured content/schema, cache, Origin, authorization
  context, lifecycle, and error requirements.
- [ ] Inventory CNCF server/client implementations, WebSocket compatibility,
  probes, launchers, Textus AI, Sanpomap, Bok, CBD Support, SIE, and older
  runtime consumers such as `textus-mcp-rag`.
- [ ] Separate required interoperability work from optional MCP capabilities
  and record every deferred capability with an explicit owner or non-goal.
- [ ] Freeze one legacy/modern compatibility matrix for CNCF client to server,
  external client to CNCF server, and CNCF client to external server.
- [ ] Register exact failing-first executable specifications for era selection,
  mixed evidence, lifecycle, discovery, results, cache, security, fallback,
  observability, and legacy regression.

Evidence:
- Pending.
## MCP68-02: Dual-Era Model and Selection Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF MCP protocol and compatibility maintainers
- Entry rule: MCP68-01 is DONE.
- Completion rule: One closed runtime-owned protocol model fixes era,
  revision, detection, policy, fallback, and failure semantics.

- [ ] Define closed typed `legacy` and `modern` protocol-era vocabulary and
  exact supported revision values without using unbounded version strings in
  program logic.
- [ ] Preserve `2025-11-25` as the preferred legacy/default behavior until the
  Phase 68 closure decision explicitly changes it.
- [ ] Define server-side era detection from normative request evidence and
  reject missing, mixed, ambiguous, and contradictory evidence.
- [ ] Define client-side era selection as runtime/operator policy plus admitted
  capability evidence; application requests and Components cannot select it.
- [ ] Define fallback eligibility, attempt limits, structured reasons, and the
  no-fallback-after-dispatch rule for possibly side-effecting calls.
- [ ] Define capability/result/cache/security differences as typed protocol
  policy rather than scattered date comparisons.
- [ ] Define stable structured failures and safe facets for unsupported era,
  unsupported revision, header/body mismatch, invalid lifecycle, unavailable
  capability, unsafe fallback, and incompatible result.
- [ ] Add property-based negotiation/selection totality, downgrade resistance,
  mixed-evidence rejection, and no-side-effect fallback specifications.

Evidence:
- Pending.

## MCP68-03: Dual-Era Server Boundary

Stage Status:
- Current status: PLANNED
- Owner: CNCF MCP server, HTTP, Operation projection, and security maintainers
- Entry rule: MCP68-02 is DONE.
- Completion rule: One server endpoint preserves the complete legacy contract
  and implements the admitted modern contract without a second execution path.

- [ ] Preserve legacy initialize, initialized notification, revision header,
  optional session, tools/list, tools/call, and accepted compatibility behavior.
- [ ] Implement modern discovery and request-scoped lifecycle without routing
  it through legacy initialization/session state.
- [ ] Implement modern request metadata and standard method/name/parameter
  header handling with deterministic header/body consistency validation.
- [ ] Implement modern HTTP method, status, notification, unknown-method, and
  malformed-request outcomes while keeping era-specific behavior explicit.
- [ ] Project modern tool definitions and call results, including admitted
  result disposition and structured content, from the same internal Operation
  catalog and normal authorization/UnitOfWork execution path.
- [ ] Validate Origin and authorization context before discovery or execution;
  preserve safe loopback defaults without treating them as Origin validation.
- [ ] Keep WebSocket behavior explicitly legacy/compatibility-only or retire it
  through a separately tested compatibility decision.
- [ ] Add real HTTP positive, negative, hostile-header, lifecycle, and legacy
  regression executable specifications.

Evidence:
- Pending.

## MCP68-04: Dual-Era Client Boundary

Stage Status:
- Current status: PLANNED
- Owner: CNCF MCP client, transport, Port, and runtime-registry maintainers
- Entry rule: MCP68-03 is DONE.
- Completion rule: The existing provider-neutral client Port can use admitted
  legacy and modern servers without exposing protocol wire controls.

- [ ] Preserve the complete legacy initialization, notification, session,
  expiry/reinitialize, pagination, response, and close behavior.
- [ ] Implement the modern request-scoped path without initialize,
  initialized-notification, session-map, or DELETE-close assumptions.
- [ ] Implement modern discovery, catalog construction, tool invocation, and
  result disposition behind the existing typed client service.
- [ ] Support admitted modern structured results and continuation/input-required
  outcomes without requiring a legacy content array or leaking wire JSON.
- [ ] Enforce exact server-set policy, tool allowlist, limits, credential
  resolution, authorization context, cancellation, and interruption in both eras.
- [ ] Apply compatibility fallback only before dispatch and record one bounded
  attempt history without automatic replay after uncertain delivery.
- [ ] Keep protocol era, endpoint, transport, headers, credentials, and raw
  payloads absent from Component Port and application request models.
- [ ] Add fake-exchange and real-server legacy/modern, failure, cancellation,
  fallback, and lifecycle executable specifications.

Evidence:
- Pending.

## MCP68-05: Schema, Cache, Security, and Observability

Stage Status:
- Current status: PLANNED
- Owner: CNCF MCP typed-model, cache, security, and observability maintainers
- Entry rule: MCP68-04 is DONE.
- Completion rule: Both eras have bounded typed schema/result behavior,
  authorization-correct cache semantics, and payload-safe evidence.

- [ ] Extend the typed schema/value boundary only for admitted modern schema
  features; raw JSON Schema and transport JSON remain outside Components.
- [ ] Define modern result-disposition handling, including success, error, and
  input-required/continuation semantics, with deterministic typed projection.
- [ ] Define catalog/cache hints, TTL, scope, invalidation, and authorization-
  context partitioning; legacy indefinite cache behavior must not leak into a
  modern contract.
- [ ] Prevent cache reuse across server identity, protocol era/revision,
  authorization context, tool policy, or incompatible schema identity.
- [ ] Validate Origin, standard headers, header/body consistency, credential
  redaction, endpoint policy, and downgrade resistance before provider access.
- [ ] Project bounded era, revision, lifecycle, method class, cache decision,
  result disposition, fallback, and failure evidence into CallTree,
  diagnostics, metrics, and audit.
- [ ] Prove observability/cache state retains no endpoint, credential, header,
  argument, raw result, payload, or unbounded remote error text.
- [ ] Add property, concurrency, expiry, cache-isolation, hostile-input,
  redaction, and repeated-run executable specifications.

Evidence:
- Pending.

## MCP68-06: Interoperability, Downstream Acceptance, and Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF release maintainers with Textus MCP consumer maintainers
- Entry rule: MCP68-05 is DONE.
- Completion rule: Both protocol eras, representative external peers,
  downstream consumers, documentation, validation, review, and release evidence
  agree without changing the default implicitly.

- [ ] Execute the frozen legacy/modern interoperability matrix using real HTTP
  server/client boundaries and deterministic external-peer fixtures.
- [ ] Prove the full legacy regression suite before and after modern support is
  enabled and record exact negotiated era/revision evidence.
- [ ] Update CNCF probes and shared request builders so each protocol era is
  explicit; remove duplicated raw legacy request construction where admitted.
- [ ] Validate representative Textus AI, Sanpomap, Bok, CBD Support, SIE, and
  older runtime consumers without exposing protocol selection to application code.
- [ ] Update normative server/client design and specification, Help, operator
  configuration, compatibility, security, observability, and migration guidance.
- [ ] Record a separate decision on whether and when modern becomes the default;
  Phase 68 completion alone must not change the default silently.
- [ ] Run focused MCP/HTTP/security/cache suites, affected downstream suites,
  `Test/compile`, and the complete CNCF suite through serialized SBT execution.
- [ ] Complete clean review, admitted review-fix/re-review, naming,
  executable-specification, `git diff --check`, version, artifact, and release gates.

Evidence:
- Pending.
