# Operational Observation Read Model

Spec ID: `operational-observation-read-model`.
Status: static producer/consumer contract; implementation and installed availability are separate requirements.
@since Oct. 24, 2026. @version Oct. 24, 2026.

Architectural context: [observation ownership](../design/operational-observation-read-model.md).
The existing [managed-service contract](managed-service-container-runtime.md)
continues to own service lifecycle and ownership. The existing
[HTTP execution contract](http-execution-runtime.md) owns outbound admission.
This document defines read behavior; it grants no infrastructure authority.

## R1. Operation and admission

The producer is the normal CNCF Component QUERY `system.operationalObservations`.
Normal Help, Operation resolution, ActionCall, UnitOfWork and automatic HTTP
projection own its entry points. No separate controller or Dashboard scraping
path supplies this contract. A consumer must verify that its selected installed
producer exposes the operation before using it; this specification alone is not
an availability claim.

The request has only optional integral `limit`, default 50, range 1..100.
Invalid input is a structured `operationInvalid` failure. Requests cannot supply
profiles, destinations, selectors, executable names, credentials or grants.
Before source access, the producer requires an authenticated subject with an
existing ApplicationContentManager, Operator, System or Internal privilege,
capability or role, following normal security selection. Launcher registration
credentials alone do not authorize this read. Operation admission is distinct
from the runtime's admission of each source read.

## R2. Configured scope

Runtime-selected configuration supplies one explicit profile and a bounded
inventory of at most 100 targets. A target contains its stable logical
`targetId`, `service`, named `instance`, `kind` (`runtime` or `infrastructure`),
enabled flag and separately selected source bindings for each observation axis.
The inventory has unique target identities; `(service, instance)` is unique
within that profile. Provider bindings are resolved before execution through
the runtime configuration owner and are not request-overridable. Invalid,
duplicate or oversized declarations fail with structured configuration errors.

The server profile declares PostgreSQL `ops` and `dev` as independent targets
and explicitly enabled observability targets. The client profile may declare
those targets disabled. Profile labels do not establish facts about a machine;
hostnames, process discovery and an absent configuration cannot select a
profile. Ordinary infrastructure requires no CAR identity or Launcher entry.
An optional runtime association must name an actual configured runtime identity;
it must not be inferred from a service name, container name or image.

No observation configuration returns `configuration.state=not-configured`, no
profile and an empty inventory. It does not mean client profile, disabled
infrastructure, healthy infrastructure or an empty live Docker installation.

## R3. Independent observations

Every enabled target has four independently attributable axes:

| Axis | Authority | Positive observations establish |
| --- | --- | --- |
| `deployment` | Admitted live deployment inspection | The selected container/deployment state only |
| `health` | Selected Component's normal `system.health`, or an explicitly declared service health source | That source's health result and scope only |
| `readiness` | Selected service's declared readiness source | Readiness for that exact service/probe at its observation time |
| `reachability` | Admitted bounded access to the selected endpoint | Reachability from the producer's network location |

Control Center joins a fifth, separate `launcherLease` observation from its
existing registration/heartbeat authority using the explicit runtime association.
The CNCF producer does not copy that registry or invent lease facts. An absent
association means that the lease axis is not applicable. A configured association
whose report is absent or expired remains unknown/stale under the registry's
own contract; it is not an authoritative stopped deployment.

Running Docker, a current Launcher lease, a successful HTTP exchange and a
successful historical readiness check do not imply current application health
or readiness. Compose configuration establishes desired topology only.
The producer does not calculate a combined healthy/running value across axes.
Control Center's existing overall health remains scoped to Control Center.

## R4. Observation value and freshness

An axis is exactly one of:

- `availability=observed`, with `value`, logical `sourceId`, `observedAt` and
  `expiresAt`;
- `availability=unavailable`, with a bounded `reasonCode`, and `sourceId` when
  a source was selected; no successful value or observation timestamp;
- `availability=not-applicable`, with no successful value or source access.

`deployment.value` preserves the declared deployment provider's finite state
vocabulary. The Docker provider uses `created`, `running`, `restarting`, `paused`,
`exited`, `dead`, `removing` or `absent`. An unknown provider state is unavailable,
not unhealthy. Health retains the declared source's health vocabulary and
scope. Readiness uses `ready`/`not-ready`, and reachability uses
`reachable`/`unreachable`; negative observations require an actual completed
source result. A transport/admission failure is unavailable, not a negative
service observation.

All instants use UTC ISO-8601. The request clock comes from ExecutionContext.
An observation timestamp describes the actual observation, not registry
construction, query arrival or response receipt substituted for an old sample.
`expiresAt` comes from the source's configured finite freshness bound. Missing,
expired, malformed or implausibly future source timestamps cannot establish a
current observation. No history cache or background polling store is introduced.
An adapter that samples current state directly records its actual sampling
instant; a historical metric must retain its original sample instant.

Disabled targets return all four axes not-applicable and invoke no provider.
Enabled targets with missing sources return unavailable. Finite reason codes
are `source-not-configured`, `source-unavailable`, `permission-denied`, `timeout`,
`invalid-source`, `stale`, `ambiguous-target`, `unsupported` and
`budget-exhausted`. Raw exceptions and provider output are not public diagnostics.

## R5. Deployment source ownership

A runtime-owned service can be matched only by its admitted service owner and
logical service identity, using existing ownership/compatibility rules. The
producer needs a fresh, separately admitted read-only inspection for deployment
state. A `ServiceContainerRegistryEntry` revision/status or a lifecycle metric is
not a live inspection. Calling `resolveC`, starting a service or waiting for its
lifecycle to make it observable is forbidden on this read path.

Externally managed Docker/Compose services use a separate, explicitly configured
observation target and a runtime-owned read-only inspection capability. Their
observation does not adopt them into the managed lifecycle registry, assign CNCF
ownership labels, register cleanup or grant lifecycle access. In particular,
`ServiceContainerDefinition.External` remains an endpoint-only definition under
the managed-service contract and is not extended to inspect Docker.

The runtime binds finite exact selectors and grants before use. Neither a public
request nor a consumer supplies arbitrary Docker arguments, sockets, commands or
environment. Zero matches after a successful authorized exact lookup can yield
`absent`; ambiguous matches, denied access, a missing daemon or a failed lookup
are unavailable. The read capability must be separable from mutation capability.

## R6. Wire projection and confidentiality

Business JSON uses the following fixed camelCase structure. Optional associations
are omitted when absent; unavailable axis fields follow R4. Normal CNCF output
envelopes retain their standard semantics.

```json
{
  "protocolVersion": 1,
  "observedAt": "2026-10-24T00:00:00Z",
  "producer": {"componentId": "<actual Component ID>", "instanceId": "<actual instance ID>"},
  "configuration": {"state": "configured", "profile": "server", "healthContracts": []},
  "total": 1, "returned": 1, "truncated": false,
  "data": [{
    "targetId": "postgresql-ops", "service": "postgresql", "instance": "ops",
    "kind": "infrastructure", "enabled": true,
    "observations": {
      "deployment": {"availability": "unavailable", "reasonCode": "source-not-configured"},
      "health": {"availability": "unavailable", "reasonCode": "source-not-configured"},
      "readiness": {"availability": "unavailable", "reasonCode": "source-not-configured"},
      "reachability": {"availability": "unavailable", "reasonCode": "source-not-configured"}
    }
  }]
}
```

Producer identities come from the actual selected Component context. Missing
identity is a structured failure, never a synthesized identity. Optional
`runtimeInstanceId` is an opaque explicit association, not proof of registration.
Canonical identities are preserved without parsing their printed representation.
Labels are safe display data and cannot confer source or ownership authority.

Configured results include `configuration.healthContracts`, an array of
`{sourceId, scope, values:[...]}` declarations from the actual selected host Plan.
Include exactly the HTTP health sources referenced by returned enabled targets,
even when the current acquisition is unavailable. Sort declarations by sourceId
and each values array lexicographically, without duplicates. There are at most
eight declarations and sixteen values per declaration. No health references means
an empty array; `not-configured` retains only its state and omits this array.
These are safe source declarations, not observations or grants. They never
include URLs, endpoints, credentials or selectors. Observed health scope and value
must match the selected declaration. Consumers obtain both profile and these
declarations from this public result rather than copying private host Plans.

Sort configured targets by `targetId` before applying `limit`; `total` counts
all configured targets, `returned=data.size`, and `truncated=(returned<total)`.
Only returned targets may be observed. Data omits endpoints, host paths, sockets,
container IDs, labels, mounts, environment, credentials, secrets, full provider
responses and command lines. `sourceId` is a safe logical binding identity,
never a URL. A successful query reports a read result, not all-target health.

## R7. Execution and bounds

Source reads use CNCF ActionCall/UnitOfWork and runtime-owned typed capabilities.
Outbound HTTP uses the ordinary internal DSL, exact host-selected grants and
the current HTTP admission rules. It must not use StartupImportReader, a direct
HTTP driver, process-global context, shell commands or an application HTTP client.
Credentials remain at the existing authorized runtime provider boundary.

Before a source read, the runtime selects finite operation time, per-source time
and byte bounds compatible with its consumer's deadline. HTTP redirects are
disabled and caller controls can only tighten the host bounds. Source binding
cardinality and aggregate work are bounded in configuration; at most eight
distinct provider acquisitions may serve one response. Configurations exceeding
that bound fail before acquisitions. Shared batch results may cover multiple
explicit targets without discovering new targets. A source may not acquire
unreturned targets merely to populate counts.

The default observation operation budget is four seconds, each source exchange
is capped at one second and 64 KiB, and business JSON is capped at 256 KiB.
These are ceilings; narrower native/runtime limits apply. Runtime-owned deadline
and cleanup support is a producer delivery requirement, not permission to add
caller timers, detached work, retries or a new scheduler. No launch occurs after
the remaining budget is exhausted. Unread axes become unavailable with
`budget-exhausted`; completed independent observations remain attributable.
Authorization/configuration/input failure rejects the operation; an admitted
source's failure is isolated to that source's axes.

## R8. Consumer and profile evidence

Control Center consumes the selected producer's normal authenticated Operation/
HTTP result, validates version, producer identity, profile, target association,
counts, timestamps and finite axis vocabularies, then projects it through the
shared Operational Summary API. Web and native clients display that projection;
they perform no source discovery or infrastructure probes. A disconnection is
distinct from the last successful observation and its expiry.

The server profile must demonstrate independent PostgreSQL ops/dev and enabled
observability targets, including Docker deployment inspection where configured.
The client profile must demonstrate disabled targets without source effects or
failure status. Record the actual host/profile and supported providers. Fixtures
do not demonstrate live Docker state or a platform matrix. Missing required
producers or host evidence remain explicit acceptance gaps, not checked items.

## R9. Executable specification and producer delivery requirements

Behavioral coverage must use Given/When/Then and property-based cases for:

| Rules | Required behavior |
| --- | --- |
| R1/R2 | Administrative admission before reads; generated normal operation routing; strict bounded configuration; arbitrary request selectors refused |
| R2/R3/R4 | Distinct ops/dev identities; disabled profiles perform zero source effects; permutations and one-axis changes cannot alter other axes |
| R3/R4/R5 | Running container plus unhealthy Component; fresh lease plus missing deployment; stale metrics; ambiguous/absent/denied lookup distinctions |
| R4/R6 | Timestamp skew/expiry, bounded safe projection and exact camelCase/envelope output; no private source values |
| R5/R7 | Exact read admission; no create/start/resolve/stop/remove/cleanup registration, including externally managed services and failures |
| R6/R7 | Deterministic limits, batch scoping, real deadline/byte-limit behavior, isolated source failure and settled resources |
| R8 | Same server projection for Web/native, actual server/client profiles and explicitly admitted live Docker read-only evidence |

Existing [service-container runtime specifications](../../src/test/scala/org/goldenport/cncf/servicecontainer/ServiceContainerRuntimeSpec.scala)
and [HTTP admission specifications](../../src/test/scala/org/goldenport/cncf/http/HttpExecutionAdmissionSpec.scala)
cover their existing boundaries only. They do not supply this operation or its
read-only provider. Delivery requires the typed producer model/configuration,
normal QUERY, admitted provider read, executable specifications, installed entry
verification and actual scoped host results. Implementation and validation scope
must be admitted separately before those changes or effects are performed.

## Host declaration v1

The confidential, host-file-only parameter
`textus.subsystem.operational-observation.plan` contains a JSON string with
exact fields `schema`, `profile`, `owners`, `limits`, `sources`, `targets`.
The schema is `cncf.operational-observation.v1`. `profile` is an explicit safe
logical label; `owners` is a nonempty unique array of actual qualified Component
IDs allowed to invoke the capability. This declaration does not replace their
ordinary HTTP GET grants. Only the selected Subsystem's Home/Project/Cwd file
bindings are accepted; request/environment bindings cannot install it.

`limits` has integral positive `operationMillis` (at most 4000), `sourceMillis`
(at most 1000), `sourceBytes` (at most 65536), `responseBytes` (at most 262144).
Source time cannot exceed operation time. There are at most eight unique sources,
including unused declarations; every referenced source must exist. Execution
samples only sources referenced by enabled returned targets and acquires each
selected source once, with the remaining operation budget tightening its bounds.

Every target has exact fields `targetId`, `service`, `instance`, `kind`, `enabled`,
`sources`, and optional `runtimeAssociation`. `sources` is an object whose
optional keys are `deployment`, `health`, `readiness`, `reachability`, and whose
values are logical source IDs. `runtimeAssociation` has exactly `componentId`
and `instance`, decoded into the existing ComponentInstanceId type. It must
match an actual assembled Component instance at invocation. The wire uses that
identity's canonicalKey, without parsing its printed representation.

Sources have common `sourceId`, `type`, `freshnessMillis` (1..300000).
A `docker` source adds `baseUrl` (an HTTP(S) origin without credentials, query or
fragment) and one exact `selector`: `{type:"name", name:"postgres-ops"}`,
`{type:"compose", project:"ops", service:"postgresql"}`, or
`{type:"runtime-owned", ownerKind:"component-runtime", ownerId:"...", serviceId:"..."}`.
The runtime performs only Docker Engine's GET `/containers/json?all=1&filters=...`
against that explicitly granted origin, then validates returned names/labels
again. Runtime-owned results additionally pass the existing registry definition
and ownership/image/port compatibility contract. An existing Docker read proxy
or separately admitted Engine HTTP endpoint is required. This feature does not
enable a Docker TCP listener, mount a socket or reuse the lifecycle gateway.

An `http` source adds `url`, `axis` (`health`, `readiness`, `reachability`),
`scope`, `vocabulary`, and `sampleMode` (`current` or `reported`). The vocabulary
contains 1..16 unique safe logical values; readiness/reachability must use exactly
their R4 vocabularies. A source produces only its declared axis. Completed HTTP
exchanges establish reachability independently of status; non-2xx health/readiness
results are unavailable rather than fabricated negative service facts. Health
and readiness use a bounded JSON object with `value` (or normal system.health
`status`), optionally beneath normal envelope `data`; an explicitly declared
`reported` source must also supply UTC `observedAt` and matching `scope`.
`current` means the endpoint itself samples on this exchange, and uses the
ExecutionContext instant immediately before that exchange only when the source
does not return `observedAt`. An explicit timestamp is always parsed, retained
and checked, including for current sources. An explicit scope must always match;
a current source without scope uses the host-declared probe scope. A reported
source must return its scope. Null or wrongly typed metadata is invalid, never
treated as absent. Current mode must not be used for historical metric endpoints.
Freshness is checked again after all reads.

An illustrative server declaration (to be encoded as this parameter's JSON
string) keeps ops/dev separate and enables observability explicitly:

```json
{
  "schema":"cncf.operational-observation.v1",
  "profile":"server",
  "owners":["org.example.ObservationOwner"],
  "limits":{"operationMillis":4000,"sourceMillis":1000,"sourceBytes":65536,"responseBytes":262144},
  "sources":[
    {"sourceId":"ops-docker","type":"docker","freshnessMillis":30000,"baseUrl":"https://engine.example.test","selector":{"type":"compose","project":"ops","service":"postgresql"}},
    {"sourceId":"dev-docker","type":"docker","freshnessMillis":30000,"baseUrl":"https://engine.example.test","selector":{"type":"compose","project":"dev","service":"postgresql"}},
    {"sourceId":"observability-health","type":"http","freshnessMillis":30000,"url":"https://observability.example.test/system/health","axis":"health","scope":"component","vocabulary":["ok","error"],"sampleMode":"current"}
  ],
  "targets":[
    {"targetId":"postgresql-ops","service":"postgresql","instance":"ops","kind":"infrastructure","enabled":true,"sources":{"deployment":"ops-docker"}},
    {"targetId":"postgresql-dev","service":"postgresql","instance":"dev","kind":"infrastructure","enabled":true,"sources":{"deployment":"dev-docker"}},
    {"targetId":"observability-main","service":"observability","instance":"main","kind":"runtime","enabled":true,"sources":{"health":"observability-health"}}
  ]
}
```

These reserved example hosts do not install connections, establish actual host
results or select the real system.health vocabulary. The host owner substitutes
its actual installed producer's declared status vocabulary and admits the
separate HTTP origin/GET grants. No runtime association is inferred from these
labels. Unbound axes remain unavailable rather than being inferred from Docker.

Example independent disabled client inventory (no source effects):

```json
{"schema":"cncf.operational-observation.v1","profile":"client",
 "owners":["org.simplemodeling.textus.ControlCenter"],
 "limits":{"operationMillis":4000,"sourceMillis":1000,"sourceBytes":65536,"responseBytes":262144},
 "sources":[],"targets":[
  {"targetId":"postgresql-ops","service":"postgresql","instance":"ops","kind":"infrastructure","enabled":false,"sources":{}},
  {"targetId":"postgresql-dev","service":"postgresql","instance":"dev","kind":"infrastructure","enabled":false,"sources":{}}]}
```

The server uses the same two distinct target identities with `enabled=true`
and explicit per-axis source references, plus explicitly enabled observability
targets. Installing that host-specific declaration and demonstrating live reads
remain separately admitted host work. Neither this example nor fixtures assert
the availability of an actual server profile.
