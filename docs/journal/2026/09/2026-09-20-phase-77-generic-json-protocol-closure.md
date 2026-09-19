# Phase 77 Generic JSON Protocol Closure Decisions

Date: 2026-09-20
Status: superseded in protocol scope by
[Phase 77 Minimum Skill Continuation JSON Contract](2026-09-20-phase-77-minimum-skill-continuation-json-contract.md)

> This journal preserves the earlier broad Phase 77 proposal. Phase 77 now
> retains only the minimum schema-versioned, fail-closed Continuation wire
> contract; the Start, Presentation, reasoning-vocabulary, and broad protocol
> parts below are later-phase extensions.

## Context

sm-workflow の GoalPhase 開始境界を検討する過程で、start input だけでなく start result、continuation、work result、terminal result までを JSON protocol として閉じる必要が明確になった。

この protocol は sm-workflow 固有機能ではなく CNCF Workflow runtime の基本機能である。sm-workflow はその上に software-development application schema を載せる。

## Decision

Phase 77 は application-neutral な JSON protocol を提供する。

- StartRequest / StartResult
- WorkflowHandle
- Continuation: WORK_ORDER | DECISION | WAIT | TERMINAL
- WorkOrder
- typed Result / Evidence
- Decision / Wait / Terminal payload
- Presentation
- ExecutionRequirement

application-specific payload は schemaId/schemaVersion 付き envelope に格納し、CNCF は domain semantics を推測しない。

## Reasoning level

Continuation が WORK_ORDER の場合、WorkOrder.executionRequirement に abstract reasoning level を含める。

初期語彙:

- ROUTINE
- STANDARD
- DEEP
- CRITICAL

具体的な model/provider/reasoning effort は Skill/Host の versioned mapping policy が決める。Workflow semantics は model 名に依存しない。actual execution profile は evidence として返却可能とする。

## Human-visible progression

JSON response は Presentation を含み、Codex console / UI で現在状況、次の作業、理由、進捗を人間が追えるようにする。

Presentation は projection であり control source ではない。制御は structured fields のみを使用する。

## Application layer

sm-workflow は GoalPhaseStartInput、GoalPhaseResult、software-development WorkOrder payload 等を所有する。Goal/Phase/Step/Slice、repository、review、validation、commit といった語彙を CNCF generic protocol に持ち込まない。

## Phase 77 acceptance implication

Phase 77 の executable specification では、real/generated fixture に対して少なくとも次を証明する。

1. typed StartRequest を受理する。
2. handle と最初の Continuation を返す。
3. WORK_ORDER が abstract reasoning requirement と presentation を持てる。
4. typed Result/Evidence を submit できる。
5. deterministic progression 後に次の Continuation を返す。
6. terminal 時に generic outcome + typed application result を返す。
7. console projection が JSON から生成できる。
8. presentation text や concrete model 名を progression 判断に使用しない。

これにより CNCF Phase 77 closure 後、sm-workflow Phase 1 は generic protocol の consumer/application として直ちに executable specification 作業へ進める。
