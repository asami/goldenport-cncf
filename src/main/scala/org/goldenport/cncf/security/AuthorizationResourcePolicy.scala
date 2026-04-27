package org.goldenport.cncf.security

/*
 * @since   Apr. 28, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AuthorizationResourcePolicy(
  capabilities: Vector[String] = Vector.empty,
  permission: Option[String] = None
) {
  def normalizedCapabilities: Vector[String] =
    capabilities.map(SecuritySubject.normalize)
}

final case class AuthorizationResourcePolicies(
  collections: Map[String, Map[String, AuthorizationResourcePolicy]] = Map.empty,
  associations: Map[String, Map[String, AuthorizationResourcePolicy]] = Map.empty,
  stores: Map[String, Map[String, AuthorizationResourcePolicy]] = Map.empty
) {
  def collection(
    name: Option[String],
    action: String
  ): Option[AuthorizationResourcePolicy] =
    _lookup(collections, name, action)

  def association(
    domain: Option[String],
    action: String
  ): Option[AuthorizationResourcePolicy] =
    _lookup(associations, domain, action)

  def store(
    name: Option[String],
    action: String
  ): Option[AuthorizationResourcePolicy] =
    _lookup(stores, name, action)

  def mergeOverride(
    overridePolicies: AuthorizationResourcePolicies
  ): AuthorizationResourcePolicies =
    AuthorizationResourcePolicies(
      collections = _merge(collections, overridePolicies.collections),
      associations = _merge(associations, overridePolicies.associations),
      stores = _merge(stores, overridePolicies.stores)
    )

  private def _lookup(
    table: Map[String, Map[String, AuthorizationResourcePolicy]],
    name: Option[String],
    action: String
  ): Option[AuthorizationResourcePolicy] = {
    val act = SecuritySubject.normalize(action)
    name.map(SecuritySubject.normalize).flatMap { n =>
      table.get(n).flatMap(_.get(act))
    }
  }

  private def _merge(
    defaults: Map[String, Map[String, AuthorizationResourcePolicy]],
    overrides: Map[String, Map[String, AuthorizationResourcePolicy]]
  ): Map[String, Map[String, AuthorizationResourcePolicy]] =
    overrides.foldLeft(defaults) { case (acc, (name, actions)) =>
      val key = SecuritySubject.normalize(name)
      val inherited = acc.getOrElse(key, Map.empty)
      acc + (key -> (inherited ++ actions.map { case (action, policy) =>
        SecuritySubject.normalize(action) -> policy
      }))
    }
}

object AuthorizationResourcePolicies {
  val empty: AuthorizationResourcePolicies = AuthorizationResourcePolicies()
}
