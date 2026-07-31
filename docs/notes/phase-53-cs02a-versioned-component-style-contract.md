# Phase 53 CS-02A: Versioned ComponentStyle Contract

Date: 2026-07-31

This implementation note records the first, runtime-owned slice authorized by
`P53-CS02-SCHEMA-001`.  It is a contract note, not a new extension mechanism.

## Catalog

The framework owns one packaged catalog at
`META-INF/cncf/component-style-catalog.json`.  Its envelope is canonical and
closed: `apiVersion`, `kind`, `provider`, `capabilityBundles`, and
`componentStyles`, in that order.  The only initial provider is `cncf`; the
only accepted API version is `cncf.textus/v1`.

The initial style is `full-fledged-with-standalone@1`.  Its closed empty
parameter schema is exactly an object with empty `properties` and `required`,
and `additionalProperties: false`.  It declares `domain.full@1`, directly
provides `user.fixed-context-compatible@1` and `user.multi-user@1`, and
requires the four canonical datastore/current-user-context capabilities.
`domain.full@1` expands deterministically to its nine domain capabilities.

Catalog identities are not UniversalIds: their `@major` version suffix and
hyphenated names are catalog syntax, whereas UniversalId labels use a different
transport grammar. A catalog identity part is ASCII `[a-z0-9-]+`. A style is
either `name@major` or `family.name@major`; a capability, bundle, or Subsystem
capability is exactly `family.name@major`; and `major` is the canonical decimal
form of a positive signed 32-bit Scala `Int` (`1` through `2147483647`).
Framework and Cozy apply this one grammar to the same catalog bytes.

## Rules

- CS02A-R1: the packaged catalog envelope and provider are closed and
  framework-owned.
- CS02A-R2: every catalog identity uses the shared ASCII and positive-Int
  canonical grammar.
- CS02A-R3: every descriptor-v2 snapshot is deterministic and exactly matches
  its resolved catalog definition.

## Descriptor schema v2

A descriptor whose numeric `schemaVersion` is `2` must carry one complete
`componentStyle` snapshot.  Its canonical field order is `apiVersion`,
`provider`, `id`, `version`, `parameterSchema`, `parameters`, `provides`, and
`requires`.  `provides` orders `bundles`, `capabilities`, and `effective`;
`requires` contains `subsystemCapabilities`.  Every identity vector is unique
and lexically ordered by canonical identity.  The snapshot must match the
packaged catalog exactly, including its computed effective capabilities.

Pre-v2 descriptors remain decodable for compatibility, but
`requireComponentStyleSnapshotC` rejects them.  Therefore newly generated
style-less descriptors are not valid for the v2 admission path.

No Component mode, fixed user, principal, locale, or datastore-policy value is
introduced here.  Those belong to later Phase 53 slices.
