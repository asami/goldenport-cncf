# Phase 114 - Typed Domain Rule and AI Evaluation

Status: PLANNED
Created: 2026-10-17
Replanned: 2026-11-26

## Goal
Deliver a reusable typed Domain Rule evaluation mechanism during sm-workflow Phase 25.5. The original full hybrid evidence demonstrator moves to [Phase 114.1](phase-114.1.md), which is not a Phase 25.5 closure dependency.

## Delivery
- Reuse Phase 32 RuleEngine/InferenceEngine and JudgmentAction/Continuation boundaries where semantically appropriate. Define Predicate (Boolean), Selection (admitted candidate ID), and Value (domain-typed value) contracts independently of deterministic or AI implementation.
- Define a provider-neutral evaluation SPI with explicit decline, failure, invalid output and evidence/provenance; distinguish these from false. Preserve evaluation/action separation and prohibit direct persistent mutation.
- Textus AI implements the AI adapter outside CNCF core; OpenAI Structured Outputs is a provider implementation detail. Keep AI judgments distinct from facts and estimates. Validate domain types, candidate membership and constraints deterministically.
- External execution uses admitted CNCF capability/UoW and Safety Sandbox boundaries. Preserve deterministic bootstrap routing, authorization and audit; avoid recursive AI routing.
- Coordinate sm-workflow requirements, CNCF release, Textus AI integration and sm-workflow consumption within Phase 25.5. No temporary duplicate rule engine. Value is supported by CNCF; initial sm-workflow use prioritizes Predicate and Selection.

## Acceptance
Deterministic fixtures for all three result types, invalid output, decline, authorization, provenance and provider substitution; Phase 32 regressions; reviewed SPI and integration contract. Coordinate live structured-output qualification in Textus AI and end-to-end sm-workflow qualification without requiring live AI for deterministic tests. No measured savings claimed before experiment. The original checklist must be split before execution; full SIE/RDF/embedding/analytics and Entity Space evidence integration belongs to Phase 114.1.
