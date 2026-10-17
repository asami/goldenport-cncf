# Editing Studio development reference

Document role: the frozen P96-09A development-fixture behavior contract,
composed from the admitted [read/runtime](display-model-runtime.md),
[protocol](display-model-protocol.md), [ordinary mutation](display-mutation-service.md)
and [Business](display-business-operation-descriptor.md) contracts.
Status: authored and independently accepted for P96-09 on 2026-10-17;
`phase96-p96-09-step-review-001@1` is admitted in review ledger revision 38.
This document establishes no production Studio semantics,
application authority, Flutter completion or app Phase 5 acceptance.
The [design](../design/display-editing-studio-reference.md) fixes responsibility;
the [sample journal](../journal/2026/10/2026-10-17-phase-96-editing-studio-reference.md)
records all differences from the non-normative ESS2-01 sample.

## R1: Explicit development declaration and composition

`DisplayEditingStudioReferenceFixture.Profile` has exactly `UpdateOnly` and
`Crud`. Only its exact opt-in composition admits lifecycle `withList=true`
and the unchanged non-production Business setup together under ordinary
registration, with no shared session. Production, custom registration and
inconsistent setups reject. Existing defaults and disjoint fixture paths keep
their original behavior.

The reference uses real SQLite by default, or an explicitly supplied existing
managed PostgreSQL database. Ordinary component factory, generated-definition
admission, protocol, datastore, Entity/Aggregate and UnitOfWork remain the
execution owners. Entity, Aggregate root and registered alias each support
Embedded and Detached managed revisions. No fixture map or alternate writer
is admitted. Supplied create identity is declared before its request.

Each session declares the base fixture's typed `component.id` as selected and
`component.unrelatedId` as unrelated before reading anything. Their collection
namespace is session-scoped. `capture:book-1` is never parsed or manufactured
as a CNCF EntityId. Initial selected/unrelated titles are `Before`/`Unrelated`,
description is `retained 日本 Ω`, number is decimal `123.4500`, flag is true,
instant is `2026-10-15T03:04:05.123456789Z`, nullable is `present`, and stage is
`draft`. Both initial managed revisions are one unless a scenario explicitly
declares the selected starting revision.

## R2: Connected List/select/Detail and independent provenance

List screen `capture-list` has title `Normal list`, logical Detail target
`capture-detail`, ascending actual `EntityId.value` order, no item or screen
actions, no eager Detail, and no optional snapshot. Detail title is `Normal
update`, section is `main`, and its action is `request-review` / `Request review`
with binding `business:request-review` and displayed availability `Enabled`.

Both screens have the exact ordered fields `title`, `description`, `number`,
`flag`, `instant`, `nullable`, `stage`; labels are `Title`, `Description`,
`Number`, `Flag`, `Instant`, `Nullable`, `Stage`. Title has role `Title`; the
others have role `Content`. Reuse all existing field converters.

Definition provenance is immutable: producer
`cncf.development.editing-studio-reference`, source
`cncf.editing-studio-reference.v1:UpdateOnly` or
`cncf.editing-studio-reference.v1:Crud`, revision `fixture-1`. It is exactly
shared by List and Detail. Actual managed revisions belong independently to
the model's Resource and never replace definition provenance.

The client checks decoded List membership, values, order and revisions against
independent declarations, then uses that decoded item's actual identity and
logical Detail target to request Detail. After independent Detail checks, its
actual resource identity/revision supplies the standard update observation.
Expected models cannot come from returned JSON or post-action source state.

## R3: Typed update, exact text and current observation

Six editable fields have `main` scoped locators and `update.<field>` binding
IDs. Title/description accept Text, number Number, flag Boolean, instant
Timestamp, and nullable Text or Missing. Stage is readonly with no inverse
binding. Title uses the existing `String.trim` nonblank constraint;
description may be empty. No new Unicode-whitespace policy is implied.

Raw title/description text, including newline, NUL, Unicode and arbitrary
UTF16 units, is preserved in semantic state and every fresh Display. Encoded
storage text is not a semantic View oracle. Decimal scale and coefficient are
preserved. A positive native branch updates nullable to Missing: the physical
column remains SQL NULL and the logical attribute is absent.

Changed update performs one canonical effect and current-operation commit,
returns its actual receipt and one contextual fresh Detail, and does not query
List. Current equal input preserves revision/audit; stale equal input conflicts
before equality. The client explicitly queries List afterwards. Earlier
immutable List/Detail JSON retains its original values.

## R4: Structured failure and complete preservation

Readonly stage, unknown or duplicate scoped field, ASCII-blank title,
wrong value kind, stale observation and invalid List/Detail resource/screen
selectors return their existing structured failures. No successful placeholder
result or unsupported write is permitted. Each request has a predeclared
complete state oracle covering ordered logical root fields, unrelated root,
overflow bodies, native catalog/column types/rows/NULL, resident/memory state,
semantic Views and build counts. A rejected standard mutation preserves that
entire state. No expected value is inferred from a failure or post-action read.

## R5: Metadata and execution authority remain distinct

Mutation and Business descriptors are inert. An Enabled action grants no
authentication, factory permission or canonical Entity ownership. Anonymous
and factory-denied ordinary update/Business requests cannot write or refresh.
Foreign-owner List search follows existing owner visibility; Entity instance
commands follow canonical permissions. Aggregate/root-alias command policy
remains authentication-only; no invented owner-only rule is added.

## R6: Same-resource direct Business and explicit reload

After the selected standard update in the same session, ordinary
`business.requestReview` consumes the validated current Detail identity and
canonical positive revision as String Properties `resourceId`, `revision`.
It changes stage only to `review-requested`, with one canonical effect and
ordinary commit. It returns exactly ordinary JSON outcome, resourceId and
numeric revision, carrying no Display. No find/query or eager View rebuild is
caused by the Business response. Explicit Detail and List requests alone expose
the new managed revision and preserve the supplied title/description. With an
initial revision one, changed update yields two and Business yields three.
The Action remains displayed Enabled after the transition.

## R7: Separate capability profiles and actual Crud

UpdateOnly advertises read/query/update Supported and create/delete Unsupported;
ordinary create/delete return the existing operation-unsupported failure before
writes. Explicit Crud advertises all five Supported, consistently in update and
create bindings. Crud is a separate generic development scenario, not an ESS2-01
or production capability escalation.

One Crud session starts with selected root durably absent, creates with its
independently declared supplied identity, checks the actual Created receipt ID
before following its Detail/List, invokes direct Business, explicitly reloads,
then deletes with the returned current observation. Delete returns inert
reload-list and performs no implicit query or deleted Detail read. Explicit
List then establishes selected absence and unchanged unrelated state.

## R8: Commit durability, generated laws and native evidence

Postcommit projection/read failure cannot replay a standard mutation or Business
writer. Standard mutation retains a real pending receipt; Business retains its
ordinary response when a later explicit read fails. Explicit read recovery
shows the committed state with the original provider attempt count unchanged.
No receipt or Display is fabricated to make recovery appear successful.

REF-P1 generates total connected sequences over all targets, representations,
profiles and starting revisions 1..16 with a nonblank fixed title prefix plus
raw bounded UTF16 payload and arbitrary raw description, including empty. The
initial newline/NUL revision-sixteen witness runs across all six target/
representation combinations and both profiles. REF-P2 separately generates
current-noop/stale-equal/readonly/invalid-title cases with deterministic controls
for every branch. Both laws require at least 100 successes, one worker, zero
discards, and meaningful shrinking without filtering, sanitization or
catch-to-success. Pools own at most four live sessions and close every owner.

REF-PG1..3 require real managed PostgreSQL 17.11 (`170011`), `fsync=on` and
`synchronous_commit=on`, complete independent native evidence and owned-runtime
cleanup. SQLite/mock/unavailable/skipped execution cannot substitute.

| Executable specification | Requirement coverage |
| --- | --- |
| [Reference scenarios](../../src/test/scala/org/goldenport/cncf/display/DisplayEditingStudioReferenceSpec.scala) | REF-E1 R1/R2/R5; E2 R2/R3/R4; E3 R2/R3/R6; E4 R4; E5 R5; E6 R1/R7; E7 R2/R6/R7; E8 R3/R6/R8 |
| [Generated laws](../../src/test/scala/org/goldenport/cncf/display/DisplayEditingStudioReferencePropertySpec.scala) | REF-P1 R1/R2/R3/R6/R8; REF-P2 R3/R4/R8 |
| [Native acceptance](../../src/test/scala/org/goldenport/cncf/display/PostgresqlDisplayEditingStudioReferenceAcceptanceSpec.scala) | REF-PG1 R1/R2/R3/R6/R8; PG2 R1/R2/R6/R7/R8; PG3 R4/R5/R6/R8 |

No src/main, public schema/ABI, transport, transaction, security policy,
application Knowledge Candidate/KnowledgeHub/CaptureSession, Flutter/TFC/TFAF,
P96-10 context or Phase 98 behavior is introduced. Authored specifications do
not establish focused PASS, Step acceptance, Phase acceptance or consumer closure.
