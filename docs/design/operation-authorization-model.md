# Operation Authorization Model

## Scope

Operation authorization is the selector-level authorization model for
Component / Service / Operation dispatch.

Common CNCF authorization vocabulary such as subject, privilege, role, scope,
capability, permission, access kind, and resource families is defined in
`authorization-concepts.md`. This document applies those concepts to operation
selector admission.

It is independent of Web. Command, server, client, REST, Web HTML, Form API,
and plain HTML FORM entry roots must all converge on the same Operation
authorization checkpoint before Operation logic starts.

## Runtime Rule

The canonical runtime carrier is `OperationAuthorizationRule`:

- `operationModes`: operation modes in which the Operation may run. Empty means
  no operation-mode restriction.
- `allowAnonymous`: whether an anonymous subject may invoke the Operation.
- `anonymousOperationModes`: operation modes in which anonymous invocation is
  allowed when `allowAnonymous` is true. Empty means anonymous invocation is not
  further restricted by operation mode.
- `minimumPrivilege`: minimum runtime/system privilege ceiling required for the
  operation.
- `roles`, `scopes`, and `capabilities`: operational policy requirements after
  the privilege ceiling has been satisfied.
- `deny`: explicit deny rule used for disabled built-in policy surfaces.

An Operation definition may supply the rule by implementing
`OperationAuthorizationProvider`. Generated CML Operations should use this path.
The current CNCF-side CML metadata hook is
`CmlOperationDefinition.operationAuthorization`; Cozy-generated components can
populate that field until generated `OperationDefinition` classes directly
implement `OperationAuthorizationProvider`.

Subsystem descriptors may also supply selector rules through
`operationAuthorization`. This is the minimum descriptor route for generated or
declarative authorization metadata:

```yaml
operationAuthorization:
  notice-board.notice.post-notice:
    allowAnonymous: true
    anonymousOperationModes: [develop, test]
  notice-board.notice.publish:
    operationModes: [production, demo]
```

The descriptor vocabulary is intentionally the same as the runtime rule so that
CML, component descriptors, and subsystem descriptors can all adapt into one
model.

## Checkpoint

`Subsystem` resolves ingress security first, then evaluates Operation
authorization before creating or executing the `ActionCall`.

The rule source order is:

1. `OperationAuthorizationProvider` on the resolved Operation definition.
2. `CmlOperationDefinition.operationAuthorization` on the generated component
   metadata for the resolved Operation.
3. `GenericSubsystemDescriptor.operationAuthorization` for the resolved selector.
4. no Operation authorization rule.

Operation implementations should not inspect `production` / `develop` mode or
anonymous-user settings for ordinary admission control.

The built-in admin component uses the shared
admin authorization policy. In `develop` and `test`, this preserves the
`OperationAuthorizationRule.developAnonymousAdmin` behavior. In `production`,
admin operations are denied by default; when explicitly enabled, access still
requires both a sufficient privilege ceiling and the configured admin roles.

`privilege` is not a replacement for roles. It is the CNCF runtime/system guard
and should remain coarse-grained. Role, scope, and capability carry the
operational policy.

Operation `capabilities` are subject-side grants. They are not Entity-local
owner/group/other permissions. Entity-local permissions are evaluated later at
the UnitOfWork/resource boundary, as described in
`entity-authorization-model.md`.

In the common authorization model, operation policy contributes selector-level
guards and resource/action requirements: it derives the privilege ceiling,
runtime-mode predicates, and subject-side roles/scopes/capabilities required to
invoke the selector. It does not derive Entity-local capabilities for target
objects.

## Relation To WebDescriptor

`WebDescriptor.authorization` is a Web ingress override or supplement. It is not
the source of truth for Operation authorization.

When a Web route admits a request, the target Operation still passes through
the Subsystem Operation checkpoint. This keeps Web/Form behavior aligned with
command, client, server, and REST behavior.

## Failures

Authorization failure is a structured `Consequence.securityPermissionDenied`
with the selector and denial reason attached to the observation descriptor.
The failure uses the common authorization diagnostic model:

- missing operation capability uses `Cause.Kind.Capability` plus a
  `Capability(...)` facet;
- privilege ceiling, runtime-mode, and other operation guards use
  `Cause.Kind.Guard` plus a `Guard(...)` facet;
- `Reason(...)` records the stable denial reason.

Runtime metrics, dashboard rows, and `authorization.decision` observability
events project their diagnostic records from the `Conclusion`. They do not emit
legacy failure-label fields or a parallel operation-local error label.

HTTP/Web entry points map the same failure to an HTTP admission response such
as 403. Command/client/REST style callers receive the structured failure or the
formatted response produced from it.
