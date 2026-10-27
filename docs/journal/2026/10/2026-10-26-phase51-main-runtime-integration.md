# Phase 5.1 Canonical CNCF Main Runtime Integration

Date: October 26, 2026.
Fix Manifest: `CC51-MAIN-INTEGRATION-20261026-R3` (r1/r2 retained).
Status: source, focused validation, normal local publication and installed-runtime
qualification and independent final delivery review completed. Owner input
adoption and Phase acceptance remain separate.

## Human instruction and preserved owner state

The parent verified native Human instruction `01a174f7-e34b-7673-af87-80227d8535eb`
on October 26, 2026. It selects latest main integrated with the necessary
accepted Phase 5.1 features, followed by normal validation, review, local
publication and qualification of the actual selected runtime. It supersedes
restoration of an obsolete artifact as the current delivery route.

The parent-supplied owner state remains the original base `41a276ad`, Session
`fd87cec948d0933cb6181af1f696cb52`, ordered revision 812, expected revision 813,
counters `1/1/3` and issue 4 OPEN. Accepted S1 and failed intents are preserved.
This editor did not access the live Session or storage. Source integration
and qualification do not adopt inputs into that Session.

The initial r1 handoff stopped before edits because its repair-skill designation
was incompatible with the bounded repair role. The parent corrected only that
classification and skill metadata in r2 to `cncf-review-fix`; the admitted
source paths, strategy, authority and semantics remain unchanged.

## Source lineage and publication policy

The parent-observed remote main `4f9325d29072493b570d66ffb7daeb2614a0ef41` is
an ancestor of canonical local main `7b59ac431adba296d655bc89cebd1b518cb93598`.
The canonical HEAD was observed at that latter commit before this edit. The
older dedicated CNCF source based on
`e77a4e0f6b691c57ec8daa9ac9935b1f03af2894` lacked the current Git V5 behavior
and the completed correlated stored-row integration. Canonical current main
already contains the current Git, memory and other latest work, while lacking
the approved observation read feature and five correlated-row files selected
for this integration.

The approved 30-path observation port comes from the read-only
`cc51-latest-cncf-host32-runtime-r1/cncf-producer` reference. The five
complete-row integration and regression files come from the original read-only
C69 `goldenport-cncf-source` checkout. Both references remain unchanged. Their
accepted behavior is integrated into canonical CNCF; no new feature or
persistence architecture is selected here. Scala headers are dated
`Oct. 26, 2026`, compressing the latest month while retaining reliable
`@since`, author attribution and earlier history.

The mutable `0.5.3-SNAPSHOT` Maven/Ivy coordinate is a development alias.
Divergent normal publishers can replace the artifact at that coordinate;
coordinate equality does not identify one immutable source build. The selected
policy uses canonical integrated CNCF as the normal local publisher, with
phase-specific features integrated there before normal SBT `publishLocal`.
Owning chats coordinate publication and use. No machine-wide lock, resolver,
cache, version change or manual JAR/manifest copy is introduced. SBT native
locks neither serialize all publishers of the logical coordinate nor
authenticate a later dependency selection.

The governing documents are the
[development runtime lineage specification](../../../spec/development-runtime-lineage.md)
and its [design](../../../design/development-runtime-lineage.md), together with
the [observation specification](../../../spec/operational-observation-read-model.md)
and [design](../../../design/operational-observation-read-model.md).

## Historical results retained

The old CNCF artifact-restoration record `7064` and the original SM
single-successor record `1757` were already completed before the policy change.
They and the private image `86a1f3e7` remain historical evidence. The older
four-suite, two-case, 36-test results and actual 4 GiB / 6 GiB observations
likewise remain historical; none qualifies this integrated source. No old
result is relabeled as current acceptance. Installed images remain immutable
independent snapshots through the normal updater.

## Imported observation paths (30)

- `docs/design/operational-observation-read-model.md`
- `docs/spec/operational-observation-read-model.md`
- `src/main/scala/org/goldenport/cncf/action/BehaviorOperationalObservationPart.scala`
- `src/main/scala/org/goldenport/cncf/config/SubsystemOperationalObservationConfiguration.scala`
- `src/main/scala/org/goldenport/cncf/operationalobservation/DockerOperationalObservationProvider.scala`
- `src/main/scala/org/goldenport/cncf/operationalobservation/HttpOperationalObservationProvider.scala`
- `src/main/scala/org/goldenport/cncf/operationalobservation/OperationalObservationModel.scala`
- `src/main/scala/org/goldenport/cncf/operationalobservation/OperationalObservationPlan.scala`
- `src/main/scala/org/goldenport/cncf/operationalobservation/OperationalObservationPlanCodec.scala`
- `src/main/scala/org/goldenport/cncf/operationalobservation/OperationalObservationProvider.scala`
- `src/main/scala/org/goldenport/cncf/operationalobservation/OperationalObservationRuntime.scala`
- `src/test/scala/org/goldenport/cncf/cli/CncfRuntimeOperationalObservationInitializationSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/ComponentOperationalObservationsSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationalobservation/DockerOperationalObservationProviderSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationalobservation/HttpOperationalObservationProviderSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationalobservation/OperationalObservationModelSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationalobservation/OperationalObservationPlanCodecSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationalobservation/OperationalObservationRuntimeSpec.scala`
- `src/main/scala/org/goldenport/cncf/action/Behavior.scala`
- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceLifecyclePart.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentMetadataActions.scala`
- `src/main/scala/org/goldenport/cncf/config/CncfConfigurationParameterCatalog.scala`
- `src/main/scala/org/goldenport/cncf/context/GlobalRuntimeContext.scala`
- `src/main/scala/org/goldenport/cncf/context/ScopeContext.scala`
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`
- `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala`
- `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala`
- `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkProgramPlanning.scala`
- `src/main/scala/org/goldenport/cncf/servicecontainer/ServiceContainerRuntime.scala`
- `src/main/scala/org/goldenport/cncf/observability/ServiceContainerRuntimeObservation.scala`

## Imported complete-row paths (5)

- `src/main/scala/org/goldenport/cncf/event/EventHistoryCorrelation.scala`
- `src/main/scala/org/goldenport/cncf/datastore/ManagedEventPreparation.scala`
- `src/main/scala/org/goldenport/cncf/event/ManagedEventObservationRequest.scala`
- `src/test/scala/org/goldenport/cncf/event/EventHistoryCorrelationSpec.scala`
- `src/test/scala/org/goldenport/cncf/datastore/sql/ManagedEventObservationIsolationSpec.scala`

The complete-row import preserves canonical V1/V2 envelope bytes and full
metadata through preparation, reconstruction and both read boundaries. Its
existing regressions cover retained duplicates, genuinely new facts, physical
reopen and rejection of changed frozen metadata. Seven imported observation
specifications cover admission, host configuration and inheritance, normal
QUERY/UoW routing, independent provider observations, bounds and cancellation.
These specifications were imported, not executed by this editor.

## Preserved concurrent work and existing debt

The pre-edit unrelated changes were the staged additions
`docs/journal/2026/10/2026-10-25-direct-upload-integrity-plan.md`,
`docs/notes/direct-upload-integrity.md` and
`docs/phase/esa63-direct-upload-integrity-plan.md`, and the unstaged modification
to `docs/journal/2026/10/2026-10-26-repository-git-execution-route.md`.
This editor made no edits to them and performed no Git index or history mutation.
No changes were made to the Git V5 plan/codec, SbtJvmParameters, SbtExecution
or GitDiffParameters.

Previously approved imported naming and GWT presentation debt remains
nonblocking separate follow-up work, including
`HYG-CC51-C69-LEGACY-NAMING-001` and `HYG-CC51-C69-LEGACY-GWT-001`.
The required header maintenance is completed only in the 33 imported Scala
files; it does not claim closure of the wider historical
`HYG-CC51-C69-HEADER-001` record. The separate HOST32 SM naming and GWT debts
also remain unchanged. No incidental rename, scenario rewrite or new
conformance finding is admitted.

## Pending parent evidence and adoption

At the source-integration handoff, the following parent results were pending.
The collected results below supersede that pending status at their stated scope:

- Ordinary diff verification against the frozen manifest and `git diff --check`.
- Existing owner 32-contract suites plus current memory and default-Git
  regressions, including `SbtJvmParametersSpec` and `GitRepositoryExecutionSpec`,
  followed by independent source review.
- Normal canonical CNCF `publishLocal` and normal qualified SM publication
  with the new CNCF input and fresh attempt/authority.
- The actual source commit and intended working diff, publication outcome,
  chosen classpath/JARs, CAR, runtime manifest, full dependency closure and
  effective heap. Ordinary byte association is diagnostic, not control authority.
- Four SM suites and two actual filtered CLI cases with 4 GiB / 6 GiB readback,
  followed by independent delivery review.

Qualified HOST32 SM remains separate from live evolving SM. Parent source
development through `TEXTUS_CNCF_SOURCE_ROOT` / `.dependsOn` and actual
binary/CAR/runtime selection must be reported separately. The original
dependency root containing the 69-path work is not rebound. The primary
product/CAR and three original dependency aliases still need explicit
owner-side public candidate/dependency revision adoption. Runtime qualification
does not accept primary validation or Phase 5.1. Preserve the same owner Session
and original base; do not reset checkout HEAD, replay an old source Submit,
edit storage or alter host grants/configuration to bypass adoption.

This edit performs no SBT or test execution, publication, runtime launch,
remote release, push, shared-grant change or owner Session transition.

## Parent-collected qualification on October 26

The parent inspected the exact 39-path r3 delta and `git diff --check` before
accepting execution evidence. The first new canonical run compiled the selected
34 suites and ran 411 tests: 410 passed and one existing recording scenario
failed. `UnitOfWorkProgramRecordingSpec` built only three SBT operations while
enumerating the four current kinds, including `FixCheck`. The bounded r3 repair
adds that actual fourth operation and its expected failure prefix; it preserves
enum enumeration and the ordinal, outcome, effect and plan assertions. Production
`FixCheck` behavior is unchanged. The original failed invocation is retained.

Fresh invocation `cc51-main-cncf-test-20261026-002` passed all 411 tests across
34 suites, including the original owner 32 contracts, memory and default-Git
regressions. Main and Test compilation are covered by that focused invocation.
`cc51-main-cncf-focused-validation-r1.json` retains the failure;
`cc51-main-cncf-focused-validation-r2.json` records the successful new run.
The independent r2 source review passed with no Current Boundary Blocker;
its scope does not yet include the final r3 test or runtime delivery evidence.

Normal canonical `publishLocal`, invocation
`cc51-main-cncf-publish-20261026-001`, terminated successfully. The normal
package and Ivy JAR have SHA256
`5cc4e887944f21f2abe1e14448a7b4160f00ca4148804c0dc78ae7341b79f8ff`.
All 1003 production Scala source files match the normal sources JAR. This
identifies canonical HEAD `7b59ac43` plus the intended integrated working delta,
not the HEAD alone. Owning sm-workflow development was informed that this
unified input is available and that divergent CNCF republication is unnecessary.

Normal qualified HOST32 SM `publishLocal`, invocation
`cc51-main-sm-publish-20261026-001`, also terminated successfully. Its ordinary
updater activated private installation
`ea0b0ca1-9b5e-4936-8f49-1d356a2d855a`. Readback of the actual publication
receipt, updater request, CAR, main JAR and installed manifest confirmed all
112 dependency modules, the exact canonical CNCF JAR above, and all 8409
compiled class/TASTy entries in the installed component JAR. The 294 generated
Scala files remain equal to qualified HOST32 output. SM production source and
the approved two host keys remain unchanged. Previous private images remain
retained. No artifact or manifest was manually substituted.

Fresh invocation `cc51-main-sm-test-20261026-001` explicitly selected that new
installation manifest and passed all 36 tests: four complete SM suites and two
filtered native CLI cases, emitted as six suite-summary blocks across three
logical commands. Actual newly created product JVM observations report
4294967296 bytes (4 GiB) for the primary and 6442450944 bytes (6 GiB) for the
dependency, each with one product execution. Thus the default and larger
project setting are both preserved by this selected runtime. These observations
come from the new run; older results were not promoted to current evidence.

The parent admitted each completed command through its public typed verifier
and checked its own `process=terminated serialization=sbt-native` completion,
zero SBT/wrapper exits and retained full log/summary. Current records are
`cc51-main-cncf-publication-qualification-r1.json`,
`cc51-main-cncf-artifact-association-r1.json`,
`cc51-main-sm-publication-qualification-r1.json`,
`cc51-main-installed-association-r1.json`,
`cc51-main-sm-focused-validation-r1.json` and
`cc51-main-sm-heap-qualification-r1.json` in the retained Phase 5.1 task evidence.

These are standalone producer/runtime qualifications. They do not adopt the
new canonical source, primary product/CAR selection or runtime into the live
owner Session, nor accept its original 32-suite validation. The original
69-path root and three aliases remain unchanged. In particular, primary source
mode through `TEXTUS_CNCF_SOURCE_ROOT` does not by itself align its ordinary
binary dependency or prove actual primary CAR compatibility. Owner-side public
revision/adoption remains necessary. No live Session, Goal, accepted S1,
original base, counter, failed intent or unrelated index/document was modified.
No integration commit, push or remote release was performed by this repair.

## Independent final delivery review

Independent read-only final delivery review CC51-MAIN-INDEPENDENT-FINAL-DELIVERY-REVIEW-R1 passes boundary CC51-MAIN-FINAL-DELIVERY-20261026-R1 for canonical goldenport-cncf HEAD 7b59ac431adba296d655bc89cebd1b518cb93598 plus the frozen 39-path r3 working delta. No Current Boundary Blocker remains. The M1 recording specification now exercises all four typed SBT operations, including FixCheck, without filtering enumeration or weakening exact failure-prefix, outcome, ordinal, effect or plan assertions. The first 411-test run and its one failure remain retained; the fresh 34-suite run passed all 411 tests. Normal canonical CNCF and qualified HOST32 SM publishLocal invocations terminated successfully. Actual publication and installed-image evidence associate installation ea0b0ca1-9b5e-4936-8f49-1d356a2d855a with the integrated CNCF JAR, matching CAR/component package and complete 112-module dependency closure. The newly bound installed runtime passed all 36 selected HOST32 tests, and its actual product JVM observations show a 4 GiB primary and preserved 6 GiB dependency setting, one execution each. Existing imported naming/GWT and wider historical header debt remain nonblocking. This PASS is standalone producer/runtime qualification only: it neither adopts the original 69-path dependency bindings or three aliases nor accepts primary CAR compatibility, original owner32 Session validation, evolving RS-VC1 work or Phase 5.1. Original owner and RepositorySync Sessions, base, counters, accepted S1, failed intents and concurrent document/index work remain untouched.

The reviewer also reports nonexhaustive `FixCheck` matches in the unselected
SM fixtures `ThreeWorkflowSbtOperationSpec` and `TypedSbtHostPlanFixture` from
the actual current compilation log. The selected HOST32 scenarios pass, but
this delivery does not establish wider SM/FixCheck coverage or warning-free SM
compilation. Those fixtures were not changed as part of this bounded repair.
The actual structured reviewer result is retained in native chat history;
`cc51-main-independent-final-review-r1.json` is the parent-retained concise
transcription. No new tests are required for this results-only journal append.
