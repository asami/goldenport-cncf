# Phase 118: External Component Execution

status=planned
planned_at=2026-11-02
owner=CNCF
checklist=[Phase 118 checklist](phase-118-checklist.md)
consumers=Textus EAI Phase 2; TKL Phase 2

## Goal

Execute an externally implemented component through CNCF admission, execution
context, managed effects, isolation, lifecycle and TRACE. Deliver one usable
Docker execution route for EAI's attachment processing before adding broader
profiles. Do not introduce a second EAI execution engine.

Source: [External Component Execution proposal](https://github.com/asami/goldenport-cncf/blob/ea852b7d059cd4345e17945e8534980475d0cda1/docs/journal/2026/2026-11-02-external-component-execution-development-proposal.md).
This is a formal planned delivery, not a claim that Security Complete
qualification or Docker confinement already exists. Phase 117 and active
development retain their priority and state. No Session is started.

## Existing owners and dependencies

| Existing scope | Use in this Phase |
| --- | --- |
| [Process Execution](../design/process-execution-runtime.md) and [File Workspace](../spec/file-workspace-dsl.md) | Reuse admission, UoW/interpreter, WorkArea, process lifecycle and bounded outputs; add only the missing external-execution binding |
| [Phase 110](phase-110.md) Safety Sandbox / [architecture](../design/safety-sandbox-architecture.md) | Owns in-JVM effect classification, capability resolution and loading admission; consume or hand off the exact missing prerequisite, not a duplicate policy engine |
| [Phase 104](phase-104.md) / [Phase 109](phase-109.md) | Consume relevant CAR trust/admission evidence; signature or metadata alone is not Security Complete qualification |
| [Phase 108](phase-108.md) and Supervisor SPI | Align termination/replacement and incident evidence; whole active/standby deployment is not an entry gate |
| EAI Phase 2 | Owns data classification, destination, artifact retention and provider adapters |

P118-01 records implemented, planned and missing enforcement separately. A label,
manifest assertion or successful direct execution is not a qualification. Missing
qualification keeps the component on the sandbox route; missing Docker or required
confinement rejects execution rather than falling back to the host.

## Delivery order

| Step | Work and connected outcome | Dependency |
| --- | --- | --- |
| P118-01 | Inventory and promote the typed execution contract into design/spec: identity/version, input/output, context/capabilities, budgets, profile, result/log/TRACE, cancellation and failure; agree the EAI handoff | Existing public runtime contracts |
| P118-02 | One controlled input -> Docker tool -> bounded output/result route, using real CNCF admission/UoW, baseline confinement and a harmless deterministic fixture | P118-01 |
| P118-03 | Enforce the default confinement profile and resource ceilings; qualify Security Complete direct-CAR selection and explicit bounded administrator overrides | P118-02 and actual Phase 110 qualification prerequisites |
| P118-04 | Complete crash/cancellation/termination, protected incident evidence, teardown and orphan recovery; align image/replacement lifecycle | P118-02/03 |
| P118-05 | Integrated executable acceptance, macOS Docker Desktop/Linux evidence, independent review and usable producer handoff | P118-01..04 |

Build P118-02's connected implementation and executable specifications together.
The initial safe Docker handoff can be consumed by EAI after the required
confinement, termination and protected-cleanup subset of P118-03/04 is qualified
and independently reviewed, while direct-CAR and broader platform qualification
finish. That handoff states precisely which profile is accepted; it does not
close Phase 118. Do not require all of Phases 108/110
before a bounded Docker path, or accept an unsafe partial path to avoid a gate.

## Planned execution boundary

- Qualified Security Complete CAR may execute directly only under actual
  admission and enforced UoW/capability boundaries; Docker remains available.
- Other CARs and Python/CLI/Java/Node tools use Docker by default. Any permitted
  relaxation is an explicit administrator policy within hard limits, visible
  in TRACE; application input cannot grant that authority.
- Default confinement denies unrestricted outbound network, broad host mounts,
  Docker socket exposure, privileged mode, host IPC and ambient credentials.
  Controlled inputs/outputs, process/resource limits and tenant separation must
  be enforced, not merely described in a container manifest.
- Resource budgets include CPU, memory, processes, runtime and bounded output.
  Preserve incidents under protected policy before removing disposable resources.
- EAI decides artifact retention/publication; CNCF owns sandbox resources and
  execution evidence. Keep one owner for each cleanup action.

Exact types and public Operations are P118-01 outputs, not invented signatures
in this plan. Stronger VM isolation for hostile code, Component Repository
distribution, a general container orchestrator and a claim that rotation prevents
exfiltration are outside this Phase.

## Executable acceptance

| ID | Given / When | Then |
| --- | --- | --- |
| XEC-A1 | Unqualified CAR or non-CAR tool requests execution | Docker is selected; missing required isolation rejects before effects |
| XEC-A2 | Actually qualified CAR with an admitted direct profile executes | Normal authorization/UoW/capabilities apply; a self-declared qualification cannot select direct execution |
| XEC-A3 | Tool attempts network, host path, socket, IPC, credential or cross-tenant access outside its grant | Access is prevented; typed failure/trace contains no secrets |
| XEC-A4 | Tool exceeds memory/process/runtime/output budget or is cancelled | Bounded termination and truthful terminal/unknown state; resources are reclaimed under lifecycle policy |
| XEC-A5 | Execution crashes or the host restarts with an orphan | Protected evidence remains available and recovery does not delete another tenant's or active execution's resources |
| XEC-A6 | Administrator changes an allowed soft policy within/beyond hard ceilings | Permitted change is attributable; hard-ceiling violation and client widening are rejected |
| XEC-A7 | EAI attachment fixture uses the producer on macOS Docker Desktop and Linux | Results, limits and host-specific restrictions are recorded separately; unsupported enforcement is not silently downgraded |

Use safe fixtures and bounded negative tests. Classify costly Docker/platform
checks separately; fixture success is not real-container acceptance. Run required
repository checks and one integrated independent review, with focused repair
closure rather than repeated helper reviews.

## Completion and handoff

Close only from the [checklist](phase-118-checklist.md). Provide EAI with the
usable revision/artifact, public usage contract, admitted profiles, actual
platform evidence and remaining limitations. Whole consumer completion is not
a producer gate. Consumers retain their own security and business acceptance.

Coordination: [EAI Phase 2](https://github.com/asami/textus-enterprise-application-integration/blob/main/docs/phase/phase-2.md)
and [TKL Phase 2](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-2.md).
