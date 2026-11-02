# Small-business security defaults and strengthening options

Date: 2026-11-02
Status: accepted product direction; implementation and deployment acceptance pending

## Decision

Textus's primary target is a business system that small and medium-sized
organizations can use without difficult security configuration. A supported
standard setup should meet an explicit ordinary-business security baseline.
Users decide business roles and sharing, not each guard, cryptographic parameter
or network setting. Stronger protection remains a selectable extension.

The normative [baseline and options specification](../../../spec/small-business-security-baseline-and-options.md)
and [security architecture](../../../design/textus-security-architecture.md)
separate three categories:

| Category | Scope | Customer responsibility |
| --- | --- | --- |
| STANDARD | Common authentication/session integration, Operation/resource rights, safe input/output, private storage, secret protection, intended data destinations and useful redacted audit evidence | Necessary organization/member/role/provider onboarding, plus clearly identified standard operational tasks |
| CONFIGURABLE | Supported business choices for external sharing, AI processing, bulk export, stronger privileged authentication and access/retention | Choose business policy; Textus consistently applies the supported choice across components |
| EXTERNAL | Enterprise IdP/device management, security keys, HSM, dedicated workstations, isolated media and custodians | Supply and operate the selected services/equipment, with explicit costs and recovery procedures |

Security categories are not a single slider. Hardware-key enforcement includes
both a policy setting and external key provision/recovery. Unsupported options
must be unavailable or explain prerequisites, never silently fall back. Standard
authorization and private-data protections cannot be disabled by a strengthening
setting. A shared service credential cannot widen a user's authority by default.

## Relationship to preceding assessments

The [Textus-wide disclosure journal](2026-11-02-customer-information-disclosure-textus-response.md)
maps leakage routes and owners; the
[mobile journal](2026-11-02-mobile-security-incidents-and-textus-response.md)
supplies platform/SDK examples. Their OUT OF SCOPE / STANDARD OPERATIONS /
ACCEPTANCE REQUIRED classifications remain useful: those describe the response
to a threat, whereas this decision describes the product and deployment offering.

The [post-intrusion journal](2026-11-02-post-intrusion-protection-and-physical-control-options.md)
and [physical-controls journal](2026-11-02-physical-controls-protection-boundaries.md)
remain advanced-option references. They must not make expensive physical
procedures the default prerequisite for ordinary business use.

“Ordinary-business acceptable” is demonstrated by the declared Textus baseline,
not asserted as universal certification, regulatory compliance or immunity to
administrator/physical compromise. Standard operations such as updates and
restoration remain necessary; a managed offering performs them, while a
self-hosted offering exposes a bounded operator procedure. No current deployment
is marked qualified by this journal.

## Delivery allocation

| Owner / existing plan | Allocation |
| --- | --- |
| [CNCF Phase 107](../../../phase/phase-107.md) | Standard invariants and supported policy semantics; qualify SB-01..03 using existing UC/common security evidence |
| [Core authentication integration](https://github.com/asami/textus-flutter-core/blob/main/docs/phase/user-account-authentication-integration-plan.md) | Shared native/Web authentication, credential and policy-result presentation contract; preserve [MS-01/02](https://github.com/asami/textus-flutter-core/blob/main/docs/spec/media-sdk-and-egress-acceptance.md) |
| [App 6.10](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-6.10-checklist.md) / [App 7](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-7-checklist.md) | Ordinary mobile/Web business route with necessary onboarding only; no developer token, storage-ID or per-guard settings; accept supported choices without inventing a broad security console |
| [Studio Phase 5](https://github.com/KnowledgeHubProject/nict-editing-studio/blob/codex/ess3-server-editing/docs/phase/phase-5-checklist.md) | Standard participant identity, role/resource boundaries and deployment contract; UI and direct API behavior agree |
| [TKL delivery](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/tkl-managed-mobile-material-delivery-plan.md) / [TKL Phase 2](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-2.md) | Private/default Material storage, configured sharing/provider policy and truthful registration/access; operators provision physical storage, mobile users do not |
| [EAI Phase 2](https://github.com/asami/textus-enterprise-application-integration/blob/main/docs/phase/phase-2.md) | Private artifacts and intended provider transfer by default; customer policy does not silently expand destination/credential authority |

User Account retains identity/session authority and Framework presents Core
facilities. Use existing supported owner APIs; missing policy capability becomes
a specific producer requirement rather than duplicated app code.

## Acceptance strategy

SB-01 accepts a supported standard setup with necessary onboarding and default
positive/negative authorization; SB-02 checks each supported policy consistently
across UI/direct/background paths; SB-03 checks understandable setup, limits and
external requirements. Reuse UC/MS evidence and one integrated review.

Current phases deliver the baseline applicable to their selected features and
record supported/unavailable strengthening choices. They do not wait for every
optional IdP, HSM, offline-storage or policy-editor feature. Preserve existing
IDs, completed histories and scope; no new Phase or universal approval/anti-tamper
framework is introduced. This change is documentation only, not test evidence.
