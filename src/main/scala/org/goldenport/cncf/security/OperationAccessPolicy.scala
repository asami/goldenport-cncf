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
 * @version Apr.  6, 2026
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

  def authorizeUnitOfWorkDefault(
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Consequence[Unit] =
    authorization.access.flatMap(a => Option(a.policy).map(_.trim.toLowerCase(java.util.Locale.ROOT))) match
      case Some(policy) if Set("manager_only", "manager-only").contains(policy) =>
        authorizeManagerOnly()
      case _ =>
        _authorize_domain_default(authorization)

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
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Consequence[Unit] =
    authorization.resourceFamily.trim.toLowerCase(java.util.Locale.ROOT) match
      case "domain" =>
        authorization.targetId match
          case Some(id) if Set("read", "update", "delete").contains(authorization.accessKind) =>
            authorizeSimpleEntityOwnerOrManager(id, _ => Consequence.success(None))
          case _ =>
            Consequence.unit
      case _ =>
        Consequence.unit

  private def _is_visible_simple_entity[T](
    entity: T,
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Boolean = {
    val entityId = tc.id(entity)
    if (entityId.print == ctx.security.principal.id.value)
      true
    else
      _owner_token(tc.toRecord(entity)).contains(ctx.security.principal.id.value)
  }
}
