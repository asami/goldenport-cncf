package org.goldenport.cncf.entity

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.{Query as EntityDirectiveQuery}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Hook point for normal entity access scope.
 *
 * Logical delete is active today. Tenant scoping is intentionally a NOP until
 * ExecutionContext carries the canonical tenant model.
 *
 * @since   May. 2, 2026
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
object EntityAccessScopePolicy {
  // Normal read/search scope hook.
  //
  // This is the single place where EntityStore-level invariants are translated
  // into datastore query conditions. `deletedAt` is active now. Tenant scope is
  // deliberately wired as a NOP hook so future ExecutionContext tenant data can
  // add conditions here without changing application code or internal DSLs.
  def normalSearchExpr(
    collection: EntityCollectionId,
    base: EntityDirectiveQuery.Expr,
    deletedAtStoreField: String
  )(using ExecutionContext): EntityDirectiveQuery.Expr =
    _and(Vector(
      base,
      EntityDirectiveQuery.IsNull(deletedAtStoreField)
    ) ++ tenantSearchExpr(collection))

  // Post-load/post-search safety check paired with `normalSearchExpr`.
  //
  // Keep this even when query predicates exist. Some stores may not support all
  // predicates equally, and identity/uniqueness must never treat deleted rows as
  // candidates.
  def normalRecordVisible(
    collection: EntityCollectionId,
    record: Record
  )(using ExecutionContext): Boolean =
    !EntityLifecycleRecordPolicy.isLogicallyDeleted(record) &&
      tenantRecordVisible(collection, record)

  def filterNormalRecords(
    collection: EntityCollectionId,
    records: Vector[Record]
  )(using ExecutionContext): Vector[Record] =
    records.filter(normalRecordVisible(collection, _))

  def visibilityRecordVisible(
    collection: EntityCollectionId,
    record: Record,
    scope: Option[EntityVisibilityScope]
  )(using ExecutionContext): Boolean =
    normalRecordVisible(collection, record) && (scope match {
      case Some(EntityVisibilityScope.Public) =>
        _post_status(record).contains("published") &&
          _aliveness(record).contains("alive")
      case Some(EntityVisibilityScope.Owner) =>
        _owner_matches_current_subject(record)
      case Some(EntityVisibilityScope.Admin) =>
        true
      case None =>
        true
    })

  // Future tenant query hook.
  //
  // When ExecutionContext grows canonical tenant/organization scope, this should
  // return an Expr such as `tenant_id = currentTenant`. Today it is NOP so global
  // uniqueness and normal entity visibility stay unchanged.
  def tenantSearchExpr(
    collection: EntityCollectionId
  )(using ExecutionContext): Option[EntityDirectiveQuery.Expr] =
    None

  // Future tenant post-filter hook paired with `tenantSearchExpr`.
  //
  // Keeping the record-level hook now prevents a future SQL-only implementation
  // from diverging from in-memory stores, EntitySpace, and identity lookup.
  def tenantRecordVisible(
    collection: EntityCollectionId,
    record: Record
  )(using ExecutionContext): Boolean =
    true

  private def _and(
    items: Vector[EntityDirectiveQuery.Expr]
  ): EntityDirectiveQuery.Expr = {
    val normalized = items.flatMap {
      case EntityDirectiveQuery.True => Vector.empty
      case EntityDirectiveQuery.And(xs) => xs
      case other => Vector(other)
    }
    normalized match {
      case Vector() => EntityDirectiveQuery.True
      case Vector(single) => single
      case xs => EntityDirectiveQuery.And(xs)
    }
  }

  private def _post_status(record: Record)(using ExecutionContext): Option[String] =
    _record_value(record, Vector("postStatus", "post_status"))
      .flatMap(EntityLifecycleRecordPolicy.postStatusToken)

  private def _aliveness(record: Record)(using ExecutionContext): Option[String] =
    _record_value(record, Vector("aliveness", "aliveness_status"))
      .flatMap(EntityLifecycleRecordPolicy.alivenessToken)

  private def _owner_matches_current_subject(
    record: Record
  )(using ctx: ExecutionContext): Boolean = {
    val subject = SecuritySubject.current
    subject.isAuthenticated &&
      _record_value(record, Vector("ownerId", "owner_id"))
        .exists(v => SecuritySubject.normalize(v.toString) == SecuritySubject.normalize(subject.subjectId))
  }

  private def _record_value(
    record: Record,
    keys: Vector[String]
  )(using ctx: ExecutionContext): Option[Any] = {
    val m = record.asMap
    keys
      .flatMap(org.goldenport.cncf.context.RuntimeContext.Context.default.propertyName.aliases)
      .distinct
      .collectFirst(Function.unlift(name =>
        m.get(name).filterNot(java.util.Objects.isNull)
      ))
  }
}
