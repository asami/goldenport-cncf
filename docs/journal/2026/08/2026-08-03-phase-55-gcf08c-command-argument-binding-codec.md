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

Focused argument/string/environment/catalog validation passed 12/12 at
`46388-20260803T004232Z` through the serialized runner. Independent review is
clean; focused re-review remains the final local gate.
