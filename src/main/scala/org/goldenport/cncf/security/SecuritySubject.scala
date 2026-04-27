package org.goldenport.cncf.security

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Apr.  7, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SecuritySubject(
  subjectId: String,
  authenticationState: SecuritySubject.AuthenticationState,
  accessTokenPresent: Boolean,
  primaryGroup: Option[String],
  groups: Set[String],
  roles: Set[String],
  scopes: Set[String] = Set.empty,
  privileges: Set[String],
  capabilities: Set[String],
  securityLevel: Set[String],
  attributes: Map[String, String] = Map.empty
) {
  def isAuthenticated: Boolean =
    authenticationState == SecuritySubject.AuthenticationState.Authenticated

  def isProviderAuthenticated: Boolean =
    attributes.get(SecuritySubject.AuthenticationProvenanceAttribute)
      .exists(x => SecuritySubject.normalize(x) == SecuritySubject.ProviderAuthenticationProvenance)

  def isAnonymous: Boolean =
    authenticationState == SecuritySubject.AuthenticationState.Anonymous

  def hasGroup(name: String): Boolean =
    groups.contains(SecuritySubject.normalize(name))

  def hasRole(name: String): Boolean =
    roles.contains(SecuritySubject.normalize(name))

  def hasScope(name: String): Boolean =
    scopes.contains(SecuritySubject.normalize(name))

  def hasPrivilege(name: String): Boolean =
    privileges.contains(SecuritySubject.normalize(name))

  def hasCapability(name: String): Boolean =
    capabilities.contains(SecuritySubject.normalize(name))

  def hasCreateGrant(
    resourceType: Option[String],
    collectionName: Option[String]
  ): Boolean = {
    val legacyTargets = SecuritySubject.legacyCreateGrantTargets(resourceType, collectionName)
    val collectionTargets = collectionName.toSet.flatMap(SecuritySubject.collectionCreateGrantTargets)
    _has_any(legacyTargets) || _has_capability(collectionTargets)
  }

  def hasCollectionGrant(
    collectionName: String,
    action: String
  ): Boolean =
    _has_capability(SecuritySubject.collectionGrantTargets(collectionName, action))

  def hasAssociationGrant(
    domain: String,
    action: String
  ): Boolean =
    _has_capability(SecuritySubject.associationGrantTargets(domain, action))

  def hasStoreGrant(
    storeName: String,
    action: String
  ): Boolean =
    _has_capability(SecuritySubject.storeGrantTargets(storeName, action))

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

  def hasServiceGrant(
    sourceComponentName: Option[String],
    targetComponentName: Option[String]
  ): Boolean = {
    val targets = SecuritySubject.serviceGrantTargets(sourceComponentName, targetComponentName)
    targets.exists { target =>
      capabilities.contains(target) ||
      privileges.contains(target) ||
      roles.contains(target)
    }
  }

  def attributeValues(name: String): Set[String] =
    SecuritySubject.attributeKeys(name)
      .iterator
      .flatMap(attributes.get)
      .toSet
      .flatMap(SecuritySubject.splitTokens)

  def normalizedAttributeValues(name: String): Set[String] =
    attributeValues(name).map(SecuritySubject.normalize)

  def withRoleDefinitions(
    definitions: Iterable[SecurityRoleDefinition]
  ): SecuritySubject =
    copy(capabilities = capabilities ++ SecurityRoleDefinition.expandCapabilities(roles, definitions))

  private def _has_any(targets: Set[String]): Boolean =
    targets.exists { target =>
      capabilities.contains(target) ||
      privileges.contains(target) ||
      roles.contains(target)
    }

  private def _has_capability(targets: Set[String]): Boolean =
    targets.exists(capabilities.contains)
}

final case class SecurityRoleDefinition(
  name: String,
  includes: Vector[String] = Vector.empty,
  capabilities: Vector[String] = Vector.empty
)

object SecurityRoleDefinition {
  def expandCapabilities(
    roles: Set[String],
    definitions: Iterable[SecurityRoleDefinition]
  ): Set[String] = {
    val index = definitions.map(d => SecuritySubject.normalize(d.name) -> d).toMap
    roles.flatMap(role => _expand_role(SecuritySubject.normalize(role), index, Set.empty))
  }

  def normalizedIndex(
    definitions: Iterable[SecurityRoleDefinition]
  ): Map[String, SecurityRoleDefinition] =
    definitions.map(d => SecuritySubject.normalize(d.name) -> d).toMap

  private def _expand_role(
    role: String,
    index: Map[String, SecurityRoleDefinition],
    visited: Set[String]
  ): Set[String] =
    if (visited.contains(role))
      Set.empty
    else
      index.get(role) match
        case Some(definition) =>
          val direct = definition.capabilities.flatMap(_capability_tokens).toSet
          val nested = definition.includes.flatMap { include =>
            _expand_role(SecuritySubject.normalize(include), index, visited + role)
          }.toSet
          direct ++ nested
        case None =>
          Set.empty

  private def _capability_tokens(value: String): Set[String] =
    SecuritySubject.splitTokens(value).map(SecuritySubject.normalize) + SecuritySubject.normalize(value)
}

object SecuritySubject {
  val AuthenticationProvenanceAttribute: String = "cncf.security.authentication.provenance"
  val ProviderAuthenticationProvenance: String = "provider"

  enum AuthenticationState {
    case Anonymous, Authenticated, Unspecified
  }

  def current(using ctx: ExecutionContext): SecuritySubject =
    from(ctx.security, _role_definitions(ctx))

  def from(security: SecurityContext): SecuritySubject = {
    val attributes = security.principal.attributes
    val accessTokenPresent =
      _has_token(attributes)
    val sessionPresent =
      _has_session(security)
    val authenticationState =
      _authentication_state(security, accessTokenPresent, sessionPresent)
    val primaryGroup =
      _first_token(attributes, "group", "group_id")
    val groupSet =
      _tokens(attributes, "group", "group_id", "groups", "group_ids")
    val roleSet =
      _tokens(attributes, "role", "roles", "authority", "authorities")
    val scopeSet =
      _tokens(attributes, "scope", "scopes")
    val privilegeSet =
      _tokens(attributes, "privilege", "privilege_id", "privileges")
    val capabilitySet =
      security.capabilities.flatMap(x => _capability_tokens(x.name))
    val levelSet =
      splitTokens(security.level.value).map(normalize)
    SecuritySubject(
      subjectId = security.principal.id.value,
      authenticationState = authenticationState,
      accessTokenPresent = accessTokenPresent,
      primaryGroup = primaryGroup.map(normalize),
      groups = groupSet.map(normalize),
      roles = roleSet.map(normalize),
      scopes = scopeSet.map(normalize),
      privileges = privilegeSet.map(normalize),
      capabilities = capabilitySet,
      securityLevel = levelSet,
      attributes = attributes
    )
  }

  def from(
    security: SecurityContext,
    roleDefinitions: Iterable[SecurityRoleDefinition]
  ): SecuritySubject =
    from(security).withRoleDefinitions(roleDefinitions)

  def splitTokens(value: String): Set[String] =
    Option(value).toSet.flatMap(_.split("[,\\s|]+")).map(_.trim).filter(_.nonEmpty)

  def normalize(value: String): String =
    value.trim.toLowerCase.replace("_", "").replace("-", "")

  def snake(text: String): String =
    text.flatMap {
      case c if c.isUpper => "_" + c.toLower
      case c => c.toString
    }.stripPrefix("_")

  def attributeKeys(name: String): Vector[String] = {
    val base = Vector(name, snake(name)).distinct
    (base ++ base.map("subject." + _) ++ base.map("principal." + _)).distinct
  }

  def createGrantTargets(
    resourceType: Option[String],
    collectionName: Option[String]
  ): Set[String] = {
    legacyCreateGrantTargets(resourceType, collectionName) ++
      collectionName.toSet.flatMap(collectionCreateGrantTargets)
  }

  def legacyCreateGrantTargets(
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
    resourceType.toSet.flatMap(mk) ++
      collectionName.toSet.flatMap(mk)
  }

  def collectionCreateGrantTargets(collectionName: String): Set[String] =
    collectionGrantTargets(collectionName, "create").filter(_.startsWith(normalize("collection")))

  def collectionGrantTargets(
    collectionName: String,
    action: String
  ): Set[String] = {
    val name = normalize(collectionName)
    val act = normalize(action)
    Set(
      normalize(s"collection:$name:$act"),
      normalize(s"collection.$name.$act"),
      normalize(s"collection_${name}_$act"),
      normalize(s"$name:$act"),
      normalize(s"$name.$act"),
      normalize(s"${name}_$act")
    )
  }

  def associationGrantTargets(
    domain: String,
    action: String
  ): Set[String] = {
    val d = normalize(domain)
    val act = normalize(action)
    Set(
      normalize(s"association:$d:$act"),
      normalize(s"association.$d.$act"),
      normalize(s"association_${d}_$act"),
      normalize(s"$d:$act"),
      normalize(s"$d.$act"),
      normalize(s"${d}_$act")
    )
  }

  def storeGrantTargets(
    storeName: String,
    action: String
  ): Set[String] = {
    val name = normalize(storeName)
    val act = normalize(action)
    Set(
      normalize(s"store:$name:$act"),
      normalize(s"store.$name.$act"),
      normalize(s"store_${name}_$act"),
      normalize(s"$name:$act"),
      normalize(s"$name.$act"),
      normalize(s"${name}_$act")
    )
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

  def serviceGrantTargets(
    sourceComponentName: Option[String],
    targetComponentName: Option[String]
  ): Set[String] = {
    val source = sourceComponentName.map(normalize).filter(_.nonEmpty)
    val target = targetComponentName.map(normalize).filter(_.nonEmpty)
    (source, target) match
      case (Some(s), Some(t)) =>
        Set(
          normalize(s"service-grant:$s:$t"),
          normalize(s"service.grant.$s.$t"),
          normalize(s"service_grant_${s}_$t"),
          normalize(s"$s:$t:service-grant"),
          normalize(s"$s.$t.service-grant")
        )
      case _ =>
        Set.empty
  }

  private def _tokens(attributes: Map[String, String], keys: String*): Set[String] =
    keys.iterator.flatMap(k => attributes.get(k).toVector).flatMap(splitTokens).toSet

  private def _capability_tokens(value: String): Set[String] =
    splitTokens(value).map(normalize) + normalize(value)

  private def _first_token(attributes: Map[String, String], keys: String*): Option[String] =
    keys.iterator.flatMap(k => attributes.get(k).toVector).flatMap(splitTokens).find(_.nonEmpty)

  private def _has_token(attributes: Map[String, String]): Boolean =
    Vector("access_token", "token", "bearer_token", "jwt", "authorization")
      .exists(k => attributes.get(k).exists(_.trim.nonEmpty))

  private def _has_session(security: SecurityContext): Boolean =
    security.session.exists { session =>
      session.sessionId.exists(_.trim.nonEmpty) ||
      session.tokenId.exists(_.trim.nonEmpty) ||
      session.authenticatedAt.nonEmpty ||
      session.expiresAt.nonEmpty ||
      session.refreshSessionId.exists(_.trim.nonEmpty) ||
      session.attributes.exists { case (_, value) => value.trim.nonEmpty }
    }

  private def _authentication_state(
    security: SecurityContext,
    accessTokenPresent: Boolean,
    sessionPresent: Boolean
  ): AuthenticationState = {
    val attributes = security.principal.attributes
    val explicitAuthenticated = _boolean(attributes.get("authenticated"))
    val explicitAnonymous = _boolean(attributes.get("anonymous"))
    val subjectId = normalize(security.principal.id.value)
    if (explicitAuthenticated.contains(true) || accessTokenPresent || sessionPresent)
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

  private def _role_definitions(
    ctx: ExecutionContext
  ): Iterable[SecurityRoleDefinition] =
    _subsystem_from_scope(ctx.cncfCore.scope)
      .flatMap(_.descriptor)
      .flatMap(_.security)
      .flatMap(_.authorization)
      .map(_.roles.values)
      .getOrElse(Vector.empty)

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
}
