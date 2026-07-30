# Phase 52 EID-01 — Identity Consumer Inventory and Failing-First Contract

status=DONE
phase=[Phase 52](../phase/phase-52.md)
checklist=[EID-01](../phase/phase-52-checklist.md)

## Purpose

This inventory fixes the Phase 51 starting point as executable evidence before
Phase 52 changes any identity encoding. It records the current scalar loss and
the runtime compensation that Phase 52 will remove. It is not a compatibility
contract: old scalar IDs, context rebinding, migration, and dual-format reads
remain outside the target design.

## Current Identity Construction and Loss

`simplemodeling-model` owns `EntityId` and `EntityCollectionId` in
`org.simplemodeling.model.datatype.EntityId.scala`. Both delegate rendering to
`UniversalId`. `EntityId.value`, `print`, `show`, and `toString` therefore
render the Entity-local `major`/`minor` plus the collection name. They do not
contain the independent `major`/`minor` of `EntityId.collection`.

`EntityId.parse` calls `UniversalId.parseParts` and constructs
`EntityCollectionId(parts.major, parts.minor, name)`. A parsed scalar is thus
synthetic whenever the entry namespace and the exact collection namespace
differ. `EntityId.equals` and `hashCode` compensate in memory by including the
complete collection, but String transport loses it. `EntityIdSpec` proves both
the collision and the manual Phase 51 rebind needed to restore equality.

`EntityCollectionId.parse` has its own `UniversalId` projection. Its exact,
versioned replacement is deliberately deferred to EID-02; EID-01 does not
select syntax, delimiters, or version markers.

## Persistence and Generated-Code Inventory

| Boundary | Current behavior | Evidence / Phase 52 direction |
| --- | --- | --- |
| SimpleModeler `Scala3ClassGeneratorBase` | Generates `EntityPersistent.fromStoreRecord(context, record)` and calls `EntityPersistent.restoreCollectionIdentity`. | `SimpleEntityRevisionGenerationSpec`; remove restoration in EID-03 after exact parsing exists. |
| Cozy modeler output | Generates Entity primary IDs and `EntityStoreDecodeContext` restoration source. | `ModelerScalaGenerationSpec`; register generated runtime persistence acceptance in EID-03. |
| Built-in, custom, and raw codecs | `EntityPersistent` validates or restores the selected owner through `EntityStoreDecodeContext`. | `EntityPersistentCollectionIdentitySpec`; EID-04 removes normal restoration. |
| Stored record and datastore addressing | Entity record `id` and entry keys use `EntityId.value`; selected collection supplies routing. | `EntityStoreQueryRouteSpec`; EID-04 requires the parsed exact ID to supply both values. |

Generated primary IDs and optional/repeated `EntityId` attributes enter the
generated `ValueReader`/`EntityPersistent` decode path today. Association
binding parses request values directly in `AssociationBindingWorkflow`, and
Blob metadata parsing does so directly in `BlobStores`. All three routes lose
the independent collection namespace when the scalar is parsed, then depend on
selected-owner restoration or target-kind resolution.

## CNCF Identity Consumer Inventory

| Consumer group | Current owner / executable evidence | EID-02 through EID-05 target |
| --- | --- | --- |
| EntitySpace, direct store, loader, UnitOfWork | `EntitySpace`, `EntityStore`, `EntityStoreSpace`, `EntityLoader`, `UnitOfWork*`; `EntitySpaceCollectionIdentitySpec`, `EntityStoreQueryRouteSpec` | `EntityId.value`/`print` serialize record and query keys; `id.collection.print` supplies selected routing. EID-04 routes only from the parsed exact collection, with no logical-name resolution. |
| Association, Blob, child binding | `AssociationBindingWorkflow:234`, `BlobStores:298`, `ChildEntityBindingWorkflow`; corresponding specifications | Association and Blob call `EntityId.parse` directly; child binding receives parsed source IDs. EID-05 persists and returns the parsed exact target without scan or rebind. |
| Built-in persistence | Tag, Blob, Media, Association, Job `EntityPersistent` implementations | Generated and built-in record readers use `ValueReader[EntityId]` or direct parsing; EID-04 adopts the common pure parser and exact ID storage contract. |
| Resident maps, cache, locks, revision | `EntitySpace` resident projection, `EntityStore` upsert lock (`id.print`), UnitOfWork maps, aggregate locks, revision keys | These state keys consume `value`, `print`, or the ID object. EID-04/EID-05 require exact parsed IDs and prohibit reconstructed owners. |
| Admin and authorization | Admin Entity operations, `UnitOfWorkTargetAuthorizationSpec`, authorization resources | Request IDs pass through `EntityId.parse`, then authorization and provider paths. EID-05 rejects a mismatch before provider, resident, authorization, audit, or CallTree side effects. |
| Diagnostics and observability | `ConclusionDiagnostics`, audit, CallTree, metrics, `UnitOfWorkInterpreter` diagnostics | `value`, `print`, and `show` are emitted in messages and attributes. EID-05 reports the complete opaque canonical value and does not branch on its structure. |

Admin, aggregate-lock, revision, and diagnostic execution changes are owned by
EID-05. They are inventoried here and retain their existing integration
evidence until their production behavior changes.

## Acceptance Ownership and Failing-First Registration

EID-01 retains the observed current defect as passing evidence and registers
the desired contract with `pendingUntilFixed`, which is pending only while the
current implementation demonstrably fails it:

| Acceptance group | Current-defect evidence | Target registration |
| --- | --- | --- |
| Exact codec / String boundary | `EntityIdSpec` proves the current `EntityCollectionId` scalar round-trip and registers the failing `EntityId` round-trip, non-collision, context-free parse, and old-scalar rejection laws. | EID-02 makes every registered `EntityId` law pass. |
| Generated primary and references | `SimpleEntityRevisionGenerationSpec` and `ModelerScalaGenerationSpec` prove that generated primary, optional, and repeated fields use the concrete store encode/decode route. `cozy/exact-entity-id-generated-roundtrip` scaffolds, compiles, and runs the generated `Facility`; `EntityPersistentCollectionIdentitySpec` E1c supplies complementary framework generated-shape coverage. | EID-03 makes the registered generated-shape round-trip pass under canonical parsing. |
| Store and routing | `EntityPersistentCollectionIdentitySpec`, `EntityStoreQueryRouteSpec`, `EntitySpaceCollectionIdentitySpec` prove current repair and synthetic routing, with failing parsed-owner registrations. | EID-04 routes from the parsed owner and proves mismatch rejection before a store side effect. |
| Consumers | Association, Blob, child-binding, and authorization specifications prove each current direct parse or restored source; E7 executes the authorization/store branch reached by an accepted old scalar. | EID-05 proves exact retention without scanning, rebinding, or provider/cache/authorization side effect. |

`sbt-cozy` is an admitted Phase 52 repository only for later build/generation
fixtures. It contains no `EntityId` or `EntityCollectionId` semantic use, so
EID-01 makes no artificial identity test there. The Cozy scripted fixture is
the bridge evidence: it selects the explicit mutable CNCF/Cozy development
pair, compiles the generated `Facility`, and records the present loss with
`EID01_GENERATED_ENTITY_CURRENT_LOSS_OK`.

## Modified Scala Compliance Ledger

| File | Naming | Executable-specification style | Validation |
| --- | --- | --- | --- |
| `simplemodeling-model/.../EntityIdSpec.scala` | header and naming conform | Given/When/Then and matcher style | review-fix focused test: 8 passed, 3 pending scopes |
| `simple-modeler/.../SimpleEntityRevisionGenerationSpec.scala` | header and naming conform | generated store encode/decode route is explicit | review-fix focused test: 1 passed, 1 pending scope |
| `cncf/.../EntityPersistentCollectionIdentitySpec.scala` | header and naming conform | EID metadata, shallow `which`, and E1c generated-shape runtime round-trip added | review-fix focused test: 5 passed, 2 pending scopes |
| `cncf/.../EntitySpaceCollectionIdentitySpec.scala` | header and naming conform | EID metadata and shallow `which` grouping added | review-fix EID-01 focus (7 suites): 96 passed, 8 pending, 0 aborted |
| `cncf/.../EntityStoreQueryRouteSpec.scala` | header and naming conform | EID metadata and shallow `which` grouping added | review-fix EID-01 focus (7 suites): 96 passed, 8 pending, 0 aborted |
| `cncf/.../AssociationBindingWorkflowSpec.scala` | normalized private names | EID metadata and shallow `which` grouping added | review-fix EID-01 focus (7 suites): 96 passed, 8 pending, 0 aborted |
| `cncf/.../BlobAttachmentWorkflowSpec.scala` | restored Apr. 30 history and normalized private names | EID metadata added | review-fix EID-01 focus (7 suites): 96 passed, 8 pending, 0 aborted |
| `cncf/.../ChildEntityBindingWorkflowSpec.scala` | normalized internal private names | EID metadata and shallow `which` grouping added | review-fix EID-01 focus (7 suites): 96 passed, 8 pending, 0 aborted |
| `cncf/.../UnitOfWorkTargetAuthorizationSpec.scala` | header and naming conform | EID metadata added; E7 covers authorization/store side-effect boundary | review-fix focused test: 34 passed, 1 pending scope |
| `cozy/.../ModelerScalaGenerationSpec.scala` | header and naming conform | generated store encode/decode route is explicit | review-fix focused test: 27 passed, 1 pending scope |
| `cozy/.../exact-entity-id-generated-roundtrip` | scripted fixture names conform | generated `Facility` primary, optional, repeated, and cross-collection Entity IDs compile and run | `scripted cozy/exact-entity-id-generated-roundtrip`: passed; current-loss marker emitted |

## Deferred Work

- EID-02 chooses and implements the versioned lossless codecs.
- EID-03 adopts them in generated primary and reference storage.
- EID-04 removes routing and decode-context compensation.
- EID-05 repairs every built-in consumer and side-effect boundary.
- EID-06 promotes only verified behavior to canonical design and specification.

## Completion Record

EID-01 is complete. The recorded failing-first evidence and its focused
re-review establish the pre-EID-02 loss, collision, synthetic reconstruction,
and consumer boundaries. EID-02 now supersedes only the codec-specific pending
laws; generated-code and runtime-consumer pending laws remain owned by their
recorded later slices.
