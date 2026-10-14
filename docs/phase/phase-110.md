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
Define `SandboxDefinition`, deterministic `SandboxResolver` and resolved `SandboxContext`. Resolve Server, Client and Operation definitions by narrowing/intersection before Operation execution and bind the Effective Sandbox to `ExecutionContext`. A child scope cannot widen its parent. Preserve provenance sufficient for diagnostics without making runtime consumers re-resolve policy.

Prove CLI/command use and resident-server use share the same Operation model: command invocation maps to Client scope; later server deployment supplies the outer Server scope.

### P110-04 Runtime Sandbox scope
Define and implement the operation/CAR execution scope that binds admitted runtime capabilities and logical resources. Integrate existing UnitOfWork, ResourceTreeAccess/Query, ProcessExec, datastore/entity and managed provider paths. Managed runtime effects that do not pass through UnitOfWork receive the same Safety Sandbox semantics without artificial UnitOfWorkOps.

### P110-05 Runtime enforcement
Deterministically reject requests outside the admitted logical scope. ResourceTree operations stay within bound logical trees; persistence stays on admitted framework routes; ProcessExec uses trusted registered definitions/grants; managed network/provider authority cannot be widened by application input; failures use normal CNCF Consequence/Conclusion diagnostics.

### P110-06 CAR Loading Admission
Add a bounded admission stage to the existing CAR loading path before ordinary component activation. Classify packaged bytecode references for direct filesystem, network/socket/HTTP, ProcessBuilder/Runtime.exec/shell, JDBC/raw persistence, native/JNI/Unsafe/internal JVM, and deterministic dynamic/reflection escape classes.

A CAR capability declaration never converts direct JVM access into an allowed Safety Sandbox route.

### P110-07 Admission profiles and hard-isolation handoff
Define the normal Safety Sandbox profile and the handoff to Docker/process isolation. Do not attempt adversarial hostile-code proof in JVM. Native/untrusted/unrestricted execution is classified for hard isolation.

### P110-08 Shared lint/review contract
Expose External Effect/admission classifications to CAR lint and CBD Review so textus-cbd-support Phase 16 does not invent a second taxonomy. CAR lint is early feedback; Loading Admission is runtime-entry enforcement; Runtime Sandbox bounds effects after admission.

### P110-09 Executable end-to-end specification
Prove:
1. direct Files / ProcessBuilder usage is attributable and rejected by Loading Admission;
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

## sm-workflow development-driver adoption

Phase 110 provides the generic runtime/admission primitives. sm-workflow adopts Safety Sandbox only after its basic workflow operation is working; Safety Sandbox must not become a prerequisite that delays that foundation.

During adoption, sm-workflow is the practical development driver for the three-scope model and for CNCF/cbd-support integration. Concrete gaps found while constraining sm-workflow Git/SBT/worktree/process effects are fixed at their owning layer: CNCF Runtime for managed execution/enforcement/admission, CBD Support for lint/review interpretation, and sm-workflow only for its own scope/profile definitions. Do not add product-specific bypasses to make the trial pass.

## Handoff
textus-cbd-support Phase 16 consumes the shared External Effect / escape classification for deterministic CAR lint and CBD Review. Phase 13 remains the human-in-the-loop resolution workflow. sm-workflow consumes resulting review/admission evidence through its existing review route.
