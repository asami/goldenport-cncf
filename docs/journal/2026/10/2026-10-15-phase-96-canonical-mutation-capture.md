# Phase 96 canonical create/delete capture authoring

2026-10-15. This historical note records the P96-07C2B2 capture boundary and
fixture provenance. It is not a specification, validation report or acceptance
record. The [specification](../../../spec/entity-mutation-capture-outcome.md)
and [design](../../../design/entity-mutation-capture-outcome.md) own the settled
behavior and responsibility decisions.

The existing canonical create and observed-delete paths can acknowledge durable
work before resident coherence fails. The internal carriers and opt-in UoW
nodes preserve those actual results before optional View/resident work. Existing
writers, defaults, SQL NULL omission, legacy returns and Aggregate compensation
remain with their original owners.

Fixture provenance follows the registered Component/descriptor/Realm pattern in
EntityObservedDeleteCanonicalFixture, strict-create checkpoint stores in
EntityCreateFailureSafetyFixture, and native resources/acknowledgement stores in
PostgresqlPersistenceFixture. New observation delegates call through native and
Space APIs without supplying results. The identity variation point returns a
known test-owned ID. Decoder/Realm faults are armed after actual acknowledgements.

During initial authoring, two documentary oracle discrepancies were returned to
the parent before test expectations were written. The existing overflow writer
uses content_byte_size/content_digest/content_charset. Strict-create SQL uses
enum print strings published/alive, while conditional logical delete converts
states to numeric values. The parent settled the complete independent oracle,
including explicit TEXT/integral SQL schemas, text 3 after SQL deletion, physical
permission JSON versus logical nested Record, guard spelling and read aliases.
No production writer/default/decoder/schema change was used to reconcile them.

The original base remains 6cbe877c53c116639feb11fa80546e79c051804c on the dedicated
Phase 96 branch. Prior atomic-create, native PostgreSQL, SQL NULL and versioned
outcome/producer-port work remains prerequisite history. The original main
checkout and unrelated edits remain outside this boundary.

The executable counterparts are EntityMutationCaptureOutcomeSpec,
UnitOfWorkMutationCaptureSpec and PostgresqlUnitOfWorkMutationCaptureAcceptanceSpec,
with UnitOfWorkMutationCaptureFixture. Authoring does not establish execution
success. Ordinary Entity/Aggregate/Display routes, whole-commit handling,
contextual Detail/List reload, C3 and PG12 remain original P96-07 obligations.
All four P96-07 criteria and the original following Step, Phase, release and
reciprocal aggregate gates remain OPEN.

The first representative13 validation failed during Test compilation before any
suites executed: the context wildcard import shadowed the unitofwork
TransactionContext required by RecordingStore's prepare, commit and abort
delegations (CB-P96-C2B2-TX-TYPE-001). The three lifecycle parameter types are now
explicitly qualified as org.goldenport.cncf.unitofwork.TransactionContext.
Revalidation and independent assurance remain pending; this authored correction
does not establish execution success or Step/Phase acceptance.

The second representative13 validation (representative002) resolved the original
three TransactionContext diagnostics but failed during Test compilation with
E058 at UnitOfWorkMutationCaptureFixture.scala:514:8 before any suites or tests
executed. Vocabulary mixes in GivenWhenThen and requires the org.scalatest.Informing
self type (CB-P96-C2B2-INFORMING-001). The prescribed self-type annotation is now
authored following the existing outcome/port fixture pattern, preserving the
previous TransactionContext correction. Revalidation and fresh independent
assurance remain pending; this correction does not establish execution success
or Step/Phase acceptance.

The third representative13 validation (representative003) passed main and Test
compilation and ran 13 suites / 491 tests: 467 passed and 24 failed, with none
skipped or pending. The parent identified three deterministic fixture premises:
the execution context allocated observability independently of the Runtime
scope (CB-P96-C2B2-CONTEXT-FIXTURE-001); duplicate candidates selected the old
typed identity while retaining the fresh raw Record.id
(CB-P96-C2B2-DUPLICATE-FIXTURE-001); and a separately constructed overflow
CollectionId did not equal the canonical policy's typed fault address
(CB-P96-C2B2-FAULT-ADDRESS-001).

The authored correction binds the predeclared observability into the immutable
execution context, supplies the selected old identity in both typed and raw
duplicate inputs before interpretation, and binds only the injection address
from the existing pure versioned-save plan. The address is checked against the
independently prescribed body collection name and entry print. No plan record,
field order, metadata or returned result supplies expected state; every complete
logical/physical oracle, counter and property criterion remains prescribed
independently. The prior TransactionContext and Informing corrections remain.
The source analysis identifies a universal context mismatch at the first full
created-state check; no executed PBT counterexample is claimed as causal proof.

Fresh representative13 validation, the selected 80-suite validation and fresh
independent assurance across the original 12-path boundary remain pending.
These fixture corrections establish neither a product defect nor successful
validation, independent review or Step/Phase acceptance. The original 54/33
history, four OPEN P96-07 criteria and whole-Phase obligations remain unchanged.

The fourth representative13 validation (representative004) ran 13 suites / 491
tests: 487 passed and four failed, with zero aborts, cancellations, ignored or
pending tests. Candidate 3 therefore narrowed the prior 24 failures to four.
The remaining registered NullFailure decoder cases expected Reason
`entity-mutation-coherence-invalid` and received no Reason
(CB-P96-C2B2-NULL-FAILURE-COHERENCE-001). The parent established that decoder
`Failure(null)` survives `decoded.map`, then legacy `recoverWith` calls
`_is_not_implemented(null)`. Its null dereference is wrapped by
`Consequence.Failure.recoverWith` through `Consequence.run` as a new nonnull
Failure, losing the malformed boundary before outer coherence capture.

The authored correction threads private default-false `capturecoherence` through
the persisted-record and shared put helpers. Only the new captured-create
callback opts in. Its mapped `Failure(null)` returns unchanged before the legacy
recovery clauses, allowing existing outer coherence capture to redact it. All
other results and old callers retain the existing recovery and NotImplemented
behavior; the real decoder regression cases and all assertions remain unchanged.
The interpreter's same-month version history is compressed into its current
October version entry under the shared header rule.

Fresh representative13 validation, then the selected 80-suite validation and
fresh independent high-assurance review across the original 12 paths remain
pending. This editing-complete, unvalidated correction establishes no acceptance.
The previous failure history, all four OPEN P96-07 criteria and the original
Step, Phase, release and reciprocal aggregate obligations remain unchanged.
