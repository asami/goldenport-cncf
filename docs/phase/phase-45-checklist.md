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
- Current status: IN_PROGRESS
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only when MCP client observability is payload-safe and
  lifecycle cleanup cannot leak or orphan transport resources.

- [x] Record safe server-set/tool/outcome facts in CallTree and runtime metrics.
- [ ] Define lifecycle and cleanup behavior for client resources and in-flight
  calls.

## Stage MC-06 - Consumer Evidence And Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime maintainers
- Update rule: Close only after CNCF fake evidence and a Textus AI consumer
  prove the contract without requiring a live remote MCP service.

- [ ] Verify discovery, invocation, admission, limit, and diagnostic behavior
  through deterministic fake transport specifications.
- [ ] Verify existing MCP server projection behavior remains unchanged.
- [ ] Verify Textus AI receives only an admitted catalog and cannot bypass the
  client policy through provider-native remote MCP configuration.
- [ ] Record optional live remote MCP evidence as a heavy test.
