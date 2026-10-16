# Phase 96 ordinary Display update integration

Date: 2026-10-15
Document role: causal implementation history and development fixture samples.
Boundary: PHASE-96 / P96-07 / P96-07C3, manifest
`phase96-p96-07c3-normal-update-implementation-manifest-002@1`.
The [specification](../../../spec/display-mutation-service.md) and
[design](../../../design/display-mutation-service.md) own the selected contract.
This journal records causal history and fixture examples, not acceptance evidence.

## Why the ordinary integration is update-first

The earlier pure mutation boundary fixes typed preparation, strict request/
descriptor/result schemas, inert editor metadata and candidate correspondence.
The internal Entity bridge and observed Aggregate/UoW capture boundaries supply
canonical programs and authoritative outcomes. None alone exposes an ordinary
Display command or proves a current enclosing commit and selected contextual
read. This slice composes those existing owners through normal ComponentFactory,
Subsystem and ActionEngine rather than assigning transaction ownership to an
application callback.

The initial drafts are continued as uncompiled/unverified integration work;
their existence is not acceptance. The private current-operation witness uses
staged callbacks because CommitResult is Unit and a historical successful
termination cannot attest a new command whose commit action did nothing.
Known capture projection/coherence failure is retained separately from provider
acknowledgment so it can become committed ProjectionUnavailable without replay.

## Read provenance boundary correspondence

The actual final DisplayRuntime.Registry returns Consequence[Display], not a
Source-bearing result. Its selected Browser and projector hold the admitted
definition. Normal factory admission supplies the same definitions, read
Registry and resource mappings to both original service and mutation compiler;
the immutable compiled Entry supplies the admitted Source for that actual read.

This has two distinct executable boundaries. Actual postcommit lag and reachable
Browser/row failures run through normal command/commit/read. Foreign source,
definition or provenance is checked at typed registration/prepublication and
request admission, plus two real route-isolated components. Retained direct
Result/codec tampering checks remain candidate proof, not physical write or
query provenance. No foreign postcommit injection API or read-runtime change is
added to manufacture a branch the final Registry does not expose.

## Typed development fixture samples

`logicalui.DisplayMutationServiceDomain.Root` is a canonical EntityPersistable
with ordinary full Record read data. `PatchShape` has Update-typed fields;
the seed is all-noop. The fixture extends an admitted development definition
explicitly for native value coverage. It is handwritten development metadata,
not a new Cozy generator artifact or ABI change.

| Detail field | Public display value | Canonical patch target | Declared store codec |
| --- | --- | --- | --- |
| main/title | Text | Entity owner or registered Aggregate/alias owner, title | UTF16 code units encoded as a `utf16:` hexadecimal String |
| main/description | Text | description; explicit alias scenario targets title | Same UTF16 codec, including empty String |
| main/number | Number | number | BigDecimal canonical String preserving scale |
| main/flag | Boolean | flag | Exact `true` / `false` String |
| main/instant | Timestamp | instant | Instant canonical String preserving nanoseconds |
| main/nullable | Text or Missing | nullable | Text codec or existing Update.setNull |

These mappings are explicit fixture domain choices. They are not guessed
inverse bindings. The explicit decimal String codec avoids a declared generic
SQL numeric conversion through Double; the text codec preserves lone UTF16
surrogates across PostgreSQL UTF8 without claiming that a native database text
column can store invalid UTF8. Decoding is limited to the declared carrier
types, not arbitrary value.toString conversion. Physical SQL NULL is observed
only by the independent SQL reader; its logical Record field remains omitted.

Sample state retains a long content body (`日本 Ω`), unrelated root/body,
business, audit and lifecycle data. Selected examples include Japanese/Greek
text and surrogate units, empty description, decimal
`12345678901234567890.0012300`, false, Instant nanoseconds `987654321` and Missing
clear. The fixed clock is `2026-10-15T09:10:11.123456789Z`; the fixed principal is
owner. Declared audit and representation field names are chosen before action
for the independent expected delta, not inferred from returned mutation JSON.

Canonical Entity create is setup for the update scenarios only. Actual normal
Entity and Aggregate/alias operations then use the registered optimistic
Embedded/Detached collections, same current authorization/UoW, canonical
provider, real commit and contextual Browser/View. Normal managed PostgreSQL
configuration retains the existing server/fsync/synchronous-commit contract.
SQLite sessions own unique directories and exact database-file cleanup after
managed runtime shutdown. A shared two-component scenario reuses the actual
subsystem/physical reader, with distinct canonical values and separate Browser
traces.

## Authored executable specification organization

The Update suite documents Detail/descriptor/update/refreshed Detail, root and
alias authorization, no-op/max/stale ordering, original denial Conclusions,
selected callbacks/storage guards, committed pending states and two-component
isolation. The Ingress suite uses the actual raw Http4s route, ArgsIngress,
published MCP tools/call and registered SpiInvoker, including strict nested
JSON/header/observation rejection.

Four generated laws cover selected native patches/outcomes, current/stale/no-op
state/no replay, zero-effect vetoes and actual strict adapter correspondence.
Their generators are total and bounded with explicit shrinking and no discards.
The PostgreSQL suite uses the real native provider/independent connection and
the Commit Observation suite exercises actual current versus absent/historical
witness, abort/rejection/cleanup and retained capture faults. All behavior
examples place Given/When/Then at their setup/action/expectation boundaries and
use should vocabulary. This is an authoring organization, not a run result.

## Common ingress context correspondence amendment

An earlier representative attempt remained unaccepted after the complete
Aggregate authorization, adapter clock/full-ID and generated adapter-law
correspondence failures. The intervening twelve authoring corrections also
remain unaccepted pending the selected execution and independent assurance.
The corrected Aggregate hypothesis is a missing outer `update` declaration in
the development fixture. Authorization already selects the public Action/
request operation correctly; the fixed `changeFields` name supplies access-kind
evidence. Declaring both operations preserves the original domain declaration
without changing that canonical selector.

The fixture now explicitly represents its owner as signed in under the selected
ControlledTest profile. For example, valid HTTP/MCP input carries User privilege,
owner, `cncf.security.authenticated=true` and canonical full trace/correlation
strings. The negative sends false for the same owner at the current revision
after a positive write. Direct custom Execution removes its fixture token for
that negative. This separately admitted fixture amendment supersedes the prior
correspondence repair's prohibition on adding flags; that earlier restriction
and its unaccepted attempt remain historical. No production provider, System or
ServiceInternal authority is fabricated.

Fresh default runtime spaces and an explicit Global/manual-profile ancestry
address the fixture's clock and store correspondence. The component's actual
unbound datastore is the single fallback inherited by custom and adapter paths;
managed external-required SQL binding remains authoritative. Constructing a
RuntimeConfig from configuration was excluded because it opens a second
unmanaged SQL provider. Subsystem acquisition cleanup owns its created runtime
and SQLite directory, and shared sessions retain their existing Subsystem.

The common [specification](../../../spec/ingress-context-correspondence.md) and
[design](../../../design/ingress-context-correspondence.md) now fix restoration
of complete parsed timestamp/entropy values and final ingress context binding
to its current UnitOfWork. Independent identity/ingress scenarios and total laws
use input-declared canonical strings; E2 and H7 retain complete state oracles
for authenticated success and unauthenticated zero-effect rejection. These
authoring changes supply no execution, review or Phase-closure claim.

## Authentication carrier and MCP kind correspondence amendment

The next unaccepted representative attempt exposed distinct authorization and
adapter premises. The generic ActionEngine Consequence wrapper rebuilt a
Conclusion from its exception carrier, and the default hook could throw an
attached Throwable instead of preserving the supplied structured failure. The
frozen amendment retains the existing protected hook, calls it once and extracts
the original Failure Conclusion from its structured carrier. Unexpected
NonFatal overrides retain the existing conversion. The normal authorization
specification now authors exact object/full-component preservation, prior and
Throwable-bearing causes, hook success/delegation/fallback, no-start evidence
and a total reducing supplied-Conclusion law.

The earlier E2/H7 negative oracle expected the later Aggregate domain-command
authentication text. Normal ActionCall authentication actually runs first and
names the full component Display update request; it produces no UnitOfWork
factory authorization event. E4 also incorrectly treated an authenticated-only
Aggregate declaration as owner-only. Its canonical-access premise now uses an
unauthenticated exact owner for Aggregate/alias and the existing foreign-owner
denial for Entity. Factory, operation, domain and transition denials retain
their original boundaries and Conclusions.

H7's ordinary HTTP/MCP paths do not install custom Execution's ObservedSpace,
which alone emits the fixture `capture` trace. Its rejected count assertion did
not establish execution of the later negative branches. The amended eight-case
scenario uses the existing one Aggregate load, complete positive authorization
vector, actual current committed UnitOfWork result/termination, receipt, domain
command/validation, contextual find and independent complete state instead.
No capture callback or receipt is fabricated and the ordinary adapter is kept.

MCP previously converted every JSON argument kind to a String before the
Display ingress could reject object payloads, losing the original kind boundary.
The server-owned actual Display Operation marker now declares String inputs
after published catalog admission and before generic normalization. H4 keeps
duplicate raw Strings as strict tool failures while object payloads are exact
-32602 protocol failures without results. H8 authors the update/descriptor
member-by-member object/array/number/Boolean/null matrix and complete zero-effect
state checks. A real unmarked `display.update` QueryAction regression scenario
retains the existing generic conversions through the normal admitted subsystem.

These are source/specification amendments to unaccepted hypotheses. Earlier
failed expectations remain history; these changes claim no fresh execution,
independent assurance, Step acceptance or Phase closure. The current logical SQL
NULL omission boundary remains unchanged; no domain Option API is introduced.

## Unchanged remaining ownership

No original P96-07 criterion closes here. Positive Display create/delete and
lifecycle/List/PG-E12 proof, P96-08 through P96-11, pending exact Step commits,
later independent whole-Step acceptance, the sole full-Phase review and final
validated release/aggregate obligations remain required. Parent-owned selected
validation and fresh whole-boundary assurance remain separate from authoring.

Existing large Http4sHttpServer and Subsystem size/legacy-name debt remains
preserve-only Hygiene; no broad extraction or cleanup belongs to this slice.
No core/parser/Cozy/application/build/version/dependency or Phase/checklist
changes are introduced by this implementation boundary.

## E8 independent isolation oracle amendment

AUX-P96-C3-E8-001 identifies an executable-assurance gap, with no proven
production fault. E8 already compared shared physical state for the selected
successful route, but B's expected resident/View state was sampled after A's
action, and reciprocal and rejected-route checks did not compare both complete
states. The amended setup fixes both components' logical and resident/View
expectations before A's read, descriptor and update. B's declared physical delta
composes with A's independently declared delta, so the final shared physical
oracle includes exactly both allowed successful writes.

Each successful route now checks the other component's complete state. Each
of the three foreign selector variants and the actual B-source/A-target
rejection checks both components' logical, physical, resident/memory, semantic
View and build-count state against the predeclared successful baseline. Existing
result, row, command, provenance and zero-effect assertions remain in place.

Focused whole-UpdateSpec validation and fresh independent focused closure remain
pending. Prior validation history remains attributable to its earlier subject.
Original P96-07/create/delete/lifecycle/List/native PG-E12, later Step acceptance
and commits, the sole full-Phase review, final release and aggregate-validation
obligations remain unchanged; this amendment claims no acceptance or closure.
