package org.goldenport.cncf.event

import java.time.Instant
import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.statemachine.*
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/**
 * Complete typed lookup facts only. Decoding never admits a CML execution.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
private[event] object CommittedDeliveryKeyJsonV1 {
  val schema = "cncf.committed-delivery-key.v1"
  val prefix = schema + ":"
  val maximumBytes = 65536

  def encodeStorage(key: CommittedDeliveryLookupKey): String = {
    val value = prefix + toJson(key).noSpaces
    require(value.getBytes(UTF_8).length <= maximumBytes, "Delivery key exceeds byte bound")
    value
  }

  def decodeStorageC(value: String): Consequence[CommittedDeliveryLookupKey] = safely {
    require(value != null && value.getBytes(UTF_8).length <= maximumBytes && value.startsWith(prefix),
      "Invalid delivery key storage")
    val result = fromJson(parse(value.substring(prefix.length)).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid delivery key JSON")))
    require(encodeStorage(result) == value, "Noncanonical or ambiguous delivery key")
    result
  }

  def toJson(key: CommittedDeliveryLookupKey): Json = {
    require(key != null && key.entityId != null && key.binding != null, "Missing delivery key")
    val entity = key.entityId
    val binding = key.binding
    val context = binding.triggerContext.map { ctx =>
      require(ctx != null && ctx.fields != null && ctx.fields.size <= 256, "Invalid trigger context")
      Json.obj("fields" -> Json.arr(ctx.fields.map { field =>
        require(field != null && field.identity != null && field.scalarType != null, "Invalid context field")
        Json.obj("name" -> text(field.identity.name), "type" -> text(field.scalarType.code))
      }*))
    }.getOrElse(Json.Null)
    val target = binding.target match {
      case CmlStateMachineTransitionTarget.State(state) =>
        Json.obj("kind" -> text("state"), "path" -> path(state.path))
      case CmlStateMachineTransitionTarget.ShallowHistory(history) =>
        Json.obj("kind" -> text("shallow-history"), "composite" -> path(history.composite.path),
          "fallback" -> path(history.fallbackLeaf.path))
      case CmlStateMachineTransitionTarget.Final => Json.obj("kind" -> text("final"))
    }
    // Referenced machine/trigger/context/version identities are shared only
    // where the producer constructors enforce equality; arrays retain order.
    val json = Json.obj("schema" -> text(schema), "invocationKey" -> text(key.invocationKey, 8192),
      "entity" -> Json.obj("major" -> text(entity.major), "minor" -> text(entity.minor),
        "collection" -> collection(entity.collection),
        "timestamp" -> text(entity.timestamp.get.toString), "entropy" -> text(entity.entropy.get)),
      "binding" -> Json.obj("component" -> text(binding.componentId.name),
        "entityType" -> collection(binding.entityType), "machine" -> text(binding.machine.name),
        "version" -> Json.fromInt(binding.version.value), "order" -> Json.fromInt(binding.transition.declarationOrder),
        "source" -> path(binding.source.path), "target" -> target, "trigger" -> text(binding.trigger.name),
        "operation" -> binding.operation.map(op => Json.obj("service" -> text(op.service),
          "operation" -> text(op.operation))).getOrElse(Json.Null), "context" -> context),
      "operationId" -> text(key.operationId))
    // Validate producer invariants even for a case-class copy or null member.
    require((prefix + json.noSpaces).getBytes(UTF_8).length <= maximumBytes, "Delivery key exceeds byte bound")
    require(fromJson(json) == key, "Delivery key is not structurally restorable")
    json
  }

  def fromJson(json: Json): CommittedDeliveryLookupKey = {
    val root = fields(json, Set("schema", "invocationKey", "entity", "binding", "operationId"))
    require(string(root("schema")) == schema, "Unknown delivery key schema")
    val e = fields(root("entity"), Set("major", "minor", "collection", "timestamp", "entropy"))
    val entity = EntityId.restore(string(e("major")), string(e("minor")), readCollection(e("collection")),
      Instant.parse(string(e("timestamp"))), string(e("entropy"))).toOption.getOrElse(
        throw new IllegalArgumentException("Invalid structured EntityId"))
    val b = fields(root("binding"), Set("component", "entityType", "machine", "version", "order",
      "source", "target", "trigger", "operation", "context"))
    val machine = CmlStateMachineIdentity(string(b("machine")))
    val version = CmlStateMachineVersion(integer(b("version")))
    val trigger = CmlStateMachineTriggerIdentity(machine, string(b("trigger")))
    def state(value: Json) = CmlStateMachineStateIdentity(machine, readPath(value))
    val targetFields = b("target").asObject.getOrElse(throw new IllegalArgumentException("Invalid target"))
    val target = targetFields("kind").map(string(_)).getOrElse("") match {
      case "state" => CmlStateMachineTransitionTarget.State(state(fields(b("target"), Set("kind", "path"))("path")))
      case "shallow-history" =>
        val t = fields(b("target"), Set("kind", "composite", "fallback"))
        CmlStateMachineTransitionTarget.ShallowHistory(CmlStateMachineShallowHistoryTarget(state(t("composite")), state(t("fallback"))))
      case "final" => fields(b("target"), Set("kind")); CmlStateMachineTransitionTarget.Final
      case _ => throw new IllegalArgumentException("Unsupported target")
    }
    val operation = if (b("operation").isNull) None else {
      val op = fields(b("operation"), Set("service", "operation"))
      Some(CmlStateMachineOperationIdentity(string(op("service")), string(op("operation"))))
    }
    val context = if (b("context").isNull) None else {
      val identity = CmlStateMachineTriggerContextIdentity(trigger)
      val ctx = fields(b("context"), Set("fields"))
      val entries = ctx("fields").asArray.filter(_.size <= 256).getOrElse(
        throw new IllegalArgumentException("Invalid context fields"))
      Some(CmlStateMachineTriggerContext(identity, version, entries.map { entry =>
        val f = fields(entry, Set("name", "type"))
        val scalar = CmlStateMachineScalarType.values.find(_.code == string(f("type"))).getOrElse(
          throw new IllegalArgumentException("Unsupported context scalar"))
        CmlStateMachineTriggerContextField(CmlStateMachineTriggerContextFieldIdentity(identity, string(f("name"))), scalar)
      }))
    }
    CommittedDeliveryLookupKey(string(root("invocationKey"), 8192), entity,
      CmlTransitionBinding(ComponentId(string(b("component"))), readCollection(b("entityType")), machine, version,
        CmlStateMachineTransitionIdentity(machine, integer(b("order"))), state(b("source")), target, trigger, operation, context),
      string(root("operationId")))
  }

  private def collection(value: EntityCollectionId): Json =
    Json.obj("major" -> text(value.major), "minor" -> text(value.minor), "name" -> text(value.name))
  private def readCollection(json: Json): EntityCollectionId = {
    val c = fields(json, Set("major", "minor", "name"))
    EntityCollectionId(string(c("major")), string(c("minor")), string(c("name")))
  }
  private def path(value: CmlStateMachineStatePath): Json = {
    require(value.segments.size <= 64, "State path exceeds bound")
    Json.arr(value.segments.map(text(_))*)
  }
  private def readPath(json: Json): CmlStateMachineStatePath =
    CmlStateMachineStatePath(json.asArray.filter(_.size <= 64).getOrElse(
      throw new IllegalArgumentException("Invalid state path")).map(string(_)))
  private def fields(json: Json, expected: Set[String]): Map[String, Json] =
    json.asObject.map(_.toMap).filter(_.keySet == expected).getOrElse(
      throw new IllegalArgumentException("Unexpected structural fields"))
  private def text(value: String, limit: Int = 128): Json = {
    require(value != null && value.trim.nonEmpty && value.length <= limit, "Invalid structural text")
    Json.fromString(value)
  }
  private def string(json: Json, limit: Int = 128): String = {
    val value = json.asString.getOrElse(throw new IllegalArgumentException("Expected text"))
    text(value, limit); value
  }
  private def integer(json: Json): Int =
    json.asNumber.flatMap(_.toInt).getOrElse(throw new IllegalArgumentException("Expected Int"))
  private def safely[A](f: => A): Consequence[A] = try Consequence.success(f) catch {
    case NonFatal(_) => Consequence.stateConflict("Delivery key canonical admission failed")
  }
}
