# Service Bus and Control Center Boundary

Date: 2026-09-24

Decision: Service Bus belongs in CNCF Runtime, at the same architectural level as Job Management and Workflow/StateMachine. CNCF Dashboard/Admin will expose basic Service Bus and journal operational information for runtime administration and debugging.

Textus Control Center is the higher-level operational surface. It uses CNCF views/APIs and authoritative journal records to build meaningful timelines and cross-subsystem/application views, and combines them with non-CNCF resources such as databases, OpenTelemetry, OpenClaw and AI runtimes.

The boundary is: CNCF UI explains/operates the runtime mechanism; Control Center explains/operates the Textus system.
