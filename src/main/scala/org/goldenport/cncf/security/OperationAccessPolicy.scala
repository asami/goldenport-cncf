package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.ObservabilityEngine
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Apr.  6, 2026
 * @version Apr. 13, 2026
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
      _security_attributes(record).map(_.ownerId.id.value) match {
        case Some(owner) if owner == _subject.subjectId =>
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
    else if (entityId.print == _subject.subjectId)
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
    _with_decision_event(authorization, "unit-of-work") {
      authorization.accessMode match
      case EntityAccessMode.ServiceInternal if _is_cross_component(authorization) && !_subject.hasServiceGrant(authorization.sourceComponentName, authorization.targetComponentName) =>
        Consequence.failure(s"Cross-component service grant is required: ${authorization.sourceComponentName.getOrElse("<unknown>")} -> ${authorization.targetComponentName.getOrElse("<unknown>")}.")
      case EntityAccessMode.System | EntityAccessMode.ServiceInternal =>
        _emit_permission_bypass(authorization, "unit-of-work")
        Consequence.unit
      case EntityAccessMode.UserPermission =>
        authorization.access.flatMap(a => Option(a.policy).map(_.trim.toLowerCase(java.util.Locale.ROOT))) match
          case Some(policy) if Set("manager_only", "manager-only").contains(policy) =>
            authorizeManagerOnly()
          case _ =>
            _authorize_domain_default(authorization, loadRecord)
    }

  def filterVisibleSearchResult[T](
    authorization: UnitOfWorkAuthorization,
    result: SearchResult[T],
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Consequence[SearchResult[T]] =
    _with_decision_event(authorization, "search/list") {
      authorization.accessMode match
      case EntityAccessMode.ServiceInternal if _is_cross_component(authorization) && !_subject.hasServiceGrant(authorization.sourceComponentName, authorization.targetComponentName) =>
        Consequence.failure(s"Cross-component service grant is required: ${authorization.sourceComponentName.getOrElse("<unknown>")} -> ${authorization.targetComponentName.getOrElse("<unknown>")}.")
      case EntityAccessMode.System | EntityAccessMode.ServiceInternal =>
        _emit_permission_bypass(authorization, "search/list")
        Consequence.success(result)
      case EntityAccessMode.UserPermission =>
        authorization.access.flatMap(a => Option(a.policy).map(_.trim.toLowerCase(java.util.Locale.ROOT))) match
          case Some(policy) if Set("manager_only", "manager-only").contains(policy) =>
            authorizeManagerOnly().map(_ => result)
          case _ =>
            authorization.resourceFamily.trim.toLowerCase(java.util.Locale.ROOT) match
              case "domain" if authorization.accessKind == "search/list" && !_is_manager =>
                val visibility = result.data.map(_visibility_evaluation(_, tc, authorization))
                val visible = visibility.collect {
                  case (entity, true, _) => entity
                }
                _emit_abac_filter_diagnostics(authorization, visibility)
                Consequence.success(
                  result.copy(
                    data = visible,
                    totalCount = Some(visible.size),
                    fetchedCount = visible.size
                  )
                )
              case _ =>
                Consequence.success(result)
    }

  private def _is_manager(using ctx: ExecutionContext): Boolean = {
    (_subject.roles ++ _subject.capabilities ++ _subject.securityLevel).exists(_manager_aliases.contains)
  }

  private def _authorize_domain_default(
    authorization: UnitOfWorkAuthorization,
    loadRecord: EntityId => Consequence[Option[Record]]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    authorization.resourceFamily.trim.toLowerCase(java.util.Locale.ROOT) match
      case "domain" =>
        authorization.accessKind match
          case "create" =>
            _authorize_domain_create_default(authorization)
          case kind if Set("read", "update", "delete").contains(kind) =>
            authorization.targetId match
              case Some(id) =>
                loadRecord(id).flatMap {
                  case Some(record) =>
                    _natural_condition_miss(record, authorization) match
                      case Some(miss) =>
                        Consequence.failure(miss.message)
                      case None if (authorization.accessKind == "read" && _is_public_policy(authorization)) =>
                        Consequence.unit
                      case None if _matches_relation(record, authorization) =>
                        Consequence.unit
                      case None =>
                        authorizeSimpleEntity(record, authorization.accessKind)
                  case None => authorizeSimpleEntityOwnerOrManager(id, loadRecord)
                }
              case None =>
                Consequence.unit
          case _ =>
            Consequence.unit
      case _ =>
        Consequence.unit

  private def _authorize_domain_create_default(
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else if (_subject.hasCreateGrant(authorization.resourceType, authorization.collectionName))
      Consequence.unit
    else
      Consequence.unit

  private def _is_visible_simple_entity[T](
    entity: T,
    tc: EntityPersistent[T],
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Boolean = {
    val record = tc.toRecord(entity)
    if (_natural_condition_miss(record, authorization).isDefined)
      false
    else if (_is_public_policy(authorization))
      true
    else if (_matches_relation(record, authorization))
      true
    else authorizeSimpleEntity(record, "read") match
      case Consequence.Success(_) => true
      case _ => false
  }

  private def _visibility_evaluation[T](
    entity: T,
    tc: EntityPersistent[T],
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): (T, Boolean, Option[EntityAbacCondition.Evaluation]) = {
    val record = tc.toRecord(entity)
    _natural_condition_miss(record, authorization) match
      case Some(miss) =>
        (entity, false, Some(miss))
      case None if _is_public_policy(authorization) =>
        (entity, true, None)
      case None if _matches_relation(record, authorization) =>
        (entity, true, None)
      case None =>
        authorizeSimpleEntity(record, "read") match
          case Consequence.Success(_) => (entity, true, None)
          case _ => (entity, false, None)
        }
  }

  private def _emit_abac_filter_diagnostics[T](
    authorization: UnitOfWorkAuthorization,
    visibility: Seq[(T, Boolean, Option[EntityAbacCondition.Evaluation])]
  )(using ctx: ExecutionContext): Unit = {
    val misses = visibility.flatMap(_._3)
    if (misses.nonEmpty) {
      val first = misses.head
      val _ = ctx.observability.emitInfo(
        ctx.cncfCore.scope,
        "authorization.abac.filter",
        Record.dataAuto(
          "resource-family" -> authorization.resourceFamily,
          "resource-type" -> authorization.resourceType,
          "collection" -> authorization.collectionName,
          "access-kind" -> authorization.accessKind,
          "total-count" -> visibility.size,
          "visible-count" -> visibility.count(_._2),
          "filtered-count" -> misses.size,
          "first-miss-condition" -> first.conditionText,
          "first-miss-actual" -> first.actual,
          "first-miss-expected" -> first.expected
        )
      )
    }
  }

  private def _emit_permission_bypass(
    authorization: UnitOfWorkAuthorization,
    surface: String
  )(using ctx: ExecutionContext): Unit = {
    val _ = ctx.observability.emitInfo(
      ctx.cncfCore.scope,
      "authorization.permission.bypass",
      Record.dataAuto(
        "surface" -> surface,
        "access-mode" -> _label(authorization.accessMode),
        "resource-family" -> authorization.resourceFamily,
        "resource-type" -> authorization.resourceType,
        "collection" -> authorization.collectionName,
        "target-id" -> authorization.targetId.map(_.print),
        "access-kind" -> authorization.accessKind,
        "subject-id" -> _subject.subjectId
      )
    )
  }

  private def _with_decision_event[A](
    authorization: UnitOfWorkAuthorization,
    surface: String
  )(
    body: => Consequence[A]
  )(using ctx: ExecutionContext): Consequence[A] = {
    val result = body
    _emit_decision_event(authorization, surface, result)
    result
  }

  private def _emit_decision_event[A](
    authorization: UnitOfWorkAuthorization,
    surface: String,
    result: Consequence[A]
  )(using ctx: ExecutionContext): Unit = {
    val subject = _subject
    val outcome = result match
      case Consequence.Success(_) => "allow"
      case Consequence.Failure(_) => "deny"
    RuntimeDashboardMetrics.recordAuthorizationDecision(outcome == "deny")
    val _ = ctx.observability.emitInfo(
      ctx.cncfCore.scope,
      "authorization.decision",
      Record.dataAuto(
        "surface" -> surface,
        "outcome" -> outcome,
        "access-mode" -> _label(authorization.accessMode),
        "resource-family" -> authorization.resourceFamily,
        "resource-type" -> authorization.resourceType,
        "collection" -> authorization.collectionName,
        "target-id" -> authorization.targetId.map(_.print),
        "access-kind" -> authorization.accessKind,
        "entity-names" -> authorization.entityNames.mkString(","),
        "source-component" -> authorization.sourceComponentName,
        "target-component" -> authorization.targetComponentName,
        "subject-id" -> subject.subjectId,
        "subject-roles" -> subject.roles.toVector.sorted.mkString(","),
        "subject-groups" -> subject.groups.toVector.sorted.mkString(","),
        "subject-capabilities" -> subject.capabilities.toVector.sorted.mkString(","),
        "subject-security-level" -> subject.securityLevel.toVector.sorted.mkString(",")
      )
    )
  }

  private def _is_public_policy(
    authorization: UnitOfWorkAuthorization
  ): Boolean =
    authorization.access
      .flatMap(a => Option(a.policy))
      .map(_.trim.toLowerCase(java.util.Locale.ROOT))
      .contains("public")

  private def _is_cross_component(
    authorization: UnitOfWorkAuthorization
  ): Boolean =
    (authorization.sourceComponentName, authorization.targetComponentName) match
      case (Some(source), Some(target)) =>
        SecuritySubject.normalize(source) != SecuritySubject.normalize(target)
      case _ =>
        false

  private def _natural_condition_miss(
    record: Record,
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Option[EntityAbacCondition.Evaluation] = {
    val context = EntityAuthorizationContext(record, authorization)
    authorization.naturalConditions
      .filter(_.allows(authorization.accessKind))
      .iterator
      .map(_.evaluate(context))
      .find(!_.matched)
  }

  private def _matches_relation(
    record: Record,
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Boolean = {
    val evaluations = authorization.relationRules.map { rule =>
      val applicable = rule.allows(authorization.accessKind)
      val matched = applicable && rule.matches(record, _subject)
      (rule, applicable, matched)
    }
    _emit_relation_diagnostics(authorization, evaluations)
    evaluations.exists(_._3)
  }

  private def _emit_relation_diagnostics(
    authorization: UnitOfWorkAuthorization,
    evaluations: Vector[(EntityAccessRelation, Boolean, Boolean)]
  )(using ctx: ExecutionContext): Unit =
    if (evaluations.nonEmpty) {
      val details = evaluations.map {
        case (rule, applicable, matched) =>
          s"${rule.entityField}=subject.${rule.subjectAttribute}:${if (applicable) "applicable" else "not-applicable"}:${if (matched) "matched" else "missed"}"
      }
      val _ = ObservabilityEngine.emitDebug(
        ctx.observability,
        ctx.cncfCore.scope,
        "authorization.relation.diagnostics",
        Record.dataAuto(
          "resource-family" -> authorization.resourceFamily,
          "resource-type" -> authorization.resourceType,
          "collection" -> authorization.collectionName,
          "target-id" -> authorization.targetId.map(_.print),
          "access-kind" -> authorization.accessKind,
          "match-count" -> evaluations.count(_._3),
          "miss-count" -> evaluations.count(x => x._2 && !x._3),
          "not-applicable-count" -> evaluations.count(x => !x._2),
          "details" -> details.mkString(";")
        )
      )
    }

  private def _label(value: EntityAccessMode): String =
    value.toString.flatMap {
      case c if c.isUpper => "-" + c.toLower
      case c => c.toString
    }.stripPrefix("-")

  private def _role_for(
    record: Record
  )(using ctx: ExecutionContext): Option[String] = {
    _security_attributes(record).flatMap(SecurityAttributes.roleFor(_, _subject.subjectId, _matches_group))
  }

  private def _matches_group(
    groupId: String
  )(using ctx: ExecutionContext): Boolean =
    _subject.hasGroup(groupId)

  private def _has_matching_privilege(
    record: Record
  )(using ctx: ExecutionContext): Boolean =
    _security_attributes(record).map(_.privilegeId.id.value).exists { privilegeId =>
      _subject.hasPrivilege(privilegeId) ||
      _subject.hasCapability(privilegeId) ||
      _subject.securityLevel.contains(SecuritySubject.normalize(privilegeId))
    }

  private def _permission_for(
    record: Record,
    role: String,
    accessKind: String
  ): Boolean =
    _security_attributes(record).exists(_.permissionFor(role, accessKind))

  private def _security_attributes(
    record: Record
  ): Option[SecurityAttributes] =
    SecurityAttributes.fromRecord(record)

  private def _subject(using ctx: ExecutionContext): SecuritySubject =
    SecuritySubject.current
