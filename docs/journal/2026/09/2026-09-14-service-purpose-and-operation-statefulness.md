# Service Purpose and Operation Statefulness Discussion

Date: 2026-09-14
Type: design discussion and implementation-planning handoff
Owner: CNCF

## Context

The SimpleModeling.org application-modeling review clarified the application
model as a thin orchestrator. Its role is explained before the main structural
example of one application combining multiple domains. Domain models own
important data operations, business rules, invariants, and business state.
Shared domains can also serve multiple applications.

Cloud-function implementation is an important realization objective. The
abstract model expresses properties that enable it while leaving deployment
choice to implementation. Cloud functions are not placed at the foundation of
the abstract model.

## Discussion Sequence

1. An execution-contract distinction initially used function/procedure names.
   Function raised ambiguity with pure functions; task raised ambiguity with
   other task concepts. Neither name was selected.
2. Accessor/mutator/orchestrator was considered. Accessor/mutator describes
   effects, while orchestrator describes composition responsibility, so they
   do not form one mutually exclusive classification.
3. An orchestrator label alone provided no concrete checking or generation
   rule. Composition remains represented through the participating operations
   and their ordering, branching, results, events, and failure behavior.
4. Accessor/mutator also overlaps existing query/command. The selected addition
   therefore narrows to statefulness rather than another effect classification.
5. The decisive state distinction is dependence on authoritative memory-resident
   data retained between invocations. Durable domain updates and disposable
   caches are separate from that dependency.
6. Service purpose supplies the statefulness default. Application defaults to
   stateless; domain defaults to stateful. Omitted service purpose means domain.
   Explicit service statefulness overrides that default; an operation inherits
   the effective service setting and may override it explicitly.
7. The user requested CNCF implementation planning with notes, journal, and
   phase documents. Phase 75 is the next available phase after the existing
   Phase 74 plan.
8. The user extended the scope with component-level purpose defaults:
   application, domain, and both. Single-purpose components supply the default
   service purpose; explicit service declarations take precedence. Both
   describes a mixed component. The planning compatibility proposal keeps
   domain for an unspecified service under both, with explicit service purposes
   recommended; SP75-01 freezes that omission policy.

## Recorded Outcome

- Component purpose: domain/application/both, default domain.
- Service purpose: domain/application, explicit value or component default;
  domain remains the fallback when no unique component default is available.
- Service statefulness: stateless/stateful, default derived from purpose.
- Operation statefulness: explicit override or effective service inheritance.
- Query/command remains the existing independent effect distinction.
- No new accessor/mutator, function/procedure, task, or bare orchestrator
  attribute is selected by this discussion.
- Stateless is a cloud-function candidate property, not a complete deployment
  guarantee. Target execution constraints are checked during realization.

## Evidence and Handoff

The inspected CNCF Service delegates to shared protocol definitions, and CNCF
already has CmlOperationDefinition as a generated operation metadata input.
The shared ServiceDefinition.Specification has a ServiceMetadata extension
point. Phase 75 starts with producer/consumer inventory and owner-contract
promotion, then implements resolution and reviewable execution-contract use.

This entry is chronological evidence. The note is a non-normative proposal;
accepted design/spec documents and executable specifications become the
behavior authority during implementation.

- [Design note](../../../notes/service-purpose-and-operation-statefulness-design-note.md)
- [Phase 75](../../../phase/phase-75.md)
- [Phase 75 checklist](../../../phase/phase-75-checklist.md)
