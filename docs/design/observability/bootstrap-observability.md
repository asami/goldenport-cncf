    # Bootstrap Observability

    ## Purpose

    This document defines how **bootstrap-phase events** (especially component.d loading)
    are made observable in Cloud Native Component Framework (CNCF).

    The goal is to ensure that **initialization failures and early component loading behavior
    are observable in real operations**, not silently lost due to initialization order.

    ---

    ## Background

    Historically, CNCF emitted bootstrap logs in ad-hoc ways:

    - Direct `stderr` output via `BootstrapLog`
    - Conditional logging gated by environment variables
    - Observability events emitted before the runtime was fully initialized

    As a result:

    - Trace logs written during bootstrap were often **dropped**
    - `--log-level trace` did not reliably surface early initialization behavior
    - It was impossible to distinguish:
      - “component.d is not loaded”
      - vs.
      - “component.d is loaded but invisible”

    This document describes the corrected design.

    ---

    ## Design Overview

    ### Key Principles

    1. **Bootstrap is a first-class observability phase**
    2. **Early events must never be silently dropped**
    3. **Visibility is controlled by the same policy as runtime logs**
    4. **Default behavior remains quiet unless explicitly enabled**

    ---

    ## Bootstrap Scope

    A dedicated **Bootstrap scope** is introduced:

    - Scope name: `Bootstrap`
    - Used exclusively during:
      - component.d discovery
      - component instantiation
      - early runtime construction

    Bootstrap logs are emitted **before** Subsystem or Action scopes exist,
    but are treated as normal observability events.

    ---

    ## Pre-Initialization Buffering and Replay

    ### Problem

    Bootstrap events are emitted **before** `GlobalObservability` is initialized.

    Previously:
    - These events were silently dropped.

    ### Solution

    GlobalObservability now supports **buffering and replay**:

    1. Events emitted before initialization are stored in a bounded FIFO buffer
    2. On `initialize(root)`:
       - The buffer is drained
       - Events are replayed in original order
    3. After initialization:
       - Events are emitted normally

    ### Properties

    - Bounded buffer (prevents unbounded memory usage)
    - Replay happens **once**
    - No duplicate emission

    This guarantees that **initialization order does not affect observability**.

    ---

    ## Component Repository Integration

    ### Previous State

    - `ComponentRepository` and `ComponentProvider` emitted bootstrap logs via:
      - `BootstrapLog.stderr`
    - These logs bypassed observability and visibility policies.

    ### Current Design

    - Bootstrap-phase logs are emitted via:
      - `GlobalObservability.observe_trace`
      - with `ScopeContext = Bootstrap`
    - This applies to:
      - component discovery
      - JAR loading
      - component instantiation

    As a result:
    - Component loading is observable
    - Logs respect:
      - visibility policy
      - buffering/replay
      - backend configuration

    ---

    ## Visibility Control

    ### Defaults

    - Bootstrap logs are **hidden by default**
    - No increase in noise for normal runs

    ### Explicit Enablement

    Bootstrap logs become visible when an audible backend and sufficient log level are enabled, for example:

        --log-backend stdout --log-level trace

    This works consistently for:

    - `sbt run`
    - `cs launch`
    - `docker run`

    Bootstrap logs, Global logs, and Action logs all follow the same policy.

    ---

    ## Non-Goals

    This design intentionally does **not**:

    - Change default log verbosity
    - Introduce new CLI flags specific to bootstrap
    - Redesign backend selection logic
    - Require per-component scope contexts during bootstrap

    ---

    ## Operational Guarantees

    With this design:

    - Bootstrap-phase failures are observable in production
    - component.d loading behavior is diagnosable
    - Observability is no longer dependent on initialization order
    - Logs written during bootstrap behave the same as runtime logs

    ---

    ## Summary

    Bootstrap observability is now treated as a **first-class concern**.

    By introducing:
    - a Bootstrap scope
    - buffered pre-initialize emission
    - unified routing through GlobalObservability

    CNCF guarantees that **early initialization behavior is observable, controllable, and reliable**.

    This prevents silent initialization failures and enables effective debugging
    without compromising default runtime noise levels.
