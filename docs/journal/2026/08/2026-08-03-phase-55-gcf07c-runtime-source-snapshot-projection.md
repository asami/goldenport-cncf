# Phase 55 GCF-07C Runtime Source Snapshot Projection

Date: 2026-08-03

GCF-07C connects the generic configuration snapshot SPI to the CNCF runtime.
The runtime retains its final physical source sequence as one immutable
`ConfigurationResolutionSnapshot`, with `ResolvedConfiguration` preserved only
as the legacy compatibility projection. Typed catalog candidates are built from
those already-loaded per-source values after a stable `SubsystemInstanceId`
exists; the Subsystem receives the selected user-mode binding before the first
user-mode evaluation.

Source discovery now removes an earlier occurrence of the same physical file
identity, retaining the later higher-precedence source. This prevents a cwd
that is also the project root from producing duplicate canonical candidates.
The projection keeps `.textus` as baseline and `.cncf` as compatibility
override, flattens nested legacy configuration input only to canonical field
paths, and admits one field per spelling within one document.

Validation evidence:

- generic `Test/compile`: `6265-20260802T185501Z` (pass);
- generic development `publishLocal`: `6736-20260802T185545Z` (pass);
- CNCF `Test/compile`: `9773-20260802T190131Z` (pass);
- CNCF focused runtime snapshot/catalog/user-mode suites: 8 succeeded, 0
  failed, `14382-20260802T191024Z`.

Independent review found two provenance defects and their focused fix is part
of this slice: final source-vector deduplication now also covers explicit and
test-descriptor sources, and binding provenance carries `textus`/`cncf` layer
separately from the legacy `file` source type. Generic `Test/compile` passed at
`19659-20260802T191909Z`, development `publishLocal` at
`20154-20260802T192000Z`, and the focused CNCF regression suite passed 9/9 at
`21648-20260802T192253Z`.

The later-explicit-source retention assertion passed in the final focused run
at `26741-20260802T193249Z`; the subsequent independent focused re-review was
clean.

Full suites remain reserved for the Phase 55 release gate. This journal records
implementation evidence only; it does not promote the provisional Phase 55
specification or alter Phase 53 CS-01--CS-07.
