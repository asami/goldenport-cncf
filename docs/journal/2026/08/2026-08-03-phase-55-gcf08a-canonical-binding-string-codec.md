# Phase 55 GCF-08A — Canonical Binding-String Codec

Date: 2026-08-03

GCF-08A adds a reversible external spelling for a typed configuration parameter
identity and one concrete target without creating a String-keyed configuration
authority. `simplemodeling-lib` owns the target-parametrized reference,
qualifier SPI, and outer `canonical-id | @qualifier:canonical-id` grammar.
CNCF owns the four target qualifier semantics, strict canonical UTF-8 percent
encoding, and catalog admission.

The CNCF codec emits bare canonical ids only for Global, `@c` for component
class, `@s` for Subsystem instance, and `@i` for a fully qualified component
instance. It rejects malformed/non-canonical encodings, invalid/non-NFC
identities, decode-only aliases, unknown canonical ids, and parameter/target
kind combinations not admitted by the catalog. It does not parse configuration
values, query resolution state, or connect environment, arguments, launchers,
or diagnostics.

Validation: generic `Test/compile` and the focused codec suite passed 3/3 at
`87333-20260802T233707Z`; development-local `publishLocal` passed at
`88525-20260802T233849Z`. CNCF `Test/compile` and the focused codec suite
passed 3/3 at `99220-20260802T235141Z`. All SBT invocations used the serialized
runner. Independent review found only P3 test-structure/naming debt; it was
repaired and the generic and CNCF suites passed 3/3 again at
`20981-20260803T001221Z` and `21767-20260803T001311Z`. Final focused re-review
is clean.
