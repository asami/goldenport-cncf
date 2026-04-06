package org.goldenport.cncf.security

import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SecuritySubject(
  subjectId: String,
  primaryGroup: Option[String],
  groups: Set[String],
  roles: Set[String],
  privileges: Set[String],
  capabilities: Set[String],
  securityLevel: Set[String]
) {
  def hasGroup(name: String): Boolean =
    groups.contains(SecuritySubject.normalize(name))

  def hasRole(name: String): Boolean =
    roles.contains(SecuritySubject.normalize(name))

  def hasPrivilege(name: String): Boolean =
    privileges.contains(SecuritySubject.normalize(name))

  def hasCapability(name: String): Boolean =
    capabilities.contains(SecuritySubject.normalize(name))

  def hasCreateGrant(
    resourceType: Option[String],
    collectionName: Option[String]
  ): Boolean = {
    val targets = SecuritySubject.createGrantTargets(resourceType, collectionName)
    targets.exists { target =>
      privileges.contains(target) ||
      capabilities.contains(target) ||
      roles.contains(target)
    }
  }
}

object SecuritySubject {
  def current(using ctx: ExecutionContext): SecuritySubject =
    from(ctx.security)

  def from(security: SecurityContext): SecuritySubject = {
    val attributes = security.principal.attributes
    val primaryGroup =
      _first_token(attributes, "group", "group_id")
    val groupSet =
      _tokens(attributes, "group", "group_id", "groups", "group_ids")
    val roleSet =
      _tokens(attributes, "role", "roles", "authority", "authorities")
    val privilegeSet =
      _tokens(attributes, "privilege", "privilege_id", "privileges")
    val capabilitySet =
      security.capabilities.flatMap(x => splitTokens(x.name).map(normalize))
    val levelSet =
      splitTokens(security.level.value).map(normalize)
    SecuritySubject(
      subjectId = security.principal.id.value,
      primaryGroup = primaryGroup.map(normalize),
      groups = groupSet.map(normalize),
      roles = roleSet.map(normalize),
      privileges = privilegeSet.map(normalize),
      capabilities = capabilitySet,
      securityLevel = levelSet
    )
  }

  def splitTokens(value: String): Set[String] =
    Option(value).toSet.flatMap(_.split("[,\\s|]+")).map(_.trim).filter(_.nonEmpty)

  def normalize(value: String): String =
    value.trim.toLowerCase.replace("_", "").replace("-", "")

  def createGrantTargets(
    resourceType: Option[String],
    collectionName: Option[String]
  ): Set[String] = {
    def mk(name: String): Set[String] = {
      val n = normalize(name)
      Set(
        normalize(s"create:$n"),
        normalize(s"create.$n"),
        normalize(s"create_$n"),
        normalize(s"$n:create"),
        normalize(s"$n.create"),
        normalize(s"${n}_create")
      )
    }
    resourceType.toSet.flatMap(mk) ++ collectionName.toSet.flatMap(mk)
  }

  private def _tokens(attributes: Map[String, String], keys: String*): Set[String] =
    keys.iterator.flatMap(k => attributes.get(k).toVector).flatMap(splitTokens).toSet

  private def _first_token(attributes: Map[String, String], keys: String*): Option[String] =
    keys.iterator.flatMap(k => attributes.get(k).toVector).flatMap(splitTokens).find(_.nonEmpty)
}
