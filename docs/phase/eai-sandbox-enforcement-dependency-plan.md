# CNCF dependency plan: EAI sandbox execution enforcement

status=planned / dependency assessment
planned_at=2026-11-02
owner=goldenport-cncf
references=../journal/2026-11-02-gmo-security-incident-assessment.md

## Boundary
CNCF owns common capability admission, UoW and execution-context security. Textus EAI owns Integration Component semantics, artifacts, retention and provider-specific transfer. Do not create duplicate EAI execution authorization in CNCF. Existing file-workspace DSL is **cooperative path admission, not an OS sandbox** (see docs/design/file-workspace-dsl.md). Security Complete CAR's actual qualification and runtime enforcement must be checked before using it as an in-process execution grant.

| Item | Status | Required CNCF proof / change |
| --- | --- | --- |
| CNCF-EAI-01 | OPEN | Inspect Security Complete CAR admission criteria and runtime UoW enforcement; define what permits direct execution and how a failed admission is rejected. |
| CNCF-EAI-02 | OPEN | Provide or bind a Docker sandbox execution adapter/profile with default network/mount/IPC/credential restrictions, hard resource limits, timeout and trace; avoid unbounded host capabilities. |
| CNCF-EAI-03 | OPEN | Define explicit audited policy relaxation separate from Security Complete classification; disallow implicit development bypass and enforce hard ceilings. |
| CNCF-EAI-04 | OPEN | Establish clean image/rotation, isolation, incident evidence retention and orphan cleanup operational requirements; distinguish containment from prevention. |
| CNCF-EAI-05 | OPEN | Conformance tests: bypass attempts (network, mounts, Docker socket), failed/malicious tool cleanup, authorization, container runtime limitations on macOS/Linux and rejected unqualified in-process execution. |

## Implementation routing
First inspect existing CNCF security sandbox, CAR admission, file/workspace UoW and operational phases; attach work to suitable planned phases when identified. This document is a cross-project dependency ledger, **not** evidence that a new independent implementation phase is necessary or that any feature is already complete. TEAI must not implement an alternative CNCF sandbox engine. Preserve existing phase scope and do not infer security from package naming alone.
