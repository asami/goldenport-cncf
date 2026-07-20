# Managed Service Container Runtime Specification

Status: normative static specification

## Scope

This specification fixes the ownership and execution boundary for long-lived
container-backed services managed by CNCF. Detailed runtime model, gateway,
transition, readiness, persistence, and failure records are specified by later
Phase 44 work.

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

## Deferred Contract

The following contract details belong to later Phase 44 slices: concrete model
types, registry implementation, reuse compatibility fields, gateway methods,
readiness probes, persistence declarations, transition outcomes, structured
failure vocabulary, CallTree projection, metrics, and runtime shutdown wiring.

## Executable Evidence

Phase 44 fake-gateway and lifecycle executable specifications will provide the
behavioral evidence for these requirements when the model and runtime are
implemented. Normal executable specifications must not require a Docker
daemon.
