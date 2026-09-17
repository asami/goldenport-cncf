# Generic Skill Workflow Support

Date: 2026-09-17
Status: Design decision

## Decision

Skill経由でdurable Workflowを利用する方式は今後一般化すると見込み、Skill向けの汎用Workflow支援はCNCF側で提供する。

`sm-workflow` はSoftware Development Workflowに特化したconsumer/reference applicationとし、汎用Skill Workflow基盤の所有者にはしない。

## Layering

```text
Cozy / CML
  Generic Workflow semantics
  Participant Invocation Binding
  Context / Continuation contracts
        |
        v
CNCF Workflow Runtime
  Generic Workflow Runtime
  Generic Skill Workflow Support
        |
        +--> sm-workflow
        |      Software Development specialization
        |
        +--> Knowledge workflow
        +--> Document workflow
        +--> Operations workflow
        `--> other skill workflows
```

## Generic Skill Workflow Support

CNCF側で、汎用Continuation/Invocation contractをSkillから使いやすくするprojection/supportを整備する。

候補:

- skill-friendly `advance` / resume API
- compact Continuation projection
- Skill WorkOrder projection
- ContextBundle / ContextReference のskill向け解決境界
- Context Budget / lazy dereference
- Parent/Worker handoff contract
- capability / complexity / risk metadata
- structured Result / Evidence normalization
- conversation history非依存のresume
- pending continuation discovery
- AI/Human continuationの共通identity/revision/lease/idempotency
- Skill bundle / protocol compatibility metadataとの連携

これらは可能な限りprogram/runtime側に置き、SKILL.mdの自然言語制御ロジックへ埋め込まない。

## Promotion rule

`sm-workflow` の実利用から便利機能を発見した場合、次の順で一般性を判定する。

```text
sm-workflow discovery
  -> software-development specific ?
       yes -> sm-workflow
       no  -> CNCF Generic Skill Workflow Support
                 -> skill-specific ?
                      yes -> CNCF Skill Workflow layer
                      no  -> Generic Workflow/CML-CNCF contractへ昇格
```

この二段階昇格により、software development固有概念を汎用protocolへ漏らさず、実利用から汎用機構を成熟させる。
