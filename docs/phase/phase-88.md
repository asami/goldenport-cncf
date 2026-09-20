# Phase 88: Workflow Server Application Support

Status: planned
Planned: 2026-09-20
Depends on: Phase 77
Related: Phase 87

## Goal

Provide CNCF runtime support for long-lived Workflow server applications that use the normal Entity/Aggregate/View facilities, while preserving the same Workflow semantics for one-shot launcher/CLI execution.

The first representative consumer is sm-workflow. The implementation should reuse the application-runtime patterns proven by textus-art-scene rather than introducing a Workflow-specific storage/application framework.

## Required capabilities

1. **Long-lived server runtime**
   - host Workflow application services in a continuously running CNCF/Textus process;
   - keep admitted master/reference data resident through canonical Collection/View facilities;
   - reuse normal ComponentFactory/provider/bootstrap and configuration boundaries.

2. **Workflow Aggregate/View integration**
   - expose Workflow instance/progression data through canonical Aggregate/View read models where appropriate;
   - support efficient enumeration/filtering needed by Phase 87 Workflow Management;
   - avoid a UI-only or server-only shadow registry.

3. **Resident reference/master data**
   - allow Workflow definitions, application profiles, policy/reference data, and other stable lookup data to be loaded once and reused by server requests;
   - retain datastore/version authority and explicit refresh/reload semantics;
   - do not make an in-memory cache the source of truth.

4. **Shared application service**
   - server transports and launcher/CLI invoke the same typed Workflow application service;
   - server lifetime is an optimization/operational mode, not a different Workflow model.

5. **One-shot execution**
   - the same component remains runnable through the CNCF launcher/CLI without a pre-existing server;
   - one-shot mode bootstraps the required runtime, loads required reference data, invokes the exact typed Operation, persists/returns the result, and shuts down cleanly;
   - durable Workflow instances created in one-shot mode remain observable when the configured durable provider is shared.

6. **Presentation neutrality**
   - REST/Web/MCP/CLI are adapters over the same typed service/runtime truth;
   - MCP is not required for CLI execution and CLI is not required for server execution.

## Runtime shape

    Component / Application
      |
      +-- ComponentFactory / providers
      +-- Entity / Aggregate
      +-- View / resident reference projections
      +-- Workflow application service
      |     +-- start
      |     +-- submit result / decision
      |     +-- advance
      |     +-- status/history/result
      |
      +-- Server host
      |     +-- REST/Web
      |     +-- MCP adapter
      |
      +-- One-shot launcher/CLI
            +-- same typed application service

## sm-workflow acceptance target

The support is sufficient when sm-workflow can:

- run as a long-lived server;
- keep its stable workflow/profile/reference data resident through CNCF collection/view support;
- persist and query Workflow instances through canonical runtime contracts;
- expose Skill-facing MCP without embedding Workflow semantics in the MCP adapter;
- appear in the generic Phase 87 Workflow Management Web surface;
- execute the same typed operation from launcher/CLI when no server is running.

## Non-goals

- sm-workflow application policy or GoalPhase semantics.
- A second Workflow engine for server mode.
- Mandatory Job wrapping of Workflow execution.
- Requiring a daemon for local/CI usage.
- Treating cached master/reference data as authoritative mutable state.
- Duplicating generic Entity/Aggregate/View facilities.

## Implementation note

Before coding, inventory the current textus-art-scene server bootstrap, Aggregate/View use, resident/reference-data loading, datastore lifecycle, and standalone restart patterns. Reuse the CNCF-level mechanism where it is generic; keep ArtScene-specific policy out of CNCF.

## References

- [Phase 77](phase-77.md)
- [Phase 87](phase-87.md)
- [Workflow Server Application Support Note](../notes/workflow-server-application-support.md)
- [Decision journal](../journal/2026/09/2026-09-20-workflow-server-application-and-cli-dual-mode.md)
