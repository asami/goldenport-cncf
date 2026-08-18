# Internal Operation Tool Boundary Specification

## Scope

This specification defines provider-neutral discovery and invocation of
runtime-admitted CNCF Operations as internal tools.

## Identity And Ownership Contract

An internal tool identity MUST be the exact
`component.service.operation` identity of one assembled Operation. A remote MCP
tool identity MUST remain `server/tool`. CNCF MUST NOT implicitly convert,
alias, or merge these identity domains.

### Operator-Policy Builtin Selectors

At the operator-policy input boundary only, the fixed selectors
`tool.<service>.<operation>` and `admin.<service>.<operation>` MAY be used for
framework builtins. `tool.*` maps exactly to
`org.goldenport.cncf.Tool.<service>.<operation>` and `admin.*` maps exactly to
`org.goldenport.cncf.Admin.<service>.<operation>`. The policy admission,
activated catalog, and invocation identity retain only the resulting canonical
`OperationToolIdentity` values. No arbitrary local component prefix is
accepted, and these selectors neither denote nor convert a remote MCP
`server/tool` identity.

CNCF MUST own internal Operation admission and execution. A consumer MAY
compose admitted internal definitions with separately admitted remote MCP
definitions, but that composition and any provider-function mapping MUST remain
outside CNCF.

An application caller MUST NOT select or override an Operation tool set,
Operation identity, MCP server, endpoint, transport, credential, header,
provider function, or raw protocol payload.

## Definition Contract

The provider-neutral model MUST represent tool-set identity, Operation
identity, bounded display metadata, typed input schema, structured arguments,
and structured result without retaining MCP, JSON-RPC, HTTP, or provider-native
wire objects.

Definition and schema construction MUST derive from canonical assembled
Component, Service, Operation, and Parameter definitions. Catalog ordering MUST
be lexical by complete identity. Duplicate complete identities MUST fail
structurally; CNCF MUST NOT select one entry or append an unstable suffix.

MCP JSON Schema projection MUST remain an adapter concern. `McpToolCatalog` MAY
adapt the provider-neutral definition, but MUST NOT remain the owner of the
generic internal contract.

## Admission Contract

Discovery MUST be deny-by-default. A tool MUST be discoverable and invocable
only when its assembled primary-participant Operation is admitted by the exact
runtime-owned tool-set policy.

MCP publication readiness MUST NOT by itself grant internal admission. Remote
MCP allowlists MUST NOT admit internal Operations. Admission MUST be immutable
for one invocation and MUST include positive call-count, input-byte,
result-byte, and concurrency limits.

The runtime MUST load internal tool-set admission only from the operator-owned
policy selected by `textus.operation-tools.policy`,
`textus.runtime.operation-tools.policy`, or the corresponding `cncf.*` alias.
The policy record MUST contain `toolSets`; every entry MUST contain a unique
`id` and one or more exact `operations`. Optional positive integral limits are
`maximumCalls`, `maximumInputBytes`, `maximumResultBytes`, and
`maximumConcurrency`. A missing, malformed, duplicate, stale, or unsupported
entry MUST fail runtime activation atomically. It MUST NOT leave a partially
installed consumer socket.

Unknown, stale, duplicate, unadmitted, malformed, or over-limit calls MUST fail
before business operation execution when the limit is measurable before
dispatch. Result size MUST be checked immediately after execution and before
the result crosses the tool boundary. Existing Operation timeout and
cancellation policy MUST remain authoritative; the internal source MUST NOT
create a second scheduler or detach execution from the caller transaction. A
denied call MUST NOT be retried through local MCP HTTP.

## Execution Contract

An admitted call MUST be converted to the normal CNCF operation request and
executed through `Subsystem.executeOperationResponse` using the admitted
caller's `ExecutionContext`.

The execution MUST preserve normal parameter validation, ActionCall,
UnitOfWork, authorization, transaction, Job/Event, timeout, cancellation,
resource-cleanup, CallTree, metrics, and `Consequence` / `Conclusion` behavior.
The internal source MUST NOT call `/mcp`, construct an MCP client, invoke
`McpJsonRpcAdapter`, or construct a remote `server/tool` identity.

## Failure And Diagnostic Contract

Failures MUST remain `Consequence.Failure(Conclusion)`. CNCF MUST NOT introduce
an internal-tool error envelope or classify failure by parsing display text.

Default diagnostics MAY contain bounded tool-set identity, complete internal
identity, duration, outcome, and structured conclusion classification. They
MUST NOT contain argument or result values, endpoint details, credentials,
headers, prompts, provider payloads, or raw transport bodies.
