package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.simplemodeling.model.datatype.EntityCollectionId

/** Pure host declaration. Construction does not issue a runtime capability,
  * open storage, authenticate an actor or authorize an Operation.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
final class ComponentRecordStorageDeclaration private (
  val grants: Vector[ComponentRecordStorageDeclaration.CreateGrant]
) {
  private[cncf] def admitC(componentId: ComponentId, storeName: String,
    collection: DataStore.CollectionId): Consequence[ComponentRecordStorageDeclaration.CreateGrant] =
    grants.find(g => g.componentId == componentId && g.storeName == storeName &&
      g.collection == collection).map(Consequence.success).getOrElse(
      Consequence.configurationInvalid("Component record Create scope is not declared"))

  /** Trusted host composition must exclude Workflow/EventStore infrastructure
    * before capturing this declaration. An empty exclusion set grants nothing.
    */
  private[cncf] def validateReservedC(reserved: Vector[DataStore.CollectionId]): Consequence[Unit] =
    if (reserved == null || reserved.exists(_ == null) ||
        grants.exists(g => reserved.contains(g.collection)))
      Consequence.configurationInvalid("Component record Create targets reserved storage")
    else Consequence.unit
}

object ComponentRecordStorageDeclaration {
  val MAX_GRANTS = 64

  final case class CreateGrant(componentId: ComponentId, storeName: String,
    collection: DataStore.CollectionId)

  def createC(grants: Vector[CreateGrant]): Consequence[ComponentRecordStorageDeclaration] =
    if (grants == null || grants.isEmpty || grants.size > MAX_GRANTS ||
        grants.exists(g => g == null || !_canonical_grant(g)) ||
        grants.map(_.collection).distinct.size != grants.size)
      Consequence.configurationInvalid("Component record storage requires bounded canonical grants and unambiguous collections")
    else Consequence.success(new ComponentRecordStorageDeclaration(grants))

  private def _canonical_grant(grant: CreateGrant): Boolean =
    grant.componentId != null && grant.componentId.name != null &&
      grant.storeName != null && grant.storeName.nonEmpty &&
      ComponentDataStore.Request.forComponent(grant.componentId, grant.storeName)
        .canonicalComponentId.contains(grant.componentId) &&
      ComponentDataStore.Request.forComponent(grant.componentId, grant.storeName)
        .normalizedName == grant.storeName && _canonical_collection(grant.collection)

  private def _canonical_collection(collection: DataStore.CollectionId): Boolean =
    collection match {
      case DataStore.CollectionId.EntityStore(identity) if identity != null =>
        EntityCollectionId.parse(identity.print).toOption.contains(identity)
      case _ => false
    }
}
