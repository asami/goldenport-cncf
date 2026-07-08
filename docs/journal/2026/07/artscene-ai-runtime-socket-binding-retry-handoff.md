# ArtScene AI Runtime Socket Binding Retry Result

Date: 2026-07-08

## Summary

ArtScene was retried after the CNCF runtime SPI resolution change. CNCF now loads
`textus-ai-runtime` from the ArtScene assembly and installs its `AiRunner` provider
into ArtScene's `AiRunnerSocket` during runtime subsystem assembly.

This replaces the earlier retry note where ArtScene still reported an uninstalled
AI runner socket.

## Retry Command

Run from ArtScene:

```sh
cd /Users/asami/src/dev2026/textus-art-scene
scripts/check-stage5b-live-fetch-operation-report.sh
```

The script no longer changes `user.home` or the execution directory. It uses the
normal `cncf` launcher and an explicit local datastore path under
`target/cncf.d/stage5b-work`.

## Current Result

The Stage 5B script completes and regenerates:

- `/Users/asami/src/dev2026/textus-art-scene/target/cncf.d/stage5b-live-fetch-operation-report.json`
- `/Users/asami/src/dev2026/textus-art-scene/target/cncf.d/stage5b-live-fetch-operation-report.md`

Classification counts:

```text
facility_count: 49
official_success: 8
museum_or_jp_success: 29
ai_success: 0
unresolved: 12
audit_error: 0
```

The unresolved records are now provider/source coverage results, not socket wiring
failures.

## Assembly Report Evidence

Current admin report command:

```sh
cncf --config /Users/asami/src/dev2026/textus-art-scene/.cncf/launcher.yaml \
  /Users/asami/src/dev2026/textus-art-scene \
  command admin.assembly.report --format yaml
```

Relevant output:

```yaml
spi_sockets:
- component: ArtScene
  contract: ai-runner
  runtime_class: org.goldenport.cncf.spi.ai.runner.AiRunner
  installed: true
components:
  loaded:
  - name: TextusAi
    origin: active car textus-ai-runtime@0.2.0-SNAPSHOT
  - name: ArtScene
    origin: component-dev-dir
warnings:
  status: ok
  warning_count: 0
```

Interpretation:

- The assembly descriptor is being read.
- `TextusAi` is loaded from `textus-ai-runtime@0.2.0-SNAPSHOT`.
- `ArtScene` is loaded from the component dev directory.
- CNCF runtime assembly resolves the AI runner SPI socket after runtime extra
  components are added.
- ArtScene no longer needs an application-level `SpiResolver.resolve(...)`
  fallback.

## Follow-up Boundary

Remaining Stage 5B gaps should be treated as ArtScene source coverage/provider
work unless a later report again shows `spi_sockets.installed = false`.

Do not reintroduce:

- ArtScene-level `SpiResolver.resolve(...)` fallback;
- JVM system property injection for the provider;
- custom ArtScene fallback around CNCF assembly;
- weakening of the Stage 5B report classification.
