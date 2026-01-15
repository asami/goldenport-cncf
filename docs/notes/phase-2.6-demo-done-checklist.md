# Phase 2.6 DONE Checklist — Demo Completion
status = done

Position in Process
-------------------
- This checklist is the authoritative source of truth for
  current work status in Phase 2.6.
- Phase / Stage definitions are defined in:
  - docs/strategy/cncf-development-strategy.md
- Stage Status and checklist semantics are defined in:
  - docs/rules/stage-status-and-checklist-convention.md

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
  - Expected stdout:
    - runtime: cncf
    - mode: command
    - subsystem: cncf
    - version: 0.3.0-SNAPSHOT
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
=> runtime: cncf
=> mode: server
=> subsystem: cncf
=> version: 0.3.0-SNAPSHOT

#### Evidence (fake http)

$ sbt -Dcncf.http.driver=fake 'run client admin system ping --no-exit'
=> runtime: cncf
=> mode: client
=> subsystem: cncf
=> version: 0.3.0-SNAPSHOT

Notes:
- CLI -> ClientComponent -> HttpDriver -> Server Action path verified.
- Quick-hack runtime stub removed.
- Client prints HTTP body for HTTP-backed OperationResponse (curl/server-emulator equivalent).
- Config keys: cncf.http.driver, cncf.http.baseurl.

- [x] Evidence: client mode invocation(s) documented
  - sbt 'run client admin system ping --no-exit'
  - sbt -Dcncf.http.driver=fake 'run client admin system ping --no-exit'
- [x] Evidence: expected stdout/stderr + exit code behavior documented
  - Expected stdout:
    - runtime: cncf
    - mode: server
    - subsystem: cncf
    - version: 0.3.0-SNAPSHOT
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

Status
----------------------------------------------------------------------
- Current status: DONE
- Current step: Completed
- Status owner: Phase 2.6 / Stage 6
- Status update rule:
  - Update this field when a checklist step is completed
  - Stage is CLOSED only when all Stage 6 checkboxes are checked

Stage 6 completes when the CNCF introduction & demo article
on SimpleModeling.org has all embedded demos fully working.

Stage 6 is article-driven, but remains contract-based.


Purpose
----------------------------------------------------------------------
- Introduce CNCF to new users via a SimpleModeling.org article
- Use the article as the single narrative entry point
- Ensure every demo described in the article actually works end-to-end

This stage finalizes demo usability, not platform redesign.


Stage 6 Completion Definition (Overridden / Extended)
----------------------------------------------------------------------
Stage 6 is DONE when:

- The CNCF introduction & demo article exists (draft level)
- Every demo described in the article runs successfully
- All demo commands are copy-paste reproducible
- Any required fixes are completed (no known broken demo remains)


Allowed Work in Stage 6
----------------------------------------------------------------------
- Demo plan design for the article
- Drafting the demo article
- Running demos described in the article
- Bug fixes required to make demos work
- Minimal supporting implementation required for demo correctness

Disallowed:
- Quick hacks purely for presentation
- Untracked design changes
- Silent contract changes


Stage 6 Checklist (Engineering Management)
----------------------------------------------------------------------

#### Step 1: Demo plan for article
- [x] Demo article scope and narrative flow defined
- [x] All demos listed explicitly (no implicit demo)
- [x] Each demo mapped to an existing Stage 3–5 artifact

Evidence:
- Demo plan section in the article draft or separate planning doc


#### Step 2: Demo article draft
- [x] CNCF introduction section written
- [x] Demo flow written in linear order
- [x] Each demo includes:
      - exact command
      - expected output
      - verification hint

Evidence:
- Draft article text exists (local or SimpleModeling.org repo)


#### Step 3: Demo execution & verification
Execution rule:
- Step 3 items are executed and verified sequentially.
- Implementation fixes MAY be performed during Step 3.
- Documentation updates MUST NOT be performed during Step 3.
- Unresolved or deferred items are collected in Step 4.

##### 3.1 Command demo (Docker)
- [x] Docker command: admin system ping
      - Expected:
        - mode: command
        - runtime / subsystem names and versions are correct
- [x] Docker command: spec export openapi
      - Expected:
        - OpenAPI JSON is produced
        - No errors
- [x] OpenAPI minimum implementation for demo usage
      - Service-level operation lists are visible in OpenAPI
      - Output is sufficient for demo explanation (paths + operations)
      - This item represents active demo-driven development

##### 3.2 Server demo (Docker)
- [x] Server starts successfully in Docker
      - Expected:
        - No startup errors
- [x] HTTP GET /admin/system/ping
      - Expected:
        - mode: server
        - runtime / subsystem names and versions are correct
- [x] HTTP GET /openapi
      - Expected:
        - OpenAPI JSON is returned
Verification scope:
- OpenAPI minimum implementation introduced in Step 3.1
  is re-verified via server HTTP surface.
---
Decision record (Stage 6):
- OpenAPI projection is currently sufficient for demo usage
  when consumed via `spec export openapi.json`.
- Text output is accepted as canonical behavior,
  but JSON output is used in demos for readability.
- This decision is recorded here as a demo-time constraint,
  not as a platform or spec-level contract.
---

##### 3.3 Client demo
- [x] Client ping via real HTTP
      - Expected:
        - HTTP path used
        - mode reflects client/server correctly
- [x] Client ping via fake HTTP
      - Expected:
        - Fake driver used
        - mode reflects client

##### 3.4 Custom component demo
- [x] Custom component appears in component list
  - Note:
    - ScriptExecutionComponent is visible via `admin component list`.
    - Naming / alias / canonical normalization is not finalized.
    - Component/service/operation canonical construction is deferred to Phase 2.8.
- [x] Custom component command executes successfully
      - Scope:
        - Script execution path only (SCRIPT DEFAULT RUN).
      - Non-goals:
        - Full integration with command / server / client execution surfaces.
        - These are explicitly deferred to Phase 2.8.


#### Step 4: Required development during demo validation
(This step is conditional)

- [x] Any required implementation work is:
      - directly necessary for demo correctness
      - minimal and justified
      - documented as “Stage 6 demo-driven fix”
  - Note:
    - No additional demo-driven fixes were required.
    - All remaining concerns are explicitly deferred to Phase 2.8+.

- [x] Non-essential improvements are deferred to Phase 2.8+

Deferred items discovered in Stage 6:
- [x] ping structured output via suffix (e.g. ping.json, ping.yaml)
      -> Deferred: Phase 2.8+
      -> Origin: Phase 2.6 / Stage 6
      -> Rationale: output format contract requires separate design freeze
      -> Status: Explicitly deferred; Phase 2.6 responsibility completed
- [x] OpenAPI full projection features
      -> Deferred: Phase 2.8+
      -> Includes:
         - Schema / response model definitions
         - Error response specification
         - Tagging and grouping policy
         - Versioning and compatibility rules
         - Formal OpenAPI contract stabilization
      -> Status: Demo-sufficient minimum confirmed; advanced features deferred

- [x] Canonical representation vs suffix-based format rule
      -> Deferred: Phase 2.8+
      -> Origin: Phase 2.6 / Stage 6
      -> Rationale:
           Canonical vs representation-selection is a protocol-level rule;
           demo accepts current behavior until Phase 2.8.
      -> Status: Decision recorded; implementation deferred

- [x] HttpDriver responsibility and naming clarification
      -> Deferred: Phase 2.8+
      -> Origin: Phase 2.6 / Stage 6
      -> Rationale:
           Current fake driver = in-process loopback.
           Renaming / reclassification requires broader contract review.
      -> Status: Scope clarified; refactoring deferred

Evidence:
- Commit messages / notes referencing demo-driven fixes


#### Step 5: Final demo completion check
- [x] All demos in the article run end-to-end
- [x] No known broken demo remains
- [x] Stage 6 checklist fully checked

Acceptance:
- A new reader can follow the article
  and successfully execute every demo.


Publication Constraint (Non-blocking)
----------------------------------------------------------------------
- Target publication date: 2026-01-19
- Article publication timing does NOT override engineering correctness
- Avoid quick hacks purely for article convenience


Stage 6 Acceptance Rule
----------------------------------------------------------------------
- Stage 6 is DONE only when all above checkboxes are checked
- Demo correctness has priority over article polish
- Any remaining issue must be explicitly deferred

## Phase 2.6 Closure

- Phase 2.6 is declared DONE.
- All Stage 3–6 checklists are fully checked with executable evidence.
- All demo scenarios described in the article run end-to-end.
- No known broken demo remains.
- All non-essential or unresolved items are explicitly deferred to Phase 2.8+.
- No platform contract changes were introduced in Phase 2.6.

## Acceptance Rule
- Phase 2.6 is DONE only when every checkbox is checked with explicit evidence (commands + expected outputs and/or links to outputs).
- If any Stage remains NOT DONE or any checkbox is unchecked, Phase 2.6 is NOT DONE.
- Phase 2.6 completion does not modify platform contracts; if a contract change is needed, it must be handled as Interrupt Work with an Interrupt Ticket.

## References
- docs/notes/helloworld-demo-strategy.md
- docs/notes/helloworld-bootstrap.md
- docs/notes/interrupt-ticket.md
- docs/notes/phase-2.5-observability-overview.md
