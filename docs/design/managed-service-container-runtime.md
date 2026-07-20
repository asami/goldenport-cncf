# Managed Service Container Runtime

Status: normative design

## Purpose

The Managed Service Container Runtime is the CNCF execution-platform boundary
for long-lived services whose lifecycle is owned by a component runtime. It
provides typed ownership, endpoint, readiness, reuse, and cleanup semantics
without exposing a generic Docker command surface to component behavior.

This runtime is provider-neutral. Ollama, Fuseki, vector stores, and other
service products are consumers of the contract rather than CNCF runtime
concepts.

## Canonical Vocabulary

- A **service definition** is the admitted declaration of one logical service,
  its ownership mode, image identity when managed, endpoint contract,
  readiness policy, persistence policy, reuse policy, and cleanup policy.
- A **service owner** is the explicit runtime/component scope responsible for
  the managed lifecycle. Ownership is not inferred from a container name,
  endpoint, image, process, or provider convention.
- A **managed service** is a long-lived service instance created or adopted by
  the runtime only after ownership and compatibility checks succeed.
- An **external service** is an endpoint supplied by deployment configuration.
  CNCF does not inspect, create, start, stop, restart, or remove its backing
  resource.
- The **service registry** is the runtime-owned lifecycle registry keyed by
  service owner and logical service identity.
- The **service-container gateway** is a constrained infrastructure driver. It
  accepts typed lifecycle intents and does not accept arbitrary Docker
  arguments, shell text, environment maps, or host mounts from components.
- The **service-container runtime** resolves ownership mode, coordinates the
  registry and gateway, and returns a credential-free endpoint after the
  declared readiness contract succeeds.

## Ownership Modes

Every admitted service definition selects exactly one ownership mode.

### External

External mode resolves an explicitly configured endpoint and performs no
gateway operation. Endpoint validation and safe projection still apply, but
the runtime does not claim ownership and does not register lifecycle cleanup.

### Runtime Owned

Runtime-owned mode binds the logical service to an explicit service owner. The
runtime may inspect, create, reuse, start, wait for readiness, stop, restart,
or remove the backing service only through the constrained gateway. A
same-name resource is reusable only when deterministic ownership labels and
the admitted compatibility contract both match.

## Runtime Model

`ServiceContainerOwner` combines an explicit owner kind with a safe logical
owner id. `ServiceContainerId` identifies the logical service within that
owner. `ServiceContainerRegistryKey` is their product; a service name alone is
never a registry key.

`ServiceContainerDefinition.External` contains only the logical service id and
a validated credential-free HTTP(S) endpoint.
`ServiceContainerDefinition.RuntimeOwned` contains the owner, image identity,
unique logical/container ports, bounded readiness policy, persistence policy,
reuse policy, and cleanup policy. Readiness references a declared logical
port. Persistence is either ephemeral or a set of validated named volumes; it
cannot carry a host path.

The initial readiness probes are HTTP and TCP. Both use a positive bounded
timeout and polling interval. HTTP readiness also carries a safe absolute path
and a non-empty expected status set. The gateway implementation owns actual
transport execution.

The initial reuse policies are `CreateOrReuse` and `RequireExisting`. The
initial runtime-shutdown cleanup policies are `Keep`, `Stop`, and `Remove`.
Registry status is one of `Declared`, `Absent`, `Created`, `Starting`, `Ready`,
`Unhealthy`, `Stopped`, or `Failed`; transition legality belongs to the
lifecycle runtime. The typed transition vocabulary is `Inspect`, `Create`,
`Reuse`, `Start`, `CheckReadiness`, `Stop`, `Restart`, and `Remove`.

`ServiceContainerRegistry` admits runtime-owned definitions only. Exact repeat
registration is idempotent, while a different definition under the same owner
and service id is an incompatibility conflict. Status updates and removal use
monotonic revisions so concurrent lifecycle decisions cannot silently replace
one another. A `Ready` registry entry always has a validated endpoint.

## Lifecycle Ownership

The service registry belongs to the component/runtime lifecycle, not to an
operation, ActionCall, Task, Job, or UnitOfWork. A managed service may outlive
the UnitOfWork that first requested it. Runtime shutdown applies the admitted
cleanup policy idempotently to every owned registry entry.

UnitOfWork terminal cleanup must not stop a managed service. UnitOfWork
resources remain appropriate for operation-scoped resources such as process
handles and temporary WorkAreas; they are not the ownership mechanism for
long-lived services.

## Relationship To Process Execution

Process Execution is the canonical CNCF boundary for an approved one-shot
external program. It runs inside an ActionCall/UnitOfWork and its process
handle is released when that UnitOfWork terminates.

Managed Service Container Runtime is for a reusable endpoint-bearing service
with runtime-level ownership and readiness. It is not a Process Execution
driver, a Command execution mode, or a way to retain an operation-owned
process after UnitOfWork termination. Provider initialization performed after
readiness, such as installing a model or preparing a dataset, remains a
separate observable consumer operation.

## Existing Docker Adapter Boundary

`CommandDockerAdapter` remains a legacy one-shot `docker run --rm` adapter and
does not define managed-service semantics. New one-shot integrations use
Process Execution.

`ServerDockerAdapter` is an obsolete stub. It must not be completed, adapted,
or redirected by adding lifecycle behavior to
`DockerAdapter.execute(DockerInput)`. The typed service-container runtime and
gateway replace that proposed direction. The stub may be removed after the
new contract is implemented and repository references have been migrated.

## Safety And Observability

Component behavior receives an admitted service capability or a resolved safe
endpoint. It does not receive a Docker client, container handle, host path, raw
environment, credential, or arbitrary lifecycle command surface.

Lifecycle diagnostics may include logical service identity, safe image
identity, ownership mode, transition, readiness outcome, elapsed time, and a
credential-free endpoint authority. They must not include credentials,
environment values, sensitive mount details, provider payloads, prompts,
models, datasets, or arbitrary gateway output.

Lifecycle failures use ordinary `Consequence.Failure(Conclusion)` values.
Gateway unavailable, image unavailable, ownership conflict, incompatible
existing service, port conflict, startup failure, readiness timeout, unhealthy
service, and stale registry revision remain structurally distinguishable
through existing taxonomy, `Cause.Kind`, and descriptor facets. The runtime
does not define a parallel error envelope or application `detailCode`.

## Non-Goals

- Docker Compose interpretation or cluster orchestration;
- arbitrary Docker arguments, shell commands, environment maps, or host mounts;
- provider-specific model, dataset, or collection management;
- replacing Process Execution for one-shot tools;
- adopting an existing resource from its name alone;
- tying a long-lived service lifecycle to UnitOfWork cleanup.
