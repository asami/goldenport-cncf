package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*

/** Component/Factory declaration, not an engine, executable definition loader
  * or default storage selection. None keeps legacy components opt-out.
  * @since   Sep. 29, 2026
  * @version Oct. 10, 2026
  */
trait WorkflowInstancePersistenceSource {
  def workflowInstancePersistenceDeclarationOption:
    Option[WorkflowInstancePersistenceBinding.Declaration] = None
}

/** Immutable bootstrap binding. Each SPI call borrows a fresh managed view
  * inside the owning Subsystem lease; never retain a lease-bound SqlDataStore.
  * This binding grants no operation/command authority and owns no resource.
  */
final class WorkflowInstancePersistenceBinding private (
  val componentId: ComponentId,
  val declaration: WorkflowInstancePersistenceBinding.Declaration,
  subsystem: Subsystem,
  environment: ComponentDataStore.Environment,
  request: ComponentDataStore.Request
) {
  def createC(record: InstanceRecord)(using ExecutionContext): Consequence[InstanceRecord] =
    withProviderC(_.create(declaration.configuration, record))

  def loadC(identity: InstanceIdentity)(using ExecutionContext): Consequence[Option[InstanceRecord]] =
    withProviderC(_.load(declaration.configuration, identity))

  def findBySessionC(sessionId: String, workflowIdentity: WorkflowDefinitionIdentity)(
    using ExecutionContext
  ): Consequence[Vector[InstanceRecord]] =
    withProviderC(_.findBySessionC(sessionId, workflowIdentity))

  def appendC(identity: InstanceIdentity, expectedRevision: InstanceRevision,
    entry: HistoryEntry)(using ExecutionContext): Consequence[InstanceRecord] =
    withProviderC(_.append(declaration.configuration, identity, expectedRevision, entry))

  private[workflow] def probeC()(using ExecutionContext): Consequence[Unit] =
    withProviderC(_ => Consequence.unit)

  private def withProviderC[A](f: WorkflowInstanceDataStorePersistence => Consequence[A])(
    using ExecutionContext
  ): Consequence[A] =
    subsystem._with_managed_datastore_lease_c { _ =>
      subsystem.resolveManagedComponentDataStoreC(environment, request).flatMap { store =>
        WorkflowInstanceDataStorePersistence.bindC(store, declaration.configuration,
          declaration.definitions, declaration.collection).flatMap(f)
      }
    }
}

object WorkflowInstancePersistenceBinding {
  final case class Declaration(
    configuration: Configuration,
    definitions: Vector[DefinitionBinding],
    collection: DataStore.CollectionId
  )

  /** No implicit in-memory fallback. Explicit canonical datastore configuration
    * and resource lifecycle remain owned by ComponentDataStore/SystemNode.
    */
  def bindC(componentId: ComponentId, subsystem: Subsystem,
    environment: ComponentDataStore.Environment, declaration: Declaration
  )(using ExecutionContext): Consequence[WorkflowInstancePersistenceBinding] =
    if (componentId == null || subsystem == null || environment == null ||
        declaration == null || declaration.configuration == null ||
        declaration.collection == null || declaration.collection.collectionName == null ||
        declaration.collection.collectionName.trim.isEmpty)
      Consequence.configurationInvalid("Workflow instance bootstrap declaration is incomplete")
    else for {
      _ <- declaration.configuration.validateC
      _ <- WorkflowInstanceRecordJsonV1.catalogC(declaration.definitions)
      request = ComponentDataStore.Request.forComponent(componentId,
        declaration.configuration.workflowStore.name)
      _ <- if (request.canonicalComponentId.contains(componentId) &&
          request.normalizedName == declaration.configuration.workflowStore.name) Consequence.unit
        else Consequence.configurationInvalid("Workflow instance bootstrap requires canonical component and store names")
      binding = new WorkflowInstancePersistenceBinding(componentId, declaration,
        subsystem, environment, request)
      _ <- binding.probeC()
    } yield binding
}
