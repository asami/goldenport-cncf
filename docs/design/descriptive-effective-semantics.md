# Descriptive Effective Semantics

status=design
published_at=2026-06-23

---

# Overview

CNCF uses the shared SmartDox/Goldenport descriptive metadata vocabulary for
component, service, operation, entity, and generated help metadata. SmartDox is
the semantic reference implementation; goldenport-scala-library provides the
shared `DescriptiveAttributes` value model for non-Dox consumers.

Common fields:

- `headline`
- `brief`
- `summary`
- `description`
- `lead`
- `abstract`
- `remarks`
- `tooltip`

# Effective Rules

| Effective accessor | Precedence |
| --- | --- |
| `effectiveHeadline` | `headline -> brief -> tooltip` |
| `effectiveBrief` | `brief -> summary -> lead -> abstract -> headline` |
| `effectiveSummary` | `summary -> lead -> abstract -> description -> brief` |
| `effectiveDescription` | `description -> abstract -> lead -> summary` |
| `effectiveTooltip` | `tooltip -> brief -> headline -> summary -> abstract` |

CNCF projections should preserve authored `summary` and `description` fields and
use effective values only when a consumer needs a fallback display text.
Generated metadata must not collapse all descriptive fields into one
`description`.

# Product Boundary

- SmartDox: source parsing and Dox document metadata normalization.
- goldenport-scala-library: shared `DescriptiveAttributes` and effective
  accessor behavior.
- CNCF: runtime/help/projection consumer.
- Cozy: BoK/publication/RDF/dashboard consumer.
