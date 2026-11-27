# CML-driven automatic application assembly and execution demo — discussion record

Date: 2026-11-25
Status: Idea discussed; implementation and feasibility not verified

## Context
A demonstration concept was discussed to show end-to-end model-driven application construction using CML, CNCF component-based development, and AI-assisted automation.

## Idea captured
- Input consists of CML and supplementary information.
- Both domain/application models and UI models are expressed in **CML**, not in a separate UI DSL.
- The automation identifies reusable existing CNCF Components and generates only the missing Components.
- Existing and newly generated Components are combined into a Subsystem using an **Assembly definition**.
- CNCF's existing Assembly-based Subsystem construction capability is already implemented and should be reused; the idea does **not** call for a new Subsystem Builder.
- The resulting Subsystem is started on CNCF Runtime.
- The CML UI model drives Flutter UI generation for Web, iPhone (iOS), and Android, all interacting with the running Subsystem.
- AI / sm-workflow is a candidate for orchestrating model interpretation, component discovery, generation, validation, build, tests, and launch.

## Intended demonstration
The audience supplies CML and supplementary information, observes component reuse versus generation and Assembly composition, then interacts with the running application from Flutter clients. The demonstration is intended to make executable modeling, Cloud Native CBD, and AI-assisted development automation tangible.

## Existing capability versus open questions
**Reported as already implemented:** Assembly-based Subsystem composition in CNCF.

**To investigate, not claimed as implemented:** automatic Component discovery and selection; generation of missing Components; automatic Assembly-definition generation; CML-to-Flutter generation and platform-specific packaging; end-to-end workflow and safe runtime launch.

This entry records the discussion rather than defining implementation requirements. Any subsequent design or specification belongs in the appropriate documentation layer under [Document Lifecycle](../../../rules/document-lifecycle.md).
