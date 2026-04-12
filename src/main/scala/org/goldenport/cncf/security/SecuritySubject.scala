package org.goldenport.cncf.security

import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}

/*
 * @since   Apr.  7, 2026
 * @version Apr. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SecuritySubject(
  subjectId: String,
  authenticationState: SecuritySubject.AuthenticationState,
  accessTokenPresent: Boolean,
  primaryGroup: Option[String],
  groups: Set[String],
  roles: Set[String],
  privileges: Set[String],
  capabilities: Set[String],
  securityLevel: Set[String],
  attributes: Map[String, String] = Map.empty
) {
  def isAuthenticated: Boolean =
    authenticationState == SecuritySubject.AuthenticationState.Authenticated

  def isAnonymous: Boolean =
    authenticationState == SecuritySubject.AuthenticationState.Anonymous

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

  def hasUsageCapability(
    targetKind: String,
    targetName: String,
    action: String
  ): Boolean = {
    val targets = SecuritySubject.usageCapabilityTargets(targetKind, targetName, action)
    targets.exists { target =>
      capabilities.contains(target) ||
      privileges.contains(target) ||
      roles.contains(target)
    }
  }

  def attributeValues(name: String): Set[String] =
    attributes
      .get(name)
      .orElse(attributes.get(SecuritySubject.snake(name)))
      .toSet
      .flatMap(SecuritySubject.splitTokens)
}

object SecuritySubject {
  enum AuthenticationState {
    case Anonymous, Authenticated, Unspecified
  }

  def current(using ctx: ExecutionContext): SecuritySubject =
    from(ctx.security)

  def from(security: SecurityContext): SecuritySubject = {
    val attributes = security.principal.attributes
    val accessTokenPresent =
      _has_token(attributes)
    val authenticationState =
      _authentication_state(security, accessTokenPresent)
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
      authenticationState = authenticationState,
      accessTokenPresent = accessTokenPresent,
      primaryGroup = primaryGroup.map(normalize),
      groups = groupSet.map(normalize),
      roles = roleSet.map(normalize),
      privileges = privilegeSet.map(normalize),
      capabilities = capabilitySet,
      securityLevel = levelSet,
      attributes = attributes
    )
  }

  def splitTokens(value: String): Set[String] =
    Option(value).toSet.flatMap(_.split("[,\\s|]+")).map(_.trim).filter(_.nonEmpty)

  def normalize(value: String): String =
    value.trim.toLowerCase.replace("_", "").replace("-", "")

  def snake(text: String): String =
    text.flatMap {
      case c if c.isUpper => "_" + c.toLower
      case c => c.toString
    }.stripPrefix("_")

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

  def usageCapabilityTargets(
    targetKind: String,
    targetName: String,
    action: String
  ): Set[String] = {
    val kind = normalize(targetKind)
    val name = normalize(targetName)
    val act = normalize(action)
    Set(
      normalize(s"$kind:$name:$act"),
      normalize(s"$kind.$name.$act"),
      normalize(s"${kind}_${name}_${act}"),
      normalize(s"$name:$act"),
      normalize(s"$name.$act"),
      normalize(s"${name}_${act}")
    )
  }

  private def _tokens(attributes: Map[String, String], keys: String*): Set[String] =
    keys.iterator.flatMap(k => attributes.get(k).toVector).flatMap(splitTokens).toSet

  private def _first_token(attributes: Map[String, String], keys: String*): Option[String] =
    keys.iterator.flatMap(k => attributes.get(k).toVector).flatMap(splitTokens).find(_.nonEmpty)

  private def _has_token(attributes: Map[String, String]): Boolean =
    Vector("access_token", "token", "bearer_token", "jwt", "authorization")
      .exists(k => attributes.get(k).exists(_.trim.nonEmpty))

  private def _authentication_state(
    security: SecurityContext,
    accessTokenPresent: Boolean
  ): AuthenticationState = {
    val attributes = security.principal.attributes
    val explicitAuthenticated = _boolean(attributes.get("authenticated"))
    val explicitAnonymous = _boolean(attributes.get("anonymous"))
    val subjectId = normalize(security.principal.id.value)
    if (explicitAuthenticated.contains(true) || accessTokenPresent)
      AuthenticationState.Authenticated
    else if (
      explicitAnonymous.contains(true) ||
      explicitAuthenticated.contains(false) ||
      Set("", "anonymous", "anon", "guest", "unauthenticated").contains(subjectId)
    )
      AuthenticationState.Anonymous
    else
      AuthenticationState.Unspecified
  }

  private def _boolean(p: Option[String]): Option[Boolean] =
    p.map(_.trim.toLowerCase).collect {
      case "true" | "yes" | "on" | "1" => true
      case "false" | "no" | "off" | "0" => false
    }
}
