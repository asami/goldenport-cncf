# Phase 85: Advanced Transaction, Compensation, and Recovery Runtime

Status: planned
Planned: 2026-09-20

## Goal

Extend the local atomic UnitOfWork foundation with advanced distributed transaction and post-commit recovery facilities after the basic Workflow runtime and sm-workflow executable-specification path are available.

## Dependency

- Phase 64.1 local UnitOfWork atomic foundation.
- Phase 64.2 program planning/interpreter foundation.
- May be refined using operational evidence from Workflow consumers.

This Phase is not a prerequisite for CNCF Phase 77 or sm-workflow Phase 1.

## Scope

### Distributed atomic execution

- optional 2PC / equivalent distributed atomic protocol;
- participant capability admission;
- prepare / commit / abort ordering;
- coordinator identity and durable outcome;
- timeout / in-doubt handling;
- fail closed when required atomicity cannot be provided.

### Compensation

- application-defined compensation-handler SPI;
- stable logical action/effect occurrence identity;
- compensation as a new explicit executable occurrence;
- normal authorization / UnitOfWork / observability / idempotency boundaries;
- explicit domain reversal through normal StateMachine transitions where required.

### Durable recovery escalation

- durable `RecoveryRequired` event;
- original `UnitOfWorkId`, causation/correlation and occurrence references;
- compensation failure and irreversible-effect escalation;
- restart/offline-safe recovery obligation;
- explicit administrative/recovery Operation re-entry.

### Recovery boundary

CNCF guarantees execution facts and durable escalation. It does not invent business recovery procedures, synthesize inverse operations, or erase committed history.

## Acceptance direction

- required distributed atomicity never silently degrades to best effort;
- compensation is explicit application logic, not framework-generated rollback;
- compensation failure durably records a recovery obligation;
- recovery facts survive restart/offline consumers;
- manual/admin recovery re-enters through authorized CNCF Operations with audit/history preserved.

## Non-goals

- automatic Saga synthesis;
- compensation-of-compensation chains as a mandatory v1 feature;
- owning application-specific recovery workflow;
- blocking Phase 77 / sm-workflow Phase 1.
