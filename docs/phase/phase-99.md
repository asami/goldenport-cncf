# Phase 99: Finding Disposition ABI

status=in-progress
planned_at=2026-10-04
started_at=2026-10-27
consumer=CAR lint, Cozy, textus-cbd-support, sm-workflow
related=Phase 90 Candidate-Admission

## Purpose

Define the CNCF-standard semantic contract for durable disposition of static-analysis and review findings. The first driver is CAR lint, but the contract is not CAR-lint-specific.

A finding is not resolved merely because a warning disappears. The system distinguishes an unresolved finding from an explicit developer decision and from a source/model correction.

## Public ABI

CNCF owns the public Finding Disposition ABI.

The ABI defines at minimum:

- stable finding/rule identity;
- disposition kind;
- human-readable reason;
- target/scope binding sufficient to identify the intended finding location;
- version/provenance needed for compatibility;
- Scala annotation syntax for hand-written Scala sources.

Initial disposition semantics are:

- `fix`: correct the underlying source/model;
- `accept`: explicitly accept the finding at the bound target;
- `defer`: retain the finding as known follow-up work;
- `configure`: resolve through an admitted lint/tool configuration;
- annotation/property representation is a persistence mechanism, not a separate semantic disposition.

The annotation name, arguments, disposition values and their meanings are public CNCF ABI. Individual lint rule IDs are extensible data and adding a rule does not by itself constitute an ABI break. Rule removal/renaming must preserve an explicit compatibility/deprecation path.

## Representation boundary

CNCF defines one normalized Finding Disposition semantic model.

- hand-written Scala uses CNCF ABI annotations;
- CML uses Cozy-owned CML properties mapped to the same semantic model;
- generated Scala is not an independent source of truth;
- other languages may later provide native representations without changing the semantic model.

CAR lint consumes the normalized disposition deterministically. It must remain usable without AI, cbd-support or sm-workflow.

## Human-in-the-loop boundary

This Phase does not embed approval workflow into annotations.

Human/AI resolution is a separate process:

`Finding -> Resolution Proposal -> Human Admission -> Work -> Re-lint/Review -> Close`

A tool may propose Fix / Accept / Defer / Configure, but developer admission determines which proposal may be applied. AI must not mass-insert annotations merely to reduce warning counts.

## Acceptance criteria

1. Public Scala annotation ABI and normalized disposition model are defined and versioned.
2. Exact target/rule binding and reason semantics are deterministic.
3. CAR lint can distinguish unresolved, explicitly accepted/deferred, and corrected findings.
4. New rule IDs can be introduced without changing the annotation ABI.
5. Cozy can map CML properties into the same semantic contract without depending on Scala annotation syntax.
6. A fixture proves round-trip interpretation for at least Fix-equivalent disappearance, Accept and Defer.
7. Documentation explicitly defines unresolved findings, not raw warning count, as the actionable backlog metric.

## Implementation and validation

The implementation contract is [Finding Disposition ABI V1](../spec/finding-disposition.md),
with the representation/authority boundary in [the design](../design/finding-disposition.md).
`FindingDispositionSpec` provides executable coverage. On 2026-10-27, native
compile and all 10 dedicated tests passed, and independent FD-01 review accepted
commit `8e3e2db4`. These results prove the ABI fixtures, exact binding,
compatibility, actual correction and actionable-backlog behavior.

The first whole-repository check ran 6,880 tests in 909 completed suites. Its
only failure was an existing configuration catalog test fixing the total number
of definitions at 58 while the catalog contained 59. On 2026-10-29, the native
Phase repair inventory continuation admitted that exact test file within the
same reserved repair, preserving the accepted Step, original base and counts.
The correction checks the scenario's single canonical Subsystem user-mode
registration and retains its alias, target and codec assertions.

Fresh whole-repository validation, independent integrated Phase review and
native release closure remain required. This Phase remains in progress.

<!-- sm-workflow completion 95b37fc2053d60c77b044b00a1d871b6 phase 99 -->
Accepted phase 99.
Evidence: 95b37fc2053d60c77b044b00a1d871b6:ordered-validation:124:validation
Evidence: file:///Users/asami/src/dev2026/goldenport-cncf/target/sm-workflow/phase-99-20261027-host/target/sm-workflow/tasks/phase-99-01a10b3f/phase-review-result-02-20261030.json
Local record/commit closure is runtime-owned; later publication, return and synchronization outcomes are recorded separately.
