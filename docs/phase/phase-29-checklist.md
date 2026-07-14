# Phase 29 — Typed Component API and Multi-Instance SPI Checklist

This document contains detailed task tracking and decisions for Phase 29. It
complements the summary-level phase document (`phase-29.md`).

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document holds summary status only.
- A DONE item here must also be checked in the phase dashboard.
- Investigation and rejected alternatives belong in the Phase 29 journal.
- The settled contract is authoritative in
  `docs/design/typed-component-api-and-multi-instance-spi.md`; the preceding
  note remains an implementation-history record.

## Post-closure Maintenance

- [x] Jul. 14, 2026: Apply explicitly selected assembly descriptor `config`
      entries as runtime defaults while preserving test-descriptor and
      command-line precedence.
- [x] Verify the assembly configuration order in CNCF executable specs and in
      ArtScene's standalone/multi-user Phase 9A smoke.
- [x] Jul. 12, 2026: Add explicit CNCF test-home support without mutating JVM
      `user.home`.
- [x] Normalize `cncf test --test-config`, `--home`, and `--temporary-home`
      into ordinary runtime modes plus test-only configuration keys.
- [x] Extend `test.yaml` / `test.json` descriptors with logical runtime and
      component datastore shorthand.
- [x] Keep assembly dependency and repository inheritance available by default
      for packaged Web/server smoke tests.
- [x] Reject explicit `local` / `sqlite` datastore declarations without a path
      instead of silently falling back to another datastore.
- [x] Reject missing `cncf test --test-config` / `--home` option values at the
      command normalization boundary.

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

Status: DONE

### Objective

Bind a consumer socket to a specific named provider instance.

### Detailed Tasks

- [x] Add provider instance identity to assembly SPI bindings.
- [x] Add explicit socket identity where multiple sockets share a contract.
- [x] Resolve providers by component type, instance, and contract.
- [x] Keep input sockets out of provider candidate discovery.
- [x] Return structured missing, incompatible, and ambiguous failures.
- [x] Preserve existing `Port.of(...)` and single-provider compatibility.

### Implementation Evidence

- `SpiProviderSelector` carries optional provider instance identity, while
  `SpiSocketSelector` carries optional consumer instance and socket name.
- `SpiSocket.spiSocketName` defaults to `default`; named sockets published as
  `Component.Port.input(...)` are resolved independently and never become
  provider candidates.
- Exact instance selection uses canonical `ComponentInstanceId` identity and
  never falls back to another instance.
- A component-only provider selector uses one declared default instance, then
  the literal `default` instance; unresolved and ambiguous cases fail
  explicitly rather than selecting the first candidate.
- Descriptor and resolver executable specs cover exact, declared-default,
  literal-default, missing, ambiguous, named-socket, and compatibility paths.

## TC-04: Socket Set and Abstract Component Selection

Status: DONE

### Objective

Support several assembly-admitted providers and select one by exact or
abstract intent.

### Detailed Tasks

- [x] Add `SpiSocketSet[S]` and resolved member metadata.
- [x] Define `ComponentSelector` for instance, purpose, capability, and tags.
- [x] Apply health, policy, priority, and default selection deterministically.
- [x] Reject equal remaining candidates as ambiguous.
- [x] Add public typed `ComponentApiResolver` over assembly-admitted instances.
- [x] Cover required, optional, set, and non-empty-set cardinalities.

### Implementation Evidence

- `ResolvedSpiMember` records logical component identity and selection metadata
  without exposing provider implementation classes.
- `SpiSocketSet` and `ComponentApiResolver` share exact and abstract selector
  semantics over the assembly-admitted member catalog.
- `cardinality: many` bindings merge by provider member identity; required sets
  fail when no healthy compatible member is available.
- Component health projection and SPI selection use the same runtime health
  snapshot; error providers are excluded before materialization. Typed
  `ComponentSelectionPolicy` supplies the policy boundary.
- Executable specs cover exact and component-only sets, selector metadata,
  health filtering, policy rejection, ambiguity, and all four cardinalities.

## TC-05: Generic SPI Invoker and Canonical Operation Dispatch

Status: DONE

### Objective

Provide a provider-neutral `Record` invocation path that typed proxies can use
without bypassing CNCF execution semantics.

### Detailed Tasks

- [x] Add resolved binding/socket reference models.
- [x] Add `SpiInvoker` contract, operation, request, and selector invocation.
- [x] Dispatch through canonical request construction and `ActionEngine`.
- [x] Preserve validation, authorization, UnitOfWork, jobs, and events.
- [x] Return structured conversion, operation, and provider failures.
- [x] Verify typed and generic routes produce equivalent behavior.

### Implementation Evidence

- `ResolvedSpiBinding` retains public socket/provider identity plus the internal
  assembly participant and declared operation catalog needed for exact,
  contract-bounded operation dispatch.
- `ComponentApiResolver.resolveBinding` restricts socket-reference resolution
  to the providers actually bound to that assembly socket. Programmatic calls
  without a socket reference retain assembly-admitted provider selection.
- Resolved bindings are owned by their source subsystem and are rejected by a
  different subsystem before operation dispatch.
- `Subsystem.spiInvoker` converts `Record.fields` to a field-preserving request
  and dispatches through authorization, operation request validation,
  `ComponentLogic`, `ActionEngine`, and operation association bindings.
- The caller runtime context is retained while the provider action receives its
  normal child action scope.
- `SpiOperationResponseCodec` defines deterministic Record, scalar, void,
  JSON, YAML, HTTP, and opaque response behavior.
- Executable specs cover exact and purpose selection, repeated fields,
  authorization, structured failures, safe CallTree metadata, response
  conversion, direct/generic result equivalence, and managed command/job/
  UnitOfWork/event behavior.

## TC-06: Cozy-Generated Typed Component API and Proxy

Status: DONE

### Objective

Generate application-facing typed component contracts from CML service
operation metadata.

### Detailed Tasks

- [x] Finalize SPI-prefixed CML service properties with Cozy.
- [x] Support standard SPI declaration metadata and component-specific socket exposure.
- [x] Generate typed API traits using public request/response/value types.
- [x] Generate proxy implementations over `ResolvedSpiBinding` and `SpiInvoker`.
- [x] Generate both single socket and socket-set traits.
- [x] Generate consumer requirements from `1`, `?`, and `*` multiplicity.
- [x] Add CNCF, Kaleidox, and Cozy/SimpleModeler executable coverage.

## TC-07: Standard SPI Single/Set Socket Alignment

Status: DONE

### Objective

Make both single and set socket forms consistently available for CNCF-owned
standard SPI contracts.

### Detailed Tasks

- [x] Define the reusable standard socket-set baseline.
- [x] Apply it to AI runner, geographic resolver, and toolchain runner SPI contracts.
- [x] Preserve existing single-socket source compatibility.
- [x] Verify one component may consume/provide standard SPI and publish a
      generated component API without ambiguity.

## TC-08: ArtScene and Textus Scraper Development-Driver Smoke

Status: DONE

### Objective

After the framework and generator features are implemented, prove the model by
integrating configured `textus-scraper` instances into ArtScene. This is the
Phase 29 development-driver smoke, not an implementation shortcut for earlier
TC items.

### Detailed Tasks

- [x] Publish a component-specific `TextusScraperApi` contract.
- [x] Declare the static `textus-scraper` as an ArtScene assembly dependency.
- [x] Bind and invoke the static component through the generated socket set.
- [x] Replace ArtScene direct dependencies on `textus-scraper.impl` with the
      generated public typed API.
- [x] Verify ArtScene exhibition acquisition uses the scraper component's
      public operations and canonical CNCF operation/action path.
- [x] Verify ArtScene's existing provider/fallback behavior remains intact
      around the scraper boundary.
- [x] Add executable assembly integration coverage with the real ArtScene and
      static scraper components and deterministic HTTP fixture.
- [x] Defer dynamic Playwright and multiple named scraper instance selection
      beyond the TC-08 static vertical slice.

### Post-closure Evidence Correction

TC-08's flattened development classpath verified typed invocation and
selection semantics, but not contract-only API packaging or isolated assembly
classloading. Phase 30 CA-07 later supplied that proof through the standard
launcher with packaged ArtScene and textus-scraper dependencies. The correction
does not change TC-08's DONE status.

## TC-09: Observability, Failure Semantics, and Regression Coverage

Status: DONE

### Objective

Make typed multi-instance calls diagnosable and safe under failure.

### Detailed Tasks

- [x] Add contract, operation, provider instance, and selection basis to
      calltree metadata.
- [x] Trace pre-binding resolution failures without copying request bodies,
      secrets, or raw provider payloads into SPI metadata.
- [x] Add finite `selection_basis` to metrics while excluding instance IDs and
      free-form selector values.
- [x] Cover unavailable, ambiguous, incompatible, unhealthy, and rejected
      selections.
- [x] Run CNCF core, Cozy generation, static scraper, and ArtScene driver
      regressions.

## TC-10: Decided Design Promotion and Phase Closure

Status: DONE

### Objective

Close Phase 29 only after the implemented contract is executable and promoted
to authoritative design documentation.

### Detailed Tasks

- [x] Reconcile the working note with implementation evidence.
- [x] Promote the settled contract to `docs/design`.
- [x] Reduce the working note to a history/pointer role or mark it superseded.
- [x] Update developer guides and document indexes.
- [x] Record validation evidence and deferred work.
- [x] Update strategy history and close Phase 29.

## Completion Check

- [x] All TC items are DONE.
- [x] Dashboard and checklist statuses agree.
- [x] CNCF and Cozy executable specifications pass.
- [x] The ArtScene + `textus-scraper` development-driver smoke passes.
- [x] The decided design document is authoritative.
