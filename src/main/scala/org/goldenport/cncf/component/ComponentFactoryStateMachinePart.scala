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
private[component] trait ComponentFactoryStateMachinePart { self: ComponentFactory =>

  private[component] def _bootstrap_state_machine_planners(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Unit = {
    _bootstrap_state_machine_definitions(component)
    val provider = new CollectionStateMachinePlannerProvider(component.stateMachinePlannerProvider)
    val rules = _default_collection_transition_rules(component, plans)
    val saverulesbycollection = rules.collect {
      case m if m.trigger == TransitionTrigger.Save || m.trigger == TransitionTrigger.Operation => m
    }.groupBy(_.collectionName)
    val updaterulesbycollection = rules.collect {
      case m if m.trigger == TransitionTrigger.Update || m.trigger == TransitionTrigger.Operation => m
    }.groupBy(_.collectionName)

    saverulesbycollection.foreach { case (name, groupedrules) =>
      val planner = new CollectionStateMachinePlanner[Any](
        groupedrules.toVector.map(_to_transition_rule_any)
      )
      provider.registerSave(name, planner)
    }
    updaterulesbycollection.foreach { case (name, groupedrules) =>
      val planner = new CollectionStateMachinePlanner[Any](
        groupedrules.toVector.map(_to_transition_rule_any)
      )
      provider.registerUpdate(name, planner)
    }

    val _ = component.withStateMachinePlannerProvider(provider)
  }

  private def _bootstrap_state_machine_definitions(
    component: Component
  ): Unit = {
    val provider = component match {
      case m: CmlStateMachineDefinitionProvider => Some(m)
      case _ => component.factory.collect {
        case m: CmlStateMachineDefinitionProvider => m
      }
    }
    provider.foreach { m =>
      component.withStateMachineDefinitions(m.stateMachineDefinitions)
    }
  }

  private[component] def _bootstrap_generated_provided_api_metadata_c(
    component: Component
  ): Consequence[Unit] = {
    val provider = component match {
      case m: GeneratedProvidedApiMetadataProvider => Some(m)
      case _ => component.factory.collect {
        case m: GeneratedProvidedApiMetadataProvider => m
      }
    }
    provider match {
      case Some(m) =>
        GeneratedProvidedApiAbi.admitC(
          m.generatedProvidedApiDefinitions,
          component.admittedGeneratedWorkflowMetadata
        ).map { definitions =>
          component.withAdmittedGeneratedProvidedApiMetadata(definitions)
          ()
        }
      case None => Consequence.unit
    }
  }

  private[component] def _bootstrap_state_machine_provider_resolver_c(
    component: Component
  ): Consequence[Unit] = {
    val source = component match {
      case m: StateMachineProviderSource => Some(m)
      case _ => component.factory.collect {
        case m: StateMachineProviderSource => m
      }
    }
    source match {
      case Some(m) =>
        StateMachineProviderResolver.create(
          m.stateMachineProviderBindings,
          m.stateMachineProviders
        ).map { resolver =>
          component.withStateMachineProviderResolver(resolver)
          ()
        }
      case None =>
        Consequence.unit
    }
  }

  private[component] def _bootstrap_state_machine_provided_api_dispatcher_c(
    component: Component
  ): Consequence[Unit] = {
    val source = component match {
      case m: StateMachineProvidedApiProgramSource => Some(m)
      case _ => component.factory.collect {
        case m: StateMachineProvidedApiProgramSource => m
      }
    }
    StateMachineProvidedApiDispatcher.createC(
      component.admittedGeneratedProvidedApiMetadata,
      component.admittedGeneratedWorkflowMetadata,
      source.toVector.flatMap(_.stateMachineProvidedApiPrograms)
    ).map { dispatcher =>
      component.withStateMachineProvidedApiDispatcher(dispatcher)
      ()
    }
  }

  private[component] def _bootstrap_continuation_runtime_c(
    component: Component
  ): Consequence[Unit] = {
    val source = component match {
      case m: ContinuationRuntimeSource => Some(m)
      case _ => component.factory.collect {
        case m: ContinuationRuntimeSource => m
      }
    }
    source match {
      case Some(m) =>
        m.continuationRuntimeOption match {
          case Some(runtime) if runtime != null =>
            component.withContinuationRuntime(runtime)
            Consequence.unit
          case Some(_) =>
            Consequence.componentInvalid("continuation runtime source returned null")
          case None =>
            Consequence.unit
        }
      case None => Consequence.unit
    }
  }

  private def _to_transition_rule_any(p: CollectionTransitionRule[Any]): TransitionRule[Any] =
    TransitionRule[Any](
      eventName = p.eventName,
      priority = p.priority,
      declarationOrder = p.declarationOrder,
      guard = p.guard,
      plan = p.plan,
      machineName = p.machineName,
      stateFieldName = p.stateFieldName,
      fromState = p.fromState,
      fromStateValue = p.fromStateValue,
      toState = p.toState,
      toStateValue = p.toStateValue,
      historyCompositeName = p.historyCompositeName,
      historyFieldName = p.historyFieldName,
      historyDirectLeaves = p.historyDirectLeaves,
      historyDirectLeafValues = p.historyDirectLeafValues,
      historyFallbackLeaf = p.historyFallbackLeaf,
      expectedHistoryRecordWrites = p.expectedHistoryRecordWrites,
      binding = p.binding,
      trigger = p.trigger
    )

  private def _default_collection_transition_rules(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Vector[CollectionTransitionRule[Any]] =
    component match {
      case m: CollectionTransitionRuleProvider =>
        val _ = plans
        m.stateMachineTransitionRules
      case _ =>
        component.factory match {
          case Some(m: CollectionTransitionRuleProvider) =>
            val _ = plans
            m.stateMachineTransitionRules
          case _ =>
            Vector.empty
        }
    }

}
