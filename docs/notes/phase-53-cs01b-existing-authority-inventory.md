# Phase 53 CS-01B - Existing Authority Inventory

Status: CS-01B in progress; this is a non-normative inventory.  It records
current owners and boundaries; it does not select an implementation API or
change a specification.

## Current ownership

| Concern | Current authority | Consumer or projection | Phase 53 boundary |
| --- | --- | --- | --- |
| Explicit `COMPONENT` CML | Kaleidox `ComponentSubsystemModel.ComponentDefinition` and `_parse_component_definition` | Cozy `Modeler` and `CmlModelMetadata` | Kaleidox owns the typed field; Cozy does not add a parallel parser. |
| Component descriptor | CNCF `ComponentDescriptor` and Cozy `CozyArchivePackager._component_descriptor_json` | packaged and development runtime descriptor readers | The current descriptor has no versioned style snapshot. |
| Development runtime evidence | sbt-cozy `cozyPrepareRuntime` | CNCF development repository selection | `runtime-classpath.txt` and `car-runtime-manifest.json` remain sbt-cozy-owned extensible evidence. |
| Component parameters | CNCF `ComponentParameterBootstrap` and `ComponentParameterResolutionLayers` | `Component.Factory` initialization | Current fixed precedence is packaged default, assembly default, subsystem instance, runtime configuration, then explicit test overlay. |
| Built-in catalog precedent | CNCF `PredefinedResultCatalog` and `McpToolCatalog` | result and MCP projections | Immutable ordering and duplicate rejection are precedents; no `ComponentStyle` catalog or Metadata Factory exists. |
| Generic configuration | simplemodeling-lib `ConfigurationResolver`, `ResolvedConfiguration`, and `ConfigurationTrace` | CNCF runtime configuration | File locations exist on sources, but ordinary file-source type/id is not retained in the final trace. |
| Runtime posture | CNCF `ExecutionContext.CncfCore.Holder.operationMode` and `RuntimeContext` | launcher/runtime internals | Internal runtime access is valid; Component/factory-facing permanent policy must not depend on it. |
| ArtScene operating policy | ArtScene `impl.ComponentFactory`, CML `ApplicationMode`, and assembly YAML | ArtScene factories and tests | The private application-mode key, standalone datastore default, and mode branch are competing authorities to remove in CS-06. |

## Confirmed exclusions

CS-01B does not add a `ComponentStyle` type, catalog, descriptor field, parser
grammar, Metadata Factory, configuration framework, launcher behavior, or
ArtScene behavior.  `ComponentProvider` and `ComponentRepository` dynamic
discovery are not a style-discovery mechanism.

Kaleidox remains read-only.  Before changing `ComponentDefinition` or its
parser, Phase 53 must record a scope reset admitting only
`/Users/asami/src/dev2025/kaleidox` as the CML production owner.

## Registered failing-first contracts

CS-01A already supplies executable pending evidence for loss of the explicit
CML `STYLE` selection before it reaches Kaleidox's typed component model.  CS-01
also registers these pending behavioral boundaries before their implementation:

| Contract | CS-01 executable evidence | Implementation slice |
| --- | --- | --- |
| built-in catalog selection and descriptor snapshot | CS-01 registration remains pending the ComponentStyle identity/schema freeze | CS-02 and CS-03 parity acceptance |
| a Component factory does not receive the full Subsystem policy surface | `ComponentFactoryModeBoundarySpec` | CS-04 |
| resolved file configuration preserves type and source identity | `ConfigurationResolverTraceSpec` | CS-05 |
| ArtScene exposes no component-owned operation-form/datastore-policy result | `Phase53AuthorityBoundarySpec` | CS-06 |

The test names describe required behavior, not an implementation class or a
Metadata Factory discovery mechanism.  Production implementation is deferred
to the listed slices.  The still-open catalog/descriptor contract registration
remains CS-01 work and must be completed immediately after the identity/schema
freeze; it is not deferred to CS-02.

## CS-01B outcome

The inventory fixes the owner of each current concern and the scope boundary
for the next slices.  CS-01 remains in progress until all remaining inventory,
the registered contracts, and the documented freeze decisions are reviewed.
