package org.goldenport.cncf.entity.aggregate

/*
 * @since   Mar. 21, 2026
 *  version Mar. 31, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AggregateDefinition(
  name: String,
  entityName: String,
  members: Vector[AggregateMemberDefinition] = Vector.empty,
  creates: Vector[AggregateCreateDefinition] = Vector.empty,
  commands: Vector[AggregateCommandDefinition] = Vector.empty,
  state: Vector[AggregateStateDefinition] = Vector.empty,
  invariants: Vector[AggregateInvariantDefinition] = Vector.empty
)

final case class AggregateMemberDefinition(
  name: String,
  entityName: String,
  kind: Option[String] = None,
  boundary: Option[String] = None,
  join: Option[String] = None,
  joinFieldName: Option[String] = None,
  multiplicity: Option[String] = None
)

final case class AggregateCommandDefinition(
  name: String,
  input: Map[String, String] = Map.empty,
  validations: Vector[String] = Vector.empty,
  events: Vector[String] = Vector.empty,
  newState: Option[String] = None
)

final case class AggregateCreateDefinition(
  name: String,
  input: Map[String, String] = Map.empty,
  validations: Vector[String] = Vector.empty,
  events: Vector[String] = Vector.empty,
  initialState: Option[String] = None
)

final case class AggregateStateDefinition(
  name: String,
  datatype: Option[String] = None,
  multiplicity: Option[String] = None
)

final case class AggregateInvariantDefinition(
  name: String,
  expression: Option[String] = None
)
