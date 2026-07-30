# Phase 52 P52-COMP-01 - Public Constant Canonicalization Consideration

Status: decided

## Context

Phase 52 found 158 PascalCase public `RuntimeConfig` values and 36 PascalCase
Information Model facade values.  The Scala mandatory naming gate applies to
the public declaration itself, so changing only callers leaves the admitted
source non-conformant.

The work was considered alongside Entity ID exact serialization.  They share a
release boundary because generated applications and runtime consumers must
compile against one unambiguous public framework contract; the Entity ID wire
format itself is unchanged by this constant-name migration.

## Alternatives Considered

1. Keep PascalCase declarations and suppress the naming gate.
   This preserves the immediate source surface but leaves the mandatory rule
   unenforced and creates another exception to carry forward.
2. Add lower-camel declarations while retaining PascalCase aliases.
   This offers source compatibility, but the aliases remain public PascalCase
   declarations and therefore do not satisfy the mandatory gate.  It also
   keeps two names for one contract.
3. Make lower-camel names canonical and migrate the admitted callers.
   This satisfies the gate and gives generated and handwritten consumers one
   stable API spelling.  It is a deliberate breaking source migration.

## Decision

Option 3 was selected on 2026-07-30 JST.  Compatibility aliases and
reflection-based old-name fallbacks are excluded.  The migration preserves the
existing configuration keys, capability strings, state values, defaults, and
runtime behavior; only the Scala public identifiers change.

The exact implementation boundary and completed validation are recorded
separately in
`2026-07-30-phase-52-public-constant-canonicalization.md`.  Keeping this
consideration record independent prevents later work from reopening the alias
proposal without the reasons it was rejected.
