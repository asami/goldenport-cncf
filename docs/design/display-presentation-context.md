# Display Presentation Context design

Document role: frozen P96-10 responsibility and compatibility decisions.
[The specification](../spec/display-presentation-context.md) defines observable
behavior. Connected P96-10 A+B, covering pure inert values/wire, Display
preservation and normal Operation admission/continuation, is independently
accepted on all four original Step criteria through original whole-Step review
`phase96-p96-10-step-review-001@1` and fresh focused closure
`phase96-p10-step-repair-focused-result-001@1` in review ledger revision 40.
The [Step acceptance record](../journal/2026/10/2026-10-17-phase-96-presentation-context.md#p96-10-step-acceptance-2026-10-17)
supersedes the historical authoring/pending-acceptance correspondence below.
Execution evidence remains parent-owned. This records no whole-Phase or
consumer acceptance and changes no responsibility, schema or ABI semantics.

## One optional wire boundary

The version-1 Display root admits only the explicit optional
`presentationContext` addition. Absent context preserves old bytes, metadata,
model semantics and trusted definition identity. The existing Display renderer
continues to own exact UTF16 and decimal representation. A separate pure codec
owns the bounded context envelope; it adds no ExecutionContext dependency,
provider, lookup, clock, transport or dispatcher. Display's attachment helper
retains its original trusted members and cannot re-admit definitions.

Private constructors make the public immutable PresentationContext and
companion-owned Flow values impossible to alter through case-class copy.
Factories validate canonical strings and nullable interop boundaries. Validation
does not turn a supplied variant, flow reference or expiry into authorization.
In particular, a context without flow is still representable; interpretation
remains server-owned.

Independent root and family versions permit bounded optional evolution without
changing the Display schema, generated ABI, Phase 74 semantics, UI definition or
resource revision. The closed supported families carry only canonical locale,
client-safe variant and opaque flow references/Instant. Raw runtime,
security/evaluation lifecycle, UnitOfWork and provider objects never enter the
wire. Phase 98 continues to own Experiment/Run/Arm state, allocation,
observation, persistence and reward.

## Strictness and bounded extensions

Unknown names and unsupported positive family versions are discarded optional
extensions. They are never preserved for echo or executed. Closed envelopes,
positive integral versions, bounded names, uniqueness before filtering, total
eight-family count, eight-container nesting and 8192 UTF8-byte limits apply
equally to supported and ignored data. Known v1 payloads remain strict and
cannot disappear on malformed input. An unsupported context root version fails
compatibility rather than selecting another context interpretation.

Raw entry checks bytes and quoted/escaped lexical nesting before strict Jawn
parsing. Tree entry accounts for size/depth with bounded iterative traversal
instead of recursively rendering an untrusted tree. Numeric version
classification uses coefficient/scale text without expanding a huge exponent.
These mechanisms exist for the explicit bounded input failure model only.
Fixed typed diagnostics carry structure and paths without input or parser
details. No authority is inferred from parsing success.

## Runtime ownership and ordinary Operation path

The specification's R5 family map fixes source, producer, reader/updater,
version, lifetime and propagation. Core ExecutionContext owns locale. A
server-resolved flow owns safe variant, Display Instance/correlation and expiry.
Client/Core retain typed values; TFAF renders registered realizations. Apps
compose public Operations without experiment allocation or custom context
forwarding. The physical return is reserved `cncf.presentationContext` Request
metadata or `X-CNCF-Presentation-Context` HTTP metadata, outside business
payload. Raw candidate multiplicity must survive HTTP adaptation so duplicates
can be rejected.

Slice B must use the existing three normal authorized Request, constructed
Action and prepared ActionTask preparation routes. Restoration belongs after
ordinary authorization and before evaluation admission/assignment-dependent
business construction. A DisplayService-specific hook misses direct Business
and internal Action/Task routes. No alternate dispatcher or response-rewriting
proxy is introduced. Denied Operations invoke no provider/resolver/business.

The inherited ScopeContext capability defaults disabled. Runtime immutable
CncfCore presentation data contains safe resolved values only; provider/store
handles and app configuration stay out. Server lookup binds exact opaque
references to component, authenticated principal, session and server expiry.
Incoming locale/variant/expiry are candidates. Unknown/expired/unsupported
optional flow and absent server state use the ordinary cleared-context path;
absence invokes no provider. Provider exception or malformed return fails
structurally before business. A restart cannot recover assignment from wire.

## Existing evaluation and committed outcomes

The existing [evaluation design](operation-evaluation-capture.md) and
[R1..R9 admission contract](../spec/operation-evaluation-capture.md) remain
authoritative. Only declared Operations call the existing resolver. Each new
Operation creates its own logical execution/attempt and immutable admitted
assignment before attempt start. The resolver may consume a server-resolved
continuation reference, never client allocation data. Required admission must
still succeed or fail. Retry retains accepted flow and assignment without
reallocation. Parent context alone gives nested or cross-component Operations
no membership or assignment authority.

Resolved context must reach source View callbacks and actual committed reload.
Read/List and ready refreshed Display carry the safe projection. Pending/delete
acknowledgements must preserve the flow even when they have no Display. Existing
receipt/resource revision, no-replay and `retryCommand:false` rules continue.
A projection failure after commit never replays the mutation. Direct ordinary
Business success still requires explicit Display reload; a Business response
does not become refreshed Display by rewriting.

## Frozen Slice B API and physical origin decisions

The auxiliary PresentationContextResolver separates Unavailable from Supported.
Its request carries only trusted immutable Binding, optional opaque References
and server now. Binding and Resolution constructors remain private and use safe
factories; flowless support is representable but a variant without server flow
is not. CncfCore stores Resolution only. ScopeContext owns the inherited
capability and disabled fallback, preserving typed component ancestry through
wrappers and preserving provider plus cross-sink policy during both runtime
rebound paths. OperationEvaluationResolver and immutable admission types do not
change.

Restoration clears parent presentation for each new Operation. Strict bounded
wire decode precedes provider selection. A second bounded Json inspection only
recognizes unsupported display-flow version envelopes; it cannot relax duplicate
validation or reinterpret discarded flow as support. Binding is derived from
route component instance and current trusted User/Service/Subsystem principal
and server session. Server session expiry and returned flow expiry are checked
against the server clock. Client expiry never controls continuation lifetime.
Missing capability/state clears optional data; mismatched or malformed provider
output fails before evaluation/business using fixed redacted structured errors.

Origin is typed private transport information. The real HTTP entry passes its
original HttpRequest through the existing dispatcher until authorized
restoration, preventing adapter Property mirrors and two framework enrichment
passes from creating fictitious candidates. Request.source Http supplies the
same origin for Action/Task or reconstructed canonical HTTP calls. Source.None
Request plus independent HTTP retains separate Property evidence. There is no
client origin switch, public flag or CncfCore transport retention.

Generic enrichment must avoid even early decoding of reserved raw query values.
It lexically excludes the exact canonical ASCII key, accepting each character
as literal or its percent-encoded byte, before the old generic query parser.
This step neither decodes nor validates presentation input. After authorization,
the dedicated ingress decodes raw key/value once, retains ordered physical
duplicates and compares the structured mirror. Raw duplicates already fail even
if structured transport collapsed them. Headers and form remain independent
physical sources; Record fields and single values preserve original types.
Other generic metadata keeps its prior parser and enrichment behavior.

Projection performs no effectful lookup. The current typed Component.Context,
trusted binding, server lifetime and core locale determine whether Resolution
can be projected. Read/query registries replace supplied context only after the
registered contextual source and pure projection. No wire locale can overwrite
the core locale and no parent context can grant unrelated membership.

Mutation Result adds optional root context after retryCommand with no schema
version change. Runtime attachment updates Ready nested Display to the same safe
projection while preserving trusted receipt, definitions and model references.
Pending/Delete retain root context without Display. Pure root Some requires
semantic equality with Ready nested Some; root None preserves earlier Slice A
inert supplied-value behavior. Existing one-shot completion and all canonical
storage/transaction branches remain responsible for outcome and no replay.
Direct Business responses stay ordinary and require explicit Display reload.

## Evidence boundary

The eleven Slice A scenarios and two generated laws correspond to R1..R4 and
wire-only exclusion aspects of R5. They do not fake authorization, runtime
admission, provider failure, expiry/isolation, retry or post-commit execution.
The existing pure mutation-ready codec is exercised only for transitive supplied
Display fidelity. Slice B retains real connected fixtures for all ordinary
routes and lifecycles. One connected P96-10 Step review/commit follows that
work; A is not an independent acceptance unit. P96-11 retains consumer and Phase
closure. Decision history remains in the dated journal, and the October 2
proposal is preserved without editing.
