canonical-alias-suffix-resolution.md

status = draft
owner = Phase 2.8
scope = design / specification (mutable)

======================================================================
Canonical / Alias / Suffix Resolution Rules
======================================================================

Purpose
----------------------------------------------------------------------
This document defines the canonical resolution rules for
component / service / operation names across CNCF execution surfaces
(CLI, Script, HTTP).

Goals:
- Reduce parser complexity
- Make ambiguity explicit and controllable
- Provide predictable, explainable resolution behavior
- Unify CLI / Script / HTTP semantics

This is a DESIGN document.
The rules defined here are mutable during Phase 2.8 and MUST be
reflected in implementation changes immediately.

> NOTE: The Phase 2.8 alias behavior described below now has an
> implementation-aligned reference in `docs/design/path-alias.md`; this
> document serves as the historical and design-intent background.
>
> Any “deferred” wording elsewhere in this document refers to the
> pre–Phase 2.8 state—the alias handling described here is now
> implemented and captured in `path-alias.md`.


Terminology
----------------------------------------------------------------------
- Component name
- Service name
- Operation name
- FQN (Fully Qualified Name): component.service.operation
- Alias: abbreviated or partial name resolved by matching rules
- Canonical name: unique, fully resolved FQN
- Suffix: representation selector (e.g. .json, .yaml) — defined later


Basic Input Classification
----------------------------------------------------------------------
The first non-option argument is interpreted as the operation selector.

The selector is classified by the number of dots ("."), NOT by position.

- No dot        : "xxx"
- One dot       : "yyy.xxx"
- Two dots      : "zzz.yyy.xxx"
- Three or more : INVALID (error)


Resolution Model
----------------------------------------------------------------------
At startup, the runtime builds a resolution table containing all valid
combinations of:

- component
- service
- operation

This table is the ONLY source used for resolution.
No dynamic lookup or fallback is allowed.


Case 1: No dot ("xxx")
----------------------------------------------------------------------
Interpretation:
- Operation name only

Resolution steps:
1. Search all operations across all components/services
2. Find operations whose name matches or aliases "xxx"

Outcomes:
- Exactly one match
    -> Use it
- No match
    -> Error: operation not found
- Multiple matches
    -> Error: ambiguous operation
       -> List matching operations as FQN

Optional prefix matching:
- If enabled:
    - Partial match allowed only when exactly one candidate exists
    - Otherwise treated as ambiguous


Case 2: One dot ("yyy.xxx")
----------------------------------------------------------------------
Interpretation:
- service + operation

Resolution steps:
1. Resolve service name using same rules as Case 1
2. Resolve operation name within matched services

Outcomes:
- Exactly one (service, operation) pair
    -> Use it
- No valid pair
    -> Error: not found
- Multiple valid pairs
    -> Error: ambiguous
       -> List matching operations as FQN


Case 3: Two dots ("zzz.yyy.xxx")
----------------------------------------------------------------------
Interpretation:
- component + service + operation

Resolution steps:
1. Resolve component name
2. Resolve service name within component
3. Resolve operation name within service

Outcomes:
- Exactly one FQN
    -> Use it
- No match
    -> Error: not found
- Multiple matches
    -> Error: ambiguous (should be rare)


Default / Implicit Resolution Rules
----------------------------------------------------------------------
Special rule for minimal systems:

If ALL of the following are true:
- Exactly one non-builtin component exists
- That component has exactly one service
- That service has exactly one operation

Then:
- Operation selector MAY be omitted
- The sole operation is used implicitly

Additional rule:
- If the selector contains a dot, implicit resolution is DISABLED
- Dot presence always means explicit resolution intent


Alias Handling
----------------------------------------------------------------------
- Alias resolution is applied uniformly to:
    - component
    - service
    - operation
- Aliases MUST be declared explicitly
- Alias matching follows the same ambiguity rules as canonical names


Error Reporting
----------------------------------------------------------------------
Errors MUST be explicit and informative.

For ambiguity:
- List all candidate operations using FQN
- Do NOT auto-select arbitrarily

For not found:
- Indicate which resolution stage failed
  (component / service / operation)


Suffix Handling (Deferred)
----------------------------------------------------------------------
Suffixes such as:
- .json
- .yaml
- .txt

are NOT handled in this document.

Rule:
- Suffix parsing and representation selection are deferred to
  Phase 2.8 (separate specification)

This document defines NAME resolution ONLY.


Non-goals
----------------------------------------------------------------------
- No fuzzy matching beyond explicit prefix rules
- No interactive disambiguation
- No runtime mutation of resolution table


Relation to Phases
----------------------------------------------------------------------
- Phase 2.6:
    - Demo-level behavior accepted
    - No canonical guarantee
- Phase 2.8:
    - This document is authoritative
    - Implementation MUST follow this spec
- Phase 2.9+:
    - Error taxonomy refinement may extend error semantics


Status
----------------------------------------------------------------------
- Phase: 2.8
- State: ACTIVE DESIGN (mutable)
- Freeze: End of Phase 2.8

======================================================================
End of document
======================================================================
