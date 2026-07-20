# Managed Service Container Runtime Specification

Status: normative static specification

## Scope

This specification fixes the ownership, model, registry, gateway, and execution
boundary for long-lived container-backed services managed by CNCF. Lifecycle
transition orchestration is specified by later Phase 44 work.

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

### Constrained Gateway (SC3-R1)

`ServiceContainerGateway` MUST expose only typed inspect, create, start,
readiness-check, stop, restart, and remove intents. Create MUST receive an
admitted runtime-owned definition and MUST derive infrastructure ownership
labels from that definition. The gateway MUST NOT accept arbitrary Docker
arguments, shell text, component-supplied environment maps, host paths, host
mounts, or raw provider commands.

Gateway inspection MUST project only the provider container identity, admitted
registry key, safe image identity, declared ports, ownership labels, lifecycle
status, and credential-free endpoint. Component behavior MUST NOT receive the
inspection or provider handle directly.

### Ownership Labels And Compatibility (SC3-R2)

Runtime-owned creation MUST apply deterministic framework-owned labels for the
managed marker, owner kind, owner id, logical service id, and admitted contract
digest. Component input MUST NOT replace these labels.

Reuse MUST require both matching ownership identity and matching admitted
contract evidence. Missing or mismatched ownership evidence MUST produce the
ownership-conflict outcome. Matching ownership with a changed contract, image,
or declared port set MUST produce the incompatible-existing-service outcome.
A same-name resource MUST NOT be adopted from its name alone.

The contract digest MUST be deterministic for one admitted definition and MUST
cover owner/service identity, image, declared ports, readiness, persistence,
reuse policy, and cleanup policy. The digest is compatibility evidence, not a
credential or an application identity.

### Deterministic Gateway Evidence (SC3-R3)

Normal executable specifications MUST use a deterministic fake implementation
of the constrained gateway and MUST NOT require a Docker daemon. The fake MUST
record typed transitions, produce deterministic instance identity, preserve
normal structured failures, and implement the same gateway surface expected of
a Docker-backed implementation.

### Lifecycle Resolution (SC4-R1)

`ServiceContainerRuntime.resolveC` MUST return an external definition's
validated endpoint without registry or gateway access. A runtime-owned
definition MUST be admitted to the owner-scoped registry before gateway
inspection.

`CreateOrReuse` with no observed resource MUST create, start, and wait for
readiness. `RequireExisting` with no observed resource MUST fail without
creation. An observed resource MUST pass ownership and definition compatibility
before any start, restart, readiness, stop, or remove operation. A created or
stopped compatible resource MUST be started; an unhealthy or failed compatible
resource MUST be restarted; a starting or ready compatible resource MUST be
readiness-checked without creation.

Successful readiness MUST synchronize the registry to `Ready` with the
credential-free endpoint. Failed readiness MUST preserve the original
structured failure and synchronize the registry to `Unhealthy`. Repeated
resolution of one compatible ready definition MUST converge on one provider
instance without unnecessary registry revision changes.

### Explicit Lifecycle Operations (SC4-R2)

Restart MUST require a registered, observed, compatible provider resource and
MUST return only after its readiness contract succeeds. Stop and remove MUST
inspect and verify compatibility before mutation. Repeated stop or remove after
the requested state is reached MUST succeed without repeating the provider
mutation.

### Runtime-Owned Cleanup (SC4-R3)

The lifecycle runtime MUST provide an idempotent runtime-shutdown cleanup
entrypoint over deterministic registry order. Cleanup MUST continue across all
registered services after an individual failure and MUST return the aggregated
structured failures after best-effort recovery. `Keep` MUST preserve provider
and registry state. `Stop` MUST stop a compatible provider resource at most
once and retain a `Stopped` registry entry. `Remove` MUST remove a compatible
provider resource at most once and release its registry entry. An already
absent provider resource MUST converge to absent registry state without a
provider mutation.

This cleanup entrypoint belongs to the service-container runtime and MUST NOT
be registered as UnitOfWork terminal cleanup. Wiring the entrypoint into the
host/subsystem shutdown sequence and projecting cleanup diagnostics belong to
SC5.

## Deferred Contract

The following contract details belong to later Phase 44 slices: Docker
transport implementation, readiness transport, CallTree projection, metrics,
and host/subsystem runtime-shutdown wiring.

## Executable Evidence

`ServiceContainerModelSpec` covers safe identities, endpoints, ports,
readiness, persistence, and structured diagnostics.
`ServiceContainerRegistrySpec` covers owner-scoped keys, idempotent admission,
definition conflicts, deterministic enumeration, revision checks, Ready
endpoint requirements, and removal. `ServiceContainerGatewaySpec` covers
deterministic ownership labels, compatibility refusal, typed fake transitions,
readiness, and missing-instance behavior. `ServiceContainerRuntimeSpec` covers
external bypass, convergent create-or-reuse, RequireExisting refusal, ownership
refusal, readiness failure state, restart, idempotent stop/remove, and runtime
cleanup policies.
