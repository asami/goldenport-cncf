# Phase 55 GCF-09P — Final Consumer/Adapter Closure Ledger

Date: 2026-08-04
Status: GCF-09P accepted after second focused re-review and updated through accepted GCF-09R; GCF-09 Step closure complete, GCF-10/Phase 55 closure pending

This ledger is the auditable GCF-09P record.  It is read-only evidence about
the current implementation; it is not a new catalog or runtime specification.
The admitted inventory and ownership baseline is
[`phase-55-gcf01-inventory-and-binding-contract-freeze.md`](phase-55-gcf01-inventory-and-binding-contract-freeze.md).
The GCF-09 completion rule is the rule in
[`phase-55-checklist.md`](../phase/phase-55-checklist.md): every frozen consumer
uses typed binding lookup and no temporary internal `String` authority remains.
GCF-10/full Phase 55 closure is not claimed here.

## Repository evidence

The five frozen repository roles and the current identities observed for this
ledger are:

| Repository | Role | Identity observed |
| --- | --- | --- |
| `dev2025/simplemodeling-lib` | generic typed binding core, physical-source snapshot, resolution, provenance, derived projection | `7c2b9401519f1f1a1d238e31d744aa60b6d159fa` |
| `dev2025/cloud-native-component-framework` | CNCF/Textus catalog, source admission, aliases, typed runtime consumers and orchestration | baseline `110510b448bffb15ddb35d44160ea77c80163b8e` plus the current GCF-09 accumulator |
| `dev2026/cncf-launcher` | launcher-owned external codec and opaque transport | `3efdac8dbeac11ed743a1695e31dca4a48dde99d` |
| `dev2026/textus-launcher` | launcher-owned external codec and opaque transport | `ef67c5be2bbee15d9919c61a6646d587b02e2d09` |
| `dev2026/textus-art-scene` | the three admitted direct consumers and typed-consumer migration | `fd06303faddcb3f16816a3b3f765ffdb91417f59` plus the accepted current five-file GCF-09G delta |

## Classification (mutually exclusive)

Every remaining access is assigned exactly one class:

1. **Generic resolution/source core** — physical source loading, merge,
   resolution, provenance, or trace machinery in the generic library.
2. **External codec/transport** — launcher decoding or forwarding of an
   opaque binding envelope; it does not interpret CNCF binding semantics.
3. **Admitted typed runtime consumer** — a runtime path whose value authority
   is a typed binding projected from the admitted collection.
4. **Explicit direct-call compatibility API not used by admitted runtime** —
   public/legacy overloads retained for callers outside the admitted runtime
   path.
5. **Diagnostics/protocol/domain data parsing** — JSON, `Record`, MCP maps,
   wire fields, or domain records, not configuration authority.
6. **Unadmitted parameter family deferred without mutation** — a real runtime
   or compatibility family not admitted by GCF-01/GCF-09 and therefore
   recorded, not migrated speculatively.

## Accepted families and sole authorities

The current GCF-09A–R accumulator has GCF-09A–R accepted, including this
GCF-09P ledger after a clean second focused re-review.  The accepted slices
leave one typed value-policy authority per
admitted family.  GCF-09A/F/J/N use the Global collection for repository,
bootstrap, collaborator paths, and process-exit policy.  GCF-09B/C/L use the
Subsystem/SubsystemInstance projections for Web execution, descriptor roots,
operation mode, and authorization.  GCF-09D/E project service-container and
component-development paths from the admitted Subsystem values.  GCF-09H/I
project the SystemNode drain timeout and startup-import paths from their
SubsystemInstance values.  GCF-09M and GCF-09O use the one execution-profile
projection (including locale/timezone) selected before final admission;
formatting overlays remain explicit authenticated/fixed-user overlays.

GCF-09G has three distinct ArtScene outcomes: the application-mode key and the
datastore-policy key were removed as Component inputs (application mode remains
presentation vocabulary), while execution locale is supplied by the admitted
typed execution profile/ExecutionContext formatting path.  They therefore do
not share one generic consumer outcome.  GCF-09Q specifically changes the
global-only Ingress compatibility constructor to
`RuntimeConfig.defaultOperationMode`; it no longer reads
`global.config.operationMode`.  The source seam is
`security/IngressSecurityResolver.scala` (`_production_runtime_context`), and
the non-vacuous regression is
`security/IngressSecurityResolverSpec.scala`, “use the framework default
operation mode on the direct global compatibility path,” which supplies a
conflicting raw mode and observes the framework default.  In an admitted
Subsystem path, GCF-09L's typed operation policy remains authoritative.
`RuntimeConfig._operation_mode` and its raw aliases still decode a
compatibility source for legacy construction; that decoding has not disappeared
and is not claimed as an admitted authority.

GCF-09R projects GCF-09L's admitted operation mode into normal Component
execution and the Action, Job, Metrics, OpenTelemetry, and diagnostic-payload
externalization paths.  Mode-sensitive observability defaults and validation
are recomputed at that typed consumer boundary, including conflicting raw and
admitted values.  The remaining raw OpenTelemetry and diagnostic-payload
fields are still unadmitted class-6 policy; they no longer supply a second
operation-mode authority.

GCF-09K closed the deprecated CNCF resolver/merge/trace consumer stack.
Canonical `textus.*` names are the admitted identities;
`textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` spellings are decode-only
aliases and terminate at admission, never at an admitted runtime consumer.

## Remaining CNCF access ledger

The following are coherent families (not a claim that raw access globally
disappears):

| Symbols/family | Classification and boundary |
| --- | --- |
| `RuntimeConfig._from` server-emulator and `httpDriverKey` reads | **Unadmitted parameter family**: `RuntimeConfig` semantically constructs `HttpDriver`/server policy from raw `ResolvedConfiguration`; it is not an external codec and no typed catalog authority is asserted. |
| `RuntimeConfig.modeKey` and `commandExecutionModeKey` reads | **Unadmitted parameter family**: run-mode and command-execution compatibility inputs remain outside GCF-01/GCF-09 admission. |
| `RuntimeConfig.operationModeKey`/`_operation_mode` and raw operation-mode aliases | **Explicit direct-call compatibility API**: raw decoding remains for legacy `RuntimeConfig` callers; GCF-09L's typed Subsystem operation policy is the admitted authority, GCF-09Q's global-only Ingress constructor uses `defaultOperationMode`, and GCF-09R projects the typed mode into normal Component and observability consumers. |
| `RuntimeConfig` `DataStoreSpace.create` and `EntityStoreSpace.create` families | **Unadmitted parameter family**: datastore/entity-store runtime configuration is still decoded from the resolved source; this is distinct from ArtScene's removed Component datastore-policy input. |
| `RuntimeConfig.logBackendKey`, `logLevelKey`, and `logFilePathKey` | **Unadmitted parameter family**: logging backend, level, and file-path policy remain raw runtime inputs. |
| `RuntimeConfig` execution-history keys | **Unadmitted parameter family**: `ObservabilityEngine.ExecutionHistoryConfig` limits and filters are constructed from raw configuration. |
| `RuntimeConfig` diagnostic-payload externalization keys | **Unadmitted parameter family**: payload destination, thresholds, targets, overrides, and retention remain raw observability policy; GCF-09R recomputes their operation-mode-dependent defaults and validation from the admitted typed mode. |
| `RuntimeConfig` OpenTelemetry keys | **Unadmitted parameter family**: OTEL endpoint, protocol, and trace/metric/log enablement remain raw observability policy; GCF-09R recomputes their operation-mode-dependent endpoint and validation behavior from the admitted typed mode. |
| `ComponentLogic`, Action/Job diagnostics, `OpenTelemetryExporter`, and built-in Metrics operation-mode projection | **Admitted typed runtime consumer**: normal runtime execution and observability resolve operation mode from the admitted Subsystem policy at execution/export time; they do not consult raw `RuntimeConfig.operationMode`. |
| `RuntimeConfig` debug call-tree/trace-job/save-calltree and `DebugAuthConfig` keys | **Unadmitted parameter family**: debug and call-tree controls are raw diagnostic/runtime inputs. |
| `RuntimeConfig` static-form renderer keys (`WEB_RENDERER_*`) | **Unadmitted parameter family**: page-size, filter, preview, body-preview, and call-tree renderer limits are built by `_static_form_app_renderer_config`; no catalog admission is asserted. |
| `RuntimeConfig` resource URL, Textus-URN, URN-provider, and resource-tree policy keys | **Unadmitted parameter family**: `_resource_url_policy`, `_textus_urn_resource_policy`, `_urn_resource_providers`, and `_resource_tree_policy` construct resource policy from raw values. |
| `RuntimeConfig.MCP_CLIENT_POLICY_KEY` and `CodexMcpRuntimeConfiguration.loadC` | **Unadmitted parameter family**: the policy path is raw `RuntimeConfig` input and the referenced record is decoded only for this unadmitted MCP-client activation. |
| `RuntimeConfig.OPERATION_TOOL_POLICY_KEY` and `OperationToolRuntimeConfiguration.loadC` | **Unadmitted parameter family**: operation-tool policy path and its record activation remain outside the admitted catalog. |
| `RuntimeConfig.idNamespaceMajorKey`/`idNamespaceMinorKey` and `_id_namespace` | **Unadmitted parameter family**: ID namespace compatibility values are normalized into `RuntimeConfig`, not admitted by GCF-09. |
| `RuntimeConfig.webOperationDispatcherKey` and `webOperationDispatcherRestBaseUrlKey` | **Unadmitted parameter family**: dispatcher selection and REST base URL remain outside GCF-01/GCF-09 admission. |
| `RuntimeConfig` develop/demo/production-admin flags and role-list keys | **Explicit direct-call compatibility API**: raw decoders remain available to legacy/direct callers for catalog-admitted Web/authorization values; the admitted runtime authority is GCF-09L's typed Subsystem projection. |
| `RuntimeConfig._execution_profile` and `ExecutionProfileResolver.resolve(ResolvedConfiguration, ...)` | **Explicit direct-call compatibility API**: the raw resolver remains for legacy/direct callers of the admitted execution-determinism family; GCF-09M's typed `RuntimeExecutionProfileConfiguration`/Subsystem projection is the runtime authority. |
| `RuntimeExecutionProfileConfiguration.from` and the admitted Subsystem execution-profile projection | **Admitted typed runtime consumer**: canonical execution-profile bindings are decoded into value-only policy before runtime admission and consumed by the typed execution context. |
| `BlobStoreConfig.fromConfiguration` and blob backend/name/container/root/provider/size keys | **Unadmitted parameter family**: blob-store construction remains an existing blob API input and has no typed catalog admission. |
| `RuntimeTestDescriptor`, `RuntimeConfig.TEST_HOME_*`, test-home and test-descriptor reads | **Unadmitted parameter family**: test controls are bootstrap/test compatibility inputs, not admitted runtime parameters. |
| `ClientConfig` and client default/endpoint resolution | **Unadmitted parameter family**: client identity/configuration is not catalog-admitted and no second runtime authority is claimed. |
| `CncfRuntime` factory/discovery, workspace, site, subsystem/component identity/version, assembly, and residual launch reads | **Unadmitted parameter family**: these bootstrap/identity inputs remain outside GCF-01/GCF-09; accepted repository/bootstrap, execution, Web, import, shutdown, and exit projections remain their typed authorities. |
| `ServiceContainerRuntimeConfiguration.createC` legacy key decoding | **Explicit direct-call compatibility API**; admitted service-container runtime construction consumes the typed Subsystem policy. |
| `GenericSubsystemFactory` legacy overloads and fallback paths | **Explicit direct-call compatibility API**; runtime admission uses the typed factory path and does not revive fallback configuration. |
| Subsystem legacy `http-driver` overloads | **Explicit direct-call compatibility API** outside the admitted Web path; no second runtime authority. |
| Subsystem legacy mode configuration | **Unadmitted parameter family** outside the admitted Web/operation policy; it is not a typed authority. |
| `RuntimeParameterParser` and `ConfigurationAccess` reads used by raw CNCF runtime construction | **Unadmitted parameter family**: these are CNCF source-intake helpers whose values are semantically consumed by `RuntimeConfig`/legacy factories; they are not launcher codecs or generic-core authorities. |
| `OperationResponseFormatter` output format, shape, and command compatibility | **Unadmitted parameter family**. Locale/timezone second authority is removed by GCF-09O; display compatibility remains deferred. |
| `WebExecutionResolutionPolicy.resolveForSubsystem`/`resolveForRuntimeSubsystem` | **Admitted typed runtime consumer**: typed Subsystem Web execution/presentation policy is consumed without raw revival. |
| `WebExecutionResolutionPolicy.fromConfiguration` | **Explicit direct-call compatibility API** for presentation-only callers; it is not the admitted runtime Web authority. |
| `CollaboratorRepositorySpace` raw `ResolvedConfiguration` overload | **Explicit direct-call compatibility API**; GCF-09J runtime discovery receives only normalized Global typed bootstrap paths. |
| `Http4sHttpServer` job-input retention/TTL/threshold | **Unadmitted parameter family**: these policies remain outside GCF-01/GCF-09. |
| `Http4sHttpServer` typed tri-state legacy Web fallbacks | **Explicit direct-call compatibility API**; admitted Web policy remains the Subsystem projection. |
| `WebDescriptorResolver` direct `resolve` overloads | **Explicit direct-call compatibility API**; admitted Web descriptor/root resolution uses typed Subsystem values. |
| `Component`, `ComponentDependency`, `ComponentRepositorySpace` direct component/development configuration | **Unadmitted parameter family** (component dependency/cache and direct component-development APIs); GCF-09E's admitted development directory projection is the only accepted runtime authority. |
| JSON/`Record`/MCP map `.get[String]` (for example `McpStreamableHttpTransport`) | **Diagnostics/protocol/domain data parsing**, never configuration authority. |

## Other admitted repositories

In `simplemodeling-lib`, **generic resolution/source core** (`ConfigurationResolver`, `MergePolicy`,
`ResolvedConfiguration`, and `ConfigurationTrace`) are the generic
physical-source/legacy source-intake core.  They are not CNCF runtime
consumers or temporary internal adapters; typed binding collection remains the
effective authority.  They are retained and are not candidates for deletion.

In both launchers, **external codec/transport** (`LauncherConfig`) performs
launcher-owned configuration decode and carries an opaque binding envelope.
`textus.runtime.*` and
`cncf.runtime.*` spellings are external decode aliases only; there is no
`ConfigurationBinding` or `ResolvedConfiguration` semantic consumption in
either launcher.

In ArtScene no production Component consumer remains for the two removed
inputs (`textus.artscene.application.mode` and
`textus.component.art-scene.datastores.application.policy`).  The canonical
external admission inputs in
`conf/cncf/assembly-standalone.yaml` and
`conf/cncf/assembly-multi-user.yaml` still provide
`textus.execution.locale`; that value is admitted as execution-profile input,
not consumed by a production Component.  Remaining cursor `.get[String]`
calls are domain/protocol data.  The accepted GCF-09G five-file delta corrects
stale documentation/specification claims; it does not create another
authority.

## Deferred, unadmitted families

The following categories are explicitly deferred and correspond to the class-6
rows above: server-emulator/HTTP-driver, run mode and command-execution mode,
datastore/entity-store configuration, logging backend/level/file,
execution-history limits and filters, diagnostic-payload externalization,
OpenTelemetry, debug/call-tree and debug-auth controls, static-form renderer
limits, resource URL/URN/provider and resource-tree policy, MCP-client and
operation-tool policy activation, ID namespace, Web dispatcher/REST base URL,
client identity/endpoint, CncfRuntime discovery and identity/bootstrap
residuals, blob-store configuration, Subsystem mode configuration, CNCF
`RuntimeParameterParser`/`ConfigurationAccess` source intake,
operation-response display compatibility, HTTP job-input retention/TTL/
threshold, component dependency/cache and direct component-development APIs,
and test-descriptor/test-home controls.  These class-6 families are not
accepted typed parameters.  Class-4 rows may decode an admitted parameter for
legacy/direct callers, but are not the authority for the admitted runtime.
Future
admission of any class-6 family requires a separate authority, scope, codec,
default, and precedence Slice; absence of admission means do not mutate now.

## Closure conclusion and limits

Temporary internal `String` adapters for admitted families are removed or
confined to external/direct compatibility boundaries.  The resolver/merge/trace
consumer requirement was closed by GCF-09K while the generic core remains.
Launcher boundaries remain opaque.  The rows above cover the active
`RuntimeConfig` decoding helpers and directly cited CNCF consumer families
audited for GCF-09P; this ledger does not imply that unrelated future raw
accesses are globally absent. Phase full validation, GCF-10 integration, and
Phase closure remain pending; this ledger does not change strategy or claim
GCF-10/Phase completion.

## Reproducible re-audit commands

Run from `/Users/asami/src` (line counts are intentionally not evidence):

```sh
rg -n 'RuntimeConfig|get\[String\]|ConfigurationAccess|RuntimeParameterParser' \
  dev2025/cloud-native-component-framework/src
rg -n 'textus\.runtime\.|cncf\.runtime\.|ConfigurationBinding|ResolvedConfiguration' \
  dev2026/cncf-launcher/src dev2026/textus-launcher/src
rg -n 'textus\.artscene\.application\.mode|textus\.component\.art-scene\.datastores\.application\.policy|textus\.execution\.locale' \
  dev2026/textus-art-scene/src dev2026/textus-art-scene/docs dev2026/textus-art-scene/conf
rg -n 'ConfigurationResolver|MergePolicy|ResolvedConfiguration|ConfigurationTrace' \
  dev2025/simplemodeling-lib/src
git -C dev2025/cloud-native-component-framework diff --check
```

No SBT/full-test result is fabricated or implied.  Manifest status: the
GCF-09P ledger content is accepted after a clean second focused re-review and
the GCF-09 Step closure review/integration gate is complete; GCF-10 integration
and Phase 55 closure remain pending.
