# Phase 73 Checklist - Component Knowledge Contribution Hub

status=in_progress
phase=[Phase 73](phase-73.md)

## Stage Status

- Current status: IN_PROGRESS
- Owner: CNCF maintainers
- Update rule: Check an item only from the stated executable, review, or
  release evidence.

## Execution preparation

Execution order updated 2026-10-08. All 12 original outcomes retain their
stable IDs below; only accepted inventory P73-A-01 is checked. Their batch ownership is recorded in the
[Phase execution order](phase-73.md#execution-order-and-evidence-allocation).

P73-MR-02 authored the [frozen main-route design](../design/component-knowledge-contribution-hub.md),
[public specification](../spec/component-knowledge-contribution-hub.md),
provider/model/hub implementation, activation-completion observation and active
E1–E6 executable scenarios. The spec evidence matrix allocates normal opaque
delivery, captured-generation rejection, 50-case arbitrary-byte preservation,
absence, ordinary Server read placement and two independent consumers across
all six admission permutations. Parent-recorded Stage A expected-red invocation
`p73-mr02-red2-20261008-01a10b3f` compiled and completed with one absence pass
and ten intended failures out of 11 tests in two suites, no aborted suite and
`lock=released`. This is authored failing-first evidence only.

Parent-verified final focused invocation `p73-mr02-final-20261008-01a10b3f`
compiled and passed the assembled suite (7/7), then contract plus existing
activation lifecycle suites (16/16): 23/23 tests across three suites, zero
failures or aborted suites. The active ScalaCheck opaque-byte property passed
with a minimum of 50 successful generated cases. SBT and wrapper exited 0 with
`lock=released`. Independent review and main-route Step acceptance remain
pending, as do broader Phase full validation and closure.
P73-A-03–04, P73-PREP-01–03 and main-route outcome rows remain open until their
full obligations have accepted evidence.

Main-route-first order: one provider -> assembled CNCF discovery/read -> one
consumer, then a second independent consumer. Establish and retain that
executable route in E2 before E3 branch development. P73-A-03–04 and
P73-PREP-01–03 span E1/E3: complete only their main-route portion before E2,
keep the whole row open until all its obligations are proved, and finish
replacement/failure/compatibility/isolation details alongside E3. Neither the
second provider nor an exhaustive fixture matrix is an E2 entry prerequisite.

- [ ] P73-PREP-01: Record run-mode-specific registration/readiness/read timing
      and provider/consumer ordering in the frozen design/spec, preserving Phase 70.
- [ ] P73-PREP-02: Freeze generation scope, descriptor/read binding, duplicate
      provider disposition and complete-replacement behavior without hot swapping.
- [ ] P73-PREP-03: Allocate one opaque provider/consumer evidence matrix between
      pure contract checks, assembled integration and supplier handoff acceptance.

One attributable evidence record may satisfy multiple explicitly mapped rows.
Existing Phase 70 acceptance is prerequisite evidence, not a fresh Phase 73
result. E4 reuses applicable E2/E3 evidence; it does not repeat the same driver
solely because acceptance is a separate work-group heading.

## P73-A: Inventory and Contract Freeze

- [x] P73-A-01: Inventory reusable component registry, assembly, activation, resource,
      and KnowledgeSource facilities before adding a new abstraction.
      Evidence 2026-10-08: [eight-entry reuse inventory](../journal/2026/10/2026-10-08-phase-73-knowledge-contribution-inventory.md),
      independent review `p73-mr01-review-20261008-01a10b3f` revision 1 by
      `/root/p73_mr01_inventory_review`, and parent M0 amendment
      `p73-mr01-m0-amendment-20261008-01a10b3f` revision 1 resolving
      `CPB-P73-MR01-001` (`componentInstanceId` -> `instanceId`). This accepts
      inventory only; no executable-route acceptance is inherited.
- [ ] P73-A-02: Freeze separate `knowledgeDomainId` and `providerComponent` identities.
- [ ] P73-A-03: Freeze the versioned descriptor, complete immutable snapshot,
      generation, provenance, discovery/read, and failure contracts.
- [ ] P73-A-04: Register opaque failing-first fixtures with two interchangeable
      providers, one active at a time, and two consumers.

## P73-B: Hub Implementation

- [ ] P73-B-01: Implement only missing domain-neutral provider registration and
      consumer discovery/read mechanics.
- [ ] P73-B-02: Preserve opaque domain records without CNCF interpretation or inference.
- [ ] P73-B-03: Enforce deterministic selection, complete-snapshot replacement,
      attribution, isolation, and absence/failure behavior.
- [ ] P73-B-04: Integrate availability after ordinary component assembly without direct
      provider-to-consumer implementation wiring.

## P73-C: Supplier and Consumer Acceptance

- [ ] P73-C-01: Verify one provider can be consumed independently by two consumers.
- [ ] P73-C-02: Verify provider replacement can retain `knowledgeDomainId` while
      changing `providerComponent`.
- [ ] P73-C-03: Produce frozen handoffs for Textus Knowledge Editor Phase 2 and Textus
      BoK Phase 7.5 without claiming their consumer acceptance.
- [ ] P73-C-04: Run proportionate CNCF validation, reconcile documentation, complete
      independent review, and create the Phase release commit.
