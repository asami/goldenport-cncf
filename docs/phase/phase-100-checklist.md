# Phase 100 Checklist: DataStore Logical Types, Storage Mapping and Recovery

status=in-progress
phase=[Phase 100](phase-100.md)

## DSM-100-01: Impact and recovery inventory

Stage Status:
- Current status: CLOSED
- Owner: CNCF DataStore and affected consumer owners
- Update rule: Close only on recorded inventory and reproducible evidence. The checklist below is the closure basis.

- [x] Inventory direct DataStore and EntityStore readers/writers, provider implementations, generated declarations and current codec call sites.
- [x] Map StandardEntityStore create/save/update, query/projection and conditional/versioned readback paths; identify conversion stages, custom admission hooks and risks of duplicate encoding/decoding.
- [x] Inventory `DataType.Named("record")` producers, generated artifacts and consumers, including the existing Record declaration path and missing dedicated JSON datatype support.
- [x] Capture the workbench failure with String, class X, semi-structured Record, JSON and array fixtures, including JSON-looking Strings.
- [x] Classify affected collections, current mapping assumptions, available original bytes/backups and ambiguous data.
- [x] Record the actual prerequisite revisions, local safeguards and cross-repository ownership without altering active Phase scope.

## DSM-100-02: Logical and physical contract

Stage Status:
- Current status: OPEN
- Owner: CNCF DataStore/Entity contract owners
- Update rule: Close only when canonical specs/design and executable contracts fix the mapping boundary. The checklist below is the closure basis.

- [x] Bind the approved `XSemiStructuredData` and `XJson` types and the selected declaration producer to the OR mapper used by DataStore consumers and EntityStore operations.
- [ ] Freeze the producer-facing logical datatype/storage-mapping ABI and expected generated fixture for `asami/cozy` Phase 78; record the exact handoff revision before producer implementation.
- [x] Define `XSemiStructuredData` with Record values and `XJson` with JSON values in the existing canonical type system, with normalization and structured failures; reject name-only `Named` substitutes as the final route.
- [x] Specify explicit JSON/YAML/HOCON format selection and conversion to Record for `XSemiStructuredData`, including accepted shapes and unsupported format-specific values, independently of the DB storage format.
- [x] Distinguish application type, JSON intermediate value, serialized JSON text and physical column type; specify codec/version and field-name normalization without losing mapping identity.
- [x] Define String/class X/semi-structured Record/JSON/array, accepted Record JSON shapes, nested values, array element semantics, numeric precision, null, absent and explicit-null update behavior.
- [x] Define structured missing-mapping, incompatible-schema, wrong-shape and malformed-payload outcomes without content inference.
- [x] Distinguish application-owned explicit `toRecord`/`fromRecord` calls from automatic OR mapping selected by declared `XJson`/`XSemiStructuredData` and storage mappings; runtime values or method availability must not substitute for declarations.
- [x] Freeze EntityStore -> OR mapper -> DataStore and the reverse route, with stage input/output types and an explicit relationship to EntityPersistent store/admission hooks.
- [x] Freeze source/binary compatibility and mapped/unmigrated consumer activation/removal rules before changing defaults.

## DSM-100-03: OR-mapper and physical-provider integration

Stage Status:
- Current status: CLOSED
- Owner: CNCF OR-mapper/DataStore/provider owners
- Update rule: Close only when implementation satisfies the admitted contracts and provider specifications. The checklist below is the closure basis.

- [x] Implement admitted mapping resolution in the OR mapper while keeping DataStore text reads as physical Strings.
- [x] Route every identified EntityStore application-value boundary through the same declared-type mapping contract while retaining physical internal operations, transaction/provider identity, revision checks and atomic side effects.
- [x] Consume the Cozy Phase 78 generated fixture and carry its canonical datatype identities through generated Scala/schema metadata and the existing EntityStore/EntityPersistent storage hooks into mapping resolution.
- [x] Integrate explicit JSON/YAML/HOCON codecs for `XSemiStructuredData` with the declared normalization and failure contracts; reuse existing format support where applicable.
- [x] Apply OR-mapper JSON encoding/decoding and distinct X/Record/JSON reconstruction to create/save/update and load/search/projection, including authoritative mutation readback.
- [x] Preserve mapped application semantics across in-memory and SQL providers and process restarts; test the physical and logical boundaries independently.
- [x] Preserve transaction participation, revision/OCC, identity, managed attributes and partial-update semantics.

## DSM-100-04: Consumer transition

Stage Status:
- Current status: CLOSED
- Owner: CNCF and selected consumer/producer owners
- Update rule: Close only when the affected consumer matrix is migrated and temporary behavior has an explicit disposition. The checklist below is the closure basis.

- [x] Adopt OR-mapper declarations for the selected workbench path and direct DataStore consumers requiring automatic reconstruction; retain explicitly application-owned conversions with clear ownership.
- [x] Adopt declarations for durable Job/quota canonical text and opaque identity fields; retain exact String bytes.
- [x] Connect EntityStore projection and any required generated metadata without guessing field types.
- [x] Map each supported behavior of the current `DataType.Named("record")` path to formal declaration/normalization/generation/OR-mapping responsibilities and retain regression coverage.
- [x] Consume regenerated Cozy Phase 78 outputs to replace `DataType.Named("record")` producer/runtime dependencies with the `XSemiStructuredData` route; retire any temporary legacy declaration reader from the accepted route before closure.
- [x] Verify no active producer, generated schema, runtime branch or executable consumer in the selected scope depends on `DataType.Named("record")`; distinguish historical references and deliberate rejection fixtures from active support.
- [x] Replace payload-shape inference and collection/field-specific exceptions with declaration-based behavior and equivalent regression coverage.
- [x] Demonstrate the selected compatibility cutover and remove or explicitly locate any residual temporary legacy mode outside accepted typed behavior.

## DSM-100-05: Existing data diagnosis and recovery

Stage Status:
- Current status: CLOSED
- Owner: CNCF persistence and affected application data owners
- Update rule: Close only with safe migration and recovery evidence on isolated data copies. The checklist below is the closure basis.

- [x] Distinguish metadata-only adoption from physical data migration using explicit provenance and versioned mappings.
- [x] Produce a read-only/dry-run inventory of exact targets, ambiguous rows, corrupt payloads and unavailable original evidence.
- [x] Preserve raw originals/backups; require explicit disposition for ambiguous data without guessing or silently overwriting it.
- [x] Prove interruption/resume, idempotent retry, verification and rollback/restoration; document irrecoverable prior information loss.
- [x] Record the operational cutover procedure and separate authority required for real database changes.

## DSM-100-06: Integration and closure

Stage Status:
- Current status: OPEN
- Owner: CNCF and workbench validation owners
- Update rule: Close only after required tests, recovery rehearsal, independent review and release evidence. The checklist below is the closure basis.

- [x] Prove String preservation and X/Record/JSON/array roundtrip laws with Given/When/Then and property-based specifications, including empty/nested/Unicode/null/numeric edge cases.
- [x] Accept a Cozy Phase 78 generated model declaration -> datatype/schema/storage metadata -> OR-mapper codec -> restart/readback route preserving distinct semi-structured and JSON types without `Named("record")`, a `Named("json")` substitute or ad hoc string-name dispatch.
- [x] Prove supported JSON/YAML/HOCON representations normalize to Record under `XSemiStructuredData`, with explicit format selection and failures for unsupported values; keep `XJson` readback as JSON.
- [x] Prove automatic codec selection follows declared types, with no fallback based on Record/Json runtime values, `RecordPresentable` or `toRecord`/`fromRecord` availability; preserve explicitly application-owned conversions.
- [x] Prove a text backend returns String and the OR mapper separately restores X, Record or JSON according to the declared application type, including distinct JSON-parse and application-decode failures.
- [x] Prove workbench write -> SQL persistence -> restart -> read reconstructs the declared application values.
- [x] Prove an EntityStore entry-point roundtrip through the OR mapper and SQL TEXT, including restart, for String, XJson and XSemiStructuredData; codec-only tests do not establish the integration route.
- [x] Validate direct reads, queries/projections, partial updates and mutation readback, plus all identified durable consumers.
- [x] Complete selected focused, full-suite and required downstream checks through the normal execution gates.
- [ ] Complete independent review and retain migration/compatibility evidence and any explicitly assigned follow-up work.
- [ ] Record Phase release and closure only after all in-scope ledger items are satisfied.

## Completion evidence and remaining gates — 2026-10-18

Checked items project the sole recovery-full review's item-level assessment,
the independently closed specification fixture correction, completed current
CNCF validation and subsequent ordinary dependency refresh, Workbench generation,
runtime preparation and full validation. Detailed native evidence remains in
private workflow storage; the original reviews and failed attempts are retained.
The source-only/no-existing-DB decision and the
[Phase100/115 boundary disposition](../journal/2026/10/2026-10-18-phase100-phase115-collection-boundary.md)
remain applicable. This projection changes no requirement or historical counter.

DSM-100-02 item 02.02 remains OPEN pending focused review of the explicit
2026-10-18 human disposition: the original preimplementation revision/order
remains UNPROVEN. For this resumed Phase only, the user replaced that historical
ordering condition with current frozen ABI/generated-fixture conformance,
completed functional validation and independent assessment. The original item
and history remain, without backdating or a claim of historical proof. See the
[dated decision](../journal/2026/10/2026-10-18-phase100-phase115-collection-boundary.md#explicit-historical-abi-criterion-disposition--2026-10-18).
Final review closure and Phase release remain OPEN. The user also approved the
four exact generated-fixture paths' persistent blank-at-EOF convention; the
root .gitattributes is applied and both parent-relative Git checks pass under
that changed policy. Generated fixture bytes remain intact. The newly
authorized CNCF-only integration has not yet executed. The completed Modeler and Workbench integration
commits remain retained. Phase 100 is in progress, with 44 of 47 items satisfied;
this is not Phase acceptance, release, push or Phase 115 implementation.
