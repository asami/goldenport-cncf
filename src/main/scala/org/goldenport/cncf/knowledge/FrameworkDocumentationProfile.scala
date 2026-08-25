package org.goldenport.cncf.knowledge

import org.goldenport.Consequence

/*
 * Read-only framework documentation snapshot profile. It validates only
 * caller-supplied publication and manifest evidence; it neither selects a
 * target Component nor creates an access, activation, or runtime dependency.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
enum FrameworkDocumentationSnapshotProfile {
  case Absent
  case Present(
    frameworkDocumentation: Vector[ComponentKnowledgeResourceEntry],
    publicAiGuide: Option[PublicDirectiveProjection],
    publicSkillCatalog: Option[PublicSkillCatalog]
  )
}

final case class FrameworkDocumentationProfile(
  frameworkPublication: FrameworkPublicationContext,
  snapshot: FrameworkDocumentationSnapshotProfile
)

object FrameworkDocumentationProfile {
  def createC(
    frameworkPublication: FrameworkPublicationContext,
    snapshotManifest: Option[ComponentKnowledgeManifest]
  ): Consequence[FrameworkDocumentationProfile] =
    _create(frameworkPublication, snapshotManifest).fold(Consequence.argumentInvalid, Consequence.success)

  private def _create(
    frameworkpublication: FrameworkPublicationContext,
    snapshotmanifest: Option[ComponentKnowledgeManifest]
  ): Either[String, FrameworkDocumentationProfile] =
    for {
      _ <- FrameworkPublicationContext.validateC(frameworkpublication).toOption
        .toRight("frameworkPublication violates framework publication validation")
      profile <- (frameworkpublication.documentationComponentSnapshot, snapshotmanifest) match {
        case (None, None) =>
          Right(FrameworkDocumentationProfile(frameworkpublication, FrameworkDocumentationSnapshotProfile.Absent))
        case (Some(_), None) =>
          Left("frameworkPublication.documentationComponentSnapshot requires supplied snapshot manifest evidence")
        case (None, Some(_)) =>
          Left("snapshot manifest evidence requires frameworkPublication.documentationComponentSnapshot")
        case (Some(snapshot), Some(manifest)) => _present(frameworkpublication, snapshot, manifest)
      }
    } yield profile

  private def _present(
    frameworkpublication: FrameworkPublicationContext,
    snapshot: FrameworkDocumentationComponentSnapshot,
    manifest: ComponentKnowledgeManifest
  ): Either[String, FrameworkDocumentationProfile] = {
    val documentation = manifest.resources.filter(_is_framework_documentation)
    for {
      _ <- ComponentKnowledgeManifest.validateC(manifest).toOption
        .toRight("snapshot manifest violates Component knowledge manifest validation")
      _ <- Either.cond(
        manifest.componentId == snapshot.componentId,
        (),
        "snapshot manifest componentId must equal framework documentation snapshot componentId"
      )
      _ <- Either.cond(
        manifest.logicalRelease == snapshot.logicalRelease,
        (),
        "snapshot manifest logicalRelease must equal framework documentation snapshot logicalRelease"
      )
      _ <- Either.cond(
        manifest.frameworkPublication.contains(frameworkpublication),
        (),
        "snapshot manifest frameworkPublication must exactly equal supplied framework publication"
      )
      _ <- Either.cond(
        documentation.nonEmpty,
        (),
        "snapshot manifest must contain Framework framework-documentation text/markdown evidence"
      )
      _ <- manifest.publicDirective.map(_public_ai_guide(_, manifest.resources)).getOrElse(Right(()))
      _ <- manifest.skillCatalog.map(_public_skill_catalog(_, manifest.resources)).getOrElse(Right(()))
    } yield FrameworkDocumentationProfile(
      frameworkPublication = frameworkpublication,
      snapshot = FrameworkDocumentationSnapshotProfile.Present(
        frameworkDocumentation = documentation,
        publicAiGuide = manifest.publicDirective,
        publicSkillCatalog = manifest.skillCatalog
      )
    )
  }

  private def _is_framework_documentation(entry: ComponentKnowledgeResourceEntry): Boolean =
    entry.kind == ComponentKnowledgeResourceKind.FrameworkDocumentation &&
      entry.role == ComponentKnowledgeResourceRole.FrameworkDocumentation &&
      entry.mediaType == ComponentKnowledgeMediaType.TextMarkdown &&
      entry.metadata.authority == ComponentKnowledgeAuthority.Framework

  private def _public_ai_guide(
    guide: PublicDirectiveProjection,
    resources: Vector[ComponentKnowledgeResourceEntry]
  ): Either[String, Unit] =
    for {
      _ <- PublicDirectiveProjection.validateC(guide, resources).toOption
        .toRight("snapshot public AI Guide violates public Directive validation")
      _ <- _framework_entry(guide.entry, "snapshot public AI Guide entry")
      _ <- _public_or_ecosystem(guide.visibility, "snapshot public AI Guide visibility")
    } yield ()

  private def _public_skill_catalog(
    catalog: PublicSkillCatalog,
    resources: Vector[ComponentKnowledgeResourceEntry]
  ): Either[String, Unit] =
    for {
      _ <- PublicSkillCatalog.validateC(catalog, resources).toOption
        .toRight("snapshot public Skill Catalog violates public Skill Catalog validation")
      _ <- _framework_entry(catalog.entry, "snapshot public Skill Catalog entry")
      _ <- _public_or_ecosystem(catalog.visibility, "snapshot public Skill Catalog visibility")
    } yield ()

  private def _framework_entry(entry: ComponentKnowledgeResourceEntry, context: String): Either[String, Unit] =
    Either.cond(
      entry.metadata.authority == ComponentKnowledgeAuthority.Framework,
      (),
      s"$context must have Framework metadata authority"
    )

  private def _public_or_ecosystem(visibility: PublicMetadataVisibility, context: String): Either[String, Unit] =
    Either.cond(
      visibility == PublicMetadataVisibility.Public || visibility == PublicMetadataVisibility.Ecosystem,
      (),
      s"$context must be Public or Ecosystem"
    )
}
