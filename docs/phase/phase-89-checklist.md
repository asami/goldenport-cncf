# Phase 89 Checklist: Generalized Composite StateMachine Artifact Admission

status=planned
phase=[Phase 89](phase-89.md)

This is a future execution ledger. It is not active until Phase 89's
evidence-bound entry condition and the Cozy Phase 66 handoff are satisfied.

## Entry

- [ ] The first `sm-workflow` vertical slice is stable.
- [ ] A concrete consumer requirement identifies missing semantic data and its
      runtime use.
- [ ] Cozy Phase 66 is accepted with an exact artifact/revision handoff.
- [ ] The current CNCF runtime and compatibility baseline are inventoried.
- [ ] A fresh bounded estimate and any necessary split are accepted.

## GCSA-89-01: Compatibility inventory

- [ ] Pin artifact schema, generator, definition, constituent, rule, and action
      identities/versions from the Cozy Phase 66 handoff.
- [ ] Freeze supported and incompatible version behavior.
- [ ] Preserve source/model/location provenance and producer diagnostics.

## GCSA-89-02: Fail-closed admission

- [ ] Admit complete compatible artifacts through typed CNCF Value Objects.
- [ ] Reject unknown/incompatible schema or generator versions.
- [ ] Reject missing, duplicate, foreign, unpinned, incomplete, ambiguous, or
      opaque semantic records.
- [ ] Emit structured rejection diagnostics without CML parsing, name
      inference, current-version substitution, or handwritten repair.

## GCSA-89-03: Discovery

- [ ] Register only admitted artifacts through ComponentFactory.
- [ ] Detect absent, duplicate, and incompatible artifact registrations.
- [ ] Preserve exact artifact, definition, source, and producer identity in
      discovery results.

## GCSA-89-04: Runtime projection

- [ ] Project exact constituents, configuration, and derivation rules into the
      existing Composite StateMachine runtime.
- [ ] Preserve action occurrence order, correlation/causation, execution
      metadata, and provenance.
- [ ] Preserve producer diagnostics without introducing a second CNCF analysis
      language.
- [ ] Do not modify Phase 77 Workflow API/SPI or persistence semantics.

## GCSA-89-05: Executable specifications

- [ ] Prove one accepted Cozy Phase 66 artifact through admission, discovery,
      runtime projection, and the concrete consumer path.
- [ ] Prove incompatible schema/generator/definition versions fail closed.
- [ ] Prove missing or ambiguous rules, diagnostics, provenance, and action
      occurrences fail closed.
- [ ] Prove no CML parser, name inference, or handwritten canonical substitute
      is used.

## GCSA-89-06: Handoff and closure

- [ ] Run the validation and independent review required by the newly frozen
      execution plan.
- [ ] Record exact Cozy/CNCF revisions, schema/generator versions, artifact
      digest, fixture identity, and validation evidence.
- [ ] Freeze the concrete consumer handoff.
- [ ] Confirm Phase 64, Phase 77, and the first `sm-workflow` vertical slice
      remain closed/unchanged and were not prerequisites reopened by Phase 89.
