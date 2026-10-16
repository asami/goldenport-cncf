# Phase 96 canonical Entity create failure safety

Document role: investigation history and development fixture/sample choices.
Worker handoff status: reproduction-not-executed. Source authoring is complete;
compilation, behavioral reproduction, independent review and acceptance remain
parent-owned. This entry introduces no normative specification or acceptance
claim.

## Original requirement and frozen authoring boundary

The original [P96-07 checklist](../../../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
requires positive canonical create/delete lifecycle proof and preservation of
resources after authorization, validation, conflict and capability failures.
[R1, R2 and R5](../../../spec/entity-conflict-and-conditional-transition.md)
retain framework-owned initial revision one, assembled representation and
managed-field rejection; R13 and R14 retain provider failure safety and the
canonical UnitOfWork chokepoint. The
[runtime design](../../../design/entity-conflict-and-conditional-transition.md)
owns the execution boundary. The remaining integration obligations are also
recorded in [UB13](../../../spec/display-mutation-bridge.md#ub13-remaining-original-obligations).

P96-07B2B1 implements the frozen reproduction-first manifest
`phase96-p96-07b2b1-implementation-manifest-001@1`: four new executable
specification/fixture files and this journal, with the existing canonical
fixtures and production code retained. It belongs to PHASE-96, Step P96-07,
Slice P96-07B2B1 under the original Phase authority, original repository bases,
PLAN epoch one and retained prior review and repair history. It does not close
any of the four original Step criteria.

## Investigation and authoring chronology

On October 14 the bounded editor read the frozen manifest, shared naming,
type ownership, executable specification and header instructions, repository
rules and guidance, and the directly affected canonical create and ContentBody
paths. The source inspection identified the existing generic `create` and
`save` entry points used by canonical create; this determined where the
manifest's pre-write rejection adapters are attached. No production correction
was selected or implemented.

The fixture reuses `EntityObservedDeleteCanonicalFixture` and its actual
`EntityStoreSpace`, `StandardEntityStore`, revision registration, Component
factory, resident collection and View. The providers are the existing real
memory provider and file-backed `SqlDataStore` with SQLite. Newly authored
adapters override only generic create/save, reject one exact collection/entry
before delegation after seeding, and retain all state in the underlying real
provider. Root rejection follows a successful side save; side rejection occurs
before the side write. Generic create is not represented as invoking a
versioned checkpoint or native atomic mutation port.

The snapshot includes the attempted selected root and overflow plus both
preexisting fixture roots and both preexisting overflow rows as complete maps.
Fresh failures use a typed `CanonicalId` within the fixture collection, separate
from its selected and unselected preexisting identities. Duplicate attempts
target the existing selected identity. A local Record-backed input carries an
optional identity and permits truly absent content, explicit `Update.SetNull`
and deliberate invalid managed revision input without changing existing codecs.

SQLite databases use the existing task-owned temporary database lifecycle.
Every observation opens an independent provider on that same file, and the
faulting writer closes in `finally` before the enclosing database cleanup.
The positive durable scenario assembles a fresh canonical reader and hydrates
through EntityStoreSpace. Memory and SQLite snapshots use their own provider's
record representation; no cross-provider normalized map is substituted for
authoritative read-back.

## Frozen scenario and sample choices

| Scenario | Observable law and sample choice |
| --- | --- |
| CFS-E1 | Generated identity, admitted record, INITIAL one and exact large hydrated content for Embedded and Detached; all preexisting maps retained. |
| CFS-E2 | Existing overflow root plus a different large candidate; native duplicate taxonomy and every complete record retained. |
| CFS-E3 | Same duplicate identity with a small inline candidate; original overflow retained. |
| CFS-E4 | Independent absent-content and SetNull attempts, each with its own fixture, operation and full comparison. |
| CFS-E5 | Fresh typed root create rejected before write after successful real side save; no root or orphan body. |
| CFS-E6 | Fresh canonical side save rejected before write; root create unreached and all records retained. |
| CFS-E7 | Application-supplied Embedded `revision` and Detached `cncf_revision` each fail before side/root effects. |
| CFS-E8 | Actual Free EntityStoreCreate program with UserPermission `create` grant denied by real Component.Factory; stores, primed resident and cached View retained. |
| CFS-S1 | Large duplicate candidate with fresh independent durable read-back of every selected and unrelated row. |
| CFS-S2 | Inline, absent and SetNull each execute separately in a fresh seeded SQLite database and compare their own durable before/after maps. |
| CFS-S3 | Keyed fresh root rejection after real side save; independent reader requires no orphan body. |
| CFS-S4 | Keyed fresh side rejection; independent reader requires no root and unchanged durable records. |
| CFS-S5 | Generated identity and INITIAL one persist in both representations; independent canonical reader hydrates the exact body and retains old rows. |
| CFS-P1 | Total generator covers both representations, old managed revisions 1–1,000,000 and all four candidate body modes; duplicate taxonomy and full snapshots retained. |
| CFS-P2 | Total generator covers both representations and large text; real root rejection preserves fresh absence and all preexisting rows. |

Large property text repeats one of `x`, `日` or `λ` 4,200–9,000 times, remaining
strictly above the existing 4,096-byte inline threshold without filtering or
discard. Each sample creates and seeds a fresh real memory provider. Data types
and closed body/fault alternatives belong to the stable package-level fixture
object. Both properties request at least 100 successful cases and zero discard;
a real failing law may stop early and must retain its counterexample rather
than acquire a fabricated success count. Given/When/Then appear at the actual
deferred property setup and canonical operation boundaries as well as the
Test.check and final expectation boundaries.

## Remaining original work

Parent compilation and reproduction have not run at this handoff. Desired
preservation laws remain unchanged even when current behavior fails them;
failures must be causal evidence for the subsequent parent-frozen B2B2 repair,
not canceled, weakened, inverted or treated as acceptance. Native logs,
counterexamples, receipts and local evidence remain outside Git.

B2B2 retains production remediation, provider-owned atomicity and required real
MySQL create/commit rejection or indeterminate semantics for the actual selected
port. These obligations are not waived by memory/SQLite reproduction. Slice C
retains registered Entity/Aggregate integration, committed outcome acquisition
and actual contextual reload through `afterCommitResponseC`, alongside ordinary
mutation routes and all four P96-07 criteria. The original aggregate-validation
handoff and sole full-Phase review obligation remain open. No Step/Phase
counter, review history, original base or acceptance status changes here.
