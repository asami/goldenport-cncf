# Canonical Knowledge materialization persistence boundary

Decision date: 2026-10-29. Candidate implementation; validation/review pending.

CNCF owns a generic immutable canonical graph result and native Entity persistence.
Semantic owners supply exact revisioned source/target/context and attributable
decision references. CNCF validates the generic graph and data shape, preserving
its complete typed value; Hub remains the semantic-processing and formation owner.
Workbench remains the common work/correction/review owner. No canonical semantic
Mapping/Interpretation model or second approval state machine is introduced.

The result codec is separate from KnowledgeRecordCodec's display projections.
Those projections intentionally omit or normalize information and cannot be used
as a lossless storage contract. Materialization stores the complete canonical
KnowledgeWorkingSetSnapshot, not operational KnowledgeSpace state/status or
post-load derived node structure. Pure graph validation is extracted from the
existing WorkingSet load boundary and remains backward compatible with it.
Materialization adds stricter closed-graph checks without changing ordinary
KnowledgeSpace behavior for its existing operational callers.

The repository returns ordinary Entity UnitOfWork programs, so the owning runtime
retains admission, datastore binding, storage transactions, native failure taxonomy
and factory authorization. Its configured collection requires a native Detached
revision descriptor. Native create uses a retained framework identity and strict
creation; there is no upsert path. Native content overflow remains the existing
storage responsibility. No custom provider, SQL schema manager or parallel store
is introduced in production.

A native storage envelope carries decoded framework SecurityAttributes separately
from the semantic graph. This preserves row authorization during native search
filtering without permitting a submitted semantic reference to grant access.
Exact source matching and node membership occur after authorized visible-row
selection. The initial bounded repository uses existing native search; it does
not add a secondary index or claim unbounded federation performance.

The owning producer persists a formation identity before invoking create. A native
failure or unobserved acknowledgement is retained as uncertainty; get reconciles
the same identity without a write. Framework persistence does not decide whether
a changed semantic target remains approved. Hub must read/validate the actual
Workbench target/decision and enforce renewed approval before invoking creation.
The source Information remains an external immutable source; this API never
updates or imports it into CNCF InformationSpace.

Consumers register the provided persistent envelope codec through their native
Entity plan and compose this Scala API in public Operations. This change does not
install a new framework component, publish Operations, release a CAR or demonstrate
the consumer route. Source/artifact revisions, local publication and integrated
consumer acceptance retain their ordinary owners.
