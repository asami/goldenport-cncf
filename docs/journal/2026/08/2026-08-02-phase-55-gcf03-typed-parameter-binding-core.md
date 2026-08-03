# Phase 55 GCF-03 Typed Parameter and Binding Core

Date: 2026-08-02

Status: complete; independent review and focused re-review clean

GCF-03 implements the generic `CanonicalParameterId`, strict typed value
codecs, `ConfigurationParameter`, bounded `ConfigurationProvenance`, immutable
candidate/effective bindings, candidate multiplicity, effective collection
uniqueness, and exact-witness typed lookup. All construction failures use the
existing structured `Consequence.configurationInvalid` taxonomy.

CNCF now has a validated `SubsystemInstanceId` and exactly four construction
targets: `Global`, `ComponentClass`, `SubsystemInstance`, and a containing-
Subsystem-qualified `ComponentInstance`. No unqualified target exists.

The provenance evidence decision is explicit: retain the first 16 entries,
cap each at 256 UTF-16 code units without splitting a surrogate pair, and
record omitted entry count. This is not a diagnostic trace projection.

Review-fix makes target identities unambiguous: `SubsystemInstanceId` allows
only NFC-normalized Unicode letter/digit/`_`/`-` labels and no path separators
or dot segments; ComponentInstance target admission requires raw labels already
in the existing canonical component-label grammar. Therefore no distinct input
spellings become the same admitted target identity.

The first independent review found canonical target identity, E4 traceability,
and checklist-status findings; review-fix corrected them. The first focused
re-review then found the required generic Collection idiom, and its repair added
typed canonical `empty` values plus zero-argument `apply()` factories for both
binding collections. The final focused re-review was clean. GCF-03 is complete;
GCF-04 remains responsible for source snapshots and candidate construction.

Focused evidence passed:

- generic compile and `ConfigurationBindingCoreSpec`: 9 succeeded;
- generic GCF-02 contract: 3 succeeded, 3 intentional deferred scenarios;
- CNCF clean compile and `CncfConfigurationTargetSpec`: 2 succeeded;
- CNCF GCF-02 contract: 4 succeeded, 4 intentional deferred scenarios.

GCF-04 through GCF-07 remain responsible for source loading, document and alias
decoding, duplicate-source policy, resolution/precedence, trace projection,
catalog adoption, and fixed-user integration. Phase 53 and Phase 54 behavior
remain unchanged.
