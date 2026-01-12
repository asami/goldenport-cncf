# Phase 2.6 DONE Checklist — Demo Completion
status = draft

## Scope
- Phase 2.6 completes remaining demo stages defined in `docs/notes/helloworld-demo-strategy.md`
- Platform contracts are frozen by Phase 2.5 (no changes allowed here)

## Checklist
### 1. OpenAPI projection (Stage 3)
This stage validates the canonical execution boundary:
CLI / server-emulator / HTTP requests are normalized into
(component, service, operation) and executed exclusively
via Subsystem.executeHttp.

- Note:
  - Component rule violations discovered during implementation are addressed within Phase 2.6.
  - Path alias logic refactoring is explicitly deferred to Phase 2.8.
  - Error taxonomy refinement is deferred to Phase 2.9.
- [x] Evidence (verified): command(s) to generate or serve OpenAPI are documented
  - command admin system ping
  - command admin.system.ping
  - command spec export openapi
- [x] Evidence (verified): sample output or endpoint is documented
  - Expected stdout: ok
  - Expected stdout: JSON containing "openapi" and "paths"
- [x] Evidence (verified): how to verify is documented
  - Specs:
    - CommandExecuteComponentSpec
    - OpenApiProjectionScenarioSpec
  - Repro:
    - sbt -no-colors "testOnly *CommandExecuteComponentSpec"
    - sbt -no-colors "testOnly *OpenApiProjectionScenarioSpec"

Result:
Stage 3 is complete with executable evidence.
No platform contracts were changed; all remaining concerns
(projection completeness, visibility policy, alias hygiene)
are explicitly deferred to Phase 2.8.

### 2. Client demo (Stage 4)
Status: DONE (2026-01-11)

#### Evidence (real http)

$ sbt 'run server'
$ sbt 'run client admin system ping --no-exit'
=> ok

#### Evidence (fake http)

$ sbt -Dcncf.http.driver=fake 'run client admin system ping --no-exit'
=> ok

Notes:
- CLI -> ClientComponent -> HttpDriver -> Server Action path verified.
- Quick-hack runtime stub removed.
- Client prints HTTP body for HTTP-backed OperationResponse (curl/server-emulator equivalent).
- Config keys: cncf.http.driver, cncf.http.baseurl.

- [x] Evidence: client mode invocation(s) documented
  - sbt 'run client admin system ping --no-exit'
  - sbt -Dcncf.http.driver=fake 'run client admin system ping --no-exit'
- [x] Evidence: expected stdout/stderr + exit code behavior documented
  - Expected stdout: ok
  - Expected stderr: (empty)
  - Exit behavior: process continues with --no-exit
- [x] Evidence: end-to-end example included
  - Server: sbt 'run server'
  - Client: sbt 'run client admin system ping --no-exit'

### 3. Custom component demo (Stage 5, scala-cli)

Stage Status:
- Status: CLOSED
- Closure basis:
  - All verification checklists in Steps 1–3 are completed with explicit evidence.
  - All deferred items identified during Stage 5 are explicitly relocated in Step 4.
  - No unresolved requirements or checklist items remain within Stage 5 scope.

Stage 5 demonstrates how users add and run a newly developed component
with minimal operational friction using scala-cli.

Deferred items (future design or hygiene work):
- ComponentDefinition / DSL 定義の正式整理
  (Note: Demo-level DSL exists, but normative design contract is not frozen.)
- 複数 Component Repository の優先順位
  (Note: Repository priority rules are explicitly deferred in design.)
- bootstrap log の永続化・運用連携
  (Note: BootstrapLog is temporary; persistence/ops integration is future work.)
- config → initialize → runtime の完全統合
  (Note: Cross-cutting semantic contract; not allowed in Phase 2.8.)

The primary goal is to validate that a user can:
- add a new component,
- run it immediately via scala-cli,
- and access all standard CNCF execution surfaces without special setup.

- [x] Evidence (verified): scala-cli command list is documented
  - CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command admin component list --component-repository=scala-cli
- [x] Evidence (verified): demo component appears in admin component list
  - Expected: component "hello" listed with origin: scala-cli
- [x] Evidence (verified): demo command execution output is documented
  - CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command hello world greeting --component-repository=scala-cli
  - Expected stdout: ok
- [x] Evidence (verified): reproduction steps are copy-paste runnable
  - All commands listed in the runbook can be executed as-is with scala-cli only

#### Step 1: scala-cli connectivity (baseline)

- Verify scala-cli execution using an existing built-in command.
- Use `ping` to confirm end-to-end connectivity.

Evidence:
- scala-cli invocation succeeds.
- `client admin system ping` returns `ok`.

#### Step 2: New component development and execution

- Implement a minimal custom component.
- Run the component using scala-cli with no special configuration.
- The following execution surfaces MUST work:
  - command
  - server-emulator
  - server
  - client
- Auxiliary capabilities MUST also work:
  - `help`
  - OpenAPI projection

Evidence (Docker standalone):

$ sbt -no-colors clean assembly
=> target/scala-3.6.2/goldenport-cncf.jar

$ docker build -t goldenport-cncf .
=> image built successfully

$ docker run --rm goldenport-cncf command admin system ping
=> ok

Evidence:
- scala-cli commands for each execution surface are included.
- Expected outputs are documented.

##### Stage 5 / Step 2: Component development demo plan

This step prepares a reproducible environment where users can
develop and run custom components with minimal friction.

Planned structure:

1. Run CNCF standalone on Docker
   - CNCF runs as a standalone runtime inside Docker.
   - User component source code is not required inside the container.

2. Prepare scala-cli based demo component development
   2.1 scala-cli basics
       - Minimal scala-cli commands required to build and run demo components.
       - No sbt usage is assumed.
   2.2 Demo component DSL
       2.2.1 Level 1
             - operation(...) with implicit RequestCommand and SimpleActionCall.
       2.2.2 Level 2
             - operation_call(...) with explicit SimpleActionCall implementation.
       2.2.3 Level 3
             - Full Component / Service / Operation / ActionCall definitions.

3. Integrate Docker runtime and scala-cli demo
   - Run user-developed components against the Docker-based CNCF runtime.
   - Demonstrate end-to-end execution via cncf-command.

Notes:
- This section defines the plan only.
- No implementation is performed at this step.

##### Stage 5 / Step 2.2: scala-cli Level 1 demo (Strategy C: run directory)

- Compile once (optional):
  scala-cli compile .

- Run Admin listing (with bootstrap log):
  CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command admin component list --component-repository=scala-cli

- Run demo command (with bootstrap log):
  CNCF_BOOTSTRAP_LOG=1 scala-cli run . -- command hello world greeting --component-repository=scala-cli

Expected Results:
- admin component list includes demo component
- command hello world greeting prints ok

#### Step 3: Exact commands included

Purpose:
- Ensure reproducibility for end users.
- Eliminate implicit knowledge or hidden setup steps.

Requirements:
- All scala-cli commands used in this stage are listed verbatim.
- Commands can be copied and executed as-is.
- No undocumented environment variables or configuration files are required.

Evidence:
- [x] Runbook: docs/notes/scala-cli/scala-cli-component-runbook.md

Acceptance:
- A new user can reproduce the demo by following the listed commands only.

#### Step 4: Deferred items resolution (Stage closure)

This step enumerates all deferred items discovered up to and including Step 3
and records their explicit relocation targets.
These items are NOT part of Stage 5 completion conditions once relocated.

Deferred items (all relocated):

- [x] ComponentDefinition / DSL definition formalization  
      -> Relocated to: Phase 2.8 (Deferred Development Resolution)

- [x] Multiple Component Repository priority / override rules  
      -> Relocated to: Phase 2.8 (Deferred Development Resolution)

- [x] Bootstrap log persistence and operational integration  
      -> Relocated to: Phase 2.8 (Deferred Development Resolution)

- [x] Full integration contract: config → initialize → runtime  
      -> Relocated to: Phase 2.8 (Deferred Development Resolution)

Acceptance:
- [x] All deferred items are explicitly listed.
- [x] Each item has a single, named relocation target.
- [x] No deferred item remains unassigned.

Stage 5 is DONE when Steps 1–3 evidence is verified
and all deferred items above are relocated.

### 4. Demo consolidation (Stage 6)

Stage 6 consolidates all demo stages into a coherent, reproducible flow
that serves as the primary entry point for new users.

The goal is not to introduce new behavior, but to present existing demos
in a clear, linear, and discoverable form.

#### Step 1: Single entry point

- Provide one clear starting document for all demos.
- All demo paths originate from this entry point.

Evidence:
- A single document is identified as the starting point.

#### Step 2: Linear demo flow

- Demo steps are ordered and sequential.
- Users can follow the demo without backtracking or guesswork.

Evidence:
- Demo sequence is explicitly documented.

#### Step 3: Verification guidance

- Each demo step includes:
  - the exact command to run
  - the expected output
- Users can verify correctness at every stage.

Evidence:
- Verification steps are included for all demo stages.

#### Step 4: Link completeness

- All referenced documents and commands are reachable.
- No dead ends or missing links exist.

Acceptance:
- A new user can complete the entire demo sequence without external guidance.

## Acceptance Rule
- Phase 2.6 is DONE only when every checkbox is checked with explicit evidence (commands + expected outputs and/or links to outputs).
- If any Stage remains NOT DONE or any checkbox is unchecked, Phase 2.6 is NOT DONE.
- Phase 2.6 completion does not modify platform contracts; if a contract change is needed, it must be handled as Interrupt Work with an Interrupt Ticket.

## References
- docs/notes/helloworld-demo-strategy.md
- docs/notes/helloworld-bootstrap.md
- docs/notes/interrupt-ticket.md
- docs/notes/phase-2.5-observability-overview.md
