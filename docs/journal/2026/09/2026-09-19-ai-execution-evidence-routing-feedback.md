# AI Execution Evidence と Routing Feedback Loop

- Date: 2026-09-19
- Status: Design consideration

## Context

Textus Workflow では AI Action を Workflow の一部として扱える。AI Engine は固定せず、Local LLM、Cloud LLM、Codex、Human など複数の実行 Provider を Capability と要求品質に応じて使い分ける。

重要なのは、AI Engine の選択を一度決めて終わりにせず、実運用から Evidence を取得し、その結果を Routing Policy へフィードバックすることである。

## Separation of concerns

Workflow には具体的な model 名を原則として埋め込まない。

```text
Workflow
   |
   v
AI Capability Requirement
   |
   v
AI Routing Policy
   |
   +-- Local LLM
   +-- Cloud LLM
   +-- Codex
   +-- Human
```

Workflow は例えば次のような要求を記述する。

- capability: summarize / classify / extract / compare / reason / generate
- quality requirement
- privacy / locality constraint
- latency requirement
- cost constraint
- structured output / validation requirement

Provider / model の具体的選択は Routing Policy の責務とする。

ただし、AI -> stronger AI -> Human のような escalation 自体が業務上の意味を持つ場合は Workflow に明示してよい。

## Evidence

AI の自己申告 confidence だけを評価根拠にしない。Workflow の後続処理から得られる実際の Outcome を Evidence として関連付ける。

```text
AI execution
    |
    v
validation
    |
    v
workflow continuation
    |
    +-- human correction
    +-- retry / escalation
    +-- downstream success/failure
    +-- final outcome
    |
    v
Evidence
```

AIExecution の Evidence 候補:

- Workflow / Job / phase identity
- AI Capability
- Provider / model
- prompt / configuration version
- input characteristics
- generated result reference
- schema / rule validation result
- retry / escalation
- human correction
- downstream outcome
- latency
- token / resource usage
- cost

raw input/output の保存は privacy、security、retention policy に従い、必要に応じて bounded metadata / reference とする。

## Feedback loop

```text
             Evidence Store
                   ^
                   | outcome
                   |
Workflow -> AI Router -> Provider
              |
              v
         Routing Policy
              ^
              |
        evaluation / update
```

Capability x Input Characteristics x Provider/Model ごとに、少なくとも以下を評価できるようにする。

- success rate
- validation failure rate
- human correction rate
- escalation rate
- retry rate
- latency
- cost

例えば短い定型的な分類や抽出は Local LLM、長文や高度な推論は Cloud LLM / Codex といった選択を、印象ではなく実運用 Evidence に基づいて改善できる。

## Progressive determinization of routing

Routing 自体も漸進的に決定化する。

初期:

```text
AI / heuristic chooses provider
```

Evidence が蓄積した後:

```text
Evidence
   |
   v
Metrics
   |
   v
Routing Policy
   |
   v
deterministic provider selection
```

例えば、特定 Capability と Input 条件で Local LLM が十分な品質を継続して示すなら、その範囲を決定的 Routing Rule にできる。例外や未知の入力だけを高度な AI 判断や Human Review に残す。

## Workflow role

Textus Workflow は Evidence 採取と Feedback の双方を実行可能にする。

1. AI Capability を実行する。
2. 出力を schema / rule / executable contract で検証する。
3. 必要なら別 Provider へ escalation する。
4. Human correction や後続処理の結果を取得する。
5. Job / Workflow execution trace と AI execution を相関させる。
6. Evidence を集計する。
7. Routing Policy の変更候補を生成・評価する。
8. Policy 変更を承認・適用する。

Policy の自動更新は、十分な Evidence、bounded rule、rollback、auditability が成立する範囲から段階的に導入する。

## Significance

この仕組みにより Textus は AI を単に呼び出す Runtime ではなく、AI 利用を継続的に観測・評価・最適化する Harness になる。

```text
AI usage
   -> Evidence
   -> Evaluation
   -> Routing improvement
   -> Deterministic policy
   -> Exception-only AI reasoning
```

AI Engine の能力や価格が変化しても Workflow semantics を維持し、Capability 単位の実運用 Evidence に基づいて最適な Provider を交換できることを目標とする。
