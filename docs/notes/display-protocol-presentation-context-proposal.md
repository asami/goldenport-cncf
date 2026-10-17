# Presentation Context projected from ExecutionContext — proposal

Date: 2026-10-02
Status: non-normative proposal; not an accepted schema or runtime contract
Owner: CNCF Phase 96 generic protocol/runtime handoff
History: [proposal recording](../journal/2026/10/2026-10-02-presentation-context-proposal.md)

## Input and purpose

The [Editing Studio app UI-driven proposal](../../../../Project2026/nict-editing-studio-app/docs/notes/display-protocol-presentation-context-proposal.md)
asks Phase 96 to carry presentation-relevant CNCF ExecutionContext information
with Display and Operation calls. The [existing Display proposal](display-model-phase-96-proposal.md)
and [server sample](../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md)
are inputs, not frozen codecs. UI requirements and server-model evolution can
be proposed before completed server integration.

## CNCF responsibility

CNCF owns the generic ExecutionContext-to-Presentation-Context projection,
physical protocol/codec, compatible extension rules and validated continuation
into a subsequent execution. Applications own semantic resource projection and
bindings; Cozy owns Logical UI vocabulary. Core transports/retains the client
context and TFAF resolves supplied arm/variant identity to registered UI
realization. App screens compose public APIs without experiment branching or
manual context forwarding.

ExecutionContext is the runtime source of authority, including core locale and
admitted CNCF evaluation/correlation/scope state. Define an explicit mapping
from each projected family to its source and permitted use. The source remains
runtime-only: this proposal does not add persistent application/configuration
state to ExecutionContext or serialize service capabilities, RuntimeContext,
UnitOfWork or internal security objects.

## Proposed generic protocol structure

Keep logical business/display data and context as separate concerns. Context
contains typed, namespaced, versioned families. Proposed families include
locale/presentation, admitted experiment/arm and permitted correlation
references. Family spelling and JSON/header binding remain open.

For each family, define producer, allowed readers/updaters, lifetime, required
or optional support, propagation scope and merge/replacement behavior. Known
payloads require strict validation. Optional unknown carry-through requires a
bounded explicit rule. Generic extensibility must not permit arbitrary runtime
objects, free-form execution options or authority created by the client.

Protocol, context-family, generated ABI, Phase 74 semantic-contract and UI-definition versions and resource revision
have distinct meanings. Observed revision retains its existing transport
contract. Presentation selection must not grant mutation or Business Operation
capability; ordinary server authorization and Entity/Aggregate rules apply.

## Continuation proposal and existing constraints

Review context flow across query/read, standard mutation, direct REST Business
Operation, reload and retry. Define lifetime/expiration, independent-flow
isolation, restart/resume and component/server boundary behavior. A new attempt
can acquire its own correlation while retaining an admitted continuation
reference under the declared policy. Incoming context must be validated/rebound
before it influences projection or execution.

The accepted [Operation Evaluation design](../design/operation-evaluation-capture.md)
and [specification](../spec/operation-evaluation-capture.md) own admission and
immutable assignment for one logical execution. Nested Operations inherit
assignment only under explicit policy. Experiment run correlation additionally
requires the existing arm and immutable Corpus revision correspondence.
Cross-operation UI continuity is a proposed extension to resolve against those
rules; receiving an arm ID must not allocate an arm or bypass required admission.
Evaluation-free calls and optional unavailable admission keep their declared
normal behavior. No accepted admission rule is changed by this note.

## A/B and multi-arm UI

Experiment owners define and allocate arms and own lifecycle/measurements/
evaluation. CNCF provides the provider-neutral admission/correlation boundary.
The admitted arm information is projected into generic context; Core carries it
and TFAF uses it for UI-definition selection. The app can register definitions
declaratively while remaining unaware of experiment purpose or allocation.
This retains framework selection by arm identity, including when the server
also varies the Display projection. Unsupported/missing arm or definition
behavior and permitted defaults require an explicit contract.

No UI measurement responsibility is added here. Existing automatic operation
capture remains governed by CNCF evaluation contracts. A future exposure
measurement surface would need its own declared owner and handoff.

## Server-model feedback and fixtures

The [Editing Studio model proposal](../../../../Project2026/nict-editing-studio/docs/notes/display-protocol-server-model-evolution.md)
supplies typed semantic state, source revision, display/input values, scoped
editable-field bindings, operation availability and deterministic fixtures.
Resolve field-addressable errors and committed-write/Display-pending behavior
with the existing execution envelope. Pending projection must not induce
replay of an already committed command.

Required proposed handoffs are concrete request/response/state-transition
examples, a per-field ExecutionContext projection map, continuation/admission
rules and deterministic codec/context fixtures. Verify compatible extension,
unknown/expired families, flow isolation, fixed-arm rendering inputs, retries
and cross-Operation reload. These are proposed specifications, not executions.

Coordination lives in the [TKW note](../../../../dev2026/textus-knowledge-workbench/docs/notes/display-protocol-presentation-context-coordination.md).
[Core](../../../../dev2026/textus-flutter-core/docs/notes/display-protocol-presentation-context.md)
and [TFAF](../../../../dev2026/textus-flutter-application-framework/docs/notes/display-protocol-context-realization.md)
retain their own dependency handoffs. App Phase 4 owns client prototype
feasibility; Phase 5 owns real-server aggregate/E2E acceptance. Phase 96 retains independent acceptance; current progress is recorded in its
[checklist](../phase/phase-96-checklist.md). Exact codec,
public APIs, context admission and continuation are open review decisions.

## Client prototype scope update — 2026-10-02

The user clarified that app Phase 4 ends at provisional client implementation
and feasibility exploration. It uses development-only sample models and
controllable fake transport/Operations to prove the Core/TFAF/app boundary,
context continuation and supplied-arm UI. Findings revise protocol/server-model
proposals. Completed server implementation and frozen wire contracts are not
prototype closure prerequisites; simulation does not prove real CNCF admission.
App Phase 5 consumes this prototype and owns real-server integration and final
aggregate/E2E acceptance. Dependency contracts/Phases retain separate acceptance.
