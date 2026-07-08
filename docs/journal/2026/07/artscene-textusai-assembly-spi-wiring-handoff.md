# ArtScene / TextusAi Assembly SPI Wiring Investigation Handoff

Date: 2026-07-08

## Summary

ArtScene Stage 5B live fetch reporting proved that the production fetch chain can run across the packaged facility catalog, but AI fallback did not activate even though `textus-ai-runtime` is declared in ArtScene `src/main/car/assembly-descriptor.yaml`.

The user direction is that applications should not call `SpiResolver.resolve(...)` as an application-level fallback. The standard pattern should be:

1. the consuming component implements the relevant socket trait, here `AiRunnerSocket`;
2. the assembly includes the provider component, here `textus-ai-runtime`;
3. CNCF runtime resolves and installs the provider SPI into the socket during subsystem startup;
4. if no provider can be installed or invoked, application code reports a structured runtime/configuration error.

Sanpomap currently performs an extra application-level `SpiResolver.resolve(...)` fallback. That behavior may hide runtime wiring gaps and should not become the standard pattern for ArtScene.

## Observed ArtScene Result

Command run outside the sandbox:

```sh
scripts/check-stage5b-live-fetch-operation-report.sh
```

The script completed successfully with no audit infrastructure errors:

- facilities checked: 49
- official_success: 8
- museum_or_jp_success: 29
- ai_success: 0
- unresolved: 12
- audit_error: 0

Reports:

- `/Users/asami/src/dev2026/textus-art-scene/target/cncf.d/stage5b-live-fetch-operation-report.md`
- `/Users/asami/src/dev2026/textus-art-scene/target/cncf.d/stage5b-live-fetch-operation-report.json`

The unresolved entries consistently contained this AI fallback message:

```text
AI fallback is unavailable: AI runner socket is not installed for ArtScene exhibition fallback.
```

## ArtScene Assembly State

ArtScene repo:

```text
/Users/asami/src/dev2026/textus-art-scene
```

Relevant commit:

```text
93b398d Add live fetch operation report smoke
```

ArtScene assembly descriptor:

```yaml
subsystem: textus-art-scene
version: 0.1.0
components:
  - name: textus-art-scene
    version: 0.1.0-SNAPSHOT
  - name: textus-ai-runtime
    version: 0.1.1
```

ArtScene component descriptor:

```yaml
name: textus-art-scene
version: 0.1.0-SNAPSHOT
component: textus-art-scene

config:
  textus.component.art-scene.datastores.application.policy: local-default
```

ArtScene primary component implements the socket:

```scala
final class ArtScenePrimaryComponent extends ArtSceneComponent with AiRunnerSocket
```

ArtScene intentionally only checks already-installed sockets:

```scala
private def _ai_runner(
  core: ActionCall.Core
)(using ExecutionContext): Consequence[AiRunner] = {
  def find(components: Vector[Component]): Option[AiRunner] =
    components.collectFirst {
      case socket: AiRunnerSocket if socket.isSpiInstalled => socket.aiRunner
    }
  val components = core.component.toVector ++ core.component.flatMap(_.subsystem).map(_.components).getOrElse(Vector.empty)
  find(components) match {
    case Some(runner) => Consequence.success(runner)
    case None => Consequence.serviceUnavailable("AI runner socket is not installed for ArtScene exhibition fallback.")
  }
}
```

## Assembly Loading Evidence

Running:

```sh
cncf --runtime-dev-dir /Users/asami/src/dev2025/cloud-native-component-framework . command meta.help
```

showed `TextusAi` as a subsystem child, along with `ArtScene`.

Running:

```sh
cncf --runtime-dev-dir /Users/asami/src/dev2025/cloud-native-component-framework . command admin.assembly.descriptor --format yaml
```

showed that the component-local assembly descriptor is present and read:

```yaml
kind: assembly-descriptor
subsystem: textus-art-scene
version: 0.1.0
components:
- name: TextusAi
  origin: active car textus-ai-runtime@0.1.0
- name: ArtScene
  origin: component-dev-dir
source:
  assembly_descriptor:
    present: true
    source: component-car
    path: src/main/car/assembly-descriptor.yaml
```

Running:

```sh
cncf --runtime-dev-dir /Users/asami/src/dev2025/cloud-native-component-framework . command admin.assembly.report --format yaml
```

showed both components loaded:

```yaml
components:
  loaded:
  - name: TextusAi
    origin: active car textus-ai-runtime@0.1.0
  - name: ArtScene
    origin: component-dev-dir
```

Therefore the immediate problem is not that `assembly-descriptor.yaml` is ignored. The provider component is present in the subsystem, but the `AiRunner` SPI is not installed into ArtScene.

## Suspicious Version Detail

ArtScene requests `textus-ai-runtime` version `0.1.1`, but the admin report says:

```text
active car textus-ai-runtime@0.1.0
```

The local CAR exists at:

```text
/Users/asami/src/maven-repository/repository/car/textus-ai-runtime/0.1.1/textus-ai-runtime-0.1.1.car
```

Need verify whether this is display metadata from inside the CAR, a stale descriptor, or a version resolution problem.

## TextusAi Runtime Provider State

TextusAi source repo:

```text
/Users/asami/src/dev2026/textus-ai
```

The repo was clean during investigation.

TextusAi `ComponentFactory` registers runtime bindings and then adds `TextusAiRunnerProvider` to the component port:

```scala
private[ai] def configureRuntimeSpi(
  component: Component,
  configuration: Option[ResolvedConfiguration]
): Component =
  val gemma = Some(GemmaConfig.fromEnvironment())
  val openai = configuration.flatMap(OpenAiConfig.fromConfiguration).orElse(OpenAiConfig.fromEnvironment())
  val google = configuration.flatMap(GoogleConfig.fromConfiguration).orElse(GoogleConfig.fromEnvironment())
  val defaultselection = _default_selection(configuration, openai.nonEmpty, google.nonEmpty)
  val profiles = AiProfileConfig.fromConfiguration(configuration)
  AiRuntimeGenerateBinding.register(component, gemma, openai, google)
  AiRuntimeChatBinding.register(component, gemma, openai, google)
  component.withPort(
    Component.Port
      .of(new TextusAiRunnerProvider(component, defaultselection, profiles))
      .orElse(component.port)
  )
```

Potential bug: if `Component.withBinding` is immutable-style, the results of `AiRuntimeGenerateBinding.register(...)` and `AiRuntimeChatBinding.register(...)` are discarded. Then `TextusAiRunnerProvider.supports(...)` may return false because `generate` and `chat` bindings are not available.

`TextusAiRunnerProvider.supports(...)` requires both generate and chat services to resolve:

```scala
contract.name == "ai-runner" &&
  contract.runtimeClass == classOf[AiRunner] &&
  _is_success(generateService(selection)) &&
  _is_success(chatService(selection))
```

If the bindings are missing, `SpiResolver` will not select this provider, and ArtScene remains uninstalled.

## TextusAi CAR Descriptor Observation

Inspecting the published CAR:

```sh
jar tf /Users/asami/src/maven-repository/repository/car/textus-ai-runtime/0.1.1/textus-ai-runtime-0.1.1.car
```

showed:

```text
component-descriptor.json
component/main.jar
config/.keep
lib/.keep
spi/.keep
web/.keep
```

Extracted `component-descriptor.json`:

```json
{"component":{"name":"textus-ai-runtime","boundedContext":"platform","domain":"ai-runtime"},"componentlets":[]}
```

This descriptor does not visibly identify the handwritten `org.simplemodeling.textus.ai.ComponentFactory`. However, CNCF factory discovery may still find root-package `ComponentFactory` by class scanning. This needs confirmation before concluding descriptor metadata is the root cause.

## CNCF SPI Resolution Path

CNCF already appears to run SPI resolution during subsystem creation:

```scala
// /Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/subsystem/GenericSubsystemFactory.scala
val spibindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor) ...
val components = SpiResolver.resolveOrRaise(_collapse_duplicate_components(builtins ++ components0), spibindings)
subsystem.add(components)
```

`SpiResolver` scans loaded components for sockets and providers:

```scala
case socket: SpiSocket[?] if !socket.isSpiInstalled
case m: SpiProviderComponent => m.spiProviders
case m: ExtensionPoint[?] => ...
val directproviders = component.port.entries.map(_DirectSpiProvider(_))
```

So, if both ArtScene and TextusAi are in the pre-add component vector and TextusAi exposes a supported provider through its port, ArtScene should be installed before `subsystem.add`.

## Likely Root Cause Candidates

1. TextusAi provider exists but `supports(...)` returns false because generate/chat bindings were not installed due discarded `withBinding` results.
2. TextusAi CAR is loaded as a generated component without the handwritten runtime `ComponentFactory`, so no `TextusAiRunnerProvider` is placed on the port.
3. SPI resolution happens before the actual ArtScene `AiRunnerSocket` participant exists, or it runs on a component vector that does not include the socket-bearing participant.
4. Version metadata/resolution mismatch causes the wrong TextusAi CAR or stale internal descriptor to be loaded.

## Desired CNCF Behavior

CNCF should make this normal path work without application-level fallback:

```text
component-dev-dir consumer + assembly-resolved provider CAR -> runtime SPI installed into consumer socket
```

Concrete acceptance target:

1. A dev-dir component implements `AiRunnerSocket`.
2. Its component-local assembly includes `textus-ai-runtime` or another provider component.
3. Starting via normal `cncf . ...` / component dev-dir path loads both components.
4. Runtime SPI resolution installs the provider into the socket before operations run.
5. Application code can call `socket.aiRunner` / `isSpiInstalled` without invoking `SpiResolver.resolve(...)` itself.

## Suggested CNCF Tests

Add or adjust executable specs for:

- packaged provider CAR + dev-dir consumer socket wiring;
- assembly component defaults crossing active CAR and component-dev-dir boundaries;
- provider component port entry becoming visible to `SpiResolver`;
- socket-bearing primary/componentlet participant being present at SPI resolution time;
- clear diagnostics when a provider component is loaded but no provider supports the socket contract.

## Suggested TextusAi Check

If investigation shows TextusAi provider is present but unsupported, fix TextusAi binding registration to preserve returned components, for example:

```scala
val withgenerate = AiRuntimeGenerateBinding.register(component, gemma, openai, google)
val withchat = AiRuntimeChatBinding.register(withgenerate, gemma, openai, google)
withchat.withPort(
  Component.Port
    .of(new TextusAiRunnerProvider(withchat, defaultselection, profiles))
    .orElse(withchat.port)
)
```

This should be verified in the TextusAi repo and then republished locally before rerunning ArtScene Stage 5B.

## Current Boundary

Do not fix this by adding `SpiResolver.resolve(...)` in ArtScene. The issue should be fixed in CNCF runtime wiring and/or TextusAi provider packaging/configuration so ArtScene remains a normal socket consumer.


## Update: 2026-07-08 Retry After CNCF Fix

The user updated CNCF and asked to retry the ArtScene live fetch report. The retry was run outside the sandbox from the ArtScene repository:

```sh
scripts/check-stage5b-live-fetch-operation-report.sh
```

The script completed successfully and regenerated:

- `/Users/asami/src/dev2026/textus-art-scene/target/cncf.d/stage5b-live-fetch-operation-report.json`
- `/Users/asami/src/dev2026/textus-art-scene/target/cncf.d/stage5b-live-fetch-operation-report.md`

The result did not change:

```text
facility_count: 49
official_success: 8
museum_or_jp_success: 29
ai_success: 0
unresolved: 12
audit_error: 0
```

Every unresolved facility still ended with the same AI fallback error:

```text
AI fallback failed: AI fallback is unavailable: AI runner socket is not installed for ArtScene exhibition fallback.
```

The current CNCF admin report confirms that assembly loading itself works and that both components are present:

```yaml
components:
  loaded:
  - name: TextusAi
    origin: active car textus-ai-runtime@0.1.0
  - name: ArtScene
    origin: component-dev-dir
warnings:
  status: ok
```

However, no runtime SPI binding is reported:

```yaml
wiring_bindings: []
```

Current conclusion: the CNCF fix makes or keeps assembly loading healthy, but it still does not install `TextusAi`/`AiRunner` into ArtScene's `AiRunnerSocket`. The remaining issue is runtime SPI binding, not ArtScene fetch-chain behavior and not missing assembly descriptor loading.

For the next CNCF fix, the minimum acceptance check should be:

1. `cncf --runtime-dev-dir /Users/asami/src/dev2025/cloud-native-component-framework . command admin.assembly.report --format yaml` shows a non-empty binding from `TextusAi` to ArtScene's `AiRunnerSocket`, or an equivalent diagnostic field that proves the socket was installed.
2. Re-running ArtScene `scripts/check-stage5b-live-fetch-operation-report.sh` changes the last fallback failure away from `AI runner socket is not installed`.
3. If no AI backend credentials/provider are configured, the expected next failure should be a structured AI provider/configuration error, not a socket installation error.
4. If a deterministic/test AI provider is configured through normal CNCF assembly/test configuration, at least one previously unresolved facility should classify as `ai_success` with `fetch_source = ai_runner` and confidence metadata.

Do not solve this by adding an ArtScene-level `SpiResolver.resolve(...)` fallback or by using a JVM system property to inject the provider. The intended standard path is assembly-driven provider loading plus CNCF runtime socket installation.
