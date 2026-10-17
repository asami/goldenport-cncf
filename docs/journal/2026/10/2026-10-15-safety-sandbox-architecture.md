# Safety Sandbox architecture split and implementation plan

Date: 2026-10-15

## Decision

The earlier CAR Capability Sandbox work mixed two valid but different architectural viewpoints. They are now separated as first-class design authorities:

- Capability-Oriented Execution Architecture: how CNCF represents and executes managed External Effects.
- Safety Sandbox Architecture: how CNCF prevents ordinary CARs from producing effects outside their intended boundary.

They coexist. Safety Sandbox reuses capability-oriented execution for runtime enforcement but also requires CAR Lint and CAR Loading Admission.

## Primary objective

The sandbox is primarily a safety mechanism. It protects against program bugs, input/configuration mistakes and unexpected AI-generated implementations. Security is an important benefit because removing ambient filesystem/network/process/persistence routes reduces the ability of hostile user input to turn a vulnerable program into an external-resource proxy.

Containment of a deliberately hostile CAR is not an in-JVM goal. Docker/process isolation owns that requirement.

## Implementation decision

Create Phase 108 rather than expanding Phase 107. Phase 107 owns safe-by-construction application-security primitives and escape-hatch metadata. Phase 108 owns Safety Sandbox runtime scope/enforcement and CAR Loading Admission. textus-cbd-support Phase 16 owns deterministic lint/review consumption; Phase 13 owns human resolution.

Existing Phase 36 capabilities, UnitOfWork, ResourceTreeAccess/Query, ProcessExec and CAR ClassLoader infrastructure are reused. No parallel sandbox resource framework is planned.
