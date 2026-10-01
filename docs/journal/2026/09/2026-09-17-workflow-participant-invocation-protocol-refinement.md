# Workflow Participant Invocation Protocol refinement

Date: 2026-09-17
Status: Design decision / Phase 80 input

## Decision

CNCF Workflow Runtime は Orchestration / Continuation を WorkflowRun 全体の排他的execution modeとして扱わない。CML generated ABI が宣言する Action/Participant単位の `InvocationBinding` として実行する。

```text
Workflow Runtime
  -> BuildProject       : ORCHESTRATION
  -> RequestApproval    : CONTINUATION -> UI
  -> ReviewChange       : CONTINUATION -> AI
  -> CommitChanges      : ORCHESTRATION
```

ORCHESTRATIONではRuntimeがParticipant/Operationを直接invokeする。CONTINUATIONではRuntimeがContinuationをdurably persist/yieldし、process/threadを保持せずreturnする。後からUI/AI/remote driverがResultをsubmitし、snapshot/contractを検証してresumeする。

## Human UI

Human Approvalを`openDialog`等のRuntime Actionとして実装しない。Runtimeは`RequestApproval : ApprovalContext -> ApprovalDecision`のInvocation Contractを扱う。

UI adapterはContinuationを取得し、schema/presentation metadataから表示を構築し、ApprovalDecisionをsubmitする。UIのsession lifetimeとWorkflowRun lifetimeを分離する。

## Common continuation clients

- Web / Flutter / CLI UI: HUMAN continuation client
- Codex/ChatGPT Skill: AI continuation client / driver
- remote worker/service: remote continuation client

Participantが異なっても、Continuation identity、revision/snapshot、lease、completion/evidence validation、idempotent resumeのruntime機構を共有する。

## Runtime invariant

Continuation client/driverがWorkflow semanticsを所有しない。次のvalid action/stateはWorkflow Runtimeが決める。UI/AIはContinuation Contractに対するResultを返すだけである。

## Phase 80 impact

Phase 80ではAction/Participant単位のbinding dispatch、mixed-protocol workflow、durable human continuation、stale snapshot rejection、UI/AI共通resume contractをruntime acceptanceへ追加する。

## 2026-09-18 — AI時代のWorkflow設計原則とCitizen Developer Goal

### Goal

CNCF Workflowは、AIによるad-hocな業務遂行を、業務担当者自身が段階的に安全・再現可能・観測可能なWorkflowへ育てられる基盤を目指す。

特に、プログラマだけでなく、Excelで高度な関数や簡単なmacroを組み、業務改善を自ら行える程度の技術的勘を持つ **Citizen Developer** を主要な利用者像に含める。

Citizen DeveloperはScala等の実装コードを直接理解することを必須としない。Goal / Capability / Workflow / StateMachineと自然言語を主な操作対象とし、Codex等がモデル・実装・Executable Specificationの生成、build、test、修正を支援する。複雑化した場合はprofessional developerが同じ成果物を引き継げることを重視する。

### AIで仮運用し、安定部分をWorkflowへ蒸留する

最初から完全な業務Workflowを設計しない。まずAIに自然言語で業務を依頼してad-hocに運用し、繰り返しの中から安定したprocess patternを発見する。

```text
Ad-hoc AI Operation
        ↓
Process Pattern Discovery
        ↓
Workflow Candidate
        ↓
Human Review / Refinement
        ↓
Textus / CNCF Workflow
        ↓
Stable Business Operation
```

Workflow化の過程では、決定的にできる処理をAI Agent内部からCNCF側へ順次移す。

- goal/context interpretation、曖昧な分類、推論、生成はAI Participantに残せる。
- 手順制御はWorkflowへ移す。
- 状態管理はStateMachineへ移す。
- human/AI待ちはContinuationへ移す。
- 安定した処理はOperation / Capabilityへ移す。
- retry、timeout、authorization、observability、evidenceはRuntime/Harness側で管理する。

成熟するほどAIが所有する制御範囲を小さくし、非決定的な知的処理だけをAIへ残すことを基本方向とする。

### WorkflowがAIを制御する

AgentがWorkflow semanticsを所有する構造を基本形にしない。CNCF Workflow / StateMachineがcontrol flowを所有し、AIをParticipantとして呼び出す。

```text
Workflow / StateMachine
  ├─ System Action  -> Operation
  ├─ AI Action      -> ChatGPT / Codex
  │                    ↓
  │                 Continuation Result
  ├─ Human Action   -> Slack / UI
  │                    ↓
  │                 Continuation Result
  └─ System Action  -> Operation
```

HumanとAIはExecution Model上、いずれも外部Participantになり得る。IoC / Continuation Protocolにより、Runtimeはprocess/threadを保持せずsemantic boundaryでsuspendし、結果を受けてresumeする。

これによりAI固有のAgent Frameworkへcontrol flow、memory、retry、human approval等を重複実装する必要を減らす。

### AI層とExecution Harnessの境界

推奨構造は次の通り。

```text
Slack / Human
      ↓
ChatGPT / Codex
  goal interpretation
  reasoning / generation
  capability selection
      ↓
===== Harness Boundary =====
      ↓
Textus / CNCF
  Workflow / StateMachine
  Capability / Operation
  Continuation / Job
  Authorization / Guard
  Executable Specification
  Observability
```

AI層は人間との曖昧なinterfaceおよび非決定的処理へ集中し、決定的なprocess controlを外側のCNCFへ置く。

### External service access

Google Workspace / GitHub等の外部serviceは、原則としてChatGPT/Codex側のuser-scoped integrationを優先する。CNCF Workflowが直接credentialを保持して外部APIへアクセスするのは、background execution、durable continuation、bulk deterministic operation等、Workflow自身が外部状態を所有する必要がある場合に限定する。

Workflow側で直接accessする場合はCapability/Operation単位のleast privilegeとGuardを適用し、広い常設credentialを避ける。

### CodexによるWorkflow化支援

Ad-hoc AI OperationからWorkflow Candidateへの移行はCodexによる半自動化を想定する。

Codexは実行履歴・会話・既存Capabilityを材料に、Workflow / StateMachine / Operation / Executable Specificationを提案・生成し、build/testを実行する。Citizen Developerは「承認を追加」「判断不能なら人間へ回す」「3回retry」等の業務自然言語で修正・debugできることを目標とする。

これはGUI中心のNo-codeではなく、**自然言語 + Model + AI-generated implementation** によるCitizen Developmentである。生成されたmodel/code/specは通常のsoftware assetとして残し、専門開発者によるreview・拡張を可能にする。

### Design principle summary

1. AIでまず運用し、実際の仕事からWorkflowを発見する。
2. 安定したcontrolをAI内部からCNCF Workflow / StateMachineへ蒸留する。
3. AIはWorkflowのownerではなく、非決定的知的処理を担うParticipantとする。
4. Human / AI / Systemを同じWorkflow Model上で扱う。
5. IoC / Continuationによりlong-running interactionをdurableかつobservableにする。
6. 外部service integrationはAgent側を優先し、Workflow credentialはleast privilegeにする。
7. CodexでWorkflow実装・test・debugを半自動化し、Citizen Developerが自然言語とmodelを中心に改善できるようにする。
8. WorkflowとExecutable SpecificationをAI製品・モデルから独立した長期的software assetとする。


## 2026-10-01 — Production Agent Architecture refinement

### Agent runtime is software, not prompting

Production Agent の実行主体を LLM / Skill としない。Agent System は stateful execution system を骨格とし、AI はその内部で非決定的な判断・生成を担う Action / Participant と位置付ける。

> Agent = Stateful Execution System + Intelligent Actions

Workflow / StateMachine が state、transition、Continuation、retry/failure、Admission、audit/evidence を所有する。AI、Human、External Service は typed contract に対する結果を返す。

### LLM is not a state carrier

LLM / Skill の会話履歴や前回出力に workflow state や長寿命の構造化データを持ち回らせない。field 欠落、rename、nesting drift、古い値による上書き等は自然言語実行では fail-fast せず、長い Agent execution ほど累積するためである。

canonical state は typed/persisted runtime model に置き、各 AI invocation にはその Action に必要な bounded typed input だけを materialize する。AI result も typed result として回収し、runtime が validation / Admission 後に state transition へ反映する。

### AI result is a candidate, not state

非決定的な AI output を直接 authoritative state とみなさない。

```text
Workflow / StateMachine
        |
        v
Judgment / Generation Action
        |
        v
AI Candidate Result
        |
        v
Validation / Admission
        |
        v
Accepted State Transition
```

Candidate が不正・不十分なら reject / retry / escalate でき、authoritative workflow state を壊さない。この原則は Candidate-Admission Model と Continuation runtime を Agent execution に接続する。

### AI as bootstrap for formalization

Citizen Development の価値は、最初から完全な StateMachine を設計しなくても仕事を開始できる点にある。曖昧な業務は AI / Skill で exploratory execution し、AI Audit / runtime evidence を蓄積する。繰り返し現れる安定部分を Workflow / StateMachine / rule / deterministic Operation へ段階的に昇格させる。

成熟に伴って AI 比率が下がることを失敗とみなさない。非決定性が不要な部分を software asset へ回収できたことが system maturity である。AI は未形式化業務と形式化された software の間を埋める bootstrap mechanism として使う。

### Production Agent invariant

1. Workflow / StateMachine Runtime owns execution semantics and canonical state.
2. LLM / Skill is not a state carrier.
3. AI receives bounded typed input and returns typed candidate result.
4. Candidate is validated/admitted before authoritative state transition.
5. Human / AI / deterministic Operation can coexist as Actions under the same runtime.
6. Evidence from exploratory execution feeds progressive determinization.
7. Stable control migrates out of AI into Workflow / StateMachine / rule / program; genuinely semantic judgment may remain AI-backed.
