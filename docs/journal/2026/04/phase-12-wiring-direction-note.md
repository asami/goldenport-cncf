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

### Resolved Assembly Is The Wiring Diagram

The intended operating model is:

1. The subsystem descriptor lists the components that are required.
2. CNCF resolves wiring by convention where explicit wiring is not provided.
3. CNCF resolves `glue` by convention where explicit glue is not provided.
4. The subsystem should run with those resolved bindings.
5. The resolved assembly result becomes the runtime-generated wiring diagram.

The wiring diagram should include:

- selected components
- declared or inferred `api` ports
- declared or inferred `spi` ports
- resolved wiring edges
- resolved `glue`
- convention-completed entries
- warnings, conflicts, and selection reasons

`admin.assembly.report` should be treated as the current retrieval surface for
that wiring diagram. Later, the same resolved result should be exportable back
into a subsystem descriptor, or into a separate assembly descriptor, so the
operator can review and pin the convention-generated wiring.

The wiring diagram should be retrievable in two classes of representation:

- descriptor representation
  - YAML / JSON / HOCON document
  - directly usable as a subsystem descriptor fragment or standalone assembly descriptor
  - intended for review, version control, editing, and re-application
- visual representation
  - SVG or another web-renderable diagram format
  - intended for admin console / dashboard display
  - should show components, ports, wiring edges, glue, convention-generated entries, and warnings

The descriptor representation is the authoritative export format.
The visual representation is a projection of the same resolved assembly model.
They should not diverge semantically.

This separates:

- input subsystem descriptor
  - minimum intent, often component-list-first
- resolved assembly / wiring diagram
  - runtime-completed result, suitable for review, dashboard display, and descriptor export

## Current Runtime State

The framework now supports:

- declared component `api` / `spi` ports
- raw subsystem `wiring`
- resolved `wiring_bindings`
- `glue` metadata carried into resolved bindings
- `admin.assembly.report` as the current observability surface

## Remaining Gap

`glue` now has a minimal execution meaning through the framework `Subsystem.executeWired` helper.

The current framework-level helper applies:

- `request/mode: passthrough`
- `response/mode: passthrough`

This proves the descriptor shape and execution direction without keeping the
passthrough rule inside the sample caller component.

The remaining framework gap is that the mediation entry point is still an
explicit helper call from the sample, not a fully general dispatch path.

## Next Step

- move from explicit `executeWired` helper calls toward more general runtime-mediated dispatch
- add convention-based completion for unspecified wiring
- move from sample-local delegated interpretation toward more general runtime mediation
