package org.goldenport.cncf.workflow

import java.time.Instant
import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.{Component, ComponentLocator, ComponentLogic}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.ReceptionDomainEvent
import org.goldenport.cncf.job.{ActionId, ActionTask, JobId, JobPersistencePolicy, JobRunMode, JobSubmitOption}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class WorkflowDefinitionId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "workflow_def", timestamp, entropy)

object WorkflowDefinitionId {
  def generate(): WorkflowDefinitionId =
    WorkflowDefinitionId("cncf", "workflow_def")

  def parse(s: String): Consequence[WorkflowDefinitionId] =
    UniversalId.parseParts(s, "workflow_def").map(parts =>
      WorkflowDefinitionId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy))
    )
}

final case class WorkflowPriority(value: Int) extends AnyVal

final case class WorkflowStatusRule(
  currentStatus: String,
  nextAction: String,
  note: Option[String] = None
)

final case class WorkflowRegistration(
  name: String,
  eventName: String,
  entityCollection: String,
  entityIdKey: String,
  statusField: String,
  statusRules: Vector[WorkflowStatusRule],
  priority: WorkflowPriority = WorkflowPriority(0)
)

final case class WorkflowDefinition(
  id: WorkflowDefinitionId = WorkflowDefinitionId.generate(),
  name: String,
  registrations: Vector[WorkflowRegistration]
)

enum WorkflowStatus {
  case Active
  case NoProgress
  case Submitted
  case Failed
}

final case class WorkflowInstanceId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "workflow_inst", timestamp, entropy)

object WorkflowInstanceId {
  def generate(): WorkflowInstanceId =
    WorkflowInstanceId("cncf", "workflow_inst")

  def parse(s: String): Consequence[WorkflowInstanceId] =
    UniversalId.parseParts(s, "workflow_inst").map(parts =>
      WorkflowInstanceId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy))
    )
}

final case class WorkflowHistoryEntry(
  occurredAt: Instant = Instant.now(),
  status: WorkflowStatus,
  message: String,
  entityStatus: Option[String] = None,
  selectedAction: Option[String] = None,
  relatedJobId: Option[JobId] = None
)

final case class WorkflowInstance(
  id: WorkflowInstanceId,
  registrationName: String,
  entityCollection: String,
  entityId: String,
  status: WorkflowStatus,
  currentEntityStatus: Option[String],
  triggeringEventName: String,
  startedAt: Instant,
  updatedAt: Instant,
  lastAction: Option[String],
  relatedJobIds: Vector[JobId],
  history: Vector[WorkflowHistoryEntry]
)

final case class WorkflowDecision(
  registrationName: Option[String],
  entityCollection: Option[String],
  entityId: Option[String],
  currentEntityStatus: Option[String],
  selectedAction: Option[String],
  relatedJobId: Option[JobId],
  progressed: Boolean,
  reason: Option[String],
  instanceId: Option[WorkflowInstanceId]
)

final case class WorkflowEntrypoint(
  component: Component,
  definition: WorkflowDefinition,
  registration: WorkflowRegistration
)

trait WorkflowEngine {
  def register(component: Component, definitions: Vector[WorkflowDefinition]): Unit
  def handle(componentName: String, event: ReceptionDomainEvent)(using ExecutionContext): Consequence[WorkflowDecision]
  def definitions: Vector[WorkflowDefinition]
  def registrations: Vector[WorkflowRegistration]
  def findDefinition(nameOrId: String): Option[WorkflowDefinition]
  def findRegistration(definitionNameOrId: String, registrationName: String): Option[WorkflowRegistration]
  def findEntrypoint(definitionNameOrId: String, registrationName: String): Option[WorkflowEntrypoint]
  def findInstance(id: WorkflowInstanceId): Option[WorkflowInstance]
  def history(id: WorkflowInstanceId): Vector[WorkflowHistoryEntry]
  def instances: Vector[WorkflowInstance]
}

object WorkflowEngine {
  def inMemory(subsystem: => Subsystem): WorkflowEngine =
    new InMemoryWorkflowEngine(() => subsystem)

  private final class InMemoryWorkflowEngine(
    subsystem: () => Subsystem
  ) extends WorkflowEngine {
    private final case class _Entry(
      definition: WorkflowDefinition,
      registration: WorkflowRegistration,
      component: Component,
      order: Long
    )

    private val _entries = mutable.ArrayBuffer.empty[_Entry]
    private val _instances = mutable.LinkedHashMap.empty[(String, String, String), WorkflowInstance]
    private var _registration_order = 0L

    def register(component: Component, definitions: Vector[WorkflowDefinition]): Unit = synchronized {
      definitions.foreach { definition =>
        definition.registrations.foreach { registration =>
          _validate_registration(component, definition, registration)
          _registration_order = _registration_order + 1
          _entries += _Entry(definition, registration, component, _registration_order)
        }
      }
    }

    def handle(componentName: String, event: ReceptionDomainEvent)(using ctx: ExecutionContext): Consequence[WorkflowDecision] = {
      val collectionopt = _event_entity_collection(event)
      val candidates = _resolve_candidates(componentName, event.name, collectionopt)
      candidates match {
        case Vector() =>
          Consequence.success(
            WorkflowDecision(
              registrationName = None,
              entityCollection = collectionopt,
              entityId = None,
              currentEntityStatus = None,
              selectedAction = None,
              relatedJobId = None,
              progressed = false,
              reason = Some("no-workflow-match"),
              instanceId = None
            )
          )
        case Vector(entry) =>
          _handle_entry(entry, event)
        case xs =>
          val sorted = xs.sortBy(x => (x.registration.priority.value, x.order))
          _handle_entry(sorted.head, event)
      }
    }

    def instances: Vector[WorkflowInstance] = synchronized {
      _instances.values.toVector.sortBy(_.startedAt)(Ordering[java.time.Instant].reverse)
    }

    def definitions: Vector[WorkflowDefinition] = synchronized {
      _entries.iterator.foldLeft(Vector.empty[WorkflowDefinition]) { (z, entry) =>
        if (z.exists(_.id == entry.definition.id))
          z
        else
          z :+ entry.definition
      }
    }

    def registrations: Vector[WorkflowRegistration] = synchronized {
      definitions.flatMap(_.registrations)
    }

    def findDefinition(nameOrId: String): Option[WorkflowDefinition] = synchronized {
      val key = Option(nameOrId).getOrElse("").trim
      definitions.find(x => x.id.value == key || x.name == key)
    }

    def findRegistration(
      definitionNameOrId: String,
      registrationName: String
    ): Option[WorkflowRegistration] =
      findDefinition(definitionNameOrId).flatMap(_.registrations.find(_.name == registrationName))

    def findEntrypoint(
      definitionNameOrId: String,
      registrationName: String
    ): Option[WorkflowEntrypoint] = synchronized {
      val definitionopt = findDefinition(definitionNameOrId)
      definitionopt.flatMap { definition =>
        _entries.find { entry =>
          entry.definition.id == definition.id && entry.registration.name == registrationName
        }.map(entry => WorkflowEntrypoint(entry.component, entry.definition, entry.registration))
      }
    }

    def findInstance(id: WorkflowInstanceId): Option[WorkflowInstance] = synchronized {
      _instances.values.find(_.id == id)
    }

    def history(id: WorkflowInstanceId): Vector[WorkflowHistoryEntry] =
      findInstance(id).map(_.history).getOrElse(Vector.empty)

    private def _handle_entry(
      entry: _Entry,
      event: ReceptionDomainEvent
    )(using ctx: ExecutionContext): Consequence[WorkflowDecision] = {
      val entityidopt = _extract_entity_id(entry.registration, event)
      entityidopt match {
        case None =>
          Consequence.success(
            WorkflowDecision(
              registrationName = Some(entry.registration.name),
              entityCollection = Some(entry.registration.entityCollection),
              entityId = None,
              currentEntityStatus = None,
              selectedAction = None,
              relatedJobId = None,
              progressed = false,
              reason = Some("missing-entity-id"),
              instanceId = None
            )
          )
        case Some(entityidtext) =>
          val instance = _instance(entry, entityidtext, event)
          _resolve_entity(entry, entityidtext).flatMap {
            case None =>
              val updated = _record(instance, WorkflowStatus.NoProgress, "entity-unresolved")
              Consequence.success(_decision(updated, reason = Some("entity-unresolved")))
            case Some((resolvedid, entity, statusopt)) =>
              statusopt match {
                case None =>
                  val updated = _record(instance, WorkflowStatus.NoProgress, "missing-status-field", entityStatus = None)
                  Consequence.success(_decision(updated, entityId = Some(resolvedid.value), reason = Some("missing-status-field")))
                case Some(status) =>
                  entry.registration.statusRules.find(_.currentStatus == status) match {
                    case None =>
                      val updated = _record(instance, WorkflowStatus.NoProgress, "status-unmatched", entityStatus = Some(status))
                      Consequence.success(_decision(updated, entityId = Some(resolvedid.value), entityStatus = Some(status), reason = Some("status-unmatched")))
                    case Some(rule) =>
                      _submit_action(entry, event, rule.nextAction).map { case (jobid, resolvedaction) =>
                        val updated = _record(
                          instance,
                          WorkflowStatus.Submitted,
                          "action-submitted",
                          entityStatus = Some(status),
                          selectedAction = Some(resolvedaction),
                          relatedJobId = Some(jobid),
                          entityId = Some(resolvedid.value)
                        )
                        _decision(
                          updated,
                          entityId = Some(resolvedid.value),
                          entityStatus = Some(status),
                          selectedAction = Some(resolvedaction),
                          relatedJobId = Some(jobid),
                          progressed = true,
                          reason = None
                        )
                      }
                  }
              }
          }
      }
    }

    private def _resolve_candidates(
      componentName: String,
      eventName: String,
      collection: Option[String]
    ): Vector[_Entry] = synchronized {
      collection match {
        case Some(entitycollection) =>
          _entries.toVector.filter { entry =>
            entry.component.name == componentName &&
              entry.registration.eventName == eventName &&
              entry.registration.entityCollection == entitycollection
          }
        case None =>
          Vector.empty
      }
    }

    private def _validate_registration(
      component: Component,
      definition: WorkflowDefinition,
      registration: WorkflowRegistration
    ): Unit = {
      if (registration.name.trim.isEmpty)
        throw new IllegalStateException(s"workflow registration name is empty: ${definition.name}")
      if (registration.eventName.trim.isEmpty)
        throw new IllegalStateException(s"workflow registration eventName is empty: ${component.name}.${registration.name}")
      if (registration.entityCollection.trim.isEmpty)
        throw new IllegalStateException(s"workflow registration entityCollection is empty: ${component.name}.${registration.name}")
      if (registration.entityIdKey.trim.isEmpty)
        throw new IllegalStateException(s"workflow registration entityIdKey is empty: ${component.name}.${registration.name}")
      if (registration.statusField.trim.isEmpty)
        throw new IllegalStateException(s"workflow registration statusField is empty: ${component.name}.${registration.name}")
      if (registration.statusRules.isEmpty)
        throw new IllegalStateException(s"workflow registration statusRules is empty: ${component.name}.${registration.name}")
      synchronized {
        _entries.find { entry =>
          entry.component.name == component.name &&
          entry.registration.eventName == registration.eventName &&
          entry.registration.entityCollection == registration.entityCollection &&
          entry.registration.priority == registration.priority
        }.foreach { existing =>
          throw new IllegalStateException(
            s"ambiguous workflow registration: ${component.name}:${registration.eventName}:${registration.entityCollection}:${registration.priority.value} (${existing.registration.name}, ${registration.name})"
          )
        }
      }
    }

    private def _resolve_entity(
      entry: _Entry,
      entityidtext: String
    ): Consequence[Option[(EntityId, Any, Option[String])]] =
      entry.component.entitySpace.entityOption[Any](entry.registration.entityCollection) match {
        case None => Consequence.success(None)
        case Some(collection) =>
          collection.resolveEntityId(entityidtext) match {
            case None => Consequence.success(None)
            case Some(entityid) =>
              collection.resolve(entityid).map { entity =>
                val record = collection.descriptor.persistent.toRecord(entity)
                val status = record.getString(entry.registration.statusField).orElse(record.asMap.get(entry.registration.statusField).map(_.toString))
                Some((entityid, entity, status))
              }.recoverWith(_ => Consequence.success(None))
          }
      }

    private def _submit_action(
      entry: _Entry,
      event: ReceptionDomainEvent,
      actionName: String
    )(using ctx: ExecutionContext): Consequence[(JobId, String)] =
      _resolve_target_action(entry, event, actionName).flatMap { case (component, action, resolvedselector) =>
        val task = ActionTask(ActionId.generate(), action, component.actionEngine, Some(component))
        val option = JobSubmitOption(
          persistence = JobPersistencePolicy.Persistent,
          runMode = JobRunMode.Async,
          requestSummary = Some(s"workflow:${entry.registration.name}"),
          parameters = event.attributes ++ Map(
            "workflow.registration" -> entry.registration.name,
            "workflow.event" -> event.name,
            "workflow.entityCollection" -> entry.registration.entityCollection
          ),
          executionNotes = Vector(
            "workflow-triggered action submission",
            s"workflow registration: ${entry.registration.name}",
            s"workflow action: $resolvedselector"
          )
        )
        subsystem().jobEngine.submit(List(task), ctx, option).map(jobid => (jobid, resolvedselector))
      }

    private def _resolve_target_action(
      entry: _Entry,
      event: ReceptionDomainEvent,
      actionName: String
    ): Consequence[(Component, Action, String)] = {
      val selector = _action_selector(entry.component, actionName)
      subsystem().operationResolver.resolve(selector) match {
        case OperationResolver.ResolutionResult.Resolved(fqn, componentName, serviceName, operationName) =>
          subsystem().findComponent(componentName) match {
            case Some(component) =>
              val request = Request.of(
                component = componentName,
                service = serviceName,
                operation = operationName,
                arguments = _build_arguments(event),
                switches = Nil,
                properties = List(
                  Property("event_name", event.name, None),
                  Property("event_kind", event.kind, None),
                  Property("workflow_registration", entry.registration.name, None)
                )
              )
              component.logic.makeOperationRequest(request).flatMap {
                case action: Action => Consequence.success((component, action, fqn))
                case _: OperationRequest => Consequence.argumentInvalid(s"workflow target is not action: $selector")
              }
            case None =>
              Consequence.operationNotFound(s"workflow component: $componentName")
          }
        case OperationResolver.ResolutionResult.NotFound(_, selector0) =>
          Consequence.operationNotFound(s"workflow action: $selector0")
        case OperationResolver.ResolutionResult.Ambiguous(selector0, candidates) =>
          Consequence.argumentInvalid(s"ambiguous workflow action: $selector0 => ${candidates.mkString(",")}")
        case OperationResolver.ResolutionResult.Invalid(message) =>
          Consequence.argumentInvalid(message)
      }
    }

    private def _build_arguments(
      event: ReceptionDomainEvent
    ): List[Argument] = {
      val params = event.payload.map { case (k, v) => k -> Option(v).fold("")(_.toString) } ++ event.attributes
      params.toVector.sortBy(_._1).map { case (k, v) => Argument(k, v) }.toList
    }

    private def _action_selector(
      component: Component,
      actionName: String
    ): String =
      actionName.split("\\.").toVector.filter(_.nonEmpty) match {
        case Vector(service, operation) =>
          s"${component.name}.$service.$operation"
        case Vector(_component, _service, _operation) =>
          actionName
        case _ =>
          actionName
      }

    private def _instance(
      entry: _Entry,
      entityidtext: String,
      event: ReceptionDomainEvent
    ): WorkflowInstance = synchronized {
      val key = (entry.registration.name, entry.registration.entityCollection, entityidtext)
      _instances.getOrElseUpdate(
        key,
        WorkflowInstance(
          id = WorkflowInstanceId.generate(),
          registrationName = entry.registration.name,
          entityCollection = entry.registration.entityCollection,
          entityId = entityidtext,
          status = WorkflowStatus.Active,
          currentEntityStatus = None,
          triggeringEventName = event.name,
          startedAt = Instant.now(),
          updatedAt = Instant.now(),
          lastAction = None,
          relatedJobIds = Vector.empty,
          history = Vector(
            WorkflowHistoryEntry(
              status = WorkflowStatus.Active,
              message = "instance-created"
            )
          )
        )
      )
    }

    private def _record(
      instance: WorkflowInstance,
      status: WorkflowStatus,
      message: String,
      entityStatus: Option[String] = None,
      selectedAction: Option[String] = None,
      relatedJobId: Option[JobId] = None,
      entityId: Option[String] = None
    ): WorkflowInstance = synchronized {
      val now = Instant.now()
      val updated = instance.copy(
        entityId = entityId.getOrElse(instance.entityId),
        status = status,
        currentEntityStatus = entityStatus.orElse(instance.currentEntityStatus),
        updatedAt = now,
        lastAction = selectedAction.orElse(instance.lastAction),
        relatedJobIds = relatedJobId.fold(instance.relatedJobIds)(jobid => instance.relatedJobIds :+ jobid),
        history = instance.history :+ WorkflowHistoryEntry(
          occurredAt = now,
          status = status,
          message = message,
          entityStatus = entityStatus,
          selectedAction = selectedAction,
          relatedJobId = relatedJobId
        )
      )
      _instances.update((updated.registrationName, updated.entityCollection, updated.entityId), updated)
      updated
    }

    private def _decision(
      instance: WorkflowInstance,
      entityId: Option[String] = None,
      entityStatus: Option[String] = None,
      selectedAction: Option[String] = None,
      relatedJobId: Option[JobId] = None,
      progressed: Boolean = false,
      reason: Option[String] = None
    ): WorkflowDecision =
      WorkflowDecision(
        registrationName = Some(instance.registrationName),
        entityCollection = Some(instance.entityCollection),
        entityId = entityId.orElse(Some(instance.entityId)),
        currentEntityStatus = entityStatus.orElse(instance.currentEntityStatus),
        selectedAction = selectedAction.orElse(instance.lastAction),
        relatedJobId = relatedJobId.orElse(instance.relatedJobIds.lastOption),
        progressed = progressed,
        reason = reason,
        instanceId = Some(instance.id)
      )

    private def _event_entity_collection(
      event: ReceptionDomainEvent
    ): Option[String] =
      _string_value(event, Vector("entity", "entityName", "entity_name", "entityCollection", "entity_collection", "collection"))

    private def _extract_entity_id(
      registration: WorkflowRegistration,
      event: ReceptionDomainEvent
    ): Option[String] =
      _string_value(event, Vector(registration.entityIdKey))

    private def _string_value(
      event: ReceptionDomainEvent,
      keys: Vector[String]
    ): Option[String] =
      keys.iterator.map(_.trim).filter(_.nonEmpty).flatMap { key =>
        event.attributes.get(key).filter(_.nonEmpty).orElse(
          event.payload.get(key).collect {
            case s: String if s.nonEmpty => s
            case x if x != null => x.toString
          }.filter(_.nonEmpty)
        )
      }.toSeq.headOption
  }
}
