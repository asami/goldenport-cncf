# Phase 96 final validation repair

Date: 2026-10-17
Scope: PHASE-96 / P96-11B / final-full validation repairs 1 and 2.
Status: focused repair accepted; successful final full validation recorded;
distinct local release/committed closure bound by `phase96-final-closure-20261017-001@1`.

## Current repair acceptance — 2026-10-17

The affected two-suite run had 46 passes/1 failure. The remaining enabled-form
fixture was corrected without changing product policy, HTTP 403, local action
count 1 or validation-failure count 2. The complete TypedUpdate suite then
passed 4/4 tests, with zero other outcomes, SBT0/wrapper0 and lock released.
Fresh independent focused review `phase96-final-repair-focused-review-002@1`
passed with zero blockers. Its predecessor FINDINGS remains below; two repairs
converged with nonconverging 0, without another comprehensive Phase review.

The subsequent actual full run completed **638 suites / 5,670 succeeded / 0 failed / 0 aborted; 5 canceled, 1 ignored, 46 pending**,
including all seventeen mandatory native PostgreSQL suites/424 cases.
Its SBT0/wrapper0 and lock release were independently admitted by the parent.
See the [final acceptance journal](2026-10-17-phase-96-final-acceptance.md).
Release/Goal completion still requires the actual distinct committed closure
receipt, not this authored record. No additional publication occurred.

The remaining sections preserve earlier authored/pending checkpoints literally;
their pending language is historical and superseded by this current record.

## Retained validation history

The original repository-full validation ran 638 suites and 5,670 tests: 5,647
succeeded and 23 failed, with 5 canceled, 1 ignored, 46 pending, and no aborted
suites. Its actual PostgreSQL 17 positive evidence remains historical evidence;
the final full validation must still exercise actual PostgreSQL.

The 23 failures had four diagnosed causes:

- One Job experience HTTP assertion expected 503 but received 500 because the
  consumed local core SNAPSHOT lacked the accepted 503 mapping.
- Twenty controlled startup-import failures (10 entity and 10 data) came from
  the forked test JVM lacking the existing `-Dtextus.test=true` test profile.
- One legacy audit-viewer assertion expected production Operator jobs access
  despite lacking the required `job_admin` or `content_admin` capability.
- One typed-update scenario treated bounded process-global metric totals as
  strictly increasing request-local evidence after their 10,000-event cap.

The single approved core `publishLocal` refresh completed. The subsequent
focused run with `Test / fork := true` and `-Dtextus.test=true` passed all 41
tests in `JobExperienceHttpSpec`, `StartupEntityImportSpec`, and
`StartupDataImportSpec`. This current console result addresses 21 of the 23
original failures. Old full-run JUnit XML remains historical and does not
represent that later focused result. No further local publication is selected.

The original full Phase review passed once. This repair retains that count of
one and requires focused closure review, not another full Phase review.

## Authored oracle repairs

- `CPB-P96-FINAL-OPERATOR-AUDIT-ASSERTION-001`: the real production audit-viewer
  scenario now expects concealed Operator jobs denial (404), while retaining
  system admin home and performance denial (403 each). Session, request paths,
  and product authorization policy remain unchanged. The existing positive
  `content_admin` H1 counterpart remains covered by the retained 41-test run.
- `CPB-P96-FINAL-BOUNDED-METRIC-ORACLE-001`: the real typed-update operation now
  observes actual `ActionCall.execute` calls and structured request-validation
  failures locally. The sequential Form clear, REST null, and REST clear
  requests retain HTTP 400 / 200 / 400 and `updated:SetNull`, with exact action
  counts 0 / 1 / 1 and validation-failure counts 1 / 1 / 2. A descriptor-denied
  Form clear request expects 403 with both counts unchanged, specifying
  authorization before normalization. Production metrics remain unchanged.

Both selected scenarios place Given / When / Then beside setup, actual HTTP
actions, and expectations. Their Scala version headers record the October 17
source edit while preserving the September 28 creation date.

## Historical pending parent gates

- Actual diff admission and required source conformance checks: pending.
- Focused validation of the two affected suites, with compilation and the
  existing forked test JVM profile: pending.
- Fresh independent focused closure review: pending.
- Complete final CNCF repository-full validation with actual PostgreSQL:
  pending.
- Acceptance commit, release ledger, and Phase closure: pending.

Authored assertions are regression evidence to be executed, not a test PASS or
an acceptance/closure result. The original Goal, base, PLAN epoch, full-review
count, and reserved convergence accounting remain owned by the parent.

## Existing Debt (Separate Follow-up)

Inherited unused-import boilerplate and generic or late Given / When / Then
clauses in unrelated historical scenarios remain outside this frozen repair.
They are not repaired or admitted as current blockers here.

## Repair 2 follow-up

The actual focused run of the two affected suites completed 47 tests: 46
succeeded and one failed. The production audit-viewer denial assertion and
the original bounded-metric oracle defect were resolved. The typed-update
sequence passed its HTTP 400 / 200 / 400, `updated:SetNull`, action-count
0 / 1 / 1, and validation-failure-count 1 / 1 / 2 expectations. Its subsequent
denied Form API request returned 404 instead of the required 403, so the
remaining unchanged-count expectations were not reached.

The first independent focused review returned FINDINGS and admitted
`CPB-P96-FINAL-FORM-DENIAL-FIXTURE-001`: adding the deny authorization rule
activated descriptor controls while the selected form remained disabled by
its default internal exposure. The existing HTTP boundary checks form
enablement before authorization.

Repair 2 now explicitly enables only the same observed operation form through
the existing descriptor contract. It copies the existing form (or its default)
and changes only `enabled`, preserving the other form settings and explicit
deny rule. The real denied POST still expects 403 with action count 1 and
validation-failure count 2; no product policy or original oracle is changed.

This follow-up records authored repair, not successful execution. Focused
validation of the complete `StaticFormAppRendererTypedUpdateSpec` with the
existing forked JVM profile, a fresh independent focused closure review,
complete final CNCF repository-full validation with actual PostgreSQL, and
release/Phase closure remain pending. The earlier 41-test result, original
failure history, and sole full Phase review remain historical evidence.
