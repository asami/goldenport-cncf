# Component/Service Purpose and Operation Statefulness - Design Note

Date: 2026-09-14
Status: non-normative implementation proposal
Owner: CNCF
Implementation ledger: [Phase 75](../phase/phase-75.md)
History: [Design discussion](../journal/2026/09/2026-09-14-service-purpose-and-operation-statefulness.md)

## Objective

Represent service responsibility separately from dependence on resident memory.
Application services normally coordinate domain operations as thin
orchestrators. Domain services manage domain data, business rules, invariants,
and business state. An application can combine multiple domains, and multiple
applications can share a domain.

The abstract model prioritizes execution without dependence on memory retained
between invocations. Cloud-function deployment is an important implementation
option enabled by that property and the target's remaining execution constraints.
It is not the abstract modeling primitive or a mandatory deployment choice.

This note captures the selected planning direction. Phase 75 promotes the
accepted owner contracts into design/spec documents paired with executable
specifications before claiming implementation acceptance.

## Model Attributes

| Owner | Attribute | Values | Omission behavior |
| --- | --- | --- | --- |
| Component | purpose | domain / application / both | domain |
| Service | purpose | domain / application | Inherited from a single-purpose component; otherwise domain |
| Service | statefulness | stateless / stateful | Derived from effective purpose |
| Operation | statefulness | stateless / stateful | Inherited from effective service statefulness |

The attribute labels are working semantic names, not a claim that the current
CML parser accepts new syntax. Phase 75 inventory freezes the exact Scala,
CML, metadata, and serialized representations at their owning boundaries.
Canonical spelling is stateful; statefull is not a second semantic value.

## Statefulness Meaning

Stateful means that correctness depends on authoritative memory-resident data
retained across invocations by a continuing execution instance. Stateless means
that correctness does not require that retention.

- Invocation-local working memory is compatible with stateless execution.
- A disposable cache is compatible with stateless execution when losing it
  does not change the required result or behavior.
- Reading or updating durable domain data through a domain service is compatible
  with stateless execution. Query/command and statefulness are independent axes.
- A stateful declaration describes a requirement; it does not by itself supply
  persistence, restart recovery, instance affinity, or concurrency guarantees.

Application/domain describes responsibility, not proof of a particular
implementation's memory behavior. Either purpose permits an explicit override.

## Default and Override Resolution

1. Resolve component purpose from its explicit declaration, otherwise domain.
2. Resolve service purpose from its explicit declaration; otherwise inherit
   application/domain from a single-purpose component. For a both component,
   the planning fallback is domain, preserving the existing omission default.
3. Resolve service statefulness from its explicit declaration; otherwise use
   application -> stateless or domain -> stateful.
4. Resolve operation statefulness from its explicit declaration; otherwise
   inherit the effective service value.

Both means that the component contains application and domain services. It is
not a third effective service purpose and does not imply a third statefulness.
Mixed components should identify each service's purpose explicitly. The domain
fallback is a compatibility proposal to freeze in SP75-01, rather than inferring
service purpose from its name, query/command kind, or code body.
An explicit service purpose overrides a component default, including the other
single-purpose value. Component purpose supplies defaults, not an exclusive
membership restriction. No component-level statefulness attribute is added.

| Component purpose | Service purpose | Effective service purpose | Default service statefulness |
| --- | --- | --- | --- |
| omitted | omitted | domain | stateful |
| domain | omitted | domain | stateful |
| application | omitted | application | stateless |
| both | application | application | stateless |
| both | domain | domain | stateful |
| both | omitted | domain (compatibility proposal) | stateful |
| application | domain | domain | stateful |
| domain | application | application | stateless |

| Effective service purpose | Service statefulness | Operation statefulness | Effective operation value |
| --- | --- | --- | --- |
| domain (all defaults) | omitted | omitted | stateful |
| domain | omitted | omitted | stateful |
| application | omitted | omitted | stateless |
| application | stateful | omitted | stateful |
| domain | stateless | omitted | stateless |
| application | omitted | stateful | stateful |
| domain | stateful | stateless | stateless |
| application | stateful | stateless | stateless |

Keep the explicit declaration and its resolved value distinguishable so that
serialization and review preserve intentional overrides and default provenance.
An unrecognized explicit value is a structured definition error, not omission.

## Implementation Boundary

Verified local integration inputs:

- CNCF Service extends the shared protocol Service and obtains its definition
  through that boundary: src/main/scala/org/goldenport/cncf/service/Service.scala.
- CNCF generated operation metadata is carried by
  src/main/scala/org/goldenport/cncf/operation/CmlOperationDefinition.scala.
  Its existing kind and command execution policy remain separate concepts.
- The current shared ServiceDefinition.Specification in simplemodeling-lib
  carries ServiceMetadata. Inventory that extension point before choosing a
  representation: org/goldenport/protocol/spec/ServiceDefinition.scala.

CNCF owns effective resolution, runtime consumption, and the producer/consumer
handoff. Shared protocol attributes belong to their existing upstream owner;
upstream types must not depend on CNCF. Freeze any required upstream changes
before implementation. This planning change edits CNCF documentation only.

Implement one authoritative resolver and preserve its effective value through
definition construction, generated metadata, runtime lookup, and review-facing
inspection. Executable specifications cover default resolution, every override,
invalid values, source/builder compatibility, and declared/effective round trips.

Existing declarations with component and service purposes and statefulness
omitted resolve to domain/stateful.
Preserve existing invocation, routing, query/command, authorization, and storage
behavior. Inventory mixed services and shared ComponentLogic instances before
using operation overrides to influence an instance lifecycle.

## Execution and Deployment Use

A stateless operation is a cloud-function deployment candidate. Applicability
also depends on invocation completion, time/resource limits, supported
dependencies, and the target adapter contract. Stateful operations require an
implementation that supplies their resident-state requirement.

Metadata is a declaration, not automatic proof about arbitrary Scala code.
Phase 75 defines checkable framework-owned state requirements and exposes the
effective contract to validation/inspection. An explicitly stateless operation
with a known required resident-state dependency receives a structured mismatch
instead of a claim of cloud-function eligibility. Unknown behavior is reported
as unverified. No automatic deployment or universal static proof is implied.

## Deliberately Separate Concerns

- Retain existing query/command; adding accessor/mutator would duplicate it.
- Express orchestration through operation composition, ordering, branching,
  result/event transfer, and failure handling. No unqualified orchestrator flag
  is introduced in this plan.
- Function/procedure and task-like names are not selected statefulness types.
- Session/continuous execution and time/resource requirements remain separate
  execution concerns; they are not new required enums in this phase.
- Cloud adapters, production deployment, and broad service migrations remain
  outside this metadata/resolution/validation boundary.

## Downstream Coordination

Simple-modeler and Cozy consume the accepted CML/metadata contract through a
bounded generator handoff when inventory identifies affected edges.
Textus CBD Support can use declared/effective attributes for review and
execution-profile checks. SimpleModeling.org can explain the accepted model
without making cloud functions its abstract primitive. This note does not
claim those consumers have already implemented or adopted the change.
