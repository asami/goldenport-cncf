# Typed Workflow Protocol, JSON Encoding, and Workflow Composition

Date: 2026-09-20
Status: later-phase design input

> Phase 77 retains only the minimum Skill Continuation wire contract recorded
> in [Phase 77 Minimum Skill Continuation JSON Contract](2026-09-20-phase-77-minimum-skill-continuation-json-contract.md).
> The typed Start model, broad JSON encodings, and parent/child composition
> described below are later-phase extensions.

## Decision

CNCF Workflow protocol の正本は JSON schema ではなく typed Value Object model とする。JSON は Codex/Skill/REST/CLI/UI 等へ渡す標準 encoding の一つである。

このため CNCF core は WorkflowStartRequest、WorkflowHandle、Continuation、WorkOrder、ExecutionRequirement、WorkResult、Evidence、Presentation、TerminalResult 等を適切な Value Object / algebraic data type として定義する。

generic CNCF fields と application-specific fields は型合成する。Start input、Work input/result、Terminal result の application payload は component/application が型として所有し、CNCF は domain semantics を推測しない。

## JSON / Codex boundary

Codex には CNCF Value Object の JSON encoding を渡す。Codex から返る JSON は codec/schema validation を通して typed Result/Evidence に戻してから Workflow runtime に渡す。

Presentation と abstract reasoning requirement も Value Object の一部であり、JSON encoding に投影される。Presentation は制御に使用しない。ReasoningLevel は abstract requirement のまま Skill/Host mapping に渡す。

## Scala workflow composition

Workflow は Scala program 内で Outer/Inner composition されることを前提にする。同一 JVM 内では JSON を介さず typed Value Object を直接渡せる必要がある。

Inner Workflow は独立 WorkflowInstance として durable identity/revision/history/continuation を持てる。Outer は typed start input で child を開始し、typed terminal result を受け取る。Inner の private state を直接参照して Outer progression を決めない。

Inner が WORK_ORDER / DECISION / WAIT で停止しても Outer が thread/process を保持する必要はない。parent/child causal correlation と child-completion boundary を durable に扱える設計にする。

## Architectural consequence

Workflow Protocol Model は wire protocol より上位の概念である。

```text
Typed Workflow Protocol Model
   -> direct Scala composition
   -> JSON encoding -> Codex/Skill/REST
   -> MCP projection
   -> other transports
```

Phase 77 では full child-workflow orchestration feature を作り込む必要はないが、Value Object、identity、correlation、Continuation の設計が将来の typed parent/child composition を妨げないことを acceptance/design constraint とする。
