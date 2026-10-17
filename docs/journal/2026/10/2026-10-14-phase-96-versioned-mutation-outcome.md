# Phase 96 versioned mutation outcome authoring

Date: 2026-10-14. Scope: PHASE-96 / P96-07 / P96-07C2A.
This entry records authoring history, fixture samples and provenance. It is
not a test, review or acceptance receipt.

## Pre-edit source admission correction

The predecessor stopped before editing when its MissingBody failure assumption
did not match existing source. `ContentBodyStoragePolicy.hydrate` returns the
unchanged raw Record when no overflow body is found, and the existing
Aggregate Root decoder accepts an identity-valid raw Record. The corrected
manifest002 preserves that successful fallback and introduces no corruption
or body-absence error policy.

Actual factory-owned SQL closure supplies the separate hydration-failure
scenario: SqlDataStore reads borrow its managed resource, whose closed state
rejects subsequent borrowing with structured Failure. SQLite factory stores
and native `database.open(normalizeColumns=true)` own their resources. Each
scenario retains its original context and a separate live reader. The
predecessor and workflow-control receipts remain local and outside Git.

## Authored kernel and fixture

The [specification](../../../spec/entity-versioned-mutation-outcome.md) and
[design](../../../design/entity-versioned-mutation-outcome.md) describe the
settled internal raw-outcome/optional-projection compatibility boundary.
The carrier captures supplied raw data and actual branch before hydration;
legacy helpers retain direct expression evaluation while new helpers contain
optional projection faults.

The [fixture](../../../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationOutcomeFixture.scala)
reuses AggregateObservedSnapshotFixture's lawful IDs, Root, canonical setup and
MemoryStore, plus owned SQLite factories and the existing required native
PostgreSQL fixture. Samples include `日本 Alpha Ω`, retained audit/security,
explicit NULL, full overflow bodies, unrelated rows, varied positive revision,
changed/current/stale/failure modes and maximum current revision. Shrinking
reduces nonempty text, positive revision or mode complexity.

The [portable spec](../../../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationOutcomeSpec.scala)
and [native spec](../../../../src/test/scala/org/goldenport/cncf/entity/PostgresqlEntityVersionedMutationOutcomeAcceptanceSpec.scala)
were authored with full nullable Field and independent physical observations.
They distinguish actual postprovider body absence, real managed SQL closure,
pure typed domain probes, negative metadata/callback admission and real
commit acknowledgment faults. No SBT, project runtime, native database or
validation execution was performed by the implementation editor.

## Remaining workflow obligations

Parent diff verification, focused eight-suite and cumulative 75-suite execution,
fresh independent observation and all original Step/Phase/release gates remain
required. Original C2B/C3 create/delete/ordinary Operations/strict raw HTTP,
commit/contextual reload, PG12 and all four P96-07 criteria remain mandatory
and OPEN. Existing prior work, Phase/checklist and separate Hygiene records
were preserve-only during this authoring batch. No acceptance or closure is
claimed by these source artifacts.

## Decoded body-key and native reader correspondence correction

The native source disproved the fixture's snake-only decoded-key assumption:
regular normalized SQL readers expose camel body/audit keys, while the
unchanged acknowledgment writer retains its default false convention. The
bounded correction reads only the explicit existing snake/camel storage,
reference and charset spellings with first-nonempty snake precedence. Canonical
writes, payload whitespace, metadata replacement, effects, addresses and the
raw outcome remain unchanged. Only the ACK scenario's independent reader now
matches its writer's default convention; regular native pools still normalize.

The body specification adds real-memory samples using nondefault exact body
references, padded Japanese/Greek text, ordered NULL/security/audit Fields,
absent exact references, omitted-content preservation, padded UTF-16BE sizing
at both threshold boundaries and snake precedence/camel fallback. Existing
missing/NULL/SetNull/whitespace scenarios and all three body property laws
remain. Changed audit observation selects the unique prior snake/camel keys.
Full Field and physical comparisons retain their original predicates with
safe address/column/count/index/key clues, and the outcome property checks
retain their existing passed/at-least-100/zero-discard gates with result clues.
Unknown physical NoOp differences receive no speculative production change.

This is authoring of a source-disproved decoded-key correction, not acceptance
or closure. Current parent gates are representative nine suites (the original
eight plus ContentBodyStoragePolicySpec), the unchanged 75-suite accumulator
and a fresh cumulative ten-path independent observation. Earlier authoring
history and all original Step/Phase/release obligations remain. Actual logs,
results, reports and failed-run payloads remain in private existing storage;
this journal adds no execution receipt or repository evidence appendix.

## Closed managed-read failure oracle

The real closed-writer scenarios now require the existing managed-read cause
directly for NoOp and in the Applied Conclusion's previous failure. The
expectations prescribe System/Corrupted, exact IllegalStateException and the
fixed managed-resource-closed message before comparing the complete canonical
Conclusion built from that already checked exception. This preserves fresh
timestamp handling without comparing independent Throwable identities, while
requiring the complete committed wrapper and its original predecessor for
Applied. Existing raw, branch, legacy, full-state and no-replay expectations
remain alongside this stronger cause oracle. This records authoring rationale
and supplies no validation or acceptance claim.
