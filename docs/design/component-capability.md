# Component Capability

Status: structural design

## Metadata admission stages

The structure has four pure metadata stages: external JSON, raw typed
`Projection`, explicit selected reference `Scope`, and private-constructed
`Admitted`. The strict codec owns transport decoding/validation and compact
encoding. Admission joins the acquired Component snapshot to caller-supplied
model/vocabulary facts, supplemental definitions and admitted target contract
facts. `Admitted` keeps the original projection, selected scope ID, combined
definitions and separate resolved edge/target records.

The Reference and Projection objects own their typed carriers. Internal
decoding, encoding and validation parts support the codec facade; a binding
part supports the admission facade. This package consumes canonical Component
identity/release authorities while keeping model/vocabulary identities in their
existing literal qualified domains. It receives no runtime Component or Factory
and performs no activation or source acquisition.

[R11](../spec/component-capability.md#r11-concrete-v1-format) owns the complete
wire/scalar/snapshot contract;
[R12](../spec/component-capability.md#r12-executable-metadata-admission) owns
scope, exact binding, compatibility, public APIs and diagnostics. The
[working specifications](../spec/component-capability.md#working-specifications-and-conformance-input)
use hand-authored external input and independently supplied ready facts.
Their validation and independent CAP-78-02 acceptance are complete as
recorded in [Phase 78](../phase/phase-78.md#cap-78-02-acceptance-and-implementation-boundary).

CAP-78-03 adds the accepted actual typed target adapter described below;
focused validation and independent protected Step conformance accepted it on
2026-10-14, as recorded in [Phase 78](../phase/phase-78.md#cap-78-03-acceptance).
CAP-78-04 has accepted the R14 canonical ComponentFactory/package/CAR metadata
source and public native discovery integration through focused validation and
independent protected Step conformance on 2026-10-14, as recorded in
[Phase 78](../phase/phase-78.md#cap-78-04-canonical-package-and-discovery).
CAP-78-05 retains real Cozy-generated input and producer/admission/CBD
interoperability with Cozy CAP-64-05 and CBD Phase 11. Pure binding against
explicit scope facts does not establish that normal producer/consumer proof.

## Actual typed target adapter structure

[R13](../spec/component-capability.md#r13-actual-typed-target-adapter) owns
acquisition, literal selection, source attribution, private accepted states
and failure semantics. The five internal modules divide that responsibility:

| Module | Responsibility |
| --- | --- |
| ComponentCapabilityTargetSource | Closed explicit acquisition/snapshot/key/binding carriers and three-family actual metadata Contract enum. |
| ComponentCapabilityTargetAdapter | Sole construction of Adapted/AdaptedTarget and Bound/BoundRealization; orchestration of pure adaptation and unchanged R12 admission. |
| ComponentCapabilityTargetValidationPart | Original ready-scope validation and scalar/source/key/context validation using existing R11/R12 helpers. |
| ComponentCapabilityTargetSelectionPart | Exact finite snapshot/native-selector joins, uniqueness checks and supplied binding order. |
| ComponentCapabilityTargetBindingPart | Kind-specific selected native shape and normalized machine checks; accepted R12 edge-to-actual-target joins. |

```text
Observed Acquired native metadata + original Ready scope + explicit bindings
  -> validate original facts and finite snapshots
  -> select exact snapshot/kind/native name
  -> Adapted (original actual objects + effective R12 scope)

Original raw Projection or strict R11 JSON
  -> unchanged R12 admission using effective scope
  -> original Admitted and ResolvedRealization
  -> exact TargetContract join to AdaptedTarget
  -> Bound (original admission + actual target objects in edge order)
```

The original R12 pipeline remains independently usable. The adapter supplies
its target facts from selected actual immutable CmlOperationDefinition,
WorkflowDefinition and normalized CmlStateMachineDefinition carriers, retaining
the original acquisition and ready input. Support parts return validated data;
the facade owns accepted construction. Native procedure metadata stays on
retained actual objects. No Component, engine, source reader or runtime
activation enters this structure. Explicit acquisition and opaque version
observations belong to the caller; canonical acquisition/discovery is accepted
in [CAP-78-04](../phase/phase-78.md#cap-78-04-canonical-package-and-discovery).
The [R13 working specifications](../spec/component-capability.md#actual-target-working-specifications)
locate the accepted CPT01..32 behavior inventory.

## Canonical package and public discovery structure

[R14](../spec/component-capability.md#r14-canonical-package-and-public-native-discovery)
owns the publication grammar, local acquisition bounds, public provider/catalog
contract, failure precedence and Factory behavior. Eleven modules implement this
structure:

| Module | Responsibility |
| --- | --- |
| ComponentFactory | Preserve runtime API and expose companion metadata delegation. |
| ComponentFactoryCapabilityPart | Self-typed instance metadata delegation independent of runtime repository. |
| ComponentCapabilityPackageSource | Explicit public selected/rejected location and coordinate carriers; layout constants. |
| ComponentCapabilityPackageLoader | Acquisition orchestration and sole accepted package/projection construction. |
| ComponentUsageCapabilityPackageLoader | Optional raw Usage acquisition and sole construction of its public opaque accepted carrier. |
| ComponentCapabilityPackageSelectionPart | Shared exact selected package validation, descriptor/Component release comparison and diagnostic context. |
| ComponentCapabilityPackageIndexPart | Strict index grammar, filename/order/count and inventory validation. |
| ComponentCapabilityPackageArchivePart | Local Directory/CAR inventory and bounded UTF-8 reads, original static descriptor/identity evidence. |
| ComponentCapabilityMetadataProvider | Public pure native acquisition/snapshot/binding/contract carriers. |
| ComponentCapabilityDiscovery | Package-before-provider sequencing, original adaptation/admission, batch uniqueness and sole accepted catalog construction. |
| ComponentCapabilityDiscoveryBindingPart | Lossless public-to-R13 conversion and original native-object joins; returns data. |

```text
Explicit Directory/CAR selection
  -> strict index + complete bounded resources + original canonical descriptor
  -> Acquired (ordered original R11 projections and raw source inputs)
  -> exactly one public provider read
  -> original R13 whole-native adaptation
  -> unchanged R12 admission for every projection + batch uniqueness
  -> Discovered (original package/native input + entries + original targets)

ComponentFactory companion / instance -> this static Discovery pipeline
```

The approved [R15 Usage contract](../spec/component-usage-capability-package.md#r15-acquisition-and-failures)
adds a separate metadata-only branch:

```text
ComponentFactory companion / instance acquireUsageCapabilities(PackageInput)
  -> ComponentUsageCapabilityPackageLoader.load(PackageInput)
  -> shared selection + fixed Usage family in existing Directory/CAR reader/index
  -> validate original canonical descriptor against selected Component/release
  -> Right(None) only when Usage family has no entries
  -> Right(Some(Acquired(input, descriptor, resourcePath, content))) when present
```

The public return type is
`Either[Diagnostic, Option[ComponentUsageCapabilityPackageLoader.Acquired]]`.
Only the Usage loader constructs this final carrier; its four public immutable
fields retain the exact selected input, original descriptor, fixed relative
`meta/component-usage-capability/usage.json` path and original decoded text.
Present broken index/resource, unreadable bytes and unsafe/ambiguous entries
remain typed failures. An absent family still retains descriptor evidence for
selection comparison without rereading the package. Directory absence uses
no-follow path attributes, with access/IO failure kept distinct.

ArchivePart and IndexPart share exactly two fixed internal family selections,
Component and Usage, retaining the original resource limits, strict UTF-8,
inventory, symlink/alias/duplicate handling and stream closure. No public
metadata registry or second reader is introduced. The Usage index selects
only `usage.json`; Component grammar/order/empty behavior and package-before-
provider discovery sequencing remain unchanged. A Usage-only canonical
Component has no native-provider/target or Component projection requirement.
The Usage branch never parses its envelope or checks model/owner/source/
producer/package semantics; CBD owns that interpretation.

The finite scope now names the inductive model
`ModelKind.ComponentUsageCapability` / `COMPONENT_USAGE_CAPABILITY`; unrelated
information Application APIs, Component ABI/PROVIDED/REQUIRED and ModelRole
mappings remain unchanged. CAP-78-PKG01 code, focused Step validation, the complete
Capability accumulator and independent protected Step review are accepted.
Earlier Step acceptance records above retain their historical scope.

Acquisition provenance and model provenance remain separate retained values.
The public bridge carries actual native definitions while keeping accepted
R13 Adapted/Bound internal. Support modules cannot construct accepted catalog
states. Cozy CAP-64-05 supplies the normal generator/publication path; CNCF
CAP-78-05 and CBD Phase 11 consume it for real interoperability. Authored
fixtures leave those obligations and participant Phase closure open.

## Authority and scope

This document describes Component Capability responsibilities and structure.
The [Component Capability Specification](../spec/component-capability.md)
is the self-contained normative authority for semantic behavior. Rule links
below identify that authority without defining a second behavioral contract.

## Ownership and non-instantiated Capability boundary

The responsibility boundary connects Cozy's model production, CNCF's
Component contract, and Textus CBD Support's catalog consumption.
ComponentUsageCapability is the separate inductive model of Component use;
ComponentCapability is the deductive architectural provided/required contract.
Both describe the reviewed Component and can share its Directory/CAR while
remaining separate typed families. Application/Domain is a different model/tier
axis. The [Usage package contract](../spec/component-usage-capability-package.md)
owns transport; Cozy owns Usage model generation. The pre-release migration
removes old Application symbols, with no aliases or backward-compatible reader.

The model contract and the runtime execution boundary occupy separate layers.
[R1](../spec/component-capability.md#r1-ownership-and-non-instantiated-contract)
defines their ownership and separation. The
[CNCF Architecture Overview](cncf-architecture-overview.md) supplies the
surrounding Component and Subsystem runtime structure.

## Exact model identity versus Component identity

The structure has two identity domains: the upstream model element describing
the Capability and the owning Component described by the existing
[Component Identity Specification](../spec/component-identity.md).
The [Component Identity design](component-identity.md) describes the
Component's namespace and local-ID authority.

[R2](../spec/component-capability.md#r2-exact-qualified-identity)
defines the Capability identity and its relation to Component identity.
[R3](../spec/component-capability.md#r3-provided-and-required-declarations)
defines the declaration edges joining these domains and the ownership of
definitions and links.

## Distinct version and provenance domains

Projection schema, Capability contract, generated CNCF ABI, producer,
Component/package release, and source revision are separate domains in the
handoff. Their structure keeps contract meaning, production, distribution,
and source attribution independently representable.

[R4](../spec/component-capability.md#r4-distinct-version-domains)
defines version admission;
[R5](../spec/component-capability.md#r5-independent-provenance)
defines provenance;
[R8](../spec/component-capability.md#r8-directed-contract-compatibility)
defines compatibility and changed-realization boundaries. The surrounding
[Component and Subcomponent Architecture](component-subcomponent-architecture.md)
provides Component, CAR, and payload ownership context without assigning
Capability identity to physical artifact evidence.

## Semantic grounding and explicit realization graph

Capability declarations are the graph's architectural facts. Grounding,
realization, and Specification/Evidence traceability are independently owned
edges with their own target and source domains.

```text
Component -- declaration --> versioned Component Capability
                                  |
                                  +-- grounding --> model element / term
                                  |
                                  +-- realization --> Operation
                                  |                   Workflow
                                  |                   StateMachine
                                  |
                                  +-- traceability --> Specification / Evidence
```

[R6](../spec/component-capability.md#r6-admitted-semantic-grounding)
defines grounding;
[R7](../spec/component-capability.md#r7-explicit-realization-edges)
defines realization;
[R9](../spec/component-capability.md#r9-specification-and-evidence-traceability)
defines traceability. Each edge family has its own meaning; the diagram is
structural context for those rules.

## Producer/framework/consumer handoff

| Participant | Structural responsibility | Normative authority |
| --- | --- | --- |
| Cozy | Syntax, IR, normalization, and Component Capability projection production | R1, R2, R5, R6 |
| CNCF | Component Capability semantics, version admission, realization binding, and discovery | R1, R3, R4, R7, R8 |
| Textus CBD Support | Ingestion, catalog, and query consumption | R1, R9, R10 |

[R10](../spec/component-capability.md#r10-admission-and-scoped-query-outcomes)
defines admission and query outcome distinctions across this handoff.
The [specification's implementation ownership](../spec/component-capability.md#implementation-ownership)
locates projection/admission, target binding, factory/package discovery, and
real producer/consumer proof within [Phase 78](../phase/phase-78.md).
