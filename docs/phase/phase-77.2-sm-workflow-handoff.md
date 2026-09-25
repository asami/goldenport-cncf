# Phase 77.2 -> sm-workflow Consumer Handoff (Released)

status=released
released_at=2026-09-25
phase=[Phase 77.2](phase-77.2.md)

This releases the first-slice CNCF consumer boundary, not a claim that
`sm-workflow` has already integrated it. The accepted implementation baseline
is CNCF `74aa88f4c4e4a8b2e4d681fe285cfe57bca9ba49`; the aggregate
`P772-MANUAL-FULL-001` run passed 3,823 tests with zero failures and
`lock=released`. The [manual closure journal](../journal/2026/09/2026-09-25-phase-77.2-manual-closure.md)
records the reviewed scope and exclusions.

The accepted upstream chain is Cozy Phase 62.1 API/SPI and durable
ActionExecution (`51b4556b3ae0b038cf74d1718d8e2312b8d3f226`), Phase 62.2
generated ABI/bootstrap (`60603f371a9aa8f987810338bad8fe61c892a1b4`,
Step `a38daafbafaf490a9a2efb2c83c00027283d4696`), and Phase 62.3
producer fixture/consumer handoff (`db5e2b6bc6a035b9c598abdeee56d856116ee0a4`,
handoff `f3c85b3de12c78efb6e88e210af71f1bd84ea9a5`). The admitted
Workflow and bootstrap schema identities are
`cozy.cml.statemachine-workflow-abi.v1` and
`cozy.cml.statemachine-workflow-bootstrap.v1`. The additive Cozy placed
producer and Provided API changes are accepted at
`8d70aff3bc4b6e311bd7de38c209f08a7ad72bf0`, separately from the frozen
62.3 fixture. The placed sidecars use
`cozy.cml.logical-action-program.v1` and
`cozy.cml.statemachine-provided-api-abi.v1` alongside the Workflow ABI.

## Producer and typed contract

- Cozy Phase 62.3 generated Workflow ABI is pinned in
  `src/test/resources/workflow/skill-driven-workflow-producer-abi.json` with
  canonical JSON body SHA-256 (excluding the resource file's trailing newline)
  `c3f11c6160691dc747ab9b614ab040cb288d25bd83196b205891e1b6e365c013`.
  The CNCF fixture correlates the declared Workflow, four Actions, Required
  SPI, and source locations. The frozen fixture remains unchanged. An additive
  Cozy fixture separately provides generated Action placement and Provided API
  sidecars; CNCF admits those outputs without reparsing CML.
- A human-selected, registered application Start Operation constructs
  `WorkflowProtocolV1.WorkflowStartRequest[I]`; `WorkflowStartJsonV1` is its
  strict wire projection. The application owns the payload codec and selection
  authority. There is no generic public Start selector.
- A successful public Service response carries one
  `WorkflowProtocolV1.WorkflowInteraction[W, O]`. Consumers can use
  `WorkflowInteractionJsonV1` to encode/decode the closed `WORK_ORDER`,
  `DECISION`, `WAIT`, or `TERMINAL` response through the corresponding strict
  variant codec. `Presentation` is display data, not progression input.
- For `WORK_ORDER`, the current Continuation names the exact registered
  completion Operation. The application/Host maps its abstract
  `ExecutionRequirement` to a concrete worker profile, then converts the
  worker output with `SkillWorkResultNormalizerV1` to a typed
  `ContinuationResult[R]`. `WorkflowResultJsonV1` carries that Result and
  `ExecutionEvidence` across turns. Profile/model selection is evidence, not
  StateMachine control.
- The completion Operation uses
  `WorkflowCompletionServiceOperation` with an application-owned result codec
  and closing-program selection. The runtime checks the issued WorkOrder,
  matching saved WorkflowInstance suspension, and recovered private claim
  before resuming in a fresh UnitOfWork. A matching persisted Completed record
  is required before `TERMINAL` projection.

## Required sm-workflow specialization

`sm-workflow` supplies its three profile-specific CML definitions, typed
Start/WorkResult/Terminal payload codecs, registered Operations and Provider
bindings, human-selected invocation record, versioned worker-mapping policy,
application result admission, and Skill/Host transport. It must not copy the
generic envelope, select the next StateMachine state from worker prose, or
interpret `Presentation` as control data. The actual Codex invocation is a
Host/Skill integration concern, not a CNCF StateMachine transition.
`sm-workflow` Phase 1's executable-specification checklist owns P1-A06
(`JudgmentAction` goal/context/alternatives/criteria/expected-result
specialization) and P1-S06 (actual Codex worker with typed decision, rationale,
and evidence through the fail-closed CNCF boundary). Neither is proved by
CNCF's deterministic ReviewChange fixture.

## Verified boundary and remaining consumer work

The public Service fixture uses the shared response entry point for its
WorkOrder and Terminal (`P772-PUBLIC-INTERACTION-TEST-013`, 15/15, lock
released). It also exercises the persisted external ReviewChange suspension,
strict Result, fresh-UnitOfWork closing Action, and Completed record using
ABI-correlated *test programs*. The separate normalizer fixture uses a test
Host output, not a Codex invocation (`P772-SKILL-FIXTURE-TEST-010`, 24/24).

No production `sm-workflow` component, launcher/MCP integration, actual Codex
dispatch, or Decision submission path has been proved here. `DECISION` has a
typed wire projection only in the Phase 77.2 minimum contract; its submission
and execution semantics are outside this Phase and it must not be submitted
through the WorkOrder Result path. Phase 90's separate
Candidate-Admission dependency is also not released by this handoff.

The frozen Cozy 62.3 fixture's four logical `ACTION` declarations have no
`CONSTITUENT-ACTION` or `DERIVED-ACTION` placement. An additive Cozy fixture
now provides placements and produces a deterministic ActionProgram sidecar
(`P772-COZY-PLACED-TEST-003`, 1/1, lock released); the old fixture remains
unchanged and passed the combined regression (`P772-COZY-PLACED-REGRESSION-004`,
7/7, lock released). CNCF now admits the new sidecars together and binds their
occurrence sequence to `ExecutionPlan` (`P772-CNCF-PLACED-RECEIVER-TEST-017`,
3/3, lock released). A separate executor fixture runs that plan to
ReviewChange suspension and its CommitChanges entry binding in a fresh
UnitOfWork after recovery (`P772-CNCF-PLACED-EXECUTION-TEST-018`, 4/4, lock
released). CNCF also binds the generated plan to a provenance-checked
`StateMachineProvidedApiProgram` (`P772-CNCF-ADAPTER-RETEST-020`, 5/5, lock
released). The public Service vertical slice still binds its own Action
programs and admits the frozen Workflow identity; generated-plan
Service/WorkflowInstance integration was the remaining gate at that point.
The placed identity/revision/source SHA now has its own closed admission
profile, and WorkflowInstance definition binding validates it
(`P772-CNCF-PLACED-ADMISSION-TEST-021`, 30/30 across three suites, lock
released). The placed CML fixture now explicitly declares the public
`beginReview`/`submitReview` Operations, and Cozy generates their Provided API
sidecar (`P772-COZY-PLACED-PROVIDED-TEST-005`, 1/1, lock released). CNCF pins
and admits it with the Workflow and ActionProgram sidecars
(`P772-CNCF-PROVIDED-ADMISSION-TEST-022`, 30/30 across three suites, lock
released). The public Service fixture now executes the admitted generated
plan through ComponentFactory, persisted Continuation and WorkflowInstance,
fresh-UnitOfWork generated closing Action, and typed Terminal projection
(`P772-CNCF-PUBLIC-PLACED-TEST-023`, 15/15, lock released). This remains a
deterministic test Provider, not a production `sm-workflow` adapter.
Two distinct registered ReviewChange Provider identities produce the same
placed Workflow definition, Action trace, and terminal history
(`P772-CNCF-PROVIDER-REPLACE-TEST-024`, 15/15, lock released).
`sm-workflow` must not treat the Workflow ABI descriptor array as an automatic
progression plan or claim generated execution from this handoff.

The first slice uses a loose post-commit boundary: a failed or indeterminate
Continuation write yields an incomplete response and no WorkOrder from that
response, but may leave an `Available` record requiring reconciliation. It
does not provide shared atomic rollback or global claim exclusion. The shared
transaction upgrade remains Phase 94.
