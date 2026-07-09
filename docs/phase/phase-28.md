# Phase 28 — Web UI DSL / Bootstrap Core / Material Design / UX Profile

status = closed

## 1. Purpose of This Document

This work document records Phase 28, which implemented the `9.19 Web UI
DSL / Bootstrap Core / Material Design / UX Profile` development item.

Phase 27 closed the Knowledge Editor and Domain Knowledge Authoring baseline.
Phase 28 turns the Web lessons from that editor work into reusable CNCF
Web/platform infrastructure: semantic Web UI widgets, stable Bootstrap Core
DOM, selectable UX profiles, a Material Design profile path, editable repeated
form rows, and demo-assist metadata for generated screens.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Promote the Web UI DSL from ad hoc widget rendering into a canonical
  generated-screen vocabulary.
- Preserve existing display widgets such as `textus:table`,
  `textus:card-list`, `textus:line-list`, `textus:summary-card`,
  `textus:action-form`, and `textus:error-panel`.
- Add an editable repeated-row widget as a distinct semantic widget. Existing
  `textus:line-list` remains display-oriented.
- Define Bootstrap Core DOM conventions for generated pages, sections, forms,
  fields, actions, and widgets.
- Standardize semantic selector attributes such as `data-textus-page`,
  `data-textus-section`, `data-textus-form`, `data-textus-field`,
  `data-textus-action`, and `data-textus-widget`.
- Add a UX Profile model with initial `bootstrap`, `material`, `compact`, and
  `admin` profiles.
- Treat Material Design as a UX profile layered on the same semantic widget and
  Bootstrap Core DOM model, not as a separate Web runtime.
- Use `/Users/asami/src/dev2026/textus-knowledge-editor` as the first
  application driver. InformationSpace / Knowledge Editor screens provide the
  concrete pressure, while CNCF owns the reusable Web UI DSL, widget, DOM, and
  UX profile contracts for Static Form Web Apps and generated
  admin/application screens.
- Treat TKE source fragment composition/fragments editor repeated-row editing
  as a concrete `textus:editable-line-list` migration driver, replacing
  application-local JavaScript as the primary editing model where appropriate.
- Add a Web Demo Assist Manifest for cozy video and demo script generation.

Scope boundaries:

- Phase 28 does not reopen Phase 27 Knowledge Editor items except for targeted
  regressions.
- Phase 28 does not replace Static Form Web Apps with an SPA runtime.
- Phase 28 does not implement Web Island Architecture Runtime.
- Phase 28 does not implement API Gateway / public REST exposure policy.
- Phase 28 does not add a production visual theme marketplace.
- Phase 28 does not introduce a custom frontend package lifecycle or design
  system compiler.

## 3. Active Work Stack

- A (DONE): WU-01 — Open Phase 28 and freeze Web UI DSL scope.
- B (DONE): WU-02 — Web UI DSL vocabulary and projection contract.
- C (DONE): WU-03 — Bootstrap Core DOM and semantic selector contract.
- D (DONE): WU-04 — UX Profile model with Bootstrap and Material profile
  support.
- E (DONE): WU-05 — Static Form renderer integration vertical slice.
- F (DONE): WU-06 — Editable line-list widget vertical slice.
- G (DONE): WU-07 — TKE / InformationSpace / Knowledge Editor driver
  integration.
- H (DONE): WU-08 — Validation, issue, capability, and empty-state widget
  alignment.
- I (DONE): WU-09 — Web Demo Assist Manifest for cozy video and demo tooling.
- J (DONE): WU-10 — Phase 28 verification and closure.

Resume hint:

- Phase 28 is closed. Select a new independent 9.x Web/platform or runtime
  development item before opening the next phase.

## 4. Development Items

- [x] WU-01: Open Phase 28 and freeze Web UI DSL scope.
- [x] WU-02: Web UI DSL vocabulary and projection contract.
- [x] WU-03: Bootstrap Core DOM and semantic selector contract.
- [x] WU-04: UX Profile model with Bootstrap and Material profile support.
- [x] WU-05: Static Form renderer integration vertical slice.
- [x] WU-06: Editable line-list widget vertical slice.
- [x] WU-07: TKE / InformationSpace / Knowledge Editor driver integration.
- [x] WU-08: Validation, issue, capability, and empty-state widget alignment.
- [x] WU-09: Web Demo Assist Manifest for cozy video and demo tooling.
- [x] WU-10: Phase 28 verification and closure.

Detailed task breakdown and progress tracking are recorded in
`phase-28-checklist.md`.

## 5. Completion Conditions

Phase 28 can close when:

- The Web UI DSL vocabulary is documented and backed by executable renderer or
  projection specs.
- Generated pages expose stable Bootstrap Core DOM and semantic
  `data-textus-*` selectors for pages, sections, forms, fields, actions, and
  widgets.
- Existing display widgets remain compatible.
- The editable repeated-row widget supports add row, template-defined delete
  controls, hidden template row, stable field names, row-level validation
  anchors, field-level validation anchors, and no-JS fallback.
- UX profile selection supports at least the Bootstrap baseline and a Material
  profile path without changing operation selectors, form field names,
  authorization, data binding, or server-side execution paths.
- TKE InformationSpace / Knowledge Editor driver screens exercise the new
  widget and DOM contracts.
- TKE fragments editor/source fragment composition repeated-row editing is
  covered as an editable-line-list migration target.
- The Web Demo Assist Manifest can expose page/section/form/field/action/widget
  selector mappings when demo mode is explicitly enabled.
- Demo assist does not expose hidden sensitive values, session tokens, raw
  provider payloads, or confidential field values.
- Deferred Web work remains tracked under the independent 9.x Web/platform
  development items.

## 6. Closure Result

Phase 28 closed after WU-10 verification. The completed baseline includes:

- Web UI DSL vocabulary and projection contracts.
- Bootstrap Core DOM and semantic `data-textus-*` selectors.
- UX profile metadata for `bootstrap`, `material`, `compact`, and `admin`.
- `textus:editable-line-list` as the repeated-row form-edit widget.
- Validation, issue, capability, and empty-state semantic hooks.
- Web Demo Assist Manifest gated by runtime configuration.

Deferred work remains outside Phase 28:

- Material visual rendering and profile-specific assets.
- Broader generated-page selector coverage beyond the Phase 28 vertical slices.
- Web Island Architecture Runtime.
- API Gateway and public REST exposure policy.
- Production visual theme marketplace.


## 7. Post-closure Maintenance

Phase 28 remains closed. Post-closure runtime maintenance on Jun. 28, 2026
added CNCF build-version metadata and a runtime `version` command so launcher
development-runtime paths can report the selected CNCF runtime version without
falling back to a published artifact lookup.

Post-closure platform maintenance on Jul. 2, 2026 added the canonical CNCF SPI
baseline:

- `org.goldenport.cncf.spi` owns provider-neutral SPI contracts, provider
  publication, socket injection, and already-loaded component resolution.
- `org.goldenport.cncf.spi.ai.runner.AiRunner` defines the first canonical AI
  runner SPI, with `generate` and `chat` executed under CNCF
  `ExecutionContext`.
- Components that mix in SPI socket traits can receive compatible providers
  from dependency components during bootstrap.
- `textus-ai` is the first provider driver and publishes an AI runner adapter
  without replacing its existing public generate/chat operations.

Post-closure Web route maintenance on Jul. 7, 2026 kept Phase 28 closed while
hardening the Static Form Web App namespace. Component-owned Static Form Web
apps use `/web/{component}/{webApp}` as the canonical route, generated form
indexes use `/form/{component}`, and `/web/{webApp}` is valid only when a
SAR/subsystem descriptor declares an explicit alias.

Post-closure component entry route maintenance on Jul. 7, 2026 kept the same
namespace separation while adding an explicit `entry: true` app option.
Configured entry apps may serve `/web/{component}`,
`/web/{component}/index`, and `/web/{component}/index.html` as short Web entry
routes without restoring generated form-index fallback.

Post-closure component integration-test maintenance on Jul. 8, 2026 added an
explicit test descriptor overlay while keeping Phase 28 closed:

- Test runs may pass `--textus.test.descriptor=<path>` or the `cncf.*` alias.
- The descriptor may contribute runtime `config` values and an `assembly`
  overlay, including `assembly.spi.bindings`.
- Relative descriptor paths are normalized from the runtime cwd so config and
  assembly overlays read the same file.
- Malformed SPI bindings fail deterministically instead of being silently
  dropped.
- `provider.service` is reserved for future service-level provider matching and
  is rejected when specified.

Post-closure Help/Manual and CAR developer guidance maintenance on Jul. 8,
2026 kept Phase 28 closed while aligning documentation and executable route
coverage:

- Generated Help and packaged Manual routes are canonicalized under `/help`
  and `/man`.
- These routes remain development/operator inspection surfaces and are hidden
  in production operation mode.
- Compatibility document routes under `/web/.../document` remain tested where
  available, but new docs should prefer `/help` and `/man`.
- CAR developer guidance now treats `src/main/car` as the default
  `packaging.kind: car` source directory.
- `src/main/car/assembly-descriptor.yaml` is documented as the standard
  component-local assembly defaults source for wiring, SPI/provider defaults,
  and required provider component declarations.

Post-closure CAR runtime intake maintenance on Jul. 8, 2026 kept Phase 28
closed while hardening packaged component loading:

- Component CAR descriptors must declare name, version, and component metadata.
- CAR discovery supports plain `Component.Factory` implementations as well as
  bundle factories.
- Socket wiring is executable-spec covered for providers loaded from component
  CARs.
- cwd `component.d` is not auto-activated as a default active repository;
  packaged component directories remain explicit activation inputs.

Post-closure CAR assembly dependency maintenance on Jul. 9, 2026 kept Phase 28
closed while hardening `--component-file` and component-CAR startup:

- CAR-local assembly descriptor component dependencies are activated from
  active/search repositories before SPI socket resolution.
- Missing declared component dependencies fail startup deterministically instead
  of being hidden as later SPI binding or service lookup failures.
- Startup diagnostics identify the missing component, requesting CAR/subsystem
  descriptor, assembly descriptor, and configured repositories.

Post-closure AI runner SPI maintenance on Jul. 9, 2026 kept Phase 28 closed
while extending the provider-neutral AI runner contract:

Post-closure SPI observability maintenance on Jul. 9, 2026 kept Phase 28 closed
while adding caller-side tracing for canonical provider-neutral SPI calls:

- Traced services are installed into caller component `SpiSocket`s, so SPI
  invocation spans are recorded in the caller's execution context.
- Canonical `AiRunner`, `GeoResolver`, and `ToolchainRunner` calls emit
  `spi:<contract>.<operation>` calltree spans and `spi.invocation` runtime
  metrics.
- Provider request payloads, prompts, route DSL, API keys, SVG content, and raw
  provider output are not copied into calltree or metrics.
- If a provider implementation calls a CNCF operation, the existing action /
  internal-DSL trace appears inside the outer SPI invocation span.

- `AiRunnerRequirement` can carry logical tool requests through the existing
  generate/chat SPI path.
- The CNCF tool vocabulary includes URL context and provider-neutral web
  search, with Google-specific aliases normalized by the parser.
- Unknown tool tokens are preserved as contract values so provider adapters can
  fail explicitly instead of silently ignoring requested behavior.

Post-closure runtime maintenance on Jun. 29, 2026 aligned the CAR component
developer startup path and launcher command installation path:

- CNCF command-mode parsing keeps `component.service.operation` as the
  canonical command selector and rejects the old three-token
  `component service operation` command form.
- CNCF still supports operation-leaf selection when runtime selector resolution
  can disambiguate a leaf operation name.
- `cncf-launcher` owns target-first convenience syntax and translates it to the
  runtime mode-first command contract.
- `cncf install-cli` installs development commands that pin the resolved runtime
  and fixed project target.
- `textus install-cli` installs operation-facing user commands for packaged CAR
  artifact operation.
- `docs/notes/car-development-start-guide.md` records the CAR development
  startup procedure for component developers.

Remaining work stays under independent future development items.
