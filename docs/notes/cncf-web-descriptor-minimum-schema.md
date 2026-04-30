# CNCF Web Descriptor Minimum Schema

status = implemented

## Purpose

This note defines the minimum WEB-03 Web Descriptor schema for Phase 12.

The descriptor is subsystem-scoped deployment/configuration data. It is not CML
and does not redefine Component / Service / Operation semantics.

## Location And Loading

The canonical packaged location is:

```text
/web/web.yaml
```

For a SAR, the descriptor applies to the whole subsystem. For a standalone CAR
treated as a subsystem, the same location may be used inside the CAR.

Loading precedence for Phase 12 is:

1. explicit runtime configuration override
2. SAR `/web/web.yaml`
3. standalone CAR `/web/web.yaml`
4. built-in defaults

The intended override key is:

```text
textus.web.descriptor=<path>
```

The first runtime hook is `WebDescriptor.load(path)`. It accepts either an
explicit descriptor file path or a descriptor root directory. When a directory is
given, the loader resolves `web/web.yaml` below that root.

`WebDescriptorResolver.resolve(configuration)` reads `textus.web.descriptor`
through `RuntimeConfig.getString` and loads the descriptor when configured. If no
descriptor path is configured, it returns `WebDescriptor.empty`.

`HttpExecutionEngine` resolves the descriptor from the subsystem configuration.
The Static Form App renderer and http4s `/form/...` routes use it to filter form
index entries, operation form pages, form submissions, result pages, and
continuation pages.

The first Web Tier authorization gate is applied to `/form/...` operation
access. It evaluates descriptor `authorization` entries against role, scope, and
capability values supplied by request headers or query parameters. The supported
minimum key names are `role(s)`, `scope(s)`, `capability/capabilities`, plus
`x-cncf-*` and `x-textus-*` header variants.

Authorization is framework-enforced. Component, service, and operation
definitions should declare policy; normal Operation implementations should not
need to inspect `production`/`develop` mode or anonymous-user settings. The
canonical Operation policy is `OperationAuthorizationRule`, supplied by the
Operation definition or generated/adapted from CML or component descriptors.
The Web Descriptor `authorization` entry is only the Web-facing override or
supplement for the same selector-level policy. It can constrain operation
modes, allow anonymous access, and constrain the modes in which anonymous access
is allowed:

```yaml
web:
  authorization:
    notice-board.notice.post-notice:
      allowAnonymous: true
      anonymousOperationModes: [develop, test]
      capabilities: [notice.post]
```

`apps` is connected to `/web/...` HTML app rendering. If no app entries are
configured, the built-in HTML apps remain available for compatibility. If app
entries are configured, `/web/...` rendering uses them as an allow-list by app
name or path.

SAR/CAR archive discovery uses the same `web/web.yaml` convention. The loader
can read the descriptor from a directory root or from `.sar`, `.car`, and `.zip`
archive files. Runtime resolution first honors the explicit
`textus.web.descriptor` override, then falls back to the loaded subsystem
descriptor path when it is available.

## Minimal Shape

```yaml
web:
  expose:
    notice-board.notice.search-notices: public
    notice-board.notice.post-notice: protected

  auth:
    mode: none

  authorization:
    notice-board.notice.post-notice:
      roles: ["moderator"]
      scopes: ["notice:write"]
      capabilities: ["notice.post"]

  form:
    notice-board.notice.search-notices:
      enabled: true

  admin:
    entity.notice:
      totalCount: optional
      fields:
        - id
        - title
        - name: body
          type: textarea
          required: true
    data.audit:
      totalCount: required
      fields: id action actor
      controls:
        action:
          type: select
          values: [created, updated]

  apps:
    - name: manual
      path: /web/manual
      kind: manual
    - name: console
      path: /web/console
      kind: console
```

Static Form Web app entries may omit convention-derived fields. When only
`name` is supplied, the runtime completes the entry as:

```yaml
name: notice-board
kind: static-form
root: /web/notice-board
route: /web/{component}/notice-board
path: /web/notice-board
```

The completed descriptor can be inspected from the Management Console at
`/web/system/admin/descriptor`.

## Selectors

Operation selectors use the normalized logical form:

```text
component.service.operation
```

Selectors are fully qualified at the descriptor surface. Shortcuts may be added
later, but the minimum schema keeps the descriptor unambiguous.

## Exposure Levels

The minimum exposure levels are:

- `internal`: not exposed through Web paths; this is the default.
- `protected`: exposed through Web paths and requires authentication.
- `public`: exposed through Web paths without authentication.

Resolution priority, when broader scopes are added later, is:

```text
operation > service > component > default
```

Phase 12 minimum support may start with operation-level entries only.

## Authentication

The minimum `auth.mode` values are:

- `none`
- `session`
- `token`
- `hybrid`

`none` is acceptable for local demos and public-only surfaces. `protected`
operations require an authenticated identity when auth enforcement is enabled.

## Authorization

The Web Descriptor authorization section is a Web Tier gate. It does not replace
the existing CNCF operation/entity authorization model.

Minimum keys per operation selector are:

```yaml
authorization:
  notice-board.notice.post-notice:
    roles: ["moderator"]
    scopes: ["notice:write"]
    capabilities: ["notice.post"]
```

The minimum interpretation is:

- `roles`: required subject role names.
- `scopes`: required token/session scope names.
- `capabilities`: required CNCF subject capability names.

For Phase 12, multiple entries within one key are evaluated as OR by category
and AND across configured categories:

- if `roles` is present, the subject must have at least one listed role.
- if `scopes` is present, the subject must have at least one listed scope.
- if `capabilities` is present, the subject must have at least one listed
  capability.
- if more than one category is present, each present category must pass.

Example: `roles + scopes` means a matching role and a matching scope are both
required.

This section only controls Web Tier admission to an exposed operation. After
admission, the operation still executes through the normal CNCF path. Entity and
UnitOfWork authorization remain governed by CML `ACCESS` settings, operation
metadata, `SecuritySubject`, relation rules, natural ABAC conditions, and
owner/group/other permissions.

Operation authorization fields use the same vocabulary as the canonical rule:

- `operationModes`: allowed operation modes for the selector.
- `allowAnonymous`: whether anonymous subjects may invoke the selector.
- `anonymousOperationModes`: operation modes in which anonymous invocation is
  allowed when `allowAnonymous` is true.

For Web routes, these fields are evaluated before dispatch and are then checked
again at the Operation checkpoint when the target Operation supplies an
authorization provider. This keeps command, server, client, REST, and Web/Form
roots aligned.

The Web Descriptor must not define entity relation rules, natural ABAC
conditions, DAC-style owner/group/other permissions, or service-internal/system
access modes. Those remain in the existing security model.

## Form API Control

`form.<operation>.enabled` controls whether the JSON Form API and plain HTML
FORM entry are available for that operation.

The stable JSON response contract for Form API definition endpoints is defined
in `docs/design/web-form-api-schema.md`. This note keeps the WebDescriptor
configuration side of that contract.

The operation form definition endpoint is:

```text
GET /form-api/{component}/{service}/{operation}
```

It uses `WebSchemaResolver.resolveOperationControls` and returns the same
resolved field contract used by the corresponding HTML page under
`/form/{component}/{service}/{operation}`. `POST /form-api/...` remains the
direct Operation-response route; it is not the schema-definition endpoint.

Management Console form definition endpoints use the same JSON field contract:

```text
GET /form-api/{component}/admin/entities/{entity}
GET /form-api/{component}/admin/entities/{entity}/{id}/update
GET /form-api/{component}/admin/data/{data}
GET /form-api/{component}/admin/data/{data}/{id}/update
GET /form-api/{component}/admin/views/{view}
GET /form-api/{component}/admin/aggregates/{aggregate}
```

Entity, View, and Aggregate definitions are resolved from the effective entity
schema plus WebDescriptor controls. Data definitions use descriptor/schema
metadata when available and otherwise infer fields from the admin data read/list
operations. Because inferred fields may pass through unordered map-like
structures, the fallback route normalizes `id` to the first field when it is
present.

The admin entity collection route describes create. It uses the entity `create`
view fields when available, then falls back to `detail`, then to the effective
schema. The id-scoped `/update` route describes edit/update and uses the entity
`detail` view fields. This split lets create omit fields such as `id` while
update keeps the fields required to edit an existing record.

The admin data collection route describes create. The id-scoped data `/update`
route describes edit/update. Data currently uses the same resolved data field
set for both routes because data schema metadata does not yet define
entity-style `create/detail` profiles.

The admin view and aggregate routes are read-oriented definitions. They return
GET list/detail navigation metadata and do not define generic create/update
submit actions. Aggregate create and command/update forms are resolved through
the corresponding component Operation Form API definitions.

If no form entry exists, the default follows exposure:

- `public` or `protected`: form may be generated.
- `internal`: form is not generated.

## Admin Surface Control

`admin.<surface>.<collection>.totalCount` controls whether Management Console
list/read pages may request total count values.

Supported `totalCount` values are:

- `disabled`: default. Total count is not requested or displayed.
- `optional`: the page may request total count. If the backing store reports
  that total count is unsupported or effectively unavailable, the page falls
  back to `hasNext` paging and displays a warning.
- `required`: the page requires total count. If the backing store cannot provide
  it, the admin Operation fails with a structured error.

Initial selector forms are:

```yaml
admin:
  entity.notice:
    totalCount: optional
  data.audit:
    totalCount: required
```

The supported surface names are `entity`, `data`, `view`, and `aggregate`.
Component-qualified selectors such as `notice-board.entity.notice` are also
accepted. The unqualified form applies to the named surface and collection
across components.

`admin.<surface>.<collection>.fields` declares Web UI field selection and
presentation overrides for Management Console pages. It is not the primary
model schema. The primary field source is `EntityRuntimeDescriptor.schema`,
an effective static schema normally built from CML-derived
`org.goldenport.schema.Schema` metadata on generated entity/value/operation
companion objects. If that schema lacks necessary information, the schema model
itself should be extended rather than adding parallel field-list metadata. If no
schema is available, the renderer falls back to best-effort inference from
existing read/list results.

The composition point is `WebSchemaResolver`. It resolves
`EntityRuntimeDescriptor.schema` and WebDescriptor controls into a Web-facing
schema before rendering. Renderers should consume the resolved Web schema rather
than directly reading CML, generated companions, component descriptor, or
WebDescriptor structures.

The current runtime information route is:

```text
CML
  -> org.goldenport.schema.Schema / ParameterDefinition
  -> EntityRuntimeDescriptor / OperationDefinition
  -> WebSchemaResolver.ResolvedWebSchema
  -> StaticFormAppRenderer
```

`ResolvedWebSchema` is the single Web-facing composition result for generated
HTML forms and Management Console forms. It carries `ResolvedWebField` entries
with name, label, datatype, multiplicity, control type, requiredness, readonly,
hidden/system flags, select values, multiple-selection intent, placeholder, and
help text. Renderer code should operate on `ResolvedWebField` rather than on a
parallel `Vector[String]` plus lookup `Map`, because the field vector preserves
the source schema order and the full Web metadata together.

Portable Web hints belong to `org.goldenport.schema.Schema` rather than to a
parallel CNCF-only field list. The initial carrier is `Schema.Column.web`, which
can hold control type, required override, hidden/system flags, select values,
multiple selection, readonly intent, placeholder, and help text. CNCF consumes
the same vocabulary through `WebDescriptor.FormControl`, and operation
parameters can carry it through `ParameterDefinition.web`. Missing presentation
needs should be added to the shared Schema model first and then mapped through
`WebSchemaResolver`.

### Web Schema Ordering

The default input order for Entity, Data, View, Aggregate detail, and Operation
forms is the CML-derived Schema or ParameterDefinition definition order.
Implementations must preserve this order by passing the ordered
`ResolvedWebField` vector through to rendering. Maps may be used only for
lookup; they must not become the authoritative form field order.

When no schema is available and a field list is inferred from runtime records,
the inferred order is best effort. The current fallback rule moves `id` to the
first position when present and leaves the remaining inferred fields in their
observed order.

Management Console list pages may use a different explicit field-ordering
strategy. The initial list strategy is `FieldOrderStrategy.IdFirst`, which keeps
`id` visible as the first list column even when a schema was authored in another
order. Form/detail screens use `FieldOrderStrategy.SchemaOrder`.

WebDescriptor-only fields that do not exist in the schema are extension fields.
For operation controls they are appended after schema parameters in stable name
order. For HTML create/edit forms, extra submitted values are rendered after
schema fields in stable name order.

### Web Schema Merge Rules

WebDescriptor is a presentation and deployment override layer, not a replacement
schema. Partial overrides must not erase CML/Schema-derived metadata. For
example, a WebDescriptor field that only supplies `placeholder` must preserve
the field's schema-derived `label`, `type`, `values`, `required`, and `help`.

The merge rule is:

- `controlType`, `required`, `placeholder`, and `help` are overridden only when
  the descriptor explicitly supplies a value.
- `hidden`, `system`, `multiple`, and `readonly` are additive flags.
- `values` are replaced only when the descriptor supplies a non-empty list.
- Descriptor-only fields are allowed, but they are treated as extension
  controls with `WebDescriptor` as their source.

Field entries may be short strings or records:

```yaml
admin:
  entity.notice:
    fields:
      - id
      - title
      - name: body
        type: textarea
        required: true
```

`controls` may be used when the field order is clearer as a short `fields` list
and control details should be separated:

```yaml
admin:
  data.audit:
    fields: id action actor
    controls:
      action:
        type: select
        values: [created, updated]
      actor:
        required: true
```

Admin controls reuse the same minimum control vocabulary as operation forms:
`type`, `required`, `hidden`, `system`, `values`, and `multiple`.

For Phase 12, resolved Web schema is used for `entity` create/detail/edit pages
when available, so the pages can render useful controls even when the collection
is empty and no Web Descriptor is present. `fields` can narrow or reorder that
schema. `data` still uses descriptor fields or read/list inference because it
has no CML entity schema. `view` and `aggregate` admin pages remain read-oriented,
but their list/detail pages also use the resolved schema of their backing entity
for display order before applying descriptor overrides. Editable view/aggregate
command forms will be promoted to the same CRUD form pipeline in a later pass.

Aggregate operation forms can use operation-specific admin field controls with
one additional selector segment:

```yaml
admin:
  aggregate.notice-aggregate.approve-notice-aggregate:
    fields:
      - name: id
        hidden: true
      - name: approved
        type: select
        values: [true, false]
```

This is currently used as a fallback for the normal `/form/{component}/{service}/{operation}`
operation form when the operation has no explicit `form.<selector>.controls`
entry. Explicit operation form controls remain the highest-priority form
descriptor because they describe the concrete Web Form endpoint directly.

## CML WEB Metadata Bridge

Cozy `car-sbt-project` may generate `src/main/car/web/web.yaml` from a top-level CML
`# WEB` section. The section body is treated as raw Web Descriptor YAML and is
copied into the generated project. When the section is absent, Cozy generates a
default sample descriptor scaffold.

This bridge keeps Web deployment/configuration data outside the CML core model
while still allowing a sample application or small CAR project to carry the
initial Web Descriptor next to the model. The descriptor is CAR metadata and
therefore lives under `src/main/car/web`; Web application pages and assets live
under `src/main/web`. The current implementation is a raw metadata bridge. A
later pass may connect the same concept to Dox/Kaleidox AST metadata once the
CML metadata contract is finalized.

`src/main/web` is the public Web application resource tree. If a future Web app
needs private bundle metadata or non-public resources, the reserved location is
`src/main/web/WEB-INF`, and those resources must not be exposed by static Web
serving. `src/main/web/META-INF` is not used; CAR-wide internal metadata, if it
becomes necessary, belongs under `src/main/car/META-INF` instead.

## Application Hosting Entries

`apps` defines user-facing Web HTML entry points, not operation execution.

Minimum fields:

- `name`
- `path`
- `kind`

Initial `kind` values:

- `dashboard`
- `manual`
- `console`

Dashboard and Manual remain read-only. Console may link to operation forms, but
operation execution remains on `/form/...` submission paths.

## Non-Goals For WEB-03 Minimum

- No frontend framework selection.
- No SPA build pipeline definition.
- No detailed rate-limit schema.
- No authentication provider implementation.
- No replacement of CML operation access rules.

## Remaining Work

WEB-03 minimum is implemented for descriptor loading and `/web` / `/form`
admission control. Remaining work belongs to later Web development items:

- Auth provider integration for real session/token identity.
- Broader selector scopes beyond explicit operation selectors.
- Rate-limit and traffic policy schema.
- SPA packaging and frontend build pipeline integration.
- Detailed admin UI for inspecting the resolved Web Descriptor.
