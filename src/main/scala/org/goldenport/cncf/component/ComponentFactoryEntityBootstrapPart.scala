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
private[component] trait ComponentFactoryEntityBootstrapPart { self: ComponentFactory =>

  private[component] def _bootstrap_entities(
    component: Component,
    revisionbindings: Map[String, Option[EntityRevisionBinding]],
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    _entity_collection_names(component).distinct.foreach { name =>
      val storerealm = _create_store_realm(name, storesnapshot)
      var storage = EntityStorage(storerealm)
      val legacymemoryplan = _legacy_memory_plan(name)
      val descriptor = EntityDescriptor(
        collectionId = _bootstrap_collection_id(component, name),
        plan = legacymemoryplan,
        persistent = _bootstrap_entity_persistent(component, name),
        revisionBinding =
          revisionbindings.getOrElse(_normalize_entity_name(name), None)
      )

      val memoryrealm = new PartitionedMemoryRealm[Any](
        strategy = legacymemoryplan.partitionStrategy,
        idOf = _entity_id_of_any,
        maxPartitions = legacymemoryplan.maxPartitions,
        maxEntitiesPerPartition = legacymemoryplan.maxEntitiesPerPartition
      )
      storage = storage.copy(memoryRealm = Some(memoryrealm))

      if (entityspace.entityOption(descriptor.collectionId).isEmpty) {
        val collection = new EntityCollection[Any](descriptor, storage)
        entityspace.registerEntity(name, collection)
      }
    }
  }

  private[component] def _create_store_realm(
    name: String,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): EntityRealm[Any] = {
    given EntityPersistent[Any] = _entity_persistent_any
    val state = new IdRef[EntityRealmState[Any]](EntityRealmState(Map.empty))
    new EntityRealm[Any](
      entityName = name,
      loader = EntityLoader[Any](id => _load_entity_from_store(storesnapshot, id)),
      state = state
    )
  }

  private def _load_entity_from_store(
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any],
    id: EntityId
  ): Option[Any] = {
    storesnapshot.get(id)
  }

  private[component] def _entity_id_of_any(
    p: Any
  ): EntityId =
    p match {
      case m: EntityPersistable => m.id
      case _ => throw new IllegalStateException("Entity must implement EntityPersistable to be cached in memory realm")
    }

  private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value: A = initial

    def get: A = synchronized {
      _value
    }

    def set(a: A): Unit = synchronized {
      _value = a
    }

    override def getAndSet(a: A): A = synchronized {
      val prev = _value
      _value = a
      prev
    }

    def access: (A, A => Boolean) = synchronized {
      val snapshot = _value
      val setter: A => Boolean = (next: A) => synchronized {
        if (_value == snapshot) {
          _value = next
          true
        } else {
          false
        }
      }
      (snapshot, setter)
    }

    override def tryUpdate(f: A => A): Boolean = synchronized {
      _value = f(_value)
      true
    }

    override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
      val (next, out) = f(_value)
      _value = next
      Some(out)
    }

    def update(f: A => A): Unit = synchronized {
      _value = f(_value)
    }

    def modify[B](f: A => (A, B)): B = synchronized {
      val (next, out) = f(_value)
      _value = next
      out
    }

    override def modifyState[B](state: State[A, B]): B = synchronized {
      val (next, out) = state.run(_value).value
      _value = next
      out
    }

    override def tryModifyState[B](state: State[A, B]): Option[B] = synchronized {
      val (next, out) = state.run(_value).value
      _value = next
      Some(out)
    }
  }


  private[component] def _entity_collection_names(
    component: Component
  ): Vector[String] = {
    val names =
      if (component.aggregateDefinitions.nonEmpty)
        component.aggregateDefinitions.map(_.entityName).toVector
      else if (component.viewDefinitions.nonEmpty)
        component.viewDefinitions.map(_.entityName).toVector
      else
        component.core.protocol.services.services
          .map(_.name)
          .filterNot(_ == "meta")
          .filterNot(_ == "system")
          .toVector
    // Fallback collection when no service-derived names exist
    if (names.nonEmpty) names.distinct else Vector("default")
  }

}
