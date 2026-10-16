# Textus EAI Workflow Requirements Intake

Date: 2026-10-03
Status: planned consumer requirements; implementation and runtime acceptance pending
Role: attributed requirement intake for a post-Phase-88 candidate and planned integrations
Development driver: sm-workflow
Additional consumer: textus-eai (`textus-enterprise-application-integration`)

## Direction and authority

The user requested that planned CNCF Phases incorporate textus-eai requirements
while Workflow extensions continue to be driven by `sm-workflow`. This intake
updates planning documents only. It starts no Phase or product implementation,
grants no release/publication authority and reopens no closed Phase 77 ledger.
TEAI implementation and runtime proof remain deferred pending a stable upstream
artifact/provider and consumer handoff.

### Running Phase 88 correction — 2026-10-03

The user reports that Phase 88 is executing on Air. The local document's planned
status is not evidence of that run's frozen execution scope. The initial EAI
addition to Phase 88 has been withdrawn; this intake cannot amend the running
plan. Runtime/provider requirements are retained in the
[unnumbered post-Phase-88 follow-up](../../../phase/workflow-consumer-integration-follow-up.md).
Reconcile the actual delivered result and stable sm-workflow handoff first;
allocate only remaining gaps, without duplicating delivered work or renumbering
the running Phase. Planned Phase 87/93/94 additions remain consumer requirements.

The consuming repository's `docs/spec/minimum-teai-integration-contract.md`
(teai.minimum-integration.v1, sections 1–9),
`docs/design/minimum-teai-integration-boundaries.md` and
`docs/phase/phase-1.md` remain the EAI contract, ownership and acceptance sources.
This journal records their CNCF-facing requirements rather than replacing them.

## Consumer scenario and ownership

One configured source, one fixed Binding and one document Workflow run in a
single runtime with sequential requests. EVENT admits FileArrived and dispatches
a managed start Command; INVOCATION performs deterministic declared document
retrieval; DELEGATION returns a persisted WORK_ORDER; CONTINUATION submits the
native typed result in a later call and reaches TERMINAL on the same instance.
These four interaction labels are not four CNCF Continuation variants. The first
EAI fixture supports WORK_ORDER and TERMINAL; DECISION and WAIT stop explicitly.

- EAI owns EnterpriseEvent/Binding, typed mapping and payload codecs, configured
  scope admission, the minimal source/event receipt association and business
  evidence policy. Use normal Entity/store facilities; do not create a competing
  Workflow/receipt/claim engine.
- CNCF owns generated ABI admission, managed Command/Job and Workflow runtime,
  revision/Continuation admission, private claims and fresh-UnitOfWork closing.
- A consuming component supplies the concrete datastore/provider and its
  migration, retention and lease policy under the accepted
  [Phase 64/77 ownership decision](../09/2026-09-20-phase-64-phase-77-ownership-alignment.md).
  The post-Phase-88 assessment consumes the actual handoff to identify the
  provider's owner, supported construction and reusable consumer binding,
  allocating only any remaining ownership/configuration gaps. This intake does not select a CNCF default backend
  or assume that the current SNAPSHOT already contains a concrete instance store.
- Cozy owns genuine CML/generated artifacts; the configured non-network Edge
  harness supplies deterministic document/work/result behavior for the fixture.
  Neither test probes nor handwritten generated metadata establish runtime proof.

## Allocation after the running Phase 88

| Phase | CNCF-facing requirement | Acceptance role |
| --- | --- | --- |
| [Post-88 follow-up](../../../phase/workflow-consumer-integration-follow-up.md) (unnumbered candidate) | Assess real application service, configured persistence, generated Start/completion, native suspend/resume and status facts against the actual Phase 88 and stable sm-workflow handoff. | Reuse delivered evidence; allocate only residual gaps to their actual owners before admitting implementation. |
| [87](../../../phase/phase-87.md#textus-eai-observability-requirements--2026-10-03) | Canonical Workflow/Job projection with explicit EAI association mapping, truthful status/result and scoped/redacted lookup. | Later Web observation; no Web Console prerequisite for EAI Phase 1. |
| [93](../../../phase/phase-93.md#textus-eai-event-integration-requirements--2026-10-03) | Declared Event routing, journal/lifecycle correlation, delivery-versus-admission distinction and typed completion authority. | Later Service Bus adoption; no bus/network prerequisite for the fixed harness. |
| [94](../../../phase/phase-94.md#textus-eai-migration-requirements--2026-10-03) | Preserve EAI semantics under an explicitly selected durable shared transaction and later consumer migration. | Future atomicity upgrade after stable sm-workflow; never a prerequisite for the loose minimum slice. |

No new Phase, dependency on every intervening number, Workflow-wide protocol mode
or generic Start selector is introduced. Phases 77/77.1/77.2 remain the accepted
SPI/runtime/protocol baseline. The planned intake does not move their closure
criteria or promote an EAI application fixture into upstream acceptance.

## AC-01 through AC-10 requirement trace

| EAI acceptance | CNCF-facing obligation and plan | EAI-owned obligation |
| --- | --- | --- |
| AC-01 | Post-88 assessment: actual managed start Job and typed WorkflowHandle through declared/generated Operations. | Validate source/Event/Binding and return the recorded association. |
| AC-02 | Post-88 assessment: reuse confirmed native identities and expose actual start count; optional Phase 93 correlation. | Equal sequential delivery keeps the original pinned Binding/version and association after Binding updates. |
| AC-03 | Post-88 assessment: native start failure facts and no unauthorized creation; Phase 93 must not bypass admission. | Reject bad schema/source, zero/multiple Bindings and changed input under the same source/event key without overwrite/start. |
| AC-04 | Post-88 assessment: declared deterministic invocation and saved suspension/issued WorkOrder before successful external publication. | Fixed document fixture and delegation scope; session-local work/result reuse. |
| AC-05 | Post-88 assessment: later native completion on the same saved instance, fresh UnitOfWork and one closing Action. | Submit the issued identity and the application result through the declared completion Operation. |
| AC-06 | Post-88 assessment: reject stale/wrong identity, revision, snapshot, type and evidence; hide private claim; permit a later valid submission. | Application facts/evidence policy and malformed/duplicate result scenarios. |
| AC-07 | Post-88 assessment: native accepted completion preserves typed Failed/Cancelled outcomes; later Phase 87 projection stays truthful. | Succeeded/Failed/Cancelled codecs and truthful fixed-document evidence. |
| AC-08a | Post-88 assessment: read-only native association facts after start-response loss; no additional start on lookup/retry. | Preknown source/event lookup without a Job ID/Handle and retained receipt/pinned Binding. |
| AC-08b | Post-88 assessment: confirmed result after completion-response loss with unchanged instance/closing count; later Phase 87 projection. | Lookup plus retry proves prior completion; duplicate rejection alone is not proof of success. |
| AC-08c | Post-88 assessment: explicit absent/pending/unconfirmed/unavailable facts; later Phase 93 query must preserve that distinction. | Inspect each observation without automatic redispatch or treating absence as non-execution. |
| AC-09 | Post-88 assessment: independent Job/Workflow facts; Phase 87 projects a completed start Job and suspended Workflow separately. | Present business result only from the confirmed typed Workflow outcome. |
| AC-10 | Post-88 assessment: bootstrap-configured non-network source/endpoint/work scope, scoped read/completion and no claim leak; Phases 87/93 retain authority/redaction on later exposure. | Reject payload self-asserted authority and out-of-scope harness requests. |

This matrix traces consumer requirements for post-88 assessment; it adds no
obligation to the running Phase 88. Delivered capabilities may satisfy the
upstream portion without further development; every row still needs actual EAI
integration evidence. It changes no AC, checkpoint, reviewed implementation or test result.

## Receipt order and failure semantics to preserve

Validate fixed input/scope, record full Event plus pinned Binding and explicit
start invocation/idempotency reference, dispatch managed start, append actual
Job/Handle facts, and make the association readable before the success response.
Use an existing receipt before selecting an updated Binding. The receipt and
start are not claimed to share a transaction. Following submission, Absent is
not proof of no execution; PendingStart, Unconfirmed and Unavailable do not
permit automatic redispatch.

Preserve source/event → Binding/version → invocation → Job/Workflow →
run/Continuation/revision/snapshot → admitted result/evidence → same-instance
terminal as distinct correlated facts. Keep lookup read-only. Post-commit
persistence failure is committed-but-incomplete rather than a successful issued
WorkOrder or an atomic rollback. A missing concrete provider is an explicit
capability gap; it must not be filled with a probe or inferred default.

## Stable handoff and development sequencing

1. `sm-workflow` continues driving Workflow extensions and stabilizes its first
   real application/provider integration. EAI creates no competing upstream
   implementation branch or priority change from this planning intake.
2. Receive the actual Phase 88 result and stable sm-workflow handoff. Inventory
   the concrete provider owner/configuration, generated ABI, registered Start/
   completion binding, native suspension/resume, lookup/failure semantics and
   supported limits. The post-88 candidate records delivered evidence and only
   residual gaps; select their owners and any Phase binding before implementation.
3. Identify normal compatible CNCF artifact coordinates and selected artifact/
   dependency metadata separately from producer source and generated provenance.
   A mutable SNAPSHOT label is not immutable identity; changed artifacts require
   affected EAI consumer revalidation.
4. Resume EAI's own Phase 1 planning/adoption against that concrete handoff and
   validate AC-01 through AC-10 with the real runtime. Consumption depends on
   usable released capability evidence, not closure of every Phase 87/88/93/94
   feature. This record is not runtime acceptance or an automatic Goal resume.
5. Phase 94 remains a later opt-in migration. Define participating stores and
   extra recovery guarantees explicitly; do not silently include EventReceipt,
   bus delivery or Edge side effects in its shared commit.

## Excluded first-slice guarantees

No live OpenClaw/LLM/FTP/SFTP/Workspace, simultaneous workers/admission,
crash/restart or distributed exactly-once, automatic recovery framework,
production network exposure or new authentication service is required by the
EAI minimum fixture. A future phase may admit such scope separately; it cannot
be inferred from this planned consumer intake or a deterministic harness.
