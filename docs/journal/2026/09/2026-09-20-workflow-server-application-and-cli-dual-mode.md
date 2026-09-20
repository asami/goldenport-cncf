# Workflow Server Application and CLI Dual-Mode Decision

status=decision
date=2026-09-20
phase=[Phase 88](../../../phase/phase-88.md)

## Decision

CNCF will support Workflow consumers as full long-lived server applications while preserving one-shot launcher/CLI execution against the same typed application service.

The reference application style is the existing textus-art-scene pattern: use CNCF Entity/Aggregate/View and normal component/datastore bootstrap, including resident master/reference projections where repeated server access benefits from them.

## Motivation

sm-workflow needs two operational forms at once:

- a continuously running server for MCP skill integration, Workflow observation, and efficient repeated access to master/reference data;
- a one-shot CLI/launcher path for local development, CI, recovery, scripting, and cases where running a server is unnecessary.

These must not become separate implementations.

## Boundary

Workflow runtime owns Workflow identity and progression. Aggregate/View provide normal CNCF application modeling and efficient read/reference projections. Job remains optional execution correlation. Server, MCP, REST/Web, and CLI are presentations/hosts around the same runtime truth.

## Consequence

Phase 88 owns the generic CNCF support. sm-workflow consumes it in its post-Phase-1 server application phase. Phase 87 owns the generic Workflow management Web UI and can consume the Views/read contracts produced by this support.

Implementation must first inventory current ArtScene patterns and promote only genuinely generic mechanisms into CNCF.
