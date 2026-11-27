# Test classification metadata and legacy tag migration

status=planned
planned_at=2026-11-25
execution_started=false
owner=CNCF

CNCF policy: [classification and default execution](../spec/test-policy.md#scala-test-classification-and-default-execution).
CNCF contract: [test selection and framework bindings](../spec/test-selection-standard-annotations.md).

The planned status above applies to the remaining TCM-01 through TCM-04
migration. CNCF delivered the bounded TCM-05 producer work separately.

## Scope

Record test classifications and exclusion reasons in the existing CNCF
validation metadata. Use the declaration provider in `@ValidationSpecification`
for suite defaults and `@ValidationScenario` for operation-specific declarations.
Keep the ScalaTest annotations and the build responsible for execution behavior.
Migrate CNCF's `ManualSpec`, `TimingSpec` and `ForkSpec` tags within this work.

The [Phase 103 follow-up](phase-103.md#follow-up-standard-annotations-and-admission-only--2026-10-31)
covers standard discovery and selection, but its authoring target allowed only
`ADMISSION` as a custom label. This plan follows the shared classification
direction recorded in the CNCF test policy and carries forward the related
CNCF discovery checks.
Phase 103 remains closed and its accepted results retain their original scope.
The [execution-mode follow-up](test-execution-mode-plan.md) continues to own
mode binding and its consumer integration.

## Work and acceptance

### TCM-01 — Declaration and discovery

- [ ] Define how `HEAVY`, `EXTERNAL_SERVICE`, `MANUAL`, `TIMING` and `FORK`
  are represented and normalized through the existing declaration provider.
  Keep `ADMISSION` as the purpose for short basic acceptance. Reuse the
  existing external-service requirement as the source for actual prerequisites.
- [ ] Specify the smallest producer change for suite defaults and exact case
  declarations. Check ABI, CML mapping and inventory compatibility wherever
  the added metadata crosses those boundaries. Older declarations and
  retained results keep their original meaning.
- [ ] Verify that discovery preserves the declared reasons, framework tags,
  prerequisites, logical references and provenance.

### TCM-02 — CNCF legacy tag migration

- [ ] Add `@DoNotDiscover` to `LogBackendBootstrapReplaySpec` and record
  `MANUAL` and `FORK` in its declaration. Keep both existing cases and their
  bootstrap replay assertions.
- [ ] Keep `@DoNotDiscover` on `JobRetryTimingSpec` and record `TIMING` in
  its declaration. Keep all six real scheduler cases and their timing checks.
- [ ] Use `@Slow` when an independent reason under the common policy requires
  it. Keep `fork = true` for CNCF's existing `test` and `testOnly` routes.
- [ ] Remove CNCF's uses of `ManualSpec`, `TimingSpec` and `ForkSpec` and the
  corresponding legacy entries in the default test filter after accounting
  for actual call sites. Keep the `goldenport-core` tag definitions available
  to other consumers.

### TCM-03 — Default selection and short acceptance

- [ ] Record the current ordinary `sbt test` selection by suite and case before
  the migration and compare it after the change. Preserve the same execution
  membership, including the exclusion of both migrated suites.
- [ ] Confirm both suites still run when named explicitly through `testOnly`,
  with the same process isolation. Verify automatic discovery excludes an
  explicit-only fixture before construction.
- [ ] Verify that the migration preserves ordinary exclusions, including Slow,
  in CNCF's `testAdmission`. Use harmless fixtures to verify exclusion before
  a suite can start a service.

### TCM-04 — Qualification and handoff

- [ ] Record the actual selection comparison, focused discovery and execution
  results, and independent review. Describe the supported consumer contract
  and any required artifact refresh for sm-workflow.
- [ ] Keep label-based invocation design for a separate plan. This work
  preserves the existing typed SBT route, project priorities, active Sessions,
  accepted results, history and repair counts.

### TCM-05 — Typed ADMISSION for sm-workflow Phase 25.2

Delivered by CNCF on 2026-11-26 in commit `9c6d86d6` under
`P252-05-CNCF-TYPED-SBT-SCOPE-01`. The delivery includes ordinary ADMISSION
exclusions and qualification using the published JAR in a harmless workspace.
sm-workflow owns adoption and consumer integration.

- [x] Coordinate the typed `Full`/`Admission` selector for `sbt_test` and
  `sbt_test_attempt` with sm-workflow P252-05. Retain the existing
  single-argument methods and their FULL behavior for existing consumers.
  Prioritize this delivery and its ordinary ADMISSION exclusions from TCM-03.
  The remaining legacy tag migration and classification work can proceed
  separately.
- [x] Use the existing typed SBT/UoW owner and canonical metadata selection.
  Record scope, actual selection, and outcome against the bound workspace.
  Reject empty or unavailable admission with an attributable outcome.
  Confirm that ordinary exclusions apply and that the selected build cannot
  start an external-service test in this route.
- [x] Qualify existing FULL callers and the typed ADMISSION route, then
  provide the locally published compatible artifact and typed result contract
  for the installed sm-workflow check. Preserve prior results at their
  original selection scope.
