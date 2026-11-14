# CNCF: Persistence and Execution Qualification for Commons

status=planned
execution_started=false
updated_at=2026-11-15
availability_date=2026-12-11

## Purpose and responsibility

CNCF supplies native Entity/datastore persistence, transactions, revisions, authorization and execution primitives. Workbench owns knowledge lifecycle and business stop/budget policies. Consumer qualification comes first; implement only demonstrated generic framework gaps.

The [responsibility/formation specification](../../../../Project2026/nict-knowledgehub-commons/docs/spec/knowledge-responsibilities-and-formation-routes.md) governs the shared boundaries.
The [Commons tracking register](../../../../Project2026/nict-knowledgehub-commons/docs/strategy/related-project-tracking.md) coordinates dependencies with Phase 1 RB-01..05 and D1-P01..07.
This is a bounded owner workstream. Existing numbered Phase states, accepted checklists and unrelated work remain unchanged.

## Existing contract and implementation boundary

Existing native Entity mutation, claim/lookup, concurrency and unknown-outcome contracts are inputs to binding, not proof of the selected application deployment. Keep active HA, Phase 118 external execution and Phase 123/124 plans independent; whole infrastructure Phases are not Commons release gates.

Start from the [native contract or delivery ledger](../spec/entity-conflict-and-conditional-transition.md). Additional Studio development/new integration remains on hold.
Studio and Heritage Curator direct editing is a lifecycle comparison, not a new application implementation dependency.

## Pre-release work

Table 1 maps deliverables to prerequisites and observable acceptance. Dates are coordination targets, not execution evidence.
CD-COM-01 accepts the combined binding inventory before effects; each owner's first item can be prepared in parallel.

| Task | Target | Dependencies | Deliverable | Acceptance |
| --- | --- | --- | --- | --- |
| CD-CNCF-01 | 2026-11-20 | Existing producer contracts; no CD prerequisite | Bind public persistence/revision/authorization/execution mechanisms and selected durable datastore configuration. | Identify actual consumer-facing DSL/API, required artifacts and support for conditional updates and outcome lookup. Map each application owner to its store. No consumer calls private CNCF classes, direct SQL or a replacement persistence framework. |
| CD-CNCF-02 | 2026-11-28 | CD-COM-01 | Qualify selected mechanisms and deliver only necessary generic gap repairs. | Native contract checks cover durable create/reopen, expected-revision conflicts, same-intent claim where adopted, authorization and unknown commit lookup. Distinguish native Job limits from Workbench daily business limits. A gap is returned with producer contract/validation; no speculative clustering, HA or cache framework is required. |
| CD-CNCF-03 | 2026-12-08 | CD-CNCF-02; CD-COM-03 | Support owner-qualified interruption/restart evidence for the selected deployment. | Actually persisted Material/work/edition references and stopped/pending/consumed state recover with correct owner semantics. Lost responses are reconciled without claiming rollback or replaying unknown effects. Confirm configured DB/original storage durability, not only an in-memory catalog or normal shutdown test. |

## Owner status ledger

Current status: OPEN. Execution has not started. All acceptance evidence: pending.
The following checkboxes are the authoritative task state for this workstream; the Commons register is a dated coordination snapshot.

- [ ] CD-CNCF-01
- [ ] CD-CNCF-02
- [ ] CD-CNCF-03

Check an item only after its actual behavior, applicable executable checks, producer/consumer compatibility and independent review pass at the delivered scope.
Record the selected artifact/configuration boundary, validation result and evidence reference beside that item. Reuse valid owner evidence; retain native release gates.
Keep real originals, accounts, addresses, membership, source IDs, credentials, private properties and live logs outside Git. Tracked tests/examples use synthetic data.
Only sanitized summaries and non-sensitive evidence references belong in these documents. Private evidence references are logical locators, not secret URLs or actual source IDs.

## Joined-route acceptance and release boundary

The selected input is the operator's Gmail mail with designated NICT participants in From/To/Cc,
2026-01-01 inclusive to 2027-01-01 exclusive, under private configured timezone and finite budgets.
Credentials are store references in Git-excluded properties. The earlier ten-header inspection is a sample, not a batch limit or Material-ingestion result.
EAI uses TKL registration; Commons extracts/matches/groups knowledge from saved Material and presents Candidates through the common foundation/Workbench.
Human review is on the private operator route. The team Web is read-only and contains only reviewed authorized reference projections.
Originals and operator capabilities do not reach the browser. Native authentication and community Authority are checked at their bound operations.
Formal Information admission, publication selection and BoK adoption are separate; unconnected projections are labeled truthfully.

Targets are working integration by December 4, actual content/access/recovery verification by December 8,
and Commons Phase 1 completion by December 10 for team use on December 11. This plan starts no runtime, ingestion or native Session.
Report missing prerequisites to the owner and the Commons register with their impact; do not substitute a direct Commons-to-Gmail connector.

## After December 11

Clustering, distributed caches, working-set eviction, broad external execution providers and general read-only/HA modes continue in their native plans. Add new framework work only for demonstrated generic gaps; this workstream does not reopen accepted Phases.

The [later S1 backlog](../../../../Project2026/nict-knowledgehub-commons/docs/notes/s1-update-integration-backlog.md) retains unadopted update contracts.
Planning and partial integration do not close that backlog or entire related Phases.
