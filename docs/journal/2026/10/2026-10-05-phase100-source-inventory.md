# Phase 100 source inventory and producer prerequisites

Date: 2026-10-05
Status: initial source inventory; DSM-100-01 and DSM-100-02 remain OPEN.
Branch: `codex/workbench-cncf-phase100`
Original base: `21c1c25960bbf9db3d92c1d12c1ea043ce8af6cb`

## Reproduction and physical boundary

Workbench Phase 1 Step 2 supplies the consumer regression. Its last recorded
proposal suite had 12 passing and 4 failing tests out of 16; exact original
proposal text differed after persistence. This is retained earlier evidence,
not a new run against this branch. `CandidateProposal.parse` deliberately stores
the received JSON String without rewriting it. `CandidateProposalSpec` checks
the original at initial read, resend after a human edit, and shutdown/reopen.

In this branch the heuristic is in `SqlDataStore.scala`, not the separately
extracted `SqlDataStoreRecordCodec.scala` present in the other local checkout.
`_record_from_resultset` calls `_decode_column_value` for JDBC values. That helper
trims String values and parses object-looking text into Record or array-looking
text into Vector. `_json_to_value` additionally reduces JSON numbers to Long or
Double. SQL TEXT thus fails to preserve its physical String value.

`_column_value` and `_json_from_value` also serialize runtime Record,
RecordPresentable and Iterable values without a declared logical mapping.
These are affected write boundaries; fixing read alone cannot close Phase 100.

`EntityStoreRecordProjection.project` currently has only ScalarString handling.
It serializes a readback Record to JSON text, which cannot recover the original
String formatting already lost at the provider boundary. Extend the existing
mapping route; do not retain this reconstruction as String preservation.

## EntityStore conversion matrix

| Source | Boundary requiring declared mapping |
| --- | --- |
| `StandardEntityCreatePart.scala` | `toStoreRecord`, create and upsert/save paths |
| `StandardEntityReadSavePart.scala` | ordinary load/save, embedded/detached snapshot admission and decoding |
| `StandardEntityUpdatePart.scala` | update/patch records and mutation readback |
| `StandardEntityRecordPart.scala` | plain save/create/update, managed-field complement, ContentBody preparation |
| `StandardEntityLifecycleQueryPart.scala` | lifecycle patch, query/projection and conditional transition inputs |
| `StandardEntityConditionalPart.scala` | conditional root/successor writes and admitted authoritative readback |
| `StandardEntityRevisionPart.scala` | versioned mutation and snapshot/carrier readback |

`EntityPersistent.toStoreRecord`, `admitStoreRecord`,
`decodeAdmittedStoreRecord` and `fromStoreRecord` are the existing hook boundary.
The implementation plan must specify which stage consumes physical values and
which consumes logical values, so custom hooks do not parse or encode twice.
Identity, revision, permissions, tenant, ContentBody and transaction participation
retain their existing owners. Conversion does not alter mutation policy.

`ContentBodyStoragePolicy.scala` has direct overflow-collection writes and reads;
classify these as framework-owned physical records separately from generated
application fields. `SimpleEntityStorageShapePolicy.permissionJson` deliberately
returns String, and its permission reader already explicitly parses that String.
Permission JSON is a canonical-text compatibility case, not a semi-structured
field inferred from its content. Both require regression coverage at cutover.

## Canonical prerequisite owners

Read-only inspection identified two prerequisite implementation owners:

- goldenport-core: `src/main/scala/org/goldenport/schema/DataType.scala`
  defines the sealed DataType hierarchy and CanonicalDataType normalizers.
  Neither XJson nor XSemiStructuredData exists in that source. New canonical
  implementations cannot be declared in CNCF as subclasses of this sealed
  hierarchy. A separate CNCF type hierarchy or Named substitute would violate
  the agreed Phase contract.
- SimpleModeler: `src/main/scala/org/simplemodeling/SimpleModeler/generator/scala/ComponentPart.scala`
  emits `DataType.Named("record")`; `Scala3ClassGeneratorBase.scala` emits
  EntityStoreAttribute.scalarString metadata. Its `model/MDataType.scala`
  owns the semantic datatype representation consumed by Cozy.

The observed main checkout revisions are goldenport-core
`3c8ac18c528807b88a6bf3f92dc79a8ba775d7dd` and SimpleModeler
`494c72a5617ae3c683bd7a88bfda9bc7ae41b076`. They locate the read-only evidence;
both were subsequently selected as the dedicated prerequisite checkout bases
by the user's explicit source-owner direction. Both source checkouts were clean
at inventory time. Core and SimpleModeler are now admitted owners, with no
uncommitted main-checkout implementation imported.

## Next bounded handoff

1. Add dedicated goldenport-core and SimpleModeler checkouts to this coordinated
   development scope when the user selects them; retain separate original bases.
2. Freeze DSM-100-02 in the canonical spec/design, including actual producer ABI,
   explicit format and storage mapping, missing/invalid mapping failures and
   precise null/absence/number/array/class rules.
3. Implement canonical Core types and SimpleModeler semantic/generator support,
   then Cozy model projection and representative generated fixtures.
4. Integrate declared mappings into the existing CNCF hooks/provider route.
5. Compile and test the real generated fixture, provider/restart roundtrips,
   managed-field and durable-consumer regressions; explicitly select Workbench
   dependency versions before its downstream validation.

No new tests, implementation changes, Step acceptance or Phase closure are
claimed by this inventory. Collection/data recovery inventory, complete direct
consumer coverage, and frozen producer ABI remain required before their
checklist items can close. Existing main checkout changes are not imported.

## Current coordinated development

The retained Workbench Phase 1 Goal is active. Dedicated Core and SimpleModeler
checkouts joined the existing review context through RepositoryAddition. Core
canonical JSON types and conversion are being validated first; explicit
JSON/YAML/HOCON intake follows before the producer/storage contract is connected.
This update does not close this Phase's inventory, ABI, fixture or acceptance
checklist items.
