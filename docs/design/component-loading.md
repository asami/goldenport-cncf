Design Note — component.dir (Docker Distribution Contract)
==========================================================

Purpose
-------
component.dir defines the canonical mechanism for loading external CNCF
components in Docker-based distributions.

The goal is to separate the CNCF runtime from user-defined components
while keeping the operational model simple and explicit.

Definition
----------
- component.dir is a directory scanned by CNCF at startup
- CNCF loads component artifacts from this directory
- The directory is typically mounted from the host in Docker environments

Default location:
/app/component.dir

Supported artifacts (initial scope)
-----------------------------------
- *.jar : supported
- *.class, source files : not supported (future extension)

Loading semantics
-----------------
- Components are loaded only at process startup
- Container restart is required to reflect changes
- Hot reload is out of scope for initial phases

Configuration precedence (planned)
----------------------------------
1. Command-line option: --component.dir=/path
2. Environment variable: CNCF_COMPONENT_DIR
3. Default: /app/component.dir

Docker operational model
------------------------
Typical usage:

docker run --rm \
  -v $(pwd)/components:/app/component.dir:ro \
  goldenport-cncf \
  command demo ping

- CNCF Docker image contains only the runtime
- User components are injected via volume mounting
- component.dir is treated as read-only by CNCF

Stage positioning
-----------------
- component.dir is the official distribution contract for Docker-based CNCF usage
- It is intentionally not used in the initial Stage 5 demo
- Stage 5 demos use scala-cli + classpath for minimal user experience
- component.dir will be exercised in later stages

Design rationale
----------------
- Aligns with standard container-based plugin architectures
- Avoids classpath ambiguity and accidental dependency leakage
- Provides a clean path toward Kubernetes, marketplaces, and sandboxing
