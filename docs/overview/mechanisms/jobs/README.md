# Job Management Mechanism Overview

`overview.svg` is the primary at-a-glance view of CNCF Job management.

Theme-specific detail:

- `job-engine-runtime.svg`: submission, scheduling, Task execution, retry,
  compensation, and terminal recording.
- `job-control-inspection.svg`: authorized control, query/await surfaces,
  management projection, diagnostics, and lifecycle Events.

Authoritative evidence:

- `docs/design/job-management.md`
- `docs/design/timer-scheduling-boundary.md`
- `docs/design/jcl-language.md`
- `docs/phase/phase-22.md`
- Executable Specifications under `src/test/scala/org/goldenport/cncf/job/`

The current Job engine, bounded scheduler, control, inspection, Job Entity, and
JobDefinition snapshot paths are available. Executable JCL `flow`, `events`,
and `onEvent` remain planned.
