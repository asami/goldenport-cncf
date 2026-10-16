# Validation/Test Metadata ABI producer decision

Date: 2026-10-06
Status: design decision

sm-workflow Test Suite design and cbd-support static-review needs converged on one common requirement: executable specifications/test programs need portable metadata describing validation purpose, feature coverage, expected duration and execution requirements.

The decision is to define this semantic contract in CNCF rather than independently in sm-workflow or cbd-support.

The source of truth is the executable specification/test operation itself. Hand-written Scala uses CNCF annotations; CML uses Cozy-owned properties mapped to the same normalized model; generated Scala is projection when needed. This follows the existing Phase 99 Finding Disposition ABI pattern.

Operation/scenario metadata is the primary execution granularity, while class/specification metadata supplies defaults/shared features. The same operation can participate in several purposes such as SMOKE, ADMISSION and FULL.

The initial common purposes are SMOKE, FOCUSED, ADMISSION, FULL and HEAVY. They are explicit memberships, not a numeric inclusion hierarchy. Consumer policies remain downstream: the one-minute ADMISSION and ten-minute FULL targets belong to sm-workflow, while Test Architecture lint/KPI belongs to cbd-support.

This removes the need for a separate managed TestSuite definition file as the normal source of truth. sm-workflow Test Suites become deterministic queries over CNCF-resolved metadata. cbd-support analyzes the same metadata statically and can detect missing coverage, purpose/feature imbalance and coverage-reducing metadata changes.

CNCF should provide deterministic resolved-metadata interpretation/discovery so consumers do not each parse Scala annotation source or CML syntax independently.


## Phase allocation

Created [Phase 103](../../../phase/phase-103.md) and its
[checklist](../../../phase/phase-103-checklist.md) after confirming that GitHub
Phase numbers through 102 were already allocated. The proposal/journal were
imported from CNCF commit `b49c423e53599e29764739bd887ebe90c6e0027c`.

sm-workflow Phase 5 drives the producer ABI/discovery work and its runtime
consumer; Phase 6 adds semantic skill integration and Phase 7 uses the same
interface across repositories/overlays. Existing Phase 101 resource dependencies
remain separate. Phase 99 is the representation-pattern reference, not an excuse
to broaden this phase into Finding Disposition implementation.

textus-cbd-support implementation is assigned to Mac mini. The initial planning scope did not include editing that project; the subsequent
user instruction expanded document coordination to include it. Implementation
has not started here. Provide shared ABI/fixtures for its coverage,
ADMISSION-gap, FULL/HEAVY-bias, feature-architecture, metadata-delta and runtime
observation/KPI comparison work. Its application completion is not a blocker for
CNCF producer acceptance or initial sm-workflow runtime use.

No code, live Goal, ongoing phase status or release action was changed here.


## Mac mini consumer feedback integrated

The user then requested local coordination of cbd-support documents as well.
Its new Phase 14 owns Test Architecture visualization and change review, with
implementation still on Mac mini. Phase 103 now includes explicit feature mapping
and target-population boundaries, discovery scope/completeness and unannotated
operations, operation/source revisions, and duration units/measurement scope.
These support a shared ADMISSION membership 1 -> 0 fixture without inventing
runtime verification or percentages from incomplete inventories.

sm-workflow supplies separate attributable runtime measurements; cbd-support
compares compatible cohorts and preserves Unknown/sample limitations. Producer
metadata does not absorb observations or either consumer's policy. No cross-chat
message, Mac mini deployment or implementation is implied by the document update.


## Clarification: retain the management file as a Slice validation plan

The user corrected the blanket withdrawal of the management-file idea. Retain
it with a distinct responsibility: CNCF metadata declares test purpose/feature/
execution requirements; the Slice management file defines acceptance conditions,
metadata queries, supplemental Test Operation references and typed parameters;
execution Evidence records actual selected/executed inputs and results. The plan
must not copy metadata into a second declaration authority.

Normally select FOCUSED plus feature and supplement gaps with explicit refs,
without forcing one-off source metadata edits. Plan changes use normal admission;
execution requirements and final validation obligations remain. Evidence binds
plan revision and actual invocation parameters, deduplicating identical invocations
without collapsing intentionally different parameter cases. CBD Support separates
declared, planned and executed coverage and their deltas. This supersedes earlier
wording that could be read as prohibiting all Slice management files.
