# Optional Firebase server integration

Status: planned, unscheduled. Contract: [provider boundary](../design/firebase-provider-boundary.md).

| Work | Delivery and acceptance |
| --- | --- |
| Notification delivery | In the deployed notification component, connect one review request to a registered test installation through existing CNCF dispatch and the provider's preference and storage path. Bind registration to the authenticated account. Verify opt-out, token rotation and account switch, one inbox item under duplicate delivery, and normal inbox access after missed push. A failed FCM submission retains the accepted inbox item and reports the transport failure separately. Qualify actual delivery on a supported client and authorized read on open. |
| Ingress attestation | When a deployment selects a protected endpoint, qualify the Flutter token path and the server verifier together. Verify the expected project and application. Observe results before enabling enforcement. On the enforced route, exercise accepted, missing, invalid and wrong-app evidence, including a valid token with an unauthorized user/resource. The ordinary authorization rules must still deny that last case. |
| Deployment | Configure credentials, external effects and allowed destinations at the host. Qualify the enabled route in an isolated environment and the ordinary route with the optional integration disabled. Record the supported platform and failure behavior for operators. |

Use the existing provider and ingress interfaces first. Implement concrete FCM delivery in the provider-owning component. Add shared CNCF code only for a demonstrated gap in a common contract. Client-only diagnostics and presentation settings can ship independently. The first joint server/client acceptance is a notification that opens an authorized resource. Attestation can be selected separately.

Partner plans: [Flutter Core](https://github.com/asami/textus-flutter-core/blob/main/docs/phase/firebase-provider-plan.md) and [Flutter Application Framework](https://github.com/asami/textus-flutter-application-framework/blob/main/docs/phase/firebase-presentation-plan.md).
