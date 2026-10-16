# Native Decision issue on the shared managed Workflow store

Scoped Phase 88 dependency work for the active sm-workflow Phase 2. The user
previously included CNCF Phase 88 in mutation scope and authorized parent-direct
limited recovery of skill-only execution stops. Parent `/root`, standard mode,
model/effort not directly observable, owns implementation and validation. Source
patches received scoped platform permission; unrelated dirty work is preserved.
No acceptance, agent, commit, publication or Phase closure is claimed.

Add `WorkflowManagedIssuedDecision` and `WorkflowComponentStorage.bindDecisionC`.
Their suspend/read/immutable retry capability is explicitly Decision-bound and
exposes no WorkOrder SPI, claim or completion. Private host-selected issued kind
reuses canonical managed issued publication; public WorkOrder bindings remain
WorkOrder-only. No public payload selects the kind. Exact Decision and WorkOrder
codecs remain separate with no kind inference/fallback; stop-only Decision
requests without a result type refuse. Existing `issuedWorkPayload` property and
WorkOrder bytes remain unchanged; the separately bound Decision route stores
actual DECISION bytes in that same atomic issued-payload slot.

The Subsystem's captured grant, shared physical DataStoreSpace/provider and
managed lease/UoW own the existing root-CAS plus Continuation-Create transaction.
No raw SQL, new pool, new grant or human provenance is introduced. Native
Decision completion remains unavailable; the current completion adapter still
requires WorkOrder. Issuing a typed question does not verify a human answer,
grant command authority or automatically enable application Operations.

`WorkflowManagedIssuedWorkSpec` adds actual managed SQLite issue/readback,
immutable retry, changed presentation conflict, stale duplicate suspension,
cross-kind refusal, stopped-host refusal and fresh physical reopen. A separate
case rejects WorkOrder, stop-only question and foreign owner/collection before
publication. One managed resource/no escaped lease assertions remain. These
examples do not prove Decision completion or Decision-specific race/fault cases.

Validation: 584 stopped before compile/tests because Cozy's selected development
classpath file was absent; both exits 1, lock released. Validation-only 585 ran
Cozy's project-owned `cozyExportRuntimeClasspath`; both exits 0, lock released,
no Cozy source changes or publication. 586 then passed 83/83 tests in five suites:
WorkflowManagedIssuedWorkSpec, WorkflowIssuedWorkDataStorePersistenceSpec,
WorkflowInstanceDataStoreSuspensionSpec, WorkflowPreparedDataStoreTransitionSpec
and WorkflowCompletionDataStorePersistenceSpec. Both exits 0, no aborted/pending/
canceled cases, authoritative lock released; persisted summary inspected.

586 log: `/tmp/skill.cncf.d/a-d83ef8aa-6640-468a-8d33-74560ef5f612/70908-20261001T110236Z.log`.
586 summary: `/tmp/skill.cncf.d/a-d83ef8aa-6640-468a-8d33-74560ef5f612/70908-20261001T110236Z.summary.json`.

The detailed synchronized application ledger is
`/Users/asami/src/dev2026/sm-workflow/docs/journal/2026/10/2026-10-01-native-decision-publication.md`.
Next implement separately kind-bound native completion, then refresh the
development SNAPSHOT before consumer integration. No artifact refresh, consumer
native API validation, production human verifier, repository-full acceptance or
release is established by this focused slice.
