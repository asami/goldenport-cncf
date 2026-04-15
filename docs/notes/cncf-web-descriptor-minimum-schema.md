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

The Web Descriptor must not define entity relation rules, natural ABAC
conditions, DAC-style owner/group/other permissions, or service-internal/system
access modes. Those remain in the existing security model.

## Form API Control

`form.<operation>.enabled` controls whether the JSON Form API and plain HTML
FORM entry are available for that operation.

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

`admin.<surface>.<collection>.fields` declares the form/detail field schema for
Management Console CRUD pages. This is the preferred design-time source for
create, update, and detail rendering. If `fields` is absent, the current
renderer falls back to best-effort inference from existing read/list results.

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

For Phase 12, `fields` is implemented for `entity` and `data` create/detail/edit
pages. `view` and `aggregate` admin pages remain read-oriented, but their
instance detail pages also use descriptor fields for display order and for
design-time fields that are absent from the current record. Editable
view/aggregate command forms will be promoted to the same CRUD form pipeline in
a later pass.

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

Cozy `car-sbt-project` may generate `src/main/web/web.yaml` from a top-level CML
`# WEB` section. The section body is treated as raw Web Descriptor YAML and is
copied into the generated project. When the section is absent, Cozy generates a
default sample descriptor scaffold.

This bridge keeps Web deployment/configuration data outside the CML core model
while still allowing a sample application or small CAR project to carry the
initial Web Descriptor next to the model. The current implementation is a raw
metadata bridge. A later pass may connect the same concept to Dox/Kaleidox AST
metadata once the CML metadata contract is finalized.

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
