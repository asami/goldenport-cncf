# Phase 110: CNCF Safety Sandbox

status=planned
planned_at=2026-10-15
depends_on=[Phase 36, Phase 107]
related=[Phase 30, Phase 56, Phase 100, Phase 108, Phase 109]
consumers=[textus-cbd-support Phase 16, sm-workflow Phase 6]
authority=[Safety Sandbox Architecture](../design/safety-sandbox-architecture.md)

## Goal

Complete the existing CNCF managed execution boundary as a Safety Sandbox for ordinary in-JVM CARs.

Prevent bugs, mistaken input/configuration and unexpected AI-generated code from producing external effects outside the CAR's admitted logical authority. Provide meaningful attack-surface reduction for hostile application input without claiming containment of a hostile CAR.

## Work

### P110-01 External Effect policy vocabulary
Define the minimal CNCF-owned classification shared by Runtime Sandbox, CAR Loading Admission and CAR lint/review metadata. Reuse Phase 107 escape-hatch metadata where applicable. Do not introduce a general OS ACL language.

### P110-02 ExecutionContext propagation dependency
Use the general ExecutionContext Propagation Architecture for Client -> Server transfer. Client Sandbox Definition is one registered propagated field family, not a sandbox-specific transport. Client functionality projects the local ExecutionContext into the allowlisted envelope; server ingress verifies/admit it and reconstructs a server-local ExecutionContext. No client value may widen server authority.

Phase 110 implements/consumes the Sandbox-specific projection, ingress admission and narrowing semantics while generic envelope/transport machinery remains an ExecutionContext runtime concern.

### P110-03 ExecutionContext sandbox resolution
Define `SandboxDefinition`, deterministic `SandboxResolver`, resolved `SandboxContext` and quantitative `ExecutionLimits`. Do not use one universal intersection rule. Authority uses server hard envelopes plus client admission/delegation and operation narrowing. Quantitative limits use server soft/default plus hard ceiling; a Client ExecutionContext may request a different budget, including above soft default, only within the hard ceiling. Bind resolved authority and limits to `ExecutionContext` and preserve provenance sufficient for diagnostics without making runtime consumers re-resolve policy.

Prove CLI/command use and resident-server use share the same Operation model: command invocation maps to Client scope; later server deployment supplies the outer Server scope.

### P110-04 Process and outbound-HTTP opt-in baseline
Make external process execution and outbound HTTP default-deny for ordinary CARs. CAR metadata/manifest declares requested authority; deployment/server configuration grants authority; ExecutionContext carries only the resolved intersection. A declaration is a request, never a grant.

Provide CNCF-owned typed execution DSLs for the high-frequency development tools **Git** and **SBT**. Expose semantic operations (for example status/diff/commit/fetch/push and compile/test/testOnly/publish as actually required) rather than arbitrary argv. Runtime adapters own executable selection, working directory, environment, bounded arguments/output and declared effect semantics.

Other external commands use generic Process Execution only after explicit command-name opt-in. Shell interpreters (`sh`, `bash`, `zsh`, PowerShell/cmd equivalents) are not ordinary command-name opt-ins because they can launder arbitrary filesystem/network/process authority; unrestricted shell belongs to a stronger profile/hard isolation. Outbound HTTP similarly requires explicit admitted destination/provider scope.

Treat use of Process Execution to implement an otherwise denied effect (for example cat/cp/rm for filesystem, curl for HTTP, database CLI for persistence) as **Capability Laundering**. Prevent it primarily by typed DSL/registered program definitions and bounded grants, with lint/review diagnostics for recognizable misuse.

### P110-05 File Workspace hard-root admission
Define server/deployment hard filesystem roots as absolute outer boundaries. Let the Client ExecutionContext request named workspace roots (repository/worktree/state area) inside those roots with coarse authority. Admit only contained/permitted roots, bind them as logical File Workspaces, and let Operations narrow to sub-workspaces/weaker authority. Client paths are requests, not self-granted authority.

Provide the minimum File Workspace DSL needed by sm-workflow Phase 6 and apply normal full-read ExecutionLimits. Use a 1 MiB initial soft/default full-read limit with a configurable hard maximum; Client context may request a different value only within that maximum. Keep large-log partial/streaming extensions separate unless the real Phase 6 scenario needs them.

### P110-06 Runtime Sandbox scope
Define and implement the operation/CAR execution scope that binds admitted runtime capabilities and logical resources. Integrate existing UnitOfWork, ResourceTreeAccess/Query, ProcessExec, datastore/entity and managed provider paths. Managed runtime effects that do not pass through UnitOfWork receive the same Safety Sandbox semantics without artificial UnitOfWorkOps.

### P110-07 Runtime enforcement
Deterministically reject requests outside the admitted logical scope. ResourceTree operations stay within bound logical trees; persistence stays on admitted framework routes; ProcessExec uses trusted registered definitions/grants; managed network/provider authority cannot be widened by application input; failures use normal CNCF Consequence/Conclusion diagnostics.

### P110-08 CAR Loading Admission
Add a bounded admission stage to the existing CAR loading path before ordinary component activation. Classify packaged bytecode references for direct filesystem, network/socket/HTTP, ProcessBuilder/Runtime.exec/shell, JDBC/raw persistence, native/JNI/Unsafe/internal JVM, and deterministic dynamic/reflection escape classes.

A CAR capability declaration never converts direct JVM access into an allowed Safety Sandbox route.

### P110-09 Admission profiles and hard-isolation handoff
Define the normal Safety Sandbox profile and the handoff to Docker/process isolation. Do not attempt adversarial hostile-code proof in JVM. Native/untrusted/unrestricted execution is classified for hard isolation.

### P110-10 Shared lint/review contract
Expose External Effect/admission classifications to CAR lint and CBD Review so textus-cbd-support Phase 16 does not invent a second taxonomy. CAR lint is early feedback; Loading Admission is runtime-entry enforcement; Runtime Sandbox bounds effects after admission.

### P110-11 Executable end-to-end specification
Prove:
1. direct Files / ProcessBuilder usage and CAR-owned main/entry-point definition+invocation are attributable and rejected by Loading Admission;
2. a CAR using CNCF ResourceTree/ProcessExec routes is admitted;
3. an admitted CAR requesting an effect outside runtime logical scope is denied;
4. a correctly scoped CAR succeeds;
5. ClassLoader dependency isolation remains intact and is not described as hostile-code containment.

## Acceptance
- Capability-Oriented Execution Architecture and Safety Sandbox Architecture remain distinct compatible authorities.
- No parallel filesystem/network/process abstraction is introduced solely for sandboxing.
- Direct external-effect routes are deterministically attributable at CAR admission where supported.
- Runtime Sandbox reuses existing CNCF logical capabilities/resources.
- CAR lint/CBD Review consumes the shared classification vocabulary.
- Ordinary CARs do not require SELinux-like policy administration.
- Malicious CAR containment is explicitly a non-goal for in-JVM Safety Sandbox and is handed to Docker/process isolation.
- No SecurityManager recreation, management hash, duplicate integrity machinery or speculative rollback/checking is introduced.

## Phase 6 early delivery triggered by sm-workflow

The full Safety Sandbox remains a post-foundation development stream, but a concrete Phase 6 defect has exposed an immediate upstream requirement: AI-generated code used Process Execution to read/write external files, laundering filesystem authority through an admitted process route. Therefore P110-01 plus the minimum P110-04 Git/SBT/process/HTTP policy and shared classification are pulled forward as a bounded producer slice needed by sm-workflow Phase 6.

This early slice does not claim Phase 110 completion. Implement only enough generic CNCF functionality to remove the Phase 6 unsafe route and exercise the real sm-workflow command execution path. The remaining hierarchical SandboxContext, Loading Admission breadth and later server propagation continue under the normal Phase 110 sequence.

## sm-workflow development-driver adoption

Development proceeds by vertical slices driven by real sm-workflow use after its basic command workflow is operational. Do not attempt to complete every sandbox family speculatively before the first consumer works.

Recommended sequence:

1. **Command/ExecutionContext slice** — propagate/resolve Client(Command) + Operation sandbox definitions and prove an Effective Sandbox reaches the actual sm-workflow Operation.
2. **Worktree/resource slice** — constrain source/worktree reads and writes through CNCF logical resource boundaries; prove a sibling/unrelated repository write is denied.
3. **Managed process slice** — run admitted Git and SBT operations through CNCF managed ProcessExec/program definitions; reject arbitrary shell/process construction.
4. **Provider/network slice** — constrain admitted AI/provider network authority without exposing raw clients to workflow code.
5. **Loading Admission + lint slice** — reject representative direct JVM escape routes in the packaged CAR and consume the same classification in cbd-support Phase 16.
6. **Resident-server slice** — when sm-workflow server operation is introduced, add Server Sandbox and Client->Server ExecutionContext propagation; prove the existing Operation definitions need no semantic redesign.

Each slice must end with a real sm-workflow scenario, not only a CNCF unit fixture. A missing generic primitive discovered by the scenario becomes bounded producer work in CNCF Phase 110 or cbd-support Phase 16 and is then re-consumed by sm-workflow.


Phase 110 provides the generic runtime/admission primitives. sm-workflow adopts Safety Sandbox only after its basic workflow operation is working; Safety Sandbox must not become a prerequisite that delays that foundation.

During adoption, sm-workflow is the practical development driver for the three-scope model and for CNCF/cbd-support integration. Concrete gaps found while constraining sm-workflow Git/SBT/worktree/process effects are fixed at their owning layer: CNCF Runtime for managed execution/enforcement/admission, CBD Support for lint/review interpretation, and sm-workflow only for its own scope/profile definitions. Do not add product-specific bypasses to make the trial pass.

## Handoff
textus-cbd-support Phase 16 consumes the shared External Effect / escape classification for deterministic CAR lint and CBD Review. Phase 13 remains the human-in-the-loop resolution workflow. sm-workflow consumes resulting review/admission evidence through its existing review route.
