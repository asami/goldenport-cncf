# CNCF External Component Execution — development proposal

Date: 2026-11-02
Status: proposal in journal, NOT an active Phase or implementation claim

## Decision direction
Make generic execution of externally implemented components a CNCF foundational capability, reusable by EAI, Knowledge Lake, Orchestrator and development tooling. CNCF owns execution admission, isolation, lifecycle, execution context, resource budgets and trace. EAI owns data-integration semantics, artifact retention/placement and provider adapters. Avoid a separate EAI execution engine.

## Execution profiles
- Security Complete CAR: eligible for direct CNCF runtime execution only after actual qualification, admission and enforced UoW/capability boundary; Docker optional.
- Other CAR and non-CAR Python/CLI/Java/Node tools: Docker sandbox mandatory by default; explicit administrator policy may relax where allowed, with audit and hard ceilings.
- Docker must enforce input/output, network, volume, IPC, credential and process boundaries; no Docker socket, broad mounts or unrestricted outbound network. Docker packaging alone does not prove confinement. Stronger VM isolation is a future profile for hostile code.

## Proposed development slices (not numbered Phases)
1. Inventory existing Security Complete, CAR admission, UoW/file workspace, process execution, security sandbox and supervisor/rotation contracts; distinguish implemented enforcement from planned design.
2. Specify External Component execution contract: identity/version, typed inputs/outputs, execution context, capability, budget, sandbox profile, results/logs/TRACE, cancellation and failure.
3. Bind execution selector and Docker adapter to existing CNCF runtime; avoid duplicate algebra where UoW already suffices.
4. Implement confinement profiles and resource/timeout enforcement, explicit audited policy relaxation and controlled I/O boundary.
5. Establish lifecycle: start, collect results, preserve incident evidence, destroy sandbox, recover orphans; coordinate immutable image/rotation without claiming rotation prevents exfiltration.
6. Executable acceptance: forbidden network/mount/socket access, cross-tenant access, qualified/unqualified CAR selection, crashes, resource exhaustion and cleanup, macOS Docker Desktop vs Linux differences.

## Phase migration
Before creating formal Phase files, inspect repository phase convention and existing planned phases; integrate into suitable planned scopes or create a new formally numbered Goal Phase with explicit steps, Given/When/Then, dependencies, closure authority and evidence. Existing phase files are not modified by this proposal.

Related: GMO security case study journal and TEAI development proposal.
