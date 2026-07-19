# Stateful Docker Container Adapter Handoff (2026-07-20)

status=handoff
updated_at=2026-07-20
tag=docker, container, lifecycle, textus-ai, textus-sie

## Position of This Record

This journal entry records a framework gap observed while designing the local
Gemma/Ollama runtime for Textus AI. It is non-normative. A selected contract
must be promoted to CNCF design and specification documents, with executable
specifications, before it becomes an implementation commitment.

## Confirmed Current State

CNCF has two Docker adapter shapes in
`org.goldenport.cncf.backend.docker.DockerAdapter`:

- `CommandDockerAdapter` materializes a temporary work tree and invokes
  `docker run --rm`. It is appropriate for isolated, one-shot command work.
- `ServerDockerAdapter` is explicitly intended for stateful or session-based
  images, but currently returns `Consequence.notImplemented`.

The one-shot adapter does not own or expose a durable container identity,
health/readiness state, published endpoint, startup/stop/restart transition,
or reuse policy. Therefore it cannot be used as the component-owned runtime
boundary for a local service such as Ollama.

## Shared Consumer Need

### Textus AI

The `gemma` runtime profile is intended to make a local Ollama service usable
without requiring every application to supply a separate endpoint. The desired
runtime behavior is:

1. use an explicitly configured Ollama endpoint when one is supplied;
2. otherwise inspect a component-owned Ollama container;
3. start an existing compatible container or create one from the configured
   image;
4. wait for a bounded readiness condition;
5. ensure the selected model is present; and
6. call the endpoint while retaining safe lifecycle and CallTree summaries.

This is a long-lived service-container lifecycle, not a `docker run --rm`
command. Textus AI currently uses its managed-process binding as an interim
implementation boundary; that does not establish a reusable CNCF Docker
server-adapter contract.

### Textus SIE

Textus Semantic Integration Engine has the same framework requirement. Its
`docker/ks-14` validation/development environment uses reusable external
service containers, including the Fuseki and embedding/vector-store services.
The SIE documentation deliberately keeps those external store containers alive
while removing its owned CNCF server and temporary artifacts. A component
deployment that owns those services needs the same explicit distinction
between an external endpoint and a component-owned, long-lived container.

SIE must not solve this by embedding compose scripts or arbitrary Docker
commands in provider adapters. That would make ownership, readiness, endpoint
publication, cleanup, and observability inconsistent with Textus AI and with
other CNCF components.

## Required CNCF Contract Direction

Evolve or replace `ServerDockerAdapter` as a typed, component-owned service
container capability. It should be independent of Ollama, Gemma, Fuseki,
Chroma, and any particular application.

The initial contract should express at least:

- a stable logical service name and a deterministic ownership label set;
- image reference and an explicit create-or-reuse policy;
- constrained port publication and an endpoint projection for the owning
  component;
- inspect, start, create, stop, and remove transitions with idempotent
  outcomes;
- bounded readiness probes with typed timeout and unhealthy outcomes;
- a declared persistence/volume policy and a clear cleanup owner;
- image-pull and model/data initialization as separate, observable steps;
- failure categories for Docker unavailable, image unavailable, name/port
  conflict, startup failure, readiness timeout, and incompatible existing
  container; and
- safe CallTree attributes: logical service name, image identity, lifecycle
  transition, endpoint authority without credentials, readiness result, and
  elapsed time. Container environment values, mounted host paths when
  sensitive, prompts, model data, and credentials must not be published.

The API must accept an externally supplied endpoint as a first-class bypass.
In that mode CNCF neither inspects nor mutates Docker resources, and records
the ownership mode as `external`.

## Boundary and Non-Goals

- This is not a generic Docker Compose interpreter.
- It must not let a CAR submit arbitrary Docker arguments or host mounts.
- It must not infer ownership from a container name alone; labels and the
  declared service contract are required.
- It must not conflate one-shot batch jobs with long-lived services.
- Provider-specific bootstrap steps, such as `ollama pull`, remain consumer
  extensions after the container endpoint is ready. CNCF supplies the service
  lifecycle mechanism, not a model catalog.

## Proposed Delivery Sequence

1. Promote this record into a CNCF design note that selects the
   `ServerDockerAdapter` evolution strategy and vocabulary.
2. Write an executable specification using a fake Docker gateway for inspect,
   create-or-reuse, readiness failure, external-endpoint bypass, and safe
   observability.
3. Implement the typed service-container lifecycle in CNCF, without requiring
   a Docker daemon for normal tests.
4. Migrate Textus AI's component-owned Ollama provisioning to the new CNCF
   capability; retain its external endpoint override.
5. Migrate the relevant Textus SIE service-container deployment path, keeping
   its existing development/validation compose scripts as tooling rather than
   component runtime behavior.
6. Run live Docker integration only as an explicitly selected heavy test.

## References

- `src/main/scala/org/goldenport/cncf/backend/docker/DockerAdapter.scala`
- `src/test/scala/org/goldenport/cncf/backend/docker/CommandDockerAdapterSpec.scala`
- `textus-ai/docs/notes/gemma-integration-design-note.md`
- `textus-ai/docs/phase/phase-5.md`
- `textus-semantic-integration-engine/docker/ks-14/`
- `textus-semantic-integration-engine/README.md`
