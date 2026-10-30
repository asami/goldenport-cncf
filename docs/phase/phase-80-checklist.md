# Phase 80 Delivery Checklist

Plan: [Phase 80](phase-80.md)
Status: planned; new provider-integration acceptance is pending.

This ledger records the selected delivery and reconciliation of the earlier
minimum slice. Unchecked reconciliation does not invalidate existing results or
claim that its implementation is absent. Attach retained evidence before marking
an item complete; do not rerun successful work merely to populate this ledger.

## P80-A: inventory and contract alignment

- [ ] P80-01: Reconcile the Phase 5 minimum slice against actual typed/wire, placement, independence and existing-consumer evidence; identify only remaining gaps.
- [ ] P80-02: Inventory Harness, profile/registry metadata, Required-SPI/UoW and Continuation APIs; document the actual runtime/adapter caller and bounded dispatch gap.
- [ ] P80-03: Update spec/design for independent interface/placement dimensions, required work capabilities and result correlation; preserve the pure Harness and application-neutral CNCF boundary.

## P80-B: bounded review dispatch

- [ ] P80-04: One supplied-input review requirement can select either API Direct or Agent Continuation via configuration without modifying Workflow semantics; unsupported work capabilities are reported explicitly.
- [ ] P80-05: Connect the owning runtime's dispatch to API and Continuation adapter interfaces and return typed review result plus actual execution observations through the existing completion boundary.
- [ ] P80-06: Generic executable scenarios demonstrate both paths and hand the concrete producer interfaces, configuration and evidence to sm-workflow Phase 9 R1; distinguish adapter fixtures from real-provider qualification.

## P80-C: lifecycle and consumer handoff

- [ ] P80-07: Preserve COMPLETED/DECLINED/BLOCKED/UNAVAILABLE/FAILED and UncertainExecution; proven nonstart differs from response loss, which causes no blind redispatch.
- [ ] P80-08: Existing one-shot completion, duplicate/late-result handling and retained-result recovery remain effective; logical work correlation survives replacement issuance without completing an old Continuation twice.
- [ ] P80-09: A review in an isolated context can use the same model; implementer conversation is excluded and model change alone does not establish independence.
- [ ] P80-10: Evidence correlates actual interface, provider/profile, context and issued/result identities; absent usage stays unknown and no command authority is inferred.
- [ ] P80-11: Handoff identifies the finite Harness selection boundary and any minimum generic gap needed by Phase 9 R2; sm-workflow retains Takeover policy and repair accounting.
- [ ] P80-12: Selected implementation has focused acceptance and integrated review under the existing validation policy; current consumer Sessions, accepted results and history remain usable. Report remaining extension candidates without making every adapter or benchmark a closure gate.
