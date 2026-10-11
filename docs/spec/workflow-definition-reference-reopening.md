# Workflow execution-definition reopening

Status: corrected under the existing sm-workflow Phase 5 removal policy;
51 corrected producer tests passed 2026-10-08. After normal local SNAPSHOT
refresh, 176 affected sm-workflow consumer tests passed. Integrated Phase 5
acceptance/review remains pending. Prior nine passing tests validate the
superseded narrow implementation, not this correction.

## R1 — Reference resolution, not proof of unchanged content

New V1 records write only execution-definition identity/revision. Reopening
resolves that reference in the runtime catalog; it never compares source
lines/resources, generator provenance, raw input copies or fixture hashes.
Old records' extra definition metadata is ignored without granting authority,
reapproval or changing recorded history. Exact repeated references to the same
admitted object do not need a second catalog admission. Absent or genuinely
ambiguous reference resolution still fails when the reference is used.

E1 in WorkflowInstanceDataStorePersistenceSpec covers new writes, old metadata
reads, formatting and actual ambiguous lookup. E3 in GeneratedEntryWorkflowAbiSpec
admits relocated sources and different generator metadata. Obsolete tamper,
fingerprint and source-order expectations are removed, not lengthened or sealed.

## R2 — Actual durable state and execution compatibility

E2 physically closes/reopens SQLite with relocated diagnostic definition
metadata, retaining history, suspension, actual revision and stale-CAS rejection.
Runtime input admission retains supported schema, actual states, executable
action/Operation/type bindings and Judgment/Admission policy compatibility.
Persistence keeps its configuration/access contracts and authoritative
transition readback, not an additional whole encoded-body equality certificate.

Candidate ABI raw-copy/fingerprint producers, entry digest producers, required
persistence binding digests and repeated binding provenance re-admission are
removed together. This does not claim that every other legacy ABI/application
hash control or all Phase 5 routing work is complete.
