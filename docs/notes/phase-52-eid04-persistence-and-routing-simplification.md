# Phase 52 EID-04 - Persistence and Routing Simplification

Status: done; independent review, review-fix, and clean re-review accepted

## Outcome

The CNCF persistence and routing boundary now treats `EntityId` as a complete
Universal ID. A decoded value, a datastore collection, and a datastore entry
key retain the exact collection encoded in the canonical ID; none is rebuilt
from a logical name or selected owner.

## Implemented rules

- `EntityPersistent._decode_store_record` invokes the ordinary store decoder
  once, with the original record, then requires an exact collection match.
  The normal route never invokes `EntityStoreDecodeContext` or applies a
  restoration callback.
- `EntityStoreSpace` selects datastore collections from `id.collection` and
  entry keys from the complete `EntityId`. Explicit create and claim IDs must
  have the same collection as their declared create collection before a
  provider or datastore is selected.
- `EntitySpace`, `EntityCollection`, and `UnitOfWorkInterpreter` preserve
  exact Entity and collection IDs. Identity-sensitive paths do not select a
  uniquely named collection or copy a selected collection into an ID.
- Search, exclusion, update, delete, restore, conditional transition, and
  identity-resolution operations retain their input exact IDs.

## Evidence

- `Test / compile` passed through the serialized CNCF SBT runner.
- `EntityPersistentCollectionIdentitySpec`, `EntityStoreQueryRouteSpec`, and
  `EntitySpaceCollectionIdentitySpec`: 45 passed, 0 failed.
- `UnitOfWorkSpec`, `UnitOfWorkVersionedMutationSpec`,
  `UnitOfWorkConditionalTransitionSpec`, `UnitOfWorkSearchAuthorizationSpec`,
  and `UnitOfWorkPlainMutationProviderParitySpec`: 49 passed, 0 failed;
  one opt-in live-MySQL case canceled and two pre-existing pending cases.
- The routing suite proves exact decode acceptance, mismatch rejection,
  no context-repair invocation, create-side mismatch rejection before a
  provider mutation, and independent persistence of same-local IDs in two
  exact collections.
- Review-fix validation removed remaining UnitOfWork resident name fallbacks,
  rejects incomplete legacy scalars before any datastore call, and proves a
  resident lookup cannot cross same-named exact collection namespaces.
- The ActionCall Entity DSL now passes exact IDs directly into UnitOfWork
  operations. A same-name foreign collection root is rejected at the executing
  component boundary rather than rebound to the local collection.
- Aggregate authorization and root loading resolve one declared exact owner;
  zero owners retain the non-entity aggregate path, while multiple same-name
  owners fail closed. Record persistence accepts only an exact `EntityId` or
  a parseable canonical Universal ID; malformed and legacy scalar IDs fail
  before storage. Missing root IDs remain valid only for creates, whereas
  updates require an exact root ID. A same-name foreign aggregate ID is
  rejected before its resolver or update action can run.
- The Phase 52 focused acceptance group passed 112 tests with no failures;
  the focused UnitOfWork group passed 49 tests with no failures (one opt-in
  live-MySQL case canceled and two pre-existing cases pending).
- The review-fix boundary group passed 79 tests with no failures across the
  ActionCall, Blob, EntityStore, and UnitOfWork paths.
- The aggregate and canonical-constant follow-up group passed 83 tests with no
  failures across the ActionCall, Blob, identity, EntityStore, and UnitOfWork
  paths.
- The registered-owner and scalar-ID regression group passed 84 tests with no
  failures, and `Test / compile` passed. It proves same-name registered
  collection ambiguity rejects aggregate load/update before resolver, action,
  or persistence, and malformed or legacy scalar IDs fail before root storage.
- Release repair on 2026-07-30 converted stale mutation-fixture entropy to the
  delimiter-safe canonical alphabet, replaced startup-import legacy scalar IDs
  with canonical exact IDs, and changed the typed aggregate placeholder case to
  require exact-owner rejection rather than collection rebinding. The full
  framework suite then passed 2,639 tests in 375 suites with 0 failures.

## Deferred to EID-05

The remaining built-in and consumer-specific `EntityStoreDecodeContext` /
`restoreCollectionIdentity` adapters are EID-05 work. They are no longer on
the normal persistence route, but their consumer redesign must remove the
legacy compensation API before Phase 52 closure.
