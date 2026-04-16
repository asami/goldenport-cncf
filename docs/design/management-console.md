# Management Console

## Scope

The Management Console is the browser-facing administration surface under
`/web/{component}/admin`. It is implemented as HTML pages that call admin
Operations. HTML rendering must not bypass the Operation boundary to read or
mutate runtime state directly.

The initial managed surfaces are:

- `/web/{component}/admin/entities`
- `/web/{component}/admin/data`
- `/web/{component}/admin/views`
- `/web/{component}/admin/aggregates`

Entity and data surfaces support list, detail, edit, update, new, and create.
View surfaces are read-only. Aggregate surfaces expose list/detail reads and
operation links; create and command behavior is executed through component
Operations.

Aggregate pages classify discovered Operations as:

- `create`: create Operations declared by the Aggregate definition.
- `read`: read/get/load/search Operations for the Aggregate.
- `update`: update Operations and Aggregate command Operations.

Aggregate instance pages expose only read/update Operations and prefill the
aggregate id in the Operation form. Aggregate creation is intentionally rooted
at the Aggregate page rather than an instance page.

## Query Responses

Admin list Operations return `items`. Each item has:

- `id`: canonical detail navigation id
- `label`: browser display label
- `value`: rendered raw value

Entity/data list Operations also return `ids` for compatibility. View/aggregate
list Operations also return `values` for compatibility. HTML rendering must use
`items[].id` for links and `items[].label` for visible text when `items` is
present.

Single read Operations return `item` and duplicate `id`, `label`, and `value`
at the top level for simple form widgets. Entity/data reads also return the
structured `record`.

Entity, data, view, and aggregate responses use this same read model. Surface
differences should be expressed as available actions, not as incompatible list
or detail response shapes.

## Paging And Totals

List pages use `page` and `pageSize`. `hasNext` is calculated with
`pageSize + 1` over-fetching so total count is not required by default.

`includeTotal` is design-gated by the Web Descriptor. The default is disabled.
When enabled as optional, unsupported backing stores return no `total` and add a
warning. When enabled as required, unsupported backing stores fail at the admin
Operation boundary.

## Forms

Form HTML pages live under `/form`. JSON-style form submission APIs live under
`/form-api` and return the Operation response directly without HTML rendering
or browser redirects.

Form schemas are resolved through `WebSchemaResolver`. The resolver composes
entity operation metadata from `EntityRuntimeDescriptor.schema` with
WebDescriptor presentation overrides, then passes a Web-facing schema to the
renderer. `EntityRuntimeDescriptor.schema` is the static effective entity schema
for entity operations; it is normally derived from the generated companion
`org.goldenport.schema.Schema` and may later be adjusted by application or
descriptor policy before Web rendering.

Web-specific field hints are carried on the shared Schema model as
`Schema.Column.web`. Management Console rendering treats these hints as the
portable CML-to-Web path and then applies WebDescriptor overrides at the
resolver layer. CNCF should not add a second schema field-list model when the
shared Schema is missing information.

Operation parameters use the same portable vocabulary through
`ParameterDefinition.web`. This keeps Entity fields and Operation form
parameters on one Web schema path: generated CML metadata supplies the default
control shape, `WebSchemaResolver` normalizes it, and WebDescriptor controls may
override deployment-specific presentation.

Operation forms are generated from
`OperationDefinition.specification.request.parameters` when available and are
also normalized through the Web schema resolver. Each parameter becomes a normal
HTML input with:

- `name` from the parameter name
- `type` derived from the parameter datatype
- `required` derived from multiplicity
- help text containing kind, datatype, and multiplicity

An additional `fields` textarea remains available for extension values and for
Operations without declared parameters. On submit, normal inputs and the
textarea are merged into the Operation form record.

Boolean parameters are rendered as checkboxes with an explicit hidden `false`
value so an unchecked field still submits a value. Numeric and date-like
datatypes are rendered with HTML `number` and `date` controls when the
Operation parameter datatype exposes that intent.

The initial type mapping is intentionally conservative:

- boolean datatypes become checkboxes.
- numeric datatypes become number inputs.
- date/datetime/timestamp datatypes become date or datetime-local inputs.
- password/secret/token-like parameter names become password inputs.
- text/body/content/description/comment/message-like parameters become textarea controls.
- enum/select, multi-value, hidden fields, and system fields require explicit
  descriptor or CML support before they are generated automatically.

WebDescriptor-level form controls may override generated controls:

- `type`: `text`, `textarea`, `select`, `checkbox`, `hidden`, or another HTML
  input type.
- `values`: select option candidates.
- `multiple`: enables multiple select.
- `hidden`: renders a hidden input.
- `system`: marks a control as framework-provided for future policy checks.
- `required`: overrides multiplicity-derived requiredness.
- `readonly`: renders non-editable controls where HTML supports it.
- `placeholder`: supplies input placeholder text.
- `help`: supplies user-facing field help text.

HTML form rendering and JSON form definition APIs share the same resolved Web
schema. This is required so a plain browser Form App and a JSON Form client see
the same fields, labels, requiredness, control type, enum candidates, and help
metadata for a selector.

Management Console schema resolution currently covers:

- Entity create/update forms through `EntityRuntimeDescriptor.schema`.
- Data create/update forms through explicit schema metadata or best-effort
  admin Data record inference.
- View read forms through the View root entity schema.
- Aggregate read forms through the Aggregate root entity schema.
- Aggregate create/command forms through the Operation form path.

Form execution result pages receive properties derived from the Operation
response:

- `component`, `service`, `operation`, `operation.label`
- original submitted fields and the same fields under `form.*`
- `result.status`
- `result.ok`
- `result.contentType`
- `result.body`
- extracted metadata such as `result.id`
- `error.status` and `error.body` for HTTP error responses

HTML templates use `textus-*` widgets such as `textus-result-view`,
`textus-result-table`, `textus-property-list`, and `textus-error-panel` to
render these properties without adding template control syntax.

## Result Transitions

Plain HTML form submissions must be usable without JavaScript. The baseline
behavior is:

- execute the Operation through the Form path.
- render a result page that includes the submitted Operation result and error
  information.
- provide an explicit link back to the Operation form and the component form
  index.

Descriptor-controlled transitions are available per form:

- `successRedirect`: returns `303 See Other` after a successful Operation.
- `failureRedirect`: returns `303 See Other` after a failed Operation.
- `stayOnError`: suppresses failure redirect and redisplays the original
  Operation form with submitted values and an error panel.

Redirect templates can reference `${component}`, `${service}`, `${operation}`,
submitted form fields such as `${id}`, and result properties such as
`${result.status}` and `${result.id}`. `${result.id}` is extracted by
`FormResultMetadata` from JSON response fields named `id`, `result.id`, or
`item.id`; scalar `prefix:id` responses are also recognized.

Admin entity and data create/update forms use the same descriptor mechanism.
Their descriptor selectors are:

- `{component}.admin.entities.{entity}.create`
- `{component}.admin.entities.{entity}.update`
- `{component}.admin.data.{data}.create`
- `{component}.admin.data.{data}.update`

Admin redirect templates may also reference `${surface}` and `${collection}`.
For admin create/update, `${result.id}` is the submitted record `id` when
available, otherwise it falls back to `FormResultMetadata` extraction from the
Operation result message. `stayOnError` redisplays the relevant admin edit/new
form with submitted values and an error panel, so plain HTML form users can
correct validation or authorization failures without losing input.

Aggregate command forms use the same mechanism: create forms can redirect to an
aggregate list or detail page, while update/command forms can redirect to
`/web/{component}/admin/aggregates/{aggregate}/{id}` when the submitted or
returned id is available.

## CML And Cozy Generation Contract

CML/Cozy generation should emit Web Descriptor `form` entries rather than
hard-code HTML behavior in generated pages. Generated descriptors may declare:

- form exposure and transition policy for Operation, admin entity, and admin
  data forms.
- control overrides for CML-declared UI intent such as textarea, select,
  hidden, system, required, and multi-value controls.
- redirect templates that use the stable property names defined above.

Generated descriptors are defaults. Application descriptors may override them at
deployment time without regenerating component code.

## Display Labels

Item labels are derived from representative record or product fields when
available. The current priority is:

`label`, `title`, `name`, `summary`, `displayName`, `caption`

If no representative field is available, the item id is used as the label.
