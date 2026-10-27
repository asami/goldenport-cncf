# Development Runtime Source and Artifact Lineage

Role: integration structure and repository responsibilities.
@since Oct. 26, 2026. @version Oct. 26, 2026.

The authoritative source, publication and adoption contract is the
[development runtime lineage specification](../spec/development-runtime-lineage.md).
The existing [observation specification](../spec/operational-observation-read-model.md)
and [observation design](operational-observation-read-model.md) own the accepted
read feature. This document records the implementation mapping selected by
native Human instruction `01a174f7-e34b-7673-af87-80227d8535eb`.

## Canonical integration

Canonical CNCF at `7b59ac431adba296d655bc89cebd1b518cb93598` supplies the current
Git changes and receives the missing accepted Phase 5.1 behavior. The already
reviewed 30-path observation port is layered on that source, rather than the
older private package based only on `e77a4e0f6b691c57ec8daa9ac9935b1f03af2894`.
The port is independent of the checkpoint Git V5 plan and codec; neither Git
file is replaced. The old private CNCF source remains historical read-only
evidence under specification R1–R4.

## Complete correlated stored rows

The five approved C69 files supply the existing
`EventHistoryCorrelation.storedRowC` and `admitStoredRowC` behavior, including
V1 and V2 decoding. `ManagedEventPreparation` creates complete metadata-bearing
records through `EventRecordJsonV2.createSideEffectC` and reconstructs those same
complete rows. Preparation and observation readers validate the complete
stored row before operating. This mapping preserves metadata and original
envelope bytes without a storage schema change, envelope re-encoding or a
second validation subsystem.

The existing regression specifications cover retained correlated duplicates,
new facts, physical host reopen and rejection of altered frozen metadata.
They accompany the approved source import; their execution remains pending.

## Publication and runtime qualification responsibilities

Canonical CNCF owns the integrated source and its normal local publication.
The qualified HOST32 SM source remains separate from live evolving SM and is
consumed through normal project publication with the new CNCF input and a fresh
attempt and authority. This is source qualification; the parent has not thereby
consumed or adopted the runtime. Specification R2–R6 govern those distinctions.

The parent-selected evidence sequence comprises:

1. Existing owner 32-contract CNCF suites and the current memory and default-Git
   regressions, followed by independent source review.
2. Normal canonical CNCF `publishLocal`, then normal SM publication.
3. Association of the complete actual installed closure, including its selected
   JARs, classpath, CAR and runtime manifest.
4. Four SM suites and two actual CLI cases, including effective 4 GiB and 6 GiB
   heap observations, followed by independent delivery review.

These are pending parent responsibilities, not results of this source edit.
Ordinary artifact association may describe bytes for diagnosis; checksums are
not introduced as control authority.

## Remaining owner adoption boundary

The parent's primary product and CAR selection and the three original dependency
aliases still require explicit owner-side public revision and adoption. Report
that gap directly; host grants or configuration must not be mutated to bypass
it, and the 32-suite qualification must not be represented as accepted primary
validation. The same Session and original base remain untouched under
specification R5–R7.
