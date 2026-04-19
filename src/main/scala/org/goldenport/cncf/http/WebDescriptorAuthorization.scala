package org.goldenport.cncf.http

import org.goldenport.cncf.context.{SecurityContext, SubjectKind}
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.security.SecuritySubject

/*
 * @since   Apr. 14, 2026
 * @version Apr. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object WebDescriptorAuthorization {
  final case class Subject(
    roles: Set[String] = Set.empty,
    scopes: Set[String] = Set.empty,
    capabilities: Set[String] = Set.empty,
    anonymous: Boolean = true
  ) {
    def normalized: Subject =
      Subject(
        roles.map(SecuritySubject.normalize),
        scopes.map(SecuritySubject.normalize),
        capabilities.map(SecuritySubject.normalize),
        anonymous
      )

    def isAnonymous: Boolean =
      anonymous && {
        val values = roles ++ scopes ++ capabilities
        values.isEmpty || values.forall(x => SecuritySubject.normalize(x) == "anonymous")
      }
  }

  object Subject {
    def fromHttp[F[_]](
      req: org.http4s.Request[F]
    ): Subject = {
      val headerValues = req.headers.headers.map(h => h.name.toString -> h.value).toVector
      val queryValues = req.uri.query.params.toVector
      def tokens(keys: String*): Set[String] = {
        val normalizedKeys = keys.map(_.toLowerCase).toSet
        (headerValues ++ queryValues)
          .collect {
            case (key, value) if normalizedKeys.contains(key.toLowerCase) => value
          }
          .flatMap(_split_tokens)
          .toSet
      }
      val roles = tokens("role", "roles", "x-cncf-role", "x-cncf-roles", "x-textus-role", "x-textus-roles")
      val scopes = tokens("scope", "scopes", "x-cncf-scope", "x-cncf-scopes", "x-textus-scope", "x-textus-scopes")
      val capabilities = tokens("capability", "capabilities", "x-cncf-capability", "x-cncf-capabilities", "x-textus-capability", "x-textus-capabilities")
      val authenticatedTokens = tokens(
        "authorization",
        "x-cncf-session",
        "x-textus-session",
        "x-cncf-principal",
        "x-textus-principal",
        "principal",
        "principalId",
        "principal_id"
      )
      Subject(
        roles = roles,
        scopes = scopes,
        capabilities = capabilities,
        anonymous = authenticatedTokens.isEmpty && roles.isEmpty && scopes.isEmpty && capabilities.isEmpty
      )
    }

    def from(
      security: SecurityContext
    ): Subject = {
      val roles = security.principal.attributes
        .get("role")
        .map(_split_tokens)
        .getOrElse(Vector.empty)
        .toSet
      val scopes = security.principal.attributes
        .get("scope")
        .map(_split_tokens)
        .getOrElse(Vector.empty)
        .toSet
      val capabilities = security.capabilities.map(_.name)
      Subject(
        roles = roles,
        scopes = scopes,
        capabilities = capabilities,
        anonymous = security.subjectKind == SubjectKind.Anonymous ||
          security.principal.attributes.get("anonymous").exists(_.equalsIgnoreCase("true"))
      )
    }
  }

  def isAllowed(
    descriptor: WebDescriptor,
    selector: String,
    subject: Subject,
    operationMode: OperationMode
  ): Boolean =
    descriptor.authorization.get(selector) match {
      case Some(rule) => isAllowed(rule, subject, operationMode)
      case None => true
    }

  def isAllowed(
    rule: WebDescriptor.Authorization,
    subject: Subject,
    operationMode: OperationMode
  ): Boolean = {
    val normalizedSubject = subject.normalized
    _operation_mode_allowed(rule, operationMode) &&
      (if (normalizedSubject.isAnonymous)
        rule.allowAnonymous && _anonymous_operation_mode_allowed(rule, operationMode)
      else
        _category_allowed(rule.roles, normalizedSubject.roles) &&
          _category_allowed(rule.scopes, normalizedSubject.scopes) &&
          _category_allowed(rule.capabilities, normalizedSubject.capabilities)
      )
  }

  def isAllowed(
    descriptor: WebDescriptor,
    selector: String,
    subject: Subject
  ): Boolean =
    isAllowed(descriptor, selector, subject, org.goldenport.cncf.config.RuntimeConfig.DefaultOperationMode)

  def isAllowed(
    rule: WebDescriptor.Authorization,
    subject: Subject
  ): Boolean =
    isAllowed(rule, subject, org.goldenport.cncf.config.RuntimeConfig.DefaultOperationMode)

  private def _operation_mode_allowed(
    rule: WebDescriptor.Authorization,
    operationMode: OperationMode
  ): Boolean =
    rule.operationModes.isEmpty || rule.operationModes.contains(operationMode)

  private def _anonymous_operation_mode_allowed(
    rule: WebDescriptor.Authorization,
    operationMode: OperationMode
  ): Boolean =
    rule.anonymousOperationModes.isEmpty ||
      rule.anonymousOperationModes.contains(operationMode)

  private def _category_allowed(
    required: Vector[String],
    actual: Set[String]
  ): Boolean =
    required.isEmpty || required.exists(x => actual.contains(SecuritySubject.normalize(x)))

  private def _split_tokens(value: String): Vector[String] =
    value.split("[,\\s|]+").toVector.map(_.trim).filter(_.nonEmpty)
}
