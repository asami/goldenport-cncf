package org.goldenport.cncf.knowledge

import org.goldenport.Consequence

/*
 * Pure, read-only closed-network Documentation Hub SAR composition profile.
 * It composes validated framework-documentation snapshots only; it neither
 * describes a SAR artifact nor authorizes retrieval, activation, or runtime.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
enum DocumentationHubSarConstituentRole {
  case Cncf
  case Cml
  case Cozy
  case TextusBok
  case ApprovedRetrievalProvider
}

final case class DocumentationHubSarConstituent(
  role: DocumentationHubSarConstituentRole,
  profile: FrameworkDocumentationProfile
)

final case class ClosedNetworkDocumentationHubSarProfile(
  sarArtifactId: String,
  logicalRelease: String,
  constituents: Vector[DocumentationHubSarConstituent]
)

object ClosedNetworkDocumentationHubSarProfile {
  def createC(
    sarArtifactId: String,
    logicalRelease: String,
    constituents: Vector[DocumentationHubSarConstituent]
  ): Consequence[ClosedNetworkDocumentationHubSarProfile] =
    _create(sarArtifactId, logicalRelease, constituents).fold(Consequence.argumentInvalid, Consequence.success)

  private val _sar_artifact_id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  private def _create(
    sarartifactid: String,
    logicalrelease: String,
    constituents: Vector[DocumentationHubSarConstituent]
  ): Either[String, ClosedNetworkDocumentationHubSarProfile] =
    for {
      _ <- _sar_artifact_id(sarartifactid)
      _ <- _safe_text(logicalrelease, "logicalRelease")
      _ <- _roles(constituents)
      _ <- _constituents(constituents)
      _ <- _unique_snapshot_identities(constituents)
    } yield ClosedNetworkDocumentationHubSarProfile(sarartifactid, logicalrelease, constituents)

  private def _sar_artifact_id(value: String): Either[String, Unit] =
    Either.cond(
      Option(value).exists(_sar_artifact_id_pattern.matches),
      (),
      "sarArtifactId must be a safe index-style token"
    )

  private def _safe_text(value: String, context: String): Either[String, Unit] =
    Either.cond(
      Option(value).exists(text => text.nonEmpty && text == text.trim && !text.exists(_.isControl)),
      (),
      s"$context must be non-empty trimmed text"
    )

  private def _roles(constituents: Vector[DocumentationHubSarConstituent]): Either[String, Unit] =
    Either.cond(
      constituents.map(_.role) == DocumentationHubSarConstituentRole.values.toVector,
      (),
      "constituent roles must be the complete Documentation Hub role set in declaration order"
    )

  private def _constituents(constituents: Vector[DocumentationHubSarConstituent]): Either[String, Unit] =
    _sequence(constituents.zipWithIndex.map { case (constituent, index) =>
      _constituent(constituent, s"constituents[$index]")
    })

  private def _constituent(
    constituent: DocumentationHubSarConstituent,
    context: String
  ): Either[String, Unit] =
    constituent.profile.snapshot match {
      case FrameworkDocumentationSnapshotProfile.Absent =>
        Left(s"$context.profile must retain a Present framework documentation snapshot")
      case present: FrameworkDocumentationSnapshotProfile.Present =>
        _present_snapshot(constituent.profile.frameworkPublication, present, context)
    }

  private def _present_snapshot(
    frameworkpublication: FrameworkPublicationContext,
    present: FrameworkDocumentationSnapshotProfile.Present,
    context: String
  ): Either[String, Unit] =
    frameworkpublication.documentationComponentSnapshot match {
      case None =>
        Left(s"$context.profile.frameworkPublication.documentationComponentSnapshot is required")
      case Some(snapshot) =>
        for {
          _ <- _closed_network_availability(snapshot.availability, s"$context.profile.frameworkPublication.documentationComponentSnapshot.availability")
          reconstructed = ComponentKnowledgeManifest(
            componentId = snapshot.componentId,
            logicalRelease = snapshot.logicalRelease,
            resources = present.frameworkDocumentation ++ present.publicAiGuide.map(_.entry) ++ present.publicSkillCatalog.map(_.entry),
            frameworkPublication = Some(frameworkpublication),
            publicDirective = present.publicAiGuide,
            skillCatalog = present.publicSkillCatalog
          )
          profile <- FrameworkDocumentationProfile.createC(frameworkpublication, Some(reconstructed)).toOption
            .toRight(s"$context.profile Present snapshot violates FrameworkDocumentationProfile composition")
          _ <- Either.cond(
            profile.snapshot == present,
            (),
            s"$context.profile Present snapshot must exactly equal its reconstructed FrameworkDocumentationProfile"
          )
        } yield ()
    }

  private def _closed_network_availability(
    availability: FrameworkPublicationReferenceAvailability,
    context: String
  ): Either[String, Unit] =
    Either.cond(
      availability == FrameworkPublicationReferenceAvailability.Local ||
        availability == FrameworkPublicationReferenceAvailability.Installed ||
        availability == FrameworkPublicationReferenceAvailability.Cached,
      (),
      s"$context must be Local, Installed, or Cached for closed-network composition"
    )

  private def _unique_snapshot_identities(constituents: Vector[DocumentationHubSarConstituent]): Either[String, Unit] = {
    val identities = constituents.flatMap { constituent =>
      constituent.profile.frameworkPublication.documentationComponentSnapshot.map { snapshot =>
        (snapshot.componentId, snapshot.logicalRelease)
      }
    }
    Either.cond(
      identities.distinct.size == identities.size,
      (),
      "constituents must not repeat a Documentation Component snapshot componentId and logicalRelease"
    )
  }

  private def _sequence(values: Vector[Either[String, Unit]]): Either[String, Unit] =
    values.foldLeft(Right(()): Either[String, Unit]) { (z, x) => z.flatMap(_ => x) }
}
