# HelloWorld Bootstrap
## for Cloud Native Component Framework (CNCF)

status = draft
audience = users / platform / demos
scope = cncf-run / subsystem-bootstrap / admin / web / openapi

---

## 1. Purpose

The HelloWorld Bootstrap defines the **minimum executable experience**
of CNCF.

Its goal is to ensure that:

- CNCF can be started with zero configuration
- A Subsystem successfully boots
- Users can confirm runtime status via REST and Web
- API usage is discoverable without reading documentation

This bootstrap phase prioritizes **“it runs safely”**
over domain logic, configuration, or persistence.

This document is the single source of truth for the HelloWorld bootstrap,
demo stages, and stage results.

---

## 2. User Experience Flow

### Step 0: Run CNCF with no arguments

```bash
cncf run
```

Behavior:
- CNCF starts with a built-in default subsystem
- No configuration file is required
- No domain logic is involved

Startup log example:

```text
[INFO] No subsystem configuration provided.
[INFO] Starting default HelloWorld subsystem.
[INFO] HTTP server started on port 8080.
```

---

### Step 1: Confirm via Web Browser

Open:

```text
http://localhost:8080/
```

The Web interface displays:
- Subsystem name, tier, kind
- Runtime status (OK)
- Uptime and basic statistics
- Links to Admin APIs and OpenAPI documentation
- Instructions for next steps

This page is intended for **human users**.

---

### Step 2: Confirm via REST (Admin API)

```bash
curl http://localhost:8080/admin/ping
```

```json
{ "status": "ok" }
```

Other available endpoints:
- `/admin/meta`
- `/admin/stats`
- `/admin/health`

These APIs are intended for **automation and tooling**.

---

### Step 3: Inspect API Usage (OpenAPI)

```text
http://localhost:8080/api
```

- Displays an OpenAPI viewer (Swagger UI or equivalent)
- Shows all available REST endpoints
- Backed by `/openapi.json`

This page serves as **the authoritative usage guide** for REST APIs.

---

## 3. Default HelloWorld Subsystem

When no configuration is provided, CNCF constructs
the following implicit SubsystemModel:

```text
SubsystemModel(
  name = "hello-world",
  tier = domain,
  kind = service,
  components = []   // AdminComponent and WebInterfaceComponent are implicit
)
```

Characteristics:
- No DomainComponent
- No CML
- No persistence
- No business logic

This subsystem exists purely to validate
startup, lifecycle, and observability.

---

## 4. Components in the Bootstrap Subsystem

### 4.1 AdminComponent (standard)

Provides machine-facing APIs:

- `/admin/ping`
- `/admin/meta`
- `/admin/stats`
- `/admin/health`

Responsibilities:
- Liveness and readiness
- Runtime metadata
- Basic statistics

AdminComponent is implicitly included
in all subsystems.

---

### 4.2 WebInterfaceComponent (standard)

Provides human-facing Web pages:

- `/` : status overview
- `/instructions` : usage guidance
- `/api` : OpenAPI viewer

Responsibilities:
- Visual confirmation that the subsystem is running
- Clear instructions for next steps
- Links to REST and OpenAPI resources

WebInterfaceComponent is also implicitly included.

---

## 5. OpenAPI as the Single Source of Truth

All externally visible APIs exposed during bootstrap
are described by OpenAPI.

Endpoints:
- `/openapi.json` : OpenAPI specification
- `/api` : OpenAPI viewer

Key rule:

> API usage is never described manually.
> It is always derived from the OpenAPI model.

This applies equally to:
- Admin APIs
- Future CRUD projections
- Future domain services

---

## 6. Running with a User-Defined Subsystem

Users may provide their own minimal subsystem definition:

```bash
cncf run hello-subsystem.conf
```

Example configuration:

```hocon
subsystem {
  name = "hello-world"
  tier = domain
  kind = service
}
```

Behavior:
- The default subsystem is replaced
- The same Admin and Web interfaces are provided
- The OpenAPI surface remains visible

This step allows users to confirm
that they can control subsystem definitions safely.

---

## 7. What Is Explicitly NOT Included

The HelloWorld Bootstrap intentionally excludes:

- Domain models
- CML parsing
- CRUD APIs
- Persistence
- Memory-First runtime behavior

These are introduced in subsequent phases
after the bootstrap is validated.

---

## 7.1 Related Design Notes

The HelloWorld Bootstrap intentionally focuses on **Server mode** execution only.

However, CNCF Subsystems are designed to support multiple execution modes
while sharing the same core logic, including:

- Server mode (REST / OpenAPI)
- Command mode (CLI, in-process execution)
- Batch mode (import/export, migration, maintenance)

These additional execution modes are discussed in the following design note:

- [Subsystem Execution Modes](subsystem-execution-modes.md)

The HelloWorld Bootstrap keeps these modes out of scope for simplicity,
but its design assumes that Subsystem logic is transport-independent
and can later be reused in Command or Batch execution contexts.

---

## 8. Transition to the Next Phase

Once the HelloWorld Bootstrap is confirmed,
the next step is:

- Adding a CML file
- Generating CRUD projections
- Bootstrapping a Domain Subsystem with behavior

The bootstrap subsystem provides
the stable foundation for all later extensions.

---

## 9. HelloWorld Demo (Plan)

### 9.1 Goal and Scope
This section defines the minimal, experience-first path to complete the
HelloWorld demo before entering CRUD / CML development.

The objective is to stabilize the demo experience and execution boundary,
so that later CRUD/CML work can build on a proven, user-visible flow.

Scope is intentionally demo-first:
- Establish a clean startup and execution path.
- Validate the user experience across server, client, and command modes.

CRUD / CML is explicitly out of scope for this phase.

### 9.2 Terminology
This document uses the term **Stage** to describe user-visible demo milestones.

The term **Step** is reserved for concrete implementation tasks and
is intentionally not used at the structural level in this section.

### 9.3 Target Demo Experience
The demo experience should provide:
- No-setting HelloWorld server startup
- OpenAPI-based API specification exposure
- Client mode usage
- Command mode usage
- First custom HelloWorld Component implementation

### 9.4 Architectural Principles
- Subsystem is the stable execution boundary.
- Component / Service / Operation model is reused across modes.
- No special REST adapter is introduced.
- Projection-based outputs (OpenAPI, CLI help) are the primary interface.
- A single implementation supports multiple projections.

### 9.5 Demo Stages
The demo is completed through a sequence of **Stages**.
Each stage represents a stable, user-visible capability.
Implementation steps may be broken down separately.

#### Stage 1: No-Setting HelloWorld Server
- Default Subsystem instantiation
- AdminComponent availability
- HTTP server delegation to `Subsystem.executeHttp`

##### Observability (Stage 1 Completion Criteria)
This stage validates the basic observability wiring between the execution model
and external visibility. The goal is not advanced monitoring, but to ensure that
execution events can be observed during development and debugging.

Completion criteria:

- The observation DSL is defined:
  - observe_fatal
  - observe_error
  - observe_warning
  - observe_info
  - observe_debug
  - observe_trace

- Action execution automatically resolves its observation scope
  (component / service / operation) without explicit specification
  by the action implementation.

- The following observation events are emitted:
  - observe_info on Subsystem startup
  - observe_error on Action execution failure
  - observe_fatal on unrecoverable execution failures

- Observation events are emitted via a temporary SLF4J-based sink:
  - No SLF4J StaticLoggerBinder warnings are emitted at startup
  - Observation output is visible in standard logs

- Observation does not affect execution control:
  - observe_* does not throw, retry, or alter execution flow
  - Execution control remains the responsibility of ActionEngine

#### Stage 2: Executable HelloWorld API
- `ping` operation returning 200 OK
- Health / info as future extensions

#### Stage 3: OpenAPI Projection
- Read-only OpenAPI generation from Subsystem model
- No manual spec writing
- Projection, not adapter

#### Stage 4: Client and Command Modes
- `cncf` client execution
- Remote Subsystem invocation
- Same Component/Operation model
- Local Subsystem execution as a command
- Batch / admin use cases
- Shared implementation with server mode

#### Stage 5: First Custom HelloWorld Component Extension
- Add a simple operation (e.g. `hello(name)`)
- Verify propagation to server / OpenAPI / client / command

#### Stage 6: Demo Consolidation
This stage focuses on polishing the demo experience:
- Documentation and demo script preparation
- Error handling and messaging improvements
- Ensuring consistency across server, OpenAPI, client, and command modes

No new architectural features are introduced at this stage.

### 9.6 Stage Execution Order
1. Stage 1: No-Setting HelloWorld Server
2. Stage 2: Executable HelloWorld API
3. Stage 3: OpenAPI Projection
4. Stage 4: Client and Command Modes
5. Stage 5: First Custom HelloWorld Component Extension
6. Stage 6: Demo Consolidation

The HelloWorld demo is considered complete only after all six stages are finished.

### 9.7 Non-Goals
- No CML parsing
- No CRUD generation
- No persistence or memory-first logic
- No authentication or authorization
- No UI beyond projections

### 9.8 Transition to CRUD / CML
The following artifacts are expected to carry forward:
- Subsystem execution boundary
- Component / Service / Operation model
- Projection-based outputs

This demo plan validates the demo experience first and then transitions into
CRUD / CML with a stable, reusable execution model.

---

## 10. Summary

> The HelloWorld Bootstrap guarantees that CNCF
> can always start safely, visibly, and understandably.
>
> It establishes the operational baseline upon which
> all domain logic, configuration, and runtime evolution
> can be layered incrementally.

## Observability — Stage 1 Result

### Purpose
Stage 1 の目的は、Observability の基盤導入と ScopeContext 階層化の確立である。

### Implemented Items (Facts)
- ScopeKind / ScopeContext / ObservationDsl を導入した。
- Subsystem → Component → Service → Action の ScopeContext 伝播を実装した。
- stdout / stderr / slf4j backend の整理を行い、SLF4J をデフォルトとした。
- Action の enter/leave/error 観測フックを仕込んだ。

### Verified Behavior
- server 起動時に観測ログが出力されることを確認した。
- action 実行経路に到達した場合に enter/leave が発火することを確認した。

### Known Limitations / Defects
- command 実行経路が ActionEngine に到達していない。
- これはプログラミングバグではなく、実行経路未実装の不具合（defect）である。

### Stage Boundary Declaration
- Stage 1 は「観測基盤の導入」までで完了している。
- command 実行系の接続は Stage 2 の責務である。

## Observability — Stage 2 Plan

### Purpose
Stage 2 の目的は、command 実行系を Subsystem 実行モデルに接続し、
Server / Command の両モードで同一の Action 実行経路と観測を成立させることである。

### Scope
- 対象は HelloWorld Bootstrap に限定する。
- command 実行は in-process 実行とする（HTTP は介さない）。
- ping のような「必ず成功する Action」を基準にする。

### Planned Work Items
- command 実行要求を Subsystem.execute に委譲する。
- Component / Service / ActionEngine の通常実行経路を通す。
- ActionEngine の enter / leave / error が観測されることを確認する。
- 実行結果を標準出力（stdout backend）で確認できるようにする。

### Out of Scope
- 永続化
- 認証・認可
- 高度な CLI UX
- エラーハンドリングの洗練

### Expected Result
- `cncf run command admin.system.ping` が
  - ActionEngine に到達し
  - enter / leave が観測され
  - 既知の固定レスポンスを返す

Stage 2 は、command 実行モデルを確定する段階であり、
以降の Batch / Tooling / Automation の前提となる。

## Observability — Stage 2 Result

### Implemented Items (Facts)
- ActionEngine の enter / leave が観測できた。
- command 実行時はログが抑制され、結果のみ stdout に出る。
- --log-backend 指定により観測ログを明示的に出力できる。

### Stage 2 Result: CLI Execution
- command / client の成功時は stdout に結果のみが出力される（例: `ok`）。
- 失敗時は stderr にエラーが出力され、exit code は 1 になる。
- exit code 規約は success=0 / failure=1 である。
- デフォルトではログは出ない（NopLogBackend）。

### Stage 2 Result: Observability Architecture
- Observability の構造は以下の四層で固定された。
  - ScopeContext
  - ObservabilityContext
  - ObservabilityEngine
  - LogBackend
- フローは以下である。
  - ScopeContext → ObservabilityContext → ObservabilityEngine → LogBackend
- 各層の責務は以下である。
  - ScopeContext: スコープ情報の保持と委譲
  - ObservabilityContext: 入口としての委譲
  - ObservabilityEngine: 観測イベントの組み立てと出力の正規化
  - LogBackend: 出力先の差し替え
- 振る舞いは Stage 1 から変更していない（構造整理のみ）。

### Log / Backend Policy (Stage 2)
- command / client:
  - デフォルトは NopLogBackend。
  - `--log-backend` 指定時のみ観測出力が出る。
- server:
  - デフォルトは Slf4jLogBackend（現時点）。
- SLF4J は backend の一実装であり、ActionEngine などからは直接呼ばない。

### Stage Boundary Declaration
- Stage 2 で確立したこと:
  - 実行経路（CLI → Subsystem → ActionEngine）
  - 観測責務の分離
  - CLI として予測可能な入出力
- Stage 3 以降の対象:
  - Metrics / Trace / OpenTelemetry
  - ObservabilityEngine 拡張
  - exit code 拡張（分類）
