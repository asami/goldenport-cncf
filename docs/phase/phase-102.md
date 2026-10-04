# Phase 102: Integrated Admission Management

status=planned
planned_at=2026-10-05
depends_on=[Phase 90](phase-90.md)
consumer=textus-control-center, sm-workflow, Textus BoK/TKL integrations
checklist=[Phase 102 Checklist](phase-102-checklist.md)

## Goal

Extend the generic Candidate-Admission foundation established by Phase 90 into a minimal provider-neutral Admission Management contract for admission work whose physical candidate/review mechanism may live outside a CNCF Workflow.

Primary examples are GitHub Pull Requests for Phase/Plan and BoK editorial changes, sm-workflow candidate/review/admission, and future Textus knowledge or operational change candidates.

Phase 102 does not replace GitHub Pull Requests, repository review, or application-specific workflows. It defines the common logical identity/status/reference boundary required to present and operate them coherently through Textus Control Center and other clients.

## Boundary with Phase 90

Phase 90 remains authoritative for generic Candidate-Admission evaluation inside Workflow/StateMachine execution: CandidateRef/Snapshot, Submission, Requirement, Evidence, Evaluation, Gap, Result and admitted status.

Phase 102 consumes those concepts and adds the management/integration boundary needed for externally hosted admission processes. Do not reopen or redefine Phase 90 semantics merely to model GitHub.

## Minimal model

Freeze a provider-neutral management projection around AdmissionItem identity, CandidateRef, AdmissionProviderRef, EvidenceRef/external evidence references, AdmissionStatus, AdmissionDecisionRef, available bounded actions, authority/source reference, and revision needed for stale-decision protection. Exact names are implementation decisions.

The model must support references rather than copying external authority. A GitHub PR remains authoritative in GitHub; CNCF/Textus must not duplicate the complete diff, review conversation or repository state merely to create an AdmissionItem.

## Provider model

Define a narrow provider/SPI capable of listing/querying admission items, reading a bounded current projection, exposing allowed admission actions, applying an authorized decision/action with stale-state protection, and returning the resulting authoritative reference/status.

Initial provider targets are the CNCF/sm-workflow Candidate-Admission provider and a GitHub Pull Request adapter/provider implemented in the appropriate Textus integration boundary rather than by embedding GitHub protocol into CNCF core.

## GitHub Pull Request mapping

For Git-managed Phase/Plan and BoK artifacts:

- Candidate is represented by the Pull Request/reference and its head revision.
- Evidence references diff, rationale, validation results, source/provenance and related authoritative artifacts as appropriate.
- Admission actions map to provider-supported review/approval/change-request/rejection/merge operations according to repository policy and user authority.
- Apply remains GitHub/repository merge semantics.

CNCF does not implement a second Git review system.

## Consumers

Textus Control Center consumes Phase 102 to expose a unified Admission Inbox/Queue across providers while preserving provider authority.

sm-workflow aligns its externally visible admission status/decision projection with Phase 102 while retaining Phase 90 as its internal Candidate-Admission evaluation authority.

Slack/mobile/watch interfaces consume the same typed admission projection and bounded actions. Human interaction text is not authority.

Dot, OpenClaw, Codex or other agents may create candidates. Agent identity/product is provenance/execution context, not admission semantics or authority.

## Scope

1. Freeze the Phase 90 -> Admission Management projection boundary.
2. Define provider-neutral AdmissionItem/status/action/reference contracts.
3. Define provider SPI and stale-state/idempotency behavior.
4. Provide deterministic reference provider and executable specifications.
5. Define the sm-workflow provider mapping.
6. Define the GitHub PR adapter contract and external-owner handoff.
7. Define Control Center unified Admission Inbox consumption contract.
8. Define event/observability projection for admission lifecycle changes.
9. Record security/capability requirements for read, decide, approve and apply/merge actions.

## Non-goals

- Reimplementing GitHub Pull Requests.
- Copying full external candidate/review content into CNCF storage.
- Defining BoK editorial semantics or Phase/Plan quality rules.
- Giving AI agents admission authority.
- Building the Control Center UI itself.
- Making every external approval system conform internally to CNCF StateMachine semantics.


## Human-in-the-Loop as standard Admission

Phase 102 treats Human-in-the-Loop as a standard Admission routing/authority case rather than an application-specific workflow primitive.

A Candidate may require admission by policy, AI judgment, human authority, or a composed policy. The common management model must expose enough typed information to route a pending human decision and submit the resulting authorized action without embedding Slack, mobile, Watch, Dot, OpenClaw or other presentation/provider concepts.

Conceptually:

```text
Evidence / Context
      -> Candidate
      -> Admission Requirement
      -> Admission Authority / Policy
           -> automatic / deterministic
           -> AI judgment
           -> human decision
           -> composed policy
      -> Decision
      -> Apply
      -> Feedback
```

Human participation does not become authoritative merely because it occurs in a UI or conversation. The resulting typed Admission decision/action is authoritative under the owning provider/workflow policy.

## Continuation / IoC relationship

Admission semantics and Continuation mechanics remain separate.

- Admission answers the semantic question: may this Candidate be accepted/applied?
- Continuation provides the runtime mechanism for suspending execution when an external participant/result is required and resuming with a typed result.
- Human-in-the-Loop is one possible external participation binding selected by Admission/policy; it is not a special outer orchestration loop.

When an Admission Gap requires human judgment, the owning Workflow may route the required semantic Action through the existing Phase 77 Continuation mechanism. The external host/provider needs only the generic continuation/result contract. It does not need to know the Workflow's internal states or post-decision transition logic.

This preserves inversion of control: Workflow semantics declare required external participation, while deployment/runtime binds that participation to Slack, Web, mobile, Watch, AI, or another provider.

## Abstraction-preserving refinement

A component's abstract Workflow/Operation contract must remain stable when human or AI participation is added, removed, or changed inside the implementation, provided the external semantic contract is unchanged.

An internally automatic step may later gain Human Admission, or a Human Admission may later become policy-automatic after sufficient operational evidence, without forcing callers or collaborating abstract model elements to model the participant/UI loop.

Human/AI participation is refinable inside the Workflow implementation and must not leak into unrelated abstract model contracts.


## Driven-development sequencing

driver=textus-control-center Phase 6

Phase 102 is a CNCF-owned, consumer-driven development unit. Textus Control Center Phase 6 supplies the initial driver requirements and acceptance scenario, but MUST NOT copy or locally emulate the missing CNCF Admission Management abstraction.

Phase 102 does not become the global CNCF active phase merely because Control Center starts its consumer work. It may be developed in parallel with other independently driven CNCF phases such as Phase 100 (textus-knowledge-workbench) and Phase 101 (sm-workflow), subject to normal repository/worktree isolation and integration validation.

The intended development arrangement is a dedicated CNCF branch/worktree associated with the Control Center Phase 6 effort. The driver must not reuse or mutate another consumer project's dedicated CNCF worktree.

When upstream CNCF main changes are incorporated into the Phase 102 development branch/worktree, validate the affected CNCF scope and the CNCF full suite required by the development policy before treating the dependency baseline as accepted; then validate the Control Center consumer against that accepted CNCF state. A consumer test alone is not evidence that an incorporated CNCF change is safe.

### Control Center gate

Control Center Phase 6 may prepare its consumer-side model/UI integration in parallel, but it must not close against a private duplicate of the Phase 102 contract.

The closure handoff is:

```text
Control Center Phase 6 reaches CNCF integration boundary
  -> drives CNCF Phase 102 in its dedicated CNCF worktree
  -> CNCF Phase 102 contract/provider slice accepted
  -> Control Center consumes the accepted CNCF API
  -> Control Center Phase 6 admission vertical slice validated
```

Exact final closure ordering may be refined when Phase 102 implementation is split into slices, but CNCF remains producer authority and Control Center remains consumer/driver.
