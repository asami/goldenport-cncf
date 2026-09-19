# OpenClaw を EAI / Job Worker として利用する

- Date: 2026-09-19
- Status: Design consideration

## Context

Textus/CNCF は Job Engine と Workflow Runtime を持ち、非同期処理の状態、結果、診断、Continuation を管理できる。一方、OpenClaw は Web/Browser、PC、File/Excel、Slack、SaaS/API など、一般的なデジタル業務に必要な多様な実行手段を AI から利用できる。

この組み合わせでは、OpenClaw を Workflow Engine の代替にせず、外部世界を操作する EAI/RPA 的な汎用 Worker として扱う。

## Core model

```text
Textus Job Engine
      |
      v
Textus Workflow
      |
      | Goal / Continuation
      v
OpenClaw Worker
      |
      +-- Web / Browser
      +-- PC operation
      +-- File / Excel
      +-- Slack
      +-- SaaS / API
      +-- Codex
      +-- AI
      |
      v
Execution Result
      |
      v
Textus Job Engine
status / result / diagnostics / evidence
```

Textus が「何を、どの順序で、どの完了条件まで実行するか」を管理し、OpenClaw は Goal を外部世界で実現する手段を選択・実行する。

既存の Workflow Execution Protocol に従い、OpenClaw は Codex と同様に Continuation Participant / External Driver になり得る。WorkflowRun の状態、revision、continuation identity、idempotency、stale-result rejection などは CNCF Runtime の責務に置く。

## Execution authority and integration boundary

Textus から起動された OpenClaw Job/Worker は、物理的には OpenClaw 側で実行されるが、論理的には Textus Workflow の Execution Authority 下に置く。

Textus Workflow が所有するもの:

- current Workflow / GoalPhase
- Goal と Completion Contract
- Result acceptance
- Workflow state transition
- retry / failure / escalation policy
- continuation identity / revision / idempotency
- Workflow 全体の completion
- execution evidence correlation

OpenClaw は Goal 内部の遂行方法について自由度を持つ。Browser、PC、SaaS/API、AI、Codex などを状況に応じて利用できるが、OpenClaw 自身の判断だけで Workflow の論理状態を進めない。

```text
Textus Job Engine
       |
       v
Textus Workflow             <- Execution Authority
       |
       | Goal + Continuation
       |
       +-- dispatch ----------> OpenClaw Worker
       |                         |
       |                         +-- Browser / PC
       |                         +-- SaaS / API
       |                         +-- AI / Codex
       |                         |
       |       result/evidence  |
       <------------------------+
       |
       v
Completion validation
       |
       v
State transition
```

OpenClaw が Goal の完了を報告しても、それだけでは Workflow completion とはしない。Textus Runtime が handle/revision、Completion Contract、Result/Evidence を検証し、受理した場合のみ次の transition を commit する。

### Integration direction

現時点の推奨接続方式は、方向ごとに責務を分ける。

- Textus -> OpenClaw: HTTP/Webhook 等で Worker を dispatch する。
- OpenClaw -> Textus: MCP を標準入口として Operation、Job、Workflow Continuation を利用する。
- Workflow の意味論的な往復: Continuation Protocol を用いる。

```text
Textus Workflow
      |
      | dispatch (HTTP/Webhook)
      v
OpenClaw Worker
      |
      | MCP: operation / advanceWorkflow
      v
Textus Runtime
```

Webhook や MCP は transport / adapter の選択であり、Workflow semantics そのものには固定しない。Textus 側では Participant / External Driver SPI を維持し、将来 HTTP、Queue、その他の transport を利用しても同一の Workflow/Continuation semantics を保てるようにする。

この境界により、Codex と OpenClaw はともに Continuation Participant として扱える。Codex は software-development-oriented worker、OpenClaw は general-purpose digital worker という実行能力の違いを持つが、Textus から見た論理的な制御契約は共有できる。

## Progressive determinization

初期段階では仕様が曖昧な処理を OpenClaw + AI によって仮運用してよい。

```text
natural-language request
        |
        v
OpenClaw + AI
        |
        v
working practice
```

運用から安定した手順が発見されたら、制御を Textus Workflow に移す。

```text
Textus Workflow
  +-- Collect    -> OpenClaw
  +-- Normalize  -> CNCF Operation
  +-- Validate   -> CNCF Operation
  +-- Approval   -> Human / Slack
  +-- Register   -> OpenClaw
```

さらに安定した末端処理は CNCF Operation / API Adapter に昇格できる。

このため決定化には二段階ある。

1. 制御の決定化: AI が都度判断していた処理順序を Textus Workflow / StateMachine に移す。
2. 実装の決定化: OpenClaw の AI/Browser/PC 操作を CNCF Operation や明示的 API 呼び出しへ置き換える。

AI が本質的に有効な処理（要約、分類、抽出、意味解釈など）は、Workflow 化後も AI Action として残してよい。Workflow 化は AI の排除ではなく、非決定性の所在を明示して制御可能にすることである。

## Job / Batch usage

OpenClaw を起動する処理自体を Textus Job として管理する。

- scheduling / submission
- running / waiting / completed / failed
- result acquisition
- retry / recovery
- diagnostics
- execution evidence

などは Textus Job Engine が管理し、OpenClaw に業務レベルの Job lifecycle を背負わせない。

これにより、Web scraping、legacy UI integration、Excel/帳票処理、データ移行、定期収集、社内システム登録などを、最初は OpenClaw で低コストに統合し、価値と仕様が確認された部分から決定的な Batch / Operation へ移行できる。

## Positioning

- Textus Job Engine: asynchronous lifecycle and operational control
- Textus Workflow: deterministic orchestration and continuation
- OpenClaw: general-purpose integration worker / digital worker
- Codex: software-development-oriented worker
- CNCF Operation: stable deterministic implementation
- AI: explicitly bounded non-deterministic capability

OpenClaw を恒久的な万能 Adapter とする必要はない。未整備な外部世界への暫定的な接続を可能にし、運用から得た知識を Textus/CNCF へ段階的に回収することを基本方針とする。

## Significance

これは「AIで仮運用 -> 定型処理化 -> Textus Workflow化」という Citizen Developer の運用モデルを EAI / Batch 領域へ拡張する。

仕様を完全に確定してから統合プログラムを書くのではなく、曖昧な仕様を AI により実行可能にし、実運用から仕様を発見し、発見した制御と実装を段階的に決定化する。
