# Component Capability Specification

Status: normative semantic and external v1 contract

## Authority and scope

This specification defines Component Capability meaning, identity,
declarations, versions, provenance, grounding, realization, compatibility,
traceability, and admission/query outcome boundaries. R1 through R10 are the
self-contained normative semantic contract. R11 and R12 define the concrete
external v1 format and metadata admission boundary. R13 defines the internal
adapter from explicitly acquired actual typed target metadata to R12 binding.

The [Component Capability design](../design/component-capability.md)
provides non-normative structural context. The existing Phase 56
[Component Identity Specification](component-identity.md) remains the
authority for Component identity.

CAP-78-02 supplies the metadata projection, strict codec, explicit reference
scope, and pure admission implementation described by R11/R12. Its executable
specifications and independent Step acceptance are complete, as recorded in
[Phase 78](../phase/phase-78.md#cap-78-02-acceptance-and-implementation-boundary).
E1 through E10 remain semantic expectations. CAP-78-03 supplies the accepted
actual target adapters and CPT01..32 specifications, as recorded in
[Phase 78](../phase/phase-78.md#cap-78-03-acceptance).
ComponentFactory/package discovery and real Cozy
producer/consumer proof remain CAP-78-04 and CAP-78-05. The hand-authored conformance resource
is external-contract input, not a generated fixture or interoperability proof.

## Rules

### R1 Ownership and non-instantiated contract

Capability MUST be a non-instantiated model contract. Cozy owns syntax, IR,
normalization, and production. CNCF owns the Component Capability semantic
contract, version admission, and discovery. Textus CBD Support owns ingestion,
catalog, and query.

Application Capability MUST remain upstream and MUST NOT become Component ABI
lifecycle data. Runtime invocation, availability, authorization, permission,
and guards MUST remain separate concerns.

### R2 Exact qualified identity

Stable Capability identity MUST be the exact qualified upstream
`ModelElementId(modelId, elementId)`, tagged as Component Capability.
`contractVersion` MUST be a separate, explicitly supplied opaque value.

Model identity components MUST retain exact case, Unicode, and delimiters
under the established Cozy foundation validity rules: nonempty, unpadded,
and free of ISO control characters. Identity MUST NOT be issued as a runtime
`EntityId` or `CanonicalId`. Business meaning MUST NOT be parsed from IDs.
Names, digests, and positions MUST NOT derive or substitute for identity.

The owning runtime Component MUST retain the existing Phase 56
namespace/local-ID `ComponentId` authority and its separate release version.
Capability identity MUST NOT replace or reinterpret Component identity.

### R3 Provided and required declarations

A declaration MUST bind one exact Component identity, a `PROVIDED` or
`REQUIRED` direction, and a versioned Capability reference comprising the
R2 identity and explicitly supplied contract version.

Several Components MAY provide the same reference. One Component MAY both
provide and require that reference. The uniqueness key MUST be the complete
`(Component identity, direction, Capability reference)` tuple.
Duplicate or conflicting declarations MUST fail admission; first-wins or
last-wins collection behavior MUST NOT resolve them.

Definitions and links MUST retain their declaring model/source ownership.
Presentation labels MUST NOT merge identities.

### R4 Distinct version domains

Projection schema version, Capability `contractVersion`, generated CNCF ABI
version, producer version, Component/package release, and source revision
MUST be distinct explicit fields. The first projection semantic generation
is version 1. An unsupported future version MUST produce a typed unsupported
result and MUST NOT be decoded as version 1.

CAP-78-02 MUST freeze concrete wire keys, the codec, and the exact ABI support
set before executable admission. CAP-78-01 freezes no existing serialization
implementation or invented ABI. The field domains here describe semantics
and do not prescribe wire keys or syntax.

### R5 Independent provenance

Provenance MUST independently retain producer identity/version, declaring
model authority, exact Component identity, package coordinate/release,
explicit source revision, and project-relative source location. A supplied
source line MUST be positive; line information is retained when available.

Revisions MUST be upstream-authored data and MUST NOT be computed content
identities. Empty or unknown source acquisition MUST NOT be admitted as
provenance. This contract MUST NOT introduce hashes or byte identities.
Existing Cozy foundation `sha256` MUST NOT become Capability identity,
revision, permission, or admission authority and MUST NOT be promoted into
this contract.

### R6 Admitted semantic grounding

Admitted semantic grounding MUST preserve qualified
`ModelElementId(modelId, elementId)` or `TermId(vocabularyId, termId)` targets,
relation/profile identities when supplied, the explicit local/external
boundary, declared/derived origin and its evidence, and edge-specific
source/revision.

Actual Domain Concept, Glossary, and BoK references MUST be preserved without
name/IRI equivalence or ontology-completeness requirements. Unknown external
targets MUST remain explicitly external. A declared local target MUST resolve
uniquely within the admitted source scope. Missing grounding MUST remain
explicit absence and MUST NOT manufacture a term.

### R7 Explicit realization edges

Realization MUST be a separate explicit edge from a versioned Capability
declaration to exactly one `OPERATION`, `WORKFLOW`, or `STATE_MACHINE` target
reference per edge. Each edge MUST retain the target-owning Component/model
scope, target contract/ABI revision, and edge source/revision.

References MUST bind already admitted target contracts exactly. Unknown,
ambiguous, or incompatible declared targets MUST fail admission. Explicitly
empty realization relationships MAY be admitted as non-executable
architectural facts. They yield a downstream `MissingRealization` gap when
needed and MUST NOT fabricate a mapping.

Realization edges MUST NOT embed ordering, retry, compensation,
authorization, or guard state.

### R8 Directed contract compatibility

Provider matching MUST require equal qualified Capability identity and either
exact `contractVersion` equality or an explicit admitted directed
compatibility edge from the required version to the provided version of that
same identity.

Matching MUST NOT assume SemVer, use name similarity or cross-identity
equivalence, select a provider by ordering, infer undeclared transitivity,
or invent implicit compatibility.

A realization MAY change without changing stable Capability identity only
while the explicitly declared Capability contract remains satisfied.
Target ABI compatibility MUST be checked separately, and its provenance
changes. A semantic contract change MUST have an explicit `contractVersion`
change and declaration; a revision MUST NOT be guessed.

### R9 Specification and Evidence traceability

Specification and execution Evidence MUST be separate explicit qualified
traceability references with their own source/revision and edge ownership.
Missing references MUST remain absent.

Reference presence MUST NOT prove successful execution, specification
coverage, availability, or authorization. Edges MUST be preserved
independently; unrelated reference lists MUST NOT be cross-producted.

### R10 Admission and scoped query outcomes

Admission and query facts MUST distinguish an admitted empty relationship or
catalog from unsupported, unavailable, unauthorized, stale, ambiguous,
malformed, or incompatible input. Rejection MUST preserve the typed
diagnostic kind and exact owning scope/location.

`NoProvider` MAY be reported only over an explicitly selected, successfully
acquired, admitted catalog scope. Failed source acquisition MUST NOT prove
nonexistence. Application/Use Case demand projection availability MUST remain
separate from required Component Capability input availability.

### R11 Concrete v1 format

The Scala metadata package is `org.goldenport.cncf.component.capability`.
`Projection` is raw transport metadata; decoding or encoding MUST NOT construct
an `Admitted` result. This API requires no Component/ComponentFactory instance,
runtime activation, ClassLoader, reflection, service loading, CML parser or
source acquisition IO.

The supported `schemaVersion` is integer **1**. The exact producer ABI support
set is **{`cncf.component-capability.v1`}**. This producer ABI identifies this
external Capability contract. CAR ABI integer 1, CNCF library
`0.5.3-SNAPSHOT`, and `GeneratedWorkflowAbi` are separate domains and MUST NOT
be accepted as aliases or defaults. A realization's target ABI is independently
supplied and checked against its target contract under R12.

#### Scalar and qualified identity validity

Text MUST be nonnull, nonempty, free of ISO controls, and have no leading or
trailing code point satisfying `Character.isWhitespace` or
`Character.isSpaceChar`. This includes model/vocabulary IDs, opaque versions
and revisions, producer/package values, names, present descriptive text,
context, localized label text/language, rule IDs and absence details.
Case, Unicode composition and delimiters MUST remain exact; qualification
MUST NOT split, normalize, issue or convert existing model/vocabulary identities.

Source paths MUST use the Foundation canonical project-relative POSIX shape:
nonempty, no ISO controls, leading slash, drive/URI scheme prefix, backslash,
empty segment, `.` segment or `..` segment. Invalid paths MUST NOT be normalized.
`Source.line` is explicit null or a positive integer. A localized label's
language is explicit null or valid literal text.

`ComponentRef` is carried by the existing `ComponentId`. Decode the separate
namespace/id fields with `ComponentId.parseC`; its canonical
`namespace.value()`/`localId.value()` MUST equal the supplied fields exactly.
Validate the Component release with the existing shared
`ComponentReleaseCoordinate.create`. Titles, artifact aliases or label case
MUST NOT choose Component identity. Component release, package version,
producer version, Capability version, ABI, schema and source revisions are
independent explicitly supplied values.

#### Complete closed wire table

Every listed key is mandatory and every object has exactly its listed keys.
`text` below means valid literal text; `array[A]` is ordered and retains every
supplied entry. Only `Source.line` and `LocalizedLabel.language` permit null.
No optional-key inference, unknown-field bag, aliases or fallback defaults exist.
The order of keys in this table is the stable compact encoder order.

| Carrier / branch | Exact ordered keys and value domains |
| --- | --- |
| Projection | `schemaVersion: integer 1`; `generatedAbiVersion: cncf.component-capability.v1`; `producer: Producer`; `component: ComponentCoordinate`; `package: PackageCoordinate`; `source: Source`; `capabilities: array[CapabilityDefinition]`; `declarations: array[Declaration]`; `compatibility: Presence[array[Compatibility]]` |
| Producer | `id: text`; `version: opaque text` |
| ComponentRef | `namespace: canonical namespace`; `id: canonical local ID` |
| ComponentCoordinate | `namespace: canonical namespace`; `id: canonical local ID`; `version: explicit validated release` |
| PackageCoordinate | `coordinate: opaque text`; `version: opaque text` |
| ModelElementId | `modelId: qualified authority text`; `elementId: qualified element text` |
| TermId | `vocabularyId: authority text`; `termId: term identity text` |
| RelationId | `vocabularyId: authority text`; `relationId: relation identity text` |
| ProfileId | `vocabularyId: authority text`; `profileId: profile identity text` |
| Source | `modelAuthority: authority text`; `path: canonical POSIX path`; `sourceRevision: upstream-authored opaque text`; `line: null or positive integer` |
| Presence / Present | `state: PRESENT`; `value: A` |
| Presence / Absent | `state: ABSENT`; `reason: NotDeclared \| NotRepresented \| Unsupported \| NotApplicable`; `detail: text` |
| Origin / Declared | `kind: DECLARED` |
| Origin / Derived | `kind: DERIVED`; `ruleId: text`; `inputs: nonempty array[Source]` |
| ModelReference | `identity: ModelElementId`; `boundary: LOCAL \| EXTERNAL` |
| SemanticTarget / Model | `kind: MODEL`; `identity: ModelElementId` |
| SemanticTarget / Term | `kind: TERM`; `identity: TermId` |
| LocalizedLabel | `text: text`; `language: null or text` |
| CapabilityDefinition | `kind: COMPONENT`; `identity: ModelElementId`; `contractVersion: opaque text`; `name: presentation text`; `summary: Presence[text]`; `description: Presence[text]`; `source: Source`; `discoveryReferences: Presence[array[ModelLink]]`; `grounding: Presence[array[Grounding]]`; `traceability: Presence[array[Traceability]]` |
| CapabilityReference | `kind: COMPONENT`; `identity: ModelElementId`; `contractVersion: opaque text` |
| DeclarationKey | `component: ComponentRef`; `direction: PROVIDED \| REQUIRED`; `capability: CapabilityReference` |
| Declaration | `component: ComponentRef`; `direction: PROVIDED \| REQUIRED`; `capability: CapabilityReference`; `source: Source`; `realizations: Presence[array[Realization]]`; `traceability: Presence[array[Traceability]]` |
| Realization | `declarationKey: DeclarationKey`; `targetKind: OPERATION \| WORKFLOW \| STATE_MACHINE`; `target: ModelReference`; `targetComponent: ComponentRef`; `targetContractVersion: opaque text`; `generatedAbiVersion: independent target ABI text`; `source: Source`; `origin: Origin` |
| Compatibility | `identity: ModelElementId`; `requiredVersion: opaque text`; `providedVersion: opaque text`; `source: Source`; `origin: Origin` |
| ModelLink | `role: GOAL \| USE_CASE \| SCENARIO \| USE_CASE_SLICE \| COMPONENT \| SPECIFICATION \| EVIDENCE`; `target: ModelReference`; `source: Source`; `origin: Origin` |
| Traceability | `role: SPECIFICATION \| EVIDENCE`; `target: ModelReference`; `source: Source`; `origin: Origin` |
| Grounding | `target: SemanticTarget`; `boundary: LOCAL \| EXTERNAL`; `relation: Presence[RelationId]`; `profile: Presence[ProfileId]`; `context: Presence[text]`; `preferredLabel: Presence[LocalizedLabel]`; `source: Source`; `origin: Origin` |

The Scala field `Projection.packageCoordinate` serializes as `package`.
`CapabilityDefinition.kind` and `CapabilityReference.kind` are fixed
`COMPONENT` values; their wire tag is nevertheless mandatory. Application
Capability and Use Case demand have no substitute branch in this envelope.

Use strict Circe `JawnParser(allowDuplicateKeys = false)` before conversion
into collections. Syntax errors, duplicate keys, missing/extra keys, null
mandatory data, wrong scalar/array types, boolean/fractional schema or line
values, mixed Presence/Origin/target keys and malformed lexical tags MUST fail.
Unknown well-formed closed tags, future schema and unsupported producer ABI
MUST produce `Unsupported`. Header schema/ABI checks precede v1 nested
decoding so future fields do not silently downgrade. Malformed schema types
remain `Malformed`.

Present-empty is distinct from Absent. Every reason/detail, vector order,
origin and independent source revision MUST survive semantic roundtrip.
Original whitespace and byte identity are not promised. `sha256` is not a
Source key and MUST NOT supply a revision.

#### Snapshot and source ownership

One Projection is one Component/package generation snapshot. Every
declaration Component MUST equal the envelope Component and its source
authority MUST equal the envelope source authority. Snapshot definitions MUST
have unique qualified identities even when versions or names differ.
Definition source authority MUST equal `identity.modelId`; definition
discovery, grounding and traceability sources MUST equal that owner.
Declaration realization and traceability sources MUST equal the declaration
authority. A realization's repeated full `declarationKey` MUST equal its
enclosing key. Compatibility is authored by the envelope authority. All
Source records, including Derived inputs from independently supplied other
authorities, MUST be valid without revision inference.

#### Codec APIs

The public facade exposes exactly these operations:

```scala
ComponentCapabilityProjectionCodec.decode(json: String): Either[Diagnostic, Projection]
ComponentCapabilityProjectionCodec.validate(projection: Projection): Either[Diagnostic, Projection]
ComponentCapabilityProjectionCodec.encode(projection: Projection): Either[Diagnostic, String]
```

`validate` checks the same raw contract as decode. `encode` MUST validate
before emitting compact JSON. Manually constructed metadata MUST NOT bypass
structural, scalar, source, envelope or duplicate checks.

### R12 Executable metadata admission

Admission consumes explicit immutable acquisition and selected scope outcomes.
It MUST NOT discover global names, synthesize scope facts from unresolved
edges, scan classes or infer currentness from Foundation digests.

#### Scope, outcome and admitted carriers

All fields below are explicitly supplied; no empty/provenance defaults exist.
`Scope.capabilities` contains supplemental already acquired/admitted external
definitions and may include multiple versions acquired from separate snapshots.

| Scala carrier | Fields / alternatives |
| --- | --- |
| Diagnostic | `kind: DiagnosticKind`, `detail: String`, `scopeId: Option[String]`, `component: Option[ComponentId]`, `source: Option[Source]`, `location: String` |
| DiagnosticKind | `Malformed \| Unsupported \| Ambiguous \| Incompatible \| Unavailable \| Unauthorized \| Stale` |
| SourceInput | `Acquired(scopeId: String, json: String)` or `Rejected(diagnostic: Diagnostic)` |
| ScopeInput | `Ready(scope: Scope)` or `Rejected(diagnostic: Diagnostic)` |
| Scope | `scopeId: String`, `source: Source`, `modelFacts: Vector[ModelFact]`, `termFacts: Vector[TermFact]`, `relationFacts: Vector[RelationFact]`, `profileFacts: Vector[ProfileFact]`, `capabilities: Vector[CapabilityDefinition]`, `targetContracts: Vector[TargetContract]` |
| ModelFact | `identity: ModelElementId`, `kind: ModelKind`, `source: Source` |
| TermFact | `identity: TermId`, `source: Source` |
| RelationFact | `identity: RelationId`, `source: Source` |
| ProfileFact | `identity: ProfileId`, `source: Source` |
| ModelKind | `DOMAIN_CONCEPT \| GOAL \| USE_CASE \| SCENARIO \| USE_CASE_SLICE \| COMPONENT \| COMPONENT_CAPABILITY \| APPLICATION_CAPABILITY \| SPECIFICATION \| EVIDENCE \| OPERATION \| WORKFLOW \| STATE_MACHINE` |
| TargetContract | `targetKind: TargetKind`, `identity: ModelElementId`, `component: ComponentId`, `contractVersion: String`, `generatedAbiVersion: String`, `source: Source` |
| Admitted | Private construction; exposes original `projection: Projection`, selected `scopeId: String`, combined bound `capabilities: Vector[CapabilityDefinition]`, `realizations: Vector[ResolvedRealization]` |
| ResolvedRealization | Private construction; exposes original `edge: Realization` and exact selected `target: TargetContract` including independent target source/revision |

`TargetKind` is the Projection's single three-kind domain, reused by scope
target contracts. Model/capability/target sources MUST match their `modelId`;
vocabulary facts MUST match their `vocabularyId`. Scope data uses the same
literal ID/source validity as R11.

#### Exact resolution and binding

Each declaration reference MUST resolve uniquely against own plus supplemental
definitions. No identity candidate is `Malformed`; no requested exact version
is `Incompatible`; multiple exact identity/version candidates are `Ambiguous`.
Duplicate full supplemental/combined identity-version candidates MUST fail,
without first-wins selection. A snapshot's own repeated identity and a repeated
full `(ComponentId, direction, qualified Capability identity, contractVersion)`
declaration key are `Ambiguous`, including conflicting sources.

Local Model/Term grounding MUST resolve uniquely in selected facts, with own
and supplemental Capability model identities available to Model grounding.
Multiple contract versions are not different model identities. Local supplied
relation/profile references MUST resolve in their respective vocabulary
domains. Missing candidates are `Malformed` and multiple candidates
`Ambiguous`. Local ModelLinks MUST match their full role; Local Traceability
MUST match Specification/Evidence kind. A different kind is `Incompatible`.
External grounding/discovery/traceability remains external even if the same
identity is also present locally; unknown external vocabulary or model data
does not require local availability or ontology completion.

Every nonempty realization MUST bind a supplied admitted target contract
before Admitted construction. Select by exact target Component and qualified
model identity, then explicit target kind, exact target contract version and
target ABI. No identity candidate is `Malformed`; wrong kind/version/ABI is
`Incompatible`; duplicate/full or multiple exact matches are `Ambiguous`.
External boundary MUST NOT waive this binding. Local realization uses these
target facts directly without requiring redundant general model facts.
Present-empty or exact Absent realization remains an architectural fact with
no fabricated executable target. The pure binding primitive is CAP-78-02;
adapters to actual existing target contracts and provenance proof are CAP-78-03.

Compatibility endpoints MUST each resolve uniquely to a same-identity
definition at their explicit versions. Dangling identity is `Malformed`,
wrong version `Incompatible`, duplicate directed edge or candidate `Ambiguous`.
Redundant self-version edges MAY remain explicit. The original source and
origin are retained for each exact required-to-provided edge.

```scala
ComponentCapabilityAdmission.admit(projection: Projection, scope: ScopeInput): Either[Diagnostic, Admitted]
ComponentCapabilityAdmission.admitJson(input: SourceInput, scope: ScopeInput): Either[Diagnostic, Admitted]
ComponentCapabilityAdmission.isCompatible(admitted: Admitted, required: CapabilityReference, provided: CapabilityReference): Boolean
```

`admit` validates raw metadata and the exact selected ready scope before
construction. `admitJson` consumes acquisition then scope outcomes. Rejected
diagnostics MUST be returned unchanged, including Unsupported, Unavailable,
Unauthorized or Stale. Acquired and ready scope IDs MUST agree exactly or
produce `Incompatible`. Empty metadata can succeed only as acquired metadata
in an explicit ready scope. Failure MUST NOT become empty metadata or a
`NoProvider` answer.

`isCompatible` first requires both exact references to exist uniquely in the
admitted definitions. It is true only for equal qualified identity and either
equal versions or one supplied direct edge in that projection. Reverse,
transitive, SemVer, cross-ID, label/name and provider-order inference are false;
even equal unknown references are false. This API offers Component metadata,
not a CBD catalog/query result.

#### Diagnostic attribution

Diagnostics MUST retain typed kind, human detail, a JSON-Pointer-style location
(`/` at the root), and only known exact scope/Component/Source context.
Nested field/array locations MUST survive enclosing context enrichment.
Source line/revision MUST NOT be invented for parse failures. Duplicate JSON
keys and syntax errors are `Malformed` at the nearest available parse/root
location. Stale/unauthorized are externally supplied observed outcomes; this
package implements no source-currentness certificate, revision comparison,
hashing, network, permission acquisition, retry or lock policy.

Specification and Evidence retain independent typed roles/source/origin.
Presence asserts neither execution nor coverage, availability or authorization.
Missing Application demand cannot replace required Component metadata.

#### Working specifications and conformance input

The [codec specification](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityProjectionCodecSpec.scala)
and [admission specification](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityAdmissionSpec.scala)
provide the behavioral cases below. The portable
[v1 resource](../../src/test/resources/component-capability/projection-v1.json)
is hand-authored external-contract conformance input. Its supplemental older
definition and exact model/vocabulary/target facts are independently supplied
in the codec specification with explicit revisions. The resource is neither
Cozy-produced nor evidence of actual target adapter, Factory/CAR or CBD
integration. CAP-78-02 validation and independent acceptance are complete;
participant Phase release validation and full review remain separate.

| Case ID | Observable contract | Rules |
| --- | --- | --- |
| CPJ-01 | UTF-8 resource decode/admit/encode/decode/admit retains the complete external contract and independent ordered sources. | R1, R4, R5, R11, R12 |
| CPJ-02 | Active ScalaCheck literal-ID roundtrip preserves case, Unicode composition, delimiters and distinct equal-label definitions. | R2, R11 |
| CPJ-03 | All Presence reasons/details, present-empty and explicit line/language null survive. | R5, R6, R9, R11 |
| CPJ-04 | Missing/extra/null/type/mixed nested branches fail with owning pointers and known source/Component. | R11, R12 |
| CPJ-05 | Root/nested duplicate keys and malformed JSON fail without fabricated source context. | R11, R12 |
| CPJ-06 | Future schema/ABI precedence and malformed schema types cannot downgrade. | R4, R11 |
| CPJ-07 | Unknown closed tags and Application family are Unsupported; malformed text or keys fail. | R1, R9, R11 |
| CPJ-08 | Explicit source revision, canonical path/line and valid text cannot be replaced by legacy digest or normalization. | R2, R5, R11 |
| CPJ-09 | Raw validate/encode/admit and equivalent JSON refuse the same invalid contract. | R5, R11, R12 |
| CPA-01 | Two exact Components and provided/required declarations remain distinct without provider preference. | R2, R3, R12 |
| CPA-02 | Duplicate definition identity/full declaration key fails even with changed version/name/source. | R2, R3, R11, R12 |
| CPA-03 | Envelope Component and definition/declaration/edge source ownership are exact. | R3, R5, R11 |
| CPA-04 | Missing/wrong-version/duplicate supplemental Capability outcomes differ. | R2, R3, R12 |
| CPA-05 | Local grounding/vocabulary resolves uniquely; External and declared/derived facts stay explicit. | R5, R6, R12 |
| CPA-06 | Model/vocabulary/target scope fact identity and source-authority validity is enforced. | R2, R5, R6, R12 |
| CPA-07 | All discovery roles and independent Specification/Evidence Local kinds bind without execution claims. | R9, R12 |
| CPA-08 | All three target kinds bind independently for Local/External with both sources retained. | R4, R5, R7, R12 |
| CPA-09 | Unknown/duplicate/wrong-kind/version/ABI targets fail for every target kind and boundary. | R7, R12 |
| CPA-10 | Repeated-key conflict differs from explicit empty/absent realization. | R3, R7, R12 |
| CPA-11 | Known exact equality and authored old-to-new direct compatibility preserve independent version domains. | R4, R5, R8, R12 |
| CPA-12 | Reverse/transitive/SemVer/name/cross-ID/unknown inference stays false; active ScalaCheck declaration/fact order invariance. | R2, R3, R8, R12 |
| CPA-13 | Compatibility dangling/wrong-version/duplicate edge or candidate fails with exact owner. | R8, R12 |
| CPA-14 | Ready acquired empty succeeds; every rejected diagnostic is forwarded unchanged. | R1, R9, R10, R12 |
| CPA-15 | Acquisition and ready scope IDs must agree. | R10, R12 |
| CPA-16 | Stable Capability identity/version retains explicitly changed edge/target revision and exact replacement binding. | R2, R4, R5, R7, R8, R12 |

### R13 Actual typed target adapter

This internal `private[cncf]` boundary receives immutable metadata already
admitted by its Operation, Workflow or StateMachine owner. It MUST NOT acquire
a runtime Component, invoke a Factory, read sources or select a provider.
R11 transport and R12 admission APIs and semantics remain unchanged.

#### Explicit acquired carriers

All fields are explicit, with no defaults. Constructors record caller-supplied
facts; they do not authenticate acquisition, currentness or permission.

| TargetSource carrier | Fields / alternatives |
| --- | --- |
| AcquisitionInput | `Acquired(scopeId: String, snapshots: Vector[Snapshot])` or `Rejected(diagnostic: Diagnostic)` |
| Snapshot | `component: ComponentId`, `source: Source`, `operations: Vector[CmlOperationDefinition]`, `workflows: Vector[WorkflowDefinition]`, `stateMachines: Vector[CmlStateMachineDefinition]` |
| SnapshotKey | `component: ComponentId`, `modelAuthority: String`, `path: String`, `sourceRevision: String` |
| Binding | `snapshot: SnapshotKey`, `identity: ModelElementId`, `targetKind: TargetKind`, `selector: String`, `contractVersion: String`, `generatedAbiVersion: String`, `source: Source` |
| Contract | Scala 3 enum `Operation(definition: CmlOperationDefinition)`, `Workflow(definition: WorkflowDefinition)`, `StateMachine(definition: CmlStateMachineDefinition)` |

`Snapshot.key` MUST preserve the exact Component and source authority/path/
revision tuple, excluding optional line. Actual metadata vectors and their
objects MUST retain their order. Each binding supplies the qualified model
identity independently of the native declaration selector. The selector is
the exact declaration `name` within one selected snapshot and target kind;
it MUST NOT issue, parse, normalize or substitute for model identity.

Binding identity authority MUST equal binding source authority. Binding source
authority, path and revision MUST equal the selected snapshot source; its
positive line or None is independent of snapshot line. Contract version and
target ABI MUST be separately observed opaque caller-supplied values. Native
StateMachine numeric version, Workflow ID, source revision, producer,
Component/package version and digests MUST NOT derive those values. The
metadata types have no uniform contract revision/ABI to extract or externally
verify. Actual acquisition remains the producer/framework caller's duty.

#### Validation and exact selection

Acquisition Rejected MUST take precedence over scope Rejected; the original
diagnostic MUST be returned unchanged even when subsequent input is malformed.
Acquired/Ready MUST validate the original scope under R12 before replacing
targets. Both scope IDs MUST satisfy R11 text validity and agree exactly;
disagreement is `Incompatible`. Original `scope.targetContracts` MUST be empty
or fail `Malformed` at `/scope/targetContracts`.

Every snapshot and binding in the batch MUST be valid before any Adapted
result is constructed. Null carriers/vectors/elements, invalid Component,
Source, key, qualified identity and text are `Malformed` at their owning
`/acquisition` or `/bindings` pointer. Every native definition name MUST satisfy
R11 literal text validity. This is no second procedural/runtime admission.
Selected Operation `kind`, `inputType`, `outputType`, `inputValueKind` MUST be
valid text. Selected Workflow MUST have a nonnull ID, registrations vector and
entries. Selected StateMachine MUST have `Some(nonnull normalized)` with
nonnull identity, matching name and nonnull positive version. Legacy
`normalized=None` is `Unsupported` at the selected actual contract path; the
adapter MUST NOT invent version 1 or rewrite normalized graphs.

Repeated exact SnapshotKey is `Ambiguous` at the second
`/acquisition/snapshots/i`, including equal data or different lines. A binding
with no exact snapshot is `Unavailable` at `/bindings/i/snapshot`. With one
snapshot, no same-kind selector is `Incompatible` at `/bindings/i/targetKind`
if the literal selector exists in another kind, otherwise `Unavailable` at
`/bindings/i/selector`. Multiple same-kind candidates are `Ambiguous`, even
for equal objects. No deduplication, first/last selection or global fallback
is permitted.

Repeated complete `(component, identity, targetKind, contractVersion,
generatedAbiVersion)` context MUST fail `Ambiguous` at the second binding.
Repeated native `(SnapshotKey, targetKind, selector)` MUST also fail
`Ambiguous`, even with different model IDs/versions. Independent snapshots
may retain distinct historical versions. Unselected native metadata MUST NOT
receive invented model identity. One adapted target MAY serve multiple edges.

Diagnostics MUST retain their original nested pointer and only known exact
scope, Component and Source context. No missing line/revision is fabricated.
Failure MUST NOT yield partial targets, empty success, retry or downgrade.

#### Private accepted states and R12 binding

Only the adapter facade constructs these final classes; support parts return
validated data/tuples rather than acceptance:

| Adapter carrier | Exposed retained values |
| --- | --- |
| Adapted | Original `acquisition: Acquired`, original `scopeInput: ScopeInput.Ready`, supplied `bindings: Vector[Binding]`, `scope: Scope`, `targets: Vector[AdaptedTarget]` |
| AdaptedTarget | Original `snapshot: Snapshot`, original `binding: Binding`, actual `contract: Contract`, R12 `target: TargetContract` |
| Bound | Original `admitted: Admitted`, original `adapted: Adapted`, ordered `realizations: Vector[BoundRealization]` |
| BoundRealization | Original `realization: ResolvedRealization`, selected actual `target: AdaptedTarget` |

Adapted targets MUST follow supplied binding order. R12 TargetContract MUST
use the actual snapshot Component and binding kind, identity, opaque contract
version, ABI and Source. The effective scope copy MUST replace only
`targetContracts`, retaining every other original fact, supplemental
definition, source and order. Explicit empty acquired snapshots/bindings and
a valid Ready scope MAY produce empty targets; they are no acquisition-failure
fallback or `NoProvider` evidence.

```scala
ComponentCapabilityTargetAdapter.adapt(acquisition: AcquisitionInput, scopeInput: ScopeInput, bindings: Vector[Binding]): Either[Diagnostic, Adapted]
ComponentCapabilityTargetAdapter.admit(projection: Projection, adapted: Adapted): Either[Diagnostic, Bound]
ComponentCapabilityTargetAdapter.admitJson(input: SourceInput, adapted: Adapted): Either[Diagnostic, Bound]
```

`admit` and `admitJson` MUST delegate unchanged R12 with the effective scope.
SourceInput.Rejected MUST be returned unchanged even if adapted is null;
otherwise null adapted is `Malformed`. Only after successful original
admission does each ResolvedRealization target join by the complete exact
TargetContract tuple, including source, to exactly one AdaptedTarget. No match
is a `Malformed` internal input inconsistency; multiple matches are
`Ambiguous`. Bound preserves original admitted edge order, including Local
and External edges; External has no target-binding waiver. Raw codec,
identity, absence and traceability semantics remain R11/R12.

Procedure, command/job/execution fields, workflow registrations/status rules
and normalized StateMachine guards/actions stay on the original actual
metadata objects. The adapter MUST NOT extract them into TargetContract or
Capability, create an engine/resolver, invoke operations/events/threads,
perform reflection/ClassLoader/network/source IO or parse CML. Only existing
R11 decode handles JSON. Independent Specification/Evidence roles, sources and
origins remain R12 traceability; they assert no execution, coverage,
authorization or admission authority.

#### Actual target working specifications

The [adapter specification](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityTargetAdapterSpec.scala)
and [binding specification](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityTargetBindingSpec.scala)
cover these observable cases. Focused validation passed and independent
protected Step conformance accepted CAP-78-03 on 2026-10-14, as recorded in
[Phase 78](../phase/phase-78.md#cap-78-03-acceptance).
Typed fixtures establish no normal Cozy production,
Factory/package discovery or CBD ingestion proof.

| Case | Observable contract | Requirements |
| --- | --- | --- |
| CPT01 | Three actual families, two Components and binding order retain original objects. | R13 acquisition, private states |
| CPT02 | Model identity stays independent of same native selector across snapshots. | R2, R13 exact selection |
| CPT03 | Snapshot, binding and later edge sources retain independent lines/revisions. | R5, R13 provenance |
| CPT04 | Opaque contract and ABI values stay independent of native versions/IDs. | R4, R13 acquisition |
| CPT05 | Original rejected scope is unchanged before malformed bindings. | R10, R13 precedence |
| CPT06 | Original rejected acquisition precedes malformed scope/bindings. | R10, R13 precedence |
| CPT07 | Scope IDs agree exactly or fail at owning pointer. | R13 validation |
| CPT08 | Malformed carriers fail without partial targets; valid Unicode remains literal. | R11, R13 validation |
| CPT09 | Repeated snapshot keys fail at the second snapshot. | R13 uniqueness |
| CPT10 | Missing snapshot or exact native selector is Unavailable. | R13 exact selection |
| CPT11 | Wrong native family is Incompatible. | R13 exact selection |
| CPT12 | Repeated same-kind selectors stay Ambiguous. | R13 uniqueness |
| CPT13 | Repeated complete target contexts stay Ambiguous. | R13 uniqueness |
| CPT14 | Native aliases fail; independent historical snapshots stay distinct. | R13 uniqueness |
| CPT15 | Explicit acquired/ready empty retains its originals. | R10, R13 private states |
| CPT16 | Synthetic scope targets cannot bypass actual selection. | R13 private states |
| CPT17 | Legacy unnormalized StateMachine is Unsupported. | R13 actual contract |
| CPT18 | Normalized states/transitions/source/version remain original. | R13 actual contract |
| CPT19 | Conflicting source or malformed selected contract fails. | R5, R13 validation |
| CPT20 | Catalog enumeration cannot change exact selection or author order. | R13 exact selection |
| CPT21 | Active literal identity property: at least 100 successes, zero discards. | R2, R13 exact selection |
| CPT22 | Active source/catalog permutation property: at least 100 successes, zero discards. | R5, R13 exact selection |
| CPT23 | Raw R12 admission binds all three actual families in original edge order. | R12, R13 binding |
| CPT24 | Existing hand-authored strict wire binds independently built actual metadata. | R11, R12, R13 binding |
| CPT25 | Original schema/ABI/raw/scope failures survive without partial Bound. | R11, R12, R13 binding |
| CPT26 | Local and External realizations both require actual targets. | R12, R13 binding |
| CPT27 | Present-empty and exact absent realizations retain zero bound edges. | R7, R12, R13 binding |
| CPT28 | Independent Specification/Evidence links retain R12 kind/failure rules. | R9, R12, R13 traceability |
| CPT29 | Wrong Component/model/kind/version/ABI fails for each native family. | R12, R13 binding |
| CPT30 | Invalid duplicate/grounding/declaration admission produces no partial Bound. | R12, R13 binding |
| CPT31 | JSON Rejected is unchanged even with null adapted; acquired/null is malformed. | R13 precedence |
| CPT32 | Native procedure variations stay on actual contracts; mapping remains architectural. | R7, R9, R13 nonexecution |

## Semantic acceptance matrix

These cases specify expected interpretations of the rules. They do not
record executed tests, admitted real fixtures, or Step acceptance.

| ID | Case | Expected semantic outcome | Rules |
| --- | --- | --- | --- |
| E1 | Two distinct exact Components provide one qualified Capability/version. | Retain both distinct Component declaration edges; do not select by enumeration order. | R2, R3, R8 |
| E2 | One Component provides and requires the same reference; the complete tuple is then duplicated. | Retain distinct PROVIDED/REQUIRED declarations; reject the duplicated complete tuple. | R3 |
| E3 | Required and provided contract versions differ. | Match only through a supplied admitted directed edge from required to provided version of the same identity; different identities never match. | R2, R8 |
| E4 | Realization changes while the Capability contract remains explicitly satisfied. | Retain stable identity with explicit target/source revision; reject incompatible target ABI separately. | R5, R7, R8 |
| E5 | Admitted model/term grounding includes local/external and declared/derived provenance, with grounding absent where not supplied. | Preserve exact qualified relations, absence, and edge provenance without ontology synthesis. | R5, R6 |
| E6 | Equal labels have different qualified identities, or declarations are duplicate/conflicting. | Do not merge semantics; report duplicate/ambiguity diagnostics rather than resolve by collection order. | R2, R3 |
| E7 | A declared realization target dangles, compared with an explicitly empty realization set. | Reject the dangling declaration; retain the admitted empty set and allow a distinct MissingRealization gap when needed. | R7, R10 |
| E8 | Source is unavailable, unauthorized, or stale, compared with an admitted empty scoped catalog. | Preserve separate failure/availability diagnostics; permit NoProvider only from the successfully acquired admitted scope. | R5, R10 |
| E9 | Specification/Evidence references are present without execution evidence. | Reference presence does not assert execution, coverage, availability, or authorization; preserve independent edges. | R9 |
| E10 | Projection version is unsupported, or version domains are confused. | Return typed unsupported/inconsistent admission; do not downgrade or default ABI/source revision. | R4, R5, R10 |

## Implementation ownership

The work boundaries in [Phase 78](../phase/phase-78.md) own implementation,
validation and separately accepted executable evidence:

| Step | Responsibility |
| --- | --- |
| CAP-78-02 | Accepted concrete R11/R12 wire, codec, exact ABI set and pure selected-scope admission. |
| CAP-78-03 | Accepted R13 actual typed target adapters and CPT01..32 executable specifications, with focused validation and independent protected Step conformance. |
| CAP-78-04 | Implement runtime-independent ComponentFactory and package/CAR exposure and discovery with executable proof. |
| CAP-78-05 | Prove a real Cozy-generated fixture through CNCF admission/discovery and Textus CBD Support consumption with exact provenance. |

Cozy Phase 64 owns syntax, IR, normalization, and generated producer output.
Textus CBD Support Phase 11 owns consumer ingestion, catalog, and queries.
Their Step and Phase acceptance remain separately owned. This specification
selects no canonical publication resource filename, package/CAR surface or
running API. The portable test resource above is not a publication choice.
