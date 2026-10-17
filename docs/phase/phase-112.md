# Phase 112: Documentation Subcomponent ScalaDoc Web Projection

status=planned
planned_at=2026-10-16
depends_on=[Phase 58 series, Phase 59 series, Phase 60 series]
related=[Component Resource Subcomponent, Component Scaladoc Projection]
producer_dependency=[Cozy Phase 86]
consumers=[CNCF Help, CNCF Admin, AI/developer tooling]

## Goal

Make ScalaDoc and related documentation available through the existing Documentation Subcomponent and Component Repository path in non-Production operation, without adding a ScalaDoc-specific resolver or scanning CAR/repository paths from Web consumers.

## Existing foundation

Phase 58 already defines Documentation and SourceCode as resource Subcomponents, including operation-mode policy and the authoritative resolved-resource projection. Phase 59 defines Component documentation/knowledge contracts and Help as the human/AI knowledge entry point. Phase 60 projects the already-resolved Documentation/SourceCode composition into Admin. The existing Component Scaladoc Projection defines ordinary public ScalaDoc generation and staging.

This Phase connects those existing contracts. Closed Phases remain historical and are not reopened.

## Work

1. Define ScalaDoc as a Documentation Subcomponent resource rather than a Web-owned or resolver-owned special case.
2. Preserve the existing ScalaDoc generation contract (ordinary public ScalaDoc, index/search assets, no private/source disclosure) while moving the normal distribution target toward Documentation Subcomponent packaging.
3. Consume the existing Phase 58 resolved Documentation resource from Help/Web/Admin; do not scan Component Repository, CAR files, caches or development directories directly.
4. In Develop mode, allow the existing resource policy/resolver to obtain the matching Documentation Subcomponent from admitted Component Repository sources and expose authorized ScalaDoc HTML/resources through CNCF Web.
5. Test/Demo behavior follows the existing Phase 58 mode policy. Production must not automatically resolve, fetch or mount Documentation/SourceCode resources.
6. Provide Component -> Documentation -> ScalaDoc navigation from the existing knowledge/admin surface. Static ScalaDoc HTML/CSS/JS should be served as documentation content; CNCF should not reimplement a ScalaDoc renderer.
7. Keep identity/version binding between parent release and Documentation Subcomponent explicit so displayed ScalaDoc corresponds to the selected Component release.
8. Preserve authorization/path-safety/non-disclosure rules from the resource Subcomponent contract.

## Acceptance

A representative Component release publishes a Documentation Subcomponent containing ScalaDoc. Running the parent in Develop mode resolves that matching Documentation Subcomponent through the existing Component Repository/resource path and makes its ScalaDoc index and linked assets usable from CNCF Web. The same Web consumer uses only the resolved-resource projection. Production neither performs automatic remote Documentation resolution nor mounts ScalaDoc.

## Non-goals

No second resolver, no Repository scan from Web, no source-code disclosure, no custom ScalaDoc renderer, no reopening of Phases 58-60, and no requirement that every private implementation element have ScalaDoc.
