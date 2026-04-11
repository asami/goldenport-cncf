CNCF Web Operational Management Functions Note
==============================================

status=draft
category=web / operations / dashboard

Overview
--------

This note defines the operational web capabilities of CNCF.
The goal is to provide a unified control and observability layer
for components, services, operations, jobs, and events.

The web layer is not a separate business layer,
but an operational interface over CNCF runtime capabilities.

The design is based on three pillars:

- Dashboard (observe)
- Management Console (control)
- Manual (understand)

Additionally, metrics visualization, performance tuning,
and debugging are treated as first-class capabilities.

Initial direction:

- Build the Static Form App mechanism first.
- Treat Static Form App as the application shape; internally, it uses Form API
  for schema-driven form definition and validation.
- Place Dashboard, Management Console, and Manual on top of that shared
  mechanism as separate web applications.
- Start from a read-only Dashboard as the first entry point.
- Keep Management Console separate from Dashboard.
- Keep Manual separate from Dashboard and Management Console.
- Do not mix operation execution or configuration mutation into the first
  Dashboard surface.
- Treat Dashboard as an observation and navigation surface over existing
  runtime/meta information.


1. Dashboard (Observability)
----------------------------

Purpose:
Provide a real-time and historical view of system behavior.

Baseline stance:

The first Dashboard is read-only.
It shows CNCF runtime state and links to related surfaces, but it does not
execute operations, mutate configuration, control jobs, or edit components.
Those capabilities belong to the Management Console.

The Dashboard may link to Manual entries for explanation, but Manual content
is not embedded as the main interaction model.

Implementation stance:

Dashboard should be implemented as a Static Form App instance with
mutation paths disabled. It should reuse the same routing, page resolution,
validation, and response presentation mechanism as the other operational web
apps, rather than becoming a special-purpose dashboard-only subsystem.

### 1.1 System Overview

- Component list
- Service structure
- Operation availability
- Health status (UP/DOWN)
- Version information

### 1.2 Metrics Visualization

Metrics are visualized not only as numbers but as structures and flows.

#### Metrics Types

- Operation throughput (requests/sec)
- Latency (avg, p95, p99)
- Error rate
- Active jobs
- Queue length

#### Visualization Forms

- Time-series graphs
- Heatmaps (latency / error hotspots)
- Component interaction graph
- Operation dependency graph

### 1.3 Trace Visualization

Trace is the core observability feature.

- Operation execution flow (TraceTree)
- ActionCall hierarchy
- Event emission flow
- Job transitions

Example:

createOrder
 ├─ validateUser (5ms)
 ├─ reserveInventory (80ms)
 └─ payment (timeout)

### 1.4 Event Flow

- Event chains
- Async propagation
- Event-triggered jobs

### 1.5 Alerts

- Threshold-based alerts
- Health degradation
- Queue overflow
- Error spikes


2. Management Console (Control Plane)
-------------------------------------

Purpose:
Provide direct operational control over CNCF runtime.

The Management Console is a separate surface from Dashboard.
It owns active operation execution, runtime control, job control, and
configuration mutation.
Dashboard may link to Console actions, but the initial Dashboard must not
perform those actions inline.

The Console should be implemented as a separate Static Form App instance.
Unlike Dashboard, it may expose controlled operation execution and job control
forms, subject to the Web Tier exposure, authentication, and authorization
rules.

### 2.1 Operation Execution UI

- Selector-based invocation
- Parameter input (JSON form)
- Sync / async execution
- Result display

### 2.2 Job Management

- Job list (running, completed, failed)
- Job detail (trace, logs, result)
- Cancel job
- Retry job

### 2.3 Component Management

- Component lifecycle (start/stop)
- Reload (future)
- Configuration view/update

### 2.4 Configuration Management

- Framework configuration (cncf.*)
- Component-specific configuration
- Runtime parameter override

### 2.5 Log & Trace Access

- Unified logs per trace
- Time-ordered logs
- Filter by component/service/operation

### 2.6 Security Management

- API key management
- Access control (future)
- Audit logs


3. Manual (Self-Documentation)
------------------------------

Purpose:
Provide discoverability and understanding of the system.

Manual is a separate surface from Dashboard and Management Console.
It owns explanatory and reference-oriented views derived from CNCF meta
capabilities. Dashboard may link to Manual entries for a component, service,
operation, or warning, but the Dashboard should remain an operational
read-only overview rather than a reference manual.

Manual should be implemented as a separate Static Form App instance for
read-only reference navigation. It should use existing meta/help/describe/schema
projections as content sources rather than duplicating documentation logic.

### 3.1 Help (Human-readable)

- Component overview
- Service list
- Operation usage

Derived from meta.help.

### 3.2 Describe (Structure)

- Operation signatures
- Input/output structure
- Internal relationships

Derived from meta.describe.

### 3.3 Schema

- Input/output schema
- Validation rules

### 3.4 OpenAPI

- API documentation
- External integration

### 3.5 FAQ / Troubleshooting

- Common errors
- Resolution patterns
- Best practices

### 3.6 Search

- Selector search
- Documentation search


4. Performance Tuning
---------------------

Purpose:
Identify and improve performance bottlenecks.

### 4.1 Bottleneck Detection

- Slow operations ranking
- High error operations
- High latency services

### 4.2 Drill Down Analysis

- Operation → Trace → Step
- Identify slow ActionCall

### 4.3 Slow Operation Detection

- Threshold-based detection
- Historical tracking

### 4.4 Load Monitoring

- Request rate
- Concurrent execution
- Job queue depth

### 4.5 Optimization Guidance (Future)

- Suggest async conversion
- Suggest caching
- Suggest resource scaling


5. Debugging
------------

Purpose:
Enable reproducibility and root cause analysis.

### 5.1 TraceTree (Core Feature)

- Full execution trace
- Success/failure per step
- Timing information

### 5.2 Log Correlation

- Logs grouped by trace
- Time-series log view

### 5.3 Replay

- Re-run operation with same input
- Debugging reproducibility

### 5.4 Snapshot

- ExecutionContext capture
- Input/output preservation

### 5.5 Error Analysis

- Error classification
- Frequency analysis
- Root cause identification

### 5.6 Context Inspection

- ExecutionContext
- Resolved configuration
- Parameter origin tracking


6. Integration of Observability and Debugging
---------------------------------------------

The key value of CNCF lies in integrating these capabilities.

### 6.1 Metrics → Trace

Latency increase → Trace analysis → Root cause

### 6.2 Error → Flow

Error occurrence → Trace → Failing step

### 6.3 Job → System State

Queue growth → Worker bottleneck → Throughput issue


7. CNCF-Specific Characteristics
--------------------------------

- Operation-centric visualization
- Job as a first-class runtime concept
- Event-driven flow visibility
- Trace as a primary debugging artifact
- ExecutionContext as an inspectable entity

These differentiate CNCF from conventional web monitoring systems.


Conclusion
----------

The operational web layer of CNCF is not merely a monitoring UI.
It is a unified interface that connects:

- execution (operation)
- observation (metrics, trace)
- control (console)
- knowledge (manual)

This integration enables a new level of operability
in AI-assisted and component-based systems.
