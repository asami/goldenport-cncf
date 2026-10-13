# Presentation Context proposal from UI requirements

Date: 2026-10-02
Status: planning history; no implementation or acceptance evidence
Owner: CNCF Phase 96
Proposal: [local proposal](../../../notes/display-protocol-presentation-context-proposal.md)

The Editing Studio app discussion established UI-driven protocol and server-model
proposals using the development-only server sample. The user requested generic
context propagation, with presentation-relevant CNCF ExecutionContext content
as its source. A/B/multi-arm information remains in that context: Core carries
it, TFAF selects registered UI definitions from supplied arm/variant identity,
and application screens remain unaware of experiment logic and propagation.

The user asked to record the proposal on the server side and asked whether
Editing Studio and TKW are involved. CNCF owns generic projection, codec and validated continuation, reconciled with existing Operation Evaluation admission.
CNCF generic context/protocol, concrete Editing Studio evolution and TKW
coordination are recorded separately with reciprocal proposal links.

Only notes/journal and related planning references are changed. Existing dirty
work is preserved. No source change, test/runtime execution, commit, published
codec, new Phase acceptance or production admission contract is introduced.
