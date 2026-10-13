# Logical UI Generated ABI and Display Runtime Boundary

Document role: non-normative Phase 96 boundary proposal
Status: historical proposal; promoted design/spec subset accepted for P96-01; implementation pending
Date: 2026-10-03
Producer: Cozy Phase 76
Semantic authority: Cozy Phase 74

## P96-01C promotion and navigation

The selected ownership, ABI/admission/provider and runtime behavior subset is
promoted to [runtime design](../design/display-model-runtime.md),
[generated ABI spec](../spec/logical-ui-generated-abi.md) and
[runtime spec](../spec/display-model-runtime.md). The
[promotion journal](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md)
records scope and pending evidence. Earlier candidate statements are superseded
only for that subset; historical proposal text remains, and unpromoted wire
examples, mutation physical schemas and context details are non-normative.
Independent P96-01C review and parent admission accept the promoted documentary
subset only. Implementation/executable verification, Phase acceptance and actual
LUI-ABI-01/LUI-GEN-01/LUI-ACCEPT-01 receipts remain pending.

## Decision

Logical UI uses the ordinary CML producer/consumer ABI mechanism. CNCF owns the Scala 3 runtime/public ABI. Cozy owns semantic modeling and deterministic generation of Scala 3 source/metadata against that ABI. There is no CNCF runtime dependency on the Scala 2.12 Cozy artifact.

## Ownership

Cozy Phase 74 owns target-neutral Logical UI semantic authority and the admitted minimum vocabulary. CNCF Phase 96 owns Scala 3 generated-ABI value types, validation/admission, ComponentFactory discovery/bootstrap, component-scoped definition lookup, Display Projection, Display Model/protocol and DisplayService. Cozy Phase 76 owns mapping Phase 74 semantics to CNCF ABI, deterministic Scala source/metadata generation, bootstrap/provider metadata, producer fixture and consumer handoff.

## Consumer ABI shape

The [P96-01B ABI-v1 proposal](logical-ui-generated-abi-v1-proposal.md) records
the concrete candidate package `org.goldenport.cncf.display`, object
`GeneratedLogicalUiAbi`, provider trait `GeneratedLogicalUiMetadataProvider`
and `generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition]`.
Its static Definition/List/Detail/Section/Field/Value/Action values are distinct
from runtime instances. These names are proposal selections, not implemented
or accepted public API.

A bounded metadata provider follows generated Workflow/StateMachine discovery
patterns: component first, factory only when absent, explicit component override
when both exist and no definitions/Display capability when neither exists.
Selected-provider failure never falls back or publishes partial admission.

Admission is fail-closed: validate ABI version, stable identities, closed kinds, deterministic uniqueness/references and provenance before storing admitted definitions on the Component/runtime registry.

## ComponentFactory integration

Use a focused ComponentFactory part or equivalent bounded bootstrap responsibility.
The proposed flow is Component construction -> select provider -> atomic static ABI
admission -> required View/binding registration -> explicit typed binding join ->
component-owned Display lookup/exposure. The existing Workflow admission precedes
entity/View creation; the later Logical UI join must wait for its bindings.
Do not reparse CML at runtime or alter Workflow/StateMachine admission policy.

## Display Projection relationship

Display Projection combines admitted Logical UI definition/configuration, semantic View data and explicit value converters. It produces a Display Model instance. Logical UI definition is not itself a Display Model and is not a persisted presentation object.

## Producer fixture contract

CNCF first supplies a hand-written Scala 3 fixture representing the exact generated source shape expected from Cozy. Cozy Phase 76 generates semantically equivalent source/metadata. CNCF accepts the generated fixture through the same admission/bootstrap path. This mirrors StateMachine/Workflow producer-consumer development.

## Existing implementation reference and proposal selections

The current [GeneratedWorkflowAbi](../../src/main/scala/org/goldenport/cncf/workflow/GeneratedWorkflowAbi.scala)
and [ComponentFactoryWorkflowPart](../../src/main/scala/org/goldenport/cncf/component/ComponentFactoryWorkflowPart.scala)
provide the reference pattern: component/factory metadata provider discovery,
typed admission, then installation of admitted definitions. P96-01B's
[proposal](logical-ui-generated-abi-v1-proposal.md) selects its own closed
version/kind/reference diagnostics, provider precedence and service collision
policy for later acceptance/implementation. These references
establish a pattern; they do not mean a Logical UI provider already exists.

## Version and metadata ownership

Keep ABI identity/version, exact Phase 74 semantic descriptor, per-definition
revision, generated-source provenance revision, Display wire version and source
Entity/snapshot revision independent. SourceProvenance has producerId, sourceId
and revision; valid opaque provenance is admitted without an approved producer
commit or fixture hash. Handoff receipts separately pin actual source/artifacts.
The semantic mapping preserves the admitted roles, values, scoped IDs and order;
it does not require linking or reproducing the Cozy Scala object graph or
serializing its case-class layout.

Generated definitions contain static presentation configuration and bounded
references to admitted converters/action or field bindings. Current resource
values, revision, authorization and capability availability are supplied by CNCF
runtime. Generated v1 contains no editable/constraint-hint fields. Constraints
and mutation targets remain explicitly registered runtime/application data and
cannot weaken domain validation or grant permission. Wire members remain later decisions. P96-01B selects concrete candidate provider,
package and member names without promotion. It also selects service `display`
with canonical `query`/`read` Operations and no `list`/`get` aliases; conflicting
service or duplicate Operation is proposed to fail bootstrap. Physical routes,
media types, codec and mutation schemas remain P96-04..07 work.

## Cross-repository handoff records

| ID | Owner -> consumer | Evidence required |
| --- | --- | --- |
| LUI-ABI-01 | CNCF -> Cozy | Fixed consumer revision and usable source/artifact coordinates, ABI version, public types/provider signature, semantic mapping and unsupported subset, hand-written expected-generated fixture, admission diagnostics and focused projection proof |
| LUI-GEN-01 | Cozy -> CNCF | Fixed producer revision and generator command, Phase 74 source fixture/provenance, target LUI-ABI-01 identity, actual generated Scala/provider files and deterministic producer evidence |
| LUI-ACCEPT-01 | CNCF -> both ledgers | The exact LUI-GEN-01 source compiles against the matching ABI, passes ComponentFactory admission and yields the same List/Detail values/IDs/order as the hand-written fixture; negative admission cases and runtime classpath prove the boundary |

These are development handoffs inside the two Phases. LUI-ABI-01 does not wait
for CNCF Phase 96 closure or public artifact publication. LUI-ACCEPT-01 is
referenced by CNCF P96-03 and Cozy P76-05 and can be reused for unchanged
inputs. It permits later CNCF protocol/read/mutation work and can satisfy
Cozy's consumer-proof obligation before CNCF Phase 96 closes. Repository
release validation and app Phase 5 E2E remain separate.

All three handoffs remain pending. The proposal note is not LUI-ABI-01;
P96-02/03 must implement and compile the expected-generated fixture through
normal admission/projection. LUI-ACCEPT-01 uses actual Cozy-generated source.
Exact Git revisions/source coordinates and commands/results belong in each
receipt; no intermediate full-test or public-publication prerequisite is added.

Use the [Phase 96 checklist](../phase/phase-96-checklist.md) for current work;
the imported journal preserves the earlier proposal and its later correction.

## Prohibited shortcuts

- binary dependency from CNCF to cozy_2.12;
- reflection over Cozy Logical UI implementation classes;
- special cross-build solely for this integration;
- runtime CML reparsing in CNCF;
- a second CNCF semantic UI authority diverging from Phase 74;
- provider/client-specific UI objects in generated ABI;
- Flutter/TFAF runtime types in CNCF ABI.
