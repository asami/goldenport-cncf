# SpecificationComponent Design — Canonical Specification Exposure

status = draft
since = 2026-01-08

## 1. Background

Specification exposure is treated as a first-class runtime concern.
Demo development is used to discover and fix correct architecture rather than applying quick hacks.
This design note records the agreed architecture for specification delivery.

## 2. Component / Service Structure

- SpecificationComponent
  - Responsibility: export runtime specifications (read-only).
  - No execution logic, no state mutation, no persistence.
- ExportSpecificationService
  - formats(): List[String]
  - export(format: String): String
  - Returns raw spec content, not HttpResponse.
- OpenApiProjector is an internal helper owned by SpecificationComponent.

## 3. Canonical Path and Aliases

- Canonical (FQN):
  - /spec/export/openapi
- Default representation is JSON.
- Suffix rules:
  - /spec/export/openapi        -> JSON (default)
  - /spec/export/openapi.json   -> JSON
  - /spec/export/openapi.html   -> HTML
  - Unsupported suffixes must result in an explicit error.
- Aliases:
  - /openapi(.json|.html)
  - /spec/openapi(.json|.html)
  - /spec/current/openapi(.json|.html)
  - Aliases are internally resolved to the canonical FQN.
  - No redirect is required in the initial implementation.

## 4. Rationale for "current"

- "current" represents the specification of the currently running runtime/server.
- The name is provisional.
- Future experience with additional components may lead to a better term.
- Canonical FQN allows aliases to remain stable even if naming evolves.

## 5. Future Expansion (Not Implemented Now)

- OpenAPI may later grow into its own domain:
  - /spec/openapi/spec
  - /spec/openapi/ui
- This is intentionally deferred.
- Current design does not block this evolution.

## 6. Relationship to Existing Components

- HelloWorldHttpServer must not own OpenAPI logic directly.
- It delegates specification requests to SpecificationComponent.
- HttpExecutionEngine remains untouched.

## 7. Design Evolution Notes

- The current OpenAPI exposure is used to validate the HTTP surface.
- Specification paths are expected to evolve toward:
  - /spec/schema/*
  - /spec/idl/*
- The current OpenAPI exposure is a stepping stone, not the final taxonomy.

## 8. Non-Goals

- No execution via SpecificationComponent.
- No persistence or versioning in Phase 2.6.
- No CLI changes at this stage.
