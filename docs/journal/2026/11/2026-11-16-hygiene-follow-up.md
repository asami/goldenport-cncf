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
