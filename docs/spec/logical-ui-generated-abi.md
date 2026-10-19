# Logical UI generated ABI v1

Document role: normative implementation contract for Phase 96 static definitions.
Status: implementation contract; implementation and executable verification pending.
Independent P96-01C review passed; this documentary contract is accepted for
P96-01. Implementation, executable verification and Phase acceptance remain pending.

This specification promotes the frozen P96-01B sections 1..4 contract under
the [Display runtime design](../design/display-model-runtime.md). The separate
[runtime specification](display-model-runtime.md) governs contextual values,
service behavior and mutation. Cozy's [Phase 74 runtime spec](../../../cozy-phase76/docs/spec/logical-ui-runtime-contract.md)
and [design](../../../cozy-phase76/docs/design/logical-ui-runtime-contract.md)
are semantic authority, not binary types to import. Concrete fixture data and
source inspection provenance remain in the [P96-01B journal](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md).

## LUI96-S01: Identity and independent versions

The Scala 3 package is `org.goldenport.cncf.display`. Object
`GeneratedLogicalUiAbi` owns immutable ABI values and admission. Auxiliary
mixin trait `GeneratedLogicalUiMetadataProvider` exposes these public methods:

```text
generatedLogicalUiEnabled: Boolean = true
generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition]
```

`generatedLogicalUiEnabled` is a concrete, default-enabled server-side capability
on the selected component or factory provider. Bootstrap evaluates it once; it
is not a client parameter or a member of the generated Definition/wire shape.
Existing generated providers inherit the default without a generator change.

ABI identity is exactly `cncf.logical-ui-generated.v1`, integer version `1`.
It is independent of the exact Phase 74 descriptor, definition revision,
generated-source provenance revision, Display wire schema/version, runtime
Entity revision and source snapshot/correlation. IDs and revisions are opaque
validated text at this static boundary, not CanonicalId or EntityId issuance.
A definition revision is not EntityRevision.

`SemanticContract` must preserve and compare the full descriptor structurally
and exactly:

| Member | Exact value |
| --- | --- |
| `schema` | `cozy.logical-ui-runtime.v1` |
| `version` | `1` |
| `semanticAuthoritySchema` | `cozy.logical-ui-semantics.v1` |
| `assurance` | `provisional-scenario` |
| `driver.repository` | `KnowledgeHubProject/nict-editing-studio-app` |
| `driver.revision` | `48eb43b8eb839137e17ef4c944b3db4a73d0cec3` |
| `driver.path` | `docs/phase/phase-2.md` |
| `driver.scenarioId` | `resource-list-detail` |

Historical driver provenance does not relabel current app Phase 4 prototype
or Phase 5 integration ownership. A changed/null schema, version, semantic
authority, assurance, driver or driver member is incompatible. SourceProvenance
has opaque `producerId`, `sourceId`, `revision`; any valid strings are admitted.
Admission must not hard-code an approved producer commit or fixture hash.
Actual handoff receipts separately pin proof revisions/artifacts. No clock,
screen or resource ID derives provenance revision.

CNCF owns consumer ABI/runtime; Cozy owns semantic modeling and deterministic
generation. No copied Cozy object graph, `cozy_2.12` runtime dependency, special
shared cross-build, hash-pinned runtime fixture identity or second semantic
authority is introduced.

## LUI96-S02: Static definition shape

The following is public constructor-shape documentation, not a compilable
source artifact. All constructor members are immutable and public names are
camelCase. These types are owned by `GeneratedLogicalUiAbi`:

```text
Definition(abiIdentity: String, abiVersion: Int,
  semanticContract: SemanticContract, provenance: SourceProvenance,
  screenId: String, definitionRevision: String, viewBindingId: String,
  screen: ScreenDefinition)

sealed abstract class ScreenDefinition
ListDefinition(title: String, itemFields: Vector[FieldDefinition],
  detailTarget: DetailTarget, actions: Vector[ActionDefinition],
  itemActions: Vector[ActionDefinition])
DetailDefinition(title: String, sections: Vector[SectionDefinition],
  actions: Vector[ActionDefinition])

DetailTarget(screenId: String)
SectionDefinition(id: String, title: Option[String], fields: Vector[FieldDefinition])
FieldDefinition(id: String, label: String, role: PresentationRole, value: ValueDefinition)
ValueDefinition(bindingId: String, allowedKinds: Vector[ValueKind])
ActionDefinition(id: String, label: String, bindingId: String)

SemanticContract(schema: String, version: Int, semanticAuthoritySchema: String,
  assurance: String, driver: DriverReference)
DriverReference(repository: String, revision: String, path: String, scenarioId: String)
SourceProvenance(producerId: String, sourceId: String, revision: String)
```

The sealed abstract ScreenDefinition trunk has exactly ListDefinition and
DetailDefinition alternatives. `Definition.screenId` is the sole screen
identity; the alternatives must not duplicate it. Ordered definitions,
sections, fields, actions and allowed kinds use Vector and preserve input
order. Provider remains an auxiliary mixin trait.

Static definitions must not contain runtime resource values, Entity/source
revisions, permissions, capability results, Operation execution, functions or
callbacks, URLs/endpoints, widgets/layout/style, datastore references, an
untyped metadata map or a second validation language. Entity schema/constraints
and mutation target bindings are application/runtime-owned, not extra generated
fields. In particular generated v1 has no editable/constraint-hint members.

`viewBindingId`, `value.bindingId`, `action.bindingId` are application component
registration keys only, not semantic property expressions, routes, class names
or guessed setters. Explicit conversion may combine/derive values; no inverse
write mapping is inferred.

## LUI96-S03: Scalars and closed vocabulary

Public alternatives use these companion-qualified names, under
`GeneratedLogicalUiAbi`; no alternate naming scheme or extensible vocabulary
is selected:

| Public name | Exact tag |
| --- | --- |
| `PresentationRole.Content` | `content` |
| `PresentationRole.Title` | `title` |
| `PresentationRole.Subtitle` | `subtitle` |
| `PresentationRole.Description` | `description` |
| `PresentationRole.Status` | `status` |
| `ValueKind.Text` | `text` |
| `ValueKind.Number` | `number` |
| `ValueKind.Boolean` | `boolean` |
| `ValueKind.Timestamp` | `timestamp` |
| `ValueKind.Missing` | `missing` |

IDs, bindings, definition revisions and provenance use Phase 74's concrete
predicate `value != null && value.nonEmpty && value == value.trim`. Titles and
labels, including `Some(title)`, use `value != null && value.trim.nonEmpty`.
Admission preserves supplied bytes/display text; there is no sorting,
trimming, repair, Unicode normalization, coercion or unknown-kind fallback.
These predicates do not weaken stronger application/domain validation.

`allowedKinds` is nonempty, supported, nonnull and duplicate-free, preserving
order. A null collection is element-invalid; empty or unknown/null/duplicate
members are value-kind-invalid. Non-content roles require exactly
`Vector(ValueKind.Text)`; content admits any nonempty unique supported subset,
including text plus missing. Runtime title-role text must be nonblank; there
is no runtime title-field value to validate in the static ABI. Null/invalid
role or role-kind mismatch is role-invalid. Closed enums exclude fabricated
future variants at compile time; later decoding must reject unknown tags,
never turn them into text or missing. Date/choice/reference/structured kinds
are not admitted.

## LUI96-S04: Scoped uniqueness and references

| Identity or role | Uniqueness scope |
| --- | --- |
| Screen ID | Selected component provider vector, regardless of definition revision |
| Section ID | One Detail definition |
| Field ID | One List item template or one Detail section |
| Action ID | One List screen, List item template or Detail screen |
| Non-content role | One field container; multiple content fields permitted |

IDs may repeat across scopes; binding IDs may be reused and are not globally
unique field identities. Empty nested vectors and `None` section titles are
valid. A List DetailTarget must have a valid ID and resolve to an admitted
Detail in the same selected vector. Missing, malformed, self, wrong-kind and
cross-component targets fail; valid text alone is insufficient. A Detail may
be unreferenced by a List. No automatic cross-component import occurs.
Complete scoped duplicate ID/role checks and complete same-vector target
resolution are required, not only checking the first element or a prefix.

## LUI96-S05: Admission and deterministic structured diagnostics

The public gate is:

```text
admitC(definitions: Vector[Definition]): Consequence[AdmittedDefinitions]
Diagnostic(code: String, path: String, reason: String)
```

Raw Definition case classes remain untrusted. AdmittedDefinitions is immutable,
exposes `definitions: Vector[Definition]`, and has no public constructor/copy
that bypasses admission. Admission fetches no data, runs no converter,
authorizes no action, parses no CML and mutates no component registration.
Actual structured Consequence/Conclusion carries diagnostics, never a bare
string failure or uncaught null dereference. No wire diagnostic encoding is
selected here. Admission may emit exactly these twelve closed codes, never an
unknown code:

| Code | Rejection boundary |
| --- | --- |
| `LUI96_ABI_INCOMPATIBLE` | Null or incompatible ABI identity/version descriptor fields |
| `LUI96_CONTRACT_INCOMPATIBLE` | Null or nonidentical semantic contract |
| `LUI96_PROVENANCE_INVALID` | Missing or malformed provenance |
| `LUI96_DEFINITIONS_MISSING` | Enabled selected provider with an empty vector |
| `LUI96_ELEMENT_INVALID` | Null vector/root/element/Option container or Some(null) |
| `LUI96_ID_INVALID` | Malformed ordinary ID, binding reference or definition revision |
| `LUI96_TEXT_INVALID` | Malformed label/title text |
| `LUI96_VALUE_KIND_INVALID` | Unknown/null/duplicate/empty allowed-kind members |
| `LUI96_ROLE_INVALID` | Invalid role or role-kind mismatch |
| `LUI96_DUPLICATE_ID` | Duplicate ID within its declared scope |
| `LUI96_DUPLICATE_ROLE` | Repeated non-content role in a field container |
| `LUI96_DETAIL_TARGET_INVALID` | Invalid or unresolved same-component Detail target |

No provider or an explicitly disabled selected provider is not an ABI failure.
Disabled selection skips the definition getter and admission entirely. For an
enabled selected provider, a null vector is element-invalid and an empty vector
is definitions-missing, with the unchanged `LUI96_DEFINITIONS_MISSING` diagnostic.
Reject nested nulls before dereference: null roots/screens/structures/vectors/
elements/Option containers and `Some(null)` are element-invalid, while null or
malformed ABI descriptor, semantic descriptor and provenance use their
specific codes above. A null allowed-kind collection is element-invalid;
invalid members use value-kind-invalid. Detail target failures use their
specific code. No successful repair, normalization or fallback may occur.

First-error order is fixed:

1. Top vector shape/empty.
2. Each Definition in vector order: shape, ABI, exact semantic descriptor,
   provenance, scalar IDs/title, nested structure and scoped duplicate IDs/
   roles in declaration order, then nested fields/actions.
3. Duplicate screen IDs.
4. List Detail target referential integrity.

Code/path/reason ordering is stable. Paths use named members and numeric
indexes, for example `definitions[i].screen.sections[j].fields[k].role`.
Diagnostic text must not dump runtime secrets or values. Checks must preserve
the specific-code precedence and return before dereferencing rejected nulls.

## LUI96-S06: Provider selection

Follow the bounded ComponentFactoryWorkflowPart discovery pattern. A component
provider wins; only if it is absent is `component.factory` considered. With
both present the component explicitly overrides and the shadowed factory is
not read or accessed. Do not concatenate providers, scan classpath or reflect
Cozy classes.

After selecting the provider, evaluate `generatedLogicalUiEnabled` once during
bootstrap. False means no admitted definitions, runtime, service, mutation or
Business registration and skips every corresponding Display getter. A disabled
component still shadows an enabled factory; selection never falls through on
false. No provider preserves the same absence behavior for old components.

True, including the concrete default, requires unchanged whole-vector admission:
empty/null/invalid definitions fail construction without fallback or partial
publication. An exception from activation or definitions becomes the existing
structured component bootstrap failure. Successful repeated bootstrap retains
its registration and does not reread activation or other provider getters. This
capability introduces no late toggle, rebootstrap or retry mechanism and changes
neither ABI identity/version nor Definition constructor/wire shape.

## LUI96-S07: Atomic admission and binding readiness

Admit the complete selected vector atomically; never expose a partially
admitted prefix. Registry and lookup are component-owned with no JVM-global
mutable state. Structural ABI admission and runtime binding resolution are
distinct gates: admit static values, then join view/value/action keys to
explicit application bindings before Display lookup is usable. Missing binding
or incompatible typed source/converter is structured bootstrap failure, not
reflection or late best-effort inference. No generic DI engine is frozen.

Existing bootstrap admits Workflow metadata before entity/View creation.
Logical UI structural admission may occur there; its later typed binding join
must wait for required View and application binding registration and complete
before Display exposure. Workflow/StateMachine admission policy is unchanged.
Service registration/collision behavior is fixed by
[DSP96-R04](display-model-runtime.md#dsp96-r04-service-and-read).

## LUI96-S08: Producer evidence and executable specification ownership

Concrete ESS2-01 fixture values, explicit registration keys and source
inspection remain in the [P96-01B fixture journal](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md#ess2-01-development-fixture-mapping),
not duplicated here. Runtime identity/revision and writable-target constraints
remain separate application data. A source inspection or proposal does not
prove fixture compilation, ABI admission or generator acceptance.

The [unexecuted P96-01B-ABI-v1 matrix](../journal/2026/10/2026-10-03-phase-96-generated-abi-contract.md#unexecuted-acceptance-matrix--p96-01b-abi-v1)
contains the required Given/When/Then scenario descriptions:

| Requirement | Required executable proof | Owner | Status |
| --- | --- | --- | --- |
| S01/S02/S05 | ABI-01 external-package expected-generated fixture, immutable admitted preservation | P96-02/03 | REQUIRED / NOT YET IMPLEMENTED |
| S03/S05 | ABI-02 null/Option/Some(null), scalar/provenance diagnostics and no repair | P96-02 | REQUIRED / NOT YET IMPLEMENTED |
| S01/S05 | ABI-03 exact ABI and full semantic descriptor rejection | P96-02 | REQUIRED / NOT YET IMPLEMENTED |
| S04/S05 | ABI-04 prohibited duplicates and permitted scoped/binding reuse | P96-02 | REQUIRED / NOT YET IMPLEMENTED |
| S03/S05 | ABI-05 closed kinds, role-kind constraints and content subset preservation | P96-02 | REQUIRED / NOT YET IMPLEMENTED |
| S04/S05 | ABI-06 complete Detail target resolution and unreferenced Detail | P96-02 | REQUIRED / NOT YET IMPLEMENTED |
| S06 | BOOT-01 absent/component/factory/both and empty/invalid/throwing no-fallback | P96-03 | REQUIRED / NOT YET IMPLEMENTED |
| S07 | BOOT-02 atomic admission and late successful typed-binding join | P96-03 | REQUIRED / NOT YET IMPLEMENTED |

Implement specs with Given/When/Then at setup/action/expectation boundaries,
`should` vocabulary and property checks for scoped identity/reuse, supplied
bytes/order, closed kinds and stable diagnostic first-error paths. No text-only
Markdown tests count as proof. Suite paths/commands must be bound to actual
implemented artifacts by the owning Steps; no case is claimed passed here.

LUI-ABI-01 requires real ABI/provider implementation, the external hand-written
expected-generated fixture compilation, normal admission and focused projection.
LUI-GEN-01 is Cozy Phase 76's actual generated source/producer proof.
LUI-ACCEPT-01 uses those exact generated files through the same CNCF path,
not a handwritten substitute. Exact receipts and owners follow the
[design handoff table](../design/display-model-runtime.md#producerconsumer-handoffs).
All are pending, with no intermediate full-test/public-publication prerequisite
and no mutual whole-Phase closure dependency.
