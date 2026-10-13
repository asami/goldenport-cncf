# 2026-10-01 — Display Model Phase 96 preparation

Document role: non-normative chronological record.

While other CNCF development was in progress, the Editing Studio development
line prepared material for later Display Model work. The intended sequence is
Studio ESS2-01 concrete sample selection, CNCF Phase 96 generic contract work,
Studio Phase 2 server integration, and app Phase 4 integration. The app remains
the overall development driver; Studio supplies the concrete server-side input.

The preparation initially produced documents in `docs/spec`. The user clarified
the operational classification: specification proposals belong in `notes`,
history in `journal`, and sample-data information may be recorded in `journal`.
Accordingly the CNCF draft was moved to
[Display Model and DisplayService proposal](../../../notes/display-model-phase-96-proposal.md),
and Studio's sample was moved to its
[sample preparation journal](../../../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md).
Phase references were updated and normative/frozen status claims removed.

The sample follows the app fake Collecting Resources display shape, including
title, derived stage, description, proposed editable-field bindings, revision
examples, and a development-only Business Operation/reload scenario. These are
preparation records, not acceptance of resource semantics, protocol identity,
server bindings, or executable behavior. No implementation or Phase acceptance
was performed by this documentation reclassification.

## Continued preparation

The proposal was compared with canonical REST resolution, the current Cozy
runtime, Entity revision semantics, and the HTTP revision adapter. That review
found that the original fake revision-zero convention must not be carried
into CNCF managed Entity data: the revised sample starts at `1`, changes to
`2` on the title update, and uses framework transport metadata for observed
revision. Container-scoped Cozy field IDs also require a section/screen
locator for unambiguous editing.

The note now records candidate Operation routes, a typed update JSON example,
Cozy value encodings, strict revision-adapter requirements, no-op behavior,
and the distinction between a committed write and a pending Display projection.
The physical codec's complete metadata shape and pending-result representation
remain review questions. These additions remain in notes; they do not promote
the proposal to an accepted specification or start CNCF runtime development.
