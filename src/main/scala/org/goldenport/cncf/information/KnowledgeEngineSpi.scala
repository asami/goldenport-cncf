package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.KnowledgeWorkingSetSnapshot

/*
 * @since   May. 20, 2026
 * @version May. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class InformationAuthorityResolutionRequest(
  information: Information,
  fieldPath: String,
  value: String,
  limit: Int = 10
)

final case class InformationAuthorityResolutionResult(
  candidates: Vector[InformationResolutionCandidate]
)

final case class InformationPublicationRequest(
  information: Information,
  target: String = "rdf-vector"
)

final case class InformationPublicationResult(
  status: InformationPublicationStatus,
  snapshot: Option[KnowledgeWorkingSetSnapshot] = None
)

trait KnowledgeEngineProvider {
  def resolveAuthority(request: InformationAuthorityResolutionRequest): Consequence[InformationAuthorityResolutionResult]

  def publishInformation(request: InformationPublicationRequest): Consequence[InformationPublicationResult]

  def materializeInformation(information: Information): Consequence[KnowledgeWorkingSetSnapshot]
}
