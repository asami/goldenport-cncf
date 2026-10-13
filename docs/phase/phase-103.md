# Phase 103: Validation/Test Metadata ABI and Discovery

status=planned
planned_at=2026-10-06
driver=sm-workflow Phase 5
consumer=sm-workflow, textus-cbd-support, Cozy/CML projection
static_analysis_owner=textus-cbd-support on Mac mini
checklist=[Phase 103 Checklist](phase-103-checklist.md)

## Goal

Deliver one CNCF-owned, versioned Validation/Test Metadata ABI and deterministic
operation-level discovery interface. Executable Specifications/test operations
carry the declarations; consumers query the same normalized metadata without
maintaining separate authoritative TestSuite membership files.

Canonical input: [ABI proposal](../notes/validation-test-metadata-abi-proposal.md).
Decision and planning history: [producer journal](../journal/2026/10/2026-10-06-validation-test-metadata-abi.md).
Promote the proposal into the normal spec/design/Executable Specification layers
as implementation starts; this Phase is the work plan, not a competing ABI spec.

## Responsibility and dependency boundaries

- CNCF owns purpose/feature/duration/execution-requirement vocabulary, public Scala
  ABI, normalized class/operation merge semantics and deterministic discovery.
- [sm-workflow Phase 5](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-5.md)
  drives this producer work and consumes discovery for query/select/execute,
  runtime evidence and cost/convergence guards. Phase 6 connects AI/skills;
  Phase 7 applies it across repositories and Development Artifact Overlays.
- textus-cbd-support owns static Test Architecture analysis, lint, KPI and human
  review. Its implementation runs on Mac mini as separate consumer work. Publish
  the same ABI/fixtures for it; completion of its UI/KPI work is not a producer
  or sm-workflow initial-runtime gate.
- Cozy owns CML property syntax and generation. CNCF supplies normalized mapping
  semantics and fixtures; this phase does not implement a Cozy parser.
- [Phase 99](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-99.md)
  supplies the analogous Finding Disposition producer/consumer pattern. Reuse
  established contracts where applicable without merging the two vocabularies
  or requiring unrelated Phase 99 completion merely for similarity.
- Existing execution-requirement semantics remain authoritative. This phase
  defines their metadata binding, not a second provider/execution model.

## Work status

All items are PLANNED; implementation and executable acceptance have not started.
Next work: VM-01, followed by a connected ABI/discovery implementation and specs.
No currently running phase or Goal is changed by creating this plan.

## Development items

- [ ] VM-01: Freeze the versioned normalized metadata and representation contract.
- [ ] VM-02: Implement public Scala declarations and deterministic operation discovery.
- [ ] VM-03: Verify merge, identity, compatibility and discovery through Executable Specifications.
- [ ] VM-04: Deliver runtime/static consumer handoffs and document acceptance.

Detailed tasks and evidence belong in the [checklist](phase-103-checklist.md).

## Completion boundary

The producer ABI and discovery path are usable through public APIs, with stable
operation identities and metadata origin/revision sufficient for selection,
evidence and static analysis. Executable Specifications verify the declared
merge rules and invalid/unsupported input behavior. sm-workflow's concrete
runtime consumer demonstrates discovery -> purpose/feature query -> typed
operation binding; CNCF records its handoff without claiming all of sm-workflow
Phase 5 complete. A shared static-consumer fixture is delivered for [cbd-support Phase 14](https://github.com/asami/textus-cbd-support/blob/main/docs/phase/phase-14.md) on Mac mini
without waiting for the cbd-support application implementation.

Discovery scope/completeness, unannotated operations, feature references and
operation revision provenance are part of the shared consumer contract. Declared
operation duration units/scope remain distinct from runtime measurement input.

Class/specification defaults and operation/scenario overrides, multiple purposes,
feature union, duration semantics and existing execution-requirement composition
are covered. The supported Scala test framework must expose real executable
scenario identities; discovering a class label alone is not sufficient.
Independent review and required repository validation close the connected
producer change. Group implementation/specification and review around this
boundary rather than starting a separate review cycle for every helper.

## Non-goals

No sm-workflow one-minute/ten-minute budget constants, cbd-support KPI scores,
authoritative suite registry, runtime result fields in annotations, AI-dependent
metadata interpretation, content-integrity proofs or global build-tool lock.
Missing optional metadata is distinct from malformed/unknown required semantics;
consumer coverage policy decides what a missing required selection means.


sm-workflow retains a Slice validation management file for acceptance conditions,
metadata queries, supplemental operation references and typed parameters. Phase
103 supports stable operation discovery/reference resolution; it neither bans
that plan nor duplicates its policy or test declarations in another registry.
