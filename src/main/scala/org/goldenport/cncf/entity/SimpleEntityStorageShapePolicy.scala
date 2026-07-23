package org.goldenport.cncf.entity

import scala.deprecatedName
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.datatype.{Identifier, ObjectId}
import org.goldenport.record.Record
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Apr. 26, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
object SimpleEntityStorageShapePolicy {
  final val PERMISSION_FIELD = "permission"
  final val POLICY_NAME = "simple_entity_default"
  final val CONCURRENCY_REVISION_LOGICAL_FIELD = "cncfRevision"
  final val CONCURRENCY_REVISION_STORAGE_FIELD = "cncf_revision"
  val managementLogicalFields: Vector[String] =
    Vector(
      "id",
      "shortId",
      "createdAt",
      "updatedAt",
      "createdBy",
      "updatedBy",
      "aliveness",
      "postStatus",
      "deletedAt",
      "deletedBy",
      "tenantId",
      "organizationId",
      "publishAt",
      "publicAt",
      "publishedBy",
      "traceId",
      "correlationId",
      CONCURRENCY_REVISION_LOGICAL_FIELD
    )
  val securityIdentityLogicalFields: Vector[String] =
    Vector("ownerId", "groupId", "privilegeId")

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
    "correlationid" -> "correlation_id",
    "cncfrevision" -> CONCURRENCY_REVISION_STORAGE_FIELD
  )

  private val _managed_keys: Set[String] =
    _target_names.keySet ++ Set(
      "securityattributes",
      "security_attributes",
      "rights",
      "permission"
    ).map(_normalize)

  def targetName(
    @deprecatedName("logicalName", "0.5.1") logicalname: String
  ): String =
    _target_names.getOrElse(_normalize(logicalname), logicalname)

  def value(
    record: Record,
    @deprecatedName("logicalName", "0.5.1") logicalname: String
  ): Option[Any] = {
    val normalized = _normalize(logicalname)
    val names =
      Vector(targetName(logicalname), logicalname) ++
        _legacy_names(normalized)
    names.distinct.iterator.flatMap(record.getAny).map(_single_value).toVector.headOption
  }

  def stringValue(
    record: Record,
    @deprecatedName("logicalName", "0.5.1") logicalname: String
  ): Option[String] =
    value(record, logicalname).map(_.toString).map(_.trim).filter(_.nonEmpty)

  def withoutManagedFields(record: Record): Record =
    Record(record.fields.filterNot(field => _managed_keys.contains(_normalize(field.key))))

  def withoutConcurrencyRevisionField(record: Record): Record =
    Record(
      record.fields.filterNot(field =>
        isConcurrencyRevisionField(field.key)
      )
    )

  def isConcurrencyRevisionField(name: String): Boolean =
    _normalize(name) == _normalize(CONCURRENCY_REVISION_LOGICAL_FIELD)

  def isConcurrencyRevisionStorageField(name: String): Boolean =
    name == CONCURRENCY_REVISION_STORAGE_FIELD

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
    val rights = _permission_rights(record)
      .orElse(fallback.map(_.rights))
    _target_security_attributes(record, rights) match {
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

  private def _permission_rights(record: Record): Option[SecurityAttributes.Rights] =
    record.getAny(PERMISSION_FIELD).flatMap {
      case value: String => permissionRightsFromJson(value)
      case value: Record => _permission_rights_from_record(value)
      case _ => None
    }

  private def _permission_rights_from_record(record: Record): Option[SecurityAttributes.Rights] =
    for {
      owner <- record.getRecord("owner").flatMap(_permissions)
      group <- record.getRecord("group").flatMap(_permissions)
      other <- record.getRecord("other").flatMap(_permissions)
    } yield SecurityAttributes.Rights(owner, group, other)

  private def _permissions(record: Record): Option[SecurityAttributes.Rights.Permissions] =
    for {
      read <- record.getBoolean("read")
      write <- record.getBoolean("write")
      execute <- record.getBoolean("execute")
    } yield SecurityAttributes.Rights.Permissions(read, write, execute)

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

  private def _target_security_attributes(
    record: Record,
    rights: Option[SecurityAttributes.Rights]
  ): Option[SecurityAttributes] =
    for {
      owner <- _target_string_value(record, "ownerId")
      r <- rights
    } yield SecurityAttributes(
      ownerId = _object_id(owner),
      groupId = _object_id(_target_string_value(record, "groupId").getOrElse(owner)),
      rights = r,
      privilegeId = _object_id(_target_string_value(record, "privilegeId").getOrElse(owner))
    )

  private def _target_string_value(record: Record, logicalname: String): Option[String] =
    record.getAny(targetName(logicalname))
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
