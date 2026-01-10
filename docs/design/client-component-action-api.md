TASK: Introduce Component.execute(Action) and migrate client HTTP execution to Action-based routing, while preserving the existing Request-based CLI path.

FILES TO MODIFY:
- src/main/scala/org/goldenport/cncf/component/Component.scala
- src/main/scala/org/goldenport/cncf/client/ClientComponent.scala
- src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala
- src/test/scala/org/goldenport/cncf/client/ClientComponentSpec.scala

CHANGES:

1) Component.scala
Add a new public execution entry point:

- def execute(action: Action): Consequence[OperationResponse]
- Implementation MUST:
  - call logic.createActionCall(action)
  - then call logic.execute(call)
- Do NOT alter existing execute(Request) behavior or signatures.

2) ClientComponent.scala
- Keep the existing Request-based behavior intact (used by CLI → Subsystem → Component path).
- Do NOT add new Request parsing.
- Ensure PostCommand / GetQuery remain valid Action instances for direct execution.

3) CncfRuntime.scala (client mode only)
- For client HTTP execution:
  - STOP using Request → Subsystem.execute → ClientComponent path.
  - After CLI normalization, construct PostCommand / GetQuery directly.
  - Invoke clientComponent.execute(action).
- Preserve the existing CLI → Request path for server-emulator and other modes.
- Do NOT modify server / command / emulator execution paths.

4) ClientComponentSpec.scala
- KEEP the existing test that executes ClientComponent via Request
  (this covers the CLI-direct-to-ClientComponent route).
- ADD a new test case that:
  - Constructs PostCommand directly.
  - Calls component.execute(action).
  - Asserts FakeHttpDriver received the expected HTTP call.
- The new test MUST use the new Component.execute(Action) entry point.
- Do NOT remove or weaken existing assertions.

CONSTRAINTS:
- No redesign or refactoring beyond what is described.
- No behavior changes outside client mode.
- No new abstractions.
- All existing tests MUST continue to pass.

END.

## Stage 4 Finalized Behavior (Client Demo)

### Execution Path
- CLI -> ClientComponent -> HttpDriver -> Server Action

### HttpDriver Resolution (Effective Chain)
- Subsystem default -> Component default -> ExecutionContext resolver -> UnitOfWork -> UnitOfWorkInterpreter

### Output Contract (curl-equivalent)
- For HTTP-backed `OperationResponse`, the client prints the HTTP response body to stdout.
- This aligns `run client ...` output with curl/server-emulator output (e.g. `ok` for `admin system ping`).

### Configuration
- `cncf.http.driver = real | fake` (default: real)
- `cncf.http.baseurl = http://localhost:8080` (default)
