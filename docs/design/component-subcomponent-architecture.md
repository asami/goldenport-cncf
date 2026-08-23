# Component and Subcomponent Architecture

Status: normative design

## Authority and Scope

This document defines the stable architecture for a Component and its declared
Subcomponents. The normative behavioral contract is the
[Component and Subcomponent Architecture Specification](../spec/component-subcomponent-architecture.md).
That specification is the authority for behavioral rules and their executable
evidence.

## Component and CAR Boundary

The architecture has distinct parent Component, declared Subcomponent, CAR,
and payload-role boundaries. A parent remains a Component rather than a
container artifact. The specification defines the identity, CAR, payload-role,
and technology consequences of those boundaries in R1 through R5.

## Composition Boundary

Parent membership is the structural composition boundary. A Subsystem is the
separate executable composition boundary. The specification defines their
non-interchangeable relation and the limits of membership authority in R3 and
R6.

## Lifecycle and Deployment Boundary

Identity, publication, discovery, activation, operation, MCP access,
disclosure, and deployment are separate architectural concerns. The
specification defines their required separation, including the
external-platform deployment boundary, in R3, R5, R8, and R9.

## Identity and Provenance Boundary

Logical Component release and physical artifact evidence are different forms
of identity. Artifact digest, path, source, and provenance belong to the
physical evidence boundary. The specification defines the required identity,
provenance, authorization, integrity, and availability behavior in R7, R10,
and R11.

## Responsibility Boundary

This architecture does not select registry schemas, APIs, type names, wire
formats, archive layout, resolver representations, lifecycle mechanisms, or
consumer implementation details. Those implementation choices must preserve
the specification's contract.
