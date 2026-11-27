# Specification Transformation versus Specification Creation in AI Development

Date: 2026-11-26
Status: Methodological observation and PoC evaluation proposal

## Observation

Many visible AI coding success stories, including migration and reimplementation, appear to rely on an existing working program. This is an observation to investigate, not a quantified claim about the distribution of success stories. A working program provides a rich executable specification and a partial test oracle. Prior human work has already established much of the domain knowledge and behavior. Reproducing that behavior is different from discovering and validating the requirements of a new business system.

## Evaluation framework

Distinguish (1) technology migration, (2) product reimplementation, (3) incremental feature development, (4) greenfield business application development, and (5) creation of new business processes. Evaluate the availability and completeness of prior specifications, domain knowledge acquisition, specification creation, implementation, and independent validation separately. Successful specification transformation does not by itself demonstrate specification creation capability. Existing behavior may also encode defects, accidental technical constraints and obsolete business rules; equivalence alone is insufficient when requirements change.

## Implications for MDD and CBD

AI-era MDD should externalize and maintain domain knowledge independently of implementation. CML represents domain structure, Domain Rule represents business decisions, Workflow and State Machine represent behavior, Component packages executable knowledge, Assembly Descriptor represents composition, and Usage Contract provides explicit acceptance criteria. CBD constrains the problem space and enables reuse of verified components. Human/domain-expert feedback remains essential when the specification itself is uncertain. Tests generated from the same mistaken interpretation as implementation are not independent evidence of business correctness.

## Application generation PoC

Propose evaluating a greenfield business scenario without an existing reference implementation, deriving a domain model from requirements and supplementary information, generating missing components and selecting existing components, assembling and running the application, changing business rules and observing the resulting behavior, and incorporating user feedback into the model. Distinguish generation success from domain correctness and sustainable evolution. Use independent domain acceptance criteria and record evidence. Compare against reimplementation tasks where useful, without claiming unmeasured productivity or quality gains.

The goal is not merely automatic code generation but continuous generation and evolution of applications centered on explicit, executable business knowledge.

Related: [Devin-inspired Component-Oriented AI Development](2026-11-26-devin-inspired-component-oriented-ai-development.md).

This journal records a proposed evaluation perspective; no implementation phases or PoC scope commitments are changed.
