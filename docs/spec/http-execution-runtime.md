# HTTP Execution Runtime

Status: normative static contract

## Ordinary outbound HTTP

The normal Behavior HTTP DSL and `UnitOfWorkOp.HttpGet`, `HttpPost`,
`HttpPostBag` and `HttpPut` require explicit runtime host authority. Constructing
a program is passive. At interpretation, each operation checks the current
UnitOfWork's `ExecutionContext` before acquiring its HTTP driver or opening a
POST input bag. Direct interpretation and Free/ConsequenceT execution use this
same boundary. `HttpPostBag` requires the POST grant.

Authority is selected from the nearest actual `Component.Context` in the
current scope ancestry, using its exact `ComponentId`. Scope names, display
labels, operation metadata, request properties and component configuration
cannot supply a grant. The runtime admission is inherited through ordinary
child scopes; it is injected before Component assembly.

## Host configuration

`textus.subsystem.http-execution.plan` is a confidential, typed configuration
parameter with no aliases. Only a Home, Project or Cwd file binding for exactly
the selected Subsystem instance can install it. CLI, environment, Component and
foreign Subsystem bindings are refused. Missing configuration leaves HTTP
default-denied; malformed configuration fails startup before Component
assembly rather than silently selecting that default.

The parameter value is a JSON **string**. For example, the selected
`.textus/config.json` can contain:

```json
{
  "textus.subsystem.http-execution.plan": "{\"schema\":\"cncf.runtime-http-execution.v1\",\"baseUrl\":\"http://127.0.0.1:8080/\",\"limits\":{\"connectTimeoutMillis\":10000,\"readTimeoutMillis\":10000,\"responseBytes\":1048576},\"grants\":[{\"componentId\":\"org.example.SelectedComponent\",\"origins\":[\"http://127.0.0.1:8080\"],\"methods\":[\"GET\",\"POST\",\"PUT\"]}]}"
}
```

The inner object requires exactly `schema`, `baseUrl`, `limits` and `grants`.
Each grant requires exactly `componentId`, `origins` and `methods`. Only
uppercase GET, POST and PUT are supported. Duplicate object keys, duplicate
Component owners, equivalent duplicate origins, duplicate methods, unknown
fields/schemas, invalid Component IDs and invalid bounds fail configuration.
Each grant has nonempty origins and methods. An empty `grants` array is valid
and grants nothing. Encoding is deterministic by Component, origin and method.

Client deployments must grant the actual Client Component ID and destination
origin for the methods they use. A client base URL or a configured fake driver
alone does not authorize HTTP. Existing body, header, query and response
behavior continues through the same admitted route.

## Destinations

The host base URL is an absolute hierarchical HTTP or HTTPS URI with a nonempty
ASCII host, an absent port or port 1–65535, and no user information, query,
fragment, backslash or control character. A base path is permitted.

Request destinations may be absolute HTTP/HTTPS URIs or relative path/query
references. They use ordinary `java.net.URI.resolve` once against the host
base. With base `https://example.test/api/`, `item?q=%2B` resolves to
`https://example.test/api/item?q=%2B`, while `/item` resolves to
`https://example.test/item`. A base without a trailing slash follows the normal
URI file-path resolution rules. Raw encoded paths and queries are preserved;
the admitted absolute ASCII URI has a lowercase scheme. The driver's own base
URL cannot change that absolute authorized destination.

Network-path references (`//authority`), opaque/unsupported schemes,
userinfo, fragments, malformed ports, backslashes and control characters are
refused. The resolved destination is validated again before comparison.
Origins contain only `http(s)://host[:port]` and an empty or `/` root path.
Comparison folds scheme and host with `Locale.ROOT` and uses effective default
ports 80/443. Other ports remain distinct. There are no wildcard, substring,
subdomain or implicit localhost grants.

## Finite controls and redirects

Host connect/read timeouts are positive finite Int milliseconds; the response
byte limit is a positive finite Long. Caller controls can only tighten them by
minimum. Controlled property names are case-insensitive:

| Caller control | Effective behavior |
| --- | --- |
| `http.connect-timeout-ms`, `http.read-timeout-ms` | Tighten the corresponding timeout |
| `http.connect-timeout-seconds`, `http.read-timeout-seconds` | Convert bounded positive seconds to milliseconds and tighten |
| `http.timeout-seconds` | Tighten both timeouts |
| `http.max-response-bytes` | Tighten response bytes; zero is valid |
| `http.follow-redirects` | Only false is accepted |

Duplicate case-folded controlled keys, malformed values, nonpositive timeouts,
negative byte limits and seconds above `Int.MaxValue / 1000` are refused.
Distinct timeout aliases combine by minimum. The operation removes recognized
controls and emits exactly one connect-ms, read-ms, max-response-bytes and
`http.follow-redirects=false`. Unrelated properties retain their order.
`http.public-network-only` retains its optional additional driver check and
cannot widen the exact origin grant.

Redirect following is unavailable in this initial contract. The real URL
driver returns the original 3xx response and Location to the same program;
it issues no second request even when that origin also has a grant. Original
request method, headers and body are retained. The real successful response
returns to the calling program; response materialization fails when its byte
ceiling is exceeded.

## Failures and trust boundary

Admission failure order is unconfigured runtime, missing actual Component,
missing exact grant, method, destination, redirect opt-in, then invalid limits.
The structured policies are respectively `http.execution.unconfigured`,
`.component`, `.grant`, `.method`, `.destination`, `.redirect` and `.limits`.
Messages and policy facts are fixed; diagnostics do not contain URLs, paths,
queries, credentials, grant contents, headers or bodies. Host decoding returns
safe configuration failures. Actual I/O failures remain failures.

This route reuses `ExternalEffectClassification(Set(ExternalEffectKind.Http),
ExternalEffectRoute.Managed)`. Classification describes the route and never
creates authority. The configured HttpDriver is trusted framework
infrastructure, and the existing URL driver honors these canonical controls.
This contract does not isolate malicious drivers or host JVM code, audit every
direct framework HttpDriver use, enforce DNS/IP containment or DNS rebinding
protection, or impose streaming upload quotas.

## Executable specifications

- [Admission, URI and finite control semantics](../../src/test/scala/org/goldenport/cncf/http/HttpExecutionAdmissionSpec.scala)
- [Strict host wire codec](../../src/test/scala/org/goldenport/cncf/http/RuntimeHttpExecutionPlanCodecSpec.scala)
- [Public bootstrap and provenance](../../src/test/scala/org/goldenport/cncf/cli/CncfRuntimeHttpExecutionInitializationSpec.scala)
- [Actual UoW denial and real bounded transport](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkHttpAdmissionSpec.scala)
- [Existing HTTP wiring](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkHttpSpec.scala)
- [Ordinary Client behavior](../../src/test/scala/org/goldenport/cncf/component/builtin/client/ClientAdminSystemPingSpec.scala)
- [Inline action/UoW calltree behavior](../../src/test/scala/org/goldenport/cncf/component/ComponentLogicOperationDefinitionSemanticsSpec.scala)
