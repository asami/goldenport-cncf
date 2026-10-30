# Managed first-installation maintenance follow-up

Date: 2026-10-31

These pre-existing items were identified during the independent focused review
of the managed SQLite opening-race repair. They do not block that repair and
must be handled separately without changing its initialization contract.

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
