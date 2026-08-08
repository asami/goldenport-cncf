# Phase 56 CID-07 CAR Lint and Development Migration Plan

## Status

- Phase: 56
- Step: CID-07 CAR Lint and Development CAR Migration
- Step status: DONE / COMMITTED
- Current Slice: CID-07E Complete Inventory Acceptance
- Slice status: ACCEPTED / REVIEWED
- Entry evidence: CID-06 committed as
  `6afab962ccd33431e6e2944c8d9191295d1a7f38`
  (`Preserve bounded component identity compatibility`)
- Phase full validation: explicitly waived at the Phase 56 release gate

## Goal

Make the frozen Phase 56 CAR migration policy executable without creating a
second Component identity authority. Cozy CAR lint must consume the shared
CNCF identity and exact deferred-release classification, report stable
version-sensitive migration findings, and fail closed on disagreement or
inventory errors. Every one of the 14 frozen SNAPSHOT CAR repositories must
then author canonical `namespace + id` identity and pass lint. The four exact
released CARs remain unchanged and appear as lint-visible next-version work.

## Frozen Authority

The inventory authority is
`phase-56-cid01-car-migration-ledger.yaml`: 18 CAR repositories, comprising 14
`snapshot-required-migration` records and four
`current-release-deferred` records. The exact four release deferrals are also
the runtime authority in
`META-INF/cncf/component-identity-deferred-release-registry.json`.

CID-07 must not copy those four entries or reimplement version comparison in
Cozy. CNCF exposes one bounded Java-callable/package API over the already
validated registry and classification. Cozy maps that typed result to lint
levels, codes, and actionable diagnostics. sbt-cozy may transport existing
canonical project evidence, but it must not define another classifier.

## Required Classification

| Project state | Lint level | Required result |
| --- | --- | --- |
| Canonical `project.namespace + project.id` with every authored projection equal | OK | Report exact qualified identity, effective version, and expected projections. |
| Legacy identity with an effective SNAPSHOT release | FAIL | `migration-required`; report owner and `project.yaml` migration path. |
| Legacy identity at one exact registered current release | WARN | `deferred-to-next-version`; do not invalidate or rewrite the release. |
| Registered deferred artifact advanced to a SNAPSHOT or higher stable release | FAIL | `migration-required`; exact release equality is the only deferral. |
| Lower, malformed, or incomparable effective release | FAIL | `inventory-error`; never classify as deferred or migration-required. |
| Canonical identity with an authored derived disagreement | FAIL | `projection-disagreement` regardless of release kind. |
| Partial canonical fields, unknown registry fields, or registry/ledger mismatch | FAIL | Stable fail-closed identity/inventory diagnostic. |

Every finding includes effective version, identity shape, canonical identity
when known, expected organization/artifact/JVM package/generated class/path,
migration status, migration owner, and an actionable file path. Lint never
guesses a namespace and never rewrites project metadata.

## Frozen Cohorts

### SNAPSHOT migration required

| Repository | Effective version | Canonical identity | Artifact projection | Initial collision state |
| --- | --- | --- | --- | --- |
| `textus-ai` | `0.2.1-SNAPSHOT` | `org.simplemodeling.textus.AiRuntime` | `textus-ai-runtime` | clean |
| `textus-art-scene` | `0.1.2-SNAPSHOT` | `org.simplemodeling.textus.ArtScene` | `textus-art-scene` | unrelated docs/output only |
| `textus-aws` | `0.0.1-SNAPSHOT` | `org.simplemodeling.textus.Aws` | `textus-aws` | existing `project.yaml` port hunk |
| `textus-blog` | `0.0.3-SNAPSHOT` | `org.simplemodeling.textus.Blog` | `textus-blog` | existing `project.yaml` port hunk |
| `textus-bok` | `0.1.0-SNAPSHOT` | `org.simplemodeling.textus.Bok` | `textus-bok` | unrelated journal only |
| `textus-cbd-support` | `0.1.0-SNAPSHOT` | `org.simplemodeling.textus.CbdSupport` | `textus-cbd-support` | existing `project.yaml` port hunk plus unrelated work |
| `textus-control-center` | `0.1.0-SNAPSHOT` | `org.simplemodeling.textus.ControlCenter` | `textus-control-center` | unrelated journal only |
| `textus-knowledge-editor` | `0.1.3-SNAPSHOT` | `org.goldenport.textus.KnowledgeEditor` | `textus-knowledge-editor` | existing `project.yaml` port hunk |
| `textus-scraper` | `0.1.1-SNAPSHOT` | `org.simplemodeling.textus.Scraper` | `textus-scraper` | clean |
| `textus-semantic-integration-engine` | `0.2.0-SNAPSHOT` | `org.simplemodeling.textus.SemanticIntegrationEngine` | `textus-semantic-integration-engine` | existing `project.yaml` port hunk plus unrelated work |
| `textus-supervisor` | `0.1.0-SNAPSHOT` | `org.simplemodeling.textus.Supervisor` | `textus-supervisor` | unrelated journal only |
| `textus-toolchain-runner` | `0.2.1-SNAPSHOT` | `org.simplemodeling.textus.ToolchainRunner` | `textus-toolchain-runner` | clean |
| `textus-user-account` | `0.6.0-SNAPSHOT` | `org.simplemodeling.textus.UserAccount` | `textus-user-account` | clean |
| `textus-user-notification` | `0.6.0-SNAPSHOT` | `org.simplemodeling.textus.UserNotification` | `textus-user-notification` | clean |

The five `project.yaml` collision repositories initially failed closed because
their port changes had separate ownership. On Aug. 8, 2026, the user authorized
including those changes when they match the official allocation. Textus
Control Center's current
`docs/spec/default-server-port-registry.md` assigns AWS `18003`, Blog `18004`,
Semantic Integration Engine `18006`, Knowledge Editor `18007`, and CBD Support
`18013`; every current hunk matches exactly. The port changes are therefore
admitted into CID-07D and will be reviewed, validated, and committed with the
identity migration. Every other unrelated dirty path remains excluded.

### Exact release deferrals

| Repository | Current release | Canonical next-version identity | Legacy artifact | Owner |
| --- | --- | --- | --- | --- |
| `textus-corpus` | `0.1.0` | `org.simplemodeling.textus.Corpus` | `textus-corpus` | `textus-corpus` |
| `textus-experiment` | `0.1.0` | `org.simplemodeling.textus.Experiment` | `textus-experiment` | `textus-experiment` |
| `textus-georesolver` | `0.2.1` | `org.simplemodeling.textus.GeoResolver` | `textus-georesolver` | `textus-georesolver` |
| `textus-sanpomap` | `0.2.1` | `org.simplemodeling.textus.Sanpomap` | `textus-sanpomap` | `textus-sanpomap` |

These four source releases are linted but not rewritten, regenerated,
republished, or committed solely for Phase 56 identity migration.

## Slice Plan

### CID-07A — Shared classifier and Cozy lint contract

1. Extend the shared CNCF identity ABI with a bounded migration-classification
   request/result surface backed by the existing strict registry.
2. Add `CozyCarIdentityLint` and integrate it into `CozyCarLint` without
   reimplementing registry parsing, identity projections, or release ordering.
3. Add executable specs for canonical, SNAPSHOT legacy, exact deferred,
   advanced SNAPSHOT, higher stable, lower, malformed, incomparable,
   disagreement, partial canonical, and registry-parity cases.
4. Keep `--strict` behavior unchanged except that identity FAIL findings
   always fail and exact-deferred WARN remains visible.

CID-07A now centralizes the exact four-entry registry and version-sensitive
decision surface in `cncf-collaborator-api`, adapts the existing CNCF runtime
contract to that shared authority, and exposes the typed result through Cozy
CAR lint. Review-fix closed order-dependent JSON parsing, duplicate authored
projection evidence masking, the changed Cozy spec header, and its normative
specification reference. Final focused evidence on the repaired worktree is:

- collaborator API `22292-20260808T070443Z`: 27 tests succeeded, no warnings,
  exits zero, lock released;
- collaborator API `publishLocal` `22502-20260808T070455Z`: published
  `0.2.0-SNAPSHOT`, no warnings, exits zero, lock released;
- CNCF `22679-20260808T070510Z`: 2 suites and 23 tests succeeded; the run
  reported pre-existing `ComponentDescriptor.scala` indentation and
  deprecation warnings, exits zero, lock released;
- Cozy corrective `23433-20260808T070648Z`: 2 suites and 30 tests succeeded
  without warnings, exits zero, lock released.

Independent focused re-review first found one stale Cozy E7 checklist owner/rule
row. After correction to `CID07-R1` and the current classifier/lint/spec paths,
the corrective re-review returned PASS with no findings and
`FULL_REVIEW_REQUIRED=no`. CID-07B through CID-07D are recorded below;
CID-07E was subsequently accepted/reviewed and included in the completed
CID-07 Step commit.

### CID-07B — Canonical scaffold/build migration and User Account exemplar

1. Freeze the canonical CAR source shape from the existing Cozy scaffold:
   `project.namespace`, `project.id`, component display/version metadata, and
   authored derived fields only as validated evidence.
2. Update generated `build.sbt`/`ProjectYamlBuild.scala` wiring to consume
   `CozyProjectIdentityContract` evidence rather than legacy
   `project.organization`, `project.name`, and `project.component.name`.
3. Migrate `textus-user-account` completely, including canonical source CAR
   descriptor/ABI evidence and generated-Core/runtime assertions, while
   preserving `textus-user-account` Web routes as compatibility aliases.
4. Prove the required User Account projections and one namespace-isolated
   same-local-ID lint fixture.

CID-07B now carries canonical project identity from `project.yaml` through the
sbt-cozy bridge, Cozy/SimpleModeler generation context, generated qualified
`ComponentId`, schema-3 descriptor, ABI v2, and the initialized User Account
component. The compatibility route URLs remain stable while their authored
component targets and primary Help/manual selectors use
`org.simplemodeling.textus.UserAccount`.

Focused implementation evidence on the then-uncommitted CID-07B worktree was:

- SimpleModeler `38242-20260808T074521Z`: 1 suite and 2 tests succeeded;
  `publishLocal` `38436-20260808T074538Z` published `1.1.26-SNAPSHOT`;
- Cozy `40022-20260808T074955Z`: 4 suites and 71 tests succeeded;
  `publishLocal` `40358-20260808T075039Z` published `0.3.2-SNAPSHOT`;
- sbt-cozy `40696-20260808T075125Z`: 1 reported suite and 27 tests succeeded;
  `publishLocal` `40900-20260808T075141Z` published `0.1.19-SNAPSHOT`;
- User Account `42502-20260808T075505Z`: the CID-07B executable acceptance
  example succeeded, with only the intentional development `cozyCarName` key
  warning;
- post-fix `cozy lint car --format json` exited zero, emitted one valid JSON
  object, and reported canonical
  `org.simplemodeling.textus.UserAccount`; its two non-blocking warnings are
  the development SNAPSHOT plugin and the absent historical ABI baseline.

Independent focused review found two bounded groups: RF-CID07B-001 required an
actual `CozyCarLint.lint` namespace-isolation example for equal local IDs, and
RF-CID07B-002 required same-month Scala header compression plus the missing
`ProjectYamlBuild.scala` header. E8 now lints two real project roots with local
ID `UserAccount` under distinct namespaces and proves two separate canonical
OK results. Review-fix validation passed:

- Cozy `49328-20260808T081214Z`: 3 suites and 55 tests succeeded, no compile
  warnings or failures, exits zero, lock released;
- User Account `49550-20260808T081236Z`: 1 suite and 1 test succeeded; the only
  warning was the intentional development `cozyCarName` key, exits zero, lock
  released.

Independent focused re-review returned PASS with no findings and
`FULL_REVIEW_REQUIRED=no`; RF-CID07B-001/002 are closed and CID-07B is
ACCEPTED / REVIEWED. At that CID-07B checkpoint, CID-07C was recorded below,
CID-07D was accepted/reviewed, CID-07E awaited Step full review, and no CID-07
commit or Phase full-validation claim had yet been made.

### CID-07C — Noncolliding SNAPSHOT cohort

Migrate and validate the nine repositories whose `project.yaml` has no
pre-existing conflicting hunk: `textus-ai`, `textus-art-scene`, `textus-bok`,
`textus-control-center`, `textus-scraper`, `textus-supervisor`,
`textus-toolchain-runner`, `textus-user-account`, and
`textus-user-notification`. Preserve every unrelated dirty path unstaged.

CID-07C review fixes RF-CID07C-001 through RF-CID07C-008 are applied and
focused validation of the repaired worktrees is complete. All nine projects
now author `project.namespace + project.id`, schema-3 component descriptors,
ABI v2 evidence, project-derived build coordinates, and generated qualified
component identities. The downstream migration also closed strict framework
boundary findings in the ArtScene fixture, Control Center and Supervisor
configuration access, canonical ArtScene CAR dependency/assembly metadata,
and User Notification identity assertions. BoK's five predefined-scalar lint
failures were repaired by adding explicit bounded domain contracts; its
remaining nominal-wrapper findings are warnings and no project has an identity
FAIL.

The review-fix pass applies:

- RF-CID07C-001 canonical User Notification component targets and primary
  Help/manual selectors while retaining legacy URL aliases;
- RF-CID07C-002 actual ArtScene schema-3 descriptor assertions;
- RF-CID07C-003 admitted public/protected parameter naming;
- RF-CID07C-004 current compressed Scala headers;
- RF-CID07C-005 canonical executable-spec metadata and semantic grouping;
- RF-CID07C-006 target-isolated actual publisher lifecycle evidence; and
- RF-CID07C-007 checklist truth limited to representative first-party CARs and
  the same-local-ID fixture; and
- RF-CID07C-008 canonical BoK ABI dependency identity aligned between
  `project.yaml` and the ABI v2 manifest.

The following focused executable evidence predates this review-fix and is
historical only; it is not validation evidence for the repaired worktrees:

- Textus AI `56453-20260808T083220Z`: 2 suites, 26 tests succeeded;
- Scraper `64539-20260808T085133Z`: 1 suite, 3 tests succeeded;
- ArtScene isolated dependency acceptance `75354-20260808T091809Z`:
  3 suites, 8 tests succeeded after exact Scraper CAR publication into the
  target-local warehouse (`70824-20260808T090716Z`);
- BoK corrective `87430-20260808T095050Z`: 1 suite, 1 test succeeded;
- Control Center corrective `81667-20260808T093448Z`: 1 suite, 3 tests
  succeeded;
- Supervisor corrective `82759-20260808T093733Z`: 1 suite, 2 tests succeeded;
- Toolchain Runner `84531-20260808T094322Z`: 1 suite, 7 tests succeeded;
- User Notification corrective `85662-20260808T094623Z`: 1 suite, 24 tests
  succeeded;
- User Account remains covered by accepted CID-07B evidence `49550`.

Every historical SBT marker had `sbt_exit=0`, `wrapper_exit=0`, and
`lock=released`. Normal `cozy lint car --format json` was executed for all
nine repositories. Each reports its exact canonical qualified identity; all
historical lint invocations exited zero with no FAIL findings. The development
SNAPSHOT plugin and missing historical ABI baseline remain visible warnings,
and project-specific documentation/CML hygiene warnings remain nonblocking.
No external CAR publication or release mutation occurred. The one Scraper
publication was confined to
`textus-art-scene/target/cncf-test/work/phase56-cid07-local-repository` and
did not use the user-wide local warehouse.

Corrective post-review-fix evidence is:

- Cozy `98391-20260808T101930Z`: 2 suites, 31 tests succeeded, superseding
  test-compilation failure `97994-20260808T101833Z`;
- ArtScene `98798-20260808T102033Z`: 3 suites, 8 tests succeeded;
- Textus AI `99544-20260808T102240Z`: 1 suite, 25 tests succeeded;
- BoK `99900-20260808T102327Z`: 1 suite, 1 test succeeded;
- Control Center `203-20260808T102340Z`: 1 suite, 3 tests succeeded;
- Scraper `472-20260808T102401Z`: 1 suite, 3 tests succeeded;
- Supervisor `816-20260808T102435Z`: 1 suite, 2 tests succeeded;
- Toolchain Runner `1016-20260808T102451Z`: 1 suite, 7 tests succeeded; and
- User Notification `1185-20260808T102505Z`: 1 suite, 24 tests succeeded.

The first focused re-review closed RF-CID07C-001 through RF-CID07C-007 but
found RF-CID07C-008: BoK still authored its non-empty ABI dependency with a
legacy `name`, while canonical Cozy packaging consumes exact `namespace` and
`id`, and the ABI manifest recorded an empty dependency list. The bounded
repair now declares `org.simplemodeling.textus.SemanticIntegrationEngine` in
both authorities. Validation `8279-20260808T104439Z` built
`target/textus-bok-0.1.0-SNAPSHOT.car` and ran the BoK component spec: 1 suite,
1 test succeeded, `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.

Every final SBT marker has `sbt_exit=0`, `wrapper_exit=0`, and
`lock=released`. Corrective User Notification Cozy CAR lint exited zero with
valid JSON, the exact canonical identity, and no FAIL finding. Independent
focused re-review closed RF-CID07C-008 with PASS, no findings, and
`FULL_REVIEW_REQUIRED=no`. RF-CID07C-001 through RF-CID07C-008 are closed and
CID-07C is ACCEPTED / REVIEWED. At that CID-07C checkpoint, its commit,
CID-07E Step full review, the CID-07 Step commit, and Phase full validation
remained pending; CID-07D was ACCEPTED / REVIEWED.

Existing Debt (Separate Follow-up):

- Cozy `CarCmlSourceResolver.Resolved.projectrelativepath` requires a
  coordinated field/call-site rename and remains unchanged in CID-07C.
- BoK nominal-wrapper findings remain WARN-only debt.
- Protected unrelated worktree paths remain unchanged and outside CID-07C.

### CID-07D — Collision cohort

Status: ACCEPTED / REVIEWED.

The collision gate is cleared by exact registry evidence and user authority.
Migrate `textus-aws`, `textus-blog`, `textus-cbd-support`,
`textus-knowledge-editor`, and `textus-semantic-integration-engine`, including
their registry-conformant port changes. The official port-inclusion authority
is Textus Control Center's `docs/spec/default-server-port-registry.md`; the
five exact current hunks are explicitly user-authorized CID-07D scope. All
other unrelated dirty paths remain outside CID-07D ownership.

REVIEW_FIX findings `CID07D-R1` (stale Scala `@version` headers) and
`CID07D-R2` (Knowledge Editor assembly-identity executable-spec structure) are
applied. Pre-fix final focused validation evidence is AWS `47205` (2/2), Blog
`47523` (30/30), CBD `36824` (14/14), Knowledge Editor `41016` (124/124),
framework `44150` (27/27) plus `publishLocal` `44394`, and SIE `49993`
(33/33); every invocation exited zero with its lock released. Final Cozy CAR
lint for all five collision repositories exited zero with no FAIL findings.
Post-review-fix focused validation invocation
`58134-20260808T130002Z` used the exact serialized wrapper command
`/Users/asami/.codex/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh --batch 'cozyBuildCar; testOnly org.goldenport.textus.knowledge.editor.ComponentFactorySpec'`.
The CAR was built; one suite completed with 125 tests succeeded and zero
failed, aborted, canceled, ignored, or pending, with `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. Nonblocking warnings were unused
`cozyCarName`, SNAPSHOT, mutable-pair, and nine deprecation warnings.
Post-fix full review returned PASS with Actionable findings 0;
`CID07D-R1` and `CID07D-R2` are CLOSED and `FULL_REVIEW_REQUIRED=no`.
CID-07D is ACCEPTED / REVIEWED. At that CID-07D checkpoint, its commit,
CID-07E Step full review, the CID-07 Step commit, and Phase full validation
remained pending.

### CID-07E — Complete inventory acceptance and Step commit

Status: ACCEPTED / REVIEWED.

1. Run integrated CAR lint for all 18 frozen records.
2. Require 14 canonical/lint-clean SNAPSHOT repositories and exactly four
   deferred warnings whose coordinates, owners, and triggers match both
   machine-readable authorities.
3. Prove an advanced deferred release becomes migration-required without
   changing the original released source.
4. Reconcile the ledger, plans, Phase/checklist status, and compatibility
   notice-removal ownership.
5. Run behavior-focused cross-repository validation, independent review,
   conditional review-fix/re-review, then create the CID-07 Step commit(s) in
   dependency order. Do not run the Phase full suite here.

The frozen inventory is now fully classified: 18/18 records. All 14/14
SNAPSHOT CARs are canonical and final lint has no FAIL findings, based on the
accepted Slice evidence: User Account from CID-07B, eight CARs from CID-07C,
and five CARs from CID-07D. CID-07D remains ACCEPTED / REVIEWED. The exact
released deferrals are `textus-corpus` 0.1.0, `textus-experiment` 0.1.0,
`textus-georesolver` 0.2.1, and `textus-sanpomap` 0.2.1. Each corrective lint
exits zero, emits valid JSON with the identity-deferred WARN and no FAIL, and
the four source repositories were not modified.

TEST_FIX #1 records the compatibility-metadata correction. The initial four
deferral lints classified identity correctly but exited 1: Corpus and
Experiment reported `ReleaseGenerationPairRejected` (authored 0.3.0 versus
executing 0.3.4-SNAPSHOT), while GeoResolver and Sanpomap reported
`CozyVersionMissing`. The Cozy lint fix downgrades only the typed
`CozyVersionMissing` and `ReleaseGenerationPairRejected` diagnostics to WARN
when the exact identity code is
`CAR_COMPONENT_IDENTITY_MIGRATION_DEFERRED`; every other state and diagnostic
remains FAIL. Executable examples E-CID07E-1 through E-CID07E-3 cover Corpus,
GeoResolver, and the canonical User Account negative case.

Validation `63778-20260808T131546Z` used the exact serialized wrapper
`testOnly CozyCarLintSpec + Phase56ProjectIdentityContractSpec`: two suites,
34 successes, zero failures/aborted/canceled/ignored/pending, `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. The four corrective Cozy lints exited
zero with valid JSON, no FAIL, empty stderr, and no source mutation.

STEP REVIEW_FIX #1 (`CID07-FR-001`) repairs a fail-open registered legacy
identity path: a registered artifact previously validated `legacyLocalId` only
for the exact deferred release, so an advanced SNAPSHOT or stable release with
the wrong or missing local ID could be reported as migration-required. The
classifier now validates the registered local ID immediately after the
release-missing check and before every exact/SNAPSHOT/stable release branch,
returning `INVENTORY_ERROR` with reason `local-id-mismatch`; the redundant
exact-release check was removed. The exact repair boundary is the classifier and
its Java matrix test in `cncf-collaborator-api`, plus the Cozy E7 projection
spec. Regression evidence adds advanced registered SNAPSHOT/wrong-local and
advanced stable/missing-local Java cases, and
`advanced-deferred-snapshot-wrong-local` /
`advanced-deferred-release-wrong-local` Cozy cases using the `textus-corpus`
artifact and wrong class/local ID; valid advanced cases remain
`MIGRATION_REQUIRED`. Focused validation is selected for
`ComponentIdentityMigrationClassifierTest` and
`Phase56ProjectIdentityContractSpec` completed. Collaborator API invocation
`70784-20260808T133527Z` passed 27 tests with zero failures, errors, or ignored
tests, exited 0, and released its lock; `publishLocal` invocation
`71030-20260808T133554Z` published coordinate
`org.goldenport:cncf-collaborator-api:0.2.0-SNAPSHOT`, exited 0, and released
its lock (an initial invalid working directory did not launch SBT); Cozy
invocation `71199-20260808T133608Z` passed 2 suites and 34 tests with zero
failures, errors, ignored, aborted, canceled, or pending statuses, exited 0,
and released its lock. CID07-FR-001 post-fix validation passed. Independent
focused re-review closed CID07-FR-001 with no new finding, zero actionable
findings, `FULL_REVIEW_REQUIRED=no`, and PASS.

Commit-manifest validation then closed three packaging-only gaps. `CID07-VF-002`
aligns User Account with the already validated/published `sbt-cozy
0.1.20-SNAPSHOT`; corrective invocation `84492-20260808T140617Z` built its CAR
and passed 1/1 test. `CID07-VF-003` projects ControlCenter and Supervisor as
canonical namespace/id entries throughout the Control Center assembly, SPI,
config, and security references; invocation `88259-20260808T141513Z` built the
CAR and passed 3/3 tests. `CID07-VF-004` removes the ArtScene source descriptor
that duplicated generated CML authority and makes E3 inspect the packaged CAR
descriptor; invocation `90629-20260808T142033Z` built the CAR and passed three
suites / 8 tests. The complete serialized manifest chain finished with
`91574-20260808T142233Z`; every accepted invocation exited zero and released
the shared lock. Strict focused re-review found no actionable issue and returned
PASS with `FULL_REVIEW_REQUIRED=no`. The official registry port hunks remain
admitted and unchanged by these repairs.

Parent scope clarification: path-level staging is safe. The Blog source
`BundleFactory` duplicate is excluded because the managed generated file has
identical SHA-1 `e39535...`; the official port inclusion remains preserved.
CID-07 A-E are accepted/reviewed and committed. No full Phase claim was made
at that Step checkpoint.

The Cozy repair and five official registry port hunks admitted in CID-07D are
included in the completed Step commits.

## Commit Record

CID-07 committed on 2026-08-08 in dependency order:

- `cncf-collaborator-api` `ff9da918d28ee04e63096a214d268ce8112f56f8`;
- `simple-modeler` `c05460bd21c637e375357f6412ea1bcb1ff9e006`;
- `cozy` `8754f927a8fbac035f239565354003f51d6f6f10`;
- `sbt-cozy` `43ae856e0c1813ff8bf05e9c93ec0b258efae550`;
- `cloud-native-component-framework`
  `34cb4483813a3d276edab2d5e604804087a96c08`;
- `textus-user-account` `816d1a4e6e8d07b4b648bc77074b2bfa5dcd3b12`;
- `textus-user-notification` `0e94adc5018ae0755d498ecd59b0c8f70ae9f122`;
- `textus-scraper` `3008b2738ccb701500b7702b38617efae9118c74`;
- `textus-ai` `82d711ccbbc8b310858064f1ccaa092fb5742c31`;
- `textus-aws` `17038a9b49733a74f3a642f06e26364f2367994a`;
- `textus-blog` `36ed508d2e01803cf4dd12cb61e30506975517af`;
- `textus-cbd-support` `56a3a6ba2416eb37f6a4c498e7d79a5f36e9726b`;
- `textus-control-center` `e6fd75d7787603d9630b9f2c974f98d4cdd84f37`;
- `textus-knowledge-editor` `29b2fad4297474f7c455dd9ba951d96943eb3853`;
- `textus-semantic-integration-engine`
  `cef5ba448ea2d2f8bbb66cd7ccff00140b0e78f6`;
- `textus-art-scene` `39c46a58256e6806de76611a75aafc66ef1739b9`;
- `textus-bok` `c9ec850420ee116383fd8f8ac5f14de154ccada0`;
- `textus-supervisor` `7c496a20b731574d469bf18f192e3db7c7ef834a`;
  and
- `textus-toolchain-runner` `2da68a711065e6c1fd11ce81c423131d60a6a5f3`.

## Validation Strategy

- CNCF: focused shared-classifier/registry specs and the accepted CID-06
  deferred-release contract.
- Cozy: `CozyCarLintSpec`, a new Phase 56 identity migration lint spec, project
  identity/scaffold specs, and integrated `cozy lint car` process evidence.
- sbt-cozy: canonical `CozyProjectIdentityContract` and scaffold/build bridge
  specs only if its code or public evidence changes.
- Each migrated CAR: project-focused tests plus normal `cncf-car-lint`; no
  publish, external CAR rewrite, or release mutation.
- Step acceptance: one frozen inventory runner proving 18/18 classification
  and 14/14 canonical SNAPSHOT migration with four exact deferrals.

All SBT invocations use the shared serialized wrapper. Each Slice performs
focused validation and independent review; findings require bounded
review-fix and focused re-review. Phase full validation was explicitly waived
at the Phase 56 release gate; no new full-test claim is made.

## Exit Criteria

CID-07 is complete: the classifier has one shared authority, Cozy
lint exposes the complete stable diagnostic contract, all 14 SNAPSHOT CARs
are canonical and lint-clean, the four released CARs remain unchanged with
exact next-version warnings, no unrelated dirty hunk is committed, and the
Step has converged through review, feature validation, and commit.

## Non-goals

- No namespace inference, fuzzy aliasing, or copied registry in Cozy/sbt-cozy.
- No rewrite or republish of the four released CARs.
- No acceptance of a partial canonical project shape.
- No inclusion or commit of unrelated pre-existing port, documentation,
  runtime, or product changes, except the exact official-registry port hunks
  explicitly user-authorized into CID-07D.
- No compatibility notice removal; CID-07 only records its later owner/gate.
- No Phase full validation or Phase release commit.
