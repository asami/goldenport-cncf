# Phase 55 GCF-08C — Command-Argument Binding Codec

Date: 2026-08-03

GCF-08C adds the pure atomic argument envelope
`--textus.binding=<canonical-reference>=<raw-value>`. It delegates the
reference entirely to the canonical GCF-08A codec and preserves the raw suffix
after its first delimiter exactly, including empty values and extra `=`.

This boundary does not type-decode, log, normalize, or otherwise inspect raw
values. It does not scan an argv collection, mutate the runtime argument map,
assign provenance, construct candidates, resolve a value, or forward launchers.
Those adoption steps remain later GCF-08/GCF-09 work.

> Local execution archive: `~/backup/journal/2026/11/cloud-native-component-framework/cncf-journal-cleanup-20261116/a-46519ec7-ea0d-401b-ac84-b5c5c87060f6/docs/journal/2026/08/2026-08-03-phase-55-gcf08c-command-argument-binding-codec.md.gz`
