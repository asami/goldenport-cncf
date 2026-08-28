package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, SubjectKind}
import org.goldenport.cncf.security.{OperationAuthorization, OperationAuthorizationRule}

/*
 * Internal, value-only Component Admin authorized-management catalog and
 * admission boundary. The Component-owned management action registration and
 * admission request contain already-resolved facts; this boundary does not
 * discover operations, inspect Components, invoke actions, or perform I/O.
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] enum ComponentAdminManagementAvailability {
  case Available, Unavailable
}

private[cncf] enum ComponentAdminManagementDisposition {
  case Admitted, Forbidden, Conflict, Unavailable, StaleInstance, RetryRequired, IdempotentReplay
}

private[cncf] final case class ComponentAdminManagementActionSelector(value: String)

private[cncf] final case class ComponentAdminManagementActionRegistration(
  selector: ComponentAdminManagementActionSelector,
  authorization: OperationAuthorizationRule,
  availability: ComponentAdminManagementAvailability,
  visible: Boolean
)

private[cncf] final case class ComponentAdminOrdinaryQuery(
  selector: ComponentAdminManagementActionSelector,
  visible: Boolean
)

private[cncf] final case class ComponentAdminManagementActionInput(
  selector: ComponentAdminManagementActionSelector,
  representation: String,
  validated: Boolean,
  provenance: ComponentAdminSafeProvenance
)

private[cncf] final case class ComponentAdminManagementTarget(
  componentid: ComponentId,
  loadedinstanceid: ComponentInstanceId,
  identitybinding: ComponentAdminRuntimeIdentityBinding,
  lifecycle: ComponentAdminLifecycleEvidence
)

private[cncf] final case class ComponentAdminAuthorizedManagementCatalog(
  target: ComponentAdminManagementTarget,
  managementInventory: Vector[ComponentAdminManagementActionRegistration],
  ordinaryInventory: Vector[ComponentAdminOrdinaryQuery]
)

private[cncf] final case class ComponentAdminManagementRequest(
  componentid: ComponentId,
  loadedinstanceid: ComponentInstanceId,
  identitybinding: ComponentAdminRuntimeIdentityBinding,
  lifecycle: ComponentAdminLifecycleEvidence,
  selector: ComponentAdminManagementActionSelector,
  input: ComponentAdminManagementActionInput,
  idempotencykey: String
)

private[cncf] final case class ComponentAdminManagementAudit(
  componentid: ComponentId,
  loadedinstanceid: ComponentInstanceId,
  identitybinding: ComponentAdminRuntimeIdentityBinding,
  lifecycle: ComponentAdminLifecycleEvidence,
  selector: ComponentAdminManagementActionSelector,
  input: ComponentAdminManagementActionInput,
  principalid: PrincipalId,
  subjectkind: SubjectKind,
  idempotencykey: String,
  disposition: ComponentAdminManagementDisposition
)

private[cncf] sealed trait ComponentAdminManagementOutcome {
  def audit: ComponentAdminManagementAudit
}
private[cncf] final case class Admitted(audit: ComponentAdminManagementAudit) extends ComponentAdminManagementOutcome
private[cncf] final case class Forbidden(audit: ComponentAdminManagementAudit) extends ComponentAdminManagementOutcome
private[cncf] final case class Conflict(audit: ComponentAdminManagementAudit) extends ComponentAdminManagementOutcome
private[cncf] final case class Unavailable(audit: ComponentAdminManagementAudit) extends ComponentAdminManagementOutcome
private[cncf] final case class StaleInstance(audit: ComponentAdminManagementAudit) extends ComponentAdminManagementOutcome
private[cncf] final case class RetryRequired(audit: ComponentAdminManagementAudit) extends ComponentAdminManagementOutcome
private[cncf] final case class IdempotentReplay(audit: ComponentAdminManagementAudit) extends ComponentAdminManagementOutcome

private[cncf] object ComponentAdminAuthorizedManagement {
  private val _safe_logical_text_pattern = "[A-Za-z0-9][A-Za-z0-9._:-]*".r
  private val _registered_management_selectors = Vector(
    "entity/create",
    "entity/update",
    "data/create",
    "data/update",
    "association/admin_attach_association",
    "association/admin_detach_association"
  )

  def createC(
    target: ComponentAdminManagementTarget,
    registrations: Vector[ComponentAdminManagementActionRegistration],
    ordinaryqueries: Vector[ComponentAdminOrdinaryQuery] = Vector.empty
  ): Consequence[ComponentAdminAuthorizedManagementCatalog] =
    _validate_catalog(target, registrations, ordinaryqueries).fold(
      Consequence.argumentInvalid,
      _ => Consequence.success(ComponentAdminAuthorizedManagementCatalog(target, registrations, ordinaryqueries))
    )

  def admit(
    catalog: ComponentAdminAuthorizedManagementCatalog,
    request: ComponentAdminManagementRequest,
    acceptedAudits: Vector[ComponentAdminManagementAudit] = Vector.empty
  )(using context: ExecutionContext): ComponentAdminManagementOutcome = {
    val principalid = Option(context).flatMap(x => Option(x.security)).flatMap(x => Option(x.principal)).map(_.id).orNull
    val subjectkind = Option(context).flatMap(x => Option(x.security)).map(_.subjectKind).orNull
    val requestvalue = Option(request)
    val _audit_ = (disposition: ComponentAdminManagementDisposition) => ComponentAdminManagementAudit(
      requestvalue.map(_.componentid).orNull,
      requestvalue.map(_.loadedinstanceid).orNull,
      requestvalue.map(_.identitybinding).orNull,
      requestvalue.map(_.lifecycle).orNull,
      requestvalue.map(_.selector).orNull,
      requestvalue.map(_.input).orNull,
      principalid,
      subjectkind,
      requestvalue.map(_.idempotencykey).orNull,
      disposition
    )

    if (catalog == null || request == null || !_valid_target(catalog.target) || !_valid_request_target(request))
      _outcome(ComponentAdminManagementDisposition.RetryRequired, _audit_)
    else if (request.componentid != catalog.target.componentid)
      _outcome(ComponentAdminManagementDisposition.Conflict, _audit_)
    else if (request.loadedinstanceid != catalog.target.loadedinstanceid)
      _outcome(ComponentAdminManagementDisposition.StaleInstance, _audit_)
    else if (request.identitybinding != catalog.target.identitybinding || request.lifecycle != catalog.target.lifecycle)
      _outcome(ComponentAdminManagementDisposition.Conflict, _audit_)
    else if (!_valid_input(request.input, request.selector) || !_valid_idempotency_key(request.idempotencykey))
      _outcome(ComponentAdminManagementDisposition.RetryRequired, _audit_)
    else {
      Option(catalog.managementInventory).flatMap(_.find(_.selector == request.selector)) match {
        case Some(value) if !OperationAuthorization.authorize(value.selector.value, value.authorization).isSuccess =>
          _outcome(ComponentAdminManagementDisposition.Forbidden, _audit_)
        case Some(value) if value.availability != ComponentAdminManagementAvailability.Available =>
          _outcome(ComponentAdminManagementDisposition.Unavailable, _audit_)
        case Some(_) if request.lifecycle.state != ComponentAdminLifecycleState.Active =>
          _outcome(ComponentAdminManagementDisposition.Conflict, _audit_)
        case Some(_) if _accepted_replay(acceptedAudits, _audit_(ComponentAdminManagementDisposition.Admitted)) =>
          _outcome(ComponentAdminManagementDisposition.IdempotentReplay, _audit_)
        case Some(_) =>
          _outcome(ComponentAdminManagementDisposition.Admitted, _audit_)
        case None if Option(catalog.ordinaryInventory).exists(_.exists(_.selector == request.selector)) =>
          _outcome(ComponentAdminManagementDisposition.Forbidden, _audit_)
        case None =>
          _outcome(ComponentAdminManagementDisposition.Unavailable, _audit_)
      }
    }
  }

  def admitC(
    catalog: ComponentAdminAuthorizedManagementCatalog,
    request: ComponentAdminManagementRequest,
    acceptedAudits: Vector[ComponentAdminManagementAudit] = Vector.empty
  )(using context: ExecutionContext): ComponentAdminManagementOutcome =
    admit(catalog, request, acceptedAudits)

  private def _outcome(
    disposition: ComponentAdminManagementDisposition,
    auditfn: ComponentAdminManagementDisposition => ComponentAdminManagementAudit
  ): ComponentAdminManagementOutcome =
    disposition match {
      case ComponentAdminManagementDisposition.Admitted => Admitted(auditfn(disposition))
      case ComponentAdminManagementDisposition.Forbidden => Forbidden(auditfn(disposition))
      case ComponentAdminManagementDisposition.Conflict => Conflict(auditfn(disposition))
      case ComponentAdminManagementDisposition.Unavailable => Unavailable(auditfn(disposition))
      case ComponentAdminManagementDisposition.StaleInstance => StaleInstance(auditfn(disposition))
      case ComponentAdminManagementDisposition.RetryRequired => RetryRequired(auditfn(disposition))
      case ComponentAdminManagementDisposition.IdempotentReplay => IdempotentReplay(auditfn(disposition))
    }

  private def _accepted_replay(audits: Vector[ComponentAdminManagementAudit], requested: ComponentAdminManagementAudit): Boolean =
    Option(audits).getOrElse(Vector.empty).exists { audit =>
      audit != null &&
        audit.disposition == ComponentAdminManagementDisposition.Admitted &&
        audit.componentid == requested.componentid &&
        audit.loadedinstanceid == requested.loadedinstanceid &&
        audit.identitybinding == requested.identitybinding &&
        audit.lifecycle == requested.lifecycle &&
        audit.selector == requested.selector &&
        audit.input == requested.input &&
        audit.principalid == requested.principalid &&
        audit.subjectkind == requested.subjectkind &&
        audit.idempotencykey == requested.idempotencykey
    }

  private def _valid_target(value: ComponentAdminManagementTarget): Boolean =
    value != null &&
      value.componentid != null &&
      value.loadedinstanceid != null &&
      value.loadedinstanceid.componentId == value.componentid &&
      _valid_identity_binding(value.identitybinding) &&
      _identity_belongs_to(value.identitybinding, value.componentid) &&
      _valid_lifecycle(value.lifecycle)

  private def _valid_request_target(value: ComponentAdminManagementRequest): Boolean =
    value.componentid != null &&
      value.loadedinstanceid != null &&
      value.loadedinstanceid.componentId == value.componentid &&
      _valid_identity_binding(value.identitybinding) &&
      _identity_belongs_to(value.identitybinding, value.componentid) &&
      _valid_lifecycle(value.lifecycle) &&
      value.selector != null

  private def _valid_identity_binding(value: ComponentAdminRuntimeIdentityBinding): Boolean =
    value != null &&
      value.selectedlogicalrelease != null &&
      value.selectedlogicalrelease.componentClass != null &&
      value.selectedlogicalrelease.componentClass.componentId != null &&
      _valid_safe_logical_text(value.selectedlogicalrelease.release) &&
      value.subsystemclass != null &&
      _valid_safe_logical_text(value.subsystemclass.name) &&
      value.subsysteminstance != null &&
      value.subsysteminstance.subsystemClass == value.subsystemclass &&
      _valid_safe_logical_text(value.subsysteminstance.instance) &&
      value.implicitcomponentsubsystem != null &&
      value.implicitcomponentsubsystem.componentClass != null &&
      value.implicitcomponentsubsystem.componentClass.componentId != null &&
      _valid_safe_logical_text(value.implicitcomponentsubsystem.name)

  private def _identity_belongs_to(value: ComponentAdminRuntimeIdentityBinding, componentid: ComponentId): Boolean =
    value.selectedlogicalrelease.componentClass.componentId == componentid &&
      value.implicitcomponentsubsystem.componentClass.componentId == componentid

  private def _valid_lifecycle(value: ComponentAdminLifecycleEvidence): Boolean =
    value != null && value.state != null && _valid_provenance(value.provenance) && _valid_text(value.detail)

  private def _valid_input(value: ComponentAdminManagementActionInput, selector: ComponentAdminManagementActionSelector): Boolean =
    value != null &&
      value.selector != null &&
      value.selector == selector &&
      value.validated &&
      _valid_provenance(value.provenance) &&
      _valid_text(value.representation) &&
      value.representation == value.representation.trim

  private def _valid_provenance(value: ComponentAdminSafeProvenance): Boolean =
    value != null && value.sourceKind != null && value.logicalIdentity != null

  private def _valid_idempotency_key(value: String): Boolean = _valid_text(value)
  private def _valid_safe_logical_text(value: String): Boolean = value != null && _safe_logical_text_pattern.matches(value)
  private def _valid_text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.exists(_.isControl)

  private def _validate_catalog(
    target: ComponentAdminManagementTarget,
    registrations: Vector[ComponentAdminManagementActionRegistration],
    ordinaryqueries: Vector[ComponentAdminOrdinaryQuery]
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(_valid_target(target), (), "Component Admin management target is incomplete")
      _ <- Either.cond(registrations != null, (), "Component-owned management action registration is required")
      _ <- Either.cond(ordinaryqueries != null, (), "Component Admin ordinary query inventory is required")
      _ <- _validate_registrations(registrations)
      _ <- _validate_ordinary_queries(ordinaryqueries)
    } yield ()

  private def _validate_registrations(values: Vector[ComponentAdminManagementActionRegistration]): Either[String, Unit] =
    for {
      _ <- Either.cond(values.size == _registered_management_selectors.size, (), "Component-owned management action registration must be finite and complete")
      _ <- Either.cond(values.forall(_valid_registration), (), "Component-owned management action registration is incomplete")
      selectors = values.map(_.selector.value)
      _ <- Either.cond(selectors.distinct.size == selectors.size, (), "Component-owned management action registration must not duplicate selectors")
      _ <- Either.cond(selectors.toSet == _registered_management_selectors.toSet, (), "Component-owned management action registration must not add, omit, or replace selectors")
    } yield ()

  private def _valid_registration(value: ComponentAdminManagementActionRegistration): Boolean =
    value != null && value.selector != null && _valid_text(value.selector.value) && value.authorization != null && value.availability != null

  private def _validate_ordinary_queries(values: Vector[ComponentAdminOrdinaryQuery]): Either[String, Unit] =
    for {
      _ <- Either.cond(values.forall(x => x != null && x.selector != null && _valid_text(x.selector.value)), (), "Component Admin ordinary query inventory is incomplete")
      selectors = values.map(_.selector.value)
      _ <- Either.cond(selectors.distinct.size == selectors.size, (), "Component Admin ordinary query inventory must not duplicate selectors")
      _ <- Either.cond(!selectors.exists(_registered_management_selectors.contains), (), "Visible ordinary queries must remain distinct from Component-owned management actions")
    } yield ()
}
