# Phase 100 Checklist: DataStore Logical Types, Storage Mapping and Recovery

status=planned
phase=[Phase 100](phase-100.md)

## DSM-100-01: Impact and recovery inventory

Stage Status:
- Current status: OPEN
- Owner: CNCF DataStore and affected consumer owners
- Update rule: Close only on recorded inventory and reproducible evidence. The checklist below is the closure basis.

- [ ] Inventory direct DataStore and EntityStore readers/writers, provider implementations, generated declarations and current codec call sites.
- [ ] Map StandardEntityStore create/save/update, query/projection and conditional/versioned readback paths; identify conversion stages, custom admission hooks and risks of duplicate encoding/decoding.
- [ ] Inventory `DataType.Named("record")` producers, generated artifacts and consumers, including the existing Record declaration path and missing dedicated JSON datatype support.
- [ ] Capture the workbench failure with String, class X, semi-structured Record, JSON and array fixtures, including JSON-looking Strings.
- [ ] Classify affected collections, current mapping assumptions, available original bytes/backups and ambiguous data.
- [ ] Record the actual prerequisite revisions, local safeguards and cross-repository ownership without altering active Phase scope.

## DSM-100-02: Logical and physical contract

Stage Status:
- Current status: OPEN
- Owner: CNCF DataStore/Entity contract owners
- Update rule: Close only when canonical specs/design and executable contracts fix the mapping boundary. The checklist below is the closure basis.

- [ ] Bind the approved `XSemiStructuredData` and `XJson` types and the selected declaration producer to the OR mapper used by DataStore consumers and EntityStore operations.
- [ ] Define `XSemiStructuredData` with Record values and `XJson` with JSON values in the existing canonical type system, with normalization and structured failures; reject name-only `Named` substitutes as the final route.
- [ ] Specify explicit JSON/YAML/HOCON format selection and conversion to Record for `XSemiStructuredData`, including accepted shapes and unsupported format-specific values, independently of the DB storage format.
- [ ] Distinguish application type, JSON intermediate value, serialized JSON text and physical column type; specify codec/version and field-name normalization without losing mapping identity.
- [ ] Define String/class X/semi-structured Record/JSON/array, accepted Record JSON shapes, nested values, array element semantics, numeric precision, null, absent and explicit-null update behavior.
- [ ] Define structured missing-mapping, incompatible-schema, wrong-shape and malformed-payload outcomes without content inference.
- [ ] Distinguish application-owned explicit `toRecord`/`fromRecord` calls from automatic OR mapping selected by declared `XJson`/`XSemiStructuredData` and storage mappings; runtime values or method availability must not substitute for declarations.
- [ ] Freeze EntityStore -> OR mapper -> DataStore and the reverse route, with stage input/output types and an explicit relationship to EntityPersistent store/admission hooks.
- [ ] Freeze source/binary compatibility and mapped/unmigrated consumer activation/removal rules before changing defaults.

## DSM-100-03: OR-mapper and physical-provider integration

Stage Status:
- Current status: OPEN
- Owner: CNCF OR-mapper/DataStore/provider owners
- Update rule: Close only when implementation satisfies the admitted contracts and provider specifications. The checklist below is the closure basis.

- [ ] Implement admitted mapping resolution in the OR mapper while keeping DataStore text reads as physical Strings.
- [ ] Route every identified EntityStore application-value boundary through the same declared-type mapping contract while retaining physical internal operations, transaction/provider identity, revision checks and atomic side effects.
- [ ] Carry canonical datatype identities through model declarations, generated Scala/schema metadata and the existing EntityStore/EntityPersistent storage hooks into mapping resolution.
- [ ] Integrate explicit JSON/YAML/HOCON codecs for `XSemiStructuredData` with the declared normalization and failure contracts; reuse existing format support where applicable.
- [ ] Apply OR-mapper JSON encoding/decoding and distinct X/Record/JSON reconstruction to create/save/update and load/search/projection, including authoritative mutation readback.
- [ ] Preserve mapped application semantics across in-memory and SQL providers and process restarts; test the physical and logical boundaries independently.
- [ ] Preserve transaction participation, revision/OCC, identity, managed attributes and partial-update semantics.

## DSM-100-04: Consumer transition

Stage Status:
- Current status: OPEN
- Owner: CNCF and selected consumer/producer owners
- Update rule: Close only when the affected consumer matrix is migrated and temporary behavior has an explicit disposition. The checklist below is the closure basis.

- [ ] Adopt OR-mapper declarations for the selected workbench path and direct DataStore consumers requiring automatic reconstruction; retain explicitly application-owned conversions with clear ownership.
- [ ] Adopt declarations for durable Job/quota canonical text and opaque identity fields; retain exact String bytes.
- [ ] Connect EntityStore projection and any required generated metadata without guessing field types.
- [ ] Map each supported behavior of the current `DataType.Named("record")` path to formal declaration/normalization/generation/OR-mapping responsibilities and retain regression coverage.
- [ ] Replace `DataType.Named("record")` producer/runtime dependencies with the `XSemiStructuredData` route and regenerate affected outputs; retire any temporary legacy declaration reader from the accepted route before closure.
- [ ] Verify no active producer, generated schema, runtime branch or executable consumer in the selected scope depends on `DataType.Named("record")`; distinguish historical references and deliberate rejection fixtures from active support.
- [ ] Replace payload-shape inference and collection/field-specific exceptions with declaration-based behavior and equivalent regression coverage.
- [ ] Demonstrate the selected compatibility cutover and remove or explicitly locate any residual temporary legacy mode outside accepted typed behavior.

## DSM-100-05: Existing data diagnosis and recovery

Stage Status:
- Current status: OPEN
- Owner: CNCF persistence and affected application data owners
- Update rule: Close only with safe migration and recovery evidence on isolated data copies. The checklist below is the closure basis.

- [ ] Distinguish metadata-only adoption from physical data migration using explicit provenance and versioned mappings.
- [ ] Produce a read-only/dry-run inventory of exact targets, ambiguous rows, corrupt payloads and unavailable original evidence.
- [ ] Preserve raw originals/backups; require explicit disposition for ambiguous data without guessing or silently overwriting it.
- [ ] Prove interruption/resume, idempotent retry, verification and rollback/restoration; document irrecoverable prior information loss.
- [ ] Record the operational cutover procedure and separate authority required for real database changes.

## DSM-100-06: Integration and closure

Stage Status:
- Current status: OPEN
- Owner: CNCF and workbench validation owners
- Update rule: Close only after required tests, recovery rehearsal, independent review and release evidence. The checklist below is the closure basis.

- [ ] Prove String preservation and X/Record/JSON/array roundtrip laws with Given/When/Then and property-based specifications, including empty/nested/Unicode/null/numeric edge cases.
- [ ] Prove model declaration -> generated datatype/schema -> OR-mapper codec -> restart/readback preserves distinct semi-structured and JSON types without `Named("record")`, a `Named("json")` substitute or ad hoc string-name dispatch in the accepted route.
- [ ] Prove supported JSON/YAML/HOCON representations normalize to Record under `XSemiStructuredData`, with explicit format selection and failures for unsupported values; keep `XJson` readback as JSON.
- [ ] Prove automatic codec selection follows declared types, with no fallback based on Record/Json runtime values, `RecordPresentable` or `toRecord`/`fromRecord` availability; preserve explicitly application-owned conversions.
- [ ] Prove a text backend returns String and the OR mapper separately restores X, Record or JSON according to the declared application type, including distinct JSON-parse and application-decode failures.
- [ ] Prove workbench write -> SQL persistence -> restart -> read reconstructs the declared application values.
- [ ] Prove an EntityStore entry-point roundtrip through the OR mapper and SQL TEXT, including restart, for String, XJson and XSemiStructuredData; codec-only tests do not establish the integration route.
- [ ] Validate direct reads, queries/projections, partial updates and mutation readback, plus all identified durable consumers.
- [ ] Complete selected focused, full-suite and required downstream checks through the normal execution gates.
- [ ] Complete independent review and retain migration/compatibility evidence and any explicitly assigned follow-up work.
- [ ] Record Phase release and closure only after all in-scope ledger items are satisfied.
