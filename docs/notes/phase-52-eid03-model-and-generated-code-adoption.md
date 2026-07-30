# Phase 52 EID-03: Model and Generated-Code Adoption

status=DONE
phase=[Phase 52](../phase/phase-52.md)
checklist=[EID-03](../phase/phase-52-checklist.md)

Generated Entity persistence now stores primary, optional, and repeated
`EntityId` values as the complete EID-02 canonical scalar and restores them
through the same pure parser. Generated codecs no longer emit a context-aware
`fromStoreRecord` override or call `restoreCollectionIdentity`.

The alternate collection-namespace ID constructor was removed. Ordinary ID
generation retains its runtime outer namespace while the canonical `ec1`
payload carries the exact collection independently. Runtime clock values are
normalized to canonical epoch milliseconds before EntityId construction.

Focused implementation validation passed:

- `simplemodeling-model`: `Test / compile`, `EntityIdSpec` 13/13, and
  development `publishLocal`.
- `simple-modeler`: `Test / compile`,
  `SimpleEntityRevisionGenerationSpec` 2/2, and development `publishLocal`.
- CNCF: `Test / compile`, seven focused suites 42/42 (one unrelated EID-04
  pending scope), and development `publishLocal`.
- Cozy: `Test / compile`, `ModelerScalaGenerationSpec` 28/28, and scripted
  `cozy/exact-entity-id-generated-roundtrip` 1/1, emitting
  `EID03_GENERATED_ENTITY_EXACT_ROUNDTRIP_OK`.

Independent REVIEW and RE_REVIEW accepted the bounded delta. EID-03 is DONE.
