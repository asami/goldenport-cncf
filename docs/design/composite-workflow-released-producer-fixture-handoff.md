# Composite Workflow Released Producer Fixture Handoff

status=accepted
scope=phase77-sm-workflow-minimum
phase=64
swf=[SWF-07, SWF-08]
producer=Cozy Phase 62.3
deferred_generalized_producer=Cozy Phase 66
deferred_generalized_consumer=CNCF Phase 87

## Pinned producer evidence

This is a CNCF-side, read-only pin of the already accepted Cozy Phase 62.3
producer evidence.  It neither reparses CML nor substitutes a handwritten
definition for the producer contract.

| Evidence | Pinned identity |
| --- | --- |
| Final producer closure | [`db5e2b6bc6a035b9c598abdeee56d856116ee0a4`](https://github.com/asami/cozy/commit/db5e2b6bc6a035b9c598abdeee56d856116ee0a4) |
| Accepted producer handoff | [`f3c85b3de12c78efb6e88e210af71f1bd84ea9a5`](https://github.com/asami/cozy/commit/f3c85b3de12c78efb6e88e210af71f1bd84ea9a5) |
| CML fixture | [`src/test/resources/modeler/skill-driven-workflow-producer.cml`](https://github.com/asami/cozy/blob/db5e2b6bc6a035b9c598abdeee56d856116ee0a4/src/test/resources/modeler/skill-driven-workflow-producer.cml) |
| Fixture SHA-256 | `8d52e2116657fdc37bb1a422851e4047790efeda96831b4154ca9e46ca799827` |
| Producer revision | `workflow-producer-v1` |
| Generated Workflow ABI | `cozy.cml.statemachine-workflow-abi.v1` |
| Bootstrap schema | `cozy.cml.statemachine-workflow-bootstrap.v1` |
| Accepted-handoff executable-specification SHA-256 | `25674fe0c2b7adf9a4ecc7806f97792793969d8f6eb5084d9aa638966d0f80b1` |
| Focused receipt SHA-256 | `95006e1328d49916e7eab920fd26f5a4960d9bcf5e665a0d307d39f0a02bf6c2` |
| Final-closure strengthened executable-specification SHA-256 | `5f0837e526cb75891e57613edf70a42da08e3021bda29ce785fb3d66a9cf2908` |

The accepted handoff supplies the `workflow-producer-v1` ABI, bootstrap schema,
fixture identity, executable-specification evidence, and focused receipt used
for the Phase 77 / `sm-workflow` minimum in SWF-07/08. The final closure keeps
that fixture byte-identical and strengthens validation at its distinct
executable-specification SHA without an ABI change.

This evidence does not claim that the Workflow ABI is the complete generalized
Composite StateMachine semantic artifact described by the broad SWF-06 design.
The full-review correction recorded on 2026-09-21 supersedes the earlier
statement that no concrete ABI limitation existed.

## Minimum source facts consumed

The released fixture proves the following producer-side facts:

- the Workflow identity, version, states, action identities, and source
  locations are generated from the admitted CML fixture;
- the action sequence is `BuildProject -> RunTests -> ReviewChange -> CommitChanges`;
- `ReviewChange` declares Required SPI `review-change-capability`; and
- the generated service relation is
  `WorkflowService.reviewChange(ReviewContext) -> ReviewResult`.

These facts establish fixture-local generated-workflow completion and the
minimum typed action/progression facts consumed here.  They do **not** establish
the downstream typed `Provider`, `ActionExecution`, or `Continuation` runtime;
those remain consumer evidence owned by Phase 77.

## CNCF boundary binding

This handoff binds the released producer facts only to the minimum CNCF
contracts needed by Phase 77:

- the existing Phase 63.2 entity-triggered entrance consumes the post-commit
  `CommittedTransition`;
- SWF-04's typed action ordering boundary to the extent represented by the
  released Workflow action sequence;
- SWF-05's minimal Workflow definition identity and progression vocabulary;
  and
- the released Workflow subset of SWF-06's CML-first handoff.

The fixture itself starts explicitly through its Skill-driven entry.  That start
is not recast as an entity-triggered entrance, and this acceptance does not add
another entrance trigger.  `CommittedTransition` remains the only
entity-triggered entry fact consumed by the Phase 63.2/64 boundary.

## Observed limitation and future owner

The pinned Workflow ABI does not carry the complete generalized Composite
StateMachine configuration, derivation rules, causal/action provenance,
constituent definition version pins, or reachability/coverage/overlap producer
diagnostics required by the broad SWF-06 design. CNCF must not infer those
facts from names, reparse CML, or create a handwritten replacement.

That limitation is outside the first `sm-workflow` vertical-slice contract.
Future production of the generalized artifact belongs to Cozy Phase 66. Future
CNCF schema admission, compatibility diagnostics, ComponentFactory discovery,
and runtime projection belong to Phase 87. Both Phases have an evidence-bound
entry after the first `sm-workflow` vertical slice and do not block Phase 64,
Phase 77, or `sm-workflow` Phase 1.

## Phase 77 consumer handoff and deferrals

Phase 77 is responsible for generated API/SPI admission and `ComponentFactory`
bootstrap; the independently durable WorkflowInstance persistence SPI,
revision/history, suspension state, `Continuation` identity, `ContextSnapshot`,
and replay/stale protection; the `Provider` runtime, `ActionExecution`, bounded
advance, and Continuation/resume; and the minimum typed Workflow protocol with
schema-versioned fail-closed Skill/Codex JSON encoding, the minimum Generic
Skill projection, and the `sm-workflow` consumer handoff.  This document does
not claim any of those runtime, API, SPI, `ComponentFactory`, persistence, or
protocol responsibilities are implemented.

Later phases retain broad Start/API expansion, rich Presentation/UI, broad
reasoning vocabulary, parent/child Workflow composition, orchestration/REST/MCP
surfaces, Retry/Timeout and other runtime controls, Workflow-to-Workflow
orchestration and advanced integration.  2PC, compensation, and recovery are
Phase 85 work.  These deferrals do not alter the accepted Phase 64 semantic
contract.

## Acceptance boundary

`executable_spec_authoring_gate: not_applicable`.  No executable behavior
scenario changes in this documentation slice; the pinned producer
executable-specification and focused receipt are prior accepted evidence only.

SWF-07 and SWF-08 are accepted only for the Phase 77 / `sm-workflow` minimum on
this evidence. The generalized artifact remains explicitly unclaimed. Phase 64
is complete for this minimum; its generalized producer/consumer successor work
remains deferred.
