# Factory-owned composition context

CNCF Phase 88 prerequisite for the admitted sm-workflow Phase 2 consumer.
User-directed parent limited recovery owns this bounded slice without agents.
Only Component.scala metadata preparation and the adjacent initialization
bootstrap example were changed; pre-existing dirty work was preserved.

## Implementation and executable evidence

CNCF now prepares the already resolved owner/core/instance/parameter metadata
inside the existing Factory flow, before its existing service-composition hook.
The preparation is framework-private. It does not construct services or invoke
ordinary component initialization; ordinary initialization subsequently refreshes
the selected core, constructs services and invokes the component hook once.
No new public extension contract or production test ExecutionContext was added.

The adjacent bootstrap example checks the actual Subsystem and named instance,
the same immutable resolved parameter snapshot and owner job engine, absence of
services before composition, absence of authenticated actor provenance in the
composition context, one ordinary initialization, malformed-parameter rejection
before the hook, and composition rejection without exposed services. Existing
storage/provenance examples remain part of the regression.

All invocations used fresh typed permission metadata and the shared serial
wrapper under user-directed parent limited recovery, without delegation.
Each terminal summary was read and its matching lock release observed.

| Invocation suffix | Outcome |
| --- | --- |
| 641 | New example compile failure: unsupported exception matcher syntax; corrected to the existing ScalaTest form. |
| 642 | 32/33: named parameter fixture lacked its canonical descriptor; corrected fixture, not runtime admission. |
| 643 | 32/33: fixture expected no final services, but standard meta/system services are supplied; corrected expectation. |
| 644 | New example compile failure: Service has no direct name accessor; corrected to serviceDefinition.name. |
| 645 | Focused producer regression: 33/33, four suites, zero failures/aborts. |
| 646 | Producer storage/parameter/provenance regression: 75/75, ten suites; publishLocal succeeded. |
| 647 | Consumer Compile / packageBin succeeded; GoalPhase plus ComponentFactory regression: 200/200, 28 suites. |

Final evidence summaries:

## Remaining boundary

This closes the owner-context initialization prerequisite only. The default
sm-workflow Factory still exposes generated placeholders; production contextual
binding, standalone launcher/bootstrap and independent-process/server evidence
remain unfinished. Composition metadata is neither ingress authentication,
human approval nor command authority. No pool ownership, grant, issuer/verifier,
CML/ABI, commit, push or release boundary was changed in this slice.

> Local execution archive: `~/backup/journal/2026/11/cloud-native-component-framework/cncf-journal-cleanup-20261116/a-46519ec7-ea0d-401b-ac84-b5c5c87060f6/docs/journal/2026/10/2026-10-01-factory-owned-composition-context.md.gz`
