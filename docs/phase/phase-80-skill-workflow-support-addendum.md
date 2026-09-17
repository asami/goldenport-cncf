# Phase 80 Addendum: Generic Skill Workflow Support

Status: planned / normative addendum to Phase 80

## Goal

Participant Invocation / Continuation runtimeの上に、特定domainに依存しないGeneric Skill Workflow Supportを提供する。

## Requirements

- generic ContinuationをSkill向けcompact projectionとして取得できる。
- control-plane `advance/resume/status/submit` をAI child invocationなしで直接利用できる。
- Skill WorkOrderはsemantic boundaryだけを表し、deterministic operationをWorkOrder化しない。
- ContextBundleをSkill Context Viewとして必要最小限にprojectionし、reference/lazy dereferenceを提供できる。
- WorkOrderにmodel-independent capability/complexity/risk/review metadataを付与できる。
- Skill Resultをgeneric Result/Evidenceへnormalizeできる。
- Parent/Worker間でconversation全体を引き回さず、structured Result/Evidenceでhandoffできる。
- AI/Human/remote continuationがgeneric runtime identity/revision/idempotencyを共有する。
- Software Development固有語彙、git/sbt/build policyをgeneric Skill Workflow contractに含めない。

## Reference consumer

`asami/sm-workflow` をSoftware Development specialization/reference consumerとして利用し、実運用からgeneric candidateを抽出する。ただしPhase 80 contractを`sm-workflow`固有要件へ固定しない。
