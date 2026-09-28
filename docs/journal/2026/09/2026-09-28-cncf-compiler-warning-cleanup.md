# CNCF compiler warning cleanup

Date: 2026-09-28
Incident: INC-CNCF-COMPILER-WARNINGS-20260928
Workflow: cncf-diagnose-fix; cncf-test-fix standalone full-test gate

## Observation and bounded repair

The last successful clean/full-test log reported 15 Compile warnings and
5 Test warnings. The user requested their removal. The scope is
goldenport-cncf only; existing JVM-profile and timezone repair work is preserved.

| Stable failure ID | Cause and final repair |
| --- | --- |
| CW-RESOURCES-E029 | Two vector extractor patterns were not exhaustive. Bind the entries and select head/tail under the existing non-empty guards; preserve sorting, precedence and diagnostics. |
| CW-CONFIG-E121 | Two fully enumerated configuration target matches had null-only wildcard fallbacks. Spell them as case null and retain the error results. |
| CW-JOB-E121 | Eight fully enumerated status/mode matches had null-only wildcard fallbacks. Preserve every predicate, error and partial-match wildcard; spell only those eight fallbacks as case null. |
| CW-STATE-E121 | Two matches on stateConflict's concrete Failure result had null-only fallbacks. Spell null explicitly and retain observer/error behavior. |
| CW-UOW-E029 | StateMachineProviderExecute and StateMachineProvidedApiExecute were omitted from operation classification. Enumerate both as Local according to the existing component-owned dispatch rule. |
| CW-TEST-E190 | Two private failure assertion helpers declared Unit. Return org.scalatest.Assertion so their assertions are retained. |
| CW-TEST-APPLY | Pass ConfigurationValue.StringValue.apply explicitly. |
| CW-TEST-PROFILE-E029 | The generated profile match used two case-class companion values. Reject any unmatched profile with false while preserving both intended branches and generation bounds. |
| CW-TEST-RELEASE-DUPLICATE | Test inherits Compile's scalacOptions. Remove the extra Test release:17 setting and retain Java 17 targets for both Scala and Java. |

No warning suppression or weaker assertions were added. SNAPSHOT coordinates,
dependencies, public API shapes and compiler diagnostics are retained.
Every edited Scala file follows the repository's version-history policy.

The UnitOfWork regression uses well-formed typed requests and asserts original
operation payloads, order, zero-based ordinals, Local/External classes, and
mixed segment boundaries. Both constructors also join the existing bounded
property generator. The normative operation inventory is updated; nested
HTTP/shell/process intents retain their separate External classification.

The repair changes build.sbt, seven production Scala files, five existing
Scala specs, and docs/design/unitofwork-program-planning.md, plus this journal.

Changed paths, relative to goldenport-cncf:

- `build.sbt`
- `docs/design/unitofwork-program-planning.md`
- `src/main/scala/org/goldenport/cncf/component/repository/ResolvedComponentResources.scala`
- `src/main/scala/org/goldenport/cncf/config/CncfConfigurationBindingStringCodec.scala`
- `src/main/scala/org/goldenport/cncf/config/CncfConfigurationEnvironmentBindingCodec.scala`
- `src/main/scala/org/goldenport/cncf/job/DurableJobProjection.scala`
- `src/main/scala/org/goldenport/cncf/statemachine/ExecutionPlan.scala`
- `src/main/scala/org/goldenport/cncf/statemachine/PlannedTransitionValidationHook.scala`
- `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkProgramPlanning.scala`
- `src/test/scala/org/goldenport/cncf/action/ActionCallComponentDataStoreIdentitySpec.scala`
- `src/test/scala/org/goldenport/cncf/component/ComponentFactoryStateMachineProviderBootstrapSpec.scala`
- `src/test/scala/org/goldenport/cncf/security/IngressSecurityResolverSpec.scala`
- `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkProgramPlanningSpec.scala`
- `src/test/scala/org/goldenport/cncf/workflow/StateMachineProviderResolutionSpec.scala`

## Validation and correction

The first clean/full-test gate passed all 3,812 executable tests but retained
two job warnings. Its repair had selected _result's partial status match
instead of _result_v2's fully enumerated match. Parent diff review had missed
the distinction. A bounded correction restored _result's original wildcard
and changed only _result_v2's outer fallback to null. All inner outcome
wildcards remain unchanged. The gate was repeated because those observed
warnings required a source correction.

Final command:
```text
sbt --batch clean test 'show Compile / scalacOptions' 'show Test / scalacOptions'
```

| Check | Final result |
| --- | --- |
| Clean Compile | 736 Scala sources; zero warnings |
| Clean Test compile | 550 Scala sources and 1 Java source; zero warnings |
| Full repository test gate | 3812 succeeded, 0 failed; 518 suites, 0 aborted |
| Existing skipped/deferred tests | 13 canceled, 1 ignored, 46 pending |
| Effective Compile/Test Scala target | Exactly one -release:17 in each scope |
| Full captured log | No [warn] entries; no deprecated JST warning |
| Job correction conservation | _result unchanged from baseline; only _result_v2 outer null fallback changed |
| HEAD/index and four preexisting dirty paths | Original HEAD; index empty; all four paths byte-for-byte preserved |
| All 12 edited Scala version headers; git diff --check | PASS |

Final invocation: `cncf-warnings-clean-test-723c26aecb4f4cb5`.
SBT exit 0, wrapper exit 0, terminal `lock=released` verified.

First-gate log: `/tmp/skill.cncf.d/cncf-sbt-ea71d1697bd0826d90daa834018fbbc9304fed312cf086a01dd0495ed843778b-efb2d8676f36597b3828a984491dfdc8/37809-20260928T093432Z.log`.
Final-gate log: `/tmp/skill.cncf.d/cncf-sbt-698a92d3f540ab5e9993eb986b12cd154b00c3d62dd90e0322d309026b550378-3399b2b396ef90c6331160c07c6b6058/38301-20260928T093908Z.log`.
Final log SHA-256: `71c93d42355d56809b8cf8a428dce915172d681cd614e7b7859572c73ebf2fd9`.

Both invocations use the registered cncf_command_runner and the shared serial
wrapper, normal SBT/Ivy/Coursier repositories, and the 4096 MB/G1 JVM profile.
The parent waited for lock=released before editing or starting another SBT.

Current Incident Blocker: resolved; zero compiler warnings and the full gate passed.
No new Hygiene or Development Candidate is admitted by this warning repair.
Existing JVM-profile files and the earlier timezone journal retain their
exact original bytes; all unrelated dependency-source changes are preserved.
The repair was initially left uncommitted. The subsequent user requests
"コミットして" and "それもコミットして" include this repair, the JVM profile,
and the timezone repair in a local acceptance commit workflow. Final commit
hashes are reported externally; push and publication are outside that scope.

## Agent Usage Summary

Bounded edits used gpt-5.6-luna / high. SBT used gpt-5.6-luna / medium.
The parent's exact model identifier and effort are unavailable.

| Agent | Model | Reasoning effort | Agent type / runtime source | DIRECT | START | RESUME | Role and scope | Outcome |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| /root | unknown | unknown | parent-task / parent runtime | 1 | 0 | 0 | DIAGNOSE, VERIFY, recordkeeping | Cause and complete repair verified |
| /root/compiler_warning_resources_config_job | gpt-5.6-luna | high | cncf_fix_worker_luna / registered custom role | 0 | 1 | 2 | FIX: fix-a, fix-c, bounded job correction; 11 paths | Verified after correction |
| /root/compiler_warning_uow_classification | gpt-5.6-luna | high | cncf_fix_worker_luna / registered custom role | 0 | 1 | 0 | FIX: planning source/spec/design; 3 paths | Regression and property extension passed |
| /root/sbt_attempt_ea71d1697bd0826d90da | gpt-5.6-luna | medium | cncf_command_runner / registered custom role | 0 | 1 | 0 | SBT: first clean/full-test gate | 3,812 passed; two warnings; correction required |
| /root/sbt_attempt_698a92d3f540ab5e9993 | gpt-5.6-luna | medium | cncf_command_runner / registered custom role | 0 | 1 | 0 | SBT: final clean/full-test gate | 3,812 passed; zero compiler warnings |

One additional editor spawn request did not start because of the agent-thread
limit. The already registered editor was resumed for the remaining disjoint
manifest; no alternate profile or execution route was substituted.
