# Durable Knowledge materialization result

Status: authored candidate; validation and independent acceptance pending.
Date: 2026-10-29.

## Meaning and boundary

A materialization is an immutable result retaining the existing canonical
`org.goldenport.cncf.knowledge.KnowledgeWorkingSetSnapshot` and its exact semantic
owners. It is not an evaluation KnowledgeItem, editable Information, a proposal,
or a decision workflow. Hub validates semantic eligibility and the actual current
Workbench decision before requesting formation. A stored decision reference and
actor string alone are not proof of approval. CNCF retains that attribution;
it never interprets, approves or corrects semantic meaning.

The public types in `org.goldenport.cncf.knowledge` are:

- `KnowledgeOwnerReference(owner, identity, revision)` selects an exact external
  owning version without parsing its identity or treating it as a write grant.
- `KnowledgeDecisionReference(reference, actor, occurredAt, reason)` retains the
  attributable decision, using an actual Instant.
- `KnowledgeMaterialization(id, source, semanticTarget, context, decision, snapshot)`
  retains the native Entity identity and the complete canonical graph.
- `KnowledgeMaterializationEntity(materialization, securityAttributes)` is the
  native persistence envelope. Framework-managed native security columns are
  decoded separately from semantic data, so search visibility uses real row rights.

## Canonical data admission

`KnowledgeMaterialization.validate` requires complete nonblank owner references,
actor/time/reason, nodes and a frame. It reuses pure
`KnowledgeWorkingSet.validateSnapshot` for unique identities, relationship endpoints,
node/relationship/evidence/fact/frame references. Materialization additionally
requires nonblank graph identities, finite confidences and valid local structural,
identity-link, operation-frame, semantic-provenance and source-evidence references.
No generic Interpretation or Mapping type is introduced.

`KnowledgeMaterializationCodec.encodeC/decodeC` use the versioned
`cncf.knowledge-materialization.v1` envelope. The graph is encoded as its full typed
value, including fields absent from display projections. Decoding refuses an
unsupported schema or a lossy typed reconstruction. It does not replace the graph
with KnowledgeWorkingSet's derived operational structure. Source and result graph
are retained exactly; the existing KnowledgeSpace can separately load a view.

## Native persistence API

`KnowledgeMaterializationRepository(owner: ComponentId, collectionId: EntityCollectionId)`
uses the owning runtime's existing application datastore and UnitOfWork. The owner
registers `KnowledgeMaterializationRepository.persistent` for
`KnowledgeMaterializationEntity` with a native Detached revision binding in its
Entity runtime plan. `createPersistent` encodes strict create input. Missing or
incompatible runtime binding is a configuration failure, never memory fallback.
These are Scala framework APIs, not newly hosted service Operations or released
consumer artifacts. Producers compose the programs into their actual Operations.

- `reserveIdentity()(using ExecutionContext): Consequence[EntityId]` generates an
  identity for that configured collection. The caller must persist the reservation
  before a potentially uncertain creation; generation alone is not a reservation.
- `create(value)(using ExecutionContext): ExecUowM[EntityRevisionCarrier[KnowledgeMaterialization]]`
  validates and performs exactly one native strict Entity create, then owning read.
- `get(id)(using ExecutionContext): ExecUowM[EntityRevisionCarrier[KnowledgeMaterialization]]`
  reads that actual native identity with detached revision. Missing/denied/failed
  reads remain structured failures and confer no retry authority.
- `findBySource(reference)` returns visible results for the exact owner, identity
  and revision. `findByNode(nodeId)` returns visible result envelopes containing
  that canonical node. Together with get these provide forward and reverse trace.

All programs require an authenticated caller and use native UserPermission UoW
admission plus owning factory authorization. Reads retain native row permissions;
searches apply native visible-row filtering before exact source/node selection.
The generic repository supplies no privileged/public provider fallback. Result
payload identity must agree with the native row identity. Strict create cannot
change an existing result, even with the same source or changed target.

The payload uses native Entity `content` storage, including native overflow/body
transactions where needed. No separate file store, direct SQL persistence, hash,
TTL, contention lock, retry controller or semantic-review state machine is added.
No update/delete API is exposed by this repository. Producer factories must not
publish generic mutation routes that undermine their immutable result contract.

After an uncertain acknowledgement or failed readback, the caller retains its
existing identity and uses get. It must not infer absence means no prior effect,
create a new identity or automatically resend. The repository propagates the
native indeterminate/failure Conclusion unchanged and performs no replay.

## Executable specifications

`KnowledgeMaterializationSpec` covers full typed graph/property roundtrips,
compatibility with existing KnowledgeSpace traversal, invalid graph references,
incompatible schemas, strict duplicates, exact trace and native authorization.
`KnowledgeMaterializationPersistenceSpec` requires actual PostgreSQL through
`PostgresqlAcceptance`; missing prerequisites fail the suite, never cancel it.
It covers independent runtime/pool reconstruction, property-based exact versioned
source lookup, real commit acknowledgement loss, rejection, duplicate and denied
reads while preserving unrelated results. Controlled fixture decisions do not
establish genuine Human approval or an accepted Hub/Studio route.

Producer local SNAPSHOT refresh, affected checks, independent review and final
required full validation remain separate native responsibilities. No version bump
or publication is implied by this source specification.
