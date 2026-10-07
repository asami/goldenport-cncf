package org.goldenport.cncf.knowledge

import org.goldenport.cncf.component.ComponentInstanceId

/*
 * @since   Oct.  8, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class KnowledgeContributionSchema(id: String, version: String)

enum KnowledgeContributionAvailability {
  case Ready, Unavailable, Failed
}

/** Immutable supplier metadata; payload semantics remain owned by the domain. */
final case class KnowledgeContributionDeclaration(
  knowledgeDomainId: String,
  generation: String,
  schema: KnowledgeContributionSchema,
  provenance: Vector[KnowledgeSourceRef],
  availability: KnowledgeContributionAvailability,
  contractVersion: Int = 1
)

final case class KnowledgeContributionSnapshot(
  declaration: KnowledgeContributionDeclaration,
  payload: Vector[Byte]
)

/** CNCF adds trusted installed-provider attribution to the supplier declaration. */
final case class ComponentKnowledgeDescriptor(
  providerComponent: ComponentInstanceId,
  declaration: KnowledgeContributionDeclaration
) {
  def knowledgeDomainId: String = declaration.knowledgeDomainId
  def generation: String = declaration.generation
  def schema: KnowledgeContributionSchema = declaration.schema
  def provenance: Vector[KnowledgeSourceRef] = declaration.provenance
  def availability: KnowledgeContributionAvailability = declaration.availability
  def contractVersion: Int = declaration.contractVersion
}

/** A discovered reference has no public construction or executable supplier field. */
abstract class ComponentKnowledgeContribution private[knowledge] () {
  def descriptor: ComponentKnowledgeDescriptor
}

final case class ComponentKnowledgeSnapshot(
  descriptor: ComponentKnowledgeDescriptor,
  payload: Vector[Byte]
)
