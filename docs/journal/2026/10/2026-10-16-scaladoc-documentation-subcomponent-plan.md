# ScalaDoc Documentation Subcomponent planning decision

Date: 2026-10-16

A review of the current Component/Subcomponent implementation found that the required foundation already exists: Phase 58 owns resource Subcomponent resolution and mode policy, Phase 59 owns documentation/knowledge and Help, Phase 60 owns Admin visibility, and the stable Component Scaladoc Projection owns public ScalaDoc staging.

The implementation strategy is therefore integration rather than a new parallel documentation mechanism.

Phase 112 is added for the CNCF runtime/Web side. ScalaDoc is treated as Documentation Subcomponent content. Develop mode uses the existing Component Repository/resource resolution path; Help/Web/Admin consume the resolved projection; Production does not automatically fetch or mount documentation. No ScalaDoc-specific resolver or repository scan is introduced.

Cozy Phase 86 owns semantic ScalaDoc production and Documentation Subcomponent packaging alignment. The existing ScalaDoc generation/staging contract is retained where useful rather than replaced.

ScalaDoc source comments are also recognized as useful machine-readable context for AI development. The policy is semantic, not coverage-based: document non-obvious role, contract, constraints and design intent, and avoid comments that merely restate code.
