package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.datatype.PathName
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Apr.  6, 2026
 *  version Apr.  6, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object OperationAccessPolicy {
  private val _manager_aliases = Set(
    "contentmanager",
    "contentadmin",
    "contentadministrator",
    "contentowner"
  )

  def authorizeOwnerOrManager(
    record: Record
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else
      _owner_token(record) match {
        case Some(owner) if owner == ctx.security.principal.id.value =>
          Consequence.unit
        case Some(_) =>
          Consequence.failure("Owner or manager privilege is required.")
        case None =>
          Consequence.failure("Owner information is not available for authorization.")
      }

  def authorizeManagerOnly()(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else
      Consequence.failure("Management privilege is required.")

  def authorizeSimpleEntityOwnerOrManager(
    entityId: EntityId,
    loadRecord: EntityId => Consequence[Option[Record]]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else if (entityId.print == ctx.security.principal.id.value)
      Consequence.unit
    else
      loadRecord(entityId).flatMap {
        case Some(record) => authorizeOwnerOrManager(record)
        case None => Consequence.failure(s"SimpleEntity not found: ${entityId.print}")
      }

  def hasManagerPrivilege(using ctx: ExecutionContext): Boolean =
    _is_manager

  def authorizeSimpleEntity(
    record: Record,
    accessKind: String
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else if (_has_matching_privilege(record))
      Consequence.unit
    else
      _role_for(record) match
        case Some("owner") if _permission_for(record, "owner", accessKind) => Consequence.unit
        case Some("group") if _permission_for(record, "group", accessKind) => Consequence.unit
        case Some("other") if _permission_for(record, "other", accessKind) => Consequence.unit
        case Some("owner") => Consequence.failure(s"Owner permission is insufficient for $accessKind.")
        case Some("group") => Consequence.failure(s"Group permission is insufficient for $accessKind.")
        case Some("other") => Consequence.failure(s"Permission is insufficient for $accessKind.")
        case Some(_) => Consequence.failure(s"Permission is insufficient for $accessKind.")
        case None => Consequence.failure("Security attributes are not available for authorization.")

  def authorizeUnitOfWorkDefault(
    authorization: UnitOfWorkAuthorization,
    loadRecord: EntityId => Consequence[Option[Record]] = _ => Consequence.success(None)
  )(using ctx: ExecutionContext): Consequence[Unit] =
    authorization.access.flatMap(a => Option(a.policy).map(_.trim.toLowerCase(java.util.Locale.ROOT))) match
      case Some(policy) if Set("manager_only", "manager-only").contains(policy) =>
        authorizeManagerOnly()
      case _ =>
        _authorize_domain_default(authorization, loadRecord)

  def filterVisibleSearchResult[T](
    authorization: UnitOfWorkAuthorization,
    result: SearchResult[T],
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Consequence[SearchResult[T]] =
    authorization.access.flatMap(a => Option(a.policy).map(_.trim.toLowerCase(java.util.Locale.ROOT))) match
      case Some(policy) if Set("manager_only", "manager-only").contains(policy) =>
        authorizeManagerOnly().map(_ => result)
      case _ =>
        authorization.resourceFamily.trim.toLowerCase(java.util.Locale.ROOT) match
          case "domain" if authorization.accessKind == "search/list" && !_is_manager =>
            val visible = result.data.filter(_is_visible_simple_entity(_, tc))
            Consequence.success(
              result.copy(
                data = visible,
                totalCount = Some(visible.size),
                fetchedCount = visible.size
              )
            )
          case _ =>
            Consequence.success(result)

  private def _owner_token(
    record: Record
  ): Option[String] =
    Vector(
      Vector("security_attributes", "owner_id"),
      Vector("securityAttributes", "ownerId"),
      Vector("created_by"),
      Vector("createdBy")
    ).iterator.map(x => record.getString(PathName(x))).collectFirst {
      case Some(s) if s.trim.nonEmpty => s.trim
    }

  private def _group_token(
    record: Record
  ): Option[String] =
    Vector(
      Vector("security_attributes", "group_id"),
      Vector("securityAttributes", "groupId"),
      Vector("group_id"),
      Vector("groupId"),
      Vector("group")
    ).iterator.map(x => record.getString(PathName(x))).collectFirst {
      case Some(s) if s.trim.nonEmpty => s.trim
    }

  private def _privilege_token(
    record: Record
  ): Option[String] =
    Vector(
      Vector("security_attributes", "privilege_id"),
      Vector("securityAttributes", "privilegeId"),
      Vector("privilege_id"),
      Vector("privilegeId"),
      Vector("privilege")
    ).iterator.map(x => record.getString(PathName(x))).collectFirst {
      case Some(s) if s.trim.nonEmpty => s.trim
    }

  private def _is_manager(using ctx: ExecutionContext): Boolean = {
    val roles = _attribute_tokens("role", "roles", "authority", "authorities")
    val capabilities = ctx.security.capabilities.map(_.name).flatMap(_split_tokens)
    val level = _split_tokens(ctx.security.level.value)
    (roles ++ capabilities ++ level).exists(x => _manager_aliases.contains(_normalize_alias(x)))
  }

  private def _attribute_tokens(keys: String*)(using ctx: ExecutionContext): Set[String] =
    keys.iterator.flatMap(k => ctx.security.principal.attributes.get(k).toVector).flatMap(_split_tokens).toSet

  private def _split_tokens(value: String): Set[String] =
    Option(value).toSet.flatMap(_.split("[,\\s|]+")).map(_.trim).filter(_.nonEmpty)

  private def _normalize_alias(value: String): String =
    value.trim.toLowerCase.replace("_", "").replace("-", "")

  private def _authorize_domain_default(
    authorization: UnitOfWorkAuthorization,
    loadRecord: EntityId => Consequence[Option[Record]]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    authorization.resourceFamily.trim.toLowerCase(java.util.Locale.ROOT) match
      case "domain" =>
        authorization.targetId match
          case Some(id) if Set("read", "update", "delete").contains(authorization.accessKind) =>
            loadRecord(id).flatMap {
              case Some(record) => authorizeSimpleEntity(record, authorization.accessKind)
              case None => authorizeSimpleEntityOwnerOrManager(id, loadRecord)
            }
          case _ =>
            Consequence.unit
      case _ =>
        Consequence.unit

  private def _is_visible_simple_entity[T](
    entity: T,
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Boolean = {
    authorizeSimpleEntity(tc.toRecord(entity), "read") match
      case Consequence.Success(_) => true
      case _ => false
  }

  private def _role_for(
    record: Record
  )(using ctx: ExecutionContext): Option[String] = {
    val principalId = ctx.security.principal.id.value
    if (_owner_token(record).contains(principalId))
      Some("owner")
    else if (_group_token(record).exists(_matches_group))
      Some("group")
    else if (_owner_token(record).nonEmpty || _group_token(record).nonEmpty)
      Some("other")
    else
      None
  }

  private def _matches_group(
    groupId: String
  )(using ctx: ExecutionContext): Boolean =
    _attribute_tokens("group", "group_id", "groups", "group_ids").exists(x => _normalize_alias(x) == _normalize_alias(groupId))

  private def _has_matching_privilege(
    record: Record
  )(using ctx: ExecutionContext): Boolean =
    _privilege_token(record).exists { privilegeId =>
      val p = _normalize_alias(privilegeId)
      _attribute_tokens("privilege", "privilege_id", "privileges").exists(x => _normalize_alias(x) == p) ||
      ctx.security.capabilities.exists(c => _normalize_alias(c.name) == p) ||
      _normalize_alias(ctx.security.level.value) == p
    }

  private def _permission_for(
    record: Record,
    role: String,
    accessKind: String
  ): Boolean = {
    val normalizedRole = _normalize_alias(role)
    val segment = normalizedRole match
      case "owner" => "owner"
      case "group" => "group"
      case _ => "other"
    val pathPrefix =
      Vector(
        Vector("security_attributes", "rights", segment),
        Vector("securityAttributes", "rights", segment)
      )
    def readBool(name: String): Option[Boolean] =
      pathPrefix.iterator.map(x => _get_boolean(record, x :+ name)).collectFirst {
        case Some(b) => b
      }
    accessKind match
      case "read" | "search/list" => readBool("read").getOrElse(false)
      case "update" => readBool("write").getOrElse(false)
      case "delete" => readBool("execute").orElse(readBool("write")).getOrElse(false)
      case "create" => false
      case other => readBool(other).getOrElse(false)
  }

  private def _get_boolean(
    record: Record,
    path: Vector[String]
  ): Option[Boolean] =
    path.toList match
      case Nil => None
      case key :: Nil => record.getBoolean(key)
      case key :: rest =>
        record.getRecord(key).flatMap(_get_boolean(_, rest.toVector))
}
