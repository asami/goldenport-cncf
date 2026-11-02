# Small-business security baseline and selectable strengthening

Decision date: 2026-11-02
Status: normative product direction; implementation and deployment qualification pending
Authority: [Textus security architecture](../design/textus-security-architecture.md)

## Product objective

Textus provides small and medium-sized organizations with usable business
applications without requiring expert security configuration. A supported
standard deployment supplies the common protections below by default. Users
decide business facts such as members, roles and intended sharing; they should
not configure individual authorization guards, cryptographic parameters or
low-level network policies for ordinary use.

“Acceptable ordinary business security” means passing the published Textus
baseline for the declared deployment and feature scope. It is not certification,
regulatory compliance for every industry, absence of all vulnerabilities or a
guarantee against privileged host/physical compromise. This specification alone
does not qualify a build or deployment.

## Three delivery categories

| Category | Required behavior | Customer effort |
| --- | --- | --- |
| STANDARD — protected without expert settings | Identity/session integration, Operation and resource rights, safe data handling and supported deployment defaults are present in the normal route | Establish the organization/admin, members, business roles and intended sharing; ordinary provider onboarding where needed |
| CONFIGURABLE — stronger policy through supported settings | Business-level choices consistently affect relevant ingress, runtime, storage, clients and integrations; all baseline invariants remain | Choose permitted sharing/export/processing, stronger administrator authentication or a supported access/retention policy |
| EXTERNAL — additional services, equipment or procedures | An explicitly selected integration/operational arrangement adds a stated guarantee only after prerequisites and acceptance | Provide identity/device-management services, security keys, HSM, isolated workstations/media or custodians as applicable |

A choice may span categories: requiring a hardware security key can be a policy
setting, while procuring, registering and recovering those keys is EXTERNAL.
A visible setting must not pretend to provide missing hardware, infrastructure
or implementation. Unsupported settings are unavailable with an explanation;
missing prerequisites do not silently downgrade the selected protection.

## Standard protection — non-optional invariants

Supported deployments and generated applications must provide:

- Authentication/session handling through the chosen supported identity owner;
  no fixed shared development identity in the ordinary production route.
- Operation, Entity/resource and applicable property boundaries on public and
  internal paths. UI visibility, internal IPs or service credentials do not
  confer additional user authority. No customer switch is needed to reject
  another user's private Material or a forbidden administrative Operation.
- Explicit input contracts, parameterized data access and safe output handling;
  browser session protections appropriate to the selected transport. Web and
  native share business authorization while retaining platform-specific handling.
- Non-public customer storage by default, explicit authorized sharing and safe
  issuance of source-access references. Registration uses the existing TKL
  verification contract; client completion claims alone cannot establish it.
- Supported credential custody and encrypted transport, with no server secrets
  in client bundles and no credentials/content in ordinary diagnostic output.
  Declare custody limits; owner-only plaintext files are not hardware isolation.
- No undeclared external content processing or SDK collection. External AI
  processing starts only through the explicitly selected, disclosed feature/
  provider policy; normal TKL-managed delivery follows its authorized route.
- Useful security events through existing observability, without making logs
  another store of originals or personal data. Common protections must be
  supplied by framework/provider paths, not manually recreated by each app.

The standard deployment offering also documents supported updates, management
surface exposure, backup/restore and incident/revocation procedures. A managed
offering may perform these for the customer; a self-hosted offering identifies
the operator's tasks with a bounded procedure. Merely documenting backup does
not mean it exists: qualify actual configuration and recovery at deployment
scope. No claim that application defaults remove all operational responsibility.

## Business-oriented strengthening settings

Initial policy vocabulary should address these choices using existing contracts:

| Business choice | Consistent consequence to define |
| --- | --- |
| May information be shared outside the organization? | Authorized recipient/link scope in application and TKL/provider paths; no UI-only restriction |
| May photos, audio or text be sent to external AI? | Selected provider, content classes and disclosed purpose; no silent fallback or bypass through SDK/server jobs |
| Who may export many records or originals? | Declared export Operation/resource scope, independent of whether an export button is visible |
| Must privileged login use stronger authentication? | Selected IdP/User Account capability, client support and recovery path; no local bypass that weakens the policy |
| What supported retention/access policy applies? | Actual data/derivative/backup behavior and dependencies; selecting retention does not silently delete permanently retained originals contrary to their domain contract |

These are product policy concepts, not invented wire parameters or a requirement
to implement every option in the current demo. Producers first map each choice
to supported behavior and expose coherent defaults. A few supported presets may
help; do not create a large independent security-settings engine or contradictory
per-component toggles. Strengthening cannot disable the standard invariants.

## External strengthening and bounded guarantees

Corporate IdP/device management, HSM/isolated key services, dedicated approval
workstations, disconnected encrypted media and multi-custodian release are
optional deployment capabilities. They must declare protected assets, attacker
capabilities, equipment/custody, operation costs, recovery and unavailable modes.
Do not make them prerequisites for the ordinary business baseline.

The [physical-controls assessment](../journal/2026/11/2026-11-02-physical-controls-protection-boundaries.md)
defines the separate limits during connection, decryption, display and export.
An online search index or OCR copy can defeat a claim that the corresponding
content is physically isolated. Application/root compromise and physical
custodian compromise require different assumptions, not a single “secure” flag.

## Acceptance and delivery

| ID | Acceptance |
| --- | --- |
| SB-01 — standard route | A supported fresh setup with only necessary organization/provider onboarding runs the normal business route; default rights, storage, credential, input/output and diagnostic protections work without expert tuning. Exercise authorized and unauthorized users through actual APIs. |
| SB-02 — policy consistency | For each supported strengthening choice, UI and direct/API/background paths enforce the same meaning, preserve baseline rights and explain the outcome. Unsupported choices/prerequisites cannot be represented as active protection. |
| SB-03 — truthful scope and usability | Documentation/UI distinguishes default protection, optional policy and external requirements. A non-specialist can complete ordinary setup/use; actual deployment evidence and outstanding operator tasks are visible. Equipment-dependent guarantees are not claimed from source tests. |

Reuse [UC-01..04](untrusted-client-material-acceptance.md), client MS-01/02 and
existing owner evidence. These are acceptance allocations, not new per-check
review cycles. Close missing behavior in its shared owner; do not duplicate
authorization, file-state comparison ledgers or per-action approval flows.

[Phase 107](../phase/phase-107.md) owns CNCF baseline/policy contracts. Core and
Framework expose shared client behavior; Studio/App qualify the selected route;
TKL owns Material policy; EAI owns accepted provider effects; User Account owns
identity/session capability. The
[decision journal](../journal/2026/11/2026-11-02-small-business-security-defaults-and-options.md)
maps their existing workstreams. No closed Phase is reopened, and current demo
work accepts its supported scope without waiting for every optional feature.

## Runtime-mode defaults — 2026-11-02

The [shared mode contract](runtime-mode-security-defaults.md) defines dev/test/
demo/production defaults under this baseline. Development convenience uses
scoped fixture identities and isolated resources; the same Operation/resource
authorization remains active. Participant demo is protected according to its
exposure and real data, not treated as a low-security environment. Common
foundations own the defaults; applications do not duplicate mode/auth logic.
