package org.goldenport.cncf.component

import java.nio.file.{Files, Path, Paths}
import java.lang.reflect.InvocationTargetException
import cats.effect.Ref
import cats.data.State
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.config.{ConfigurationAccess, RuntimeConfig}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.backend.collaborator.{Collaborator, CollaboratorFactory}
import org.goldenport.cncf.collaborator.api
import org.goldenport.cncf.component.repository.{ComponentRepository, ComponentRepositorySpace}
import org.goldenport.cncf.component.repository.ComponentSource
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.datastore.{DataStore, TotalCountCapability}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.value.NominalScalar
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityConcurrencyPolicy, EntityPersistable, EntityPersistent, EntityQuery, EntityRevisionBinding, EntityRevisionModelKind, EntityRevisionModelMetadata, EntityRevisionRepresentation, EntityStore}
import org.goldenport.cncf.entity.aggregate.{AggregateAssembler, AggregateBuilder, AggregateCollection, AggregateSpace, AggregateDefinition, ContextualAggregateBuilder, ContextualAggregateCount, ContextualAggregateQuery}
import org.goldenport.cncf.event.{ActionCallDispatcher, EventBus, EventReception, EventStore, EntitySubscriptionLimit}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityDescriptor, EntityLoader, EntityMemoryPolicy, EntityRealm, EntityRealmState, EntityRuntimeDescriptor, EntityRuntimePlan, EntitySpace, EntityStorage, PartitionedMemoryRealm, PartitionStrategy, WorkingSetDefinition, WorkingSetDescriptor, WorkingSetInitializer, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.view.{Browser, ContextualBrowserCount, ContextualBrowserFind, ContextualBrowserQuery, ContextualViewBuilder, ViewDefinition, ViewBuilder, ViewCollection, ViewSpace}
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.statemachine.{CmlStateMachineDefinitionProvider, CollectionStateMachinePlanner, CollectionStateMachinePlannerProvider, CollectionTransitionRule, CollectionTransitionRuleProvider, TransitionTrigger, TransitionRule}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.spi.SpiResolver
import org.goldenport.schema.{Column, Multiplicity, Schema, ValueDomain, WebColumn, XString}
import org.goldenport.cncf.workflow.WorkflowDefinition
import org.goldenport.cncf.workflow.{ContinuationRuntimeSource, ContinuationSpiAdapter, IssuedWorkOrderPersistence, GeneratedProvidedApiAbi, GeneratedProvidedApiMetadataProvider, GeneratedWorkflowAbi, GeneratedWorkflowMetadataProvider, StateMachineProvidedApiDispatcher, StateMachineProvidedApiProgramSource, StateMachineProviderResolver, StateMachineProviderSource}
import org.simplemodeling.model.value.BaseContent
import scala.util.Try

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[component] trait ComponentFactoryWorkflowPart { self: ComponentFactory =>

  private[component] def _bootstrap_event_reception(
    component: Component
  ): Unit = {
    val store = component.subsystem.map(_.eventStore).getOrElse(EventStore.inMemory)
    val _ = component.withEventStore(store)
    component.jobEngine match {
      case m: org.goldenport.cncf.job.InMemoryJobEngine =>
        m.withEventStore(store).withEventBus(_shared_event_bus(component, store))
      case _ =>
        ()
    }
    if (
      component.eventReceptionDefinitions.nonEmpty ||
      component.eventSubscriptionDefinitions.nonEmpty ||
      component.eventReceptionRuleDefinitions.nonEmpty ||
      component.workflowDefinitions.nonEmpty
    ) {
      val bus = _shared_event_bus(component, store)
      val reception = createEventReceptionWithOperationDispatcher(component, bus)
      component.withEventReception(reception)
      component.subsystem.foreach(_.registerEventReception(component.name, reception))
    }
    if (component.workflowDefinitions.nonEmpty)
      _bootstrap_workflow(component)
  }

  private def _shared_event_bus(
    component: Component,
    store: EventStore
  ): EventBus =
    component.subsystem match {
      case Some(subsystem) => subsystem.eventBus
      case None => EventBus.default(org.goldenport.cncf.event.EventEngine.noop(DataStore.noop(), eventstore = store))
    }

  def createEventReceptionWithOperationDispatcher(
    component: Component,
    eventBus: EventBus,
    ingressSecurityResolver: IngressSecurityResolver = IngressSecurityResolver.default,
    entitySubscriptionLimit: EntitySubscriptionLimit = EntitySubscriptionLimit()
  ): EventReception =
    createEventReception(
      component = component,
      eventBus = eventBus,
      dispatcher = createOperationActionDispatcher(component),
      ingressSecurityResolver = ingressSecurityResolver,
      entitySubscriptionLimit = entitySubscriptionLimit
    )

  def createEventReception(
    component: Component,
    eventBus: EventBus,
    dispatcher: ActionCallDispatcher,
    ingressSecurityResolver: IngressSecurityResolver = IngressSecurityResolver.default,
    entitySubscriptionLimit: EntitySubscriptionLimit = EntitySubscriptionLimit()
  ): EventReception = {
    val reception = EventReception.default(
      eventBus = eventBus,
      dispatcher = dispatcher,
      ingressSecurityResolver = ingressSecurityResolver,
      entitySpace = Some(component.entitySpace),
      entitySubscriptionLimit = entitySubscriptionLimit,
      workingSetEntities = component.workingSetEntityNames,
      currentSubsystemName = component.subsystem.map(_.name),
      currentComponentName = Try(component.name).toOption,
      // EventReception can be created from lightweight test components
      // before Component.core initialization.
      jobEngine = Try(component.jobEngine).toOption
    )
    component.eventReceptionDefinitions.foreach(reception.register)
    _register_workflow_event_definitions(component.workflowDefinitions, reception)
    component.eventSubscriptionDefinitions.foreach(reception.registerSubscription)
    component.eventReceptionRuleDefinitions.foreach(reception.registerRule)
    reception
  }

  private def _register_workflow_event_definitions(
    definitions: Vector[WorkflowDefinition],
    reception: EventReception
  ): Unit = {
    val existing = scala.collection.mutable.HashSet.empty[String] ++ reception.definitions.map(_.name)
    definitions.iterator.flatMap(_.registrations).foreach { registration =>
      if (!existing.contains(registration.eventName)) {
        reception.register(
          org.goldenport.cncf.event.CmlEventDefinition(
            name = registration.eventName,
            category = org.goldenport.cncf.event.CmlEventCategory.NonActionEvent
          )
        )
        existing += registration.eventName
      }
    }
  }

  def createOperationActionDispatcher(
    component: Component
  ): ActionCallDispatcher =
    new OperationRequestActionDispatcher(ComponentLogic(component))

  private def _bootstrap_workflow(
    component: Component
  ): Unit =
    component.subsystem.foreach { subsystem =>
      subsystem.workflowEngine.register(component, component.workflowDefinitions)
      component.eventReception.foreach(_.registerWorkflowListener(
        new org.goldenport.cncf.event.WorkflowEventListener {
          def onEvent(
            event: org.goldenport.cncf.event.ReceptionDomainEvent,
            definitions: Vector[org.goldenport.cncf.event.CmlEventDefinition]
          )(using ctx: ExecutionContext): Consequence[Unit] = {
            val _ = definitions
            subsystem.workflowEngine.handle(component.name, event).map(_ => ())
          }
        }
      ))
    }

  private[component] def _bootstrap_generated_workflow_metadata_c(
    component: Component
  ): Consequence[Unit] = {
    val provider = component match {
      case m: GeneratedWorkflowMetadataProvider => Some(m)
      case _ => component.factory.collect {
        case m: GeneratedWorkflowMetadataProvider => m
      }
    }
    provider match {
      case Some(m) =>
        GeneratedWorkflowAbi.admitC(m.generatedWorkflowDefinitions).map { definitions =>
          component.withAdmittedGeneratedWorkflowMetadata(definitions)
          ()
        }
      case None =>
        Consequence.unit
    }
  }

}
