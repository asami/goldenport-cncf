# Web Form API Schema

/*
 * @since   Apr. 16, 2026
 * @version Apr. 16, 2026
 * @author  ASAMI, Tomoharu
 */

## Purpose

The Web Form API provides machine-readable form definitions for Web clients.
It is the JSON counterpart of the Static Form App HTML rendering path.

The contract is intentionally schema-oriented:

- Form definitions are derived from CNCF model metadata.
- WebDescriptor is a presentation and deployment override layer.
- The resolved result is `WebSchemaResolver.ResolvedWebSchema`.
- HTML rendering and JSON Form API responses consume the same resolved schema.

The Form API definition endpoints do not execute business logic. Execution
remains an Operation dispatch through the Web Operation Dispatcher or the normal
REST operation path.

## Definition Routes

The current definition routes are:

```text
GET /form-api/{component}/{service}/{operation}
GET /form-api/{component}/admin/entities/{entity}
GET /form-api/{component}/admin/data/{data}
GET /form-api/{component}/admin/views/{view}
GET /form-api/{component}/admin/aggregates/{aggregate}
```

The first route describes a component Operation form. The admin routes describe
Management Console form/detail metadata for the corresponding surface.

Plain HTML pages live under `/web` and browser-native HTML form submission lives
under `/form`. `/form-api` is reserved for JSON-oriented form metadata and
validation APIs.

## Response Shape

Definition endpoints return a JSON object:

```json
{
  "selector": "notice-board.entity.notice",
  "surface": "entity",
  "source": "Schema",
  "mode": "admin-entity",
  "method": "POST",
  "submitPath": "/form/notice-board/admin/entities/notice/create",
  "htmlPath": "/web/notice-board/admin/entities/notice/new",
  "actions": [
    {
      "name": "create",
      "method": "POST",
      "path": "/form/notice-board/admin/entities/notice/create"
    },
    {
      "name": "update",
      "method": "POST",
      "path": "/form/notice-board/admin/entities/notice/{id}/update"
    }
  ],
  "fields": [
    {
      "name": "body",
      "label": "Notice body",
      "type": "textarea",
      "dataType": "String",
      "multiplicity": "One",
      "required": true,
      "readonly": false,
      "hidden": false,
      "system": false,
      "values": [],
      "multiple": false,
      "placeholder": "Write the notice body.",
      "help": "Notice body shown on the board.",
      "source": "Schema"
    }
  ]
}
```

Top-level fields:

- `selector`: normalized logical selector for the resolved schema.
- `surface`: one of `operation`, `entity`, `data`, `view`, or `aggregate`.
- `source`: source classification for the resolved schema.
- `mode`: client-facing usage mode, such as `operation`, `admin-entity`,
  `admin-data`, `admin-view`, or `admin-aggregate`.
- `method`: default method for the primary path.
- `submitPath`: default primary path for form submission or read navigation.
- `htmlPath`: default browser HTML page for the same form surface.
- `actions`: available related actions with `name`, `method`, and `path`.
- `fields`: ordered field definitions.

Field entries:

- `name`: submitted field name.
- `label`: display label, if supplied by the schema or descriptor.
- `type`: HTML-oriented control type such as `text`, `textarea`, `select`,
  `checkbox`, `hidden`, `number`, `date`, or `datetime-local`.
- `dataType`: model datatype, when available.
- `multiplicity`: model multiplicity, when available.
- `required`: effective requiredness.
- `readonly`: effective readonly flag.
- `hidden`: effective hidden flag.
- `system`: framework/system field marker.
- `values`: select candidate values.
- `multiple`: multi-value control intent.
- `placeholder`: input placeholder, when supplied.
- `help`: user-facing field help text, when supplied.
- `validation`: optional validation hints that can be checked before Operation
  dispatch.
- `source`: field source classification.

The response shape is deliberately close to `ResolvedWebSchema` so clients can
use the same contract as Static Form App rendering.

## Navigation And Actions

Definition responses include navigation metadata so a JSON-oriented client can
build a usable form without hard-coding the Static Form App path conventions.

Operation definitions use:

```json
{
  "mode": "operation",
  "method": "POST",
  "submitPath": "/form-api/notice-board/notice/post-notice",
  "htmlPath": "/form/notice-board/notice/post-notice",
  "actions": [
    {
      "name": "submit",
      "method": "POST",
      "path": "/form/notice-board/notice/post-notice"
    },
    {
      "name": "api-submit",
      "method": "POST",
      "path": "/form-api/notice-board/notice/post-notice"
    },
    {
      "name": "validate",
      "method": "POST",
      "path": "/form-api/notice-board/notice/post-notice/validate"
    }
  ]
}
```

Admin entity and data definitions expose list/new/create/detail/edit/update
actions. Paths that need a runtime id use `{id}` as the path template variable.

Admin view and aggregate definitions are read-oriented in the current baseline.
Their primary path is the read/list HTML page, and their actions include list
and detail path templates. Aggregate create and command execution remains
Operation-based and is exposed through component Operation forms, not as a
generic admin aggregate form submit.

## Source Metadata

The primary source route is:

```text
CML
  -> org.goldenport.schema.Schema / ParameterDefinition
  -> EntityRuntimeDescriptor / OperationDefinition
  -> WebSchemaResolver.ResolvedWebSchema
  -> StaticFormAppRenderer / Form API JSON
```

Operation form definitions are resolved from
`OperationDefinition.specification.request.parameters` plus WebDescriptor form
controls.

Entity definitions are resolved from `EntityRuntimeDescriptor.schema` plus
WebDescriptor admin controls.

View and Aggregate definitions use their declared root `entityName` to resolve
the effective entity schema, then apply WebDescriptor admin controls for the
view or aggregate surface.

Data definitions use descriptor/schema metadata when available. If no schema is
available, the renderer may infer fields from admin data read/list Operation
results. Inferred fields are best effort and should be replaced by explicit
schema metadata for production-grade forms.

The same composition result is used by both browser-facing HTML forms and the
JSON Form API. Static Form App rendering and `/form-api` endpoints must not
resolve different field sets for the same selector.

Current covered surfaces are:

- Operation form input parameters.
- Admin Entity create/update form fields.
- Admin Data create/update form fields.
- Admin View read form fields.
- Admin Aggregate read form fields.

Admin Aggregate create and command flows are still Operation flows. Their input
schema is therefore resolved through the Operation route, even when the
Operation is declared as an Aggregate create or command method.

## Ordering

Input form/detail order is the model definition order:

- Operation fields follow `ParameterDefinition` order.
- Entity, View, Aggregate, and schema-backed Data fields follow
  `org.goldenport.schema.Schema` column order.
- WebDescriptor-only extension fields are appended after model fields in stable
  name order.

Maps may be used for lookup only. They must not become the authoritative field
order.

When no schema is available and fields are inferred from runtime records, the
order is best effort. The current fallback rule moves `id` to the first position
when present and leaves the remaining inferred fields in observed order.

Management Console list pages may use a separate explicit display strategy.
The current list strategy is `FieldOrderStrategy.IdFirst`; form/detail JSON uses
`FieldOrderStrategy.SchemaOrder`.

## Merge Rules

WebDescriptor overrides do not replace the model schema. They merge into the
schema-derived field metadata.

The effective merge rule is:

- `controlType`, `required`, `placeholder`, and `help` override only when the
  descriptor explicitly supplies a value.
- `hidden`, `system`, `multiple`, and `readonly` are additive flags.
- `values` are replaced only when the descriptor supplies a non-empty list.
- `validation` constraints are schema-first. WebDescriptor may narrow or add
  Web admission constraints, but it must not silently relax model constraints.
- Descriptor-only fields are allowed and treated as extension controls with
  `WebDescriptor` as their source.

If the shared schema model lacks information needed by Web forms, the preferred
fix is to extend `org.goldenport.schema.Schema` or `ParameterDefinition`, not to
add a parallel CNCF-only field list.

## Validation Hints

Validation hints are metadata that can be evaluated at the Web input admission
boundary without executing the target Operation.

The supported design surface is:

```json
{
  "validation": {
    "min": 0,
    "max": 100,
    "minLength": 1,
    "maxLength": 200,
    "pattern": "^[A-Z0-9-]+$",
    "step": 1
  }
}
```

Initial hint vocabulary:

- `min` / `max`: numeric or temporal range bounds.
- `step`: numeric step hint for HTML number controls and Web clients.
- `minLength` / `maxLength`: string length bounds.
- `pattern`: regular expression pattern for string values.

The primary source must be the portable model metadata:

```text
CML field/parameter annotations
  -> org.goldenport.schema.Schema / ParameterDefinition
  -> WebSchemaResolver.ResolvedWebField
  -> Form API JSON and HTML controls
```

WebDescriptor can provide deployment or presentation-specific admission hints,
but these hints are secondary. The merge policy is conservative:

- Schema/ParameterDefinition constraints are authoritative model constraints.
- WebDescriptor may add a missing hint.
- WebDescriptor may narrow a hint, such as reducing `maxLength` or tightening a
  numeric range.
- WebDescriptor must not widen or remove a model constraint. If a descriptor
  requests relaxation, the resolver should keep the model constraint and record
  a warning once the warning channel is available.

HTML rendering should map hints to native Bootstrap/browser-friendly
attributes when possible:

- `min`, `max`, and `step` become `min`, `max`, and `step` attributes on number,
  date, and datetime controls.
- `minLength` and `maxLength` become `minlength` and `maxlength` attributes on
  text controls.
- `pattern` becomes `pattern` on text-like controls.

The server-side validator remains authoritative for Web input admission. Client
attributes are usability hints only.

`readonly`, `hidden`, and `system` are not validation hints. They are form
contract flags:

- `readonly`: value may be displayed and carried through, but client edits are
  not trusted. Server-side handling must decide whether to ignore, preserve, or
  reject submitted changes.
- `hidden`: field is not visible, but still part of the form contract.
- `system`: field is framework-controlled. Required validation ignores missing
  system fields, and server-side processing should prefer server-generated
  values over client-submitted values.

The first implementation step should extend `org.goldenport.schema.Schema` and
`ParameterDefinition` so these hints are portable across CML, generated
descriptors, Form API JSON, and Static Form App HTML. Adding CNCF-only parallel
fields is a fallback only for descriptor-local override metadata.

## Validation And Execution

Definition routes are read-only. Validation routes validate submitted input
against the same resolved schema without invoking business logic:

```text
POST /form-api/{component}/{service}/{operation}/validate
```

The initial validation response shape is:

```json
{
  "selector": "notice-board.notice.post-notice",
  "surface": "operation",
  "valid": false,
  "errors": [
    {
      "field": "body",
      "code": "required",
      "message": "Notice body is required."
    }
  ],
  "warnings": [
    {
      "field": "extra",
      "code": "unknown-field",
      "message": "extra is not defined in the form schema."
    }
  ],
  "fields": []
}
```

Validation currently checks:

- required field presence
- datatype shape for boolean, integer, number, date, and datetime-like fields
- select/control `values`
- single-value multiplicity for fields that do not allow `multiple`
- schema-unknown submitted fields as warnings

The Form API validation responsibility is limited to Web input admission:

- syntactic validity of submitted values
- model/schema compatibility of submitted values
- WebDescriptor presentation constraints that can be checked without executing
  the target Operation
- stable error/warning shape for browser and JSON-form clients

It must not invoke the target Operation. It must not read or mutate application
state except for metadata needed to resolve the form schema. It also must not
perform domain decisions that belong to the Operation, Aggregate, Entity, or
UnitOfWork layer.

Plain HTML FORM submission uses the same validation boundary before Operation
dispatch. If Web input admission fails, the server redisplays the originating
HTML form with the submitted values, a validation summary, and Bootstrap
field-level feedback. The target Operation is not dispatched. The response is
HTML, not JSON, so browser-native form users are not forced through the JSON
validation endpoint.

Management Console entity/data create and update forms use the same validation
boundary before admin Operation dispatch. Schema-level errors redisplay the
originating new/edit page with submitted values and field-level feedback. A
successful Web input admission check does not replace admin Operation
validation; it only permits dispatch to proceed.

Operation execution remains the authoritative validation boundary for:

- domain invariants
- cross-field rules that depend on application semantics
- authorization that depends on the executing subject, target instance, or
  UnitOfWork context
- optimistic locking and stale update detection
- datastore-backed uniqueness or existence checks
- state-machine transition validity

If a later Web form needs cross-field validation, the rule should be declared as
metadata only when it can be evaluated without dispatching the Operation. Rules
that require the current domain state must remain Operation-side validation and
surface back through the normal Operation response/error contract.

Later validation stages may add richer datatype normalization and metadata-based
cross-field checks while preserving the same top-level shape.

Operation execution remains separate. The current compatibility route:

```text
POST /form-api/{component}/{service}/{operation}
```

submits form data and returns the Operation response directly. It is not the
definition endpoint and should not be used as the validation contract.

Management Console create/update HTML submissions use `/form/{component}/admin`
routes and dispatch admin Operations through `WebOperationDispatcher`.

## Relationship To Other Designs

- `management-console.md` defines the browser-facing admin surface and the
  admin Operation read/write model.
- `web-operation-dispatcher.md` defines how Web form submissions dispatch to
  local or remote CNCF Operation execution.
- `entity-authorization-model.md` remains the domain authorization model. Web
  Form API schema exposure is a Web tier admission concern and does not replace
  Operation, Entity, Data, View, Aggregate, or UnitOfWork authorization.
