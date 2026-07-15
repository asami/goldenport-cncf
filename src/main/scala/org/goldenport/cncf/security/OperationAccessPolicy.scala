package org.goldenport.cncf.security

import java.time.Instant
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.goldenport.observation.Cause
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Apr.  6, 2026
 *  version Apr. 29, 2026
 *  version May. 11, 2026
 * @version Jul. 16, 2026
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
          _permission_denied("Owner or manager privilege is required.", "owner-or-manager")
        case None =>
          _permission_denied("Owner information is not available for authorization.", "owner-missing")
      }

  def authorizeManagerOnly()(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else
      _permission_denied("Management privilege is required.", "manager-only")

  def authorizeSimpleEntityOwnerOrManager(
    entityid: EntityId,
    loadrecord: EntityId => Consequence[Option[Record]]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else if (entityid.print == _subject.subjectId)
      Consequence.unit
    else
      loadrecord(entityid).flatMap {
        case Some(record) => authorizeOwnerOrManager(record)
        case None => Consequence.entityNotFound(s"SimpleEntity not found: ${entityid.print}")
      }

  def hasManagerPrivilege(using ctx: ExecutionContext): Boolean =
    _is_manager

  def authorizeSimpleEntity(
    record: Record,
    accesskind: String
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _authorize_simple_entity(record, accesskind, None)

  def authorizeSimpleEntity(
    record: Record,
    accesskind: String,
    securityattributes: Option[SecurityAttributes]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _authorize_simple_entity(record, accesskind, securityattributes)

  private def _authorize_simple_entity(
    record: Record,
    accesskind: String,
    securityattributes: Option[SecurityAttributes]
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else if (_has_matching_privilege(record, securityattributes))
      Consequence.unit
    else
      _role_for(record, securityattributes) match
        case Some("owner") if _permission_for(record, "owner", accesskind, securityattributes) => Consequence.unit
        case Some("group") if _permission_for(record, "group", accesskind, securityattributes) => Consequence.unit
        case Some("other") if _permission_for(record, "other", accesskind, securityattributes) => Consequence.unit
        case Some("owner") => _permission_denied(s"Owner permission is insufficient for $accesskind.", "owner", accesskind)
        case Some("group") => _permission_denied(s"Group permission is insufficient for $accesskind.", "group", accesskind)
        case Some("other") => _permission_denied(s"Permission is insufficient for $accesskind.", "other", accesskind)
        case Some(role) => _permission_denied(s"Permission is insufficient for $accesskind.", role, accesskind)
        case None => _permission_denied("Security attributes are not available for authorization.", "security-attributes-missing", accesskind)

  def authorizeUnitOfWorkDefault(
    authorization: UnitOfWorkAuthorization,
    loadrecord: EntityId => Consequence[Option[Record]] = _ => Consequence.success(None)
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _with_decision_event(authorization, "unit-of-work") {
      authorization.accessMode match
      case EntityAccessMode.ServiceInternal if _is_cross_component(authorization) && !_subject.hasServiceGrant(authorization.sourceComponentName, authorization.targetComponentName) =>
        _cross_component_grant_required(authorization)
      case EntityAccessMode.System | EntityAccessMode.ServiceInternal =>
        _emit_permission_bypass(authorization, "unit-of-work")
        Consequence.unit
      case EntityAccessMode.UserPermission =>
        authorization.access.flatMap(a => Option(a.policy).map(_.trim.toLowerCase(java.util.Locale.ROOT))) match
          case Some(policy) if Set("manager_only", "manager-only").contains(policy) =>
            authorizeManagerOnly()
          case _ =>
            _authorize_resource_default(authorization, loadrecord)
    }

  def filterVisibleSearchResult[T](
    authorization: UnitOfWorkAuthorization,
    result: SearchResult[T],
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Consequence[SearchResult[T]] =
    _with_decision_event(authorization, "search/list") {
      authorization.accessMode match
      case EntityAccessMode.ServiceInternal if _is_cross_component(authorization) && !_subject.hasServiceGrant(authorization.sourceComponentName, authorization.targetComponentName) =>
        _cross_component_grant_required(authorization)
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
                val evaluatedat = ctx.clock.instant()
                val visibility = result.data.map(_visibility_evaluation(_, tc, authorization, evaluatedat))
                val visible = visibility.collect {
                  case (entity, true, _) => entity
                }
                _emit_abac_filter_diagnostics(authorization, visibility)
                Consequence.success(
                  result.copy(
                    data = visible,
                    totalCount = result.totalCount.map(_ => visible.size),
                    fetchedCount = visible.size
                  )
                )
              case _ =>
                Consequence.success(result)
    }

  private def _permission_denied[A](
    message: String,
    reason: String
  )(using ctx: ExecutionContext): Consequence[A] =
    _permission_denied(message, reason, "unknown")

  private def _permission_denied[A](
    message: String,
    reason: String,
    accesskind: String
  )(using ctx: ExecutionContext): Consequence[A] = {
    val (kind, diagnostic) = _authorization_diagnostic(reason)
    Consequence.securityPermissionDenied(
      message,
      kind,
      Seq(
        Descriptor.Facet.Reason(reason),
        diagnostic,
        Descriptor.Facet.Parameter.argument("access-kind"),
        Descriptor.Facet.Value(accesskind),
        Descriptor.Facet.Id(_subject.subjectId)
      )
    )
  }

  private def _authorization_diagnostic(reason: String): (Cause.Kind, Descriptor.Facet) =
    reason match {
      case "required-capability" =>
        Cause.Kind.Capability -> Descriptor.Facet.Capability("required-capability")
      case "cross-component-service-grant" =>
        Cause.Kind.Guard -> Descriptor.Facet.Guard("cross-component-service-grant")
      case "abac-condition" =>
        Cause.Kind.Guard -> Descriptor.Facet.Guard("abac-condition")
      case "manager-only" | "owner-or-manager" =>
        Cause.Kind.Guard -> Descriptor.Facet.Guard(reason)
      case "relation" =>
        Cause.Kind.Relation -> Descriptor.Facet.Relation(reason)
      case "owner" | "group" | "other" | "security-attributes-missing" | "owner-missing" =>
        Cause.Kind.Permission -> Descriptor.Facet.Permission(reason)
      case other =>
        Cause.Kind.Permission -> Descriptor.Facet.Permission(other)
    }

  private def _cross_component_grant_required[A](
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Consequence[A] =
    _permission_denied(
      s"Cross-component service grant is required: ${authorization.sourceComponentName.getOrElse("<unknown>")} -> ${authorization.targetComponentName.getOrElse("<unknown>")}.",
      "cross-component-service-grant",
      authorization.accessKind
    )

  private def _is_manager(using ctx: ExecutionContext): Boolean = {
    (_subject.roles ++ _subject.capabilities ++ _subject.securityLevel).exists(_manager_aliases.contains)
  }

  private def _authorize_resource_default(
    authorization: UnitOfWorkAuthorization,
    loadrecord: EntityId => Consequence[Option[Record]]
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val evaluatedat = ctx.clock.instant()
    authorization.resourceFamily.trim.toLowerCase(java.util.Locale.ROOT) match
      case "domain" | "aggregate" =>
        _authorize_declared_aggregate_command(authorization).getOrElse(authorization.accessKind match
          case "create" =>
            _authorize_policy_capability(authorization).flatMap(_ => _authorize_domain_create_default(authorization))
          case kind if Set("read", "update", "delete").contains(_permission_access_kind(kind)) =>
            _authorize_policy_capability(authorization).flatMap { _ =>
              authorization.targetId match
              case Some(id) =>
                loadrecord(id).flatMap {
                  case Some(record) =>
                    _natural_condition_miss(record, authorization, evaluatedat) match
                      case Some(miss) =>
                        _permission_denied(miss.message, "abac-condition", authorization.accessKind)
                      case None if (authorization.accessKind == "read" && _is_public_policy(authorization)) =>
                        Consequence.unit
                      case None if _matches_relation(record, authorization) =>
                        Consequence.unit
                      case None =>
                        authorizeSimpleEntity(record, _policy_permission_access_kind(authorization))
                  case None => authorizeSimpleEntityOwnerOrManager(id, loadrecord)
                }
              case None =>
                Consequence.unit
            }
          case _ =>
            Consequence.unit
        )
      case "association" =>
        _authorize_policy_capability(authorization)
      case "store" =>
        _authorize_policy_capability(authorization)
      case _ =>
        Consequence.unit
  }

  private def _authorize_declared_aggregate_command(
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Option[Consequence[Unit]] = {
    val isaggregate = authorization.resourceFamily.trim.equalsIgnoreCase("aggregate")
    val iscommand = authorization.accessKind.trim.toLowerCase(java.util.Locale.ROOT).startsWith("command:")
    if (!isaggregate || !iscommand)
      None
    else
      authorization.access.flatMap(a => Option(a.policy).map(_.trim.toLowerCase(java.util.Locale.ROOT))) match
        case Some("authenticated_only") | Some("authenticated-only") =>
          Some(
            if (_subject.isAuthenticated)
              _authorize_policy_capability(authorization)
            else
              Consequence.securityAuthenticationRequired(
                s"Authenticated user is required: ${authorization.accessKind}"
              )
          )
        case Some("public") =>
          Some(_authorize_policy_capability(authorization))
        case _ =>
          None
  }

  private def _permission_access_kind(accesskind: String): String = {
    val normalized = accesskind.trim.toLowerCase(java.util.Locale.ROOT)
    if (normalized.startsWith("command:"))
      "update"
    else if (normalized.startsWith("create:"))
      "create"
    else if (Set("search", "list", "search/list").contains(normalized))
      "read"
    else
      normalized
  }

  private def _authorize_domain_create_default(
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (_is_manager)
      Consequence.unit
    else if (_subject.hasCreateGrant(authorization.resourceType, authorization.collectionName))
      Consequence.unit
    else
      Consequence.unit

  private def _authorize_policy_capability(
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _resource_policy(authorization).filter(_.capabilities.nonEmpty) match {
      case Some(policy) if policy.normalizedCapabilities.exists(_subject.capabilities.contains) =>
        Consequence.unit
      case Some(policy) =>
        _permission_denied(
          s"Required capability is missing: ${policy.capabilities.mkString("|")}.",
          "required-capability",
          authorization.accessKind
        )
      case None =>
        Consequence.unit
    }

  private def _policy_permission_access_kind(
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): String =
    _resource_policy(authorization).flatMap(_.permission).map(_.trim.toLowerCase(java.util.Locale.ROOT))
      .filter(_.nonEmpty)
      .getOrElse(_permission_access_kind(authorization.accessKind))

  private def _resource_policy(
    authorization: UnitOfWorkAuthorization
  )(using ctx: ExecutionContext): Option[AuthorizationResourcePolicy] = {
    val action = authorization.accessKind
    _resource_policies.flatMap { policies =>
      authorization.resourceFamily.trim.toLowerCase(java.util.Locale.ROOT) match
        case "domain" | "aggregate" =>
          val name = authorization.collectionName.orElse(authorization.resourceType)
          policies.collection(name, action)
        case "association" =>
          val name = authorization.resourceType.orElse(authorization.collectionName)
          policies.association(name, action)
        case "store" =>
          val name = authorization.resourceType.orElse(authorization.collectionName)
          policies.store(name, action)
        case _ =>
          None
    }
  }

  private def _resource_policies(
    using ctx: ExecutionContext
  ): Option[AuthorizationResourcePolicies] =
    _subsystem_from_scope(ctx.cncfCore.scope)
      .flatMap(_.descriptor)
      .flatMap(_.security)
      .flatMap(_.authorization)
      .map(_.resources)

  @annotation.tailrec
  private def _subsystem_from_scope(
    scope: org.goldenport.cncf.context.ScopeContext
  ): Option[Subsystem] =
    scope match {
      case cc: Component.Context =>
        cc.component.subsystem
      case other =>
        other.parent match {
          case Some(parent) => _subsystem_from_scope(parent)
          case None => None
        }
    }

  private def _visibility_evaluation[T](
    entity: T,
    tc: EntityPersistent[T],
    authorization: UnitOfWorkAuthorization,
    evaluatedat: Instant
  )(using ctx: ExecutionContext): (T, Boolean, Option[EntityAbacCondition.Evaluation]) = {
    val record = tc.authorizationRecord(entity)
    val securityattributes = tc.securityAttributes(entity)
    _natural_condition_miss(record, authorization, evaluatedat) match
      case Some(miss) =>
        (entity, false, Some(miss))
      case None if _is_public_policy(authorization) =>
        (entity, true, None)
      case None if _matches_relation(record, authorization) =>
        (entity, true, None)
      case None =>
        _authorize_visible_record(record, tc.id(entity), securityattributes) match
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
    val diagnostic = _diagnostic(result)
    RuntimeDashboardMetrics.recordAuthorizationDecision(
      outcome == "deny",
      diagnostic.map(_.diagnosticKey),
      diagnostic.map(_.toRecord)
    )
    val _ = ctx.observability.emitInfo(
      ctx.cncfCore.scope,
      "authorization.decision",
      Record.dataAuto(
        "surface" -> surface,
        "outcome" -> outcome,
        "diagnostic" -> diagnostic.map(_.toRecord),
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

  private def _diagnostic[A](
    result: Consequence[A]
  ): Option[ConclusionDiagnostics.Classification] =
    result match {
      case Consequence.Failure(conclusion) => Some(ConclusionDiagnostics.classify(conclusion))
      case _ => None
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
    authorization: UnitOfWorkAuthorization,
    evaluatedat: Instant
  )(using ctx: ExecutionContext): Option[EntityAbacCondition.Evaluation] = {
    val evaluations = _natural_condition_evaluations(record, authorization, evaluatedat)
    _emit_abac_diagnostics(authorization, evaluations)
    evaluations.collectFirst {
      case (_, true, evaluation) if !evaluation.matched => evaluation
    }
  }

  private def _natural_condition_evaluations(
    record: Record,
    authorization: UnitOfWorkAuthorization,
    evaluatedat: Instant
  )(using ctx: ExecutionContext): Vector[(EntityAbacCondition, Boolean, EntityAbacCondition.Evaluation)] = {
    val context = EntityAuthorizationContext(record, authorization, evaluatedat)
    authorization.naturalConditions.map { condition =>
      val applicable = condition.allows(authorization.accessKind)
      (condition, applicable, condition.evaluate(context))
    }
  }

  private def _emit_abac_diagnostics(
    authorization: UnitOfWorkAuthorization,
    evaluations: Vector[(EntityAbacCondition, Boolean, EntityAbacCondition.Evaluation)]
  )(using ctx: ExecutionContext): Unit =
    if (evaluations.nonEmpty) {
      val details = evaluations.map {
        case (_, applicable, evaluation) =>
          val actual = evaluation.actual.getOrElse("<missing>")
          val expected = evaluation.expected.getOrElse("<missing>")
          s"${evaluation.conditionText}:${if (applicable) "applicable" else "not-applicable"}:${if (evaluation.matched) "matched" else "missed"}:actual=${actual}:expected=${expected}"
      }
      val applicableevaluations = evaluations.filter(_._2)
      val _ = ctx.observability.emitInfo(
        ctx.cncfCore.scope,
        "authorization.abac.diagnostics",
        Record.dataAuto(
          "resource-family" -> authorization.resourceFamily,
          "resource-type" -> authorization.resourceType,
          "collection" -> authorization.collectionName,
          "target-id" -> authorization.targetId.map(_.print),
          "access-kind" -> authorization.accessKind,
          "match-count" -> applicableevaluations.count(_._3.matched),
          "miss-count" -> applicableevaluations.count(x => !x._3.matched),
          "not-applicable-count" -> evaluations.count(x => !x._2),
          "details" -> details.mkString(";")
        )
      )
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
      val _ = ctx.observability.emitInfo(
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
    record: Record,
    securityattributes: Option[SecurityAttributes] = None
  )(using ctx: ExecutionContext): Option[String] = {
    _security_attributes(record, securityattributes).flatMap(SecurityAttributes.roleFor(_, _subject.subjectId, _matches_group))
  }

  private def _matches_group(
    groupid: String
  )(using ctx: ExecutionContext): Boolean =
    _subject.hasGroup(groupid)

  private def _has_matching_privilege(
    record: Record,
    securityattributes: Option[SecurityAttributes] = None
  )(using ctx: ExecutionContext): Boolean =
    _security_attributes(record, securityattributes).map(_.privilegeId.id.value).exists { privilegeid =>
      _subject.hasPrivilege(privilegeid) ||
      _subject.hasCapability(privilegeid) ||
      _subject.securityLevel.contains(SecuritySubject.normalize(privilegeid))
    }

  private def _permission_for(
    record: Record,
    role: String,
    accesskind: String,
    securityattributes: Option[SecurityAttributes] = None
  ): Boolean =
    _security_attributes(record, securityattributes).exists(_.permissionFor(role, accesskind))

  private def _authorize_visible_record(
    record: Record,
    id: EntityId,
    securityattributes: Option[SecurityAttributes] = None
  )(using ctx: ExecutionContext): Consequence[Unit] =
    OperationAccessPolicy.authorizeSimpleEntity(record, "read", securityattributes) match
      case s @ Consequence.Success(_) => s
      case _ if _security_attributes(record, securityattributes).isEmpty =>
        _load_raw_record(id).flatMap {
          case Some(raw) => OperationAccessPolicy.authorizeSimpleEntity(raw, "read")
          case None => Consequence.securityPermissionDenied(
            "Security attributes are not available for authorization.",
            Cause.Kind.Permission,
            Seq(
              Descriptor.Facet.Reason("security-attributes-missing"),
              Descriptor.Facet.Permission("security-attributes-missing"),
              Descriptor.Facet.Parameter.argument("access-kind"),
              Descriptor.Facet.Value("read"),
              Descriptor.Facet.Id(_subject.subjectId)
            )
          )
        }
      case f => f

  private def _load_raw_record(
    id: EntityId
  )(using ctx: ExecutionContext): Consequence[Option[Record]] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      r <- ds.load(cid, dsid)
    } yield r

  private def _security_attributes(
    record: Record,
    preferred: Option[SecurityAttributes] = None
  ): Option[SecurityAttributes] =
    preferred.orElse(SimpleEntityStorageShapePolicy.securityAttributesFromRecord(record))

  private def _subject(using ctx: ExecutionContext): SecuritySubject =
    SecuritySubject.current
