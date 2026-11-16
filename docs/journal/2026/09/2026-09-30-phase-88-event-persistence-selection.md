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

Actual selected-only persistence, cursor/index allocation and dispatch are
not wired or accepted. The positive S12 provider remains unsupported and
fail-closed. Pure rollback delivery projection does not prove actual provider
rollback or dispatch suppression. S12/P2-U15, consumer P2-U16 and P2-U05
remain open; no full regression, artifact refresh, commit or publication.

> Local execution archive: `~/backup/journal/2026/11/cloud-native-component-framework/cncf-journal-cleanup-20261116/a-46519ec7-ea0d-401b-ac84-b5c5c87060f6/docs/journal/2026/09/2026-09-30-phase-88-event-persistence-selection.md.gz`
