# Phase 105: Workflow Validation Hygiene

status=planned
planned_at=2026-10-12
current_status=OPEN
work_class=hygiene
consumer=sm-workflow Phase 5.2
checklist=[Phase 105 Checklist](phase-105-checklist.md)

## Goal

Remove unnecessary acceptance restrictions, repeated checks and unchanged-copy
certification from the existing Workflow execution paths. Preserve the public
interfaces so CNCF and sm-workflow can develop in parallel.

This is hygiene of existing functionality, not feature development. Remove
unnecessary checks and correct false rejections within the existing Usage
Contract; do not introduce new capabilities, Operations, public APIs, storage
formats or recovery guarantees. Any discovered missing feature is recorded as
a separate Development Candidate and is not added to this Phase's acceptance.

The existing route receives a work or decision result through the existing
public Operation, applies the next Workflow state and continuation completion
through existing storage, and returns the actual committed outcome. This is
completion of one issued interaction, not automatic completion of a whole Phase.

## Fixed interface and responsibility boundary

- Preserve public Operation names, parameters, request/response contracts and
  public Scala method signatures. Relaxing an unnecessary rejection is intended;
  requiring a new caller field or changing the result shape is not.
- Keep `WorkflowCompletionServiceOperation` and
  `WorkflowDecisionCompletionServiceOperation` as the completion entry points.
  Keep `WorkflowManagedCompletion.completeC` and
  `WorkflowManagedDecisionCompletion.completeC` non-public. Do not require an
  application-owned replacement completion Action or fake Provided metadata.
- Remove the unnecessary equality restriction between the public input carrier
  and the semantic result already produced by its codec. No new conversion
  facility is added. Their type names need not be identical. Actual decoding, consumed
  result semantics and required operation routing still apply.
- CNCF retains completion sequencing, storage transactions, local effects,
  resource lifecycle and post-commit event/response handling. sm-workflow owns
  its business result interpretation and next-work decisions.
- Preserve the current storage representation. A public API or persisted-schema
  redesign discovered during implementation is a separate design issue, not an
  implicit prerequisite or an incidental change in this Phase.

## Hygiene items

| ID | Outcome | Status |
| --- | --- | --- |
| WCS-01 | Correct completion binding and consolidate issued-input/result admission on the existing public WorkOrder and Decision routes. | OPEN |
| WCS-02 | Consolidate instance/history checks at loading and transition boundaries; remove repeated certification of the same internal data. | OPEN |
| WCS-03 | Remove post-commit comparisons against planned/copy data from completion and event paths; consume actual stored outcomes. | OPEN |
| WCS-04 | Remove diagnostic provenance gates in related public/generated Workflow paths and resolve unnecessary closed-field restrictions. | OPEN |
| WCS-05 | Validate the connected producer/consumer route, preserve interfaces and complete independent review and consumer handoff. | OPEN |

These are outcomes in one connected implementation, not a test/review/commit
cycle for each helper. The checklist owns acceptance; this plan claims no
implementation completion.

## Bounded investigation inventory

The 2026-10-12 source investigation identified the following targets. It included
uncommitted development changes and did not execute tests. Reconcile the actual
implementation tree before editing; findings are not evidence of runtime failure.

| Item | Source entry points | Correction boundary |
| --- | --- | --- |
| WCS-01 | `WorkflowCompletionServiceOperation.bindC`, `WorkflowDecisionCompletionServiceOperation.bindC` | Remove public-input/semantic-result type equality. Keep real codec consumption and operation selection. |
| WCS-01 | Both completion Operations, `WorkflowCompletionDataStorePersistence.selectCompletionC`, sm-workflow native completion owners | Identify repeated issued-work reads and result admission; assign each necessary condition to its consuming boundary. Do not indiscriminately remove reads needed for current storage state or input access. |
| WCS-02 | `WorkflowInstancePersistence.InstanceRecord.appendC` / `validateC`, `WorkflowInstanceRecordJsonV1`, completion selection | Avoid repeatedly walking unchanged whole histories after loading, in selectors, before/after append and during encoding. Keep malformed stored-data detection and the actual new transition checks. |
| WCS-03 | `ManagedEventDuplicateClassifier.Classification.finalizedReadbackC` via `DataStoreSpace.conditionalHostEventsFinalizedC` | Remove exact stored-event-string comparison to retained expected bytes. Decode and consume the actual committed event for delivery; preserve target/order semantics and actual event duplicate policy. This path is relevant when durable events are selected. |
| WCS-03 | Non-managed branch of `WorkflowCompletionServiceOperation.afterCommitResponseC` | Remove planned-versus-persisted revision/lifecycle/progression/suspension recertification after append. |
| WCS-03 | `WorkflowSelectedEventRecords`, `WorkflowCursorEventProposal`, `WorkflowPreparedDataStoreTransition.withEventRecordsC` | Resolve remaining event/index/cursor proposal-copy comparisons. These supporting paths were not established as current sm-workflow entry paths; trace their actual callers before choosing simplification or retirement. |
| WCS-04 | `StateMachineProvidedApiServiceOperation.bindC`, `GeneratedProvidedApiAbi` | Remove generator-name and whole-descriptor/source-position gates; retain actual declaration resolution. Align the public binder with the existing ABI admission semantics. |
| WCS-04 | `GeneratedWorkflowActionProgram` | Remove source-line existence/positivity/equality as execution conditions. Preserve executable action references, ordering and effect semantics. Its current direct use by sm-workflow was not established. |
| WCS-04 | `WorkflowMechanicalTransitionDataStorePersistence`, mechanical receipt codec and shared field readers | Decide and remove extra-field-only rejection where the field is unused. Preserve required fields, interpretable values, actual owner/reference and schema-version interpretation. Record any retained restriction's concrete consumer requirement. |

No TTL-based refusal was found in the inspected Workflow package. Remaining
hash uses included filename addressing and generated-artifact information;
their presence alone is not evidence of tamper detection. Do not turn this
Phase into an ecosystem-wide hash removal or address/schema migration.

## Simplification rules

Reject contradictions where input or stored data is consumed. Do not repeatedly
prove that an internal object, generated artifact or committed result is
unchanged. Do not replace removed checks with hashes, full-copy comparisons,
verification stamps, internal TTLs, check registries or another approval ledger.

Preserve actual input access/permissions, valid state transitions, required
facts/evidence, storage atomicity and existing storage concurrency mechanisms.
These labels do not exempt a redundant comparison from review. A missing or
unreadable committed result still reports the real failure; removing copy checks
must not fabricate success or retry effects after an uncertain commit.

This Phase does not add competing-writer protocols, stronger replay/recovery
guarantees, new workflow engines, or generic transaction redesign. It does not
reopen the entire scope of Phases 77/88/94. [Phase 76](phase-76.md) remains the
wider hash-responsibility review; [Phase 104](phase-104.md) owns external CAR
download verification. Neither is a prerequisite for this bounded correction.

## Parallel development and integration

CNCF owns WCS-01 through WCS-04. The existing sm-workflow development task owns
its Phase 5.2 minimal-input migration and business behavior. Both proceed on
their respective dedicated branches/worktrees; this plan does not pause,
resume, replace or reset either task's Goal, base, epoch, reviews or fix history.

sm-workflow can implement against the unchanged public completion interfaces
while CNCF removes the restrictions. Its affected integration acceptance waits
for a usable corrected CNCF artifact; unrelated work need not wait. The previous
proposal to expose `completeC` is not the integration contract for this Phase.

At integration, explicitly select the CNCF checkout/development artifact used by
the consumer and record that selection with the command/result evidence. Use
the available local dependency-selection mechanism; do not overwrite another
parallel task's selected artifact or silently use an unrelated shared SNAPSHOT.
This does not require a new artifact-overlay implementation or completion of
sm-workflow Phase 7. Preserve the established SBT runner/serialization policy.

## Implementation and acceptance sequence

1. Trace the public completion-to-storage route and agree the unchanged consumer
   interface. Resolve the bounded inventory against existing uncommitted work.
2. Implement the connected simplification and Executable Specifications as one
   batch. Verify the existing result-to-committed-state route first, then cover Decision,
   durable-event and the listed supporting paths. Update affected specs with code.
3. Run the affected producer specifications and meaningful regressions together.
   Through the existing sm-workflow public route, demonstrate minimal result
   input, semantic decoding, next state, saved completion and acknowledgement.
   Include a durable-event fixture because current consumers may select no events.
   Consumer validation exercises existing behavior; do not implement unfinished
   sm-workflow features to satisfy this hygiene Phase. If consumer development is
   not ready, record the integration limitation and coordinate its later check.
4. Review the integrated change independently, resolve findings at their actual
   scope, and hand off the selected artifact and actual evidence. Do not repeat
   unchanged tests or independent review for every helper. Required repository
   release validation remains applicable; this plan does not waive it.

Completion requires the [checklist](phase-105-checklist.md), including unchanged
interface evidence and producer/consumer integration. Source investigation and
earlier tests alone do not establish this Phase's acceptance.

## References

- [Workflow completion applicability](../spec/workflow-completion-applicability.md)
- [Single-user workbench control](../../ai/directive/core/workbench-control.md)
- [sm-workflow Phase 5.2](../../../../dev2026/sm-workflow/docs/phase/phase-5.2.md)
- [CNCF Phase 88](phase-88.md)

Planning only: no product edits, build, tests, Goal activation, artifact
publication, commit or deployment are performed by creation of this Phase.

Implementation source cutoff, 2026-10-12: the bounded Phase105 source/specification
batch is being authored in the dedicated checkout. No test, independent review,
artifact integration or acceptance result is recorded here. All WCS items remain
OPEN; the applicability section records the intended consumption boundaries.
