# Phase 111: Sensitive Information Runtime Architecture

status=planned
planned_at=2026-10-16
depends_on=[Phase 107]
related=[Sensitive Information Architecture](../notes/sensitive-information-architecture.md)
consumers=[Cozy Phase 85, textus-flutter-core Phase 2, textus-flutter-application-framework Phase 3]

## Goal

Turn the initial Sensitive Information Architecture proposal into a minimal CNCF
runtime contract without prematurely creating a large security type hierarchy.

## Planned work

1. Refine the minimum classification vocabulary and decide logical type versus
   metadata/annotation representation.
2. Define Textus SubjectId / ExternalIdentity mapping boundary and what identity
   facts enter SecuritySubject/ExecutionContext.
3. Define runtime handling contracts for Credential, Secret and Sensitive
   Personal Data across persistence, serialization, logging/OTLP and Display.
4. Define External Sensitive Reference / Assertion / Result semantics sufficient
   for provider-delegated interactions.
5. Define the server-side half of Sensitive Interaction delegation and its
   relationship to Operation authorization.
6. Define explicit escape handling for unavoidable direct sensitive-value
   capture without making it the ordinary path.
7. Produce executable examples for identity mapping and at least one delegated
   sensitive interaction.

## Initial implementation direction

Prefer small semantic contracts and provider interfaces over a universal
security wrapper object. Ordinary application/domain code should normally see
Textus SubjectId and provider references/results, not provider credentials or
excluded secret values.

## Acceptance planning

The Phase should demonstrate that a provider identity can authenticate/map to a
stable Textus SubjectId without propagating the provider identifier through an
ordinary domain Entity, and that a delegated sensitive interaction can return a
domain-safe reference/result without the excluded value entering the ordinary
CNCF domain model/log/Display path.

Detailed implementation is intentionally refined during this Phase.
