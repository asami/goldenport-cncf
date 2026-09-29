# Job User and Operator Experience Design

status=accepted
phase=69.6
slice=JM69-08A

The experience layer is a boundary adapter around `JobEngine` management
queries. `JobExperienceService` builds a scoped `JobQueryPolicy`, applies it
before query filtering/count/order/page/exact read, and maps only canonical
bounded values to records and HTML. `JobExperiencePolicy` composes the prior
session-first owner predicate with Mine, Application, and Operator admission.
The policy's visibility key is empty for existing callers; nonempty UX policies
bind management cursors to the normalized scope and application.

`JobExperienceView` owns presentation vocabulary, bounded progress, result
availability, and admitted controls. It owns no state. Diagnostic projections
redact task results and timeline notes at the edge. `JobControlPolicy` and the
engine remain authoritative for mutations and transition/replay outcomes.

The notification inbox is provider-owned. `UserNotificationInboxProvider`
extends the unchanged send provider only for implementations that offer inbox
persistence. `UserNotificationInboxRuntime` resolves that provider from current
component/subsystem wiring, validates returned page and exact-entry invariants,
and projects public fields. It contains neither notification storage nor a
success/unread cache. The event forwarder similarly delegates stable dedupe to
the provider and records accepted/refused/failure diagnostics without mutating
Job state.

`JobExperienceProtocol` supplies the only service descriptor, actions,
record/JSON output, Help metadata, OpenAPI methods, and REST shape. Its codec is
shared with Web parsing. `Http4sHttpServer` keeps descriptor ingress checks and
resolves the Job-control execution context before invoking the facade. The
narrow renderer contains escaped no-JavaScript HTML; forms reenter the standard
form/CSRF dispatcher, while `JobExperiencePolling` supplies a pure bounded
admission state machine used by the progressive detail script.

This design deliberately leaves final retained-result semantics, durable
integrity, retention/delete operations, and operational assurance to Phase
69.7. The catalog names those unavailable capabilities rather than presenting a
destructive or unsupported action.
