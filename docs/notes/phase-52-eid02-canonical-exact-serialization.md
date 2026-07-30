# Phase 52 EID-02: Canonical Exact Serialization

status=DONE
phase=[Phase 52](../phase/phase-52.md)
checklist=[EID-02](../phase/phase-52-checklist.md)

## Attributable Design Decision

On 2026-07-29, the maintainer confirmed that `EntityId` is a
`UniversalId` and that its canonical format is already defined by
`UniversalId`. EID-02 therefore MUST retain the canonical UniversalId outer
grammar; it MUST NOT introduce a parallel outer wire format such as `eid1`.

The applicable outer canonical form is:

```text
<major>-<minor>-<kind>-<subkind>-<timestamp>-<entropy>
```

For `EntityId`, `kind` remains `entity`. EID-02 will define a versioned,
lossless, label-grammar-safe encoding of the complete `EntityCollectionId`
inside the existing `subkind` field. It will use the normal `UniversalId`
parser for the outer form and decode only that `subkind` payload afterwards.

This decision supersedes the earlier proposed independent `eid1` outer format.
It does not authorize changing the `UniversalId` canonical grammar or adding
`simplemodeling-lib` to the Phase 52 mutation scope.

## Consequences

- `EntityId` remains a normal `UniversalId`; its `value`, parsing, and
  transport representation use the existing UniversalId grammar.
- EID-02 must preserve every exact collection component in the `subkind`
  payload without delimiter guessing.
- Existing UniversalId label validation remains authoritative. EID-02 must
  reject malformed or unsupported EntityId subkind payloads without fallback
  to the prior name-only scalar representation.

## Frozen `subkind` Payload Grammar

The versioned payload is an internal `subkind` value, not a second EntityId
format:

```text
ec1_<majorLength>_<major>_<minorLength>_<minor>_<nameLength>_<name>
```

`ec1` identifies the EntityCollectionId payload grammar. Each length is a
positive, canonical base-10 integer with no leading zero and counts ASCII
characters. This is byte-equivalent because all UniversalId labels already
use `[A-Za-z][A-Za-z0-9_]*`. The parser consumes each declared component length
and then the entire payload, so underscores in labels cannot create a boundary
ambiguity.

For example:

```text
EntityCollectionId("textus", "artscene", "facility")
  -> textus-artscene-entity_collection-ec1_6_textus_8_artscene_8_facility-0-stable

EntityId("single", "global", collection, EPOCH, "stable")
  -> single-global-entity-ec1_6_textus_8_artscene_8_facility-0-stable
```

The outer `major` and `minor` of an `EntityCollectionId` must agree with the
decoded payload. EntityId outer `major` and `minor` remain its independent
operational fields. Unknown payload versions, old name-only subkinds, invalid
or noncanonical lengths, overflow, truncation, invalid labels, namespace
mismatch, and trailing payload data fail deterministically. No fallback,
rebinding, or migration reader is permitted.

The public Circe codecs for `EntityCollectionId` and `EntityId` carry these
canonical Strings, not a parallel object representation. EntityId materializes
its default timestamp and entropy fields at construction, so `copy`, JSON, and
canonical String parsing retain the same identity. The public `Option` shape is
retained for source compatibility only: an explicit `None` is not an ID state
and is rejected. Structured `Record` decoding is parsing, not generation: it
requires a valid timestamp and entropy and never substitutes current values.
The canonical timestamp domain is a nonnegative `epochMilli` that converts
back to the identical `Instant`; pre-epoch and sub-millisecond values are
rejected because the established UniversalId text grammar cannot round-trip
them. Default ID generation normalizes the current instant to this millisecond
domain before rendering.
Explicit entropy is a nonempty alphanumeric-or-underscore token; the outer
UniversalId delimiter is not admitted in entropy.

## Implementation Evidence

The implementation preserves the UniversalId outer parser and encodes the
complete collection only in its `subkind` payload. `EntityCollectionId.parse`
also rejects a payload whose decoded namespace differs from its outer
UniversalId namespace. `EntityId.parse` decodes the same payload and no longer
creates a collection from the Entity-local `major` or `minor`.

Focused validation during IMPLEMENT:

- `simplemodeling-model`: `Test / compile` passed; `EntityIdSpec` passed
  13/13 with no pending scope; development `publishLocal` published
  `0.2.1-SNAPSHOT` only to local Ivy.
- CNCF: `Test / compile` passed; `EntityIdParseSpec` passed 3/3 with no
  pending scope against that local snapshot.
- `git diff --check` passed in all four frozen admitted repositories.

Review-fix evidence adds explicit rejection for incomplete or malformed
structured `Record` outer fields, null parsing, explicit `None`,
outer-delimiter entropy, leading-zero/nondecimal/missing-separator/invalid-label
payloads, and actual canonical-string round trips for Circe, URI/query, form,
CLI command, logging formatter, and datastore-key boundaries.

Independent REVIEW and RE_REVIEW accepted the bounded delta. EID-02 is DONE.
