package org.goldenport.cncf.display

import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Immutable presentation candidates, carrying neither validation proof nor execution authority. */
sealed abstract class DisplayModel {
  def screenId: String
}

object DisplayModel {
  final case class ListModel(
    screenId: String, title: String, items: Vector[ListItem],
    detailTarget: GeneratedLogicalUiAbi.DetailTarget, actions: Vector[Action],
    snapshot: Option[QuerySnapshot]
  ) extends DisplayModel
  final case class DetailModel(
    screenId: String, title: String, resource: Resource,
    sections: Vector[Section], actions: Vector[Action]
  ) extends DisplayModel
  final case class Resource(entityId: EntityId, revision: EntityRevision)
  /** Opaque source-query correlation; never an observed mutation revision. */
  final case class QuerySnapshot(reference: String)
  final case class ListItem(resource: Resource, fields: Vector[Field], actions: Vector[Action])
  final case class Section(id: String, title: Option[String], fields: Vector[Field])
  final case class Field(
    id: String, label: String, role: GeneratedLogicalUiAbi.PresentationRole, value: DisplayValue
  )
  /** Inert descriptor; every availability state is presentation data only. */
  final case class Action(id: String, label: String, bindingId: String, availability: ActionAvailability)
  enum ActionAvailability {
    case Hidden, Disabled, Enabled
  }
}
