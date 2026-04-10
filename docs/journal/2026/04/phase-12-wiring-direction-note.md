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

`glue` now has a minimal execution meaning in the phase 12 sample.

The current sample-level implementation applies:

- `request/mode: passthrough`
- `response/mode: passthrough`

This proves the descriptor shape and execution direction, but the implementation
is still inside the sample caller component.

The remaining framework gap is that `glue` is not yet applied by a general
runtime mediation layer.

## Next Step

- move `glue` execution from sample-local caller logic into framework-level runtime mediation
- add convention-based completion for unspecified wiring
- move from sample-local delegated interpretation toward more general runtime mediation
