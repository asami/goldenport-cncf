# Generic Workflow Protocol Model and JSON Encoding

Status: normative design input for Phase 77

## Purpose

CNCF Workflow / StateMachine runtime の application/domain 非依存な typed protocol model を Value Object として定義し、JSON をその標準 wire encoding の一つとして定義する。

JSON は正本ではない。Scala/JVM 内部では Value Object を直接受け渡し、Skill/Codex、REST、MCP、CLI/UI 等の境界で同じ Value Object を encode/decode する。

sm-workflow はこの protocol の consumer/application であり、Goal/Phase/Step 等の software-development 語彙は generic protocol に含めない。

## Two-layer model

```text
CNCF Generic Workflow Protocol
  StartRequest / StartResult
  WorkflowHandle
  Continuation
    WORK_ORDER | DECISION | WAIT | TERMINAL
  WorkResult / Evidence
  Presentation
  ExecutionRequirement
        ^
        |
application specialization
        |
sm-workflow
  GoalPhaseStartInput
  SplitPhaseStartInput
  RepositorySyncStartInput
  software-development WorkOrder payload/result
```

Generic envelope と application payload を分離する。CNCF は application payload の domain semantics を推測しない。

## Generic start request

```json
{
  "protocolVersion": "1",
  "workflowDefinitionId": "example",
  "workflowDefinitionVersion": "1",
  "invocation": {
    "selectionId": "...",
    "participantIdentity": "...",
    "idempotencyKey": "..."
  },
  "input": {
    "schemaId": "application-specific-start-input",
    "schemaVersion": "1",
    "payload": {}
  }
}
```

## Generic start result

```json
{
  "protocolVersion": "1",
  "handle": {
    "workflowInstanceId": "...",
    "workflowDefinitionId": "...",
    "revision": 1
  },
  "continuation": {}
}
```

Start は可能な deterministic progression を吸収し、最初の semantic boundary または terminal result を Continuation として返せる。

## Continuation

```json
{
  "continuationId": "...",
  "workflowInstanceId": "...",
  "revision": 7,
  "kind": "WORK_ORDER",
  "workOrder": {},
  "decision": null,
  "wait": null,
  "terminal": null,
  "presentation": {}
}
```

`kind` は closed set:

- `WORK_ORDER`
- `DECISION`
- `WAIT`
- `TERMINAL`

kind に対応する payload は一つだけ存在する。

## WorkOrder

```json
{
  "workOrderId": "...",
  "operationId": "...",
  "input": {
    "schemaId": "...",
    "schemaVersion": "1",
    "payload": {}
  },
  "expectedResult": {
    "schemaId": "...",
    "schemaVersion": "1"
  },
  "executionRequirement": {
    "reasoningLevel": "DEEP",
    "capabilities": ["..."],
    "riskLevel": "HIGH"
  }
}
```

### Abstract reasoning level

CNCF は具体的 model / provider / reasoning-effort 名を Workflow semantics に固定しない。generic vocabulary の初期 closed set:

- `ROUTINE`
- `STANDARD`
- `DEEP`
- `CRITICAL`

これは execution requirement であり、具体的 worker profile への mapping は Skill/Host policy の責務とする。

## Result / Evidence

```json
{
  "workOrderId": "...",
  "continuationId": "...",
  "expectedRevision": 7,
  "result": {
    "schemaId": "...",
    "schemaVersion": "1",
    "payload": {}
  },
  "evidence": [],
  "executionEvidence": {
    "requestedReasoningLevel": "DEEP",
    "selectedProfile": "configured-profile-id",
    "mappingPolicyVersion": "..."
  }
}
```

provider/model/reasoning effort 等を記録する場合も execution evidence とし、canonical Workflow semantics の条件にはしない。

## Presentation

全 response は必要に応じて human-readable projection を含められる。

```json
{
  "presentation": {
    "title": "...",
    "summary": "...",
    "currentSituation": "...",
    "nextAction": "...",
    "reason": "...",
    "progress": {
      "current": 3,
      "total": 8,
      "label": "..."
    }
  }
}
```

Presentation は人間/Codex console/UI のための情報であり、runtime/skill は presentation text を parse して制御判断してはならない。制御の正本は structured fields とする。

## Decision / Wait / Terminal

Decision は decision identity、typed choices/input contract、authority requirement を持つ。Wait は wake condition / external correlation を持つ。Terminal は generic outcome と application-specific typed result を持つ。

```json
{
  "terminal": {
    "outcome": "COMPLETED",
    "result": {
      "schemaId": "...",
      "schemaVersion": "1",
      "payload": {}
    },
    "evidence": []
  }
}
```

generic terminal outcome と application result を分離する。

## Console projection

CNCF protocol は structured JSON を正本とし、同じ response から console renderer が human-readable 状況説明を生成できる情報を提供する。

表示例:

```text
Workflow: <definition>
Run: <instance>  Revision: <revision>

現在: <presentation.currentSituation>
次:   <presentation.nextAction>

Work Order: <operation>
Reasoning: DEEP
Progress: 3 / 8
```

renderer は制御主体ではない。

## Skill / host reasoning mapping

Generic protocol は requested `reasoningLevel` までを規定する。Skill/Host は versioned mapping policy により concrete execution profile へ写像する。

```text
DEEP
  -> mapping policy
  -> concrete worker profile
  -> actual execution
  -> executionEvidence
```

mapping table 自体は application/host configuration であり CNCF Workflow definition に埋め込まない。

## Compatibility

- protocolVersion と schemaId/schemaVersion を明示する。
- unknown required protocol semantics は fail closed。
- additive presentation fields は Workflow semantics を変更しない。
- application payload は typed schema identity を必須とし、generic runtime が domain JSON を推測しない。


## Phase 77 protocol closure decisions

Phase 77 の consumer handoff は StateMachine runtime API だけでなく、Skill/CLI/UI が利用する generic JSON protocol を含む。

### Protocol round trip

```text
StartRequest
  -> StartResult(handle + continuation)
  -> Continuation(WORK_ORDER)
  -> typed WorkResult / Evidence
  -> next Continuation
  -> ...
  -> Continuation(TERMINAL + typed result)
```

application は各 `input/result.payload` の schema を所有するが、envelope、identity、revision、continuation kind、execution requirement、presentation、evidence contract は CNCF が所有する。

### Reasoning requirement placement

抽象思考レベルは Continuation 全体ではなく、実行作業を表す `WORK_ORDER.workOrder.executionRequirement.reasoningLevel` に置く。

```json
{
  "kind": "WORK_ORDER",
  "workOrder": {
    "operationId": "review",
    "executionRequirement": {
      "reasoningLevel": "DEEP",
      "capabilities": ["..."],
      "riskLevel": "HIGH"
    }
  }
}
```

DECISION / WAIT / TERMINAL は worker execution を要求しないため、通常 reasoningLevel を持たない。将来別種の execution requirement が必要になっても Continuation kind の意味を崩さず拡張する。

### Mapping boundary

CNCF は `ROUTINE | STANDARD | DEEP | CRITICAL` という model-independent requirement を返す。Skill/Host が versioned mapping policy により concrete profile/model/reasoning effort へ写像する。

Workflow は concrete model 名を guard/transition 条件として使用しない。実際に選択された profile/model/effort は execution evidence として記録可能だが、canonical semantics にはしない。

### Human-readable progress

StartResult / Continuation / Terminal 等は `presentation` を持ち、Codex console や UI が current situation、next action、reason、progress を人間向けに表示できる。

presentation は canonical control data ではない。Skill/runtime は表示文字列を parse して operation、state、reasoning level、completion を判断してはならない。


## Canonical Value Object model

CNCF protocol の正本は typed Value Object である。

```text
WorkflowStartRequest[I]
WorkflowStartResult[W, O]
WorkflowHandle

Continuation[W, O]
  WorkOrderContinuation[W]
  DecisionContinuation
  WaitContinuation
  TerminalContinuation[O]

WorkOrder[W]
ExecutionRequirement
WorkResult[R]
Evidence
ExecutionEvidence
Presentation
```

generic CNCF fields と application-specific payload を型合成する。application payload を untyped arbitrary JSON として CNCF core に持ち込まない。

概念的には:

```scala
WorkflowStartRequest[I]
WorkOrder[W]
WorkResult[R]
TerminalResult[O]
```

の `I/W/R/O` を component/application が所有する。wire boundary では schema identity/version と codec を用いて検証する。

## Encoding boundary

```text
CNCF Value Objects
   +-- direct Scala/JVM use
   +-- JSON Codec -> Codex / Skill / REST / CLI / UI
   +-- MCP projection
   `-- future transport projection
```

同一 process/JVM 内で JSON round trip を強制しない。JSON encode/decode によってのみ成立する semantics を作らない。

## Workflow composition

Scala program 内で Outer Workflow と Inner Workflow を typed Value Object で接続できることを protocol model の設計制約とする。

```text
OuterWorkflow
  -> typed InnerStartInput
  -> InnerWorkflowInstance
  -> typed InnerTerminalResult
  -> OuterWorkflow
```

Inner Workflow は単なる opaque Action に潰さず、必要に応じて独立した WorkflowInstance identity / revision / history / continuation を持てる。

Outer/Inner の関係には少なくとも causal correlation を保持できるようにする。

```text
OuterWorkflowInstance O-100
  child invocation
       |
       v
InnerWorkflowInstance I-200
  parent/cause = O-100
       |
       v
Terminal[InnerResult]
       |
       v
OuterWorkflowInstance O-100
```

Inner が WORK_ORDER / DECISION / WAIT で suspend しても Outer が execution thread を保持する必要はない。Outer は child completion を待つ durable progression boundary として扱える。

Outer は Inner の private state を読み取って progression を決めない。composition contract は typed start input と typed terminal result、および generic lifecycle/correlation contract である。

同一 JVM では typed direct invocation、remote boundary では同じ protocol model の encoding/projection を利用できることを保証する。

## Application specialization

CNCF Value Object は fixed generic fields と application-owned typed payload の合成である。sm-workflow は GoalPhaseStartInput 等を型引数/application payload として供給するだけで、generic Continuation や WorkflowHandle を再定義しない。
