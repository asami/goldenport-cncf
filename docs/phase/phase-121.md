# Phase 121 — Firebase notification delivery and ingress attestation

status=planned
planned_at=2026-11-08
execution_started=false
owner=CNCF

Progress: [Phase 121 checklist](phase-121-checklist.md). Scheduling is deferred. The first item on selection is P121-01.

## Goal

Connect Firebase-backed delivery and optional app attestation through existing CNCF interfaces for deployments that select them. The deployed notification component owns FCM delivery, and ingress owns verification of app evidence. See the [CNCF provider boundary](../design/firebase-provider-boundary.md).

## Delivery groups

| Subphase | Step | Observable outcome |
| --- | --- | --- |
| 121.1 Notification delivery | P121-01 | Deliver one review notification using the existing provider and read the item through the authenticated API. |
| 121.2 Ingress attestation | P121-02 | Qualify the project and application verification policy on one protected endpoint. |
| 121.3 Deployment and handoff | P121-03 | Qualify the configured route and the ordinary route with the integration disabled. |

## Dependencies and completion

Start with one review notification that opens an authorized resource. Coordinate push with Core and Framework using the existing notification provider and its durable inbox. Attestation can be selected independently of the notification deployment. Shared CNCF changes are limited to demonstrated gaps. Coordinate the relevant capabilities with [textus-flutter-core Phase 4](https://github.com/asami/textus-flutter-core/blob/main/docs/phase/phase-4.md) and [textus-flutter-application-framework Phase 5](https://github.com/asami/textus-flutter-application-framework/blob/main/docs/phase/phase-5.md).

The checklist records each Step's acceptance criteria and the Phase closure decision.
