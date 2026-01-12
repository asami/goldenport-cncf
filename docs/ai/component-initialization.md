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

## Operational Implications

- Debugging should identify which stage failed: repository discovery, factory
  selection, provider instantiation, or component initialization.
- Diagnostics should include repository spec, origin label, class loader, and
  instantiation path, using BootstrapLog during early bootstrap.
- AI reports must distinguish verification of this pipeline from validation of
  user requirements, per docs/ai/verification-validation.md.
