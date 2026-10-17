package org.goldenport.cncf.event

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore, DataStoreComponentOwner, DataStoreConditionalGuardBinding, DataStoreConditionalGuardTarget, DataStoreConditionalMultiGuardHostPolicy}

/** Host configuration data for the Subsystem EventStore service. Creating a
  * declaration alone grants no runtime access. Trusted Subsystem construction
  * installs it once; ordinary in-memory construction has no declaration.
  * This declaration admits storage locations and permissions, not event
  * selection. Workflow evaluates a separate EventPersistencePolicy before
  * preparing durable records; this never means persist every event.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
final class EventStorePersistenceDeclaration private (
  val storeName: String,
  val cursorCollection: DataStore.CollectionId,
  val eventCollection: DataStore.CollectionId,
  val deliveryIndexCollection: DataStore.CollectionId,
  val workflowGrants: Vector[EventStorePersistenceDeclaration.WorkflowGrant],
  val cursorEntryId: DataStore.StringEntryId
) {
  private[cncf] def serviceRequest: ComponentDataStore.Request =
    ComponentDataStore.Request.forComponent(EventStorePersistenceDeclaration.serviceId, storeName)

  private[cncf] def grantC(componentId: ComponentId):
      Consequence[EventStorePersistenceDeclaration.WorkflowGrant] =
    workflowGrants.find(_.componentId == componentId)
      .map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Workflow has no EventStore persistence grant"))

  private[cncf] def policyC(grant: EventStorePersistenceDeclaration.WorkflowGrant):
      Consequence[DataStoreConditionalMultiGuardHostPolicy] =
    for {
      workflowOwner <- DataStoreComponentOwner.create(grant.componentId.name)
      serviceOwner <- DataStoreComponentOwner.create(EventStorePersistenceDeclaration.serviceId.name)
      policy <- DataStoreConditionalMultiGuardHostPolicy.create(
        workflowOwner, grant.rootCollection, grant.continuationCollection,
        Vector(DataStoreConditionalGuardBinding(serviceOwner, cursorCollection)),
        Vector(eventCollection, deliveryIndexCollection),
        Vector(DataStoreConditionalGuardTarget(
          DataStoreConditionalGuardBinding(serviceOwner, cursorCollection), cursorEntryId)))
    } yield policy
}

object EventStorePersistenceDeclaration {
  /** Identity belongs to the existing Subsystem service, not to plan input. */
  val serviceId: ComponentId = ComponentId("org.goldenport.cncf.EventStore")
  val MAX_WORKFLOW_GRANTS = 64

  final case class WorkflowGrant(
    componentId: ComponentId,
    storeName: String,
    rootCollection: DataStore.CollectionId,
    continuationCollection: DataStore.CollectionId
  ) {
    private[cncf] def request: ComponentDataStore.Request =
      ComponentDataStore.Request.forComponent(componentId, storeName)
  }

  def createC(
    storeName: String,
    cursorCollection: DataStore.CollectionId,
    eventCollection: DataStore.CollectionId,
    deliveryIndexCollection: DataStore.CollectionId,
    workflowGrants: Vector[WorkflowGrant],
    cursorEntryId: DataStore.StringEntryId = DataStore.StringEntryId("global")
  ): Consequence[EventStorePersistenceDeclaration] = {
    val serviceCollections = Vector(cursorCollection, eventCollection, deliveryIndexCollection)
    if (cursorEntryId == null || cursorEntryId.print == null || cursorEntryId.print.isEmpty ||
        cursorEntryId.print.trim != cursorEntryId.print || cursorEntryId.print.length > 128 ||
        !_canonicalStoreName(serviceId, storeName) ||
        !serviceCollections.forall(_entityCollection) ||
        serviceCollections.distinct.size != serviceCollections.size ||
        workflowGrants == null || workflowGrants.isEmpty ||
        workflowGrants.size > MAX_WORKFLOW_GRANTS ||
        workflowGrants.exists(grant => grant == null || grant.componentId == null ||
          grant.componentId == serviceId || !_canonicalStoreName(grant.componentId, grant.storeName) ||
          !_entityCollection(grant.rootCollection) || !_entityCollection(grant.continuationCollection) ||
          grant.rootCollection == grant.continuationCollection) ||
        workflowGrants.map(_.componentId).distinct.size != workflowGrants.size)
      Consequence.configurationInvalid("EventStore persistence requires canonical store names and distinct exact entity collections and Workflow grants")
    else {
      val collections = serviceCollections ++ workflowGrants.flatMap(grant =>
        Vector(grant.rootCollection, grant.continuationCollection))
      if (collections.distinct.size != collections.size)
        Consequence.configurationInvalid("EventStore persistence collections have ambiguous ownership")
      else
        Consequence.success(new EventStorePersistenceDeclaration(
          storeName, cursorCollection, eventCollection, deliveryIndexCollection, workflowGrants, cursorEntryId))
    }
  }

  private def _canonicalStoreName(componentId: ComponentId, name: String): Boolean =
    name != null && name.nonEmpty &&
      ComponentDataStore.Request.forComponent(componentId, name).normalizedName == name

  private def _entityCollection(collection: DataStore.CollectionId): Boolean =
    collection match {
      case DataStore.CollectionId.EntityStore(_) =>
        collection.collectionName != null && collection.collectionName.trim.nonEmpty
      case _ => false
    }
}
