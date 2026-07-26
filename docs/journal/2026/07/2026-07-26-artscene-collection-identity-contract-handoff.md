# ArtScene Collection Identity Contract Handoff

date = 2026-07-26
status = resolved-current-release
source = textus-art-scene validation
related_phase = CNCF Phase 50

## Purpose

This handoff records and resolves a collection identity mismatch exposed when Textus
ArtScene runs its generated Entity persistence against the current CNCF
`0.5.1-SNAPSHOT` runtime.

This is not an ArtScene optimistic-concurrency-control (OCC) opt-in issue.
ArtScene does not request an observed revision for its ordinary Entity
operations. The failure occurs before an Entity can be created, read, or
updated because a stored scalar `EntityId` does not retain the complete
collection namespace.

## Observed Failure

The ArtScene test suite fails broadly at the first Entity operation, including
`RegisterFacility`, exhibition reads, and timeline projection.

Representative runtime diagnostic:

```text
Entity codec produced collection
'single-global-entity_collection-facility-0-stable'
for requested collection
'major-minor-entity_collection-facility-0-stable'
```

The same mismatch occurs for `exhibition`:

```text
single-global-entity_collection-exhibition-0-stable
major-minor-entity_collection-exhibition-0-stable
```

Reproduction in ArtScene:

```bash
cd /Users/asami/src/dev2026/textus-art-scene
sbt --batch test
```

The failures are visible in, for example:

- `ArtSceneNactFetchProviderSpec` while registering a Facility;
- `ArtSceneTimelineViewSpec` while reading an Exhibition; and
- any operation that reaches generated Entity persistence.

## Corrected Diagnosis

The generated Entity codec and the CNCF datastore request both declare the
canonical `major-minor` collection identity. The `single-global` value appears
after a scalar `EntityId` round-trip:

- runtime ID generation intentionally uses the configured operational
  namespace, here `single/global`;
- the scalar ID retains that runtime namespace and the logical collection name;
- decoding the scalar without external collection context therefore cannot
  recover the original `major/minor` collection namespace; and
- the decoded Entity reaches persistence validation with a reconstructed
  `single-global` collection.

The mismatch is therefore a CNCF persistence-boundary bug, not a generated
codec declaration bug. It should not be repaired in ArtScene by:

- replacing generated persistence with raw `Record` saves;
- parsing or rewriting canonical ID strings in application code;
- configuring ArtScene to use a different collection form; or
- introducing an application OCC/revision parameter.

The preceding managed-revision handoff remains relevant for its own API
boundary, but it is independent of this collection mismatch.

## Current-Release CNCF Contract

The requested Entity collection is the storage owner at the aggregate create
boundary:

```text
CML/Cozy generated EntityPersistent codec
  -> EntityStore UnitOfWork operation
  -> CNCF collection resolver
  -> datastore repository
```

- aggregate create accepts both typed and scalar `EntityId` values and replaces
  the model placeholder collection with the selected runtime collection before
  persistence;
- `EntityPersistent.fromStoreRecord` is invoked exactly once;
- CNCF validates the decoded logical collection name and rejects a different
  name as a structured failure; and
- CNCF does not add runtime type inference, a raw-Record exception policy, or a
  second decode with a rewritten Record.

The current release assumes that there is no second collection with the same
logical name. This is an explicit closure constraint, not the final Collection
Identity model.

## Current Release Closure Boundary

The current release closes only after ArtScene compiles and its collection
identity focused specifications pass through the canonical aggregate create
boundary. It does not broaden this correction into a complete redesign of
every load path or custom persistence adapter.

Phase 51 owns the formal follow-up for the complete Collection Identity model,
including compatibility policy for older generated artifacts, generic custom
codec guidance, and removal or narrowing of runtime compatibility adapters.

## Regression Coverage

Focused CNCF executable coverage fixes the scalar round-trip contract for more
than one logical collection:

1. Decode each physical Record once.
2. Accept runtime namespace differences when the logical collection name is
   equal.
3. Preserve business data and raw Record shape.
4. Reject a Facility request whose stored ID names the Exhibition collection.
5. Canonicalize a typed aggregate root ID before persistence.

Downstream ArtScene generation and operation specs remain the integration
evidence after the corrected CNCF snapshot is published locally.

## Downstream Validation

Validation commands:

```bash
cd /Users/asami/src/dev2026/textus-art-scene
sbt --batch compile
sbt --batch "testOnly org.simplemodeling.textus.artscene.ArtSceneNactFetchProviderSpec org.simplemodeling.textus.artscene.ArtSceneTimelineViewSpec"
```

The final CNCF focused regression passed 45 tests across collection decoding,
EntityStore query routing, detached revision, and aggregate collection suites;
`Test/compile` also passed. The corrected `0.5.1-SNAPSHOT` was published to
both local Ivy and Maven repositories. ArtScene then performed a clean
regeneration of 155 Scala sources, compiled 167 main sources, and passed all 12
focused tests in `ArtSceneNactFetchProviderSpec` and
`ArtSceneTimelineViewSpec`.

Any remaining managed revision API failures are independent and belong under
`2026-07-26-artscene-managed-revision-api-boundary-handoff.md`.
