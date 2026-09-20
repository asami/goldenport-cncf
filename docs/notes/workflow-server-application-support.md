# Workflow Server Application Support

status = proposed, non-normative
date = 2026-09-20
target_phase = 88

## Direction

Workflow applications should be able to use CNCF as full server applications, not merely as transient command executors. The model follows the established Textus/CNCF application style exemplified by textus-art-scene: normal component bootstrap, datastore-backed Entity/Aggregate state, View/read projections, and resident reference/master data for efficient repeated access.

Workflow adds no parallel application framework. It plugs into those facilities.

## Server and one-shot equivalence

Server and CLI are two hosting modes around one application service.

    long-lived server ----+
                          |
                          v
                    Workflow Service
                          ^
                          |
    one-shot launcher ----+

The server may retain caches, Views, indexes, definition catalogs, and providers across requests. One-shot mode reconstructs the minimum equivalent runtime for one invocation. Observable semantics, identity, authorization, persistence, and result contracts remain equivalent.

## Aggregate/View use

Workflow execution authority remains the canonical Workflow runtime. Aggregate and View support should be used to make application state and read-heavy projections efficient and composable, not to create duplicate mutable Workflow status.

Useful View projections include running/completed Workflow lists, component/workflow/status indexes, current progression summary, result/diagnostic summary, and correlations to Jobs.

Application-specific Aggregates may own application data surrounding a Workflow run. They must not silently replace WorkflowInstance identity or progression authority.

## Resident master/reference data

Long-lived applications often repeatedly consult stable data such as Workflow definitions, profile metadata, policy tables, repository/project descriptors, operation metadata, and presentation labels. CNCF should support loading these through canonical Collection/View mechanisms and retaining them in process.

The persistent/configured source remains authoritative. Refresh must be explicit and version-aware. Server startup and one-shot bootstrap should use the same loader contract so behavior does not diverge.

## Presentation adapters

- Web/REST: human/application integration and Phase 87 management.
- MCP: AI/Skill integration.
- CLI/launcher: local, CI, debugging, and single-invocation use.

Adapters must not own progression logic. They call typed operations/application services.

## Operational principle

A server is the preferred operational form when repeated Workflow interaction, observation, resident reference data, or MCP connectivity is required. A server is not a semantic prerequisite for executing a Workflow.
