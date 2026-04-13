# Entity Authorization Business Sample Follow-up

Date: 2026-04-14

## Context

The entity authorization implementation checklist is closed for the CNCF
security mechanism itself.  The remaining items are sample/business scenario
work and should not keep the security checklist open.

This follow-up tracks business-oriented examples and sample-app verification
that build on the implemented authorization model.

## Checklist

- [ ] Add SalesOrder business example after a dedicated SalesOrder sample app
      exists.
- [ ] Add sample app scenario exercising business/private entity defaults.
- [ ] Add sample app scenario exercising relation-based customer read.

## Notes

These items validate business-application usage of the security model rather
than the core authorization mechanism.

The SalesOrder example depends on generator support, so it should be handled
when the generated business entity surface is ready.

Generator support was checked on 2026-04-14 in cozy:

- `sbt "testOnly cozy.modeler.ModelerGenerationSpec -- -z authorization"`

The checked specs confirm that:

- CML entity classification fields are preserved in the Kaleidox entity model.
- Generated component descriptors carry `usageKind`, `operationKind`, and
  `applicationDomain`.
- Generated operation access metadata carries `operationModel`,
  `entityOperationKind`, `entityApplicationDomain`, relation rules, and
  natural ABAC conditions.

The remaining work is no longer blocked on generator support; it is now the
actual SalesOrder business example and sample-app scenario work.

On 2026-04-14, this follow-up was put on hold for sample-app scope reasons.
`textus-sample-app` is a notice-board sample and is not an appropriate driver
for SalesOrder business authorization tests.  The SalesOrder-related scenario
work should resume after a dedicated SalesOrder sample app is created.
