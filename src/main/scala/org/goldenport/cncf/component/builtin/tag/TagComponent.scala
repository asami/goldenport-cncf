package org.goldenport.cncf.component.builtin.tag

import java.time.Instant
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.cncf.entity.runtime.{EntityKind, EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.security.{AdminAuthorizationPolicy, OperationAuthorizationProvider, OperationAuthorizationRule}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.tag.{TagCreate, TagEntityCollections, TagRepository, TagSpace, TagUsageKind, TaggingWorkflow}
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.{DataType, XString}
import org.simplemodeling.model.datatype.EntityId

/*
 * Built-in Tag management and Entity-to-Tag workflow component.
 *
 * @since   May.  5, 2026
 * @version May.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class TagComponent() extends Component {
  override def componentDescriptors: Vector[org.goldenport.cncf.component.ComponentDescriptor] =
    super.componentDescriptors ++ TagComponent.componentDescriptors
}

object TagComponent {
  val name: String = "tag"
  val componentId: ComponentId = ComponentId(name)

  def componentDescriptors: Vector[org.goldenport.cncf.component.ComponentDescriptor] =
    Vector(org.goldenport.cncf.component.ComponentDescriptor(
      componentName = Some(name),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "tag",
          collectionId = TagEntityCollections.Tag,
          memoryPolicy = EntityMemoryPolicy.StoreOnly,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 1,
          maxEntitiesPerPartition = 10000,
          entityKind = EntityKind.Master,
          entityKindExplicit = true,
          workingSetPolicy = Some(WorkingSetPolicy.Disabled),
          workingSetPolicySource = Some(WorkingSetPolicySource.Code)
        )
      )
    ))

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      TagComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val response = spec.ResponseDefinition(result = List(DataType.Named("Record")))
      val operations = NonEmptyVector.of(
        new SimpleOperationDefinition("tag_tree", request, response, TreeAction.apply),
        new SimpleOperationDefinition("tag_create", request, response, CreateAction.apply),
        new SimpleOperationDefinition("tag_attach", request, response, AttachAction.apply),
        new SimpleOperationDefinition("tag_detach", request, response, DetachAction.apply),
        new SimpleOperationDefinition("tag_list_entity_tags", request, response, ListEntityTagsAction.apply),
        new SimpleOperationDefinition("tag_search_entities", request, response, SearchEntitiesAction.apply)
      )
      val service = spec.ServiceDefinition(
        name = "tag",
        operations = spec.OperationDefinitionGroup(operations = operations)
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(services = Vector(service)),
        handler = ProtocolHandler.default
      )
      Component.Core.create(
        name,
        componentId,
        ComponentInstanceId.default(componentId),
        protocol
      )
    }
  }

  private trait TagOperationAuthorization extends OperationAuthorizationProvider {
    def operationAuthorization(runtimeConfig: RuntimeConfig): OperationAuthorizationRule =
      AdminAuthorizationPolicy.operationRule("admin.entity", runtimeConfig)
  }

  private final class SimpleOperationDefinition(
    operationName: String,
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    build: Request => OperationRequest
  ) extends spec.OperationDefinition with TagOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = operationName, request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(build(req))
  }

  private final case class TreeAction(request: Request) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = TreeActionCall(core)
  }

  private final case class CreateAction(request: Request) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = CreateActionCall(core)
  }

  private final case class AttachAction(request: Request) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = AttachActionCall(core)
  }

  private final case class DetachAction(request: Request) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = DetachActionCall(core)
  }

  private final case class ListEntityTagsAction(request: Request) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = ListEntityTagsActionCall(core)
  }

  private final case class SearchEntitiesAction(request: Request) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall = SearchEntitiesActionCall(core)
  }

  private final case class TreeActionCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      val args = _action_values(core)
      _optional_string(args, "tagSpace", "tag_space") match {
        case Some(space) => TagRepository.entityStore().tree(space).map(tree => OperationResponse.RecordResponse(tree.toRecord))
        case None => TagRepository.entityStore().tree().map(tree => OperationResponse.RecordResponse(tree.toRecord))
      }
    }
  }

  private final case class CreateActionCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      val args = _action_values(core)
      val parent = _optional_entity_id(args, "parentTagId", "parent_tag_id")
      for {
        key <- _required_string(args, "key")
        usage <- _optional_string(args, "usageKind", "usage_kind").map(TagUsageKind.parse).getOrElse(Consequence.success(TagUsageKind.General))
        tag <- TagRepository.entityStore().create(TagCreate(
          id = _optional_entity_id(args, "id"),
          tagSpace = _optional_string(args, "tagSpace", "tag_space").map(TagSpace.normalize).getOrElse(TagRepository.DefaultTagSpace),
          key = key,
          parentTagId = parent,
          usageKind = usage,
          sortOrder = _optional_int(args, "sortOrder", "sort_order"),
          title = _optional_string(args, "title"),
          description = _optional_string(args, "description")
        ))
      } yield OperationResponse.RecordResponse(tag.toRecord)
    }
  }

  private final case class AttachActionCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      val args = _action_values(core)
      for {
        source <- _required_string(args, "sourceEntityId", "source_entity_id")
        tag <- _required_string(args, "tagRef", "tag", "tagPath", "tag_path")
        role = _optional_string(args, "role").getOrElse("tag")
        workflow = TaggingWorkflow(tagSpace = _optional_string(args, "tagSpace", "tag_space").map(TagSpace.normalize).getOrElse(TagRepository.DefaultTagSpace))
        association <- workflow.attach(source, tag, role, _optional_int(args, "sortOrder", "sort_order"))
      } yield OperationResponse.RecordResponse(org.goldenport.cncf.association.AssociationRecordCodec.toRecord(association))
    }
  }

  private final case class DetachActionCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      val args = _action_values(core)
      for {
        source <- _required_string(args, "sourceEntityId", "source_entity_id")
        tag <- _required_string(args, "tagRef", "tag", "tagPath", "tag_path")
        workflow = TaggingWorkflow(tagSpace = _optional_string(args, "tagSpace", "tag_space").map(TagSpace.normalize).getOrElse(TagRepository.DefaultTagSpace))
        count <- workflow.detach(source, tag, _optional_string(args, "role"))
      } yield OperationResponse.RecordResponse(Record.dataAuto("detachedCount" -> count))
    }
  }

  private final case class ListEntityTagsActionCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      val args = _action_values(core)
      for {
        source <- _required_string(args, "sourceEntityId", "source_entity_id")
        workflow = TaggingWorkflow(tagSpace = _optional_string(args, "tagSpace", "tag_space").map(TagSpace.normalize).getOrElse(TagRepository.DefaultTagSpace))
        summary <- workflow.listEntityTags(source, _optional_string(args, "role"))
      } yield OperationResponse.RecordResponse(summary.toRecord)
    }
  }

  private final case class SearchEntitiesActionCall(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      val args = _action_values(core)
      for {
        subsystem <- Consequence.fromOption(core.component.flatMap(_.subsystem), "subsystem is not available")
        componentName <- _required_string(args, "component")
        entityName <- _required_string(args, "entity", "entityName", "entity_name")
        component <- Consequence.fromOption(_component_by_name(subsystem, componentName), s"Component not found: $componentName")
        collection <- Consequence.fromOption(_entity_collection(component, entityName), s"Entity collection not found: $entityName")
        tag <- _required_string(args, "tagRef", "tag", "tagPath", "tag_path")
        include = _optional_boolean(args, "includeDescendants", "include_descendants").getOrElse(true)
        workflow = TaggingWorkflow(tagSpace = _optional_string(args, "tagSpace", "tag_space").map(TagSpace.normalize).getOrElse(TagRepository.DefaultTagSpace))
        ids <- workflow.searchSourceIds(tag, include, _optional_string(args, "role"))
        records <- _visible_entity_records(workflow, collection, ids.toVector.sorted)
      } yield OperationResponse.RecordResponse(Record.dataAuto(
        "component" -> componentName,
        "entity" -> entityName,
        "sourceIds" -> ids.toVector.sorted,
        "ids" -> records.flatMap(_.getString("id")),
        "data" -> records
      ))
    }
  }

  private def _visible_entity_records[A](
    workflow: TaggingWorkflow,
    collection: EntityCollection[A],
    ids: Vector[String]
  )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Vector[Record]] =
    if (collection.descriptor.collectionId.name == TagEntityCollections.Tag.name)
      _visible_tag_records(workflow, ids)
    else {
      given org.goldenport.cncf.entity.EntityPersistent[A] = collection.descriptor.persistent
      ids.foldLeft(Consequence.success(Vector.empty[Record])) { (z, sourceId) =>
        z.flatMap { xs =>
          _source_entity_id(sourceId, collection).flatMap {
            case Some(id) =>
              EntityStore.standard().load[A](id).map {
                case Some(entity) => xs :+ (collection.descriptor.persistent.toRecord(entity) ++ Record.dataAuto("id" -> id.value))
                case None => xs
              }
            case None =>
              Consequence.success(xs)
          }
        }
      }
    }

  private def _visible_tag_records(
    workflow: TaggingWorkflow,
    ids: Vector[String]
  )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Vector[Record]] =
    ids.foldLeft(Consequence.success(Vector.empty[Record])) { (z, sourceId) =>
      z.flatMap(xs => workflow.resolveTagOption(sourceId).map {
        case Some(tag) => xs :+ tag.toRecord
        case None => xs
      })
    }

  private def _source_entity_id[A](
    sourceId: String,
    collection: EntityCollection[A]
  ): Consequence[Option[EntityId]] =
    EntityId.parse(sourceId) match {
      case Consequence.Success(id) =>
        Consequence.success(Some(id.copy(collection = collection.descriptor.collectionId)))
      case Consequence.Failure(_) =>
        _parse_entity_id_value(sourceId, collection.descriptor.collectionId)
    }

  private def _parse_entity_id_value(
    sourceId: String,
    collection: org.simplemodeling.model.datatype.EntityCollectionId
  ): Consequence[Option[EntityId]] = {
    val parts = sourceId.split("-", 6)
    if (parts.length == 6 && parts(2) == "entity")
      scala.util.Try(parts(4).toLong).toOption match {
        case Some(millis) =>
          Consequence.success(Some(EntityId(
            major = parts(0),
            minor = parts(1),
            collection = collection,
            timestamp = Some(Instant.ofEpochMilli(millis)),
            entropy = Some(parts(5))
          )))
        case None =>
          Consequence.success(None)
      }
    else
      Consequence.success(None)
  }

  private def _component_by_name(
    subsystem: Subsystem,
    name: String
  ): Option[Component] =
    subsystem.findComponent(name)

  private def _entity_collection(
    component: Component,
    entity: String
  ): Option[EntityCollection[?]] =
    component.entitySpace.entityOption[Any](entity).orElse {
      component.componentDescriptors
        .flatMap(_.entityRuntimeDescriptors)
        .find(x => org.goldenport.cncf.naming.NamingConventions.equivalentByNormalized(x.entityName, entity))
        .flatMap(x => component.entitySpace.entityOption(x.collectionId))
    }

  private def _action_values(core: ActionCall.Core): Map[String, Any] =
    (core.action.properties.map(x => x.name -> x.value) ++
      core.action.arguments.map(x => x.name -> x.value)).toMap

  private def _required_string(args: Map[String, Any], keys: String*): Consequence[String] =
    keys.iterator.flatMap(k => args.get(k).map(_.toString.trim).filter(_.nonEmpty)).toSeq.headOption match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentMissing(keys.headOption.getOrElse("value"))
    }

  private def _optional_string(args: Map[String, Any], keys: String*): Option[String] =
    keys.iterator.flatMap(k => args.get(k).map(_.toString.trim).filter(_.nonEmpty)).toSeq.headOption

  private def _optional_int(args: Map[String, Any], keys: String*): Option[Int] =
    keys.iterator.flatMap(k => args.get(k)).flatMap {
      case n: java.lang.Number => Some(n.intValue)
      case s: String => scala.util.Try(s.trim.toInt).toOption
      case other => scala.util.Try(other.toString.trim.toInt).toOption
    }.toSeq.headOption

  private def _optional_boolean(args: Map[String, Any], keys: String*): Option[Boolean] =
    keys.iterator.flatMap(k => args.get(k)).flatMap {
      case b: java.lang.Boolean => Some(b.booleanValue)
      case s: String => scala.util.Try(s.trim.toBoolean).toOption
      case other => scala.util.Try(other.toString.trim.toBoolean).toOption
    }.toSeq.headOption

  private def _optional_entity_id(args: Map[String, Any], keys: String*): Option[EntityId] =
    keys.iterator.flatMap(k => args.get(k)).flatMap {
      case id: EntityId => Some(id)
      case s: String => EntityId.parse(s).toOption
      case other => EntityId.parse(other.toString).toOption
    }.toSeq.headOption
}
