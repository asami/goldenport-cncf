package org.goldenport.cncf.entity

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.datatype.{Identifier, ObjectId}
import org.goldenport.record.Record
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Apr. 26, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
object SimpleEntityStorageShapePolicy {
  final val PermissionField = "permission"

  private val _target_names: Map[String, String] = Map(
    "id" -> "id",
    "shortid" -> "short_id",
    "createdat" -> "created_at",
    "updatedat" -> "updated_at",
    "createdby" -> "created_by",
    "updatedby" -> "updated_by",
    "aliveness" -> "aliveness",
    "poststatus" -> "post_status",
    "deletedat" -> "deleted_at",
    "deletedby" -> "deleted_by",
    "ownerid" -> "owner_id",
    "groupid" -> "group_id",
    "privilegeid" -> "privilege_id",
    "tenantid" -> "tenant_id",
    "organizationid" -> "organization_id",
    "publishat" -> "publish_at",
    "publicat" -> "public_at",
    "publishedby" -> "published_by",
    "traceid" -> "trace_id",
    "correlationid" -> "correlation_id"
  )

  private val _managed_keys: Set[String] =
    _target_names.keySet ++ Set(
      "securityattributes",
      "security_attributes",
      "rights",
      "permission"
    ).map(_normalize)

  def targetName(logicalName: String): String =
    _target_names.getOrElse(_normalize(logicalName), logicalName)

  def value(record: Record, logicalName: String): Option[Any] = {
    val normalized = _normalize(logicalName)
    val names = Vector(targetName(logicalName), logicalName) ++ _legacy_names(normalized)
    names.distinct.iterator.flatMap(record.getAny).map(_single_value).toVector.headOption
  }

  def stringValue(record: Record, logicalName: String): Option[String] =
    value(record, logicalName).map(_.toString).map(_.trim).filter(_.nonEmpty)

  def withoutManagedFields(record: Record): Record =
    Record(record.fields.filterNot(field => _managed_keys.contains(_normalize(field.key))))

  def withoutSecurityFields(record: Record): Record =
    Record(record.fields.filterNot { field =>
      val key = _normalize(field.key)
      _security_keys.contains(key)
    })

  def targetRecord(fields: (String, Any)*): Record =
    Record.dataAuto(fields.map { case (k, v) => targetName(k) -> v }*)

  def permissionJson(rights: SecurityAttributes.Rights): String = {
    def p(x: SecurityAttributes.Rights.Permissions): Json =
      Json.obj(
        "read" -> Json.fromBoolean(x.read),
        "write" -> Json.fromBoolean(x.write),
        "execute" -> Json.fromBoolean(x.execute)
      )
    Json.obj(
      "owner" -> p(rights.owner),
      "group" -> p(rights.group),
      "other" -> p(rights.other)
    ).noSpaces
  }

  def securityAttributesFromRecord(record: Record): Option[SecurityAttributes] = {
    val fallback = SecurityAttributes.fromRecord(record)
    val rights = stringValue(record, PermissionField).flatMap(permissionRightsFromJson)
      .orElse(fallback.map(_.rights))
    _targetSecurityAttributes(record, rights) match {
      case Some(attributes) =>
        Some(attributes)
      case None =>
        fallback match {
          case Some(attributes) =>
            Some(rights.map(r => attributes.copy(rights = r)).getOrElse(attributes))
          case None =>
            None
        }
    }
  }

  def permissionRightsFromJson(text: String): Option[SecurityAttributes.Rights] =
    parse(text).toOption.flatMap { json =>
      val cursor = json.hcursor
      for {
        owner <- _permissions(cursor.downField("owner"))
        group <- _permissions(cursor.downField("group"))
        other <- _permissions(cursor.downField("other"))
      } yield SecurityAttributes.Rights(owner, group, other)
    }

  private def _permissions(cursor: io.circe.ACursor): Option[SecurityAttributes.Rights.Permissions] =
    for {
      read <- cursor.get[Boolean]("read").toOption
      write <- cursor.get[Boolean]("write").toOption
      execute <- cursor.get[Boolean]("execute").toOption
    } yield SecurityAttributes.Rights.Permissions(read, write, execute)

  private val _security_keys: Set[String] =
    Set(
      "securityattributes",
      "security_attributes",
      "rights",
      "permission",
      "ownerid",
      "groupid",
      "privilegeid"
    ).map(_normalize)

  private def _targetSecurityAttributes(
    record: Record,
    rights: Option[SecurityAttributes.Rights]
  ): Option[SecurityAttributes] =
    for {
      owner <- _targetStringValue(record, "ownerId")
      r <- rights
    } yield SecurityAttributes(
      ownerId = _object_id(owner),
      groupId = _object_id(_targetStringValue(record, "groupId").getOrElse(owner)),
      rights = r,
      privilegeId = _object_id(_targetStringValue(record, "privilegeId").getOrElse(owner))
    )

  private def _targetStringValue(record: Record, logicalName: String): Option[String] =
    record.getAny(targetName(logicalName))
      .map(_single_value)
      .map(_.toString)
      .map(_.trim)
      .filter(_.nonEmpty)

  private def _legacy_names(normalized: String): Vector[String] =
    normalized match {
      case "shortid" => Vector("shortid", "shortId", "short_id")
      case "createdat" => Vector("createdAt", "created_at")
      case "updatedat" => Vector("updatedAt", "updated_at")
      case "createdby" => Vector("createdBy", "created_by")
      case "updatedby" => Vector("updatedBy", "updated_by")
      case "poststatus" => Vector("postStatus", "post_status")
      case "deletedat" => Vector("deletedAt", "deleted_at")
      case "deletedby" => Vector("deletedBy", "deleted_by")
      case "ownerid" => Vector("ownerId", "owner_id")
      case "groupid" => Vector("groupId", "group_id")
      case "privilegeid" => Vector("privilegeId", "privilege_id")
      case "tenantid" => Vector("tenantId", "tenant_id")
      case "organizationid" => Vector("organizationId", "organization_id")
      case "publishat" => Vector("publishAt", "publish_at")
      case "publicat" => Vector("publicAt", "public_at")
      case "publishedby" => Vector("publishedBy", "published_by")
      case "traceid" => Vector("traceId", "trace_id")
      case "correlationid" => Vector("correlationId", "correlation_id")
      case _ => Vector.empty
    }

  private def _single_value(p: Any): Any =
    p match {
      case Some(v) => _single_value(v)
      case None => ""
      case m => m
    }

  private def _object_id(text: String): ObjectId =
    ObjectId(Identifier(_identifier_text(text)))

  private def _identifier_text(text: String): String = {
    val sanitized = text.trim.map {
      case c if c.isLetterOrDigit || c == '_' => c
      case _ => '_'
    }.mkString
    val nonempty = if (sanitized.isEmpty) "unknown" else sanitized
    if (nonempty.headOption.exists(_.isLetter))
      nonempty
    else
      s"id_$nonempty"
  }

  private def _normalize(name: String): String =
    name.filter(_.isLetterOrDigit).toLowerCase(java.util.Locale.ROOT)
}
