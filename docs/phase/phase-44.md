# Phase 44 - Managed Service Container Runtime

Stage Status:
- Current status: ACTIVE
- Current step: SC-06 Textus AI driver
- Owner: CNCF managed service runtime, with Textus AI and Textus SIE as
  downstream drivers.

status = active

## 1. Purpose

Provide a typed lifecycle for long-lived component/runtime-owned service
containers without treating them as one-shot commands or application-owned
Docker scripts.

## 2. Selected Direction

- Do not complete `ServerDockerAdapter` by forcing service lifecycle semantics
  through `DockerAdapter.execute(DockerInput)`.
- Introduce a provider-neutral `ServiceContainerRuntime` contract and a
  constrained Docker driver/gateway implementation.
- Keep explicit external endpoints as a first-class ownership mode. External
  mode performs no container inspection or mutation.
- Keep runtime-owned services outside UnitOfWork terminal cleanup. Their
  lifecycle is attached to an explicit component/runtime owner and cleanup
  policy.
- Keep provider-specific initialization, such as model download or semantic
  store bootstrap, outside the generic lifecycle contract.

## 3. Initial Contract Surface

- Stable logical service identity and deterministic ownership labels.
- Image identity and explicit create-or-reuse policy.
- Constrained port publication and credential-free endpoint projection.
- Typed inspect, create, start, stop, restart, and remove transitions with
  idempotent outcomes.
- Bounded readiness probes and typed timeout/unhealthy outcomes.
- Persistence/volume declarations that cannot express arbitrary host mounts.
- Explicit lifecycle owner and shutdown/removal policy.
- Structured Docker unavailable, image unavailable, ownership conflict,
  incompatible existing service, port conflict, startup failure, and
  readiness timeout outcomes.

## 4. Work Stack

- SC-01: Promote the handoff into normative service-container design/spec and
  freeze ownership/lifecycle terminology.
- SC-02: Define provider-neutral model, registry, ownership, endpoint,
  readiness, persistence, and failure contracts.
- SC-03: Define the constrained Docker gateway and deterministic fake gateway.
- SC-04: Implement lifecycle resolution, create-or-reuse, readiness, and
  cleanup semantics.
- SC-05: Integrate payload-safe CallTree, metrics, diagnostics, and runtime
  shutdown.
- SC-06: Migrate Textus AI's optional owned Ollama path while retaining the
  external endpoint override.
- SC-07: Validate the Textus SIE owned-service deployment path without moving
  development compose tooling into component runtime behavior.
- SC-08: Run fake-gateway/full validation plus opt-in live Docker heavy tests,
  then close the phase.

## 5. Acceptance

- External endpoint mode never calls the Docker gateway.
- Repeated create-or-reuse converges on one compatible owned service.
- A same-name container without matching ownership labels is never adopted.
- Readiness timeout, unhealthy service, incompatible container, and port
  conflict remain distinct structured outcomes.
- Runtime shutdown follows the declared cleanup owner/policy and is
  idempotent.
- CallTree and metrics contain no credentials, raw environment, sensitive host
  mount, prompt, model data, or provider payload.

## 6. Non-goals

- Docker Compose interpretation, arbitrary Docker arguments, arbitrary host
  mounts, container orchestration, scheduling, or cluster deployment.
- Replacing Process Execution for one-shot tools.
- Provider-specific model catalogs, Fuseki dataset policy, Chroma collection
  policy, or application bootstrap workflows.
- Requiring a Docker daemon for normal executable specifications.

## 7. Source

- `docs/journal/2026/07/2026-07-20-stateful-docker-container-adapter-handoff.md`
- `docs/design/process-execution-runtime.md`
- `docs/phase/phase-34.md`
- `docs/phase/phase-35.md`

## 8. Resume Point

Phase 43 closed Jul. 20, 2026. Begin SC-01 with the runtime-level
owner/registry contract, not Docker command construction.

SC-01 completed Jul. 20, 2026. The normative managed service-container design
and specification now fix explicit external/runtime-owned modes, runtime-level
ownership, the UnitOfWork and Process Execution boundaries, constrained
gateway ownership, and retirement of the obsolete `ServerDockerAdapter`
direction. Begin SC-02 with provider-neutral model and registry types.

SC-02 completed Jul. 20, 2026. CNCF now has validated provider-neutral owner,
service, external/owned definition, endpoint, image, port, readiness,
persistence, reuse, cleanup, status, registry-entry, and structured diagnostic
models. The in-memory registry provides owner-scoped deterministic keys,
idempotent exact registration, incompatibility rejection, revision-checked
updates/removal, and Ready endpoint enforcement. Begin SC-03 with the
constrained gateway and deterministic fake evidence.

SC-03 completed Jul. 20, 2026. The constrained gateway now accepts only typed
inspect/create/start/readiness/stop/restart/remove intents, derives
framework-owned labels and a deterministic definition digest, and distinguishes
ownership conflicts from incompatible definitions before reuse. The
deterministic fake gateway provides daemon-free lifecycle evidence and typed
transition history. Begin SC-04 with external bypass and lifecycle
orchestration across the registry and gateway.

SC-04 completed Jul. 20, 2026. `ServiceContainerRuntime` now bypasses owned
state for external endpoints, converges create-or-reuse through compatibility
and readiness, supports RequireExisting, restart, idempotent stop/remove, and
applies Keep/Stop/Remove through a deterministic runtime-owned shutdown
entrypoint. Readiness failure preserves its structured `Conclusion` while
moving registry state to Unhealthy. Begin SC-05 with CallTree/metrics,
payload-safe diagnostics, and host/subsystem shutdown wiring.

SC-05 completed Jul. 20, 2026. Component-facing lifecycle access now records
payload-safe `service-container:<operation>` CallTree nodes and bounded
`service-container.lifecycle` metrics using common Conclusion diagnostics.
Provider failure text, endpoint paths, instance ids, digests, credentials, and
payloads are excluded. Subsystem shutdown now quiesces jobs before
best-effort managed-service cleanup, exposes a structured shutdown result, and
rejects runtime replacement that could orphan owned resources. Begin SC-06
with the Textus AI Ollama consumer migration.
