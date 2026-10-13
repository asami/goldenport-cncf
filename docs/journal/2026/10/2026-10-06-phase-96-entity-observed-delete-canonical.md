# Phase 96 P96-07B2A canonical observed-delete authoring

Date: 2026-10-06.
Role: implementation history and development fixture/sample provenance.
This journal is not an independent acceptance or Step/Phase closure record.
Manifest: phase96-p96-07b2a-implementation-manifest-001 revision 1.
Branch: codex/phase-96-display-service.
Original Phase base: 6cbe877c53c116639feb11fa80546e79c051804c.
Current status: COD1–COD8 implementation/specification authoring; no new
validation executed by the bounded implementation worker.

## Frozen scope and rationale

The twenty owned paths add canonical typed observed deletion, its pure physical
overflow intent and active UnitOfWork integration over the existing B1 provider
port. Registered revision representation and established lifecycle helpers
remain server-owned. Typed result retains actual prior matched revision;
logical persisted revision advances once. Genuine provider comparison/publication
remains atomic inside the real provider, not in a preliminary canonical read.

The seven A update bridge files, B1 provider implementation/specs/design,
existing creation implementation and unrelated work are preserved. No public
route, Aggregate writer, ActionEngine, wire codec, presentation context, build,
dependency, shared directive, checklist or workflow control file was edited.

## Canonical development assembly and samples

EntityObservedDeleteCanonicalFixture registers a real collection, embedded or
detached binding, StandardEntityStore/EntityStoreSpace, actual ExecutionContext,
Component factory, resident realm, computed View and active UnitOfWork
interpreter. Collections use EntityCollectionId("test", "canonical", scenario
token). Typed CanonicalId constructs admitted materialized identities with
fixed 2026-10-06T03:04:05Z timestamp and selected/unselected entropy. IDs are not
parsed to choose ownership or lifecycle policy.

Fixture raw roots contain Before title, preserved business value, actual
owner/permission data, managed revision and a large canonical body. The body
exceeds the existing overflow threshold and uses the production policy's
cncf_content_body_overflow mapping and identity-derived content entry. Logical
fixtures contain aliveness; physical fixtures do not. These choices exercise
the existing soft-delete predicate, not a new universal lifecycle schema.
Clock and principal are fixed server fixtures; trace/correlation are actual
ExecutionContext values. The positive create case supplies absent optional ID
to existing ctx.idGeneration and admits INITIAL revision one.

EntityObservedDeleteCanonicalProviderFixture uses the real in-memory provider,
actual SQL provider and established native checkpoint/commit hooks. Counters
observe real dispatch. Invalid returned outcomes cover admission only and
cannot prove rollback after a buggy provider commit. SQLite temporary files
live beneath target/entity-observed-delete-canonical/work and independent
stores read the same file. Cleanup closes stores and removes only exact fixture
database/sidecar/directory paths. MySQL acceptance requires positive
Testcontainers mysql:8.4 execution and independent actual JDBC stores; no mock,
SQLite or H2 substitution supplies MySQL evidence. Ordinary owning pools close
through closeC; fault providers use their real fixture datasources.

## Authored scenario map

The [settled contract](../../../spec/entity-observed-delete-canonical.md) and
[design](../../../design/entity-observed-delete-canonical.md) link five suites:

- COD-E1–E8: detached physical and embedded logical prior/next semantics,
  complementary assembly matrix, real intervening canonical update, persisted
  and request admission, unsupported/capability/provider-domain/result bounds,
  actual memory checkpoints and positive generated-ID create/read/delete.
- COD-P1–P3: current-match prior/next and exact scope, stale complete-state
  preservation, distinct typed root/overflow correspondence. Each total bounded
  ScalaCheck law requires at least 100 successes and zero discards, without
  filters, implication or exception suppression.
- COD-S1–S4: independently reopened real file-backed SQLite physical/logical
  persistence, normal tombstone exclusion, real canonical update stale state
  and root/side/prepublication/commit rollback.
- COD-M1–M3: required live MySQL physical/logical independent visibility and
  actual intervening canonical update stale matrix plus native fault rollback.
- COD-U1–U6: actual Component resident and View success coherence, actual
  UserPermission owner/nonowner/factory policy and rebinding of only three
  target fields, stale reconciliation without success invalidation, genuine
  typed active program plus separate Local recording/planning, second-delete
  notfound/no replay, and appropriate cache preservation on prepublication
  failures.

Each scenario uses semantic should/which/in structure, afterWord metadata and
Given/When/Then at actual setup/action/expectation boundaries. All twenty-four
scenarios are authored, not claimed as executed or passing here.

## Historical prerequisites and pending parent execution

Parent-supplied actual B1 CF2 evidence is representative003 10/10 and the
27-suite accumulator002 215/215 passing, one disclosed existing pending test,
all 30 new cases and positive live MySQL 8.4. SBT and wrapper exits are zero
with terminal shared-lock release. The
[B1 journal](2026-10-06-phase-96-entity-observed-delete.md) retains the earlier
failures and repair history. These are prerequisite receipts, not evidence for
the newly authored canonical source or whole-Step independent acceptance.

The parent-selected next representative is UnitOfWorkObservedDeleteSpec,
followed only on success by the exact forty-one-suite accumulator with
CNCF_LIVE_MYSQL_TEST=true. The one SQLite baseline pending and two EntityStore
baseline pending diagnostics remain disclosed by that handoff; none is passing
proof. Required live MySQL cases must execute positively. Parent diff admission,
registered serialized validation, terminal lock release and independent review
remain pending; this worker performs none of them.

## Mandatory follow-up and debt

P96-07B2B must assess creation root/overflow/commit failure safety and separately
freeze any needed implementation. P96-07C must prove ordinary registered
Entity/Aggregate routes, explicit public authorization, canonical committed
outcomes and selected contextual reload through ActionEngine.afterCommitResponseC.
Delayed/failed projection must preserve the committed result without replay.
No global UnitOfWork abort rollback guarantee is introduced.

All four original P96-07 criteria remain OPEN. The later Steps, epoch full
Phase review, acceptance commits and release/checklist closure retain their
obligations. Same-file maintenance is limited to the new/touched declarations,
structured failure vocabulary, executable-spec authoring and source header
history. Existing oversized files and unrelated legacy naming/header debt stay
separate follow-up, with no unrelated refactoring or newly invented accepted
Hygiene/Development Candidate record. No SBT, tests, project scripts, servers,
network, self-review, staging, commit, push, publication or deployment ran in
this authoring pass.

## Parent focused-validation compile failure and frozen CF2 repair

Parent representative phase96-p96-07b2a-representative-001 completed normal
Information CML generation and production compilation (214 sources, then 72
incremental sources). Test compilation of 83 sources stopped with one E008 at
EntityObservedDeleteCanonicalPropertySpec.scala:59:69: Consequence has no
isFailure member. No suite or test executed. SBT and wrapper exited 1 with
terminal lock=released; the outer receipt executor's exit 0 means only that
the failed command result was recorded, not that validation passed.

Frozen CF2 P96-07B2A-CF2-CONSEQUENCE-FAILURE-PREDICATE replaces only that call
with the existing Consequence.isFaillure predicate, which means !isSuccess.
The exact stale-entity-revision reason, complete before/after equality, all
three total property generators, Given/When/Then structure, at least 100
successful samples and zero-discard expectations remain unchanged. No new
library API, production code, dependency or accepted behavior is introduced.

This is a bounded pre-review compile repair, not validation success or Step
acceptance. A fresh current-subject representative rerun and then the unchanged
41-suite live-MySQL accumulator remain pending. All 24 new behaviors, the
three disclosed baseline pending cases, B2B creation-failure assessment,
C registered writer/contextual reload, all four open P96-07 criteria and the
independent whole-Step/Phase/final-release gates remain required.

## Informed approval and resumed CF2 authoring

The parent received explicit informed approval for this exact two-file repair
on 2026-10-13 and recorded the resolved decision at control revision 106.
The actual original Goal is active again. The dedicated CNCF, Cozy and parser
checkouts received ai-directive integration commits; original Phase bases and
review/repair history remain unchanged. CF2 changes only the failure predicate,
required current-date test header and this history. No production edit, main
reflection, push or publication is part of this repair. Focused representative
and the unchanged 41-suite live-MySQL accumulator remain pending, followed by
the required independent whole-Step/Phase acceptance gates.

## 2026-10-14 focused history and frozen CF3 Sample ownership repair

Parent-supplied actual CF2 focused history retains representative002 for the
original subject2: UnitOfWorkObservedDeleteSpec executed six successful cases.
The current subject3 forty-one-suite accumulator/result002 executed 306
successful tests, zero failures, cancellations or ignored tests, and three
existing baseline pending cases. All 41 suites completed with zero aborts.
The three required live MySQL 8.4 suites executed sixteen positive cases.
SBT and wrapper exited zero with terminal lock=released. The parent retains
the local-only execution evidence; no execution-evidence package is added to
Git. These actual prior outcomes are historical prerequisites, not validation
of the source changed by CF3 or independent whole-Step acceptance.

Frozen CF3 P96-07B2A-CF3-INSTANCE-OWNED-SAMPLE moves the existing private final
case class Sample to the package-level companion
EntityObservedDeleteCanonicalPropertySpec in the same executable-spec file.
Its actual, logical and representation fields and constructor shape are
unchanged; the companion class imports the private type. The generators,
three property bodies, observations, exact stale-entity-revision reason,
isFaillure predicate, complete before/after equality, Given/When/Then
boundaries and at-least-100-success/zero-discard gates are preserved. The
single header now records @version Oct. 14, 2026 while retaining @since
Oct. 6, 2026. This separate conformance boundary is admitted under the
original whole-Phase source authority; it does not renew the completed
shared-runner-only recovery approval.

Changed-source focused execution of all three properties and independent
repair-conformance review remain pending with the parent, as do independent
whole-Step acceptance and the original final release gates. This bounded
editor ran no validation or self-review. CF2 remains focused-success history
awaiting product review; CF3 is a separately recorded candidate3. P96-07B2B
creation-failure safety assessment, P96-07C registered Entity/Aggregate routes
and contextual committed reload, all four open P96-07 criteria and later
Step/Phase obligations remain required. No B2B/C completion, P96-07 acceptance
or Phase closure is claimed.
