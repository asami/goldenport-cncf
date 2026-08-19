# Hygiene Resolution Batch Handoff

Status: COMPLETE
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repositories: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-hygiene /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-deprecation-test-source-hygiene-batch-handoff.md

## Purpose

Remove only compiler-announced deprecated named-argument callers and the one
value-equivalent `Char.+(String)` syntax use in test sources. Preserve every
executable-specification scenario, fixture meaning, assertion, production
source path, and public contract.

## Included Hygiene

| ID | Source | Evidence | Work Package | Required outcome |
| --- | --- | --- | --- | --- |
| HYG-H57-DEPRECATION-003 | `docs/journal/2026/08/2026-08-19-deprecation-warning-hygiene-follow-up.md` | Detail-enabled test compilation `45753-20260819T042645Z` | HP-001, HP-002 | Remove admitted named callers and the value-equivalent string-syntax caller without changing specification semantics. |

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-003
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-test-source-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

Completed On: 2026-08-19
Focused Validation: test-source deprecation compile
`82331-20260819T061040Z` (success, zero warnings)
Final Focused Review: CLEAN
Final Full Validation: `97482-20260819T065407Z`
(3,261 succeeded, 0 failed)

## Frozen Boundary

- Allowed repository: `/Users/asami/src/dev2025/cloud-native-component-framework`.
- Allowed behavior change: none.
- Allowed test paths: diagnostic-inventory locations for compiler-announced
  named labels, plus `TestComponentFactory.scala:153`.
- Allowed journal paths: this handoff and its source record, solely for the
  predeclared status and validation closure fields after final gates pass.
- Preserve paths: `src/main/scala/**`, every other journal path, and every
  test file/line not admitted by HP-001 or HP-002.
- Prohibited expansion: production migration, test expectation changes,
  fixture semantic changes, public API change, compatibility layer, warning
  suppression, or a replacement not announced by the compiler.

## HP-001 — Canonical named-argument callers in test sources

- Hygiene IDs: HYG-H57-DEPRECATION-003
- Repository: `/Users/asami/src/dev2025/cloud-native-component-framework`
- Targets: all diagnostic-inventory files below containing compiler-announced
  deprecated named-argument labels. Typical labels include
  `unitOfWorkSupplier`, `unitOfWorkInterpreterFn`, `commitAction`,
  `abortAction`, `disposeAction`, `observabilityContext`,
  `httpDriverOption`, `componentid`, `instanceid`,
  `processExecutionDriverOption`, `processExecutionAdmissionOption`,
  `operationEvaluationResolverOption`, and
  `scopedConcurrencyAdmissionOption`.
- Allowed repair: substitute only the compiler-announced canonical parameter
  label, retaining the callee, argument expression, call ordering, selected
  overload, Given/When/Then wording, and assertions.
- Prohibited expansion: renaming declarations, adding compatibility labels,
  altering fixture setup, or changing expected values.
- Focused validation: compile test sources with `-deprecation`; run focused
  specifications for each changed package; `git diff --check`.
- Dependencies: None.

## HP-002 — Value-equivalent string syntax replacement

- Hygiene IDs: HYG-H57-DEPRECATION-003
- Repository: `/Users/asami/src/dev2025/cloud-native-component-framework`
- Target: `src/test/scala/org/goldenport/cncf/testutil/TestComponentFactory.scala:153`
  — compiler-inserted `Char.+(String)` to equivalent string interpolation.
- Allowed repair: use exactly the compiler-announced replacement while retaining
  the produced value and specification meaning.
- Prohibited expansion: reformatting unrelated test content, assertion changes,
  test-fixture redesign, or call-site changes beyond the listed string syntax.
- Focused validation: compile test sources with `-deprecation`; run each
  changed specification or its package-focused command; `git diff --check`.
- Dependencies: HP-001 may be implemented independently; both packages share
  the single final gates below.

## Explicit Exclusions

- `src/test/scala/org/goldenport/cncf/datastore/ComponentDataStoreSpec.scala`:
  `useApplicationDataStore` was already resolved by accepted ordinary-task
  commit `2a90e025405d42dda1f35a1950ee312172642259`; do not touch it in this batch.
- `src/test/scala/org/goldenport/cncf/event/EventBusSpec.scala:229` and
  `src/test/scala/org/goldenport/cncf/job/InMemoryJobEngineSpec.scala:476`:
  structured fixture failures are user-directed ordinary test work, not
  Hygiene; do not modify either call in this batch.
- `src/test/scala/org/goldenport/cncf/component/AssemblyApiClassLoaderSpec.scala`,
  `Phase56ComponentIdentityContractSpec.scala`, and `PortBindingSpec.scala`:
  retiring legacy compatibility assertions/scenarios is user-directed ordinary
  test work, not Hygiene; do not modify these compatibility semantics in this
  batch.

## Diagnostic Target Inventory

The following exact files were emitted by detail-enabled test compilation
`45753-20260819T042645Z`. A file is editable only for a compiler-announced
mechanical replacement under HP-001 or HP-002, and remains subject to the
explicit exclusions above.



  - `src/test/scala/org/goldenport/cncf/action/ActionCallComponentDataStoreIdentitySpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionCallConditionalTransitionDslSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionCallDataStoreRouteSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionCallDetachedRevisionDslSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionCallEntityAccessMetricsSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionCallSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionCallSupport.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionEngineAuthorizationFailureCommitSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionEngineNormalAuthorizationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionEngineObservabilitySeparationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ActionEngineObservationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ExecutionClockDslSpec.scala`
  - `src/test/scala/org/goldenport/cncf/action/ProcessExecutionDslSpec.scala`
  - `src/test/scala/org/goldenport/cncf/admission/ScopedConcurrencyAdmissionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/association/AssociationBindingWorkflowSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/AdminSystemPingExecutionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/AssemblyApiClassLoaderSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryAggregateViewBootstrapSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryDefaultAggregateCollectionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryLegacyPlanConsistencySpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryRevisionBindingSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryRuntimePlanActivationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryStateMachineBootstrapSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryWorkingSetPolicySpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentLogicCommandScriptExecutionModeSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentLogicOperationDefinitionSemanticsSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/Phase56ComponentIdentityContractSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/PortBindingSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/builtin/client/ClientAdminSystemPingSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/builtin/client/ProcedureActionCallSpec.scala`
  - `src/test/scala/org/goldenport/cncf/config/ComponentConfigurationAccessSpec.scala`
  - `src/test/scala/org/goldenport/cncf/context/ExecutionContextSpec.scala`
  - `src/test/scala/org/goldenport/cncf/context/ExecutionProfileSpec.scala`
  - `src/test/scala/org/goldenport/cncf/context/OperationEvaluationContextSpec.scala`
  - `src/test/scala/org/goldenport/cncf/context/RuntimeContextSpec.scala`
  - `src/test/scala/org/goldenport/cncf/context/SystemStatusExecutionDeterminismSpec.scala`
  - `src/test/scala/org/goldenport/cncf/datastore/ComponentDataStoreSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/ChildEntityBindingWorkflowSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/EntityConcurrencyPolicySpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionCoherenceSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionRevisionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/EntityDetachedRevisionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/EntityManagedMutationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/EntityRevisionKernelSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/EntityStoreImportSeedSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/EntityStoreQueryRouteSpec.scala`
  - `src/test/scala/org/goldenport/cncf/event/EventBusSpec.scala`
  - `src/test/scala/org/goldenport/cncf/event/EventReceptionExplicitAsyncContractSpec.scala`
  - `src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/importer/StartupEntityImportSpec.scala`
  - `src/test/scala/org/goldenport/cncf/job/InMemoryJobEngineSpec.scala`
  - `src/test/scala/org/goldenport/cncf/job/JobCommandSyncAndTaskFirstSpec.scala`
  - `src/test/scala/org/goldenport/cncf/mcp/client/CodexMcpSubsystemActivationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/observability/ObservabilityEngineSpec.scala`
  - `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAdmissionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/operationtool/OperationToolSubsystemActivationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/processexecution/ProcessExecutionDriverSpec.scala`
  - `src/test/scala/org/goldenport/cncf/processexecution/ProcessExecutionJobCancellationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/processexecution/ProcessExecutionWorkAreaSpec.scala`
  - `src/test/scala/org/goldenport/cncf/projection/AggregateViewProjectionAlignmentSpec.scala`
  - `src/test/scala/org/goldenport/cncf/projection/AuthorizationPolicyProjectionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/projection/RuleSetProjectionIntegrationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/projection/StateMachineProjectionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/protocol/OperationResponseFormatterSpec.scala`
  - `src/test/scala/org/goldenport/cncf/repository/RepositorySelectSupportSpec.scala`
  - `src/test/scala/org/goldenport/cncf/resolver/AdminSystemPingResolverSpec.scala`
  - `src/test/scala/org/goldenport/cncf/security/IngressSecurityResolverSpec.scala`
  - `src/test/scala/org/goldenport/cncf/security/OperationAccessPolicyResourceSpec.scala`
  - `src/test/scala/org/goldenport/cncf/security/OperationAuthorizationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/spi/SpiInvokerSpec.scala`
  - `src/test/scala/org/goldenport/cncf/spi/SpiSpec.scala`
  - `src/test/scala/org/goldenport/cncf/spi/evaluation/OperationEvaluationSinkSpec.scala`
  - `src/test/scala/org/goldenport/cncf/spi/rule/engine/RuleEngineSpec.scala`
  - `src/test/scala/org/goldenport/cncf/subsystem/GenericSubsystemFactorySpec.scala`
  - `src/test/scala/org/goldenport/cncf/subsystem/RuntimeRepositoryBootstrapProjectionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/subsystem/SubsystemAuthenticatedIngressAccessPolicySpec.scala`
  - `src/test/scala/org/goldenport/cncf/subsystem/SubsystemOperationAuthorizationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/subsystem/resolver/OperationResolverSpec.scala`
  - `src/test/scala/org/goldenport/cncf/testutil/SubsystemTestFixture.scala`
  - `src/test/scala/org/goldenport/cncf/testutil/TestComponentFactory.scala`
  - `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWork2pcNoopSpec.scala`
  - `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkConditionalTransitionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkHttpSpec.scala`
  - `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkPlainMutationProviderParitySpec.scala`
  - `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkSearchAuthorizationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkStateMachineHookSpec.scala`
  - `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkTargetAuthorizationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedMutationSpec.scala`
  - `src/test/scala/org/goldenport/cncf/usernotification/UserNotificationProviderRuntimeSpec.scala`

## Final Focused Review

- Exact target programs/files: the diagnostic target inventory, limited to
  changed files and the explicit line-level exclusions.
- Required checks: every HYG-H57-DEPRECATION-003 repair follows the compiler
  replacement; all Given/When/Then wording and asserted behavior are
  preserved; no production sources change; excluded semantic candidates remain
  untouched; scope is contained to the frozen paths.
- Failure policy: stop without commit; no automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2025/cloud-native-component-framework`: `sbt --batch test`

## Completion Contract

- Commit only after the final focused review and full-validation gate pass.
- Update HYG-H57-DEPRECATION-003 to `RESOLVED` with batch, validation, and
  commit evidence.
- Mark this batch `COMPLETE` only in the accepted committed tree.
- Do not absorb Development Candidates or newly found unrelated Hygiene.

## Non-goals

- Changing test behavior, test intent, fixtures, assertions, or production code.
- Retiring compatibility assertions/scenarios or solving structured fixture
  failures; both are excluded ordinary test work.
- Treating a compiler diagnostic outside the frozen inventory as admitted work.
