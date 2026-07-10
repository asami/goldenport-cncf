# Phase 29 — Typed Component API and Multi-Instance SPI Checklist

This document contains detailed task tracking and decisions for Phase 29. It
complements the summary-level phase document (`phase-29.md`).

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document holds summary status only.
- A DONE item here must also be checked in the phase dashboard.
- Investigation and rejected alternatives belong in the Phase 29 journal.
- The working contract remains in
  `docs/notes/typed-component-api-and-multi-instance-spi.md` until TC-10.

## TC-01: Open Phase 29 and Freeze the Working Scope

Status: DONE

### Objective

Open Phase 29 as the implementation phase for typed component APIs and named
multi-instance SPI consumption.

### Detailed Tasks

- [x] Add the Phase 29 dashboard and checklist.
- [x] Register `9.28 Typed Component API and Multi-Instance SPI` in strategy.
- [x] Select Phase 29 as the current phase.
- [x] Use the existing journal as the consideration record.
- [x] Use the existing note as the mutable implementation specification.
- [x] Keep Phase 28 closed.

### Decisions

- CNCF standard SPI and Cozy-generated component APIs share runtime wiring and
  invocation infrastructure but retain separate contract ownership.
- Providers make their typed service available; consumers select single or set
  cardinality.
- Component identity, exact instance selection, and provider variation remain
  separate concepts.
- Typed calls preserve the CNCF operation/action boundary.
- `Record` remains the generic boundary type, not the normal application API.

### Expected Output

- Phase 29 is visible as active in strategy.
- TC-02 can begin without reopening the architectural discussion.

## TC-02: Named Component Instance Descriptor and Creation Model

Status: DONE

### Objective

Allow one assembly to instantiate one component type multiple times with
stable identity and isolated settings.

### Detailed Tasks

- [x] Extend assembly component declarations with `instance`.
- [x] Add instance-local `config`, `rules`, `purposes`, `tags`, priority, and
      default metadata.
- [x] Preserve duplicate component types through assembly loading.
- [x] Create each instance with a stable `ComponentInstanceId`.
- [x] Isolate effective configuration and rule metadata by instance id.
- [x] Reject duplicate instance ids and malformed instance declarations.
- [x] Add descriptor parsing and component-space executable specs.

### Implementation Evidence

- `GenericSubsystemComponentBinding` retains named-instance metadata and merges
  defaults by component type plus instance id.
- `ComponentCreate` carries `ComponentInstanceMetadata` into generated factory
  construction, and the resulting bundle participants expose stable participant
  instance ids and isolated rule/selection metadata.
- Instance-local config is installed as component-scoped resolved parameters,
  overlaying packaged component config with global runtime parameters retained
  as the parent.
- `GenericSubsystemFactory` materializes repeated descriptor bindings from one
  discovered component bundle and preserves its primary and componentlet
  participants, while undeclared legacy components retain the existing
  name-based duplicate collapse behavior.
- Descriptor, generated factory, and subsystem factory executable specs cover
  parsing, rejection, construction, property isolation, and component-space
  lookup.

## TC-03: Exact Instance Binding and Resolver Semantics

Status: NEXT

### Objective

Bind a consumer socket to a specific named provider instance.

### Detailed Tasks

- [ ] Add provider instance identity to assembly SPI bindings.
- [ ] Add explicit socket identity where multiple sockets share a contract.
- [ ] Resolve providers by component type, instance, and contract.
- [ ] Keep input sockets out of provider candidate discovery.
- [ ] Return structured missing, incompatible, and ambiguous failures.
- [ ] Preserve existing `Port.of(...)` and single-provider compatibility.

## TC-04: Socket Set and Abstract Component Selection

Status: TODO

### Objective

Support several assembly-admitted providers and select one by exact or
abstract intent.

### Detailed Tasks

- [ ] Add `SpiSocketSet[S]` and resolved member metadata.
- [ ] Define `ComponentSelector` for instance, purpose, capability, and tags.
- [ ] Apply health, policy, priority, and default selection deterministically.
- [ ] Reject equal remaining candidates as ambiguous.
- [ ] Add public typed `ComponentApiResolver` over assembly-admitted instances.
- [ ] Cover required, optional, set, and non-empty-set cardinalities.

## TC-05: Generic SPI Invoker and Canonical Operation Dispatch

Status: TODO

### Objective

Provide a provider-neutral `Record` invocation path that typed proxies can use
without bypassing CNCF execution semantics.

### Detailed Tasks

- [ ] Add resolved binding/socket reference models.
- [ ] Add `SpiInvoker` contract, operation, request, and selector invocation.
- [ ] Dispatch through `OperationCall` / `ActionEngine`.
- [ ] Preserve validation, authorization, UnitOfWork, jobs, and events.
- [ ] Return structured conversion, operation, and provider failures.
- [ ] Verify typed and generic routes produce equivalent behavior.

## TC-06: Cozy-Generated Typed Component API and Proxy

Status: TODO

### Objective

Generate application-facing typed component contracts from CML service
operation metadata.

### Detailed Tasks

- [ ] Finalize CML service properties with Cozy.
- [ ] Support standard SPI declaration and component-specific socket exposure.
- [ ] Generate typed API traits and public request/response/value types.
- [ ] Generate proxy implementations over `SpiInvoker`.
- [ ] Generate both single socket and socket-set traits.
- [ ] Generate consumer requirements from `1`, `?`, and `*` multiplicity.
- [ ] Add Cozy/simple-modeler executable generation fixtures.

## TC-07: Standard SPI Single/Set Socket Alignment

Status: TODO

### Objective

Make both single and set socket forms consistently available for CNCF-owned
standard SPI contracts.

### Detailed Tasks

- [ ] Define the reusable standard socket-set baseline.
- [ ] Apply it to representative standard SPI contracts.
- [ ] Preserve existing single-socket source compatibility.
- [ ] Verify one component may consume/provide standard SPI and publish a
      generated component API without ambiguity.

## TC-08: ArtScene and Textus Scraper Development-Driver Smoke

Status: TODO

### Objective

After the framework and generator features are implemented, prove the model by
integrating configured `textus-scraper` instances into ArtScene. This is the
Phase 29 development-driver smoke, not an implementation shortcut for earlier
TC items.

### Detailed Tasks

- [ ] Publish a component-specific `TextusScraperApi` contract.
- [ ] Configure static JSoup and dynamic Playwright named instances.
- [ ] Declare `textus-scraper` as an ArtScene assembly dependency.
- [ ] Bind and invoke one exact instance through a single socket.
- [ ] Select static/dynamic behavior through a socket set or typed resolver.
- [ ] Verify instance-local config and rules affect only the selected instance.
- [ ] Replace ArtScene direct dependencies on `textus-scraper.impl` with the
      generated public typed API.
- [ ] Verify ArtScene exhibition acquisition uses the scraper component's
      public operations and canonical CNCF operation/action path.
- [ ] Verify ArtScene's existing provider/fallback behavior remains intact
      around the scraper boundary.
- [ ] Add packaged CAR/assembly integration smoke coverage.

## TC-09: Observability, Failure Semantics, and Regression Coverage

Status: TODO

### Objective

Make typed multi-instance calls diagnosable and safe under failure.

### Detailed Tasks

- [ ] Add contract, operation, provider instance, and selection basis to
      calltree metadata.
- [ ] Keep request bodies, secrets, and raw provider payloads out of tracing.
- [ ] Add metrics dimensions that do not create unbounded cardinality.
- [ ] Cover unavailable, ambiguous, incompatible, unhealthy, and rejected
      selections.
- [ ] Run CNCF core, Cozy generation, and driver integration regressions.

## TC-10: Decided Design Promotion and Phase Closure

Status: TODO

### Objective

Close Phase 29 only after the implemented contract is executable and promoted
to authoritative design documentation.

### Detailed Tasks

- [ ] Reconcile the working note with implementation evidence.
- [ ] Promote the settled contract to `docs/design`.
- [ ] Reduce the working note to a history/pointer role or mark it superseded.
- [ ] Update developer guides and document indexes.
- [ ] Record validation evidence and deferred work.
- [ ] Update strategy history and close Phase 29.

## Completion Check

- [ ] All TC items are DONE.
- [ ] Dashboard and checklist statuses agree.
- [ ] CNCF and Cozy executable specifications pass.
- [ ] The ArtScene + `textus-scraper` development-driver smoke passes.
- [ ] The decided design document is authoritative.
