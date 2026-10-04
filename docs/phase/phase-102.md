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
