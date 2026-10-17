# Ordinary Display mutation service design

Document role: selected PHASE-96 / P96-07 / P96-07C3 and P96-07C4B architecture decisions.
The [contract](../spec/display-mutation-service.md) fixes behavior. Existing
[Display runtime](display-model-runtime.md), [pure mutation](display-mutation.md)
and [bridge](display-mutation-bridge.md) designs retain their boundaries. These
decisions add ordinary update-first and opt-in typed lifecycle composition.
Transaction, read-model, generated ABI and generic HTTP ownership retain their
existing boundaries.
The common [ingress context design](ingress-context-correspondence.md) assigns
full operational-ID restoration and final current-UnitOfWork binding to the
existing context/security/runtime owners.

## INT-D01: Canonical ActionEngine finalization

## DLC-D01: Additive lifecycle composition

The frozen lifecycle factories retain separate C and P types. One private
lifecycle binding owns the original update binding and compiles it once, then
wraps its Entry with typed create/delete dependencies. Exact enabled descriptor,
selection, capability and target correspondence is checked at registration.
Create persistent mapping validates editable storage names while allowing
trusted complete server defaults. Collection and optional identity are checked
again after pure conversion/command. No generated definition, wire schema or
provider API changes.

Object-owned internal Captured alternatives retain actual Updated, Created and
Deleted objects. Entity helpers authorize and interpret the existing nodes on
the current UnitOfWork. Aggregate helpers forward the existing C4A kernels and
run pure preparation only inside their authorized callbacks. Actual selected
provider capabilities gate observed deletion; kernel plans retain side-effect
and acknowledgement responsibility. The ordinary ActionEngine owns the sole
commit. Existing current witness guards every completion. Created coherence and
one contextual Detail produce Ready or retained pending; Deleted always uses
the existing reload-list result without Detail or replay.

Resolved HTTP operations declare expected GET/POST/PUT/DELETE independently of
their raw-body marker. The server compares the real method before converted
request admission. All CUD share strict raw UTF8/JSON and physical metadata;
non-HTTP CUD share raw String inputs and operation-specific observation rules.

The opt-in lifecycle fixture forwards actual provider capabilities and exact
acknowledgements, with wrapper identity cached across root/body. It declares
complete pre-action logical/native/resident/View oracles, distinct unhydrated
create resident and hydrated semantic View values, and exact owned-ID reset.
Generated ID injection is limited to explicit Execution; actual HTTP/MCP use a
server-supplied seed. C4C canonical List/PG-E12 and original acceptance gates
remain separate required work.

### Canonical failure reconciliation and independent fixture oracles

The selected resource and its derived caches have separate existing owners.
The Display entry delegates canonical snapshot visibility and observed-delete
reconciliation. It does not add an aliveness policy. A literal persisted `dead`
without either deleted-at attribute reaches the canonical missing-target
rejection, which evicts only selected Realm/memory. No durable row, body,
unrelated resident, cached semantic View or View build count changes. A separate
literal-dead fixture with nonblank `deleted_at` is excluded earlier by normal
snapshot visibility and retains the entire pre-action state.

The object-owned `DeleteRejectionBoundary` fixes the expected branch before
invocation. Its `BeforeCapture` oracle returns the complete snapshot unchanged;
`CanonicalMissingTarget` changes exactly selected Realm/memory to absent. The
fixture first warms real selected/unrelated Views and residents, then uses the
existing selected-ID setup provider to alter only the durable root. Native
deleted columns remain physical NULL and logically absent in the aliveness-only
case. The explicit literal `dead`, exact revision, real provider capabilities
and one pure validator remain part of the specified command. The expected
failure facets and all state dimensions are fixed before execution, never
inferred from an actual result or masked after it.

Portable and mandatory native scenarios exercise both branches. Separate total
create/delete laws reuse a finite pool indexed by target, representation and
server-owned identity/body mode. Every sample resets exact owned roots/bodies;
finally cleanup closes all sessions and deletes only their own IDs. Native
PostgreSQL uses its real required runtime and independent complete reader.

`DisplayMutationIngress` supplies normal QueryAction/CommandAction definitions.
The update ProcedureActionCall lives in `action` so it can use the existing
package/protected authorization and component datastore facilities without
widening their APIs. It evaluates a canonical captured program on the active
UnitOfWork, retains its actual result and returns internal Void. The unchanged
ActionEngine owns execute/commit/finalization and calls the existing response
hook. There is no nested run/commit/abort, replay or application-owned writer.

`DisplayMutationUpdateBridge` retains the legacy program constructor and adds
captured Embedded/Detached record outcome construction. The canonical
interpreter/provider still owns persistence, atomic observation, domain and
transition checks, revision/audit policy, resident coherence and View
invalidation. Capture projection/coherence failures are separate from known
provider acknowledgment; they do not turn a known mutation into a retryable
execute failure.

## INT-D02: A fresh current-operation witness

UnitOfWork.CommitResult is Unit. A successful last result/termination can be
historical, and a runtime commit action can be a no-op. After actual capture,
this ActionCall stages its own fresh existing postcommit/postabort callbacks
on the same UnitOfWork. Its private object-owned witness begins Pending and
can become Committed or Aborted. The response hook requires this witness plus
the actual successful last result and Committed termination.

Resource-registry termination is not a replacement: after a historical
termination a newly registered resource can release immediately. The witness
therefore uses the canonical staged callbacks, not a new resource lifecycle or
global generation counter. Absent/no-op/historical-only evidence is existing
transactionIndeterminate with no receipt or read. Actual commit rejection,
abort, unknown/disposed or cleanup failure remains the unchanged framework
failure path. No global cleanup-recovery policy is introduced.

## INT-D03: Closed application-owned typed registrations

`DisplayMutationRuntime.Binding` is closed and privately constructible. Public
Entity and Aggregate factories preserve E/A/P in private typed implementations,
with explicit canonical owner/collection/fields, seed, persistent, availability
and fixed pure Aggregate command. Public registration never accepts a writer,
raw Record/Any facade, capture constructor, client path or inferred inverse.
Concrete data ADTs and witness enums are object-owned, not class/trait-owned.

Component providers precede factory fallback. Ordinary canonical collection
bootstrap and read binding occur before optional mutation compilation, and
service publication occurs only after all registrations succeed. Missing
provider retains exact query/read behavior; a selected empty provider does not
fall back. Source/screen/definition/provenance and canonical target admission
are checked before any mutation route exists. Existing read-only `_bind_c`
callers retain source compatibility.

The binding's private target view permits fail-closed ownership/storage
checking, without serializing target internals. Explicit
`persistent.storeFieldName` is the only forward mapping. Entity and Aggregate
paths inspect the actual selected Update changes through the existing bridge
policy. Readonly/managed/unknown/kind/duplicate/alias/seed guards precede unsafe
conversion or persistence. SQL NULL remains physical only; logical attributes
are omitted under the established boundary.

## INT-D04: Framework authorization before preparation

Entity update first requests complete existing canonical authorization for the
registered entity and exact ID and evaluates its Authorize node. It loads the
registered persistent's authoritative current representation, compares observed
revision before conversion/equality and retains the same request on the strict
captured write for persistence recheck. No synthetic internal/system permission
is admitted.

Aggregate update reuses the existing observed capture helper, fixed registered
command and root/alias semantics. Its callback is reachable only after actual
Aggregate authorization and authoritative current-root/revision comparison.
Typed preparation and selected patch checks run there, followed by the pure
application command. The local pure authorizer is unit only after framework
authorization; it is not a substitute for it. The application command may
maintain domain-derived state but owns neither storage nor commit nor reload.

Declared Aggregate policy remains selected from the outer Action/request
operation. The fixed domain command name determines access-kind evidence, not
the declaration selector. The fixture declares authenticated-only `update`
alongside unchanged `changeFields`; this requires no product authorization
selector change.
Normal ActionEngine pre-execution authentication precedes Aggregate loading and
UnitOfWork factory authorization. The default protected authorization hook uses
the existing structured ConsequenceException carrier, and the ActionEngine
adapter extracts its original Failure Conclusion, including attached Throwable,
previous causes and status. An unauthenticated exact owner is rejected against
the full request operation name before Aggregate/factory effects. The fixture's
canonical-access denial therefore uses foreign ownership only for Entity;
Aggregate and alias use the same owner without authentication under their
declared policy.

Descriptor availability is per-call pure server policy. The descriptor publishes
existing inert editor metadata; its schema/capabilities cannot grant mutation
authorization. Update reevaluates current availability and readonly veto.
Structured callback Conclusions remain original; null/NonFatal boundaries use
fixed redacted existing failures rather than exception text.

## INT-D05–INT-D06: Narrow ordinary adapter composition

The actual resolved Display operation marks whether the HTTP converter must
retain its raw Bag. Subsystem selects the marker encoder only for that resolved
operation, then uses normal operation authorization, ActionEngine and egress.
The strict encoder carries ordered physical headers and decodes raw UTF8 using
REPORT before the existing mutation codec. It does not normalize JSON before
duplicate/scalar admission and does not treat normalized query metadata as
If-Match authority. Unrelated HTTP paths and global Entity REST defaults are
unchanged.

CLI ArgsIngress, published MCP tools/call and registered SpiInvoker all produce
the same ordinary operation request. Payload remains one String and observation
remains external canonical metadata. No adapter has an alternate domain writer
or a JSON object-to-map-to-string normalization path. Required execution-time
values are checked even where the published RequestDefinition allows adapter
carriage through ZeroOne metadata.

The internal `DisplayMutationOperation` capability is the common actual
Operation marker; the HTTP marker extends it without changing the HTTP
members. BoundOperation supplies an immutable String-name set for selectors
and, on update only, payload/observation. MCP first admits the unique published
tool, then finds one exact actual primary component/service/Operation. It checks
the original argument kinds in name order against that marker before generic
normalization. Invalid kinds become bounded -32602 protocol failures without a
result or subsystem execution. Accepted raw Strings are unchanged and retain
strict duplicate-preserving decoding as an ordinary tool-failure boundary.
Neither textual `display.update` naming nor catalog JSON schema selects this
policy. An ordinary unmarked QueryAction with that same service/operation name
continues normal subsystem dispatch with the existing JSON-kind conversion.

## INT-D07: Actual final Registry output and selected provenance

The final read Registry returns Consequence[Display]. It does not return Source
and cannot be replaced by an arbitrary success callback. Normal
ComponentFactory composition supplies one admitted definitions value, one
registered read Registry and matching resource bindings to both original read
service and mutation compiler. The private compiled Entry retains the admitted
selection; the final Registry retains the registered Browser and fixed
projector/definition. Completion pairs the actual selected read result only
with that retained Entry source.

One receipt is derived solely from the actual captured applied flag and persisted
identity/revision after the fresh current commit witness. One completeC call
first checks retained projection/coherence and only then performs one actual
selected contextual read with the same ExecutionContext. Exact correspondence
is Ready, correct-ID revision lag is ViewNotCurrent and callback/wrong-ID
failure is ProjectionUnavailable. The committed receipt never authorizes replay.
Source labels are not used to bless foreign cached data.

## INT-D08: Distinct executable proof boundaries

The ordinary postcommit path can reach canonical Browser lag, structured
Failure, null Consequence, Success(null), NonFatal, wrong ID and row projection
failure. Actual delegated capture projection/coherence failures occur before
read and must short-circuit it while retaining the real committed receipt.
These branches require normal resolver/ActionEngine/current commit evidence,
including physical and resident/View state and no second writer.

Foreign Source/definition substitution is not a callable branch of that final
Registry. Its proof boundary is actual typed registration and prepublication
factory admission, strict request admission, and two ordinary components with
distinct canonical values/stores/Browsers. Each valid route preserves the
other's complete state and uses only its registered context/selection. Foreign
source/definition/provenance requests reject before domain/write/read. The
existing direct Result and strict codec candidate/tampering specifications
remain required but do not substitute for ordinary physical mutation proof.

No reflection, final-class forgery, testing-only seam, unowned read Runtime or
Projection edit is introduced to manufacture an unreachable foreign postcommit
callback. Independent whole-boundary assurance and later Step/full-Phase
acceptance retain responsibility for the complete composition.

## Development fixture and independent oracle

The development-only external logicalui domain uses typed Root/PatchShape and
an explicit handwritten five-kind extension of an already admitted definition.
It is not newly generated Cozy output. Its explicit text/number/Boolean/Instant
storage codecs preserve UTF16 units, decimal scale and nanoseconds through the
declared provider representation; sample choices belong in the
[journal](../journal/2026/10/2026-10-15-phase-96-display-normal-update-integration.md).
Unknown canonical read data is preserved in ordinary Record fields, not a
second framework Option model.

Normal ComponentFactory bootstrap registers real canonical Entity, Aggregate
and alias collections plus contextual Browser/View. Canonical create is setup
only. Provider observers delegate real operations and retain actual outcomes.
An explicit test context can inject commit/auth/projection faults while the
standard resolver and ActionEngine still execute. Independent pre-action
Expected values declare ordered logical Fields, physical native tables/NULL,
resident memory and semantic Views with fixed clock/principal. Physical reads
and traces observe actual effects; response labels are not state oracles.

Portable memory/SQLite coverage and mandatory native PostgreSQL correspondence
retain their own real provider capabilities. The native fixture uses normal
managed configuration and a separate native physical reader, without skips.
Owned subsystems close their SystemNode/runtime in finally. SQLite sessions
create unique test-owned directories; shared component assembly reuses the
same actual configuration/physical reader and does not create a spare store.

Every nonshared fixture constructs a fresh RuntimeConfig from the default value
with a manually resolved Test execution profile and fresh datastore/entity-store
spaces. Explicit GlobalRuntimeContext and child Subsystem scope ancestry carry
the selected manual clock into normal ingress. Normal Subsystem startup cleanup
surrounds controlled-test admission; acquisition failure removes only its owned
SQLite directory. Existing session-finally cleanup retains runtime ownership.
RuntimeConfig.from/create is not used because it would eagerly open another
unmanaged SQL provider. Shared sessions reuse the same existing Subsystem.

After component initialize/bootstrap/add, Session resolves its actual unbound
component datastore once for the canonical collection. This same fallback feeds
custom Execution observers and normal HTTP/MCP, while managed external-required
SQL resolution still owns real persistence. There is no parallel memory store,
global clearing or lifecycle workaround. ControlledTest HTTP/MCP explicitly
supply true authentication for the owner; direct Execution keeps its existing
test token only when authenticated. False removes that token or carries one
canonical false adapter fact. Both representations of Aggregate and alias must
preserve full state when the unauthenticated exact owner submits a current
observation. Ordinary provider/provenance policy is unchanged.

Ordinary HTTP/MCP success is observed through one Aggregate load, the complete
positive authorization vector, the actual current UnitOfWork commit result and
termination, one authoritative receipt, one domain validation/command and one
contextual find with exact clock, trace, principal and UnitOfWork correspondence.
Independent logical, physical, resident and View expectations remain fixed
before action. Only custom Execution installs ObservedSpace's `capture` log
callback, so ordinary adapters do not assert that callback or fabricate it.
Negative adapter cases use the independently declared full-request-name
authentication Conclusion and expect no UnitOfWork factory authorization record
before checking zero downstream effects and full state preservation.

The original update decisions and the additive typed lifecycle decisions close
no P96-07 criterion. Acceptance of positive lifecycle Detail, canonical
lifecycle/List/PG-E12, P96-08..P96-11, Step commit, full-Phase review and
final release/aggregate obligations remain unchanged.

## C4C-D01: Existing durable query and registered Aggregate ownership

The opt-in lifecycle fixture appends one immutable List definition and matching
read/resource registrations. Its declaration is assembled from the already
admitted development Detail's exact field metadata, provenance and view binding.
The new `DisplayMutationServiceListFixture` owns C4C declarations, witness and
expected-value models, exact List assertions, sequence vocabulary and bounded
session ownership. The existing lifecycle fixture receives only the necessary
provider/search/registration and exact-ID setup access. Earlier fixture-size
debt remains a separate follow-up.

The Browser reads through the active UnitOfWork's existing direct durable-search
node. Store scope and explicit Owner visibility express this development owner
workbench's read selection. Ordinary domain UserPermission `search/list`, normal
Component factory authorization and standard result filtering remain in that
route. The selected managed datastore supplies search rows; the observing
wrapper forwards the actual SearchableDataStore capability, directive and count.
It never owns a result cache, alternate selection policy or business writer.

For Entity, each searched identity goes through the existing snapshot/detached
load. For Aggregate/root/alias, Browser calls the registered AggregateSpace
query; the collection's ContextualAggregateQuery searches dynamic identities
then calls that same registered collection's existing contextual builder.
Canonical load supplies the matching revision carrier after full-root building,
so Detached decoding need not retain managed revision in its domain object.
All failures and full-root fields propagate. Sorting by opaque EntityId.value
fixes fixture source order without parsing identities or extending public query
semantics. Display projection consumes the resulting typed Roots.

Two typed witnesses outside the selected/unrelated pair are seeded before the
action under the existing managed lease with the actual selected setup provider.
One belongs to owner and one to foreign. Trusted witness collection/identity
checks and load-before-delete cleanup retain only their exact root/body
addresses. The wrapper closes witnesses before the base Session, including
pool eviction and failure-finally cleanup. At most four sessions remain live;
replacement opens only after its eviction closes. No server-pool change is
part of this fixture lifetime policy.

Explicit List queries are separate reads after CUD. They never act on a
reload-list descriptor automatically. A committed pending Detail or failed
explicit List cannot replay a command; clearing the read fault permits another
read of the already committed state. Whole-state expectations are independently
declared before commands using retained C3/C4B oracles, plus complete witness
baselines and exact ordered seven-field List models. Native physical snapshots
remain independently read and include catalog/type/row/NULL/body/sentinel data.

Actual HTTP reuses Session.http with an optional final List screen selector;
ordinary query and ArgsIngress retain the existing resourceType/screenId API.
Real HTTP carries server-supplied identity and the existing strong If-Match
transport. Generated-ID injection remains explicit Execution setup. Shared
scenario vocabulary serves portable suites and mandatory PostgreSQL 17.11;
the latter retains version and durability checks and a separate complete
shrinking lifecycle law. Native unavailable execution is never a skip or proxy
success. The C4C specification owns traceability; source authoring alone does
not establish Step, Phase or PG-E12 acceptance.

### C4C-D02: Comparison and observation lifetimes

The memory provider normalizes only the temporary desired Record used for
business-state comparison, reusing its existing exact SetNull removal. It
retains the original plan for writes and side effects, and retains observed
stale-before-equality, normal Optimistic ordering and AlwaysWrite advancement.
No SQL JSON decoding, existing-record normalization or generic equality policy
is introduced. The independent
[memory kernel specification](../../src/test/scala/org/goldenport/cncf/datastore/InMemoryEntityClearComparisonSpec.scala)
covers full ordered authoritative records and all side targets across both
memory providers and both mutation forms, including maximum-revision NoOp and
complete generated clear/repeat/stale/omit sequences.

Direct semantic List consumers acquire the existing managed Subsystem lease,
bind their same explicit Execution within it and call the same registered
contextual Browser. Their actual UnitOfWork and authorization remain unchanged.
Empty ordinary GET query/read fixture requests omit default Content-Type and
body attachment while preserving all caller headers and context selectors.
Other methods, operations and payloads retain their existing request shape.

Missing semantic Views are deliberately uncached. A full-state verification
therefore has a predictable cost: one builder attempt for each selected or
unrelated View known to be Missing in the pre-action baseline. The List fixture
computes that exact expectation before the action, changing only the expected
build count. The generic full-state checker and production View lifecycle stay
unchanged. Complete Duplicate expectations independently append the current
provider Reason and Policy facets in their original order, preserving all
taxonomy, status, identity and other Conclusion fields.
