package org.goldenport.cncf.http

import org.goldenport.cncf.security.SecuritySubject

/*
 * @since   Apr. 14, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object WebDescriptorAuthorization {
  final case class Subject(
    roles: Set[String] = Set.empty,
    scopes: Set[String] = Set.empty,
    capabilities: Set[String] = Set.empty
  ) {
    def normalized: Subject =
      Subject(
        roles.map(SecuritySubject.normalize),
        scopes.map(SecuritySubject.normalize),
        capabilities.map(SecuritySubject.normalize)
      )
  }

  def isAllowed(
    descriptor: WebDescriptor,
    selector: String,
    subject: Subject
  ): Boolean =
    descriptor.authorization.get(selector) match {
      case Some(rule) => isAllowed(rule, subject)
      case None => true
    }

  def isAllowed(
    rule: WebDescriptor.Authorization,
    subject: Subject
  ): Boolean = {
    val normalizedSubject = subject.normalized
    _category_allowed(rule.roles, normalizedSubject.roles) &&
      _category_allowed(rule.scopes, normalizedSubject.scopes) &&
      _category_allowed(rule.capabilities, normalizedSubject.capabilities)
  }

  private def _category_allowed(
    required: Vector[String],
    actual: Set[String]
  ): Boolean =
    required.isEmpty || required.exists(x => actual.contains(SecuritySubject.normalize(x)))
}
