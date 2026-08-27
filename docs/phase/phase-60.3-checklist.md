# Phase 60.3 Checklist - Component Admin Contract and Model Visibility

status=in-progress
started_at=2026-08-28
phase=[Phase 60.3 - Component Admin Contract and Model Visibility](phase-60.3.md)
predecessor=[Phase 60.2](phase-60.2.md)
successor=[Phase 60.4](phase-60.4.md)

## ADM-04: Component Contract and Model Visibility

Stage Status:
- Current status: IN PROGRESS
- Owner: CNCF Component contract, model, schema, and Admin maintainers
- Update rule: Implement the admitted package-private, value-only `ComponentAdminContractModelProjection` over the Phase 59 `ComponentKnowledgeManifestConsumerContract` before Phase 60.4 begins.
- Entry rule: ADM-03 is DONE.
- Completion rule: Service, Operation, dependency, schema, model, and diagram evidence is visible from authoritative Phase 59 contracts.

- [ ] Show Service, Operation, SPI, capability, and dependency contracts.
- [ ] Show Entity, Powertype, StateMachine, Value, Datatype, and relationships.
- [ ] Consume Phase 59 class/state diagrams and schema/model metadata.
- [ ] Preserve contract authority and distinguish generated projections.
- [ ] Verify visibility grants no invocation or management authority.
