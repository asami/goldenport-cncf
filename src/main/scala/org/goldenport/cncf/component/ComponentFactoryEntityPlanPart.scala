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
private[component] trait ComponentFactoryEntityPlanPart { self: ComponentFactory =>

  private[component] def _bootstrap_collections_c(
    component: Component
  ): Consequence[Component] = {
    val storesnapshot = scala.collection.concurrent.TrieMap.empty[EntityId, Any]
    // One EntitySpace per component. All collections in the component share it.
    val entityspace = component.entitySpace
    val aggregatespace = component.aggregateSpace
    val viewspace = component.viewSpace
    val rawplans = _uncanonicalized_entity_runtime_plans(component)
    val plans =
      _canonicalize_entity_runtime_plan_names(component, rawplans)
    val entitynames =
      if (plans.nonEmpty) plans.map(_.entityName)
      else _entity_collection_names(component)
    for {
      _ <- _bootstrap_generated_workflow_metadata_c(component)
      _ <- _bootstrap_generated_provided_api_metadata_c(component)
      _ <- _bootstrap_state_machine_provided_api_dispatcher_c(component)
      _ <- _bootstrap_state_machine_provider_resolver_c(component)
      _ <- _bootstrap_continuation_runtime_c(component)
      _ <- _validate_entity_runtime_plan_names_c(component, rawplans)
      revisionbindings <- _resolve_revision_bindings_c(component, entitynames)
      concurrencypolicies <-
        _resolve_concurrency_policies_c(component, entitynames)
      _ <- _validate_concurrency_bindings_c(
        entitynames,
        revisionbindings,
        concurrencypolicies
      )
    } yield {
      val effectiveplans = plans.map { plan =>
        plan.copy(
          concurrencyPolicy =
            concurrencypolicies.getOrElse(
              _normalize_entity_name(plan.entityName),
              EntityConcurrencyPolicy.default
            )
        )
      }
      _enrich_component_descriptors(component, effectiveplans)
      val workingsetentities =
        _resolve_working_set_entity_names(component, effectiveplans)
      val _ = component.withWorkingSetEntityNames(workingsetentities)
      if (effectiveplans.nonEmpty)
        _bootstrap_entities_with_plan(
          component,
          effectiveplans,
          revisionbindings,
          entityspace,
          storesnapshot
        )
      else
        _bootstrap_entities(
          component,
          revisionbindings,
          entityspace,
          storesnapshot
        )
      _bootstrap_aggregates(component, aggregatespace, entityspace)
      _bootstrap_views(component, viewspace, entityspace)
      if (_working_set_enabled_for_runtime) {
        if (effectiveplans.nonEmpty)
          _initialize_working_sets_from_plan(
            effectiveplans,
            entityspace,
            storesnapshot
          )
        else
          _initialize_working_sets(component, entityspace, storesnapshot)
      } else {
        _disable_working_sets(entityspace)
      }
      _bootstrap_state_machine_planners(component, effectiveplans)
      _bootstrap_event_reception(component)
      component.withCollectionsBootstrapped()
    }
  }

  private def _resolve_concurrency_policies_c(
    component: Component,
    entitynames: Vector[String]
  ): Consequence[Map[String, EntityConcurrencyPolicy]] = {
    val descriptors = (
      component.componentDescriptors.flatMap(_.entityRuntimeDescriptors) ++
      _runtime_component_descriptors_for(component)
        .flatMap(_.entityRuntimeDescriptors)
    ).distinct
    entitynames.distinct.foldLeft(
      Consequence.success(Map.empty[String, EntityConcurrencyPolicy])
    ) { (z, entityname) =>
      for {
        policies <- z
        policy <- _resolve_concurrency_policy_c(entityname, descriptors)
      } yield policies.updated(_normalize_entity_name(entityname), policy)
    }
  }

  private def _resolve_concurrency_policy_c(
    entityname: String,
    descriptors: Vector[EntityRuntimeDescriptor]
  ): Consequence[EntityConcurrencyPolicy] = {
    val matching =
      descriptors.filter(_matches_entity_descriptor(_, entityname))
    val entitypolicies = matching
      .filter(_.revisionModelKind.nonEmpty)
      .flatMap(_.concurrencyPolicy)
      .distinct
    val collectionpolicies = matching
      .filter(_.revisionModelKind.isEmpty)
      .flatMap(_.concurrencyPolicy)
      .distinct
    if (entitypolicies.size > 1)
      Consequence.configurationInvalid(
        s"conflicting Entity concurrency policies for '$entityname'"
      )
    else if (collectionpolicies.size > 1)
      Consequence.configurationInvalid(
        s"conflicting Entity collection concurrency policies for '$entityname'"
      )
    else
      Consequence.success(
        collectionpolicies.headOption
          .orElse(entitypolicies.headOption)
          .getOrElse(EntityConcurrencyPolicy.default)
      )
  }

  private def _resolve_revision_bindings_c(
    component: Component,
    entitynames: Vector[String]
  ): Consequence[Map[String, Option[EntityRevisionBinding]]] = {
    val descriptors = (
      component.componentDescriptors.flatMap(_.entityRuntimeDescriptors) ++
      _runtime_component_descriptors_for(component)
        .flatMap(_.entityRuntimeDescriptors)
    ).distinct
    entitynames.distinct.foldLeft(
      Consequence.success(Map.empty[String, Option[EntityRevisionBinding]])
    ) { (z, entityname) =>
      z.flatMap { bindings =>
        _resolve_revision_binding_c(entityname, descriptors).map { binding =>
          bindings.updated(_normalize_entity_name(entityname), binding)
        }
      }
    }
  }

  private def _validate_concurrency_bindings_c(
    entitynames: Vector[String],
    revisionbindings: Map[String, Option[EntityRevisionBinding]],
    concurrencypolicies: Map[String, EntityConcurrencyPolicy]
  ): Consequence[Unit] =
    entitynames.distinct.foldLeft(Consequence.unit) { (result, entityname) =>
      result.flatMap { _ =>
        val normalized = _normalize_entity_name(entityname)
        val concurrency =
          concurrencypolicies.getOrElse(
            normalized,
            EntityConcurrencyPolicy.default
          )
        if (
          concurrency == EntityConcurrencyPolicy.Optimistic &&
          revisionbindings.getOrElse(normalized, None).isEmpty
        )
          Consequence.configurationInvalid(
            s"Optimistic concurrency for '$entityname' requires a managed revision representation"
          )
        else
          Consequence.unit
      }
    }

  private def _resolve_revision_binding_c(
    entityname: String,
    descriptors: Vector[EntityRuntimeDescriptor]
  ): Consequence[Option[EntityRevisionBinding]] = {
    val matching = descriptors.filter(_matches_entity_descriptor(_, entityname))
    val modeldeclarations = matching.filter(_.revisionModelKind.nonEmpty)
    val modelkinds = matching.flatMap(_.revisionModelKind).distinct
    val modelrepresentations = matching
      .filter(_.revisionModelKind.nonEmpty)
      .flatMap(_.revisionRepresentation)
      .distinct
    val collectionrepresentations = matching
      .filter(_.revisionModelKind.isEmpty)
      .flatMap(_.revisionRepresentation)
      .distinct
    if (
      modeldeclarations.exists { descriptor =>
        descriptor.revisionModelKind.contains(
          EntityRevisionModelKind.SimpleEntity
        ) && descriptor.revisionRepresentation.isEmpty
      }
    )
      Consequence.configurationInvalid(
        s"SimpleEntity revision metadata for '$entityname' requires Embedded representation"
      )
    else if (modelkinds.size > 1)
      Consequence.configurationInvalid(
        s"conflicting Entity revision model kinds for '$entityname'"
      )
    else if (modelrepresentations.size > 1)
      Consequence.configurationInvalid(
        s"conflicting Entity model revision representations for '$entityname'"
      )
    else if (collectionrepresentations.size > 1)
      Consequence.configurationInvalid(
        s"conflicting Entity collection revision representations for '$entityname'"
      )
    else
      modelkinds.headOption match {
        case Some(modelkind) =>
          EntityRevisionBinding.resolve(
            EntityRevisionModelMetadata(
              modelkind,
              modelrepresentations.headOption
            ),
            collectionrepresentations.headOption
          )
        case None if modelrepresentations.nonEmpty || collectionrepresentations.nonEmpty =>
          Consequence.configurationInvalid(
            s"Entity revision representation for '$entityname' requires revision model-kind evidence"
          )
        case None =>
          Consequence.success(None)
      }
  }

  private def _bootstrap_entities_with_plan(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]],
    revisionbindings: Map[String, Option[EntityRevisionBinding]],
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    plans.foreach { plan =>
      _bootstrap_entity_plan(
        component,
        plan,
        revisionbindings.getOrElse(_normalize_entity_name(plan.entityName), None),
        entityspace,
        storesnapshot
      )
    }
  }

  private def _bootstrap_entity_plan(
    component: Component,
    plan: EntityRuntimePlan[Any],
    revisionbinding: Option[EntityRevisionBinding],
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    val name = plan.entityName
    val storerealm = _create_store_realm(name, storesnapshot)
    var storage = EntityStorage(storerealm)
    // TODO: EntityDescriptor + EntityStorage is now the canonical wiring path.
    // When entity definitions are available, this bootstrap should only
    // construct descriptor/storage and avoid any legacy realm-based wiring.
    val descriptor = EntityDescriptor(
      collectionId = _bootstrap_collection_id(component, name),
      plan = plan,
      persistent = _bootstrap_entity_persistent(component, name),
      revisionBinding = revisionbinding
    )

    plan.memoryPolicy match {
      case EntityMemoryPolicy.StoreOnly =>
        ()
      case EntityMemoryPolicy.LoadToMemory =>
        val memoryrealm = new PartitionedMemoryRealm[Any](
          strategy = plan.partitionStrategy,
          idOf = _entity_id_of_any,
          maxPartitions = plan.maxPartitions,
          maxEntitiesPerPartition = plan.maxEntitiesPerPartition
        )
        storage = storage.copy(memoryRealm = Some(memoryrealm))
    }

    if (entityspace.entityOption(descriptor.collectionId).isEmpty) {
      val collection = new EntityCollection[Any](descriptor, storage)
      entityspace.registerEntity(name, collection)
    }
  }

  // Temporary entity bootstrap using service names.
  // This is a minimal runtime bootstrap until Cozy entity metadata
  // is available. Later this should be replaced by entity definitions
  // derived from the Cozy model.
  private def _initialize_working_sets(
    component: Component,
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    val initializer = new WorkingSetInitializer(entityspace, _working_set_clock)
    _default_working_sets(component).foreach { spec =>
      val entities = spec.entities.iterator.toVector
      _prime_store(entityspace, storesnapshot, spec.entityName, entities)
      initializer.preloadAsync(spec.copy(entities = entities))(using scala.concurrent.ExecutionContext.global)
    }
  }

  private[component] def _initialize_working_sets_from_plan(
    plans: Vector[EntityRuntimePlan[Any]],
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any]
  ): Unit = {
    val clock = _working_set_clock
    val initializer = new WorkingSetInitializer(entityspace, clock)
    plans.foreach { plan =>
      plan.workingSet match {
        case Some(ws) =>
          val entities = ws.entities.iterator.toVector
          _prime_store(entityspace, storesnapshot, ws.entityName, entities)
          initializer.preloadAsync(ws.copy(entities = entities))(using scala.concurrent.ExecutionContext.global)
        case None if _has_effective_working_set_policy(plan) =>
          entityspace.entityCollectionsByName(plan.entityName).foreach { collection =>
            // Policy-only working sets need a persistent-store scan before they
            // can be declared ready. Until that loader is wired, keep status
            // initializing so search uses direct store fallback.
            if (collection.storage.memoryRealm.isDefined)
              collection.storage.workingSetStatus.markLoading(clock.instant())
            else
              collection.storage.workingSetStatus.markDisabled()
          }
        case None =>
          entityspace
            .entityCollectionsByName(plan.entityName)
            .foreach(_.storage.workingSetStatus.markDisabled())
      }
    }
  }

  private def _disable_working_sets(
    entityspace: EntitySpace
  ): Unit =
    entityspace.entityCollections.foreach(
      _.storage.workingSetStatus.markDisabled()
    )

  private def _working_set_enabled_for_runtime: Boolean =
    GlobalRuntimeContext.current.map(_.runtimeMode) match {
      case Some(RunMode.Command) | Some(RunMode.Client) => false
      case _ => true
    }

  private def _has_effective_working_set_policy(
    plan: EntityRuntimePlan[Any]
  ): Boolean =
    plan.workingSetPolicy match {
      case Some(WorkingSetPolicy.Disabled) | None => false
      case Some(_) => true
    }

  private def _prime_store[E](
    entityspace: EntitySpace,
    storesnapshot: scala.collection.concurrent.TrieMap[EntityId, Any],
    entityname: String,
    entities: IterableOnce[E]
  ): Unit = {
    entityspace.entityOption[E](entityname).foreach { collection =>
      entities.iterator.foreach { entity =>
        val id = collection.descriptor.persistent.id(entity)
        storesnapshot.put(id, entity)
        collection.storage.storeRealm.put(entity)
      }
    }
  }

  private def _resolve_working_set_entity_names(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Set[String] =
    if (plans.nonEmpty)
      plans.flatMap { plan =>
        if (plan.workingSet.isDefined)
          Vector(plan.entityName)
        else
          plan.workingSetPolicy match {
            case Some(WorkingSetPolicy.Disabled) | None => Vector.empty
            case Some(_) if plan.memoryPolicy == EntityMemoryPolicy.LoadToMemory => Vector(plan.entityName)
            case _ => Vector.empty
          }
      }.toSet
    else
      _default_working_sets(component).map(_.entityName).toSet

  private def _default_working_sets(
    component: Component
  ): Vector[WorkingSetDefinition[Any]] =
    Vector.empty

  private def _default_entity_runtime_plans(
    component: Component
  ): Vector[EntityRuntimePlan[Any]] =
    _canonicalize_entity_runtime_plan_names(
      component,
      _uncanonicalized_entity_runtime_plans(component)
    )

  private def _uncanonicalized_entity_runtime_plans(
    component: Component
  ): Vector[EntityRuntimePlan[Any]] = {
    val declarative = _runtime_descriptors_for(component).map(_.toPlan)
    val config = _config_entity_runtime_plans(component, declarative)
    val code = _programmatic_entity_runtime_plans(component)
    val merged = _merge_entity_runtime_plans(_merge_entity_runtime_plans(declarative, config), code)
    val plans =
      if (merged.nonEmpty)
        merged
      else if (component.aggregateDefinitions.nonEmpty || component.viewDefinitions.nonEmpty)
        _entity_collection_names(component).map(_legacy_memory_plan)
      else
        Vector.empty
    plans
  }

  private def _canonicalize_entity_runtime_plan_names(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Vector[EntityRuntimePlan[Any]] = {
    val collectionnames = _entity_collection_names(component)
    plans.map { plan =>
      collectionnames.find(_ == plan.entityName).orElse(
        collectionnames
          .filter(
            _normalize_entity_name(_) ==
              _normalize_entity_name(plan.entityName)
          )
          .headOption
      )
        .filterNot(_ == plan.entityName)
        .map(name => plan.copy(entityName = name))
        .getOrElse(plan)
    }
  }

  private def _validate_entity_runtime_plan_names_c(
    component: Component,
    plans: Vector[EntityRuntimePlan[Any]]
  ): Consequence[Unit] = {
    val collectionnames = _entity_collection_names(component)
    plans.foldLeft(Consequence.unit) { (result, plan) =>
      result.flatMap { _ =>
        val matches = collectionnames.filter(
          _normalize_entity_name(_) ==
            _normalize_entity_name(plan.entityName)
        )
        if (
          collectionnames.contains(plan.entityName) ||
          matches.size <= 1
        )
          Consequence.unit
        else
          Consequence.configurationInvalid(
            s"Entity runtime plan '${plan.entityName}' ambiguously matches collections: ${matches.mkString(", ")}"
          )
      }
    }
  }

  private[component] def _programmatic_entity_runtime_plans(
    component: Component
  ): Vector[EntityRuntimePlan[Any]] =
    component match {
      case m: EntityRuntimePlanProvider =>
        m.entityRuntimePlans.map(_normalize_code_working_set_policy_source)
      case _ =>
        component.factory match {
          case Some(m: EntityRuntimePlanProvider) =>
            m.entityRuntimePlans.map(_normalize_code_working_set_policy_source)
          case _ =>
            Vector.empty
        }
    }

  private def _normalize_code_working_set_policy_source(
    plan: EntityRuntimePlan[Any]
  ): EntityRuntimePlan[Any] =
    if (plan.workingSetPolicy.isDefined && plan.workingSetPolicySource.isEmpty)
      plan.copy(workingSetPolicySource = Some(WorkingSetPolicySource.Code))
    else
      plan

  private def _merge_entity_runtime_plans(
    base: Vector[EntityRuntimePlan[Any]],
    overrideplans: Vector[EntityRuntimePlan[Any]]
  ): Vector[EntityRuntimePlan[Any]] = {
    val overridebyentity = overrideplans.map(p => _normalize_entity_name(p.entityName) -> p).toMap
    val mergedbase = base.map { plan =>
      overridebyentity.get(_normalize_entity_name(plan.entityName)).getOrElse(plan)
    }
    val appended = overrideplans.filterNot(p =>
      base.exists(x => _normalize_entity_name(x.entityName) == _normalize_entity_name(p.entityName))
    )
    mergedbase ++ appended
  }

  private def _config_entity_runtime_plans(
    component: Component,
    base: Vector[EntityRuntimePlan[Any]]
  ): Vector[EntityRuntimePlan[Any]] =
    _configuration.toVector.flatMap { conf =>
      val componentnames = _config_component_names(component)
      val candidates = (
        base.map(_.entityName) ++ _entity_collection_names(component)
      ).distinct
      candidates.flatMap { entityname =>
        componentnames.iterator.flatMap(name => _config_working_set_policy(conf, name, entityname)).toSeq.headOption.map { policy =>
          val template = base.find(p => _normalize_entity_name(p.entityName) == _normalize_entity_name(entityname))
            .getOrElse(_legacy_memory_plan(entityname))
          template.copy(
            workingSetPolicy = Some(policy),
            workingSetPolicySource = Some(WorkingSetPolicySource.Config)
          )
        }
      }
    }

  private def _config_component_names(
    component: Component
  ): Vector[String] =
    Vector(
      Some(component.name),
      component.coreOption.map(_.name)
    ).flatten ++
      component.componentDescriptors.flatMap(d => Vector(d.name, d.componentName).flatten)
        .distinct

  private def _config_working_set_policy(
    conf: ResolvedConfiguration,
    componentname: String,
    entityname: String
  ): Option[WorkingSetPolicy] = {
    val component = NamingConventions.toNormalizedSegment(componentname)
    val entity = NamingConventions.toNormalizedSegment(entityname)
    val prefixes = Vector(
      s"cncf.entity.$component.$entity.working-set-policy",
      s"cncf.entity.$component.$entity.working_set_policy",
      s"cncf.entity.$entity.working-set-policy",
      s"cncf.entity.$entity.working_set_policy"
    )
    val kind = prefixes.iterator.flatMap(prefix =>
      Vector(
        ConfigurationAccess.getString(conf, s"$prefix.kind")
      )
    ).collectFirst { case Some(value) => value }
    val duration = prefixes.iterator.flatMap(prefix =>
      Vector(
        ConfigurationAccess.getString(conf, s"$prefix.duration"),
        ConfigurationAccess.getString(conf, s"$prefix.window")
      )
    ).collectFirst { case Some(value) => value }
    val timestampfield = prefixes.iterator.flatMap(prefix =>
      Vector(
        ConfigurationAccess.getString(conf, s"$prefix.timestamp-field"),
        ConfigurationAccess.getString(conf, s"$prefix.timestampField"),
        ConfigurationAccess.getString(conf, s"$prefix.timestamp_field")
      )
    ).collectFirst { case Some(value) => value }
    kind.flatMap(value => WorkingSetPolicy.parse(value, duration, timestampfield).toOption)
  }

  private[component] def _normalize_entity_name(
    name: String
  ): String =
    NamingConventions.toNormalizedSegment(name)

}
