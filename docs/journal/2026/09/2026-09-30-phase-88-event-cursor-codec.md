# Pure versioned cursor model and codec

Date: 2026-09-30
Status: pure cursor codec/proposal passed focused-323; host persistence open

Added EventCursorRecordV1 and its seven executable examples. It encodes exactly
cursor_schema, positive integral revision and canonical decimal-text last_sequence.
Logical decoding rejects physical id, missing/unknown/duplicate fields, wrong
schema, nontext sequences, signs/whitespace/leading zeros, negative/overflowing
values and invalid revision types. It admits native Int/Long revision scalars
without string/decimal/floating coercion. Field order alone is immaterial.

Immutable proposals retain exact expected facts, advance revision once and
allocate contiguous proposed sequences for 1..256 new durable facts. Revision
and sequence arithmetic are checked before construction. Zero new facts yields
None, even at exhausted counters; no implicit reset or overflow wrap occurs.
The initial value is only data (revision 1, sequence 0), not storage bootstrap.
No provider, identity, Create/upsert/reset, lease, callback or dispatch API is
included. createC/decodeC construct facts, never permission to replace a cursor.

Seven examples cover extreme Long values, canonical forms/types/field shape,
initial facts, immutable bounded proposals, zero-work and both overflows.
They do not prove forbidden storage reset, bootstrap races or SQL recovery.

Validation history:
- SMWF-CNCF-CURSOR-CODEC-DIRECT-20260930-321 failed during Information generation,
  before cursor compile/tests: Cozy development runtime classpath was stale.
  SBT/wrapper exit=1, lock=released. No cursor-test result was produced.
- SMWF-COZY-CURSOR-CLASSPATH-DIRECT-20260930-322 ran the normal
  "compile; cozyExportRuntimeClasspath" prerequisite successfully, exit=0/0,
  lock=released. No Cozy source/cache configuration was edited.
- SMWF-CNCF-CURSOR-CODEC-DIRECT-20260930-323 reran the same logical focused argv
  and passed 183/183 tests in 21 suites. Zero failures/aborted/canceled/ignored/
  pending, compile coverage confirmed, SBT/wrapper exit=0, lock=released.

Successful log:
/tmp/skill.cncf.d/cncf-sbt-8bd43804c5767e2b0c08fba8e02183ccf2577a834c7f65fe2c8f4d7c8e3c906a-9f49045865dccb53b14ce998d42a103e/65117-20260930T071622Z.log

Validated SHA-256:
- EventCursorRecordV1.scala: c14265ed0bf51104692c0e102287907025a017cecbabaf7c6ac95acf120f7db5
- EventCursorRecordV1Spec.scala: ad0e596d2f43fa0d0d55120d91f60473946fa16732875d1860dd34d59b63984a

The user's simplified parent execution route preserved serial locking, fresh
helper-owned permission metadata and scoped platform review. No acceptance
receipt/full-suite/independent review is claimed. No commit/publication occurred.
Next: host-pinned cursor identity and explicit Create-only bootstrap/guarded
managed composition using this text schema. V2 delivery-key/index codecs and
actual runtime delivery remain open. S12/P2-U15/P2-U16/P2-U05 remain open.
