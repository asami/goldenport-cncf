# Hygiene follow-up — 2026-11-16

## HYG-FIRSTUSE-EXISTING-NAMING-01

- Status: OPEN.
- Discovered: 2026-11-16, independent focused review of explicit first-use workspace initialization.
- Owner: goldenport-cncf, process-execution maintenance.
- Locations: `src/main/scala/org/goldenport/cncf/processexecution/ProcessExecutionModel.scala`, `ProcessExecutionAdmission` constructor member `private val grants`; `src/main/scala/org/goldenport/cncf/processexecution/GitExecution.scala`, method-local helper `minimum`.
- Evidence: both declarations predate this change. The private member lacks the leading underscore required by the naming rule, and the method-local helper lacks the prescribed leading and trailing underscores.
- Category and risk: nonbehavioral naming conformance; low priority, affecting maintenance consistency.
- Current boundary: preserve the accepted initialization behavior. These existing declarations do not change first-use authority, directory creation, process binding or compatibility, and their cleanup is separate from that repair.
- Proposed task: normalize these two declarations and their references mechanically, preserving behavior and authority. This is not a new source-organization task or a duplication of `HYG-P103-FULL-001`.
- Follow-up task/commit: not assigned.

HYG-FIRSTUSE-EXISTING-NAMING-01 OPEN: Preserve current acceptance; separately normalize the preexisting private `grants` member and method-local `minimum` helper in ProcessExecutionModel.scala/GitExecution.scala. No behavior or authority change is admitted by this review.

## HYG-PARENT-PATH-DESCRIPTOR-NAMING-01

- Status: OPEN.
- Discovered: 2026-11-16, independent focused review of runtime.parent path data.
- Owner: goldenport-cncf.
- Locations: `src/main/scala/org/goldenport/cncf/subsystem/GenericSubsystemDescriptor.scala`, internal bindings in `_resolved_wiring_binding_from_record` and assembly rendering.
- Evidence: lowerCamel internal bindings such as `fromComponent` and `toService` also exist at the reviewed base.
- Category, priority and risk: naming consistency only; low priority.
- Current boundary: inherited identifiers do not affect the runtime.parent connection; no current-boundary repair is authorized.
- Follow-up task/commit: not assigned.

OPEN. Owner: goldenport-cncf. GenericSubsystemDescriptor.scala:1924-1929,1959-1963,2218,2244 retains pre-existing lowerCamel internal bindings. Risk: naming consistency only. Separately freeze behavior-preserving internal identifier maintenance, retaining public parameter names, named-argument labels, serialized fields and descriptor behavior. No current-boundary repair is authorized.

## HYG-PARENT-PATH-SPEC-GROUPING-01

- Status: OPEN.
- Discovered: 2026-11-16, independent focused review of runtime.parent path data.
- Owner: goldenport-cncf.
- Location: `src/test/scala/org/goldenport/cncf/cli/WorkspaceFirstUseInitializationSpec.scala`, explicit first-use chapter.
- Evidence: the reviewed base already groups resolution, lifecycle, validation, filesystem safety and compatibility under one chapter.
- Category, priority and risk: executable-document navigation only; low priority.
- Current boundary: grouping maintenance is separate from the new sibling-path scenario.
- Follow-up task/commit: not assigned.

OPEN. Owner: goldenport-cncf. WorkspaceFirstUseInitializationSpec.scala:156 retains a pre-existing flat chapter covering several lifecycle and safety areas. Risk: executable-document navigation only. Separately admit which or equivalent feature grouping while preserving scenarios, assertions, fixtures, property thresholds and behavior.

## HYG-PARENT-PATH-WORKTREE-GWT-01

- Status: OPEN.
- Discovered: 2026-11-16, independent focused review of runtime.parent path data.
- Owner: goldenport-cncf.
- Location: `src/test/scala/org/goldenport/cncf/subsystem/ComponentDefaultRuntimeValuesSpec.scala`, linked-worktree configuration scenario.
- Evidence: the reviewed base already performs its second worktree-config action after the first Then without another action/expectation pair.
- Category, priority and risk: specification presentation only; low priority.
- Current boundary: current changes only rename the fixture helper in this retained scenario; behavioral changes are outside the repair.
- Follow-up task/commit: not assigned.

OPEN. Owner: goldenport-cncf. ComponentDefaultRuntimeValuesSpec.scala:157-159 retains a pre-existing second worktree-config action beneath the preceding Then without a separate semantic action/expectation pair. Current changes only rename the fixture helper in this scenario. Risk: specification presentation only. Separately clarify the retained scenario boundaries without changing execution or assertions.
