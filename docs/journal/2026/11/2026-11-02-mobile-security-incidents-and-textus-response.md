# Mobile security incidents and the Textus response

Date: 2026-11-02
Status: assessment and planning record; not implementation or acceptance evidence

Broader assessment: [Textus-wide customer information disclosure response](2026-11-02-customer-information-disclosure-textus-response.md).
This mobile journal supplies the Android/iPhone and SDK detail within that
cross-project view; neither record establishes runtime acceptance.

## Decision and classification

Classify the mechanism behind each Android/iPhone incident against the actual
Textus architecture. An incident does not automatically justify a new feature.
The requested approach follows the GMO-style distinction discussed with the
developer; the original GMO assessment was not located in this investigation,
so this record does not assert its content or conclusions.

Use three dispositions, separately for each mechanism when necessary:

- **OUT OF SCOPE (対象外):** the specific product/function or attack path is
  absent from the inspected scope. Record the scope and reassessment trigger;
  this is not a claim that the wider threat cannot affect Textus.
- **STANDARD OPERATIONS (標準運用で対応):** use ordinary platform/toolchain,
  dependency, distribution, credential and storage-permission management.
  This identifies the appropriate control, not proof that the current machine
  or deployment already satisfies it.
- **ACCEPTANCE REQUIRED (追加の受入確認が必要):** exercise a relevant Textus
  boundary through the existing UC-01..04 or MS-01/02 scenarios. Reuse applicable
  evidence and keep gaps open; do not infer a need for a new implementation.

## Incident-to-response assessment

| Incident and evidence | Disposition | Textus response and owner |
| --- | --- | --- |
| XcodeGhost (2015): modified Xcode injected malicious code into shipped iOS apps, including WeChat. [Research](https://unit42.paloaltonetworks.com/malware-xcodeghost-infects-39-ios-apps-including-wechat-affecting-hundreds-of-millions-of-users/) | STANDARD OPERATIONS | Workstation/build owner obtains Xcode from Apple, preserves Gatekeeper and uses platform signature validation. Follow [Apple guidance](https://developer.apple.com/library/archive/qa/qa1900/_index.html). No Textus-specific toolchain tamper detector is needed. Current host compliance was not audited. |
| BadBazaar (2023): trojanized Signal Plus Messenger/FlyGram distributed espionage code; these were modified clients, not evidence that the official Signal/Telegram apps were compromised. [ESET](https://www.welivesecurity.com/en/eset-research/badbazaar-espionage-tool-targets-android-users-trojanized-signal-telegram-apps/) | OUT OF SCOPE for Signal-specific linking; STANDARD OPERATIONS for distribution; ACCEPTANCE REQUIRED for server rights | App owner manages official distribution and signing. CNCF/Studio/TKL retain UC-02/04 even for a client imitating the official API. Server authorization cannot protect information that a user directly gives to a malicious client within that user's legitimate access. |
| CamScanner (2019): malicious code arrived through an advertising library. [Kaspersky](https://www.kaspersky.com/blog/camscanner-malicious-android-app/28156/) | OUT OF SCOPE for the advertising path in the inspected direct dependencies; STANDARD OPERATIONS plus ACCEPTANCE REQUIRED for general SDK risk | No advertising SDK was identified in inspected App/Core direct dependencies. This is not a complete packaged/transitive dependency clearance. Maintain normal dependency provenance/update practices and apply MS-01 to actual packaged SDKs. No advertising-specific subsystem is needed. |
| Embedded AWS credentials in mobile apps (2022) and misconfigured Android cloud services (2021). [Symantec](https://www.security.com/threat-intelligence/mobile-supply-chain-aws), [Check Point](https://research.checkpoint.com/2021/mobile-app-developers-misconfiguration-of-third-party-services-leave-personal-data-of-over-100-million-exposed/) | OUT OF SCOPE for an unused provider's specific exploit; STANDARD OPERATIONS and ACCEPTANCE REQUIRED for the mechanism | The reports describe discovered exposure/vulnerabilities, not confirmed criminal exploitation of every app. For Textus, server-key custody and private storage/share configuration are standard operations; UC-01/02/04 verify actual distribution and access boundaries. Core owns client credentials, TKL owns storage policy and CNCF owns common authorization. An otherwise protected API cannot secure an independently public storage object/link. |
| SparkCat (2025): malicious Android/iOS code used OCR on accessible images and sent selected images to an attacker. The report does not establish whether every infected app was a supply-chain victim or intentionally malicious. [Kaspersky](https://securelist.com/sparkcat-stealer-in-app-store-and-google-play/115385/) | OUT OF SCOPE for cryptocurrency-wallet functionality; ACCEPTANCE REQUIRED for media exfiltration | Textus captures photos/audio and derives text, so MS-01/02 directly apply. Core assesses SDK/native networking and processing behavior; App qualifies the actual package, permissions, recipients and UI route. Server-side authorization cannot prevent malicious local code from exporting media it can already read. |
| TikTok Android (2022): a deep-link validation bypass reached arbitrary WebView content and exposed JavaScript bridges. Microsoft reported a fix and no evidence of in-the-wild exploitation. [Microsoft](https://www.microsoft.com/en-us/security/blog/2022/08/31/vulnerability-in-tiktok-android-app-could-lead-to-one-click-account-hijacking/) | OUT OF SCOPE for the specific path, provisionally | Searches of the selected App/Core dependency manifests, lockfiles, Dart and native source paths found no matching WebView/JavaScript bridge. This is not a whole-product binary audit. Reassess if a WebView, privileged bridge or relevant deep-link integration is introduced. An ordinary Flutter Web UI is not itself this attack path. |

## Textus-specific observation: ML Kit

The inspected [Core dependency declaration](https://github.com/asami/textus-flutter-core/blob/main/pubspec.yaml)
includes ML Kit text recognition and barcode scanning. SparkCat's abuse of OCR
does not show that ML Kit itself was compromised or that these dependencies are
infected. The [App dependency declaration](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/pubspec.yaml)
also selects a task-checkout Android GenAI package; final-package assessment
must follow the actually resolved dependencies rather than only canonical Core.

Google's [ML Kit terms/privacy](https://developers.google.com/ml-kit/terms)
state that image/text input and output processing stays on-device, while model/
compatibility updates and performance/utilization metrics can contact Google.
Its [Android disclosure guidance](https://developers.google.com/ml-kit/android-data-disclosure)
describes diagnostic data such as device/app information and performance metrics.
GenAI APIs additionally have their own terms; qualify the selected API/version.

MS-02 therefore distinguishes:

1. Original media and derived text transmission.
2. Model/update downloads and compatibility requests.
3. Diagnostic/usage metrics.

Network activity alone is not proof of media leakage. Conversely, on-device OCR
does not prove the surrounding plugin/application sends nothing. Confirm actual
configuration and recipients, and accurately describe collection in the app's
privacy information. No SDK removal is decided solely because OCR was used by
malware. No runtime traffic or packaged-artifact acceptance was performed here.

## Specification and delivery links

The linked specifications remain normative; this journal records rationale and
scope. Existing checked items, closed phases and historical evidence are retained.

| Owner | Specification | Development / acceptance allocation |
| --- | --- | --- |
| CNCF | [UC-01..04 common acceptance](../../../spec/untrusted-client-material-acceptance.md), [security architecture](../../../design/textus-security-architecture.md) | [Phase 107](../../../phase/phase-107.md), especially P107-04/05 common input/authorization boundaries; consumer work does not wait for unrelated Phase work |
| Flutter Core | [MS-01/02 SDK and media egress](https://github.com/asami/textus-flutter-core/blob/main/docs/spec/media-sdk-and-egress-acceptance.md), [authentication client](https://github.com/asami/textus-flutter-core/blob/main/docs/spec/user-account-authentication-client.md) | [Common integration plan](https://github.com/asami/textus-flutter-core/blob/main/docs/phase/user-account-authentication-integration-plan.md): TFC-UA01 inventory, UA02 native, UA03 Web |
| Editing Studio App | [Collection/editing specification](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/spec/collection-and-editing-modes.md) | [Phase 6.10 checklist](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-6.10-checklist.md): Android/iPhone; [Phase 7 checklist](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-7-checklist.md): Web/Fold deltas |
| Studio server | [Client integration specification](https://github.com/KnowledgeHubProject/nict-editing-studio/blob/codex/ess3-server-editing/docs/spec/two-mode-flutter-client-integration.md) | [Phase 5 checklist](https://github.com/KnowledgeHubProject/nict-editing-studio/blob/codex/ess3-server-editing/docs/phase/phase-5-checklist.md): ESS5-01/02/05, operation/resource rights and registered-source association |
| TKL | [Mobile Material delivery](https://github.com/asami/textus-knowledge-lake/blob/main/docs/spec/tkl-managed-mobile-material-delivery.md) | [Current delivery acceptance](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/tkl-managed-mobile-material-delivery-plan.md), [Phase 2 EAI migration](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-2.md): preserve ownership/admission and provider evidence |

UC-03 (reject fabricated upload completion) derives from Textus's delivery
contract. It is not claimed to be an attack observed in these incident reports.
Framework remains the presentation facade for Core; account/session authority
remains textus-user-account. No new authentication or authorization engine is
assigned to the App.

## Operational outcome

Use official tooling/distribution, normal dependency management and provider
permission/secret configuration for the operational cases. Qualify missing
UC/MS scenarios through the existing connected delivery and integrated review.
Record an unobserved condition as unknown rather than asserting acceptance.

This decision does not add a custom anti-tamper system, signature scheme,
per-launch unchanged-file proof, copied-artifact comparison, management-hash
ledger or permanent network-monitoring feature. Existing platform security and
the separately authorized TKL content-integrity contract are retained. No new
Phase, runtime implementation, validation run or release is established here.
