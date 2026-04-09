# Phase 12 Wiring Direction Note

## Purpose

Record the current framework-side direction for phase 12 wiring.

This note captures the current design stance behind:

- subsystem-level wiring
- `api` / `spi` ports
- runtime mediation
- observable assembly results

## Agreed Direction

### Wiring Is Defined At Subsystem Level

- subsystem descriptor may specify wiring explicitly
- unspecified wiring may later be completed by convention

### Wiring Uses Ports

The primary binding model is:

- caller-side `api`
- callee-side `spi`
- runtime-resolved binding between them

The intended abstraction is:

- `caller.api.x -> callee.spi.y`

rather than direct hard-coded component references as the primary model.

### Runtime Mediation Is Preferred

Phase 12 wiring is not primarily modeled as DI-style reference injection.

The preferred direction is:

- runtime mediation
- descriptor-driven routing
- subsystem-level interception

This preserves chokepoints for:

- authorization
- logging
- metrics
- tracing
- retry and timeout policy
- future governance and policy control

## Current Runtime State

The framework now supports:

- declared component `api` / `spi` ports
- raw subsystem `wiring`
- resolved `wiring_bindings`
- `glue` metadata carried into resolved bindings
- `admin.assembly.report` as the current observability surface

## Remaining Gap

`glue` is currently metadata only.

It is observable, but it does not yet execute request / response adaptation logic.

## Next Step

- give `glue` runtime execution meaning
- add convention-based completion for unspecified wiring
- move from sample-local delegated interpretation toward more general runtime mediation
