# Component Local Datastore Layout Decision

Date: 2026-08-10
Status: accepted

## Trigger

ArtScene started successfully after canonical Component execution-context
resolution was repaired, but its Web application showed zero exhibitions. The
existing database had not changed schema and did not require an application
data migration.

Inspection showed that the previous ArtScene runtime had used:

```text
~/.cncf/art-scene/application.db
```

That database was healthy and contained 50 facilities, 242 exhibitions, 126
exhibition reviews, and five exhibition period segments. Canonical Component
resolution subsequently supplied `org.simplemodeling.textus.ArtScene` to the
generic datastore binding. The current generic normalization flattened that
qualified identity and selected a different empty database below:

```text
~/.cncf/org-simplemodeling-textus-art-scene/application.db
```

The missing exhibitions were therefore a datastore-path identity regression,
not evidence of an ArtScene schema or import change.

## Considered directions

The first repair direction was to retain the old `art-scene` datastore
identity or add a compatibility fallback. That would restore the existing
database, but it would leave a Web/legacy alias as persistence identity after
the framework had adopted an exact namespace-qualified Component identity.

A flat canonical directory such as
`org-simplemodeling-textus-art-scene` was also rejected. It loses the semantic
boundary between namespace and local ID and creates an independent
normalization convention for persistence.

The discussion then selected a namespace-preserving hierarchy below the CNCF
operational home. The `components` segment reuses the established
component-scoped organization, while a dedicated `datastores` segment keeps
runtime-managed databases distinct from component configuration and other
component-owned files.

## Decision

The canonical default is:

```text
~/.cncf/components/<namespace>/<normalized-local-id>/datastores/<normalized-store-name>.db
```

For ArtScene:

```text
~/.cncf/components/org.simplemodeling.textus/art-scene/datastores/application.db
```

Namespace and local ID come only from the admitted canonical Component ID.
The local ID and store name use their deterministic path projections. Release
version, DEV/LOCAL/PUBLIC catalog source, artifact name, display name, and Web
alias are excluded. The default entity datastore name remains `application`.

No legacy path probing or automatic migration is admitted. Existing data is
moved by an explicit operator action. For this incident, the healthy ArtScene
database was copied to the selected canonical path without deleting the source.
The source and target SHA-256 values matched after the copy:

```text
7df819c97bfb69f2c5baa4e4c21bc20fd932c65be013a15a4d6471d3f9c4baac
```

## Specification boundary

The existing normative Component Identity specification already requires
`namespace + local ID` as the sole identity authority and excludes version,
artifact, route, and presentation metadata. The earlier Textus/CNCF home
decision assigns standalone and development databases to `~/.cncf`, but
explicitly deferred exact operational subdirectories and lifecycle rules.

The resulting normative contract is
`docs/spec/component-local-datastore-layout.md`. This decision records the
reasoning behind that contract; it does not itself redefine precedence or
migration behavior. It does not change explicit datastore-path configuration,
define an instance-scoped datastore hierarchy, authorize automatic
backup/deletion, or claim that the runtime implementation has already adopted
the new path.
