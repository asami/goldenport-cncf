# Firebase provider boundary

Decision adopted 2026-11-08. Textus deployments may select Firebase capabilities individually. CNCF owns the integration contracts at the server boundary. Each deployment chooses its permitted providers, credentials, destinations and endpoint policies.

| Capability | Server ownership |
| --- | --- |
| Cloud Messaging (FCM) | A domain service addresses a user through CNCF's [notification contract](../../src/main/scala/org/goldenport/cncf/usernotification/UserNotificationProvider.scala). The [runtime](../../src/main/scala/org/goldenport/cncf/usernotification/UserNotificationProviderRuntime.scala) invokes the first enabled provider. Extend the selected provider with an FCM delivery adapter so its preferences, quiet hours and durable notification record also apply to push. The existing [Textus provider](https://github.com/asami/textus-user-notification/blob/main/src/main/scala/org/simplemodeling/textus/usernotification/impl/ComponentFactory.scala) is the integration owner for that deployment. |
| App Check | A configured ingress adapter verifies client attestation for its intended Firebase project and permitted application. Endpoint policy consumes its result. UserAccount authenticates the user. The existing CNCF authorization path evaluates the Operation and target resource for that user. A valid attestation grants no additional rights. |
| Crashlytics | Client diagnostic collection belongs to the Flutter provider and the deploying application. An operator may correlate approved diagnostic identifiers with existing CNCF traces. Material, credentials and raw domain payloads stay outside telemetry. |
| Remote Config | The client consumes presentation settings with local defaults. Trusted host configuration determines operation mode, authorization and permitted external effects. TKL determines Material destinations. |

For push, the server binds each installation to the authenticated account and handles token rotation, logout and account changes. The payload contains a minimal prompt. Opening it reads current data through the authenticated domain API. Provider acceptance records the durable notification. The FCM adapter records its submission result separately from delivery, user action and domain completion. Missed, delayed or duplicate delivery leaves normal inbox/domain access available.

For attestation, deployments can observe verification outcomes before enabling enforcement on a selected endpoint. An enforced endpoint denies missing or invalid evidence. A deployment with no attestation requirement continues to use its ordinary authentication and authorization contract. Firebase credentials and verification policy belong to trusted deployment configuration.

The [Flutter counterpart](https://github.com/asami/textus-flutter-core/blob/main/docs/design/firebase-provider-boundary.md) owns client contracts and platform adapters. Textus UserAccount owns identity and session lifecycle, TKL owns Material identity and storage placement, and Core owns the pending submission queue. Firebase Authentication, Firestore and Firebase Storage would each require their own domain owner's decision.

See the [server development plan](../phase/phase-121.md) and [decision journal](../journal/2026/11/2026-11-08-firebase-provider-boundary.md).
