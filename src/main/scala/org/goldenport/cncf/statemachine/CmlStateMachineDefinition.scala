package org.goldenport.cncf.statemachine

/*
 * Cozy-generated statemachine metadata passed through component contracts.
 *
 * @since   Mar. 24, 2026
 *  version Mar. 25, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CmlStateMachineDefinition(
  name: String,
  states: Vector[String] = Vector.empty,
  events: Vector[String] = Vector.empty,
  historyFieldName: Option[String] = None,
  historyComposites: Vector[CmlHistoryCompositeDefinition] = Vector.empty
)

final case class CmlHistoryCompositeDefinition(
  name: String,
  directLeaves: Vector[String] = Vector.empty,
  fallbackLeaf: Option[String] = None
)
