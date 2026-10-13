# Phase 88 selected-event persistence correction

The existing storage/permission/atomic-update work is retained, with the
Subsystem-owned service as the selected host-authority model. Its declaration
does not mean all events must be persisted. Workflow must evaluate an explicit
versioned exact-kind policy at prepare time, independent of event-access and
storage permissions. The new EventPersistencePolicy/WorkflowEventSelection
candidate is pure and grants no runtime write or dispatch authority.

Persistent events alone participate in durable cursor/event/index preparation.
Transient events have no stored sequence/index/replay and await verified
Workflow commit/readback before best-effort dispatch. Definite rollback drops
both delivery subsets; uncertainty suppresses both without automatic retry.
Nontransactional emit remains a separate future policy/dispatch adapter.
See the design at `docs/design/workflow-event-persistence-selection.md`.

Attempt 291 stopped before tests on stale Cozy generator runtime classpath
(exit 1, lock released). Cozy prerequisite 292 completed compile and
cozyExportRuntimeClasspath (exit 0, lock released). CNCF attempt 293 passed
45/45 tests in five suites (exit 0, wrapper exit 0, lock released):
WorkflowEventSelectionSpec, EventStorePersistenceHostSpec,
DataStoreConditionalMultiGuardPlanSpec, DataStoreConditionalUpdateSpec,
EventRecordJsonV1Spec. The host examples include a storage declaration with
transient-only selection and no opened resource/file, no-declaration and
unknown-grant rejection, one shared managed resource, split-resource rejection,
escaped-view rejection, malformed declaration and construction-scope restoration.

Execution: registered cncf_command_runner, gpt-5.6-luna / medium,
`/root/sbt_receipt_482258f8f3efa574146b`, healthy route. Receipt:
`/tmp/skill.cncf.d/cncf-command-execution-4dd94129793b8b5f06b38697930330073b3c894a67cf047ba1bda2df0dffb700-74da58ef114dc9a5033c78fec6a3aa5d/command-execution-sha256-ed3582af30e8ee895b00b687be80761dee8435be73ff16c7534aa7bf8c584029`.
Validated tree SHA-256:
`449d2f9898a05f0f541c3be15aaa402b98d290891fbf2ede667a69f8d102f1c5`.
Later changes update evidence/documentation only.

Actual selected-only persistence, cursor/index allocation and dispatch are
not wired or accepted. The positive S12 provider remains unsupported and
fail-closed. Pure rollback delivery projection does not prove actual provider
rollback or dispatch suppression. S12/P2-U15, consumer P2-U16 and P2-U05
remain open; no full regression, artifact refresh, commit or publication.
