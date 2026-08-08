package org.goldenport.cncf.component.repository

import org.goldenport.cncf.component.{ComponentId, ComponentIdentityCompatibilityAdapter}

/*
 * Static, pre-activation Component identity candidate discovery for assembly
 * admission. This remains package-internal so ComponentRepository.Specification
 * retains its public API boundary.
 *
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentRepositoryStaticIdentityCandidates {
  def resolve(
    specification: ComponentRepository.Specification,
    alias: String
  ): Vector[ComponentId] = {
    val descriptors = specification.resolveStaticComponentDescriptors
    val candidates =
      if (descriptors.nonEmpty)
        descriptors.flatMap { descriptor =>
          descriptor.componentId.orElse(
            descriptor.requireCanonicalIdentityC.toOption.map(_._1)
          )
        }
      else
        specification.resolveStaticComponentDescriptor(alias).flatMap(_.componentId).toVector
    _resolve_candidates(alias, candidates)
  }

  private def _resolve_candidates(
    alias: String,
    candidates: Vector[ComponentId]
  ): Vector[ComponentId] =
    candidates
      .flatMap { candidate =>
        ComponentIdentityCompatibilityAdapter.resolve(
          alias,
          Vector(candidate),
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        ) match {
          case _: ComponentIdentityCompatibilityAdapter.Canonical => Some(candidate)
          case _: ComponentIdentityCompatibilityAdapter.Adapted => Some(candidate)
          case _: ComponentIdentityCompatibilityAdapter.Rejected => None
        }
      }
      .distinct
      .sortBy(_.name)
}
