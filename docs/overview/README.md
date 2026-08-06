# CNCF Overview

This directory provides developer-facing visual summaries of currently
available CNCF mechanisms and use cases.

The overview is explanatory and non-normative. `docs/design`, `docs/spec`, and
Executable Specifications remain the authoritative sources. Overview graphics
must identify their implementation status and link to the documents used as
their evidence.

- `mechanisms/`: reusable CNCF runtime structures and execution paths.
- `use-cases/`: goal-oriented scenarios composed from one or more mechanisms.

## Directory And Overview Convention

Graphics are grouped by field under both viewpoints:

```text
mechanisms/<field>/
use-cases/<field>/
```

Every field directory must contain an `overview.svg`. The overview is the
primary developer entry point and must show the field's current overall
capability and implementation status on one canvas.

Theme-specific graphics may be added when the overview cannot explain a
mechanism, lifecycle, or use case clearly at readable size. They supplement the
overview and must not become required reading for understanding the field's
current high-level state.

## Rule Overview Set

Mechanisms:

- `mechanisms/rules/overview.svg`
- `mechanisms/rules/rule-engine.svg`
- `mechanisms/rules/component-rule-integration.svg`

Use cases:

- `use-cases/rules/overview.svg`
- `use-cases/rules/application-rule-evaluation.svg`
- `use-cases/rules/lifecycle-rule-constraint.svg`
- `use-cases/rules/monitored-condition-event.svg`

Status vocabulary:

- `AVAILABLE`: the illustrated path is implemented and covered by current
  executable evidence.
- `PARTIAL`: some mechanisms exist, but one or more illustrated bindings are
  not implemented.
- `PLANNED`: the illustrated connection is a target direction, not current
  runtime behavior.

## Job Management Overview Set

Mechanisms:

- `mechanisms/jobs/overview.svg`
- `mechanisms/jobs/job-engine-runtime.svg`
- `mechanisms/jobs/job-control-inspection.svg`

Use cases:

- `use-cases/jobs/overview.svg`
- `use-cases/jobs/managed-synchronous-command.svg`
- `use-cases/jobs/managed-asynchronous-command.svg`
- `use-cases/jobs/synchronous-primary-async-continuation.svg`

## Documentation Management Overview Set

Mechanisms:

- `mechanisms/documentation/overview.svg`

The documentation overview shows the planned Phase 58 ownership, projection,
distribution, Help, Textus CBD Support, Textus BoK, AI Directive, and Skill
Catalog boundaries on one canvas.
