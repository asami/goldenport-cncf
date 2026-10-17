# Ordinary Display mutation service

Document role: normative PHASE-96 / P96-07 / P96-07C3 and P96-07C4B contract.
The update-first boundary adds ordinary `display.describeMutation` and
`display.update`; an explicitly registered lifecycle binding additionally
admits ordinary `display.create` and `display.delete`. This contract closes no
original P96-07 criterion. The
[pure mutation contract](display-mutation.md),
[internal Entity bridge](display-mutation-bridge.md),
[Aggregate capture](aggregate-observed-mutation-capture.md) and
[current UnitOfWork capture](unitofwork-versioned-mutation-capture.md) retain
their respective authority. The [design](../design/display-mutation-service.md)
assigns the integration responsibilities.
The common [ingress context contract](ingress-context-correspondence.md) owns
complete operational-ID restoration and final current-UnitOfWork context
correspondence, including unchanged security/profile authority.

## C3-01–C3-02: Optional typed server admission

`DisplayMutationServiceBindingProvider.displayMutationBindings(component)` is an
optional server-side capability. The component provider precedes a factory
provider. An absent provider preserves the original query/read-only service;
an empty selected provider adds no operations and does not trigger fallback.
One selected provider supplies one immutable vector before service publication.

The public `DisplayMutationRuntime.entityUpdateC[E,P]` and
`aggregateUpdateC[A <: RecordPresentable,P]` factories retain typed application
input, seed, canonical persistent, explicit owner/field mapping, current-context
availability and, for Aggregate, the fixed registered command. The closed binding
hierarchy exposes neither an erased writer nor construction of captures or
committed receipts. Client input supplies no target, closure, collection or
command name. Existing source-compatible read-only service binding remains.

Normal `ComponentFactory` bootstrap establishes canonical Entity/Aggregate/View
collections and the selected read Registry first. Mutation admission then checks
the same component, resource, Detail screen, definition revision and every
provenance member before publishing operations. Foreign/wrong selection, List
definitions, missing Browser, null/throwing providers, duplicate bindings and
wrong collection/owner/family/revision representation fail closed. Entity
collections must be the actual registered collection object, with managed
Embedded or Detached revision and Optimistic concurrency. Aggregate aliases
must resolve through their registered canonical root and matching patch
collection.

Every registered field targets an explicitly admitted owner and logical field.
Only `persistent.storeFieldName` supplies the storage mapping. Duplicate storage
aliases and managed/security/lifecycle keys reject. After pure preparation, the
actual `Update.toChangesRecord(persistent.toStoreRecord(input))` must contain
only selected admitted storage keys. Unselected seed assignments reject; no-op
selected assignments may yield fewer keys. `Update.noop` and `Update.setNull`
remain distinct. [Logical SQL NULL omission](sql-null-record-boundary.md) is
unchanged: a physical SQL NULL attribute is absent from its logical Record;
there is no nullable Option API or new raw-null domain representation.

## C3-03–C3-05: Authorization and current preparation

The normal resolver and ActionEngine perform operation authorization. Entity
update obtains the existing complete canonical `UnitOfWorkAuthorization`,
evaluates `Authorize` on the current UnitOfWork before canonical current load
or conversion, and retains the same request for persistence recheck. It loads
the authoritative Embedded snapshot or Detached carrier using the registered
persistent. Exact observed revision comparison precedes equality and converter
callbacks. The existing strict bridge then constructs one captured update node
on the active interpreter. No System or ServiceInternal grant is synthesized.

Aggregate update invokes the existing observed capture helper with the
server-fixed registered root/alias and command name. Existing Aggregate
authorization, access/relation/natural model and authoritative root/revision
checks precede its callback. Only there does typed preparation run once,
validate the selected storage patch and invoke the pure application command
once. Existing canonical full-save and coherence semantics own persistence.

The public outer Action/request operation selects declared Aggregate access
policy. The fixed pure domain command supplies command access-kind evidence;
it does not replace that operation declaration selector. The normal fixture
therefore declares authenticated-only `update` as well as its original
`changeFields` domain command. Product authorization selection is unchanged.
Normal ActionCall authentication runs before Aggregate loading or UnitOfWork
factory authorization. An unauthenticated exact owner with a current
observation therefore receives the original structured authentication-required
Conclusion for the full `<component>.display.update` request name, with no
UnitOfWork authorization event, conversion, command, provider attempt or read.
Canonical Entity ownership denial uses a foreign principal; authenticated-only
Aggregate/alias policy is an authentication requirement and adds no owner-only
requirement.

`describeMutation` is an ordinary authorized QueryAction. Its current
availability and inert editor schema use the existing descriptor codec; no
canonical target, runtime closure or authorization grant is serialized. Every
update reevaluates current availability/readonly veto and actual authorization.
An earlier descriptor is not permission. Returned structured Conclusions remain
intact. Null Consequence/output or NonFatal callback failures become fixed,
redacted callback-invalid failures and cannot grant authority.

## C3-06: Actual current-operation commit

Update is a normal CommandAction. Its ProcedureActionCall binds the ordinary
component datastore, evaluates the captured canonical program on its active
UnitOfWork, retains the actual persisted ID/revision/applied outcome separately
from projection/coherence, and returns only internal Void from `execute`.
It neither nests execution nor calls commit, abort or a second writer.

After capture, fresh operation-owned postcommit and postabort callbacks on that
exact UnitOfWork witness this attempt. The existing `afterCommitResponseC` hook
requires its own committed witness, `lastCommitTermination = Committed` and a
present successful commit result. Missing evidence, a no-op commit action or
historical success without this callback produces existing
`transactionIndeterminate`, with no receipt, reload or replay. A real current
commit failure remains the unchanged framework failure; cleanup failure is not
renamed projection lag. Ordinary default hooks and unrelated runtime behavior
are unchanged.

## C3-07: One selected contextual read, never replay

After proven current commit, Updated/Unchanged and the Receipt come only from
the actual capture. One existing `DisplayMutationResult.completeC` invocation
checks the retained projection and coherence results before any read. Either
failure retains the committed Receipt as ProjectionUnavailable and invokes no
Browser.

Otherwise exactly one selected `DisplayRuntime.Registry.readC` runs with the
same ExecutionContext and UnitOfWork. Its result is `Consequence[Display]`, not
a source-returning API. The Display is paired only with the immutable selected
Entry source admitted by the same normal factory composition. The final
Registry retains the registered Browser and fixed projector/definition; callers
cannot replace them with a postcommit callback or foreign definition.

Exact source, definition/provenance, ID and committed revision correspondence
is Ready. Correct-ID older or newer contextual revision is ViewNotCurrent.
Browser/row Failure, null Consequence, Success(null), NonFatal or wrong-ID
results are ProjectionUnavailable after one selected read attempt. Every
committed branch retains the actual outcome and `retryCommand = false`; it
does not run another writer, domain command or commit.

Foreign source/definition injection is not an ordinary callable Registry
branch. Its safety remains mandatory at typed registration and request
admission, through two normally bootstrapped route-isolated components, and at
the retained public Result/codec candidate boundary. A copied source label or a
pure completion callback alone is not proof of canonical read provenance.

## C3-08–C3-09: Ordinary adapter admission

The service remains `display`, with exact new operation names
`describeMutation` and `update`. HTTP uses the existing resolved route and JSON
egress, including `/rest/v1/<component>/display/<operation>.json` and the normal
UTF8 envelope selection. The actual resolved operation marks the narrow raw
body path; unrelated JSON normalization, read-v1 and Entity REST remain owned
by their existing implementations.

HTTP descriptor is GET, with exactly one String `resourceType` and `screenId`
query value and no body. Update is PUT with the same selectors, one raw
application/json UTF8 body and exact physical strong `If-Match` observation.
The ordered header fields retain repeated/case-variant occurrences for strict
transport validation. Missing, duplicate, weak, wildcard, list, empty,
noncanonical or overflowing observations reject. Body decoding uses UTF8
REPORT, followed by the unchanged duplicate-preserving strict mutation codec.
Duplicate JSON members at every nested level and noncanonical scalar lexical
forms reject before domain/write. Query payloads, forged normalized observation
metadata, extra domain selectors and body/query smuggling are not substitutes
for raw body and physical If-Match.

Non-HTTP update requires exactly one String `resourceType`, `screenId`, raw
`payload`, and canonical positive-long observation under
`EntityRevisionTransport.observedRevisionPropertyName`. Payload is not a JSON
object converted to a map and back. CLI uses actual ArgsIngress and the normal
subsystem; MCP uses the normally published tools/call with payload String; SPI
uses the actual registered SpiInvoker contract. These reach the same resolver,
authorization, command, current commit and contextual reload. Domain arguments,
switches, extra/duplicate properties and JSON observed/expected/target fields
reject; ordinary framework/security properties retain their owning policy.

After unique published-tool admission, MCP resolves the exact actual primary
component, service and Operation by canonical tool identity. A server-owned
internal Display mutation Operation marker declares `resourceType` and
`screenId` as String inputs, plus `payload` and the observation property for
update. Before any generic JSON-to-String conversion or subsystem execution,
each present declared member MUST have JSON String kind. The first invalid
member in name order produces JSON-RPC invalid params (`error.code = -32602`,
`error.message = "invalid params: <name> must be a string"`) with no result.
No unique actual Operation produces the existing invalid-tool-name failure.
The catalog schema, operation spelling and client metadata are not marker
authority. Accepted Strings pass unchanged, so duplicate members inside a raw
payload String still reach the strict decoder and produce an ordinary tool
failure (`result.isError = true`), rather than a protocol kind failure.
Unmarked Operations, even an unrelated `display.update`, retain their existing
object/array/number/Boolean/null-to-String conversion. Other framework/security
arguments retain their existing semantics.

## C3-10–C3-13: Executable behavior and state correspondence

The external typed
[domain](../../src/test/scala/logicalui/DisplayMutationServiceDomain.scala) and
[fixture](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceFixture.scala)
use real normal ComponentFactory admission, canonical Entity collections,
Aggregate root/alias, contextual Browser/View, subsystem/ActionEngine and
managed datastore. Canonical create is setup only, not Display create proof.
Fault observers delegate actual provider operations; no side map, fabricated
capture or receipt replaces canonical evidence.

Each owned fixture runtime has a fresh explicit GlobalRuntimeContext, manually
resolved controlled execution profile and fresh datastore/entity-store spaces.
Its normal admitted Subsystem inherits that ancestry. The actual unbound
component datastore is selected once as the custom observer fallback, so custom
execution and normal HTTP/MCP use the same canonical store. Managed SQLite/
PostgreSQL external-required binding is retained; constructing the runtime
does not open a second unmanaged SQL provider or mutate global current state.

Normal adapter requests explicitly carry the owner, User privilege, canonical
full trace/correlation and true authentication as ControlledTest input facts.
False authentication carries the same exact owner and current observation;
Aggregate and alias requests must reject before conversion, domain, provider
mutation or contextual read and preserve the entire state. These fixture facts
create no System/ServiceInternal/provider grant and do not change ordinary
production authentication policy.
The expected rejection uses the independently declared full-request-name
authentication Conclusion. HTTP projects its status and display message; MCP
projects its show text as a tool failure. The pre-execution rejection yields
no UnitOfWork factory authorization records.

Before each action an independent Expected value declares the full ordered
logical root/body/unrelated Fields, physical tables/columns/types/order/rows/SQL
NULL, resident store/memory and semantic View state. Fixed clock/principal and
explicit domain field mappings determine the delta. Observations do not filter
unexpected post-action fields or derive expectations from returned JSON.
Trace evidence includes exact authorization/UoW, converter/domain order,
provider attempts, current commit and contextual find. Every owned managed
runtime and native reader is closed in finally; SQLite cleanup targets only
the session's newly created directory/files.
For ordinary HTTP/MCP Aggregate success, one actual Aggregate load, the exact
positive authorization vector, current committed UnitOfWork termination/result,
authoritative receipt, one domain validation/command, one contextual find and
the full independent state oracle provide the evidence. The custom Execution
observer's `capture` trace is not emitted by ordinary adapters and is not an
ordinary-adapter requirement or a replacement for current commit evidence.

| Specification | Required behavior |
| --- | --- |
| [Update](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceUpdateSpec.scala) | C3-E1..E8: Entity, Aggregate/alias, both representations, no-op/stale/denied/native/selection guards, actual committed pending states, factory admission and two-route isolation |
| [Ingress](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceIngressSpec.scala) | C3-H1..H8: actual raw HTTP, nested duplicates, header/smuggling guards, actual CLI/MCP/SPI, both-representation Aggregate/alias signed-in success versus unauthenticated exact-owner preservation, and all five non-String JSON kinds for each declared MCP update/descriptor String member |
| [Generated laws](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceUpdatePropertySpec.scala) | C3-P1..P4: native selected patch/outcome, current/stale/no-op, zero-effect vetoes and strict adapter correspondence; total shrinking generators, at least 100 successes/law, zero discards |
| [Native PostgreSQL](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayMutationServiceUpdateAcceptanceSpec.scala) | C3-PG1..PG3: normal managed Entity/Aggregate/alias, both representations, actual commit and independent physical/native state, pending/no replay and physical NULL/logical omission |
| [Commit observation](../../src/test/scala/org/goldenport/cncf/action/DisplayMutationCommitObservationSpec.scala) | C3-C1..C4: current witness, absent/historical-only/no-op commit, actual failure/abort/cleanup and retained capture faults |

## DLC-01–DLC-05: Typed ordinary lifecycle

The additive `DisplayMutationRuntime.entityLifecycleC[E,C,P]` factory accepts
`updateBinding`, `ownerId`, `collection`, `fieldIds`, `updateSeed`,
`updatePersistent`, `createBinding`, `createSeed`, `createPersistent`,
`validateDelete` and `availability`. The additive
`aggregateLifecycleC[A <: RecordPresentable,C,P]` factory accepts
`updateBinding`, `aggregateName`, `updateCommandName`, `fieldIds`, `updateSeed`,
`updatePersistent`, `updateCommand`, `createBinding`, `createSeed`,
`createPersistent`, `createCommandName`, `createCommand`, `validateDelete` and
`availability`. Both return `Consequence[Binding]`. Create C and patch P are
independent application types; the original update factories remain compatible.

One closed lifecycle binding composes the original compiled update entry. Its
create and update selections, capabilities and enabled descriptor fields agree
exactly, including declaration order, provenance, binding IDs, kinds and
WebColumn. All targets retain the exact owner, family and registered field IDs.
Create storage names are unique and nonmanaged. The create seed and converted
command result retain the exact registered collection and a valid optional ID
in that collection. Complete server defaults may be outside the editable fields.
Clients supply no identity, command selector, target or callback.

Entity create interprets canonical type-level create authorization on the
current UnitOfWork before availability, conversion or domain validation. It
interprets one existing `EntityStoreCreateOutcome` with the prepared typed C,
canonical options and the same authorization. Entity delete authorizes the exact
instance before loading the authoritative typed Embedded/Detached root. Current
availability, prepareDelete, stale/next-revision and pure veto gates precede one
canonical observed-delete outcome on that UnitOfWork.

Aggregate create and delete consume the existing lifecycle capture helpers.
The fixed pure create command runs inside the authorized callback; delete
preparation and veto run after full Aggregate authorization, root correspondence,
stale and overflow gates. Selected actual provider capabilities admit observed
delete; conditional physical-side-effect, provider identity and acknowledgement
checks remain canonical kernel responsibilities. No Entity authorization
substitution, privileged grant or generic writer is permitted.

The common current-operation commit witness remains mandatory. Created derives
the actual captured ID and INITIAL revision. Actual coherence gates one selected
contextual Detail read; failure retains Created with existing pending projection
and `retryCommand = false`. Deleted derives the actual ID and matched prior
revision, returns the existing reload-list representation with no projection,
and never invokes Detail. Optional coherence failure cannot erase that committed
receipt. Provider acknowledgement or prior commit history alone proves no
current success, and no branch replays a command.

## DLC-06: Lifecycle adapter metadata

Registries containing a lifecycle entry append `create` and `delete` after the
existing descriptor/update operations. Legacy selected screens reject lifecycle
commands as unsupported. Each resolved operation declares its expected method:
GET descriptor, POST create, PUT update, DELETE delete. The real HTTP method
must match, including methods whose generic conversion would otherwise be GET.
All three commands consume raw application/json through strict UTF8 REPORT and
the existing duplicate-preserving codec. Create forbids every physical If-Match
and observedRevision property/query alias. Delete requires exactly one canonical
strong positive If-Match. Ordered physical header spelling and duplicates remain.

CLI ArgsIngress, published MCP tools/call and installed SPI consume exact raw
String payloads for all CUD. Create forbids observation, including case variants;
update and delete require its canonical positive revision String. The actual
Operation String marker covers all command inputs. Existing native lexical,
selection/provenance, duplicate, managed-field and injection admission remains.
Generated ABI, closed v1 wire schemas, Entity REST and unrelated adapters retain
their existing contracts.

## DLC-07–DLC-13: Lifecycle executable correspondence

The lifecycle fixture is explicitly opt-in; original update setup is unchanged.
It observes real selected provider create/delete results, never a side-map writer.
Complete logical, native catalog/NULL, resident and selected/unrelated View
expectations are declared before the request using explicit ID, clock, owner,
trace and defaults. Missing Views retain the known entity-not-found Conclusion;
unexpected failures fail the observation. Create resident expectations decode
the expected unhydrated captured storage record; semantic Views independently
hydrate expected body state. SQL NULL remains logical omission.

### Rejected targets and existing derived-state reconciliation

Every rejection expectation is chosen before the command, from the fixture and
its responsible gate. `DeleteRejectionBoundary.BeforeCapture` preserves the
complete snapshot: ordered selected/unrelated logical roots and overflow bodies,
the complete native catalog/column types/rows/NULL cells, Realm/memory contents,
both warmed semantic Views and their build count. Authorization, selection,
availability, field, stale/exhausted revision, domain veto and normal-read
missing-target failures use this boundary. A selected root with literal persisted
`aliveness = "dead"` and nonblank `deleted_at` is excluded by normal visibility
before availability or delete validation. Its failure is the canonical
EntityNotFound classification, without the observed-mutation reconciliation
reason.

A separate root with the same literal `dead` and both `deleted_at`/`deletedAt`
absent remains visible to authoritative snapshot loading. Canonical observed
delete rejects it before provider compare-and-delete. The failure retains
NotFound/status 404, reason `entity-mutation-target-not-found` and policy
`entity.versioned-mutation`. Existing UnitOfWork reconciliation evicts exactly
the selected Realm and memory entries. It leaves all durable/native/body and
unrelated data, both cached semantic Views and their build count unchanged.
`DeleteRejectionBoundary.CanonicalMissingTarget` expresses exactly this complete
oracle by copying only those two selected resident entries to `None`.

The fixture uses its existing task-owned setup provider before the request to
change the selected durable root after warming normal caches. SQL deleted
columns remain physical NULL and logically absent for the aliveness-only case.
Numeric `3` alone is not substituted for the literal `dead` recognized by the
existing aliveness parser. Neither branch returns a success receipt, pending
projection, automatic Detail or replay; neither adds a Display visibility gate.
Resource safety and the existing derived-cache reconciliation remain distinct.
Both branches are specified for Entity, Aggregate root and alias, both revision
representations, memory/SQLite and mandatory native PostgreSQL.

The lifecycle, lifecycle ingress and lifecycle property suites cover DLC-E1..E6,
H1..H3 and separate total shrinking create/delete laws on memory and SQLite.
`PostgresqlDisplayMutationServiceLifecycleAcceptanceSpec` requires real managed
PostgreSQL, native adapters and separate create/delete laws. Each law requires
at least 100 successes, one worker and zero discards. Native unavailability is a
failure. These are C4B Detail/lifecycle obligations; C4C canonical List refresh,
native PG-E12, all original P07 rows and Phase acceptance remain open.

Native PostgreSQL correspondence uses the existing real Database.configuration
and independent native connection, including server version, fsync and
synchronous-commit checks. It is mandatory, not skipped or replaced by MySQL,
SQLite or HTTP stubs. Existing candidate/codec, read-v1, generic ingress,
authorization, lifecycle and canonical persistence regression contracts remain.

## C3-14: Remaining boundary

The original C3 subset authors ordinary update/descriptor integration; C4B adds
typed lifecycle and its executable Detail correspondence. Execution and
acceptance of that correspondence, canonical lifecycle/List and PG-E12 proof, all original P96-07
criteria, P96-08..P96-11, exact Step commits, independent Step acceptance, the
sole full-Phase review and final validated release/aggregate obligations remain
with their original owners. Documentation is not execution or acceptance
evidence. No Phase/checklist status or other repository contract changes here.

## C4C-01–C4C-04: Canonical List after mutation

An explicitly registered List uses the same admitted resource, provenance,
view binding and seven ordered value fields as its Detail. The development
screen `capture-list` targets `capture-detail`; its screen and item actions are
empty inert vectors. A List is a read binding, never a mutation binding.
`LifecycleSetup.withList` defaults to false, preserving the earlier C3/C4B
fixture modes and operation contracts.

The registered contextual Browser MUST search the actual selected managed
provider through `EntityStoreSearchDirect` on the active UnitOfWork interpreter.
Its development owner-workbench declaration uses Store scope, Owner visibility
and ordinary domain `search/list` authorization with UserPermission and current
component/resource/collection metadata. Normal factory authorization and
per-result permission filtering remain mandatory. This declaration changes no
global product authorization or public search contract. It must never use
System/ServiceInternal/manager grants, a nested run/commit/abort, a fixed pair of
identities, or a fixture result map.

Entity results load each dynamically searched identity through the existing
canonical Embedded snapshot or Detached carrier. Aggregate root and alias
results call their actual registered `AggregateSpace.query_with_context` and
resolve each searched identity through that registered collection's contextual
builder, retaining its complete hydrated Root and matching canonical revision
carrier. Revision is not guessed from query data or decoded Detached records.
Failures retain their canonical meaning. Final fixture source order is ascending
`EntityId.value`; the List projector preserves that order.

The lifecycle provider observer MUST directly forward actual SearchableDataStore
search, count and total-count capability. Exact search directives and counts are
observed separately from mutation attempts. Unsupported search remains the
existing structured unavailable failure and Unsupported capability, with no
fallback provider, synthesized capability or fabricated rows.

## C4C-05–C4C-06: Witness and ingress correspondence

List proof MUST include an arbitrary owned third identity outside the earlier
selected/unrelated pair, a separately owned foreign witness, an authenticated
foreign caller seeing only its witness, a caller with no owned rows receiving a
genuine empty List, and a trusted semantic identity predicate selecting the
actual third row. Witness records carry full body, audit, security and explicit
managed revision state. Their trusted setup uses the actual selected provider
under the Session's existing managed lease before the action. Collection and
exact witness identities are checked; cleanup loads before deleting only those
owned roots and body addresses, then closes the base session. Evictions and
failure cleanup obey the same order. A finite generated-law pool retains at
most four live sessions and closes an eviction before opening its replacement.

Ordinary `display.query` remains exactly `resourceType` plus `screenId`.
Actual Http4s GET and ArgsIngress use those unchanged selectors. Wrong resource,
screen, branch and extra/source-control properties reject before Browser or
provider effects. The HTTP fixture's optional final `selectedScreenId` argument
defaults to the previous Detail screen. Real ingress uses server-supplied
identity; generated identity injection belongs only to explicit Execution.
No List SPI/MCP, public where/sort/paging input or new wire schema is introduced.

## C4C-07–C4C-08: Independent lifecycle and native evidence

Create commits once and produces one contextual Detail; a later explicit List
MUST include the created root. List selection, changed update, refreshed Detail
and fresh List MUST retain complete typed fields and managed revisions. A
current no-op retains revision; stale observation rejects before equality.
Matched physical/logical deletion returns the inert reload-list descriptor;
only a later explicit query observes the root's absence. Unrelated and both
witness records remain unchanged. Physical deletion setup removes only the
owned root's aliveness field before the observed-delete action so the existing
canonical policy selects the physical branch; it creates no Display delete mode.

Expected membership, values, defaults, order, identity, revisions and metadata
MUST be fixed independently before the action. Complete logical roots/bodies,
native catalogs/types/rows/NULL cells, resident memory and semantic Views use
the retained independent create/update/delete oracles plus exact witness
preservation. A pre-action snapshot is a baseline, never an expected value
derived from query output or post-action state. Factory/domain/stale/duplicate
rejections preserve the full state and List membership with their canonical
structured failures. The complete Duplicate Conclusion retains the existing
duplicate taxonomy, status and exact identity, followed by provider annotation
facets `Reason(provider-failure)` and `Policy(entity.versioned-mutation.provider)`.
Its expected value is declared independently of provider normalization.

A direct semantic Browser query uses the same active Execution and context
inside the Subsystem's managed datastore lease, binding that Execution while
the lease is active. This preserves ordinary authorization and actual provider
ownership without another execution or transaction boundary. Empty GET
`display.query` and `display.read` requests carry only caller-provided headers
and no body; security, authentication, trace and output selectors remain intact.

The full-state observer resolves both semantic Views. Successful Views are
cached; a known Missing View causes one builder attempt per observation. Before
a List action, the expected verification state therefore adds exactly the
number of known Missing selected/unrelated Views to the baseline build count,
leaving every other value equal. This observer cost is independent of the List
result and never implies that List itself builds a View.

Memory `WriteIfChanged` compares the desired root after the same exact SetNull
marker removal used by memory persistence. Only the temporary comparison value
is normalized; the raw mutation plan, stale-before-equality ordering, actual
write and side-effect paths remain canonical. Repeating a clear of an absent
attribute is NoOp, including at the maximum revision; AlwaysWrite still applies.
Raw strings, structured values, empty text and significant whitespace retain
their distinct memory meanings. The
[memory clear comparison specification](../../src/test/scala/org/goldenport/cncf/datastore/InMemoryEntityClearComparisonSpec.scala)
requires both real memory providers, Patch/Replace, full root/side/unrelated
oracles and separate total clear/repeat/stale/omit laws with at least 100
successes per provider, one worker, zero discards and reducing nonempty shrinkers.

Committed create/update with pending Detail, and committed deletion followed by
explicit List read/projection failure, retain their durable outcome. Clearing
the fault and explicitly querying List MUST expose that outcome without another
command, provider mutation or replay.

| Executable specification | Required C4C behavior |
| --- | --- |
| [List lifecycle](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceListSpec.scala) | LST-E1..E5: dynamic third/foreign/empty/predicate search; memory/SQLite Entity/Aggregate/root/alias and both representations; supplied/generated create, full update/no-op/stale/physical/logical delete, canonical rejection and committed pending/read-failure preservation. |
| [List ingress](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceListIngressSpec.scala) | LST-H1..H2: actual Http4s full lifecycle, UTF8 envelope/source/provenance/typed fields, ArgsIngress/semantic agreement and zero-effect selector rejection. |
| [List sequence law](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceListPropertySpec.scala) | LST-P1: complete portable sequence with total Unicode/decimal/Boolean/Instant/nullable, target/representation/identity/body/delete inputs and meaningful shrinking; >=100 successes per backend, one worker, zero discards. |
| [Native List acceptance](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayMutationServiceListAcceptanceSpec.scala) | LST-PG1, LST-PG2, LST-PG-P1: required real managed PostgreSQL 17.11/version 170011, fsync/synchronous_commit, complete native and HTTP correspondence, rejection/pending/no-replay, separate >=100-success total shrinking sequence law, bounded owned session cleanup. |

These C4C sources supplement the retained C3/C4A/C4B, concurrency, rollback and
acknowledgement evidence. Authoring supplies no execution result, PG-E12 pass,
Step/Phase acceptance or checklist closure. Earlier pending statements record
their original boundaries and chronology. Original P96-07 criteria, parent
representative/expanded validation, independent reviews and later release and
aggregate gates retain their existing owners.
