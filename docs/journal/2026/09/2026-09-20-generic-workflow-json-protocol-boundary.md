# Generic Workflow JSON Protocol and Application Layer Boundary

Date: 2026-09-20
Status: superseded in protocol scope by
[Phase 77 Minimum Skill Continuation JSON Contract](2026-09-20-phase-77-minimum-skill-continuation-json-contract.md)

> This journal preserves the earlier broad generic-protocol proposal. The
> active Phase 77 decision retains only the minimum Skill Continuation wire
> contract; the Start, Presentation, reasoning-vocabulary, composition, and
> orchestration parts recorded below are later-phase extensions.

CNCF Workflow runtime と sm-workflow の責務を二層に分離する。

CNCF は Start / Handle / Continuation / WorkOrder / Decision / Wait / Terminal / Result / Evidence / Presentation / ExecutionRequirement を含む application-neutral JSON protocol を所有する。抽象 reasoning level も CNCF protocol の execution requirement として定義するが、具体的 model/profile への mapping は Skill/Host policy が所有する。

sm-workflow はこの generic protocol 上の application specialization である。GoalPhase、SplitPhase、RepositorySync、Phase/Step/Slice 等の software-development 語彙と typed payload schema、console wording specialization、reasoning profile mapping policy は sm-workflow 側が所有する。

Presentation は JSON response に含め、Codex console / UI が処理推移と人間向け状況説明を表示できるようにする。ただし presentation text は制御の正本ではなく、parse して progression を決めない。

Phase 77 の executable specification は generic JSON protocol の start -> continuation -> result -> continuation -> terminal round trip を fixture で証明し、sm-workflow はその handoff 上で application-specific executable specification を構築する。
