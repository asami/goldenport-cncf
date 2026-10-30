# Managed first-installation maintenance follow-up

Date: 2026-10-31

These pre-existing items were identified during the independent reviews of the
managed SQLite opening-race repair and its main integration. They do not block
that work and must be handled separately without changing accepted behavior.

## HYG-FIRST-INSTALL-SPEC-NAMING-01

- Status: OPEN
- Discovered: 2026-10-31, independent focused review.
- Repository: goldenport-cncf.
- Location: `src/test/scala/org/goldenport/cncf/datastore/sql/ManagedEventObservationIsolationSpec.scala`; private helpers including `take`, `env`, `withHost` and `inspectRetained`.
- Category: naming conformance; priority P3.
- Evidence: the declarations already exist in the repair's parent revision and retain private camelCase or unprefixed names.
- Risk: inconsistent specification vocabulary and authoring conventions.
- Boundary: existing helper-name maintenance is outside the bounded connection-opening and fixture-isolation correction; newly authored declarations follow current naming rules.
- Proposed task: rename the private helpers and their same-spec callers, preserving all fixture behavior and assertions.
- Task / commit: not scheduled.

## HYG-FIRST-INSTALL-SPEC-GWT-01

- Status: OPEN
- Discovered: 2026-10-31, independent focused review.
- Repository: goldenport-cncf.
- Location: `src/test/scala/org/goldenport/cncf/datastore/sql/ManagedEventObservationIsolationSpec.scala`; the unchanged scenarios around lines 1605 and 1644 in the reviewed revision.
- Category: executable specification presentation; priority P3.
- Evidence: these scenarios predate the repair and lack explicit Given / When / Then clauses.
- Risk: setup, action and expectation boundaries are harder to read as behavioral documentation.
- Boundary: these unchanged scenarios are outside the first-installation repair; the new ambient-configuration scenario and revised concurrent-installation scenario retain their clauses and expectations.
- Proposed task: add clauses at the existing setup, action and expectation boundaries without changing behavior or expected outcomes.
- Task / commit: not scheduled.

The existing source-size item remains
[HYG-PH697-SQL-SIZE-01](2026-10-01-phase-69.7-hygiene-follow-up.md#hyg-ph697-sql-size-01--bounded-fix-and-inherited-debt).
No duplicate item or provider decomposition is introduced here.

## HYG-SYNC-SBT-ESTIMATE-NAMING-01

- Status: OPEN
- Discovered: 2026-10-31, independent integration review.
- Repository / owner: goldenport-cncf; the existing SBT contract or Phase 99 maintainer.
- Category: Naming conformance; priority P3.
- Location: `src/main/scala/org/goldenport/cncf/processexecution/SbtValidationEstimate.scala:84` and `src/test/scala/org/goldenport/cncf/processexecution/SbtValidationEstimateSpec.scala:12`.
- Evidence: The incoming parent retains private text/invalid helpers and unprefixed private specification fields.
- Risk: Inconsistent declaration conventions make maintenance less predictable.
- Boundary: inherited from the incoming parent, outside the limited SQLite repair; no implementation or historical Phase-status change is admitted by this record.
- Proposed task: Rename private declarations and callers without changing public fields or behavior.
- Task / commit: not scheduled.

## HYG-SYNC-SBT-ESTIMATE-GWT-01

- Status: OPEN
- Discovered: 2026-10-31, independent integration review.
- Repository / owner: goldenport-cncf; the existing SBT contract or Phase 99 maintainer.
- Category: Executable specification presentation; priority P3.
- Location: `src/test/scala/org/goldenport/cncf/processexecution/SbtValidationEstimateSpec.scala:21` and `src/test/scala/org/goldenport/cncf/action/SbtExecutionDslSpec.scala:106`.
- Evidence: Five estimate scenarios and the inventory-mismatch DSL scenario in the incoming parent lack semantic Given / When / Then clauses.
- Risk: The documented setup/action/expectation boundaries are less explicit.
- Boundary: inherited from the incoming parent, outside the limited SQLite repair; no implementation or historical Phase-status change is admitted by this record.
- Proposed task: Add clauses at existing semantic boundaries while preserving execution and expectations.
- Task / commit: not scheduled.

## HYG-SYNC-SBT-CONTRACT-PROSE-01

- Status: OPEN
- Discovered: 2026-10-31, independent integration review.
- Repository / owner: goldenport-cncf; the existing SBT contract or Phase 99 maintainer.
- Category: Specification overview consistency; priority P3.
- Location: `docs/spec/typed-sbt-execution.md:104`.
- Evidence: The incoming overview enumerates four operations and extractor arity two while the existing contract has additional operations and a three-field SbtTarget.
- Risk: Readers may infer an obsolete operation inventory or extractor shape.
- Boundary: inherited from the incoming parent, outside the limited SQLite repair; no implementation or historical Phase-status change is admitted by this record.
- Proposed task: Reconcile the overview with the existing public contract without changing its semantics.
- Task / commit: not scheduled.

## HYG-SYNC-PH99-METADATA-01

- Status: OPEN
- Discovered: 2026-10-31, independent integration review.
- Repository / owner: goldenport-cncf; the existing SBT contract or Phase 99 maintainer.
- Category: Historical Phase status projection; priority P3.
- Location: `docs/phase/phase-99.md:3`, `docs/phase/phase-99.md:86` and `docs/phase/phase-99-checklist.md:29`.
- Evidence: Inherited in-progress and closure-required text coexists with the appended native acceptance marker.
- Risk: Historical metadata gives conflicting progress projections. The current repair and synchronization do not depend on Phase 99 being closed.
- Boundary: inherited from the incoming parent, outside the limited SQLite repair; no implementation or historical Phase-status change is admitted by this record.
- Proposed task: The Phase 99 owner should reconcile metadata against native closure evidence when separately selected; this integration neither closes nor reopens Phase 99.
- Task / commit: not scheduled.
