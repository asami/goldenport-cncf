# Phase 52 EID-05 - Identity Consumers and Built-Ins

Status: focused Admin detail/edit canonical-route revalidation passed; EID-06 closure review pending

## Outcome

Every covered CNCF identity consumer now treats a canonical `EntityId` as its
complete exact identity. A selected collection can validate the ID owner, but
cannot rewrite, rebind, normalize, or infer it from a logical collection name.

The final Admin detail/edit renderer boundary passed focused revalidation: scalar
and foreign-owner locators produce neither a page nor an actionable form/link.
The remaining independent review belongs to EID-06's phase-closure gate.

## Implemented rules

- The obsolete `EntityStoreDecodeContext` and context-aware
  `fromStoreRecord` ABI are removed. Store decode parses once and validates the
  decoded exact owner against the datastore collection.
- Association, Blob, Media, Tag, Job, Admin, child-binding, and Blob/media
  ingress reject a foreign exact ID before their relevant action or storage
  side effect.
- Built-in codecs retain canonical IDs from records. Blob sidecar metadata no
  longer overrides the collection encoded in the canonical ID.
- ComponentFactory no longer discovers or invokes a context-aware persistence
  decoder. Test fixtures use the same one-argument exact decode contract.
- UnitOfWork, resident lookup/eviction, dirty state, authorization, and
  CallTree paths retain the supplied exact ID; the audit found no remaining
  collection-copy compensation in those paths.

## Evidence

- `Test / compile` passed through the serialized CNCF SBT runner.
- Built-in focused validation: 68 passed, 0 failed across 6 suites.
- Cross-consumer validation passed through the serialized runner: 14 suites,
  158 passed, 0 failed, 0 canceled, 0 ignored, and 0 pending.
- The focused group covers Association binding/repository, child binding,
  Blob attachment/content/media/store, Tag, Job identity, Blob Admin,
  ComponentFactory generated schema, store decode, EntitySpace, and
  UnitOfWork conditional transition.
- Independent review found one stale Phase 51 documentation contradiction;
  the Phase 52 design/specification was corrected and the focused re-review
  returned no P0/P1/P2 findings.
