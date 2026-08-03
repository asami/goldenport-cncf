# Phase 55 GCF-08F — Runtime argv Admission

Date: 2026-08-03

GCF-08F activates the admitted `--textus.binding=` boundary only in
`CncfRuntime`. After bootstrap normalization, GCF-08D partitions pre-sentinel
envelopes once. Admitted assignments become typed inputs in the one final
Arguments snapshot batch; envelopes do not become legacy `textus.binding`
compatibility values and do not reach downstream domain argv. The `--` sentinel
and all later tokens remain untouched command-domain text.

The final Arguments source carries test-descriptor defaults below explicit CLI
configuration. Typed envelope inputs share its provenance and collision domain
with ordinary argv configuration. Thus direct and typed values for the same
canonical parameter/target fail structurally, while different targets remain
valid. The resulting candidates participate in execution-profile selection and
the final Subsystem collection admission.

Independent review found that invocation re-bootstrap discarded admitted
assignments, test descriptors could retain multiple Arguments sources, source
normalization rewrote post-sentinel tokens, and direct emulator/script routes
did not forward their original argv to Subsystem initialization. GCF-08F
repairs all four paths: bindings are restored before a necessary re-bootstrap;
descriptor defaults and normalized descriptor path are folded into the one
final Arguments map; source normalization stops at `--`; and all runtime modes
retain raw subsystem argv while parsing residual request argv.

Review-fix `Test/compile` passed through the serialized runner at
`89673-20260803T015121Z`. Focused codec, bridge, runtime projection,
bootstrap, and runtime configuration validation passed 44/44 at
`95214-20260803T015959Z`. A real `initializeForEmbedding` admission regression
passed 5/5 at `96680-20260803T020242Z`.

Final review fixes close command-tail processing and confidentiality: all
framework scans split at `--`, resolver-added activation options insert before
the tail, and parser/client traces redact binding envelopes or omit URL query
values. The final serialized `Test/compile` passed at
`23544-20260803T024750Z`; focused regression passed 45/45 at
`26029-20260803T024933Z`; the final focused re-review is clean.
