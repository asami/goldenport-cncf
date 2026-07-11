# Phase 29 — Typed Component API and Multi-Instance SPI

status = active

## 1. Purpose of This Document

This work document records Phase 29, which implements the `9.28 Typed
Component API and Multi-Instance SPI` development item.

Phase 29 turns the existing single-provider SPI baseline into a component
consumption model that supports typed application APIs and multiple named
instances of one component type. `textus-scraper` provides the reusable
component and `/Users/asami/src/dev2026/textus-art-scene` provides the
end-to-end development driver and post-implementation smoke application.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Create multiple named component instances from one component type.
- Isolate instance configuration, rules, purpose, tags, and runtime identity.
- Bind single sockets to exact provider instances.
- Add socket sets for assembly-bounded multi-instance use.
- Resolve instances exactly or through abstract purpose/capability/tag
  selectors without silently choosing the first candidate.
- Provide generic `Record` invocation and typed application-facing APIs over
  the same CNCF operation/action execution path.
- Generate component-specific API traits, proxies, sockets, and socket sets
  from CML service operation metadata.
- Supply both single and set input forms for CNCF standard SPI contracts.
- Preserve authorization, `ExecutionContext`, UnitOfWork, jobs, events,
  calltree, metrics, and future local/remote transport substitution.
- Validate the result by integrating `textus-scraper` into ArtScene after the
  framework feature is implemented.
- Exercise the static scraper component from ArtScene without direct `impl`
  package dependencies. Dynamic Playwright integration and application-level
  multi-instance selection are deferred until the static contract is stable.
- Verify that ArtScene exhibition acquisition reaches `textus-scraper`
  through the generated typed API and canonical CNCF operation path.

Scope boundaries:

- Phase 29 does not let application code load arbitrary CARs or construct
  provider components directly.
- Phase 29 does not expose provider `Component` objects through sockets.
- Phase 29 does not introduce a general dependency-injection framework.
- Phase 29 does not implement provider hot replacement without component
  restart.
- Phase 29 does not require distributed transport implementation.
- Phase 29 does not promote every component-specific API into CNCF core SPI.

## 3. Active Work Stack

- A (DONE): TC-01 — Open Phase 29 and freeze the working scope.
- B (DONE): TC-02 — Named component instance descriptor and creation model.
- C (DONE): TC-03 — Exact instance binding and resolver semantics.
- D (DONE): TC-04 — Socket set and abstract component selection.
- E (DONE): TC-05 — Generic SPI invoker and canonical operation dispatch.
- F (DONE): TC-06 — Cozy-generated typed component API and proxy.
- G (DONE): TC-07 — Standard SPI single/set socket alignment.
- H (DONE): TC-08 — ArtScene + static `textus-scraper` development-driver smoke.
- I (NEXT): TC-09 — Observability, failure semantics, and regression coverage.
- J (TODO): TC-10 — Decided design promotion and Phase 29 closure.

Resume hint:

- Start TC-09 by verifying component API observability and structured failure
  behavior across the ArtScene/static scraper boundary.

## 4. Development Items

- [x] TC-01: Open Phase 29 and freeze the working scope.
- [x] TC-02: Implement named component instance declarations and creation.
- [x] TC-03: Implement exact instance SPI binding and resolution.
- [x] TC-04: Implement socket sets and abstract component selection.
- [x] TC-05: Implement generic invocation through the operation/action path.
- [x] TC-06: Generate typed component APIs and proxies from CML.
- [x] TC-07: Align standard SPI contracts with single/set socket forms.
- [x] TC-08: Complete the ArtScene + static `textus-scraper` driver smoke.
- [ ] TC-09: Complete observability, failure, and regression verification.
- [ ] TC-10: Promote the settled contract to design and close Phase 29.

Detailed task breakdown and progress tracking are recorded in
`phase-29-checklist.md`.

## 5. Completion Conditions

Phase 29 can close when:

- Assembly creates two named instances of one component type with isolated
  config and rules.
- A single socket binds an exact named instance.
- A socket set contains multiple assembly-admitted instances.
- Exact and abstract selectors resolve the expected typed API.
- unavailable and ambiguous selections return structured failures.
- typed and generic calls use the same operation/action execution semantics.
- ordinary Scala consumers do not handle operation-name strings or `Record`
  conversion.
- Cozy generates component-specific API, proxy, socket, and socket-set forms.
- CNCF standard SPI contracts expose both single and set input forms.
- calltree identifies the contract, operation, selected instance, and selection
  basis without exposing confidential payloads.
- ArtScene packages and resolves `textus-scraper` through assembly and invokes
  its typed API for exhibition acquisition.
- the ArtScene smoke proves the static scraper component can be selected and
  invoked without importing provider implementation code.
- dynamic Playwright and application-level multi-instance scraper selection
  remain explicit post-TC-08 work rather than implicit static-fetch fallback.
- settled contracts are promoted from the working note to `docs/design`.

## 6. Authoritative Inputs

- `docs/notes/typed-component-api-and-multi-instance-spi.md`
- `docs/journal/2026/07/2026-07-11-typed-component-api-and-multi-instance-spi-consideration.md`
- `docs/design/component-port-wiring.md`
- `docs/rules/stage-status-and-checklist-convention.md`
