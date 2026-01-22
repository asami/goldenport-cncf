# Component Loading / Discovery Status (2026-01-22)

## Current state: partially implemented
- `src/main/scala/org/goldenport/cncf/CncfMain.scala:77-180` — `ComponentRepository.parseSpecs(...)` feeds `spec.build(params).discover()` before `CncfRuntime.runWithExtraComponents`, so CLI-supplied repositories are the current hook for dynamic components.
- `src/main/scala/org/goldenport/cncf/CncfMain.scala:157-189` — `_discover_components(workspace)` falls back to `ComponentCreate` + class-directory scanning when `--discover=classes` is enabled, so developer builds on the classpath can register components without additional descriptors.
- `src/main/scala/org/goldenport/cncf/component/repository/ComponentRepository.scala:30-320` — repositories are limited to `scala-cli`, `component-dir`, and `sbt` layouts; discovery uses `ServiceLoader.load(classOf[Component], loader)` followed by `ComponentProvider.provide`, so registration is bounded to those scanners.

## What exists today
- `docs/design/component-repository.md:1-57` — “A Component Repository is an abstract source from which CNCF discovers and loads executable Components,” and the CLI flag `--component-repository=type[:dir]` is the single authoritative discovery mechanism.
- `docs/notes/omponent-discovery-from-classdir.md:1-76` — class-directory discovery relies on ServiceLoader first, then a package-prefix-restricted classpath scan (`// ServiceLoader (preferred) ... // Fallback: classpath scanning`).
- `docs/design/component-loading.md:1-56` — `component.dir` remains the formal deployment contract, but Stage 5 demos still rely on direct classpath loading and container restarts, so no hot reload is wired yet.
- Runtime code in `ComponentRepository` never reads `component.yaml`, `component.conf`, or other descriptor files; it only walks `.class` files and service loader metadata, so there is no manifest-based registry.

## Missing / unimplemented items
- `docs/notes/phase-2.8-infrastructure-hygiene.md:238-249` tracks “Multiple Component Repository priority and override rules” as an outstanding design item, so repository ordering/override semantics have not been wired yet.
- There is still no META-INF descriptor-driven discovery beyond the ServiceLoader usage inside repository scanners, and no `component.yaml`/`components.conf` parser anywhere in `src/main/scala`; the runtime only processes class directories and `--component-repository` specs.

## Planned work & Phase 2.85 context
- `docs/work/phase-2.85-checklist.md:120-180` defines DI-02 (“Deploy and execute HelloWorld component using existing loader”) as the planned verification step for the HelloWorld demo; the checklist explicitly requires that the component can be loaded without altering the loader design.
- `docs/work/phase-2.85-demo-readiness.md:113-143` reinforces that DI-02 is the second stack item after the HelloWorld project and must demonstrate execution via the existing discovery mechanism.

## Resume point for DI-02 (Phase 2.85)
1. Package the HelloWorld component into a layout that matches one of the current repository types (scala-cli/SBT/component-dir) and document the directory you will pass to `--component-repository`.
2. Invoke `cncf command ... --component-repository=<type>:<dir>` (or set `--workspace`/`--discover=classes`) so `CncfMain` parses the spec and reuses `ComponentRepository.Specification.build(...).discover()` as shown in the code references above.
3. Capture CLI output for the HelloWorld operation and note any discovery gaps to feed into NP-003 (“Component lifecycle extension”) and the Phase 2.8 deferred item on repository priority.
# OperationDefinition and CQRS Semantics

This section records the design decision to make **OperationDefinition** the semantic origin
for CQRS roles and operational attributes, rather than inferring behavior at projection time.

### OperationDefinition as Semantic Origin

Each operation explicitly declares its **CQRS role**:

- Command
- Query
- (future) additional categories or refinements

This declaration belongs to the operation model itself, not to transport- or protocol-specific
layers. Semantics must be stable regardless of how an operation is exposed.

### CQRS Role and Operational Attributes

In addition to the primary CQRS role, operations may declare orthogonal attributes, such as:

- idempotent
- high-load
- side-effect / external-effect

These attributes describe *behavioral intent* and are not tied to REST, HTTP, or OpenAPI.

### Projection to OpenAPI and REST

OpenAPI export and REST exposure are treated as **projections**.
They must read semantic information from OperationDefinition rather than infer behavior
from names or conventions.

HTTP method selection, request/response shape, and future protocol mappings are therefore
derived views over the operation model.

### Future Integration with component.yaml / CML

OperationDefinition metadata is expected to be populated from:

- component.yaml
- CML-derived specifications

These sources become the single point of truth, with OpenAPI and other projections
consuming the same semantic model.
