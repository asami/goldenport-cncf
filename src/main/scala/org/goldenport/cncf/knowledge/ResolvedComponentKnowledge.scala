package org.goldenport.cncf.knowledge

import org.goldenport.Consequence
import org.goldenport.cncf.component.repository.{
  ResolvedComponentResource,
  ResolvedComponentResources
}

/*
 * Immutable, internal composition of an already validated Component knowledge
 * manifest with the exact Phase 58 resolver evidence that it admits. This
 * value neither resolves nor reads resources; it preserves supplied evidence.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResolvedComponentKnowledgeEntry(
  entry: ComponentKnowledgeResourceEntry,
  resource: ResolvedComponentResource
)

final case class ResolvedComponentKnowledge(
  manifest: ComponentKnowledgeManifest,
  entries: Vector[ResolvedComponentKnowledgeEntry]
)

object ResolvedComponentKnowledge {
  /*
   * Binds only caller-supplied values. ComponentKnowledgeManifest.createC is
   * the authority for manifest/resource validation; pairing then retains the
   * exact resolver-owned resource beside each admitted manifest entry.
   */
  def composeC(
    suppliedResources: ResolvedComponentResources,
    candidate: ComponentKnowledgeManifest
  ): Consequence[ResolvedComponentKnowledge] =
    ComponentKnowledgeManifest.createC(suppliedResources, candidate).flatMap { manifest =>
      _pair_c(manifest, suppliedResources.resources)
    }

  private def _pair_c(
    manifest: ComponentKnowledgeManifest,
    suppliedresources: Vector[ResolvedComponentResource]
  ): Consequence[ResolvedComponentKnowledge] =
    _pair(manifest, manifest.resources, suppliedresources).fold(Consequence.argumentInvalid, Consequence.success)

  private def _pair(
    manifest: ComponentKnowledgeManifest,
    entries: Vector[ComponentKnowledgeResourceEntry],
    suppliedresources: Vector[ResolvedComponentResource]
  ): Either[String, ResolvedComponentKnowledge] =
    entries.foldLeft[Either[String, Vector[ResolvedComponentKnowledgeEntry]]](Right(Vector.empty)) { (z, entry) =>
      for {
        xs <- z
        resource <- _exact_resource(entry, suppliedresources)
      } yield xs :+ ResolvedComponentKnowledgeEntry(entry, resource)
    }.map(values => ResolvedComponentKnowledge(manifest, values))

  private def _exact_resource(
    entry: ComponentKnowledgeResourceEntry,
    suppliedresources: Vector[ResolvedComponentResource]
  ): Either[String, ResolvedComponentResource] =
    suppliedresources.filter(_.logicalIdentity == entry.binding.logicalIdentity) match {
      case Vector(resource) => Right(resource)
      case Vector() => Left(s"knowledge entry ${entry.logicalPath} does not bind a supplied Phase 58 logical identity")
      case _ => Left(s"knowledge entry ${entry.logicalPath} matches more than one supplied Phase 58 resource")
    }
}
