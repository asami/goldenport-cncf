# Phase 55 GCF-08B — Collision-Free Environment Binding-Name Codec

Date: 2026-08-03

GCF-08B adds a CNCF-owned, name-only codec for a typed binding reference. The
canonical prefix is `TEXTUS_BINDING_`; target marker and field arity represent
Global, ComponentClass, SubsystemInstance, or qualified ComponentInstance.
Target identities use canonical UTF-8 uppercase hexadecimal. Canonical
parameter ids use an injective shell-safe token: uppercase letters/digits with
`.` as `_D` and `-` as `_H`.

The codec rejects aliases, unknown ids, target-kind violations, alternate
prefixes, malformed/noncanonical tokens, invalid UTF-8, non-NFC subsystem
labels, and invalid targets. It does not read environment values or add source,
provenance, candidate, resolution, runtime, argument, or launcher authority.

Validation: focused environment/string/catalog suites passed 9/9 at
`34264-20260803T002645Z`. Independent review requested explicit `_H`
non-collision coverage and a later re-review moved one action into `When`.
The repaired suite passed 9/9 at `42249-20260803T003550Z`; final focused
re-review is clean. All SBT invocations used the serialized runner.
