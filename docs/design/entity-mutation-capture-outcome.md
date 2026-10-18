# Canonical mutation capture before optional coherence

Document role: settled internal PHASE-96 / P96-07 / P96-07C2B2 design.
The [paired specification](../spec/entity-mutation-capture-outcome.md) owns
behavior. Source authoring confers no validation or acceptance.

## Ownership and retention boundary

Canonical create and observed-delete writers already return acknowledged typed
results. Resident admission or eviction may fail after those results exist. Two
private non-case carriers therefore retain the actual canonical result before
optional coherence runs. Their companions exclusively admit identity/revision
metadata and contain optional NonFatal failures. They neither write nor infer
persistence from a later load, guessed successor or caller flag.

Create captures the exact raw ordered Record with admitted INITIAL revision.
Delete captures only the exact deletion result and matched prior revision;
there is no synthetic tombstone result. The shared private coherence helper lives
with the create carrier and uses existing structured operationInvalid vocabulary.
The helper preserves real structured Conclusions and redacts malformed callback
boundaries without retaining exception details. Control/fatal signals retain
their normal ownership. Fixed carrier strings expose no raw data.

The interpreter owns canonical authorization and dispatch. The new opt-in create
node requires the registered representation before invoking unchanged Space.create;
the new delete node retains existing target rebinding, loader and reconciliation.
After capture, View invalidation precedes eviction and optional create put. This
ordering makes invalidation survive later coherence failure. A valid captured
result remains distinct from enclosing UnitOfWork commit, which the caller still
owns. Old algebra nodes and their legacy exception/optional-coherence behavior
remain intact; no backend, transaction, default or Aggregate responsibility moves.

The captured-create callback alone supplies private `capturecoherence = true`
through persisted-record optional put to the shared put helper. After the
unchanged decode and resident-put mapping, only `Failure(null)` on this route
bypasses legacy recovery and returns the exact malformed result to the existing
outer coherence capture for redaction. Otherwise the same recovery clauses
preserve real structured Conclusions and suppress NotImplemented. Both private
helpers default the flag to false, so existing persisted/domain put callers
retain their legacy recovery and exception behavior. No new failure utility,
public callback or writer owns this boundary.

## Evidence and independent expectations

The [registered observing fixture](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkMutationCaptureFixture.scala)
delegates canonical native writes and Space calls unchanged. Post-acknowledgement
faults use the actual registered persistent decoder or EntityRealm.remove.
The explicit test identity context is an existing variation point and does not
replace a provider result. Exact native/Space object references establish the
producer correspondence; pure factories and recorder stubs supply no such proof.

The independent memory oracle declares input/default keys in a new Map to match
ordinary immutable-Map iteration semantics, then appends managed revision and
body fields. No actual Field vector is normalized, sorted or collapsed. SQL
fixtures explicitly own complete schemas and readers. Their physical String,
integral and NULL cells remain separate from omitted NULL attributes and explicit
read aliases. Under [Phase 100 R2/R9](../spec/datastore-logical-type-mapping.md#r2-physical-text-and-exact-string)
and its [undeclared physical-adapter activation](../spec/entity-store-persistence-stages.md),
the fixture's identity decoder supplies no logical field mapping: raw SQL readback
and capture therefore retain permission and JSON-looking business Strings. This
supersedes only the earlier decoded Record/Vector correspondence; it preserves
historical Phase96 decisions, receipt reference identity and lifecycle ownership.
Declared logical reconstruction remains the OR-mapping owner's responsibility.
Strict-create enum print values
and conditional-delete numeric assignment are distinct existing mechanisms;
TEXT SQL state columns therefore contain published/alive on create and text 3
after logical delete. No product conversion is changed to ease assertions.

The executable counterparts are
[receipt admission](../../src/test/scala/org/goldenport/cncf/entity/EntityMutationCaptureOutcomeSpec.scala),
[portable active UoW](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkMutationCaptureSpec.scala), and
[mandatory native PostgreSQL](../../src/test/scala/org/goldenport/cncf/unitofwork/PostgresqlUnitOfWorkMutationCaptureAcceptanceSpec.scala).
Their complete logical/physical oracles distinguish verified rollback from
durable data whose commit acknowledgement is unknown. Unknown acknowledgement
cannot produce a capture receipt or optional coherence, and never authorizes retry.

## Compatibility and lifecycle limit

The two new nodes are Local and their Free program construction is pure. Planner
and recorder consume typed occurrences without backend substitution. Existing
public APIs, defaults and return shapes remain unchanged. The carriers are not
wire values, Display responses, provenance certificates or committed receipts.

Ordinary Entity/Aggregate/Display integration, versioned UoW outcome nodes,
whole-commit/contextual reload and PG12 remain original following obligations.
All four P96-07 criteria, original Step/Phase/release and reciprocal aggregate
gates remain OPEN. The [journal](../journal/2026/10/2026-10-15-phase-96-canonical-mutation-capture.md)
records causal authoring history without defining behavior or claiming closure.
