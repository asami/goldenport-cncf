# Managed Service Container Runtime Specification

Status: normative static specification

## Scope

This specification fixes the ownership, model, registry, and execution boundary
for long-lived container-backed services managed by CNCF. Gateway behavior and
lifecycle transition execution are specified by later Phase 44 work.

## Terms

- **service definition**: an admitted declaration of one logical service;
- **service owner**: an explicit runtime/component lifecycle identity;
- **external service**: a deployment-supplied endpoint not owned by CNCF;
- **runtime-owned service**: a service whose lifecycle is registered to a
  service owner;
- **service registry**: the runtime lifecycle registry keyed by owner and
  logical service identity;
- **service-container runtime**: the provider-neutral lifecycle coordinator;
- **service-container gateway**: a constrained infrastructure driver for typed
  container lifecycle intents.

## Requirements

### Ownership (SC1-R1)

Every service definition MUST select exactly one ownership mode: `external` or
`runtime-owned`. A runtime-owned service MUST have an explicit owner and stable
logical service identity. CNCF MUST NOT infer ownership from a container name,
image, endpoint, process, or provider convention.

The service registry MUST key a managed lifecycle by the admitted service
owner and logical service identity. A resource with the requested name but
without matching ownership and compatibility evidence MUST NOT be adopted.

### External Endpoint (SC1-R2)

External mode MUST use an explicitly supplied endpoint. Resolving an external
service MUST NOT inspect or mutate a container runtime and MUST NOT register
owned cleanup. The projected endpoint MUST omit credentials.

### Runtime-Owned Lifecycle (SC1-R3)

Runtime-owned mode MAY inspect, create, reuse, start, wait for readiness, stop,
restart, or remove the backing service only through the constrained
service-container gateway. Component code MUST NOT receive an arbitrary Docker
command, Docker client, container handle, environment map, or host mount
surface.

The owned lifecycle MUST belong to the component/runtime owner rather than an
Operation, ActionCall, Task, Job, or UnitOfWork. Runtime shutdown MUST apply
the admitted cleanup policy idempotently. UnitOfWork terminal cleanup MUST NOT
stop the managed service.

### Process Execution Boundary (SC1-R4)

One-shot approved external programs MUST use Process Execution. A managed
service MUST NOT be represented as retained Process Execution, a Command mode,
or a UnitOfWork resource. Provider-specific initialization after readiness
MUST remain a separate observable consumer operation.

### Docker Adapter Retirement (SC1-R5)

`DockerAdapter.execute(DockerInput)` MUST NOT become the public lifecycle
contract for managed services. `CommandDockerAdapter` remains a legacy
one-shot adapter. `ServerDockerAdapter` is an obsolete stub and MUST NOT be
completed or redirected to hide the typed service-container runtime behind the
one-shot adapter shape. It MAY be removed after the typed contract and its
consumers are implemented.

### Confidentiality (SC1-R6)

Safe lifecycle diagnostics MAY expose logical service identity, safe image
identity, ownership mode, lifecycle transition, readiness outcome, elapsed
time, and credential-free endpoint authority. They MUST NOT expose
credentials, environment values, sensitive mounts, arbitrary gateway output,
provider payloads, prompts, models, or datasets.

### Validated Runtime Model (SC2-R1)

Owner ids, logical service ids, image identities, endpoint values, logical port
names, container ports, readiness paths, readiness timing, and named volumes
MUST be validated before a definition reaches the registry or gateway.

An external definition MUST contain a logical service id and credential-free
absolute HTTP(S) endpoint without user information, query, or fragment. A
runtime-owned definition MUST contain an explicit owner, image identity, at
least one unique declared port, bounded readiness policy, persistence policy,
reuse policy, and cleanup policy. Its readiness probe MUST reference one of its
declared logical ports.

Persistence MUST be either ephemeral or use validated named volumes. It MUST
NOT represent an arbitrary host path or host mount.

### Runtime Registry (SC2-R2)

`ServiceContainerRegistry` MUST accept runtime-owned definitions only and MUST
key each entry by `ServiceContainerOwner` plus `ServiceContainerId`.
Registering the exact same definition repeatedly MUST return the same logical
entry. Registering a different definition under an occupied key MUST return a
structured incompatibility conflict.

Registry enumeration MUST be deterministic. Status update and removal MUST use
the expected monotonic revision and MUST reject stale revisions. A registry
entry in `Ready` status MUST contain a validated endpoint.

### Status And Policy Vocabulary (SC2-R3)

The initial reuse policies are `CreateOrReuse` and `RequireExisting`. The
initial cleanup policies are `Keep`, `Stop`, and `Remove`. The initial statuses
are `Declared`, `Absent`, `Created`, `Starting`, `Ready`, `Unhealthy`,
`Stopped`, and `Failed`. The typed transitions are `Inspect`, `Create`, `Reuse`,
`Start`, `CheckReadiness`, `Stop`, `Restart`, and `Remove`. SC2-R3 defines
vocabulary only; legal lifecycle transitions belong to the lifecycle runtime
contract.

### Structured Lifecycle Failures (SC2-R4)

Lifecycle failures MUST use `Consequence.Failure(Conclusion)` and MUST NOT
introduce an independent error envelope or framework-owned application detail
code. The model MUST preserve distinct structured diagnostics for gateway
unavailable, image unavailable, ownership conflict, incompatible existing
service, port conflict, startup failure, readiness timeout, unhealthy service,
and stale registry revision. Machine classification MUST use taxonomy,
`Cause.Kind`, and descriptor facets rather than display-message parsing.

## Deferred Contract

The following contract details belong to later Phase 44 slices: gateway
methods, ownership-label projection, compatibility resolution beyond exact
definition equality, legal transition execution, readiness transport,
CallTree projection, metrics, and runtime shutdown wiring.

## Executable Evidence

`ServiceContainerModelSpec` covers safe identities, endpoints, ports,
readiness, persistence, and structured diagnostics.
`ServiceContainerRegistrySpec` covers owner-scoped keys, idempotent admission,
definition conflicts, deterministic enumeration, revision checks, Ready
endpoint requirements, and removal. Later fake-gateway and lifecycle specs
cover transition execution. Normal executable specifications must not require
a Docker daemon.
