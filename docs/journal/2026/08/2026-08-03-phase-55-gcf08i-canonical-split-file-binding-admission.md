# Phase 55 GCF-08I — Canonical Split-File Binding Admission

Date: 2026-08-03

GCF-08I admits only the canonical Textus split-file paths below a `.textus`
configuration directory. ComponentClass, SubsystemInstance, and nested
ComponentInstance `config.yaml` files are enumerated in deterministic path
order, retained as the existing File source snapshots, and decoded through
their path-derived typed document locations. No source is re-read.

A recognized split path always has flat, path-bound semantics. A hierarchy-like
`global`, `components`, or `subsystems` key in its content cannot replace that
path-derived location or inject a different target. The canonical
`.textus/config.yaml` consolidated document and recognized canonical split
files share a collision domain at one origin/rank; a duplicate canonical
parameter/target is therefore a structured failure. Ordinary retained files,
including `.cncf/config.yaml`, keep their original source identity and normal
Textus-to-CNCF precedence.

`Test/compile` plus `CncfRuntimeConfigurationProjectionSpec`,
`CncfRuntimeSnapshotBootstrapSpec`, and
`Phase55RuntimeConfigurationBoundarySpec` passed 17/17 through serialized SBT
invocation `67216-20260803T113240Z`. Independent review found that the initial
collision domain was too broad and that hierarchy-shaped split content could
retarget the document. The repair restricted canonical collision scope and
made recognized split paths unconditionally path-bound. Review-fix validation
passed 19/19 at `72734-20260803T113800Z`; focused re-review is clean.

This slice does not admit alternate names/extensions, launcher transport,
diagnostic serialization, source reloads, or raw duplicate YAML-member
retention. The last item remains generic parser/source-snapshot work.
