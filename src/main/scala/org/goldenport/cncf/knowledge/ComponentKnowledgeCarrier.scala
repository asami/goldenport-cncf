package org.goldenport.cncf.knowledge

import org.goldenport.Consequence

/*
 * Stable value-only declaration for a packaged Component knowledge consumer
 * contract. It deliberately carries neither consumer bytes nor a resource
 * location other than its one canonical CAR logical path.
 *
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentKnowledgeCarrier(
  carrierSchema: String,
  consumerContractSchema: String,
  logicalPath: String,
  sha256: String
)

object ComponentKnowledgeCarrier {
  val SCHEMA = "cncf.component-knowledge-carrier.v1"
  val LOGICAL_PATH = "component-knowledge.json"

  def createC(sha256: String): Consequence[ComponentKnowledgeCarrier] =
    validateC(
      ComponentKnowledgeCarrier(
        SCHEMA,
        ComponentKnowledgeManifestConsumerContract.SCHEMA,
        LOGICAL_PATH,
        sha256
      )
    )

  def validateC(value: ComponentKnowledgeCarrier): Consequence[ComponentKnowledgeCarrier] =
    _validate(value).fold(Consequence.argumentInvalid, Consequence.success)

  private def _validate(value: ComponentKnowledgeCarrier): Either[String, ComponentKnowledgeCarrier] =
    for {
      _ <- Either.cond(
        value.carrierSchema == SCHEMA,
        (),
        s"Component knowledge carrier schema must be exactly '$SCHEMA'"
      )
      _ <- Either.cond(
        value.consumerContractSchema == ComponentKnowledgeManifestConsumerContract.SCHEMA,
        (),
        s"Component knowledge carrier consumer contract schema must be exactly '${ComponentKnowledgeManifestConsumerContract.SCHEMA}'"
      )
      _ <- Either.cond(
        value.logicalPath == LOGICAL_PATH,
        (),
        s"Component knowledge carrier logical path must be exactly '$LOGICAL_PATH'"
      )
      _ <- Either.cond(
        value.sha256.matches("[0-9a-f]{64}"),
        (),
        "Component knowledge carrier SHA-256 must be lowercase hexadecimal"
      )
    } yield value
}
