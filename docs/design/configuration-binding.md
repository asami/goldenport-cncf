# CNCF Configuration Binding Design

status = normative
phase = Phase 55 / GCF-10A
scope = Textus catalog, typed admission, target-aware resolution, and consumer projections

This document is the dedicated normative CNCF design for the implemented
configuration-binding boundary. The generic typed identities, codecs, source
snapshots, candidates, resolver, and trace primitives remain owned by
`simplemodeling-lib`; this document defines the CNCF catalog and runtime
admission around those primitives.

## Closed catalog and targets

The CNCF catalog is closed. Each canonical `textus.*` parameter has one
registered generic parameter witness. Established `textus.runtime.*`,
`cncf.*`, and `cncf.runtime.*` spellings are decode-only aliases only where the
catalog registers them. Unknown catalog members presented inside a
catalog-owned/consolidated document, malformed values, wrong target scopes, and
a canonical spelling colliding with one of its aliases fail structurally at
admission. At a generic flat source boundary, unrelated or unregistered keys
are filtered or preserved outside CNCF binding admission rather than being
reinterpreted as catalog members or causing catalog rejection. The catalog owns
canonical names, aliases, codecs, and allowed targets; it does not infer an
unregistered parameter from input text.

The initial semantic target set is exactly:

- `Global`;
- `ComponentClass`;
- `SubsystemInstance`; and
- fully qualified `ComponentInstance` (the containing
  `SubsystemInstanceId` together with the `ComponentInstanceId`).

An unqualified document form receives its target from the owning document
location. It is not a fifth semantic target, and the generic resolver does not
infer CNCF target meaning.

## Snapshot admission and candidates

`ConfigurationResolver.resolveSnapshot` is the raw source boundary. It loads
each selected physical source once and returns an immutable
`ConfigurationResolutionSnapshot` containing the compatibility
`ResolvedConfiguration` projection and retained per-source values in its
`sources` field. CNCF typed admission uses that retained `sources` field, never
the merged `ResolvedConfiguration` projection.

CNCF typed admission decodes the retained source values through the closed
catalog into immutable `ConfigurationBindingCandidate` values. Candidate
construction derives complete provenance from the retained snapshot and does
not use a merged `ResolvedConfiguration` as typed input, retain a loader, or
reread a physical resource. Input spelling/path, source identity, layer,
source rank, and source ordinal are retained only as the bounded provenance
needed by the generic binding contract.

Admission rejects duplicate canonical parameter/target identities in one
collision domain, including collisions that arrive through consolidated and
target-tree-split documents. Canonicalization and decode-only alias handling
occur before binding construction; a compatibility spelling is never a second
semantic authority.

## Target-aware resolution

The CNCF adapter supplies an ordered target context to the generic resolver.
Within one retained source, the most-specific eligible target is the sole
winner; same-source specificity ties fail. Source winners are then folded by
`(sourceRank, sourceOrdinal)` precedence, and each later winner records the
previous effective binding in its immutable `overridden` chain.

Resolution publishes one final target-aware collection with one effective
binding per canonical parameter. Candidate alternatives remain admission data;
they are not a consumer-facing authority. The collection and its typed lookup
require the original parameter witness, so equal textual ids with substituted
witnesses cannot reinterpret a value.

## Consumer projections and diagnostics

Runtime and component consumers receive narrow value-only projections selected
from the final typed collection. They do not receive raw sources,
`ResolvedConfiguration`, candidate collections, aliases, source loaders,
provenance, or trace authority, and they cannot reload or open a resource to
explain a value. Initialization and operation-time access retain their existing
owner boundaries; this design does not add a general object-graph merge layer.

`ConfigurationBindingTrace` and the CNCF serialized diagnostic codec are
derived only from the effective collection and its bounded override chain.
Confidential current and historical values are redacted, visible values use
the registered codec, and source identity/evidence are bounded. Serialized
diagnostics accept only the sanitized resolved trace; they own no source,
loader, or resource handle and cannot reload or open resources. Diagnostics are
a projection for explanation, never a second resolution authority.

## Fixed-user identity continuity

For standalone execution, admitted `StandaloneUserProfile` values from canonical
Textus HOME and compatibility CNCF HOME are projected into the same typed
candidate collection and resolved once before Subsystem binding. If that final
fixed-user binding history contains more than one distinct id, admission fails
with a structured configuration-invalid conclusion. The conclusion directs the
operator to explicit data migration or an isolated datastore and never exposes
an identity value, profile path, or descriptor `local_subject` value.

The resolver never moves data, selects a datastore, creates a marker, or
automatically migrates any record. A clean isolated datastore with one effective
new identity is admissible. In fixed/standalone execution with zero admitted HOME
profiles, descriptor `security.authentication.local_subject.id` supplies a
bounded, non-secret default fixed-user candidate. Any admitted HOME profile is
the sole explicit authority and suppresses the descriptor fallback; conflicting
HOME identities still fail before the fallback can be considered. The fallback
does not apply to authenticated or multi-user execution.

## Compatibility and ownership boundaries

`ResolvedConfiguration` remains a raw compatibility projection for explicit
pre-admission or transport boundaries. It is never the final typed authority
and is never merged back into the retained-source candidate collection.

Physical source discovery, file/resource ownership, environment and argument
transport, launcher envelopes, migration adapters, and serialized diagnostic
transport remain at their owning boundaries. Compatibility is decode-only and
must enter the closed catalog before typed admission. This design does not
change source discovery, add new semantic targets, or claim behavior not
covered by the implemented binding and runtime specifications.
