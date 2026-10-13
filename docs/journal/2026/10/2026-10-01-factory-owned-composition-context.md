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

- Producer focused: /tmp/skill.cncf.d/a-84518e13-16d0-4cab-8481-7e7917d8663b/90103-20261001T142411Z.summary.json
- Producer regression and local refresh: /tmp/skill.cncf.d/a-cf8e6e42-a508-4091-a464-13bfdaa6a154/90769-20261001T142538Z.summary.json
- Consumer package and regression: /tmp/skill.cncf.d/a-478fc636-217d-4ece-8ab5-098067eb21a6/91481-20261001T142715Z.summary.json

The refresh was ordinary local dependency-artifact refresh of
org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT, not a release or remote upload.
All final invocations have sbt_exit=0, wrapper_exit=0 and lock=released.

## Remaining boundary

This closes the owner-context initialization prerequisite only. The default
sm-workflow Factory still exposes generated placeholders; production contextual
binding, standalone launcher/bootstrap and independent-process/server evidence
remain unfinished. Composition metadata is neither ingress authentication,
human approval nor command authority. No pool ownership, grant, issuer/verifier,
CML/ABI, commit, push or release boundary was changed in this slice.
