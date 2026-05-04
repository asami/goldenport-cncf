# ID Design — Canonical, Self-Describing Identifiers

status: decided

Status (as of 2025-12-31):
- CanonicalId is provided by goldenport core
- CNCF treats CanonicalId as opaque and uses it for correlation only
- CNCF does not generate CanonicalId

This document defines the canonical ID design for this system.
It records an architectural decision that has already been reached
through prior discussions and exists to prevent accidental reintroduction
of known anti-patterns.

---

## Core Principle

**IDs are opaque to program logic, but self-describing to humans and observability tools.**

UniversalId labels (`major`, `minor`, `kind`, `subkind`) use the restricted grammar `[A-Za-z][A-Za-z0-9_]*`.
This restriction exists for transport safety: these identifiers are expected to pass through HTTP and to
appear in URLs in their canonical form without additional escaping. Labels must begin with an ASCII letter. Hyphen is reserved as the structural
separator of the canonical UniversalId format and therefore MUST NOT appear inside labels. Parsers must
reject out-of-grammar inputs rather than infer alternative label boundaries.

IDs must support:
- observability
- debugging
- log correlation
- AI-assisted inspection

Observability identifiers that reach CLI adapters are rendered through the Presentable stdout/stderr contract locked in `docs/notes/phase-2.8-infrastructure-hygiene.md#purpose-aware-string-rendering-candidate` (see A-3 for the Phase 2.8 policy).

while remaining **non-semantic for program logic**.

---

## EntityId Runtime Namespace

`EntityId.major` and `EntityId.minor` are operational partition labels. They
are not system, subsystem, or component layer names.

The canonical interpretation is:

- `major`: the largest operating scope, such as customer, tenant,
  organization, or the default single operating scope.
- `minor`: the operating segment inside `major`, such as region, district,
  site, or the default global segment.

The standard CNCF runtime namespace is:

```text
major = single
minor = global
```

This `single/global` pair is valid for production systems that intentionally
run as one operating partition. Deployments that separate customers, tenants,
districts, regions, or sites must override the namespace in runtime
configuration.

The primary configuration keys are:

```hocon
textus.id.namespace.major = single
textus.id.namespace.minor = global
```

Runtime namespace resolution uses this precedence:

1. runtime configuration
2. SAR descriptor default
3. CAR deemed-subsystem default
4. CNCF default `single/global`

`public/global` may be used explicitly for public shared content, but it is not
the CNCF default because `public` describes publication semantics rather than a
neutral operating partition.

The old `sys/sys` namespace is not part of new design. Existing implementation
or fixture uses are compatibility debt and should be replaced by
`single/global` or by an explicit runtime namespace.

System, subsystem, and component identity belongs in descriptors, collection
namespace, runtime metadata, and observability context. Application logic must
not parse `EntityId.major` or `EntityId.minor` to recover component structure.

---

## Anti-Patterns: Semantic ID / Smart ID

### Semantic ID (informal term)

A **Semantic ID** is an identifier whose internal structure is:

- interpreted as structured information, and
- used by application logic for branching or decision-making.

In other words:

> The contents of the ID are treated as data by the program.

This is not a formally standardized term.
It is used informally among architects as shorthand for a recurring design failure.

---

### Smart ID (colloquial, intentionally negative)

**Smart ID** is a more explicit and intentionally negative term
used to describe the same anti-pattern.

A Smart ID is an ID that is:
- “clever”
- information-rich
- and therefore tempting to parse in code

Typical examples:

```scala
if (id.startsWith("sie-query-exec")) { ... }
