# Hygiene follow-up

## HYG-INFRA-CLIENT-NAMING

- Status: OPEN
- Discovered: 2026-10-25, focused review before repository synchronization.
- Location: `src/test/scala/org/simplemodeling/cncfclient/WorkflowMechanicalTransitionSpec.scala`.
- Category / priority: inherited private-helper naming / low.
- Evidence: pre-existing helpers such as `take`, `resource`, `nextEntry`,
  `host`, `codec`, `factory`, and `rows` do not use the underscore convention.
- Risk: mixed naming obscures helper visibility; no current behavior defect.
- Boundary: independent naming maintenance, outside the admitted finite
  completion repair. Preserve public client APIs and behavior.
- Proposed task: a separately selected private-helper naming pass.
- Task / commit: not scheduled.

## HYG-INFRA-SOURCE-SIZE

- Status: OPEN
- Discovered: 2026-10-25, focused review before repository synchronization.
- Locations: `SqlDataStore`, `Subsystem`, `UnitOfWorkInterpreter`,
  `ProcessExecutionModel`, `SbtExecutionDslSpec`, `GitExecutionSpec`, and
  `LocalProcessExecutionDriverSpec` in their existing Scala source/spec paths.
- Category / priority: inherited file size and responsibility concentration / low.
- Evidence: the reviewed files already span approximately 1,100–3,600 lines.
- Risk: increased review and maintenance cost, without a new correctness finding.
- Boundary: refactoring would broaden the current execution/history/completion
  changes and requires its own responsibility assessment.
- Proposed task: assess cohesive responsibilities before selecting any split.
- Task / commit: not scheduled.
