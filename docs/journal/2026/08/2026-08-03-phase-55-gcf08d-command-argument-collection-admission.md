# Phase 55 GCF-08D — Command-Argument Collection Admission

Date: 2026-08-03

GCF-08D scans a supplied argv vector into a non-authoritative result. Before
the `--` sentinel, atomic GCF-08C binding assignments are admitted in encounter
order and duplicate canonical `(parameter,target)` pairs fail structurally.
The same parameter at different targets remains valid. Ordinary pre-sentinel
tokens, the sentinel, and all post-sentinel tokens remain residual text in
their original order; post-sentinel binding-looking text is not interpreted.

The adapter does not mutate runtime argv, inspect values, create source or
provenance records, construct candidates, resolve bindings, or forward
launchers.

Focused argument/string/environment/catalog/runtime-boundary validation passed
14/14 at `52598-20260803T005122Z`. Review strengthened malformed post-sentinel
and pair-key coverage; final validation passed 16/16 at
`55883-20260803T005552Z`, and focused re-review is clean.
