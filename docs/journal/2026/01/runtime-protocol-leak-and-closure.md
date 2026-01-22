----------------------------------------------------------------------
Journal Declaration
----------------------------------------------------------------------

This document is a journal entry.

It records a concrete incident observed during Phase 2.8 / 2.85
(the leakage of runtime-level options such as --baseurl into
Domain selector resolution), and the design reasoning that led
to the current clarification of responsibilities between
Runtime Protocol and Domain Protocol.

This document is NOT a canonical specification.

The canonical and authoritative definition of GlobalProtocol
lives in:

  docs/design/global-protocol.md

This journal exists to preserve:
- the context in which the problem occurred,
- incorrect approaches that were intentionally rejected,
- and the reasoning process that justified the final design direction,

so that the same class of failure is not reintroduced in the future.

======================================================================
DRAFT — GlobalProtocol (Phase 2.8 / 2.85)
======================================================================

status = draft
owner = Phase 2.8 / 2.85
scope = design / specification (mutable)

----------------------------------------------------------------------
Purpose
----------------------------------------------------------------------
GlobalProtocol は CNCF の全実行サーフェス（CLI / Script / HTTP）に共通する
「プロトコル上の語彙（parameter）と責務境界」を定義する。

狙いは以下:
- Runtime Protocol と Domain Protocol の責務分離を仕様として固定する
- runtime-level parameter を定数化し、CLI実装/Config/Client/Serverで一貫させる
- `--baseurl` 等が Domain selector に漏れないことを設計で保証する

GlobalProtocol は「前段で消費される語彙」と
「後段へ流れる語彙」を分類し、処理順序を規定する。

----------------------------------------------------------------------
Scope
----------------------------------------------------------------------
This document defines:
- GlobalProtocol Vocabulary (parameter catalog)
- Stage boundary between Runtime Protocol and Domain Protocol
- Propagation rule for unconsumed switches/properties
- Expansion mapping to:
  - RuntimeProtocol (parser / binder)
  - Config schema (.cncf/config.yaml future)
  - GlobalRuntimeContext materialization
  - HTTP client defaults

Out of scope:
- Canonical/Alias/Suffix resolution rules (Domain Protocol; see canonical-alias-suffix-resolution.md)
- Domain-level options specific to components/services/operations
- Error taxonomy refinement (Phase 2.9+)

----------------------------------------------------------------------
Two-Stage Model (Normative)
----------------------------------------------------------------------
Invocation processing MUST be split into two stages:

Stage 1: Runtime Protocol (pre-resolution)
Stage 2: Domain Protocol (resolution + execution)

Stage 1 MUST complete before any selector (component/service/operation) resolution.

----------------------------------------------------------------------
GlobalProtocol Vocabulary
----------------------------------------------------------------------
GlobalProtocol defines runtime-level parameters that are consumed in Stage 1.

Each parameter is defined as:
- Canonical key (internal identifier)
- CLI switches/properties (external spelling)
- Config key (future)
- Target field in GlobalRuntimeContext
- Defaulting rule (CLI > config > default)
- Propagation rule (consumed / forwarded)

----------------------------------------------------------------------
Parameter Catalog (Minimal Set for Phase 2.8 / 2.85)
----------------------------------------------------------------------
The following parameters are in-scope and MUST be treated as runtime-level:

1) log_level
- CLI: --log-level <level>
- Config: runtime.log.level
- Context: GlobalRuntimeContext.logLevel
- Default: "info" (project default)
- Propagation: CONSUMED in Stage 1

2) log_backend
- CLI: --log-backend <backend>
- Config: runtime.log.backend
- Context: GlobalRuntimeContext.logBackend
- Default: project default (e.g. stdout or slf4j; defined elsewhere)
- Propagation: CONSUMED in Stage 1

3) baseurl
- CLI: --baseurl <url>
- Config: runtime.http.baseurl
- Context: GlobalRuntimeContext.baseUrl
- Default: (mode-dependent; see Defaulting)
- Propagation: CONSUMED in Stage 1
- Note: baseurl is runtime default for HTTP client; MUST NOT reach Domain Protocol.

4) http_driver
- CLI: --http-driver <driver>
- Config: runtime.http.driver
- Context: GlobalRuntimeContext.httpDriver
- Default: project default
- Propagation: CONSUMED in Stage 1

5) env
- CLI: --env <name>
- Config: runtime.env
- Context: GlobalRuntimeContext.env
- Default: "default" (or empty; project policy)
- Propagation: CONSUMED in Stage 1

----------------------------------------------------------------------
Defaulting (Normative)
----------------------------------------------------------------------
GlobalProtocol parameters MUST be resolved with precedence:

CLI > config > default

Defaults MAY depend on RunMode (Stage 1 result).
Example:
- baseurl default may vary by RunMode:
  - client: http://localhost:8080 (example only; final default is project policy)
  - server: not required (server binds listen address/port)
  - command: may be unused unless client-like HTTP is selected

GlobalProtocol MUST define whether a parameter:
- is required
- has a mode-dependent default
- is ignored in a given mode

----------------------------------------------------------------------
Propagation Rule (Normative)
----------------------------------------------------------------------
If a switch/property is not recognized by Runtime Protocol (Stage 1),
it MUST be forwarded to Domain Protocol (Stage 2) unchanged.

This ensures:
- domain-level options remain available
- forward compatibility for new domain switches

Exception:
- None. (No "best-effort" consumption. Either consume, or forward.)

----------------------------------------------------------------------
Expansion Mapping
----------------------------------------------------------------------
GlobalProtocol expands into the following artifacts:

A) RuntimeProtocol
- Parser rules:
  - consumes the catalog parameters from argv
  - determines RunMode
  - produces:
    - GlobalRuntimeContext (materialized)
    - DomainArgv (residual argv)

B) Config schema (.cncf/config.yaml future)
- Keys for each parameter under a stable namespace (runtime.*)

C) GlobalRuntimeContext
- Typed fields for the catalog parameters
- Includes RunMode and derived runtime values as needed

----------------------------------------------------------------------
Acceptance Criteria (Design)
----------------------------------------------------------------------
Given:
  cncf client --baseurl http://localhost:8080 admin.system.ping

Stage 1 MUST:
- consume --baseurl http://localhost:8080
- set GlobalRuntimeContext.baseUrl accordingly
- forward DomainArgv = [admin.system.ping]

Stage 2 MUST:
- never see "--baseurl" token
- resolve selector/admin.system.ping using DomainArgv only

----------------------------------------------------------------------
References
----------------------------------------------------------------------
- docs/design/canonical-alias-suffix-resolution.md
  - Domain Protocol and selector resolution rules
- docs/design/global-protocol.md
  - runtime-scoped parameters, precedence, and materialization
- docs/design/path-alias.md
  - implementation-aligned alias handling reference for Phase 2.8

======================================================================
End of DRAFT
======================================================================
