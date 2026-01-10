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
- [ ] Evidence: one scala-cli script runs BOTH server and client modes (as per Stage 5 note)
- [ ] Evidence: the custom HelloWorld component runs on CNCF and is invoked successfully
- [ ] Evidence: exact commands included

### 4. Demo consolidation (Stage 6)
- [ ] Evidence: "single entry point" instructions exist (one place to start)
- [ ] Evidence: demo sequence and verification steps are linear and reproducible
- [ ] Evidence: links are complete (no dead ends)

## Acceptance Rule
- Phase 2.6 is DONE only when every checkbox is checked with explicit evidence (commands + expected outputs and/or links to outputs).
- Phase 2.6 completion does not modify platform contracts; if a contract change is needed, it must be handled as Interrupt Work with an Interrupt Ticket.

## References
- docs/notes/helloworld-demo-strategy.md
- docs/notes/helloworld-bootstrap.md
- docs/notes/interrupt-ticket.md
- docs/notes/phase-2.5-observability-overview.md
