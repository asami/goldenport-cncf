# Phase 82: Workflow Scheduling and Lifecycle Runtime

Status: planned

## Goal

長時間 Workflow に必要な時間制御と lifecycle control を提供する。

## Scope

- Deadline
- general Timer / Wait
- Cancellation
- runtime-internal durable suspension/resumption
- restart-safe timer/wait scheduling

公開 suspendWorkflow / resumeWorkflow API は必須とせず、semantic event / continuation / advance を基本とする。
