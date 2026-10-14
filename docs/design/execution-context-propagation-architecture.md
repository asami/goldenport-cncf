# ExecutionContext Propagation Architecture

Status: normative design
Date: 2026-10-15
Related: ExecutionContext, Safety Sandbox Architecture

## Purpose

CNCF establishes explicit ExecutionContext propagation as a first-class execution-platform mechanism across Client -> Server and other execution boundaries.

The objective is not to serialize an ExecutionContext object. ExecutionContext contains runtime-local facilities and resolved authority that must not be transferred as object identity. Instead, CNCF projects an allowlisted propagation representation from the client context, transports it, verifies/admit it at ingress, and reconstructs a new server-local ExecutionContext.

## Model

Client-local ExecutionContext -> propagation projection -> ExecutionContextEnvelope -> transport -> server ingress verification/admission -> server-local context resolution -> Server ExecutionContext -> ActionCall / Operation.

The server ExecutionContext is newly constructed. Runtime-local capability objects, providers, physical resource handles, secrets, mutable facilities and implementation objects never cross the boundary.

## Propagation vocabulary

Propagation fields are explicitly classified. Candidate families include caller/session/security identity references that are safe across the boundary, locale/timezone/presentation assumptions, observability/correlation information, experiment/arm or other registered execution assumptions, Safety Sandbox Client Sandbox Definition/restrictions, and other CNCF-registered immutable execution assumptions.

Each field family defines projection, wire representation, ingress validation, trust source, merge/narrowing rule, redaction/diagnostic policy and whether forwarding to a next hop is permitted. Absence of a field does not grant authority.

## Authority rule

Propagation can carry identity, assumptions and restrictions. It cannot mint server authority.

For authority-bearing or authority-limiting fields, server policy is authoritative. A propagated client value may select within an admitted server range or narrow it; it cannot widen the server maximum authority.

For Safety Sandbox the Effective Sandbox is the intersection of Server Sandbox Definition, propagated Client Sandbox Definition and Operation Sandbox Definition, and the result is bound to server ExecutionContext SandboxContext.

A forged or over-broad client sandbox value therefore cannot grant filesystem, network, process, datastore or other effect authority that the server does not already permit.

## Client responsibility

CNCF Client functionality constructs the client-local ExecutionContext through normal CNCF resolution. Before transport it invokes the registered propagation projection. Application/component code does not manually serialize context fields.

## Server responsibility

The ingress adapter parses the envelope, validates version/shape/limits, authenticates the transport/request through its normal mechanism, applies field-specific trust/admission rules, combines admitted propagated context with server/runtime and Operation definitions, and only then constructs/binds the Action ExecutionContext.

Transport input is never treated as a ready-made ExecutionContext.

## Transport independence

The propagation contract is logical and transport-neutral. HTTP, MCP or later transports map the same envelope/field semantics to their protocol representation. A transport must not invent private ExecutionContext semantics.

## Multi-hop and async boundaries

Propagation is explicit at every boundary. Forwarding uses a fresh projection from the current admitted ExecutionContext, not blind forwarding of the original envelope.

Async/Job execution likewise constructs a new ExecutionContext from explicitly retained/recoverable propagation data plus current runtime policy. Runtime-local object references are never persisted as propagation state.

## Safety and security

This mechanism supports deterministic execution semantics and Safety Sandbox, and also prevents ambient client state from silently becoming server authority. It is not a credential-forwarding shortcut and does not replace authentication/authorization.

## Non-goals

- Java/Scala object serialization of ExecutionContext;
- automatic propagation of arbitrary request/session attributes;
- propagation of secrets or physical host paths;
- granting authority because a client claims it;
- transport-specific duplicate context models.
