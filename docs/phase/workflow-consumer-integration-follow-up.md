# Workflow Consumer Integration Follow-up

Date: 2026-10-03
Status: unnumbered planning candidate; assessment and implementation pending
Development driver: sm-workflow
Additional consumer: textus-eai (`textus-enterprise-application-integration`)

## Scope and entry condition

The user reports that [Phase 88](phase-88.md) is already executing on Air.
The local planning projection does not establish the contents of that running
execution binding. This candidate records EAI requirements after that run; it
does not amend Phase 88's frozen plan or require it to adopt this intake.

Receive the actual Phase 88 result and stable sm-workflow consumer handoff,
then reconcile them with the [EAI requirement inventory and AC-01 through AC-10
trace](../journal/2026/10/2026-10-03-textus-eai-workflow-requirements.md).
Use delivered compatible capabilities directly. Only unresolved gaps become
development candidates, allocated to CNCF, Cozy or the consuming application
according to their established ownership. If the handoff already satisfies the
minimum slice, EAI adoption needs no additional upstream implementation Phase.

No Phase number is assigned or execution started here. A later planning decision
may bind remaining work to an appropriate open Phase after reviewing the actual
handoff and current plan. The accepted Phase 77/77.1/77.2 boundaries remain intact.

## Ownership and acceptance

The accepted [Phase 64/77 ownership decision](../journal/2026/09/2026-09-20-phase-64-phase-77-ownership-alignment.md)
keeps the persistence SPI provider-neutral: the consuming component supplies its
concrete backend, migration, retention and lease policy. Identify the actual
provider supplier and reusable configuration from the handoff; do not infer a
CNCF default or assume a new CNCF provider must be developed.

EAI owns Event/Binding/receipt association, configured source admission, typed
mapping/codecs and business evidence. CNCF owns runtime identity, native
admission/progression and private claims; Cozy owns genuine generated artifacts.
The requirements below describe the consumer capability assessment, not fresh
obligations imposed on the running Phase 88. Evidence may come from delivered
upstream capabilities or EAI-owned integration, with remaining gaps explicit.

### EAI-WF-01: Application service and configured persistence handoff


Stage Status:
- Current status: OPEN
- Owner: planning owner; CNCF runtime and consumer provider/application owners to be identified from the stable handoff
- Update rule: Reconcile each item against the actual Phase 88 and stable sm-workflow handoff first. Record delivered evidence and allocate only remaining gaps to their actual owners before freezing any implementation scope. This checklist closes only the follow-up assessment; it does not close a Phase or accept EAI runtime behavior.

- [ ] Inventory the stable `sm-workflow` WorkflowInstance store/provider and its construction, lifecycle and consumer-owned policy; state exactly which reusable binding EAI can consume and who supplies its concrete backend.
- [ ] Demonstrate a concrete provider implementing the existing `WorkflowInstancePersistence.create/load/append` contract, including revision-checked history and saved suspension, through normal component/bootstrap configuration. An interface, test probe or implicit default provider is insufficient evidence.
- [ ] Record the separate WorkflowInstance, issued WorkOrder and Continuation persistence owners and the commit/publication order. Preserve the loose Phase 77.1 boundary until a separately admitted Phase 94 migration.
- [ ] Bind EAI's declared typed Start and completion Operations through genuine CML/generated ABI and the normal application service. The managed start Command exposes actual Job/Workflow associations without making Job wrapping mandatory for all Workflows.
- [ ] Prove deterministic declared document retrieval, external WorkOrder suspension and a later completion call on the same WorkflowInstance, with native admission, a fresh UnitOfWork and one closing Action. Do not introduce a second engine, synchronous callback surrogate or new Workflow on completion.
- [ ] Keep Job completion separate from the typed Workflow business outcome; preserve Succeeded, Failed and Cancelled results and application-owned evidence validation.
- [ ] Supply canonical read-only status/result facts for EAI source/event lookup before the caller has a Job ID or WorkflowHandle. EAI owns the source/event receipt association; lookup must not dispatch or restart work.
- [ ] Exercise stale/incorrect identity, revision, snapshot, type and evidence; duplicate completion; unavailable/corrupt store and persistence failure. Preserve a valid pending suspension and structured incomplete outcomes after a rejected or indeterminate attempt.
- [ ] Use explicitly configured, non-network source/endpoint/work scope for the EAI minimum fixture. Payload identities and correlation do not confer authority; private claim material stays inside CNCF.
- [ ] Record a consumer handoff identifying the compatible artifact coordinates and actual metadata, genuine generated ABI, concrete provider/configuration, focused executable evidence and excluded guarantees. Mutable SNAPSHOT changes require affected consumer revalidation.

## Later planned integration

[Phase 87](phase-87.md) records truthful Workflow/Job observation,
[Phase 93](phase-93.md) records Event/journal correlation, and
[Phase 94](phase-94.md) records later shared-transaction migration compatibility.
Their full closure is not required for EAI's first non-network minimum fixture.
That fixture retains sequential requests and loose persistence boundaries; it
does not claim crash recovery, distributed exactly-once or external side-effect
atomicity. EAI AC evidence and its own Phase 1 ledger remain the acceptance basis.
