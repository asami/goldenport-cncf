# CNCF Runtime Source-size Follow-up

status=open
date=2026-08-19
classification=non-behavioral source organization

| ID | Status | Repository / path | Evidence | Boundary and proposed follow-up |
| --- | --- | --- | --- | --- |
| HYG-H57-SIZE-002 | OPEN | `cloud-native-component-framework`; `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala` | The source is 6,083 lines at triage. Its CLI entrypoints, bootstrap, configuration, discovery, and execution helpers occupy one implementation unit, making whole-file review and localized maintenance unreliable. | Split only cohesive private implementation cohorts into package-private collaborators while retaining `CncfRuntime` as the public facade and preserving runtime behavior. |

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-SIZE-002
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff-02.md
Handed Off On: 2026-08-19
