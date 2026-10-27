# Operation parameter resolution development candidate

Date: 2026-10-27 (Asia/Tokyo)

## Development Candidate

<!-- cncf-phase-finding:DEV-OPERATION-PARAMETER-SOURCE-POLICY-001:begin -->

### DEV-OPERATION-PARAMETER-SOURCE-POLICY-001

- Kind: development-candidate; separate from Hygiene.
- Status: OPEN; implementation and scheduling pending.
- Owner: CNCF Operation parameter resolution, coordinating with goldenport core
  for core-owned protocol abstractions and sm-workflow for consumer adoption.
- Source: explicit user direction in the sm-workflow-dev side conversation:
  align parameter contracts so a parameter can later become property-backed or
  request-only, then retain this work as a deferred feature extension alongside
  Hygiene information.
- Proposed work: establish one typed parameter acquisition contract with a
  declared source policy and shared input-name resolution. A later source-policy
  change must not require changing the consumer's acquisition method.
- Phase / task / commit: not assigned. This record does not expand an active
  Phase or authorize implementation, publication or Session operations.

#### Observed gap

The retained sm-workflow invocation using `--session-id` was rejected with
`Workflow requires parameter sessionId`. The consumer's
`NativeWorkflowOperationInput._parameter_c` reads
`request.toRecord.getAny(name)` directly. This identifies a consumer path that
needs qualification; it does not establish that every CNCF input path is broken.

The existing [naming-policy note](../04/naming-policy-design-note.md) describes
camelCase, snake_case and kebab-case as equivalent input spellings, including
command arguments. The existing
[PropertyValueResolver](../../../../src/main/scala/org/goldenport/cncf/naming/PropertyValueResolver.scala)
resolves these aliases for Map, Record and JSON values. Its Option result does
not distinguish absence from conflicting aliases. Existing
[ActionCall property helpers](../../../../src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala)
and
[ResolvedParameters](../../../../src/main/scala/org/goldenport/cncf/config/ResolvedParameter.scala)
also require assessment before claiming a complete normalized acquisition route.

#### Agreed contract direction for the future specification

The following requirements are retained for specification and implementation;
they are not claims of implemented behavior or new executable API names.

1. A declared parameter has one canonical logical name, type, requiredness,
   default and validation contract. Consumers use the same typed acquisition
   route regardless of the permitted value sources.
2. Property-backed parameters use the existing CNCF property/configuration
   resolution route, its applicable precedence and target scope. Request values
   participate only as permitted by that route and declaration. Do not introduce
   a second merge order or expose raw configuration sources to domain code.
3. Request-only parameters read only the current Operation Request. HOME,
   project, environment and other configuration layers cannot supply a missing
   value. This restriction is declared, rather than hard-coded by a name such
   as `sessionId`. Here request-only includes named CLI options and admitted
   HTTP/structured Request input; it does not mean positional CLI arguments only.
4. Both source policies apply the same naming rules and typed decoding:
   `sessionId`, `session_id` and `session-id` denote the same logical parameter.
   Source selection and name normalization are separate concerns.
5. Absence, an invalid value and conflicting aliases remain distinguishable
   structured outcomes. Conflicting values in the same source/collision domain
   must not be silently selected or treated as absence that permits fallback.
   Legitimate overrides between admitted configuration layers retain the
   existing resolution rules.
6. Switching a parameter between property-backed and request-only changes its
   declaration/source policy, not its acquisition call or business meaning.
   Retain its logical name, type, normalization, requiredness, default and
   validation semantics unless a separate change explicitly adopts new ones.
   A request-only default, when declared, comes from the parameter declaration,
   never from a forbidden configuration source.
7. A source-policy change is an explicit contract change: publish it through
   normal declaration/version compatibility handling. It must not silently
   reinterpret already captured workflow input, alter an existing Session,
   manufacture an execution target or grant authentication/authorization.
   Business and authentication session identifiers retain their separate
   [existing ingress ownership](../../../../docs/spec/fixed-profile-service-ingress.md).

#### Acceptance conditions for a later selected task

- The specification and owning parameter declaration express permitted sources;
  the same consumer accessor works before and after each policy switch.
- Executable specifications exercise camel/snake/kebab spellings, typed values,
  required and optional absence, declared defaults, invalid values and
  contradictory aliases. Request-only cases prove that populated HOME/project/
  environment configuration cannot supply the value.
- Property-backed cases prove the existing resolution precedence, target scope
  and provenance, including legitimate overrides. A successful Request-only case
  must not depend on enabling configuration fallback.
- An isolated public CLI case using `--session-id` reaches the declared business
  input, alongside equivalent camel/snake cases. Include structured Request
  coverage and preserve authentication-session separation.
- Consumer migration reuses existing core/CNCF abstractions where applicable;
  no per-operation alias lists or parallel parameter-resolution framework.
- Qualification preserves existing Sessions, accepted results, history and repair
  counts, with no re-Start, accepted Submit replay or successful-effect replay.

Resume when this candidate is selected into an admitted task/Phase and the
core/CNCF declaration and compatibility boundary are settled. No SBT, installed
CLI execution or implementation qualification was performed for this record.

<!-- cncf-phase-finding:DEV-OPERATION-PARAMETER-SOURCE-POLICY-001:end -->
