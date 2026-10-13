package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationValue, ConfigurationValueCodec}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.datastore.{ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.{EventPersistencePolicy, EventStorePersistenceDeclaration, EventTypeId}
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * Pure wire conversion. A parsed plan is not host/source admission.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[cncf] object SubsystemStoragePlanCodec extends ConfigurationValueCodec[SubsystemStoragePlan] {
  val SCHEMA = "cncf.subsystem-storage.v1"
  val MAX_BYTES = 65536
  val MAX_DEPTH = 16
  private val parser = JawnParser(allowDuplicateKeys = false)

  def decode(value: ConfigurationValue): Consequence[SubsystemStoragePlan] = value match {
    case ConfigurationValue.StringValue(text) =>
      val parsed = for {
        _ <- Either.cond(text != null && StandardCharsets.UTF_8.newEncoder().canEncode(text) &&
          text.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES && !text.startsWith("\uFEFF") &&
          boundedDepth(text), (), "Invalid bounded storage plan text")
        json <- try parser.parse(text).left.map(_ => "Invalid storage plan JSON")
          catch { case NonFatal(_) => Left("Invalid storage plan JSON") }
        root <- fields(json, Set("schema", "policy", "persistence", "records"))
        schema <- string(root, "schema")
        _ <- Either.cond(schema == SCHEMA, (), "Unsupported storage plan schema")
        policy <- decodePolicy(root("policy").get)
        persistence <- decodePersistence(root("persistence").get)
        recordValues <- array(root, "records", ComponentRecordStorageDeclaration.MAX_GRANTS)
        recordGrants <- sequence(recordValues.map(decodeRecord))
        records <- if (recordGrants.isEmpty) Right(None)
          else valid(ComponentRecordStorageDeclaration.createC(recordGrants)).map(Some(_))
        plan <- valid(SubsystemStoragePlan.createC(policy, persistence, records))
      } yield plan
      parsed.fold(Consequence.configurationInvalid, Consequence.success)
    case _ => Consequence.configurationInvalid("Storage plan requires a JSON string")
  }

  def encode(plan: SubsystemStoragePlan): Consequence[ConfigurationValue] =
    if (plan == null) Consequence.configurationInvalid("Storage plan is required")
    else {
      val p = plan.persistence
      val json = Json.obj(
        "schema" -> Json.fromString(SCHEMA),
        "policy" -> Json.obj("revision" -> Json.fromString(plan.policy.revision),
          "rules" -> Json.fromValues(plan.policy.rules.map(r => Json.obj(
            "eventType" -> Json.fromString(r.eventType.value),
            "mode" -> Json.fromString(if (r.mode == EventPersistencePolicy.Mode.Persist) "persist" else "transient"))))),
        "persistence" -> Json.obj("storeName" -> Json.fromString(p.storeName),
          "cursorCollection" -> collectionJson(p.cursorCollection),
          "eventCollection" -> collectionJson(p.eventCollection),
          "deliveryIndexCollection" -> collectionJson(p.deliveryIndexCollection),
          "cursorEntryId" -> Json.fromString(p.cursorEntryId.print),
          "workflowGrants" -> Json.fromValues(p.workflowGrants.map(g => Json.obj(
            "componentId" -> Json.fromString(g.componentId.name), "storeName" -> Json.fromString(g.storeName),
            "rootCollection" -> collectionJson(g.rootCollection),
            "continuationCollection" -> collectionJson(g.continuationCollection))))),
        "records" -> Json.fromValues(plan.records.toVector.flatMap(_.grants).map(g => Json.obj(
          "componentId" -> Json.fromString(g.componentId.name), "storeName" -> Json.fromString(g.storeName),
          "collection" -> collectionJson(g.collection)))))
      val encoded = ConfigurationValue.StringValue(json.noSpaces)
      decode(encoded).map(_ => encoded)
    }

  private def decodePolicy(json: Json): Either[String, EventPersistencePolicy] = for {
    obj <- fields(json, Set("revision", "rules"))
    revision <- string(obj, "revision")
    values <- array(obj, "rules", EventPersistencePolicy.MAX_RULES)
    rules <- sequence(values.map { value => for {
      rule <- fields(value, Set("eventType", "mode"))
      eventType <- string(rule, "eventType")
      modeName <- string(rule, "mode")
      mode <- modeName match {
        case "persist" => Right(EventPersistencePolicy.Mode.Persist)
        case "transient" => Right(EventPersistencePolicy.Mode.Transient)
        case _ => Left("Invalid persistence mode")
      }
    } yield EventPersistencePolicy.Rule(EventTypeId(eventType), mode) })
    policy <- valid(EventPersistencePolicy.createC(revision, rules))
  } yield policy

  private def decodePersistence(json: Json): Either[String, EventStorePersistenceDeclaration] = for {
    obj <- fields(json, Set("storeName", "cursorCollection", "eventCollection", "deliveryIndexCollection",
      "cursorEntryId", "workflowGrants"))
    store <- string(obj, "storeName")
    cursor <- collection(obj, "cursorCollection")
    events <- collection(obj, "eventCollection")
    indexes <- collection(obj, "deliveryIndexCollection")
    entry <- string(obj, "cursorEntryId")
    values <- array(obj, "workflowGrants", EventStorePersistenceDeclaration.MAX_WORKFLOW_GRANTS)
    grants <- sequence(values.map { value => for {
      grant <- fields(value, Set("componentId", "storeName", "rootCollection", "continuationCollection"))
      owner <- component(grant)
      store <- string(grant, "storeName")
      root <- collection(grant, "rootCollection")
      continuation <- collection(grant, "continuationCollection")
    } yield EventStorePersistenceDeclaration.WorkflowGrant(owner, store, root, continuation) })
    declaration <- valid(EventStorePersistenceDeclaration.createC(store, cursor, events, indexes,
      grants, DataStore.StringEntryId(entry)))
  } yield declaration

  private def decodeRecord(json: Json): Either[String, ComponentRecordStorageDeclaration.CreateGrant] = for {
    obj <- fields(json, Set("componentId", "storeName", "collection"))
    owner <- component(obj)
    store <- string(obj, "storeName")
    target <- collection(obj, "collection")
  } yield ComponentRecordStorageDeclaration.CreateGrant(owner, store, target)

  private def fields(json: Json, expected: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == expected).toRight("Storage plan object fields are invalid")
  private def string(obj: JsonObject, key: String): Either[String, String] =
    obj(key).flatMap(_.asString).toRight("Storage plan field requires a string")
  private def array(obj: JsonObject, key: String, maximum: Int): Either[String, Vector[Json]] =
    obj(key).flatMap(_.asArray).filter(_.size <= maximum).toRight("Storage plan requires a bounded array")
  private def component(obj: JsonObject): Either[String, ComponentId] =
    string(obj, "componentId").flatMap(text => valid(ComponentId.parseC(text)))
  private def collection(obj: JsonObject, key: String): Either[String, DataStore.CollectionId] =
    string(obj, key).flatMap { text =>
      EntityCollectionId.parse(text).toOption.filter(_.print == text)
        .map(DataStore.CollectionId.EntityStore(_)).toRight("Storage plan collection is not canonical")
    }
  private def collectionJson(value: DataStore.CollectionId): Json = value match {
    case DataStore.CollectionId.EntityStore(identity) => Json.fromString(identity.print)
    case _ => throw new IllegalArgumentException("Storage plan requires entity collections")
  }
  private def valid[A](value: Consequence[A]): Either[String, A] =
    value.toOption.toRight("Storage plan declaration is invalid")
  private def sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (acc, next) =>
      for { previous <- acc; value <- next } yield previous :+ value
    }

  /** Bound parser nesting without counting brackets inside JSON strings. */
  private def boundedDepth(text: String): Boolean = {
    var depth = 0
    var quoted = false
    var escaped = false
    var accepted = true
    text.foreach { ch =>
      if (quoted) {
        if (escaped) escaped = false
        else if (ch == '\\') escaped = true
        else if (ch == '"') quoted = false
      } else if (ch == '"') quoted = true
      else if (ch == '{' || ch == '[') {
        depth += 1
        if (depth > MAX_DEPTH) accepted = false
      } else if (ch == '}' || ch == ']') depth -= 1
    }
    accepted
  }
}
