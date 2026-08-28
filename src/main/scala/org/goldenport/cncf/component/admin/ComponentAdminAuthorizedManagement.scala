package org.goldenport.cncf.component.admin

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, SubjectKind}
import org.goldenport.cncf.security.{OperationAuthorization, OperationAuthorizationRule}

/*
 * Internal, value-only Component Admin authorized-management catalog and
 * admission boundary.  The catalog and the admission request contain facts
 * supplied by their owner; this boundary does not discover operations,
 * inspect Components, invoke actions, or perform I/O.
 *
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] enum ComponentAdminManagementClassification {
  case Management, Ordinary
}

private[cncf] enum ComponentAdminManagementAvailability {
  case Available, Unavailable
}

private[cncf] enum ComponentAdminManagementIdempotency {
  case Required, NotRequired
}

private[cncf] enum ComponentAdminManagementLifecycle {
  case Constructed, Loaded, Active, Stopped, Failed
}

private[cncf] enum ComponentAdminManagementDisposition {
  case Admitted, Forbidden, Conflict, Unavailable, StaleInstance, RetryRequired, IdempotentReplay
}

private[cncf] final case class ComponentAdminManagementActionSelector(
  value: String
)

private[cncf] final case class ComponentAdminManagementOperation(
  selector: ComponentAdminManagementActionSelector,
  classification: ComponentAdminManagementClassification,
  authorization: OperationAuthorizationRule,
  allowedlifecycle: Set[ComponentAdminManagementLifecycle],
  availability: ComponentAdminManagementAvailability,
  idempotency: ComponentAdminManagementIdempotency,
  visible: Boolean,
  readonly: Boolean
)

private[cncf] final case class ComponentAdminManagementTarget(
  componentid: ComponentId,
  loadedinstanceid: ComponentInstanceId,
  lifecycle: ComponentAdminManagementLifecycle
)

private[cncf] final case class ComponentAdminAuthorizedManagementCatalog(
  target: ComponentAdminManagementTarget,
  inventory: Vector[ComponentAdminManagementOperation]
) {
  def managementInventory: Vector[ComponentAdminManagementOperation] =
    inventory.filter(_.classification == ComponentAdminManagementClassification.Management)

  def ordinaryInventory: Vector[ComponentAdminManagementOperation] =
    inventory.filter(_.classification == ComponentAdminManagementClassification.Ordinary)
}

private[cncf] final case class ComponentAdminManagementRequest(
  componentid: ComponentId,
  loadedinstanceid: ComponentInstanceId,
  lifecycle: ComponentAdminManagementLifecycle,
  selector: ComponentAdminManagementActionSelector,
  idempotencykey: String
)

private[cncf] final case class ComponentAdminManagementAudit(
  componentid: ComponentId,
  loadedinstanceid: ComponentInstanceId,
  selector: ComponentAdminManagementActionSelector,
  principalid: PrincipalId,
  subjectkind: SubjectKind,
  lifecycle: ComponentAdminManagementLifecycle,
  idempotencykey: String,
  disposition: ComponentAdminManagementDisposition
)

private[cncf] sealed trait ComponentAdminManagementOutcome {
  def audit: ComponentAdminManagementAudit
}

private[cncf] final case class Admitted(
  audit: ComponentAdminManagementAudit
) extends ComponentAdminManagementOutcome

private[cncf] final case class Forbidden(
  audit: ComponentAdminManagementAudit
) extends ComponentAdminManagementOutcome

private[cncf] final case class Conflict(
  audit: ComponentAdminManagementAudit
) extends ComponentAdminManagementOutcome

private[cncf] final case class Unavailable(
  audit: ComponentAdminManagementAudit
) extends ComponentAdminManagementOutcome

private[cncf] final case class StaleInstance(
  audit: ComponentAdminManagementAudit
) extends ComponentAdminManagementOutcome

private[cncf] final case class RetryRequired(
  audit: ComponentAdminManagementAudit
) extends ComponentAdminManagementOutcome

private[cncf] final case class IdempotentReplay(
  audit: ComponentAdminManagementAudit
) extends ComponentAdminManagementOutcome

private[cncf] object ComponentAdminAuthorizedManagement {
  def createC(
    target: ComponentAdminManagementTarget,
    inventory: Vector[ComponentAdminManagementOperation]
  ): Consequence[ComponentAdminAuthorizedManagementCatalog] =
    _validate_catalog(target, inventory).fold(
      Consequence.argumentInvalid,
      value => Consequence.success(ComponentAdminAuthorizedManagementCatalog(target, inventory))
    )

  def admit(
    catalog: ComponentAdminAuthorizedManagementCatalog,
    request: ComponentAdminManagementRequest,
    acceptedAudits: Vector[ComponentAdminManagementAudit] = Vector.empty
  )(using context: ExecutionContext): ComponentAdminManagementOutcome = {
    val principalid = Option(context)
      .flatMap(value => Option(value.security))
      .flatMap(value => Option(value.principal))
      .map(_.id)
      .orNull
    val subjectkind = Option(context)
      .flatMap(value => Option(value.security))
      .map(_.subjectKind)
      .orNull
    val requestvalue = Option(request)
    val componentid = requestvalue.map(_.componentid).orNull
    val loadedinstanceid = requestvalue.map(_.loadedinstanceid).orNull
    val selector = requestvalue.map(_.selector).orNull
    val idempotencykey = requestvalue.map(_.idempotencykey).orNull
    val lifecycle = requestvalue.map(_.lifecycle).orNull
    val _audit_ = (disposition: ComponentAdminManagementDisposition) =>
      ComponentAdminManagementAudit(
        componentid,
        loadedinstanceid,
        selector,
        principalid,
        subjectkind,
        lifecycle,
        idempotencykey,
        disposition
      )

    if (request == null || catalog == null || catalog.target == null) {
      _outcome(ComponentAdminManagementDisposition.RetryRequired, _audit_)
    } else if (
      request.componentid == null ||
      catalog.target.componentid == null ||
      request.componentid != catalog.target.componentid
    ) {
      _outcome(ComponentAdminManagementDisposition.Conflict, _audit_)
    } else if (
      request.loadedinstanceid == null ||
      catalog.target.loadedinstanceid == null ||
      request.loadedinstanceid != catalog.target.loadedinstanceid
    ) {
      _outcome(ComponentAdminManagementDisposition.StaleInstance, _audit_)
    } else if (
      request.lifecycle == null ||
      catalog.target.lifecycle == null ||
      request.lifecycle != catalog.target.lifecycle
    ) {
      _outcome(ComponentAdminManagementDisposition.Conflict, _audit_)
    } else if (request.selector == null) {
      _outcome(ComponentAdminManagementDisposition.Unavailable, _audit_)
    } else {
      val operation = Option(catalog.inventory)
        .flatMap(_.find(_.selector == request.selector))
      operation match {
        case None =>
          _outcome(ComponentAdminManagementDisposition.Unavailable, _audit_)
        case Some(value) if value.classification != ComponentAdminManagementClassification.Management =>
          _outcome(ComponentAdminManagementDisposition.Forbidden, _audit_)
        case Some(value) if value.availability != ComponentAdminManagementAvailability.Available =>
          _outcome(ComponentAdminManagementDisposition.Unavailable, _audit_)
        case Some(value) if !Option(value.allowedlifecycle).exists(_.contains(request.lifecycle)) =>
          _outcome(ComponentAdminManagementDisposition.Conflict, _audit_)
        case Some(value) if value.idempotency != ComponentAdminManagementIdempotency.Required || value.readonly =>
          _outcome(ComponentAdminManagementDisposition.Conflict, _audit_)
        case Some(value) if !_valid_idempotency_key(request.idempotencykey) =>
          _outcome(ComponentAdminManagementDisposition.RetryRequired, _audit_)
        case Some(_) if _accepted_replay(acceptedAudits, _audit_(ComponentAdminManagementDisposition.Admitted)) =>
          _outcome(ComponentAdminManagementDisposition.IdempotentReplay, _audit_)
        case Some(value) if !OperationAuthorization.authorize(value.selector.value, value.authorization).isSuccess =>
          _outcome(ComponentAdminManagementDisposition.Forbidden, _audit_)
        case Some(_) =>
          _outcome(ComponentAdminManagementDisposition.Admitted, _audit_)
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
  ): ComponentAdminManagementOutcome = {
    val audit = auditfn(disposition)
    disposition match {
      case ComponentAdminManagementDisposition.Admitted => Admitted(audit)
      case ComponentAdminManagementDisposition.Forbidden => Forbidden(audit)
      case ComponentAdminManagementDisposition.Conflict => Conflict(audit)
      case ComponentAdminManagementDisposition.Unavailable => Unavailable(audit)
      case ComponentAdminManagementDisposition.StaleInstance => StaleInstance(audit)
      case ComponentAdminManagementDisposition.RetryRequired => RetryRequired(audit)
      case ComponentAdminManagementDisposition.IdempotentReplay => IdempotentReplay(audit)
    }
  }

  private def _accepted_replay(
    audits: Vector[ComponentAdminManagementAudit],
    requested: ComponentAdminManagementAudit
  ): Boolean =
    Option(audits).toVector
      .flatMap(_.iterator)
      .exists { audit =>
        audit != null &&
        audit.disposition == ComponentAdminManagementDisposition.Admitted &&
        audit.componentid == requested.componentid &&
        audit.loadedinstanceid == requested.loadedinstanceid &&
        audit.selector == requested.selector &&
        audit.principalid == requested.principalid &&
        audit.lifecycle == requested.lifecycle &&
        audit.idempotencykey == requested.idempotencykey
      }

  private def _valid_idempotency_key(value: String): Boolean =
    value != null && value.trim.nonEmpty && !value.exists(_.isControl)

  private def _validate_catalog(
    target: ComponentAdminManagementTarget,
    inventory: Vector[ComponentAdminManagementOperation]
  ): Either[String, Unit] =
    for {
      _ <- _validate_target(target)
      _ <- Either.cond(inventory != null, (), "Component Admin operation inventory is required")
      _ <- _validate_inventory(target, inventory)
      _ <- Either.cond(
        inventory.map(_.selector).distinct.size == inventory.size,
        (),
        "Component Admin operation inventory must not contain duplicate selectors"
      )
    } yield ()

  private def _validate_target(
    target: ComponentAdminManagementTarget
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(target != null, (), "Component Admin management target is required")
      _ <- Either.cond(target.componentid != null, (), "Component Admin target Component identity is required")
      _ <- Either.cond(target.loadedinstanceid != null, (), "Component Admin target loaded instance is required")
      _ <- Either.cond(target.lifecycle != null, (), "Component Admin target lifecycle is required")
      _ <- Either.cond(
        target.loadedinstanceid == null || target.componentid == null ||
          target.loadedinstanceid.componentId == target.componentid,
        (),
        "Component Admin target loaded instance must belong to the selected Component"
      )
    } yield ()

  private def _validate_inventory(
    target: ComponentAdminManagementTarget,
    inventory: Vector[ComponentAdminManagementOperation]
  ): Either[String, Unit] =
    inventory.foldLeft[Either[String, Unit]](Right(())) { (result, operation) =>
      result.flatMap(_ => _validate_operation(target, operation))
    }

  private def _validate_operation(
    target: ComponentAdminManagementTarget,
    operation: ComponentAdminManagementOperation
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(operation != null, (), "Component Admin operation inventory entry is required")
      _ <- Either.cond(operation.selector != null, (), "Component Admin operation selector is required")
      _ <- Either.cond(
        operation.selector == null || _valid_selector(operation.selector.value),
        (),
        "Component Admin operation selector must be exact nonblank text"
      )
      _ <- Either.cond(operation.classification != null, (), "Component Admin operation classification is required")
      _ <- Either.cond(operation.authorization != null, (), "Component Admin operation authorization rule is required")
      _ <- Either.cond(operation.allowedlifecycle != null, (), "Component Admin operation lifecycle allowance is required")
      _ <- Either.cond(operation.availability != null, (), "Component Admin operation availability is required")
      _ <- Either.cond(operation.idempotency != null, (), "Component Admin operation idempotency requirement is required")
      _ <- Either.cond(
        operation.classification != ComponentAdminManagementClassification.Management || !operation.readonly,
        (),
        "Component Admin management operation must not be read-only"
      )
      _ <- Either.cond(
        operation.classification != ComponentAdminManagementClassification.Ordinary || operation.readonly,
        (),
        "Component Admin ordinary operation must be read-only"
      )
      _ <- Either.cond(
        operation.classification != ComponentAdminManagementClassification.Management ||
          operation.idempotency == ComponentAdminManagementIdempotency.Required,
        (),
        "Component Admin management operation must require idempotency"
      )
    } yield ()

  private def _valid_selector(value: String): Boolean =
    value != null && value.trim.nonEmpty && !value.exists(_.isControl)
}
