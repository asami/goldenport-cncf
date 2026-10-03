# 2026-10-03 — Phase 96 isolated development worktree

Document role: non-normative execution setup record.

The user requested CNCF development needed for Editing Studio on a separate
branch. Branch `codex/phase-96-display-service` was created at committed CNCF
HEAD `6cbe877c53c116639feb11fa80546e79c051804c` in the worktree
`/Users/asami/src/dev2025/cloud-native-component-framework-phase96`.
The original checkout remains on `main` with its ongoing Phase 88 changes.
Its uncommitted runtime/source changes were not imported into this worktree.

The shared `ai/directive` submodule was initialized at the recorded gitlink
`a727716b856349c0ae5da4cc7f844c3d5842fe12` using the existing local clone.
Five Phase 96 preparation documents were copied from the original checkout:
the Phase plan, Display Model proposal, Presentation Context proposal, and
the 2026-10-01/02 preparation journals. Their original files were retained.
This branch's Phase plan records its execution branch and baseline. The
imported plans reflect app Phase 4 prototype and Phase 5 real-server integration
ownership. Proposals remain in notes; sample/history remain in journal.
Setup does not accept a proposal, close a stage, or prove runtime behavior.

## Development boundary and entry work

The [Phase 96 plan](../../../phase/phase-96.md) owns the generic Display Model,
projection, codec and component-scoped DisplayService. Editing Studio Phase 2
owns semantic resource projection and editable-field/Operation bindings;
app Phase 4 supplies prototype feedback, and Phase 5 owns actual integration.
The [Presentation Context proposal](../../../notes/display-protocol-presentation-context-proposal.md)
is included as current UI-driven input and remains subject to its admission
and continuation review.

Entry work is to compare the app's current prototype/codec fixtures with the
server sample and the committed CNCF/Cozy baseline, resolve the metadata codec
and committed-write/Display-pending representation, then record accepted
design/spec and a bounded executable-spec implementation plan. Read/projection/
codec precede standard mutation and the direct Business Operation/reload proof.
Production admission and Phase 88 persistence changes retain their separate
ownership; a needed upstream change is integrated only after its committed
contract is available and reviewed for this slice.

All future source edits and CNCF validation for this development line use this
worktree. Top-level SBT still uses the shared serialized runner across worktrees.
Child handoff uses focused verification; app Phase 5 owns final aggregate/E2E
acceptance. No build, test, publication, commit, or push was performed by setup.

## Development-plan import and checklist bootstrap

The user requested incorporation of the development-plan journal available on
GitHub and creation of a Phase 96 checklist. Only that journal was imported;
there was no branch-wide pull/merge or import of the ongoing Phase 88 changes.

Source: [development-plan proposal at the source commit](https://github.com/asami/goldenport-cncf/blob/1d33ac74081df9ff9df81e52ac1c0a04571e4327/docs/journal/2026/10/2026-10-03-phase-96-development-plan-proposal.md).
The source commit is `1d33ac74081df9ff9df81e52ac1c0a04571e4327`, with Git
blob `1cd85af7b017012be28b6f93ce5771663888c007` and commit timestamp
`2026-10-02T21:44:32Z` (2026-10-03 JST). The
[local imported journal](2026-10-03-phase-96-development-plan-proposal.md)
retains its original proposed-plan status and text.

The [Phase 96 checklist](../../../phase/phase-96-checklist.md) derives
P96-01 through P96-11 and checkpoints A through D from this proposal while
retaining the Phase's existing acceptance and parent/child handoff boundaries.
All stages and items start OPEN; documentation import is not runtime,
contract, baseline-review or Phase acceptance.

Reconciliation is tracked rather than silently resolved:

- The candidate `list/get` names and existing `query/read` names need one
  admitted public Operation binding.
- Proposed value kinds must align with the exact consumed Cozy Phase 74
  vocabulary before new kinds are accepted.
- The candidate KnowledgeCandidate-like fixture and ESS2-01 have different
  writable fields. Their provenance and update targets stay distinct; the
  fixture remains development-only.
- The proposal requires positive create/delete proof. ESS2-01's disabled
  capabilities are negative proof, not a replacement for those paths.
- The previously recorded Presentation Context/admission/continuation proposal
  is retained alongside the Phase 98 correlation-only hook, not silently
  narrowed or accepted.
- Focused child/dependency handoffs do not require full-test runs. CNCF
  repository release validation and app Phase 5 aggregate/E2E acceptance remain
  separate. Checkpoints do not create additional Phase full reviews.

This bootstrap changes only Phase 96 documentation in the isolated worktree.
It neither starts implementation nor changes the blocked product Goal state;
formal workflow admission/recording remains separate from checklist creation.
No build, test, publication, commit or push is part of this bootstrap.
