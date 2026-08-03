# Phase 55 GCF-08J — Opaque Launcher Binding-Envelope Transport

Date: 2026-08-03

GCF-08J proves that `cncf-launcher` and `textus-launcher` treat
`--textus.binding=<envelope>` as an opaque runtime token. The launcher does
not decode, validate, canonicalize, redact, or otherwise assign parameter
semantics to an envelope. Canonical and noncanonical-looking tokens, empty
values, values with extra `=`, and binding-looking command-domain tokens retain
their runtime spelling and order. CNCF runtime remains the only binding
admission owner.

The acceptance specifications cover launcher configuration, packaged or
development target activation where supported, and `--` command-domain
passthrough. They assert the exact token vectors received by fake runtime
invokers. Existing Phase 53 launcher transport specifications were preserved
unchanged.

`cncf-launcher` `Test/compile` plus
`cncf.launcher.Gcf08BindingEnvelopeTransportSpec` passed 1/1 through serialized
invocation `82670-20260803T114638Z`. `textus-launcher` `Test/compile` plus
`textus.launcher.Gcf08BindingEnvelopeTransportSpec` passed 1/1 at
`83387-20260803T114714Z`. CNCF receiver regression (`Test/compile` plus the
argument codec, runtime bootstrap, and runtime boundary specs) passed 15/15 at
`84349-20260803T114804Z`. Independent review is clean.

This slice does not add launcher parameter grammar, diagnostic serialization,
file admission, reload behavior, or raw YAML duplicate-member preservation.
