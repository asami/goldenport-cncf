# Phase 45 Checklist - MCP Client and AI Tool Boundary

status=active
phase=[Phase 45 - MCP Client and AI Tool Boundary](phase-45.md)

## Stage MC-01 - Client Boundary Specification

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only when the client contract and its ownership,
  transport, and server-publication boundaries are recorded in a normative
  design/specification.

- [x] Promote the MCP client direction to normative design/specification.
- [x] Define the separation between remote MCP client consumption and the
  existing CNCF MCP server projection.
- [x] Specify the public consumer contract and prohibit caller-controlled
  endpoint, credential, transport, and tool selection.

## Stage MC-02 - Typed Protocol Model

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only when all client data crosses the Port as typed
  values and no provider wire format is exposed.

- [x] Define typed server-set, server, tool, schema, call, result, limit, and
  redacted diagnostic values.
- [x] Define structured outcome kinds for admission, transport, protocol, and
  remote tool failures.
- [x] Define bounded identity, argument, result, and diagnostic summary rules.

## Stage MC-03 - Port And Transport ExtensionPoint

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only when a consumer uses a runtime-owned Port with
  Streamable HTTP and deterministic fake implementations.

- [x] Define the MCP client Port and runtime registry/binding contract.
- [x] Implement the Streamable HTTP transport ExtensionPoint for initialize,
  `tools/list`, and `tools/call`.
- [x] Implement deterministic fake transport evidence without a remote service.

## Stage MC-04 - Admission And Safety

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only when policy rejects unsafe inputs before a
  transport call and preserves structured outcomes.

Verified progress:
- Exact per-server tool allowlists filter unlisted wire entries before metadata
  decoding and before invocation.
- Recursive required/type/additional-field input admission runs before
  `callTool`, with deterministic JSON Pointer diagnostics.
- Invocation-scoped call-count/concurrency limits reset between consumer
  invocations and reject exhaustion before `callTool`.
- Streamable HTTP applies configured timeout and bounded UTF-8 input/output
  policy before exchange or protocol decoding.
- Runtime-owned opaque Bearer credential references resolve only at the HTTP
  request boundary; resolver absence and invalid material fail before exchange.

- [x] Enforce named server-set, endpoint, credential-reference, and tool
  allowlist admission.
- [x] Enforce timeout, call-count, input-byte, output-byte, and concurrency
  limits.
- [x] Prove denied and exhausted paths do not invoke the transport.

## Stage MC-05 - Observability And Lifecycle

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only when MCP client observability is payload-safe and
  lifecycle cleanup cannot leak or orphan transport resources.

- [x] Record safe server-set/tool/outcome facts in CallTree and runtime metrics.
- [x] Define lifecycle and cleanup behavior for client resources and in-flight
  calls.

## Stage MC-06 - Codex MCP Definition Import

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after Codex MCP definitions are treated solely as
  an import source, normalized into the CNCF client model, and subjected to the
  same CNCF-owned admission policy as native definitions.

Verified progress:
- A package-restricted runtime assembly now composes successful imports with
  Streamable HTTP transport binding and the client registry, installs only
  normalized services into consumer sockets, and owns deterministic registry
  closure. Unsafe selected definitions fail before transport allocation.
- An operator-owned policy descriptor now resolves a relative Codex definition
  source, exact selected server/tool admission, limits, and opaque credential
  references. Generic core Record decoding handles TOML and recursively
  normalizes YAML mappings; malformed present limits fail instead of silently
  reverting to defaults.
- `textus.mcp.client.policy` and its runtime/CNCF aliases now activate the
  imported runtime as a Subsystem-owned resource. Existing and later consumer
  sockets receive only admitted services, and Subsystem shutdown closes the
  installed service and transport lifecycle.
- Textus AI runtime execution classes now select only a logical server-set
  identity and publish the resulting `McpClientSocket` as an input Port.
  Application-purpose configuration cannot select MCP connectivity, while
  endpoint, transport, credential, and Codex source configuration remain
  absent from the consumer contract.

- [x] Define a Codex configuration import adapter that is isolated from the
  canonical MCP client model and resilient to external schema evolution.
- [x] Import only the CNCF-supported Streamable HTTP subset and normalize
  logical server identity and endpoint configuration deterministically.
- [x] Require CNCF-owned server-set, tool allowlist, limit, and credential
  reference policy overlays before registry activation.
- [x] Reject stdio command/argument/environment definitions, raw headers,
  embedded credentials, unsupported transports, and unrestricted tool
  publication.
- [x] Prove that Textus AI receives only the normalized admitted Port service
  and never reads or forwards Codex MCP configuration directly.

## Stage MC-07 - Builtin Tool Baseline

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after the common builtin tools execute as normal
  CNCF Operations through ActionCall/UoW and are projected through MCP without
  a local MCP loopback or a parallel authorization path.

Verified progress:
- `tool.resource.read` parses a logical reference, delegates only to the
  execution-context `ResourceAccess`, and returns a bounded text projection
  without provider or physical-storage details.
- `tool.time.now` reads one injected execution-clock instant and returns a
  consistent projection in an optional bounded IANA region timezone.
- `tool.decimal.calculate` accepts bounded plain-decimal strings and the closed
  `add` / `subtract` / `multiply` operator set; property-based evidence proves
  exact results without binary floating-point conversion.
- All three services are MCP-ready normal Operations implemented with
  `FunctionalActionCall` and the existing UoW interpreter. The existing MCP
  catalog publishes `tool.resource.read`, `tool.time.now`, and
  `tool.decimal.calculate` without local loopback.

- [x] Implement `resource.read` using the canonical `ResourceAccess` boundary.
- [ ] Implement static `web.fetch` and `web.head` with scheme, host,
  private-network, redirect, content-type, byte-size, and timeout policy.
- [x] Implement deterministic `time.now` using the runtime clock and a bounded
  timezone contract.
- [x] Implement deterministic decimal calculation without arbitrary code or
  script evaluation.
- [ ] Define a provider-neutral `web.search` Operation contract while keeping
  provider credentials and selection under runtime ownership.
- [ ] Verify builtin tools use normal authorization, Consequence/Conclusion,
  CallTree, metrics, and MCP Operation projection semantics.
- [ ] Keep dynamic browser automation, arbitrary filesystem/process access,
  unrestricted headers, JavaScript evaluation, and external mutation tools in
  optional Components rather than the builtin baseline.

## Stage MC-08 - Consumer Evidence And Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime maintainers
- Update rule: Close only after CNCF fake evidence and a Textus AI consumer
  prove the contract without requiring a live remote MCP service.

Verified progress:
- A consumer can declare only logical server-set requirements through an
  `McpClientSocket` input Port. Runtime registry installation resolves the
  complete set atomically, so unavailable policy cannot expose a partial MCP
  service surface.

- [ ] Verify discovery, invocation, admission, limit, and diagnostic behavior
  through deterministic fake transport specifications.
- [ ] Verify existing MCP server projection behavior remains unchanged.
- [ ] Verify Textus AI receives only an admitted catalog and cannot bypass the
  client policy through provider-native remote MCP configuration.
- [ ] Verify Textus AI distinguishes internal Operation tool identities from
  remote MCP tool identities while presenting one provider-neutral catalog.
- [ ] Record optional live remote MCP evidence as a heavy test.
