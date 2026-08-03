# Phase 55 GCF-08H — Consolidated File Binding Admission

Date: 2026-08-03

GCF-08H makes the documented consolidated file hierarchy an admitted typed
runtime boundary. A retained file snapshot with a `global`, `components`, or
`subsystems` root is projected directly from its immutable object tree to
`ConfigurationDocument`; the existing catalog decoder then constructs Global
and concrete target candidates. No physical source is read again, and existing
flat file sources retain their explicit Global or selected-Subsystem location
interpretation.

The candidate batch preserves the original file source rank, ordinal, identity,
layer, collision domain, and `file` type. Context selection stays in the
generic resolver, so values for another Subsystem do not become the requested
Subsystem binding.

This slice intentionally does not add split-document discovery, alternate file
grammar, launcher transport, diagnostic serialization, or duplicate-key
retention. The retained source representation is already a map, so duplicate
members have been collapsed before this boundary; preserving them requires a
separate generic source-snapshot/parser change rather than a second file read.

`Test/compile` plus `CncfRuntimeConfigurationProjectionSpec`,
`CncfRuntimeSnapshotBootstrapSpec`, and `Phase55RuntimeConfigurationBoundarySpec`
passed 14/14 through serialized SBT invocation `44045-20260803T111116Z`.
Independent review found that hierarchy recognition was initially not restricted
to `ConfigurationSource.File`. The repair gates both the document and location
branches by source kind and proves a hierarchical resource source and a
`global` argument do not gain consolidated semantics. Review-fix validation
passed 15/15 at `54426-20260803T112112Z`; final focused re-review is clean.
