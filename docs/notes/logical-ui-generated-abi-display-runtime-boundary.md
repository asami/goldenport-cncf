# Logical UI Generated ABI and Display Runtime Boundary

Status: proposed Phase 96 specification
Date: 2026-10-03
Producer: Cozy Phase 76
Semantic authority: Cozy Phase 74

## Decision

Logical UI uses the ordinary CML producer/consumer ABI mechanism. CNCF owns the Scala 3 runtime/public ABI. Cozy owns semantic modeling and deterministic generation of Scala 3 source/metadata against that ABI. There is no CNCF runtime dependency on the Scala 2.12 Cozy artifact.

## Ownership

Cozy Phase 74 owns target-neutral Logical UI semantic authority and the admitted minimum vocabulary. CNCF Phase 96 owns Scala 3 generated-ABI value types, validation/admission, ComponentFactory discovery/bootstrap, component-scoped definition lookup, Display Projection, Display Model/protocol and DisplayService. Cozy Phase 76 owns mapping Phase 74 semantics to CNCF ABI, deterministic Scala source/metadata generation, bootstrap/provider metadata, producer fixture and consumer handoff.

## Consumer ABI shape

Follow established generated ABI conventions rather than importing Cozy classes. Conceptually the ABI contains versioned Definition, ListDefinition, DetailDefinition, SectionDefinition, FieldDefinition, ValueDefinition, ActionDefinition, PresentationRole and identity/provenance values. Exact package/type names must follow current CNCF generated-ABI naming.

A bounded metadata provider is discoverable through ComponentFactory using the same pattern as generated Workflow/StateMachine metadata providers. It exposes the generated Logical UI definitions; the exact trait/name follows existing provider conventions.

Admission is fail-closed: validate ABI version, stable identities, closed kinds, deterministic uniqueness/references and provenance before storing admitted definitions on the Component/runtime registry.

## ComponentFactory integration

Use a focused ComponentFactory part or equivalent bounded bootstrap responsibility. The flow is: Component construction -> discover generated Logical UI metadata provider -> ABI admission -> component admitted Logical UI definitions -> DisplayProjection/DisplayService lookup. Do not reparse CML at runtime.

## Display Projection relationship

Display Projection combines admitted Logical UI definition/configuration, semantic View data and explicit value converters. It produces a Display Model instance. Logical UI definition is not itself a Display Model and is not a persisted presentation object.

## Producer fixture contract

CNCF first supplies a hand-written Scala 3 fixture representing the exact generated source shape expected from Cozy. Cozy Phase 76 generates semantically equivalent source/metadata. CNCF accepts the generated fixture through the same admission/bootstrap path. This mirrors StateMachine/Workflow producer-consumer development.

## Prohibited shortcuts

- binary dependency from CNCF to cozy_2.12;
- reflection over Cozy Logical UI implementation classes;
- special cross-build solely for this integration;
- runtime CML reparsing in CNCF;
- a second CNCF semantic UI authority diverging from Phase 74;
- provider/client-specific UI objects in generated ABI;
- Flutter/TFAF runtime types in CNCF ABI.
