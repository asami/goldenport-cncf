# UnitOfWork Program Planning and Deterministic Recording

Status: normative design

## Purpose and boundary

This design freezes the consumer-side foundation for the retained Cozy Phase
47.2 delivery unit and CNCF Phase 64.2 ACP-01 / UTP-01 / UTP-03 / UTP-04 /
UTP-05 handoff. It records the current `UnitOfWorkOp` inventory, defines its
metadata-independent base effect classes, and specifies the smallest planning
and deterministic-recording contracts needed by later work.

The canonical executable-intent algebra is `UnitOfWorkOp[A]`. Its Free/UoW
program shapes remain:

```text
ExecProgram[A] = Program[UnitOfWorkOp, A]
ExecUowM[A]    = UowM[UnitOfWorkOp, A]
```

This design does not introduce `ActionOp` or any other parallel algebra. The
Phase 47.2.1 compiler/generated ABI and the Phase 47.2.2 fixture and execution
acceptance consume this foundation later; they are not defined here.

## Base effect classification

The following classes describe the operation constructor itself, independently
of CML or generated metadata:

- **Control**: an execution-control or evaluation step with no provider effect
  implied by the constructor.
- **Local**: an operation against CNCF/component-owned state or resources,
  including local reads, local mutations, and local resource resolution.
- **External**: an operation whose canonical intent crosses an HTTP, shell, or
  process boundary.

These are base planning classes, not claims about transaction capability,
reversibility, durability, idempotency, compensation, or deployment. A local
operation may use a provider internally; that does not turn the constructor
into a new external operation class. Later generated metadata may refine
planning, but it must not replace or silently reinterpret this base
classification.

## Current `UnitOfWorkOp` inventory

The inventory below is exhaustive for the current algebra. Names and generic
result types are taken from
`src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala`.

| Family | Current operation occurrences | Base class |
| --- | --- | --- |
| Authorization / supplemental evaluation | `Authorize`; `StageOperationEvaluationSupplemental` | Control |
| HTTP / shell / process | `HttpGet`; `HttpPost`; `HttpPostBag`; `HttpPut`; `ShellCommandExec`; `ProcessExec` | External |
| DataStore | `DataStoreLoad`; `DataStoreSave`; `DataStoreDelete` | Local |
| Embedded datastore and local data resource | `LocalDataDir`; `EmbeddedDataStoreOpen`; `EmbeddedDataStoreRead`; `EmbeddedDataStoreUpdate`; `EmbeddedDataStoreMigrate` | Local |
| EntityStore | `EntityStoreCreate`; `EntityStoreClaimOrLoad`; `EntityStoreUpsert`; `EntityStoreLoad`; `EntityStoreLoadSnapshot`; `EntityStoreLoadDetached`; `EntityStoreLoadDirect`; `EntityStoreSave`; `EntityStoreSaveDetached`; `EntityStoreSaveManaged`; `EntityStoreSaveUnversioned`; `EntityStoreUpsertUnversioned`; `EntityStoreUpdate`; `EntityStoreUpdateDetached`; `EntityStoreUpdateById`; `EntityStoreUpdateByIdObserved`; `EntityStoreUpdateByIdDetached`; `EntityStoreConditionalTransition`; `EntityStoreUpdateUnversioned`; `EntityStoreUpdateByIdUnversioned`; `EntityStoreDelete`; `EntityStoreRestore`; `EntityStoreDeleteHard`; `EntityStoreSearch`; `EntityStoreSearchDirect`; `EntityStoreSearchInternal`; `EntityStoreUniqueValueExists`; `EntityStoreResolveIdentity` | Local |
| Blob / content | `BlobNormalizeInlineImages`; `BlobAttachInlineImages`; `ContentNormalizeReferences`; `ContentAttachReferences`; `ContentValidateReferences`; `ContentSyncInlineReferences`; `ContentRenderHtml` | Local |

`EntityStoreConditionalTransition` is package-private but is still part of the
current algebra and is included in the inventory. The table classifies
operations by executable intent, not by the visibility of the case or by the
metadata carried in its fields.

## Minimal additive planner contract

The planner consumes an `ExecUowM[A]`/`ExecProgram[A]` structure and records
the operation occurrences in the program's causal traversal order. It must
preserve every occurrence, including repeated occurrences of the same
constructor, and must not reorder, deduplicate, or synthesize operations.

For this foundation, the planner emits an ordered sequence of two segment
shapes:

1. a **local atomic segment** containing a contiguous ordered run of Control
   and Local occurrences; and
2. an **isolated external boundary** for an External occurrence, preserving
   its position in the sequence and preventing local occurrences on either
   side from being silently merged through it.

The segment shape is an inspectable planning boundary. It does not promise a
database transaction, distributed atomicity, rollback, or any provider
capability. The planner may represent an empty or single-occurrence segment;
it must not infer additional effects from operation arguments.

The planner contract intentionally does not infer any of the following from a
`UnitOfWorkOp`:

- two-phase commit (2PC) or any other distributed commit protocol;
- compensation, compensation persistence, or recovery ordering;
- idempotency or retry identity;
- CML action identity or provenance;
- generated metadata, source/model identity, or ABI version; or
- production execution policy, provider selection, or deployment behavior.

Those meanings await the later CML contract and acceptance phases. They may be
attached as explicit inputs to a later planner contract, but they are not
properties of this base inventory or of the operation constructors alone.

## Deterministic recorder contract

The later test/runtime implementation shall provide a deterministic recorder
for an `ExecUowM[A]`. The recorder is a test boundary, not a production
interpreter. Its contract is:

- traverse the supplied program and capture each visited `UnitOfWorkOp`
  occurrence in traversal order;
- assign a stable occurrence ordinal from that traversal, so repeated equal
  constructors remain distinct;
- require the caller to supply a typed result stub for each occurrence (or a
  typed structured failure for that occurrence), and use that stub as the
  operation result;
- allow the caller to select an occurrence ordinal for deterministic failure
  injection, with the selected failure replacing that occurrence's supplied
  result; and
- return the program's typed final result or structured failure together with
  the ordered occurrence record.

The recorder must be able to traverse an `ExecUowM` using only the supplied
typed results and failure selection. It must not construct or invoke production
drivers, perform database/filesystem/network/process I/O, consult ambient
runtime state, or add hidden retries, compensation, or rollback. A branch is
recorded according to the deterministic result supplied for the preceding
occurrence; unvisited branches produce no occurrences.

The occurrence record is limited to the operation value, ordinal, base effect
class, and structured outcome needed for inspection. CML identity, provenance,
idempotency, and production correlation are absent until a later explicit
contract supplies them.

## Scope and follow-up boundary

This design satisfies the retained Phase 47.2 consumer foundation and the
corresponding CNCF UTP-01/03/04/05 boundary. Phase 47.2 delivers the
classifier, planner, and deterministic recorder foundation defined above. It
does not implement the CML compiler or generated ABI, the representative
fixture or composed execution acceptance, 2PC, compensation persistence, or
production-interpreter alignment. Those concerns remain excluded from Phase
47.2 and are assigned to Phase 47.2.1, Phase 47.2.2, and the later CNCF Phase
64.2 UTP items identified by that phase's work ledger.

## References

- [CNCF Phase 64.2](../phase/phase-64.2.md)
- [Cozy Phase 47.2](../../../cozy/docs/phase/phase-47.2.md)
- [Cozy Phase 47.2.1](../../../cozy/docs/phase/phase-47.2.1.md)
- [Cozy Phase 47.2.2](../../../cozy/docs/phase/phase-47.2.2.md)
- [Free & UnitOfWork Execution Model](free-unitofwork-execution-model.md)
- [UnitOfWork resource lifecycle](unit-of-work-resource-lifecycle.md)
- `../../src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala`
