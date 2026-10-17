# Display Model runtime design

Document role: selected Phase 96 architecture decisions.
Status: implementation contract; P96-01/02/03/04/05 accepted within their scoped boundaries.
Independent P96-01C documentary review and P96-03 whole-Step review passed;
all seven P96-03 criteria are CLOSED with focused LUI-ACCEPT-01 consumer proof
accepted at local P96-03 Step commit `bef59e7`; Cozy P76-05 reciprocal receipt
is accepted at `3546755` and P76-06A documentary handoff at `ddeadab9`.
The pure P96-04 codec is accepted at local Step commit `625d533`. P96-05
read-service validation and independent acceptance are complete in the
[Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance).
Its local Step commit is PREPARED/PENDING; later mutation/context work and final
Phase acceptance remain pending.
Scope: PHASE-96 / P96-01C / P96-03 / P96-04 / P96-05A.

The behavior contracts are [Logical UI generated ABI](../spec/logical-ui-generated-abi.md)
and [Display Model runtime](../spec/display-model-runtime.md). These decisions
extend the existing [Aggregate/View semantic boundary](aggregate-view-semantic-boundary.md),
without changing its authority. Promotion history belongs to the
[promotion journal](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md).

## Semantic and presentation ownership

Entity remains the source of truth; Aggregate remains the canonical write
boundary. A semantic View is independently consumable as application-typed
`V`, `Vector[V]` or `SearchResult[V]`. Display projection adds neither a second
store nor a public generic Aggregate/View runtime gateway. Domain and semantic
clients need no Display representation to consume the same View.

Static Logical UI definitions, contextual semantic View values and runtime
Display instances have separate ownership and lifetime. Cozy Phase 74 owns
the target-neutral semantics. Cozy Phase 76 produces deterministic Scala 3
source against CNCF's consumer ABI; CNCF owns admission and runtime projection.
There is no `cozy_2.12` runtime dependency, special integration cross-build,
copied Cozy semantic authority, runtime CML parser or reflection-based
generator/runtime bridge.

## Component admission and explicit bindings

The Component owns definitions and lookup. Bootstrap selects exactly one
provider, atomically admits its complete static vector, registers required
Views and application bindings, joins explicit typed bindings, and only then
exposes Display lookup/service. Component-first discovery and the no-fallback
policy are specified in [LUI96-S06/S07](../spec/logical-ui-generated-abi.md#lui96-s06-provider-selection).
Static admission may precede entity/View creation; binding join must wait for
those registrations. No partial registry or JVM-global mutable registry is
permitted, and existing Workflow/StateMachine admission remains unchanged.

Stable view/value/action binding IDs name application registration keys only.
They do not name properties, classes, routes or guessed setters. Applications
register typed sources and converters; no speculative field-name discovery,
generic DI engine or expression language is introduced. Explicit conversions
may reshape, combine or fan out, but only into declared fields and admitted
kinds, preserving deterministic order and all scoped constraints. Mutation
target registration is distinct from read conversion. A derived or combined
value has no inverse write unless separately and explicitly registered.

## Service and Operation boundary

One explicitly registered, component-scoped service has selector `display`.
Its canonical read Operations are `query` and `read`, without `list/get`
aliases. A conflicting service or duplicate Operation fails bootstrap rather
than replacing an earlier registration. With no declared definitions there
is no Display service. Ordinary canonical Operation selection, path resolution
and authorization remain authoritative; physical binding belongs to P96-04.

Runtime determines read/query/create/update/delete capability and current
availability under current authorization. Presentation values and inert
Actions cannot grant permission. Hidden, disabled and enabled action presence
is contextual data. Standard create/update/delete use the explicit canonical
mutation bridge; Business Operations execute directly through ordinary
Operations with their own input and authorization, followed by client-initiated
Display reload. Display is not a generic action tunnel, push/subscription
channel or Service Bus refresh mechanism.

## Canonical mutation authority and outcomes

Existing domain schema/constraints, Entity/Aggregate lifecycle, authorization,
managed revision, UnitOfWork, transaction and provider capability contracts
remain authoritative. Display adapters resolve admitted application mappings;
they never mutate datastores directly, patch Views or implement local-lock
concurrency. Unsupported atomic capability fails before effects. Strict
observed-revision delete requires authorized comparison and deletion inside
the canonical atomic boundary, not a check followed by ordinary delete.

The [runtime contract](../spec/display-model-runtime.md#dsp96-r06-canonical-mutation-and-concurrency)
fixes update's `WriteIfChanged + ObservedRequired`, stale-before-equality and
no-op policy, and separates rejection, committed-ready, committed-pending
projection and indeterminate acknowledgment. A committed projection failure
does not roll back the write or authorize replay. Existing non-Display APIs
and defaults remain unchanged.

## Identity and compatibility spaces

Version generated ABI, the exact Phase 74 semantic descriptor, each definition,
generated-source provenance, Display wire schema/version, Entity revision and
snapshot/correlation independently. Screen IDs are opaque presentation IDs;
runtime resource identity remains typed EntityId. Neither screen IDs nor
provenance issue resource identity. Definition/provenance revision cannot
substitute for observed EntityRevision, and a query snapshot is not a mutation
precondition. Transport encoding is a separate P96-04 decision.

## Producer/consumer handoffs

The existing [boundary handoff table](../notes/logical-ui-generated-abi-display-runtime-boundary.md#cross-repository-handoff-records)
defines this ordered development chain and its owners:

| Handoff | Owner -> consumer | Required evidence |
| --- | --- | --- |
| LUI-ABI-01 | CNCF -> Cozy | Fixed consumer revision and usable source/artifact coordinates, ABI version, public types/provider signature, semantic mapping and unsupported subset, hand-written expected-generated fixture, admission diagnostics and focused projection proof |
| LUI-GEN-01 | Cozy -> CNCF | Fixed producer revision and generator command, Phase 74 source fixture/provenance, target LUI-ABI-01 identity, actual generated Scala/provider files and deterministic producer evidence |
| LUI-ACCEPT-01 | CNCF -> both ledgers | Exact LUI-GEN-01 source compilation against the matching ABI, normal ComponentFactory admission, equal List/Detail values/IDs/order, negative admission cases and runtime-classpath boundary evidence |

Every receipt must identify exact revisions, source/artifact coordinates and
actual commands/results. Documentary promotion issues no receipt. A proposal
or hand-written fixture alone cannot prove actual generated acceptance.
LUI-ABI-01 does not wait for whole Phase 96 closure or public publication;
LUI-ACCEPT-01 is shared focused P96-03/Cozy P76-05 evidence. There is no mutual
whole-Phase closure dependency. Repository release validation remains separate.

## Consumer scope

Editing Studio Phase 2 owns application semantics; app Phase 4 owns prototype
feasibility and app Phase 5 owns real-server aggregate/E2E acceptance. They are
separately owned from CNCF and Cozy acceptance. This documentary slice admits
no production app/Entity contract or additional product root. ESS2-01 remains
a development-only reference with unsupported create/delete; separate positive
create/delete proof remains required. Context details remain P96-10, and
Phase 98 retains experiment allocation, observation and reward ownership.

## P96-03B1: Pure typed projection registration

[DisplayProjection](../../src/main/scala/org/goldenport/cncf/display/DisplayProjection.scala)
owns one bounded application registration and pure projection responsibility.
It consumes already-authorized typed semantic values rather than acquiring a
View. The separate Detail and List factories retain `V` across resource,
value and action callbacks, while List-screen actions retain `Vector[V]`.
They produce admitted immutable projectors with private constructors and no
copy bypass. No erased heterogeneous bridge or parallel View store is needed.

Registrations are screen-specific because ESS List and Detail share a View
key but declare different outputs. Explicit application functions can be
reused without admitting a View-wide superset of undeclared converter keys.
The pure admission pass first checks the complete structural registration;
compiled value/action order follows each key's first declared occurrence.
One converter may return several keys in its own explicit positional order.
The projector maps those outputs back to all declared locations, preserving
schema order and shared-key reuse across sections and separate action scopes.

Source and snapshot shape checks precede callback invocation. Resource,
compiled value groups and row actions run sequentially in supplied row order;
screen actions then consume the original complete vector even for zero rows.
Sequential Consequence composition stops on callback failure, exception,
null result or output-count defect. Final candidate assembly delegates all
runtime kind/role/title/payload/resource uniqueness checks to the unchanged
`Display.createC`, preserving that validator's structured diagnostics.
The projection-specific cause carrier uses fixed redacted reasons and indexed
paths and retains callback Failure causes without reinterpretation.

Callbacks are application-owned pure functions over immutable values and
already-authorized context. The framework cannot prove arbitrary closure
purity; deterministic typed fixtures document the obligation and availability
remains inert. Conversion does not infer an inverse write or execute an action.
Typed resource identity/revision and supplied query snapshot are retained
without deriving them from screen or producer provenance.

The [behavior suite](../../src/test/scala/org/goldenport/cncf/display/DisplayProjectionSpec.scala),
[property suite](../../src/test/scala/org/goldenport/cncf/display/DisplayProjectionPropertySpec.scala)
and [external fixture](../../src/test/scala/logicalui/TypedDisplayProjectionFixture.scala)
are authored B1 evidence with historical parent focused validation:
`phase96-p96-03b1-test-002`, seven suites and 67 successful tests, E1..E11 and
P1/P2 with at least 100 successes each, SBT/wrapper exits 0 and shared-lock
release. The finite-permutation generator and compile type qualification were
the two pre-review authoring repairs. No independent B1 acceptance is implied.
C still owns actual producer files through compile/bootstrap/equal projection.
All handoff receipts and whole P96-03 acceptance remain PENDING.

## P96-03B2: Contextual capability closure and late publication

The [contextual runtime contract](../spec/display-model-runtime.md#p96-03b2-component-scoped-contextual-typed-browser-binding)
selects one component-local closed capability vector. Private typed
DetailBinding/ListBinding instances retain `Browser[V]` beside `RowBinding[V]`
and List's `Vector[V]` action registrations, then compile directly to matching
private DetailEntry/ListEntry instances. The heterogeneous container hides
only executable capability, never transfers an untyped semantic value across
a boundary. Public candidates have no source execution methods and Registry
has no public construction or copy bypass.

Source ownership uses [ViewSpace.containsBrowser](../../src/main/scala/org/goldenport/cncf/entity/view/ViewSpace.scala):
reference identity in default or named Browser values, without extracting
`V`, normalizing names, querying, counting, or adding storage. This proves
that an application supplied the canonical typed handle already registered in
the target Component. A binding key need not be a View lookup name. Deliberate
shared registration remains lawful; another Component's unregistered handle
does not become a source through equal IDs or values.

Static admission continues before legacy semantic setup. Runtime provider
selection waits until existing entity/Aggregate/View, working-set, planner and
event setup has completed. The application getter can finish its explicit
typed handle registrations at that point. The entire runtime compiles before
the final definitions/runtime/flag publication tail, so failed late binding
exposes neither a prefix registry nor admitted definitions nor the flag.
Existing setup effects are not rolled back. A volatile optional registry
reference publishes one immutable complete value; it does not add concurrent
bootstrap support. Existing single-owner lifecycle and successful repeat
identity remain the operating assumptions.

Registry dispatch is total and confined to admitted screen IDs. Typed Entries
call the selected Browser's contextual method once with the same input and
context, then call B1 projection outside the source exception boundary.
Detail verifies requested resource identity after projection. List's canonical
Browser supplies only a vector, so no count or query snapshot is invented.
NonFatal source exceptions receive fixed redacted diagnostics; structured
source and projector failures retain their own meaning and cause. No retry,
cache, action execution, generic View gateway, authorization grant or new
Display service is introduced in this Slice.

The new [runtime suite](../../src/test/scala/org/goldenport/cncf/display/DisplayRuntimeSpec.scala)
and [bootstrap suite](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryDisplayRuntimeSpec.scala)
are authored evidence with validation/review PENDING. The bootstrap fixture
uses existing ViewBootstrapPerson persistence and a typed EntityCollection;
rejected duplicate registration observes a real factory-installed default
View without unsafe typed map lookup. The external runtime fixture retains
typed Capture and unrelated Badge values and explicitly registers handles.
It makes no production Entity or actual generated-source claim.

The parent-selected fifteen-suite accumulator must validate this changed
bootstrap boundary. Actual producer C and all three LUI receipts remain
PENDING. The next parent PLAN prepares LUI-ABI-01 from completed A/B evidence
before actual C work. No B2 documentary section closes a Step or checklist.

## P96-03C: Delivered source and application consumer boundary

The actual generated consumer uses the same public ABI, component-first
metadata selection, late typed join and contextual Registry route established
above. Two unchanged source files from Cozy's accepted P76-04 LUI-GEN-01
delivery compile in CNCF's ordinary Test configuration. Application bindings
remain external development fixtures; no runtime Cozy import, build change,
producer implementation, erased semantic bridge or alternate admission route
is introduced. Actual and handwritten expected consumers own independent
typed Browser handles over identical Capture inputs. Semantic models are
compared structurally; ABI, semantic descriptor, screen identity, definition
revision, View key and screen are compared separately, with each producer's
provenance asserted independently.

The [generated consumer specification](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedLogicalUiAcceptanceSpec.scala)
documents GC1..GC6 and GCP1 against these unchanged decisions. Fixture-only
negative vectors never modify delivered definitions. Failed complete Display
admission publishes no metadata, runtime or flag, while existing legacy setup
effects retain their original lifecycle meaning. The
[consumer journal](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md)
holds source mappings, producer revision/history, historical planned validation
and later actual parent-admitted current results. Compile/Test and GC1..GC6/GCP1
passed 7/7 tests in one suite; the unchanged 15-suite accumulator passed
133/133 tests. Both commands retain terminal SBT/wrapper exit 0 and released
serial lock. Source comparisons exited 0, and complete existing Scala3.3.8
Test classpath inspection found no cozy_2.12 runtime jar. Ordinary CNCF build
execution included its pre-existing Information CML generator hook, without
explicitly rerunning the Phase76 ESS2-01 Logical UI producer. Accepted producer commit is
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`, coordinate
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, Scala 2.12.18.

LUI-ABI-01 is issued and P76-04 LUI-GEN-01 producer delivery is accepted.
Independent protected-focused whole-Step review
`phase96-p96-03-step-review-001` revision 1 by
`/root/phase96_p96_03_step_review_001` passed all 28 paths and 19 programs;
parent admission is recorded in `phase96-review-ledger-001` revision 19.
All seven P96-03 criteria are CLOSED, including criterion 7's accepted
LUI-ACCEPT-01 focused consumer proof, recorded in the
[acceptance entry](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md#2026-10-04--p96-03-independent-step-acceptance).
Local Step commit is `bef59e7`. Cozy P76-05 reciprocal receipt is accepted at
`3546755`; P76-06A documentary handoff is accepted at `ddeadab9`. Zero blockers and nonblocking
HYG-P96-03-SPEC-TRACEABILITY-001 are retained. Historical A/B1/B2 pending statements
above describe their earlier handoff position. Final Phase acceptance and later
At P96-03 acceptance, DisplayService, mutation and presentation-context work remained pending;
no full Phase review or either Phase closure is claimed.

The [physical protocol design](display-model-protocol.md) and authoritative
[read protocol specification](../spec/display-model-protocol.md) select the
P96-04A-WIRE pure snapshot boundary accepted at local Step commit `625d533`,
after 151/151 focused tests and independent whole-Step review. Earlier dated
pending statements retain their historical meaning. P96-05 retains registered Operation and
formatter/adaptor object proof; mutation, Business Operation and context artifacts
remain separately owned by P96-06/08/10, with final handoff at P96-11.

## P96-05A: Prepared effective registration

[Runtime SR1..SR4](../spec/display-model-runtime.md#p96-05a-ordinary-component-scoped-read-service)
are authoritative behavior. This selected architecture is implemented under the
same frozen initial Slice; no new lifecycle or transport design is inferred.
Validation and independent acceptance are complete for this scoped read-service
boundary, as recorded in the
[acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance);
local Step commit remains PREPARED/PENDING. No later mutation/context or Phase
gate is closed by this evidence.

Passive application ResourceBindings close the missing resourceType ownership
edge without putting a View/converter/callback into client input or generated
metadata. Service capability selection is separate from static/runtime selection,
component-first with factory-only-if-absent, after typed Views/runtime are ready.
Existing ABI S05/S06 distinguish absent metadata from a present-empty selected
provider: absence preserves legacy success without a Display service/getter;
present-empty static admission fails with LUI96_DEFINITIONS_MISSING at
`definitions` before runtime/service getters and without fallback. No admitted
empty runtime or service is constructed. This reconciles SR1 with the existing ABI.
A full screen mapping and matching List DetailTarget resource types prevent a
resource family from being silently inferred from a screen or producer identity.
Private admitted service entries retain trusted Source plus original Definition;
the already-complete typed Registry retains each semantic source and projector.
No source value crosses an erased/generic bridge.

One narrow private immutable DisplayRegistration holds effective Protocol,
ProtocolLogic and ServiceGroup. Component Core is not replaced: Component.Instance
and application fixed Core overrides remain valid, and engines/identity/factory/
subsystem remain exactly original. Prepare all three effective objects before the
existing final Display publication tail. Append a single definition, preserving
handler, definitions and installed Service references; call ordinary serviceFactory
setup/create only for the new service. Normalized existing display services fail
closed rather than becoming extension or replacement points. NonFatal preparation
failure uses the existing semantic `componentInvalid("Display service construction failed")`
before any presentation publication. Its componentInvalid taxonomy is retained
without a Throwable cause/facet, private exception text or raw logging. This
implements the existing SR4 construction-failure contract, with no new lifecycle
or architecture guarantee.

Component protocol/protocolLogic/services getters select the complete registration
or baseline. OperationRequestActionDispatcher joins ordinary consumers by reading
component.protocol; its accompanying private selector/parameter types use compliant
UpperCamelCase. Existing resolver, CLI/meta/OpenAPI/MCP consumers already use the
effective getters. Entity bootstrap's core.protocol fallback deliberately remains
original semantic metadata, so presentation operations cannot synthesize an Entity
collection. There is no global resolver or public generic mutation API.

Final single-owner success publishes admitted definitions, typed runtime, complete
registration and bootstrap flag after every gate. Successful repeat preserves
references and getter counts. A failed late join retains baseline protocol/services
and no Display publication; preexisting semantic/provider/factory setup effects are
not rolled back. This is not a concurrency, locking, transaction rollback or
retry redesign, and there is no lazy service exposure.

## P96-05A: Ordinary authorized execution and JSON ownership

Bound query/read are ordinary OperationDefinitions with closed Property input,
QueryAction and ProcedureActionCall. Their existing canonical framework result
declaration, `ResponseDefinition(result = List(DataType.Named("OperationResponse")))`,
satisfies the specified nonvoid construction contract before prepared effective
registration. It introduces no new Display datatype or schema: actual execution
still returns OperationResponse.Json through the pure typed codec and closed
`cncf.display-model.v1`, version `1`, business protocol.
Authorization occurs on the canonical normal
Subsystem path before raw input conversion. The ProcedureActionCall forwards its
exact scoped execution context to the already-admitted Browser route once. Query
uses canonical match-all `Query.fromRecord(Record.empty)`; arbitrary filter/control
grammar is rejected. An inert enabled Action remains presentation data, never an
authorization credential. Original structured source/projection/codec Conclusions
are not wrapped into service-input errors.

Raw shape validation precedes datatype coercion to prevent incidental String
conversion from widening the public contract. Canonical EntityId parsing remains
datatype-owned; exact screen/resourceType admission is service-owned. Fixed
redacted service diagnostics distinguish admission/input defects from ordinary
security failures and existing runtime/wire failures.

The [protocol adapter design](display-model-protocol.md#p96-05a-typed-business-json-and-framework-metadata)
keeps business Json separate from framework Record metadata. This is not a
domain-to-Record projection or custom Http response. Pure typed encoding feeds
OperationResponse.Json through the ordinary selected JSON envelope, preserving
UTF16 units at the real in-process Http4s UTF8 body boundary. No live socket,
alternate YAML/XML converter, locale transformation or new envelope schema is
claimed.

Read E1..E8, registration E1..E6, public property E1/E2 and envelope E1..E3 are
linked bidirectionally in the [normative correspondence table](../spec/display-model-runtime.md#p96-05a-executable-correspondence).
The [dated acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance)
separates historical authoring/planned execution from actual focused receipts and
independent acceptance: current subject13 14/14 in two suites and retained
subject12 266/266 in 32 suites, with whole-Step review and focused naming PASS
admitted in ledger revision 28. The retained accumulator was not rerun on renamed
source. P96-05 is CLOSED as the PREPARED/PENDING local Step commit candidate;
all 54 criteria stay unchanged, later P96-06..11 remain OPEN and final Phase,
separate Cozy and app aggregate/E2E acceptance gates remain pending.
