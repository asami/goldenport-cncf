# Component Discovery and Initialization

This document summarizes the component discovery and initialization pipeline
that AI agents must respect when reasoning about CNCF runtime behavior.

Related:
- docs/design/component-repository.md
- docs/design/component-factory.md
- docs/design/component-loading.md

## Problem Statement

CNCF must discover components from multiple repositories and initialize them
with explicit runtime context. The process must be deterministic, explainable,
and compatible with non-classpath repositories.

## Why Naive Approaches Fail

- Classpath-only discovery ignores repository types and origins.
- Direct instantiation bypasses ComponentFactory/Provider responsibilities.
- Components initialized without ComponentInitParams miss required context.
- Implicit context inference breaks the explicit-context rule.

## Adopted Design

- ComponentRepository resolves repository specs and produces discovered classes
  with their ClassLoader and origin metadata.
- ComponentFactory decides applicability, prepares the Core plan, and delegates
  instantiation to a ComponentProvider.
- ComponentProvider performs reflection or singleton resolution and returns
  instantiated Components or explicit failures.
- Each Component is initialized via initialize(ComponentInitParams) to bind
  subsystem, core, and origin explicitly.
- There is no runtime interpretation of a `ComponentDefinition` DSL anymore;
  discovery resolves only concrete `Component` classes (including script- or
  repository-generated classes). ComponentProvider therefore always receives an
  instantiable class and expects the reflection/constructor path described
  above, and future code generation must emit such classes rather than
  supplying separate definition artifacts.
- ExecutionContext remains a runtime-only carrier; RuntimeContext governs UnitOfWork behavior
  and resolves HttpDriver via ScopeContext’s parent chain, and SystemContext/ApplicationContext
  wiring has been removed from the initialization pipeline.

## Operational Implications

- Debugging should identify which stage failed: repository discovery, factory
  selection, provider instantiation, or component initialization.
- Diagnostics should include repository spec, origin label, class loader, and
  instantiation path, using BootstrapLog during early bootstrap.
- AI reports must distinguish verification of this pipeline from validation of
  user requirements, per docs/ai/verification-validation.md.

### Context invariants (2026-01)

- ExecutionContext is runtime-only and only lives for the duration of an OperationCall execution.
- RuntimeContext governs execution behavior (UnitOfWork lifecycle, interpreters, commit/abort/dispose) and resolves resources via ScopeContext hierarchy.
- ScopeContexts inherit driver/observability from their parent and eventually delegate to GlobalRuntimeContext as the terminal provider.
- SystemContext and ApplicationContext are removed; do not introduce them for wiring or provisioning.
- RuntimeMetadata is removed; runtime introspection is produced directly from RuntimeContext/ScopeContext.
- Test helpers may use ExecutionContext.create()/test() to inject fake RuntimeContext implementations for specs and demos.
