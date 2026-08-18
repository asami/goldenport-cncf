# Phase 56 Hygiene Follow-up

status=open
date=2026-08-07

The following accepted hygiene records are outside the frozen CID-04/CID-05
identity and repository behavior boundaries and remain separate follow-up work.

| ID | Status | Discovered | Repository / path | Evidence | Category / risk | Boundary and proposed follow-up |
| --- | --- | --- | --- | --- | --- | --- |
| HYG-P56-001 | RESOLVED (validation and task commit gated) | 2026-08-07, CID-02/04 review | `cncf-collaborator-api`; `docs/spec/test-policy.md` | `cncf-goal-task-cross-project-hygiene-20260811-01` adds the repository-specific executable-test policy grounded in the Java JUnit Jupiter SBT build and tests. | documentation/test-policy, P2 | Final resolution is gated on the parent-selected validation and task commit; no invocation ID is asserted here. |
| HYG-P56-002 | RESOLVED | 2026-08-07, CID-04C review | `cozy`; `src/main/scala/cozy/archive/CozyArchivePackager.scala`, URLConnection around line 482 | Direct HTTP read has controlled local runtime-integration evidence. | runtime/network integration, P2 | Resolved by the Cozy hygiene batch without changing descriptor/publication behavior. |
| HYG-P56-003 | RESOLVED (validation and task commit gated) | 2026-08-07, CID-04D review | `sbt-cozy`; `CarComponentIdentityAdapter.scala` `projectRelease`/`carFilename`/`mavenCoordinate`, callsites `CozyProjectIdentityContract.scala` | `cncf-goal-task-cross-project-hygiene-20260811-01` completes the frozen package-visible adapter and caller naming migration while preserving serialized keys and expected strings. | naming/API cleanup, P3 | Final resolution is gated on the parent-selected validation and task commit; no invocation ID is asserted here. External Textus two-argument `CarDependency` migration remains CID-08 future-phase work. |
| HYG-P56-004 | RESOLVED | 2026-08-07, CID-04C re-review | `cozy`; `src/main/scala/cozy/archive/CozySarPublisher.scala`, project-local temporary SAR staging | Temporary SAR staging is contained under the project artifact boundary. | SAR publication staging, P3 | Resolved by the Cozy hygiene batch while preserving SAR content and publication semantics. |
| HYG-P56-005 | RESOLVED | 2026-08-07, CID-05A review; updated 2026-08-08, CID-05C review-fix | `cloud-native-component-framework`; `Component.scala` public/protected `componentid`/`instanceid`, `aggregate_name`, `install_binding`, package-visible `subsystem`; `ComponentParameterDiagnostics.parameterName`; `ComponentParameterContext.componentId`/`componentInstanceId`; `MetricsComponent.operationMode`; package-visible `ComponentRepository.developmentComponentClaims`, `GenericSubsystemFactory.runtimeResolveDescriptorC`, and `Subsystem.executeOperationResponse` | Naming cleanup crosses files or changes source/package API labels with external callers, so it cannot be completed as same-file incidental cleanup. | naming/API compatibility cleanup, P3 | `cncf-goal-task-cncf-hygiene-20260811-01` implements source-compatible Component API aliases and directly covering `PortBindingSpec` and `Phase56ComponentIdentityContractSpec` updates. Focused validation passed 126/126 tests in invocation `15908-20260811T071918Z`; the final focused re-review passed. The task commit is gated on the repository full `test`, so this resolution is not persisted if final validation fails. |

Hygiene Status: RESOLVED
Hygiene ID: HYG-P56-002
Resolution Batch: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: focused `90088-20260818T212818Z`; final Cozy `30861-20260818T224506Z` (1,337/1,337)
Acceptance Commit: reported externally after commit

Hygiene Status: RESOLVED
Hygiene ID: HYG-P56-004
Resolution Batch: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: focused `91554-20260818T213008Z`; final Cozy `30861-20260818T224506Z` (1,337/1,337)
Acceptance Commit: reported externally after commit
