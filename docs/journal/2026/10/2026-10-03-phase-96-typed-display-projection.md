# Phase 96 P96-03B1 typed Display projection

Date: 2026-10-03. Slice: PHASE-96 / P96-03 / P96-03B1.
Authority: frozen parent Implementation Manifest
`phase96-p96-03b1-implementation-manifest-001` revision 1.
Initial status: authored implementation with parent validation PENDING; the
authored/unexecuted sections below preserve that historical handoff. Current
historical parent validation is recorded in the appended result section.
No independent B1 acceptance, Step acceptance, review result or LUI receipt
is claimed.

## Bounded implementation and rationale

The [runtime contract](../../../spec/display-model-runtime.md#p96-03b1-explicit-typed-row-projection)
and [selected design](../../../design/display-model-runtime.md#p96-03b1-pure-typed-projection-registration)
bind already-authorized immutable application `V` and `Vector[V]` to explicit
typed resource/value/action functions. Screen-specific registrations let ESS
List and Detail share a View key with different exact output sets. Grouped
fan-out preserves explicit positional output keys, declaration order and key
reuse, without deriving an inverse mutation mapping.

[DisplayProjection](../../../../src/main/scala/org/goldenport/cncf/display/DisplayProjection.scala)
admits registrations without invoking them, compiles deterministic callback
order and assembles complete candidates before using unchanged Display
validation. Callback failures stop later work; semantic validation can reject
the completed candidate. Fixed diagnostic reasons redact application input
and exception text. Pure closures remain an application obligation, and
availability is presentation data, never authority or operation execution.

The external [typed fixture](../../../../src/test/scala/logicalui/TypedDisplayProjectionFixture.scala)
uses explicit managed development resources and an opaque semantic status
trunk interpreted by pattern matching. It consumes the unchanged hand-written
expected-generated ESS source; it is not production Entity admission or actual
Cozy output. The six P96-03A accumulator paths remain preserve-only.

## Executable authoring map — unexecuted

Every scenario has adjacent Given setup, When interpretation and Then semantic
expectations. The following names identify executable cases, not test results.

| ID | Suite and behavior | Given / When / Then authoring evidence |
| --- | --- | --- |
| E1 | [DisplayProjectionSpec](../../../../src/test/scala/org/goldenport/cncf/display/DisplayProjectionSpec.scala): ESS Detail | Typed Capture and admitted ESS / repeat projection and availability interpretation / exact fields/resource, equal model and independent semantic summary |
| E2 | Same suite: List/select/Detail | Shared View, separate registrations, reversed rows / List then typed row Detail / order, bytes, target, snapshot and no eager Detail |
| E3 | Same suite: five native kinds | Native typed members and explicit Missing / typed closures / exact Unicode/whitespace/decimal scale/instant precision and all five kinds |
| E4 | Same suite: grouped opaque/native fan-out | Reversed output keys and repeated keys/scopes / Detail and List / declared field order, once per row/group/scope and separate action inputs |
| E5 | Same suite: empty containers | Empty sections/fields/actions and screen action / empty List and empty Detail shapes / original empty vector, screen evaluation and preserved empty containers |
| E6 | Same suite: pure admission | Missing/extra/duplicate/invalid registrations and counted callbacks / factories and selection / typed codes/paths, exact registration and zero callback evaluation |
| E7 | Same suite: null boundaries | Factory/vector/entry/key/function/source/snapshot/callback nulls / independent admission/projection cases / indexed diagnostics and pre-callback rejection |
| E8 | Same suite: callback failure | Reversed registrations and failing resource/value/row/screen actions / structured or throwing callback / original taxonomy/cause or redacted failure, ordered short-circuit, no retries/later rows |
| E9 | Same suite: output/runtime validation | Too few/many/null/wrong-kind/role/payload outputs / projection and direct validator / before-zip rejection and unchanged final Display diagnostic |
| E10 | Same suite: identity spaces | One EntityId at two revisions, independent provenance/snapshot / duplicate and single-row List / duplicate rejection and exact separate version spaces |
| E11 | Same suite: public typing | Lexically external typed fixture / actual compile probes and valid typed calls / no wrong-V/result/construction/copy/arbitrary-kind bypass, observable models |
| P1 | [DisplayProjectionPropertySpec](../../../../src/test/scala/org/goldenport/cncf/display/DisplayProjectionPropertySpec.scala): generated rows/order/bytes/revisions | Total typed native generators / repeated List and Detail / equality, independent unchanged semantics, five kinds, distinct resources, exact order/payloads; passed and >=100 successes required |
| P2 | Same suite: permutations/fan-out/shared keys | Independent field/output/registration/action permutations and zero/single/multiple rows / projection plus repeated bad kind/count / deterministic order/count/shared keys and stable structured failure; passed and >=100 successes required |

## Parent validation handoff — PENDING

The parent-selected focused command covers exactly DisplayProjectionSpec,
DisplayProjectionPropertySpec, GeneratedLogicalUiAbiSpec,
GeneratedLogicalUiAbiPropertySpec, DisplayModelSpec, DisplayModelPropertySpec
and ComponentFactoryLogicalUiBootstrapSpec. The worker ran no tests, SBT,
self-review, diff validation or runtime command. Parent mechanical checks,
Test compilation, all behavioral cases, each property's >=100 successful
cases, terminal exit and shared-lock release remain PENDING.

B2 canonical contextual Browser/View integration, complete typed late joining
and independent semantic calls remain required. C actual Cozy-generated source
acceptance and LUI-ABI-01/LUI-GEN-01/LUI-ACCEPT-01 remain required. Neither this
pure-row fixture nor the journal closes any canonical P96-03 checklist item or
weakens later wire/service/mutation/context obligations. No new Hygiene or
Development Candidate finding, ledger reset, epoch or authority change occurs.
Existing HYG-P96-DOC-001 and HYG-P96-02-001 dispositions are retained.

## Historical parent focused validation result

The attributable parent execution `phase96-p96-03b1-test-002` subsequently
passed the exact seven-suite command described above: 67 successful tests,
including E1..E11 and P1/P2 with at least 100 successful generated cases each.
SBT exit was 0, wrapper exit was 0, and the shared lock was released. Two
pre-review authoring repairs preceded this successful execution: first the
finite permutation generator, then compile-time type qualification. This
updates the earlier authored/unexecuted status as historical evidence; it
does not issue independent B1 acceptance or reset repair accounting.

B2 now authors the canonical contextual Browser join and late component
publication in the [contextual runtime journal](2026-10-03-phase-96-contextual-display-runtime.md).
Its own parent validation and review remain PENDING. C actual producer
acceptance and LUI-ABI-01/LUI-GEN-01/LUI-ACCEPT-01 remain PENDING. The next parent
PLAN prepares the LUI-ABI-01 handoff from completed A/B evidence before actual
C work. The full P96-03 checklist and later service/wire/mutation/context
obligations remain unchanged.
