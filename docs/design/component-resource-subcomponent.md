# Component Resource Subcomponent

Status: normative design

## Authority and Scope

This design defines the resource-composition responsibilities for a parent
Component and its declared resource Subcomponents. The normative behavioral
contract is the [Component Resource Subcomponent
Specification](../spec/component-resource-subcomponent.md).

The [Component and Subcomponent Architecture](component-subcomponent-architecture.md)
and its [Specification](../spec/component-subcomponent-architecture.md) remain
the shared Component identity, membership, and authority contract. This design
does not create a second identity model, resolver, or public contract.

## Composition Boundary

A resource Subcomponent is an independently identified declared Component in a
parent release's composition membership. Its role identifies the information
payload it carries; it does not replace the child Component's identity, CAR,
or authority boundary. Required membership belongs to release completeness,
not to runtime activation.

## Resolution and Provenance Boundary

Resource resolution is a runtime-owned composition responsibility. It retains
the logical Component/release identity and the physical artifact, source, and
provenance evidence as distinct information. This boundary neither introduces
another resolver nor turns a resource lookup into activation, operation, MCP
access, disclosure, or deployment authority.

## Content Protection Boundary

Resource content is exposed only through the authorization, integrity, and
path-safety boundary. A composition membership, role, inventory entry, or
availability result does not grant content authority. Restricted content is
represented without disclosing its protected material.

## Runtime Policy Boundary

Develop, Test, Demo, and Production resource composition policy belongs to the
runtime boundary. Policy selects permitted deterministic resource treatment
without entering Component-domain APIs or changing the identity and authority
contract.

## Lifecycle Boundary

Verified resource artifacts are immutable release evidence. Sharing, cache
ownership, resolution flights, refresh, invalidation, unload, shutdown, and
waiter outcomes remain lifecycle responsibilities with truthful concurrent
outcomes and bounded, non-sensitive observability.

## Consumer Boundary

Help and Admin consume the same read-only resource projection. They receive
identity, role, availability, integrity, and provenance through that
projection; they do not obtain authority or reconstruct it by scanning CAR,
Subcomponent, cache, repository, or development paths.
