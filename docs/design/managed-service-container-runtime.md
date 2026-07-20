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
port. Persistence is either ephemeral or a set of validated named volumes
paired with validated absolute container target paths; it cannot carry a host
path. The provider consumer owns the target because it is part of the provider
image contract; CNCF owns validation and infrastructure projection.

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

## Constrained Gateway

`ServiceContainerGateway` is the only infrastructure lifecycle boundary used
by the service-container runtime. Its surface is deliberately narrower than a
Docker client: inspect by admitted registry key, create from an admitted
runtime-owned definition, and start, readiness-check, stop, restart, or remove
by safe provider instance identity. It has no generic argument vector,
environment map, host path, host mount, shell text, or provider-command escape
hatch.

Create derives framework-owned infrastructure labels from the admitted
definition. The fixed labels identify the managed marker, owner kind, owner id,
logical service id, and a deterministic admitted-contract digest. Components
cannot supply or override those labels. The digest covers the full definition
that affects lifecycle compatibility; it is evidence for reuse and is not an
application id or secret.

Inspection is an internal runtime projection rather than a component API. It
contains safe provider instance identity, registry key, image, declared ports,
ownership evidence, status, and optional credential-free endpoint. It does not
project raw provider output or a provider handle.

Compatibility has three results. Exact ownership and definition evidence is
`Compatible`. Missing or mismatched owner/service evidence is
`OwnershipConflict`. Matching ownership with a changed contract digest, image,
or port declaration is `IncompatibleDefinition`. The lifecycle runtime must
resolve this result before selecting reuse; a matching name alone is never
evidence of ownership.

`FakeServiceContainerGateway` implements the same typed surface without a
Docker daemon. It assigns deterministic instance identities, records typed
transition order, and returns ordinary structured `Consequence` failures. It
is the default executable-specification driver.

`DockerServiceContainerGateway` is the production infrastructure driver. It
constructs `docker` process argument vectors only from validated model values
and never invokes a shell. Creation publishes ports on random loopback host
ports, projects only framework-owned labels, and materializes only admitted
named-volume/container-target pairs. Inspection uses the complete owner/service
label key before decoding provider state. Readiness probes contact only the
projected loopback port. Provider stdout/stderr is not exposed as lifecycle
diagnostic data.

Docker runtime installation is deployment opt-in. The canonical selector is
`textus.service-container.driver=docker`; compatibility aliases under
`textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` are accepted. The default is
`none`. A deployment may select the Docker executable with
`textus.service-container.docker.executable`; it is passed directly to
`ProcessBuilder` as one executable value and is never parsed as shell text.
Subsystems resolve and install this runtime lazily when a component first asks
for managed lifecycle access. External endpoint mode does not request it.

## Lifecycle Runtime

`ServiceContainerRuntime` coordinates definitions, registry state, and the
gateway. It is the component-facing endpoint-resolution boundary; gateway
inspection and provider instance identity remain internal.

External definitions return their validated endpoint directly. They never
enter the owned registry and never call the gateway. Runtime-owned definitions
are registered by explicit owner/service key and then inspected. Absence under
`CreateOrReuse` selects create and start. Absence under `RequireExisting` is a
structured failure. Presence selects compatibility checking before any
mutation. Created or stopped instances start, unhealthy or failed instances
restart, and starting or ready instances proceed directly to readiness.

Readiness is the completion boundary for resolution and restart. Success
returns a `ServiceContainerResolution` containing the safe endpoint and enough
runtime metadata to distinguish creation from reuse. Failure keeps the original
`Conclusion` authoritative and moves registry state to `Unhealthy`; a registry
synchronization failure is retained as secondary diagnostic evidence.

Registry synchronization avoids a revision update when status and endpoint are
already equal. Consequently repeated resolution of one ready compatible
service observes and verifies readiness but does not create another provider
resource or churn registry revision.

Explicit stop and remove are idempotent. They verify current ownership and
compatibility before provider mutation. An already stopped service is not
stopped again; an already removed service returns an absent no-op outcome.
Restart requires a currently registered and observed compatible provider
resource and completes only after readiness.

The runtime owns `shutdownC`, which applies cleanup policies in deterministic
registry order. `Keep` preserves state, `Stop` converges to a retained stopped
entry, and `Remove` converges to absent provider and registry state. One cleanup
failure does not prevent later entries from being recovered; failures are
aggregated after the deterministic best-effort pass. This method is not a
UnitOfWork resource or terminal callback. The host/subsystem shutdown sequence
invokes it in the subsequent runtime-integration slice, where cleanup
diagnostics are also projected.

## Observability Boundary

Subsystems store the raw lifecycle runtime internally but expose only an
ExecutionContext-bound observed wrapper to component behavior. This keeps
CallTree attribution in the calling component's execution context while
leaving shutdown usable outside an ActionCall.

The observed wrapper emits one `service-container:<operation>` CallTree node.
Inputs are restricted to logical owner/service identity, ownership mode,
cleanup policy, and validated image identity. Success may add lifecycle status
and credential-free endpoint authority. Failure adds only status and the common
`ConclusionDiagnostics` key; provider display text is deliberately omitted.

The corresponding metric scope is `service-container.lifecycle`. Metrics use
only outcome, operation, ownership mode, logical owner/service identity,
cleanup policy, status, and common diagnostic key. Image, endpoint, provider
identity, digest, paths, credentials, and payloads are excluded to keep labels
bounded and safe. Failures also participate in the common
`service-container` diagnostic scope. Its stored diagnostic record is a
restricted re-projection: display text and previous-Conclusion chains are
omitted, and only framework-owned service-container policy names survive.

## Host Shutdown

A Subsystem admits at most one lifecycle runtime. Reinstalling the identical
instance is idempotent, while replacement is rejected because it could orphan
resources owned by the first registry.

Shutdown first quiesces the JobEngine so active tasks cannot race service
cleanup. It then runs deterministic best-effort service cleanup even when job
shutdown fails. Both failures are represented with ordinary Conclusions and
combined when necessary. `shutdownC` exposes this structured result; the
legacy Unit-returning `shutdown` entrypoint delegates to it after metrics and
diagnostics have been recorded.

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

## Textus AI Consumer

Textus AI is the first runtime consumer. Without an explicit deployment
endpoint, it declares one component-runtime-owned Ollama service with typed
image, named persistence targeting `/root/.ollama`, port, readiness, reuse,
and cleanup policy. It
requests the observed runtime from its owning Subsystem when provider execution
first needs the endpoint. It does not install inspect/start/run Docker commands
in its component Process Execution scope.

The resolved endpoint is authoritative. Provider model installation follows
readiness as a separate Ollama HTTP operation through CNCF's internal HTTP DSL,
and is cached only after all selected models succeed. An explicit Ollama
endpoint constructs no runtime-owned definition and does not request the
service-container runtime.

## Textus SIE Consumer

Textus SIE keeps explicit Fuseki and Chroma-compatible endpoints as its
default deployment mode. When a provider is selected as managed, SIE declares
component-owned Fuseki and vector services through the common runtime. SIE
owns image, readiness, and container persistence targets
`/fuseki/databases` and `/data`; CNCF owns lifecycle and safe endpoint
publication. Provider dataset initialization remains a separate SIE operation.

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
