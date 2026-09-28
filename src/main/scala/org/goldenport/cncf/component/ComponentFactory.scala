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
 * @since   Jan. 30, 2026
 *  version Jan. 31, 2026
 *  version Feb.  5, 2026
 *  version Mar. 31, 2026
 *  version Apr. 25, 2026
 *  version Apr. 26, 2026
 *  version May.  7, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactory(
  private val _component_repository_space: ComponentRepositorySpace = ComponentRepositorySpace(),
  private val _collaborators: CollaboratorFactory = CollaboratorFactory.empty,
  private[component] val _runtime_entity_descriptors: Vector[EntityRuntimeDescriptor] = Vector.empty,
  private[component] val _configuration: Option[ResolvedConfiguration] = None,
  workingsetclock: java.time.Clock = RuntimeConfig.DEFAULT_EXECUTION_CLOCK.clock,
  private[component] val _runtime_component_descriptors: Vector[ComponentDescriptor] = Vector.empty
)  extends ComponentFactoryEntityPlanPart with ComponentFactoryDescriptorPart with ComponentFactoryWorkflowPart with ComponentFactoryStateMachinePart with ComponentFactoryEntityBootstrapPart with ComponentFactoryReflectionPart {
  private[component] val _working_set_clock = workingsetclock

  def discover(): Vector[Component] =
    _or_raise(discoverC())

  def discoverC(): Consequence[Vector[Component]] = {
    given ExecutionContext = ExecutionContext.create()
    _component_repository_space.discoverC().flatMap { cs =>
      _sequence(cs.map(bootstrapC)).flatMap(SpiResolver.resolve(_))
    }
  }

  def bootstrap(component: Component): Component =
    _or_raise(bootstrapC(component))

  /** Bind an application-owned external result adapter to this Component's injected runtime. */
  def bindContinuationSpiAdapterC[W, S, R](
    component: Component,
    issuedPersistence: IssuedWorkOrderPersistence[W],
    adapter: ContinuationSpiAdapter[W, S, R]
  ): Consequence[ContinuationSpiAdapter.Bound[W, S, R]] =
    if (component == null || component.coreOption.isEmpty)
      Consequence.configurationInvalid("Continuation SPI adapter requires a Component")
    else ContinuationSpiAdapter.bindC(
      component.componentId, component.continuationRuntime, issuedPersistence, adapter
    )

  def bootstrapC(component: Component): Consequence[Component] =
    if (component.collectionsBootstrapped) {
      Consequence.success(component)
    } else {
      _initialize_special_component_c(component).flatMap { initialized =>
        try {
          _bootstrap_collections_c(initialized)
        } catch {
          case scala.util.control.NonFatal(e) => Consequence.componentInvalid(e)
        }
      }
    }

  private def _initialize_special_component_c(p: Component): Consequence[Component] =
    p match {
      case m: CollaboratorComponent =>
        val entryopt = _collaborators.resolve(m.core.name).orElse(_collaborators.entries.headOption)
        entryopt match {
          case Some(entry) =>
            val collaboratorimpl = _wrap_collaborator(entry.collaborator)
            val init = CollaboratorComponentInit(
              CollaboratorComponent.Core(collaboratorimpl),
              m.initializationParameters
            )
            try {
              Consequence.success(m.initialize(init))
            } catch {
              case scala.util.control.NonFatal(e) => Consequence.componentInvalid(e)
            }
          case None =>
            Consequence.success(m)
        }
      case m => Consequence.success(m)
    }

  private def _sequence[A](values: Vector[Consequence[A]]): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (acc, value) =>
      acc.flatMap(xs => value.map(xs :+ _))
    }

  private def _or_raise[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
    }

  private def _wrap_collaborator(apicollaborator: api.Collaborator): Collaborator = new Collaborator {
    private val _delegate = Collaborator.Instance(Collaborator.Core(apicollaborator))
    def execute(ctx: org.goldenport.cncf.context.ExecutionContext, request: org.goldenport.protocol.Request) =
      _delegate.execute(ctx, request)
  }

  private[component] lazy val _entity_persistent_any: EntityPersistent[Any] =
    new EntityPersistent[Any] {
      def id(e: Any): EntityId =
        _entity_id_of_any(e)

      def toRecord(e: Any): Record =
        e match {
          case m: EntityPersistable => m.toRecord()
          case _ => Record.empty
        }

      def fromRecord(r: Record): Consequence[Any] =
        Consequence.notImplemented("EntityPersistent[Any].fromRecord is not wired in bootstrap placeholder")
    }

  private[component] val _query_field_regex = "query\\.([A-Za-z0-9_]+)".r

  private[component] def _default_view_browser(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    collection: ViewCollection[Any]
  ): Browser[Any] = {
    val queryfn = new ContextualBrowserQuery[Any] {
      def query_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
        given ExecutionContext = _with_internal_materialization_read(ctx)
        _search_view_source_entities(component, entityspace, entityname, _sanitize_query(q)).flatMap {
          _.foldLeft(Consequence.success(Vector.empty[Any])) { (z, entity) =>
            z.flatMap(xs => _entity_to_view(component, entityname, entity).map(xs :+ _))
          }
        }
      }
    }
    val countfn = new ContextualBrowserCount {
      def count_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Int] = {
        given ExecutionContext = _with_internal_materialization_read(ctx)
        _count_view_source_entities(component, entityspace, entityname, _sanitize_count_query(q))
      }
    }
    Browser.from(
      collection,
      queryfn,
      Some(countfn),
      TotalCountCapability.Unsupported,
      Some(ctx => _entity_total_count_capability(component, entityname)(using ctx))
    )
  }

  private[component] def _default_named_view_browser(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    viewname: String,
    collection: ViewCollection[Any]
  ): Browser[Any] = {
    val loadfn = new ContextualBrowserFind[Any] {
      def find_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[Any] = {
        given ExecutionContext = _with_internal_materialization_read(ctx)
        _load_view_source_entity(component, entityspace, entityname, id).flatMap(_entity_to_view(component, entityname, Some(viewname), _))
      }
    }
    val queryfn = new ContextualBrowserQuery[Any] {
      def query_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
        given ExecutionContext = _with_internal_materialization_read(ctx)
        _search_view_source_entities(component, entityspace, entityname, _sanitize_query(q)).flatMap {
          _.foldLeft(Consequence.success(Vector.empty[Any])) { (z, entity) =>
            z.flatMap(xs => _entity_to_view(component, entityname, Some(viewname), entity).map(xs :+ _))
          }
        }
      }
    }
    val countfn = new ContextualBrowserCount {
      def count_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Int] = {
        given ExecutionContext = _with_internal_materialization_read(ctx)
        _count_view_source_entities(component, entityspace, entityname, _sanitize_count_query(q))
      }
    }
    Browser.from(
      loadfn,
      collection,
      queryfn,
      Some(countfn),
      TotalCountCapability.Unsupported,
      Some(ctx => _entity_total_count_capability(component, entityname)(using ctx))
    )
  }

  private[component] def _default_view_query_browser(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    collection: ViewCollection[Any],
    querydef: org.goldenport.cncf.entity.view.ViewQueryDefinition
  ): Browser[Any] = {
    val queryfn = new ContextualBrowserQuery[Any] {
      def query_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
        given ExecutionContext = _with_internal_materialization_read(ctx)
        val source = _sanitize_query_record(_query_record(q))
        val filtered = _filter_view_query_record(source, querydef)
        // Named-view predicates are evaluated after decoding generated entities so
        // DATATYPE query values retain their domain semantics. Pushing them into
        // the datastore would compare wire scalars with generated value objects.
        val searchrecord = _searchable_query_record(source)
        _search_view_source_entities(component, entityspace, entityname, _with_query_controls(searchrecord, q)).flatMap { entities =>
          val matched =
            if (filtered.asMap.isEmpty) entities
            else entities.filter(entity => _matches_view_query(entity, filtered))
          matched.foldLeft(Consequence.success(Vector.empty[Any])) { (z, entity) =>
            z.flatMap(xs => _entity_to_view(component, entityname, entity).map(xs :+ _))
          }
        }
      }
    }
    val countfn = new ContextualBrowserCount {
      def count_with_context(q: Query[_])(using ctx: ExecutionContext): Consequence[Int] = {
        given ExecutionContext = _with_internal_materialization_read(ctx)
        val source = _sanitize_query_record(_query_record(q))
        val filtered = _filter_view_query_record(source, querydef)
        val searchrecord = _searchable_query_record(source)
        _count_view_source_entities(component, entityspace, entityname, _with_count_controls(searchrecord, q))
      }
    }
    Browser.from(
      collection,
      queryfn,
      Some(countfn),
      TotalCountCapability.Unsupported,
      Some(ctx => _entity_total_count_capability(component, entityname)(using ctx))
    )
  }

  private def _search_view_source_entities(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    q: Query[?]
  )(using ctx: ExecutionContext): Consequence[Vector[Any]] = {
    val cid = _bootstrap_collection_id(component, entityname)
    val query = EntityQuery[Any](cid, q)
    given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
    EntityStore.standard().search[Any](query).map(_.data).recoverWith { case _ =>
      entityspace.entityOption[Any](entityname)
        .map(_.search(query).map(_.data))
        .getOrElse(Consequence.success(Vector.empty))
    }
  }

  private def _count_view_source_entities(
    component: Component,
    entityspace: EntitySpace,
    entityname: String,
    q: Query[?]
  )(using ctx: ExecutionContext): Consequence[Int] =
    {
      val cid = _bootstrap_collection_id(component, entityname)
      val query = EntityQuery[Any](cid, _with_count_controls(_sanitize_query_record(_query_record(q)), q))
      given EntityPersistent[Any] = _bootstrap_entity_persistent(component, entityname)
      EntityStore.standard().search[Any](query).flatMap(_total_count_from_result).recoverWith { case _ =>
        _count_entities(component, entityspace, entityname, q)
      }
    }

}

object ComponentFactory {
  // NEW
  def create(
    subsystem: Subsystem,
    collaborators: CollaboratorFactory,
    cwd: Path,
    c: ResolvedConfiguration
  ): ComponentFactory = {
    val componentdescriptors = _resolve_component_descriptors(cwd, c)
    val space = _build_component_repository_space(subsystem, cwd, c, componentdescriptors)
    val descriptors = componentdescriptors.flatMap(_.entityRuntimeDescriptors)
    new ComponentFactory(
      space,
      collaborators,
      descriptors,
      Some(c),
      subsystem.globalRuntimeContext.executionProfileRuntime.runtimeClock.clock,
      componentdescriptors
    )
  }

  private def _build_component_repository_space(
    subsystem: Subsystem,
    cwd: Path,
    c: ResolvedConfiguration,
    componentdescriptors: Vector[ComponentDescriptor]
  ): ComponentRepositorySpace =
    ComponentRepositorySpace.create(subsystem, cwd, c, componentdescriptors)

  // CncfRuntime
  def create(
    subsystem: Subsystem,
    c: ResolvedConfiguration,
    collaborators: CollaboratorFactory,
    repositorySpecs: Vector[ComponentRepository.Specification]
  ): ComponentFactory = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val componentdescriptors = _resolve_component_descriptors(cwd, c, repositorySpecs)
    val space = ComponentRepositorySpace.create(subsystem, c, repositorySpecs, componentdescriptors)
    val descriptors = componentdescriptors.flatMap(_.entityRuntimeDescriptors)
    new ComponentFactory(
      space,
      collaborators,
      descriptors,
      Some(c),
      subsystem.globalRuntimeContext.executionProfileRuntime.runtimeClock.clock,
      componentdescriptors
    )
  }

  private val _component_descriptor_key = "cncf.component.descriptor"
  private val _component_descriptor_dir_key = "cncf.component.descriptor.dir"
  private def _resolve_entity_runtime_descriptors(
    cwd: Path,
    c: ResolvedConfiguration
  ): Vector[EntityRuntimeDescriptor] =
    _resolve_component_descriptors(cwd, c).flatMap(_.entityRuntimeDescriptors)

  private def _resolve_component_descriptors(
    cwd: Path,
    c: ResolvedConfiguration
  ): Vector[ComponentDescriptor] =
    _resolve_component_descriptors(cwd, c, Vector.empty)

  private def _resolve_component_descriptors(
    cwd: Path,
    c: ResolvedConfiguration,
    repositoryspecs: Vector[ComponentRepository.Specification]
  ): Vector[ComponentDescriptor] = {
    val explicit =
      _split_paths(ConfigurationAccess.getString(c, _component_descriptor_key)) ++
      _split_paths(ConfigurationAccess.getString(c, _component_descriptor_dir_key))
    explicit.map(_normalize_path(cwd, _)).distinct.flatMap { path =>
      ComponentDescriptorLoader.load(path) match {
        case Consequence.Success(xs) => xs
        case Consequence.Failure(_) => Vector.empty
      }
    }
  }

  private def _split_paths(value: Option[String]): Vector[String] =
    value.toVector.flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty))

  private def _normalize_path(cwd: Path, raw: String): Path = {
    val path = Paths.get(raw)
    if (path.isAbsolute) path.normalize else cwd.resolve(path).normalize
  }

  def build(
    classNames: Seq[String],
    loader: ClassLoader,
    origin: String
  ): Consequence[Vector[ComponentSource]] = {
    val sources = Vector.newBuilder[ComponentSource]
    var error: Option[Throwable] = None
    classNames.foreach { classname =>
      if (error.isEmpty) {
        try {
          val cls = Class.forName(classname, false, loader).asSubclass(classOf[Component])
          sources += ComponentSource.ClassDef(cls, origin)
        } catch {
          case e: Throwable =>
            error = Some(e)
        }
      }
    }
    error match {
      case Some(e) => Consequence.componentInvalid(e)
      case None => Consequence.success(sources.result())
    }
  }
}
