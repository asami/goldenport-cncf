# Operational Observation Read Model

Role: producer/consumer architecture and responsibility boundaries.
@since Oct. 24, 2026. @version Oct. 24, 2026.

The authoritative behavioral contract is
[Operational Observation Read Model](../spec/operational-observation-read-model.md).
Rule identifiers below refer to that specification and do not redefine them.
This architecture extends the operational observation boundary of
[managed service containers](managed-service-container-runtime.md) and the
[Component runtime capabilities](component-runtime-boundary-capabilities.md).
It does not change their lifecycle or authority contracts.

## Operation and execution ownership

The Component's normal system QUERY is the producer facade. Ordinary CNCF
resolution, authentication, ActionCall/UnitOfWork execution and projections own
that facade. A runtime-bound observation capability supplies immutable typed
observations; the producer projects a safe read model under R1/R6. The facade
does not expose a gateway, driver, mutable registry or physical resource handle.

The runtime configuration owner resolves profile, inventory, exact source
bindings, capabilities, freshness and finite execution bounds. It binds these
values explicitly before source execution. Request input selects only the
bounded display limit. Component code consumes configured capabilities through
ExecutionContext, without ambient discovery or interpretation of machine names.
R2/R7 define the observable behavior of those boundaries.

## Source responsibility

| Owner | Responsibility |
| --- | --- |
| CNCF runtime | Resolve and admit typed source reads; enforce resource/deadline bounds and release; preserve source attribution |
| Deployment provider | Sample only explicitly admitted deployments and return deployment facts, with no service adoption or lifecycle transition |
| Component health/readiness producer | Supply the declared normal health/readiness observation and its scope |
| Endpoint provider | Supply the explicitly selected bounded reachability observation from its actual network vantage point |
| Control Center | Consume normal producer results, correlate explicit targets, join its authoritative Launcher leases and project one shared Operational Summary |
| Web/native presentations | Render the shared projection, source availability and connection state |

The independent axes, timestamps and unavailable semantics are owned by R3/R4.
There is no second workflow engine, lifecycle registry, event store, polling
daemon or cross-axis health engine in this read path.

## Runtime-owned services

Existing managed-service definitions, registry entries and ownership labels
remain owned by the service-container runtime. The read capability resolves
the selected owner/service key through those existing contracts, then requests
an independently admitted current inspection. Registry lifecycle status and
lifecycle metrics retain their original scope; they are not deployment samples.

Observation uses the read facet of a runtime provider. It does not invoke the
lifecycle coordinator to resolve, create, start, stop or clean up a service.
Sharing provider implementation is permitted only behind distinct read/mutation
admission. A consumer never obtains the lifecycle-capable gateway itself.
R5 fixes the resulting contract.

## Externally managed infrastructure

An external Docker/Compose observation target belongs to the observation
configuration, independently of a managed-service definition. A runtime-owned
read provider binds the exact deployment selector and its read authority. The
runtime's provider validates source identity and projects the bounded facts.
The provider neither claims lifecycle ownership nor registers shutdown cleanup.

The existing endpoint-only `ServiceContainerDefinition.External` contract is
unchanged. Its endpoint can be used only under its existing admission; an
endpoint is not Docker inspection authority. A separately configured observation
source is required for external deployment facts under R5. Infrastructure remains
addressable without a CAR identity or an invented Launcher registration.

## Control Center composition

Control Center selects actual producer identities and configured source
connections, invokes their normal authenticated operations, validates the
versioned observations and joins only explicitly associated runtime identities
to its existing registration/lease projection. Its same shared Summary feeds
Web and Menu Bar presentations. R8 owns the consumer-visible behavior.

Detailed CNCF health, runtime and infrastructure control remain with their
owners. A Summary read supplies neither lifecycle commands nor Docker socket
access. Missing deployment evidence remains a named source gap and cannot be
filled from Compose desired topology, HTTP success or a running lease.

## Integration boundaries

The configuration/model, normal system QUERY, typed runtime read capability,
provider implementation and consumer projection are separate delivery units
with the executable and actual-host evidence required by R9. Their integration
point is the existing Component/ActionCall runtime boundary, not a parallel
application adapter or standalone command protocol.

The selected installed producer must expose the specified QUERY and read
capability before a consumer can claim connected operation. Static contract
documents do not install providers, grant runtime permissions, establish host
results or accept a development Phase.

## Host declaration and implementation wiring

The strict v1 host schema is specified in the specification's Host declaration
section. The confidential `textus.subsystem.operational-observation.plan`
parameter is admitted only from the selected Subsystem's Home, Project or Cwd
file binding. It has no CLI or environment authority fallback. Bootstrap binds
an immutable `OperationalObservationRuntime` into the existing scope; actual
Component contexts inherit it. No configuration produces the R6 not-configured
wire, while an explicit client profile may contain disabled ops/dev targets.

The default Component operation uses `Behavior.operational_observations`, which
constructs `UnitOfWorkOp.ReadOperationalObservations`. The existing interpreter
performs authenticated administrative admission and actual Component ownership
checks before the capability selects providers. Provider GETs return through
that same interpreter's existing `HttpGet` admission and HTTP driver. There is
no direct application HTTP client or lifecycle gateway exposed to a consumer.
The new operation must also be classified as an external boundary by the
existing `UnitOfWorkOperationClassifier`; omission is an integration defect,
not permission to bypass normal UoW planning.

This adapter selects an explicitly configured HTTP(S) Docker Engine read
endpoint or read-only proxy. Its only request is the exact filtered
`GET /containers/json?all=1&filters=...`. The provider revalidates returned names
or labels locally; it never falls back to a local CLI, Unix socket or ambient
daemon. This does not enable a Docker TCP listener. Host preparation of that
read boundary and the ordinary exact HTTP origin/GET grant remains separate.
The observation Plan grants the query's logical source binding, while existing
HTTP admission independently grants each exchange; neither replaces the other.

Runtime-owned selectors read the existing registry and validate live image,
ports, ownership labels and contract digest using `ServiceContainerCompatibility`.
They do not lazily create a service runtime, register an owner, update lifecycle
state or invoke the existing mutation gateway. External selectors have no
lifecycle registration. Registry values supply compatibility, never live state
or a fabricated observation timestamp.

## Public source declarations and deadlines

R6's configured result returns its actual selected profile and safe
`healthContracts` metadata for returned enabled targets only. These declarations
come from the same admitted Plan used to acquire observations, are sorted and
bounded, and expose only logical sourceId, scope and finite values. Consumers
validate the actual observed health value/scope against this public metadata;
they need no private configuration copy or additional metadata operation.
Not-configured omits declarations, and disabled targets yield no declarations
or source acquisitions. A selected source that fails still has its declaration,
while its observation remains unavailable without a fabricated value.

Current HTTP sources must actually sample on the exchange. Their selected
host source contract supplies the probe scope when the response omits it;
an explicit returned scope must agree and is retained. Reported sources require
their own UTC timestamp and matching returned scope; those instants are never
replaced with request time. Reachability is sampled from the current producer's
network vantage point and does not infer service health from an HTTP status.

Operation elapsed time uses a monotonic clock; observation instants use the
ExecutionContext clock. Each exchange gets the smaller of the remaining
operation budget and per-source ceiling via the existing HTTP total timeout,
with redirects disabled and a source byte bound. The existing bounded HTTP
exchange owns cancellation and transport settlement. The existing Job
cancellation scope interrupts the active exchange through a short-lived
registration released in finally. No scheduler, retry loop or timer is added.
The business result is separately bounded after projecting safe data. A consumer
requiring a shorter deadline must use compatible admitted native/host limits;
the public request still accepts only limit.

## Evidence and remaining integration

The seven new executable specifications use local JSON, typed host bindings,
normal operation metadata and an injected HTTP fixture driver. They exercise
model/codec/provider semantics, ordinary ActionCall/UoW execution and public
embedding initialization. They do not establish an installed operation, a real
HTTP server connection, live Docker permission or an actual server/client host
profile. Those delivery checks and independent review remain with the caller's
native validation owner, including the matching CNCF source/check binding.
