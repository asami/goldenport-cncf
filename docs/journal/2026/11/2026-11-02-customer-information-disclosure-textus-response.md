# Textus-wide response to customer information disclosure

Date: 2026-11-02
Status: cross-project assessment and planning record; not implementation or acceptance evidence

## Purpose and relationship to the mobile assessment

Assess disclosure by data flow and authority, across Web, smartphones, public
and administrative APIs, storage, operational systems and external services.
Web and mobile are different entry points into many of the same data boundaries.

The [mobile incident assessment](2026-11-02-mobile-security-incidents-and-textus-response.md)
remains the detailed account of Android/iPhone incidents, SDKs and media egress.
This journal supplies the broader customer-information perspective; it does not
replace that record or add a new security framework.

The [post-intrusion and physical-control assessment](2026-11-02-post-intrusion-protection-and-physical-control-options.md)
extends this view to compromised employee accounts, endpoints, services and
hosts. It records selectable guarantees, physical release conditions, residual
risks and operational costs, including the limits of current credential custody.
These options are not automatically added to active implementation phases.
The independent [physical-controls assessment](2026-11-02-physical-controls-protection-boundaries.md)
contains the detailed protection/cost comparison, conditional guarantee and
limits while media is attached, decrypted, displayed or exported.

Protected information includes accounts, personal/customer records, photo/audio
originals, OCR/transcripts, Material, Candidate, Information, Knowledge and their
private metadata, associations and sharing configuration. Public data remains
public according to deliberate policy; not every artifact is automatically secret.

## Classification and evidence limits

- **OUT OF SCOPE (対象外):** a specific product, feature or attack path is absent
  from the inspected deployment/scope. State that scope and reassess when the
  component or feature is introduced. Absence must not be inferred merely from
  not finding it in one manifest. Unverified applicability remains unknown.
- **STANDARD OPERATIONS (標準運用で対応):** use established platform/toolchain,
  dependency, identity, credential, storage, patch and deployment practices.
  This is an allocation of responsibility, not a claim of verified compliance.
- **ACCEPTANCE REQUIRED (受入確認が必要):** demonstrate the selected application's
  actual boundary through focused connected scenarios, reusing relevant evidence.
  This does not by itself imply missing code or require a new implementation.

One disclosure mechanism can need both operational controls and product
acceptance. Plans/specifications establish intent; only actual evidence proves
behavior. This assessment performed no runtime, packaged-artifact, credential,
cloud-sharing or production audit.

## Disclosure routes and Textus disposition

| Route | Web / smartphone applicability | Disposition and responsibility |
| --- | --- | --- |
| Object/Operation authorization failure: replace an ID or call an administrative function directly | Shared APIs, regardless of client | ACCEPTANCE REQUIRED: CNCF supplies common Operation and internal DSL/UnitOfWork Entity enforcement; Studio/TKL bind domain policy. UC-02/04 cover authorized controls and cross-user denial through public ingress, including internal service delegation. |
| Excessive response fields or writable properties: hidden UI data remains in responses, or owner/role can be changed | Shared detail/list/search/batch APIs | ACCEPTANCE REQUIRED: public input models and response projections enforce permitted fields. Reading an Entity does not grant every property or allow owner/role changes. CNCF owns common contracts; applications own their declared views/policies. |
| Account/session compromise: stolen user/admin credentials or tokens | Shared identity, with browser and native transport differences | STANDARD OPERATIONS plus ACCEPTANCE REQUIRED: textus-user-account owns authentication/session lifecycle; Core owns native custody and common client behavior; Web ingress owns browser binding. Verify revocation/logout and account scope. Operators manage privileged accounts, appropriate authentication strength and removal of obsolete access. |
| Vulnerable common software or server intrusion | Backend exposure is independent of client | Primarily STANDARD OPERATIONS: inventory deployed software, apply relevant updates, restrict management surfaces and disable unnecessary features. An unused product's specific vulnerability is OUT OF SCOPE only after inventory confirmation. Application authorization is not a substitute for fixing a compromised host/runtime. |
| Injection, XSS and CSRF | Database/query injection is shared; XSS and CSRF depend on Web rendering/session paths | ACCEPTANCE REQUIRED through common facilities: CNCF typed queries/input binding; UI-owned context-appropriate rendering; Web ingress protection appropriate to the actual browser authentication profile. Phase 107 covers these contracts. No blanket SQL keyword filter or per-handler security ritual. |
| Direct storage, shared-link or backup exposure | Bypasses both Web and mobile API controls | STANDARD OPERATIONS plus ACCEPTANCE REQUIRED: TKL and deployment owners maintain intended sharing and original/access-link permissions, and operators protect backups. An API denial cannot secure an independently public object. Check the actual provider route and link issuance; do not assume a private UI means private storage. |
| External integration credential theft or excessive delegated privileges | Shared downstream services, providers and SaaS | STANDARD OPERATIONS plus ACCEPTANCE REQUIRED: EAI/TKL/CNCF retain scoped provider authority, credential custody, revocation and bounded delegation. A service credential does not grant a caller all data reachable by that service. Inventory granted provider scope as well as application policy. |
| SSRF: URL import/preview/fetch reaches internal services or unintended destinations | Any client can trigger a vulnerable server fetch | ACCEPTANCE REQUIRED where URL retrieval exists: CNCF/EAI common outbound boundary handles destination policy and redirects, including internal/metadata targets. A specific nonexistent fetch path is OUT OF SCOPE; reassess on introduction. This is distinct from mobile SDK outbound traffic. |
| SDK, external AI, analytics, crash reporting or logs disclose content | Native SDKs, Web scripts and server-side providers/diagnostics | STANDARD CONFIGURATION plus ACCEPTANCE REQUIRED: Core/App MS-01/02 cover client paths; server/provider owners must map equivalent content, recipients, purpose and diagnostic handling. Originals, derived text and credentials need separate consideration. No assumption that on-device OCR means all surrounding components are offline. |
| Authorized bulk export, employee misuse or accidental sharing | Shared legitimate business functions | STANDARD OPERATIONS plus BUSINESS ACCEPTANCE where present: define export/sharing scope, retention, audit and proportionate response to abnormal bulk access. Ordinary read permission need not imply every export function. Authorization alone cannot stop all misuse of legitimately visible data. Do not require new approval for every normal action. |

## Why a shared-vulnerability-only response is insufficient

JPCERT/CC's [October 2026 advisory](https://www.jpcert.or.jp/at/2026/at260030.html)
describes multiple attack categories, including known vulnerabilities, API abuse
and management/configuration weaknesses. It does not attribute all recent
incidents to one common flaw. Its [technical investigation](https://blogs.jpcert.or.jp/ja/2026/10/web_api_attacks.html)
also documents app analysis followed by attacks on server APIs.

Shared-product exploitation does occur, as in the
[MOVEit advisory](https://content.govdelivery.com/accounts/USDHSCISA/bulletins/35ec2f8).
Shared-service compromise is another route: the
[Salesloft Drift investigation](https://cloud.google.com/blog/topics/threat-intelligence/data-theft-salesforce-instances-via-salesloft-drift)
describes stolen integration OAuth tokens used across customer environments.
These are different mechanisms and require different owner controls.

OWASP distinguishes [object authorization](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/),
[property authorization](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/)
and [SSRF](https://api-security.owasp.org/editions/2023/en/0xa7-server-side-request-forgery/).
The following Textus mapping is an architectural assessment, not evidence that
these vulnerabilities were found in Textus.

## Existing specifications and delivery owners

| Owner | Related specification | Existing plan / acceptance |
| --- | --- | --- |
| CNCF | [Security architecture](../../../design/textus-security-architecture.md), [Operation authorization](../../../design/operation-authorization-model.md), [Entity authorization](../../../design/entity-authorization-model.md), [credential custody](../../../spec/oauth-credential-custody.md), [UC-01..04](../../../spec/untrusted-client-material-acceptance.md) | [Phase 107](../../../phase/phase-107.md): typed queries, output/input boundaries, browser policy and authorization; [Phase 118](../../../phase/phase-118.md): applicable external execution confinement, not a substitute for user/data authorization |
| User Account / Flutter Core | [Account integration design](https://github.com/asami/textus-user-account/blob/main/docs/design/external-federation-security-integration.md), [Core authentication client](https://github.com/asami/textus-flutter-core/blob/main/docs/spec/user-account-authentication-client.md), [MS-01/02](https://github.com/asami/textus-flutter-core/blob/main/docs/spec/media-sdk-and-egress-acceptance.md) | [Core integration plan](https://github.com/asami/textus-flutter-core/blob/main/docs/phase/user-account-authentication-integration-plan.md): native then Web integration; User Account retains producer identity/session responsibility |
| Editing Studio App | [Collection/editing specification](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/spec/collection-and-editing-modes.md) | [Phase 6.10](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-6.10-checklist.md): Android/iPhone; [Phase 7](https://github.com/KnowledgeHubProject/nict-editing-studio-app/blob/main/docs/phase/phase-7-checklist.md): Web/Fold and connected acceptance |
| Studio server | [Two-mode client integration](https://github.com/KnowledgeHubProject/nict-editing-studio/blob/codex/ess3-server-editing/docs/spec/two-mode-flutter-client-integration.md) | [Phase 5](https://github.com/KnowledgeHubProject/nict-editing-studio/blob/codex/ess3-server-editing/docs/phase/phase-5-checklist.md): public ingress, account/resource continuity and downstream source association |
| TKL | [Mobile Material delivery](https://github.com/asami/textus-knowledge-lake/blob/main/docs/spec/tkl-managed-mobile-material-delivery.md) | [Delivery workstream](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/tkl-managed-mobile-material-delivery-plan.md), [Phase 2](https://github.com/asami/textus-knowledge-lake/blob/main/docs/phase/phase-2.md): ownership, admission, provider evidence and migration |
| EAI | [Secure integration architecture proposal](https://github.com/asami/textus-enterprise-application-integration/blob/d71aa7a2e6a15f69a2f2f6a34fad810e9c481af4/docs/journal/2026/2026-11-02-secure-integration-component-architecture.md) | [Phase 2](https://github.com/asami/textus-enterprise-application-integration/blob/main/docs/phase/phase-2.md): private artifacts, provider transfer/credentials, lifecycle and consumer handoff |

These references identify relevant responsibilities; they do not claim every
row above is already fully implemented or allocated to a checklist. Framework
presents Core capabilities, and Studio/Workbench/TKL consume common authority
contracts rather than inventing parallel security engines.

## Remaining coverage mapping and practical priority

The existing UC/MS additions explicitly cover client secrets, Operation/Entity
rights, Material admission and client SDK/media destinations. Phase 107 already
plans the broader Web/query boundaries; EAI Phase 2 plans external integration.

Further cross-project mapping is needed for response-field/search/export scope,
sharing links, actual storage/backup permissions, server logs/external AI egress,
integration credential revocation and any URL-fetch paths. Inspect existing
capabilities and evidence before deciding that code or new checklist items are
missing. Do not silently expand all current phases or reopen accepted work.

Prioritize one connected route with ordinary users A/B, a privileged operator
and an internal service identity. Verify that intended rights remain distinct
through UI, direct API, search results, source access and downstream delegation.
Then close relevant Web/SDK and provider/operational gaps. Reuse evidence for
unchanged paths; retain platform/configuration limits and one integrated review.

No universal anti-tamper engine, per-launch file comparison, custom signature
scheme, duplicated permission ledger or blanket reapproval flow follows from
this assessment. Keep platform security, provider controls and already agreed
TKL content-integrity checks. This journal records decisions and links only;
no new Phase, runtime change, completed test or release is asserted.

## Product baseline refinement — 2026-11-02

The [small-business product decision](2026-11-02-small-business-security-defaults-and-options.md)
and [normative contract](../../../spec/small-business-security-baseline-and-options.md)
make ordinary use without expert security settings the primary target.
Distinguish STANDARD product protection, CONFIGURABLE strengthening and EXTERNAL
services/equipment/operations. These complement this journal's threat-response
classifications; physical isolation is an optional extension, not the baseline.
