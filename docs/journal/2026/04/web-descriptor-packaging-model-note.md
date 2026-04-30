CNCF Web Descriptor Packaging Model (Draft)
===========================================

Overview
--------

Web descriptors are packaged at the subsystem level.

Subsystem is the primary unit of deployment and configuration.

Two forms exist:

- SAR (Subsystem Archive)
- CAR (Component Archive, treated as subsystem when standalone)


1. SAR Structure
----------------

A SAR may contain:

- multiple components (CAR)
- a Web descriptor
- configuration files

Example:

/components/
/web/web.yaml


2. CAR as Subsystem
--------------------

A CAR without SAR is treated as a subsystem.

It may contain:

/web/web.yaml


3. Descriptor Scope
--------------------

Web descriptor applies to all components within the subsystem.

Selectors must be fully qualified.


4. Override Rules
------------------

Priority:

1. SAR descriptor
2. CAR descriptor (fallback)


5. Rationale
------------

- unified deployment model
- scalable from single component to multi-component system
- clean separation between model and configuration


6. Admin Form Schema Update
---------------------------

The Management Console CRUD work introduced a field schema on the Web
Descriptor admin surface.

The reason is practical: create/update/detail pages must not depend only on
sampling existing records. Sampling is useful as a fallback, but it fails for
empty collections and cannot express HTML control choices such as textarea,
select, required, hidden, or system fields.

The direction was refined after the admin field schema was introduced:

- `EntityRuntimeDescriptor.schema` is the primary source for Web-facing fields.
  It is an effective static schema normally built from CML-derived
  `org.goldenport.schema.Schema` on generated entity/value/operation companion
  objects.
- `WebSchemaResolver` is the composition layer that merges domain schema,
  read/list fallback metadata, and WebDescriptor controls before rendering.
- Parallel schema field-list metadata was removed. Missing schema information
  should be handled by extending `org.goldenport.schema.Schema`.
- The first shared Schema extension is `Schema.Column.web`, which carries
  portable Web hints such as control type, required override, hidden/system
  flags, select values, multiple selection, readonly intent, placeholder, and
  help text. `ParameterDefinition.web` now uses the same vocabulary for
  Operation parameters, and CNCF maps both through `WebSchemaResolver`.
- `WebDescriptor.FormControl` accepts the same Web hint fields, so descriptor
  overrides can adjust readonly state, placeholder text, and field help without
  introducing another schema model.
- Web Descriptor `admin.<surface>.<collection>.fields` is a Web UI override for
  field selection, ordering, and presentation, not the primary model schema.
- `controls` reuses the operation form control vocabulary.
- Runtime rendering consumes resolved Web schema, applies Web Descriptor field
  overrides when present, and falls back to read/list inference only when no
  schema is available.
- The implemented CRUD path covers entity and data surfaces first.
- View and aggregate surfaces are still read-oriented, but instance detail pages
  now use resolved backing-entity schema and Web Descriptor fields for display
  ordering. This keeps the same admin surface contract available before editable
  view/aggregate command forms are promoted into the CRUD form pipeline.
- View and aggregate list pages can also use descriptor fields as table columns
  when the read result carries field-shaped item values. The fixed ID/Value table
  remains the fallback for compact read results.
- Aggregate operation forms now accept
  `admin.aggregate.<aggregate>.<operation>.fields` as a fallback control schema
  for the normal operation form route. Explicit `form.<selector>.controls` still
  wins because it is tied to the concrete Web Form endpoint.

Cozy now has a raw CML `# WEB` metadata bridge for generating
`src/main/car/web/web.yaml` in `car-sbt-project`. This is intentionally a bridge,
not a CML semantic extension. The Web Descriptor remains deployment/configuration
data under the CAR metadata tree; Web application HTML and assets remain under
`src/main/web`. The bridge just keeps sample projects convenient until the
Dox/Kaleidox metadata contract is formalized.

If internal metadata is needed later, CAR-wide metadata should use
`src/main/car/META-INF`, while Web-app-internal metadata or private resources
should use `src/main/web/WEB-INF`. The `src/main/web/META-INF` layout is not
adopted; `WEB-INF` better matches Java Web application conventions, and any
future `WEB-INF` content must remain non-public in Web serving.
