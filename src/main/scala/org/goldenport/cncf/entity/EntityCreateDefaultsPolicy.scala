package org.goldenport.cncf.entity

import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}
import org.simplemodeling.model.value.SecurityAttributes
import org.goldenport.datatype.{Identifier, ObjectId}

/*
 * Entity create default variation point.
 *
 * This is CNCF runtime policy, not a simplemodeling-model concern.
 *
 * @since   Apr. 13, 2026
 *  version Apr. 20, 2026
 * @version Apr. 25, 2026
 * @author  ASAMI, Tomoharu
 */
trait EntityCreateDefaultsPolicy {
  def complementCreateRecord[T](
    record: Record,
    id: EntityId,
    options: EntityCreateOptions
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Record
}

object EntityCreateDefaultsPolicy {
  val default: EntityCreateDefaultsPolicy = Default

  final case class Context(
    record: Record,
    id: EntityId,
    options: EntityCreateOptions,
    principalId: String,
    principal: String
  )

  trait OwnerIdSelector {
    def ownerId(context: Context)(using ExecutionContext): String
  }

  trait GroupIdSelector {
    def groupId(context: Context, ownerId: String)(using ExecutionContext): String
  }

  trait TenantIdSelector {
    def tenantId(context: Context)(using ExecutionContext): Option[String]
  }

  trait OrganizationIdSelector {
    def organizationId(context: Context)(using ExecutionContext): Option[String]
  }

  object OwnerIdSelector {
    val principal: OwnerIdSelector = new OwnerIdSelector {
      def ownerId(context: Context)(using ExecutionContext): String =
        context.principal
    }

    def constant(value: String): OwnerIdSelector = new OwnerIdSelector {
      def ownerId(context: Context)(using ExecutionContext): String =
        value
    }
  }

  object GroupIdSelector {
    val owner: GroupIdSelector = new GroupIdSelector {
      def groupId(context: Context, ownerId: String)(using ExecutionContext): String =
        ownerId
    }

    def constant(value: String): GroupIdSelector = new GroupIdSelector {
      def groupId(context: Context, ownerId: String)(using ExecutionContext): String =
        value
    }
  }

  object TenantIdSelector {
    val none: TenantIdSelector = new TenantIdSelector {
      def tenantId(context: Context)(using ExecutionContext): Option[String] =
        None
    }

    def constant(value: String): TenantIdSelector = new TenantIdSelector {
      def tenantId(context: Context)(using ExecutionContext): Option[String] =
        Some(value)
    }
  }

  object OrganizationIdSelector {
    val none: OrganizationIdSelector = new OrganizationIdSelector {
      def organizationId(context: Context)(using ExecutionContext): Option[String] =
        None
    }

    def constant(value: String): OrganizationIdSelector = new OrganizationIdSelector {
      def organizationId(context: Context)(using ExecutionContext): Option[String] =
        Some(value)
    }
  }

  def byCollectionName(
    overrides: Map[String, EntityCreateDefaultsPolicy],
    fallback: EntityCreateDefaultsPolicy = default
  ): EntityCreateDefaultsPolicy =
    ByCollectionName(overrides, fallback)

  def byEntityName(
    overrides: Map[String, EntityCreateDefaultsPolicy],
    fallback: EntityCreateDefaultsPolicy = default
  ): EntityCreateDefaultsPolicy =
    byCollectionName(overrides, fallback)

  def withApplicationDefault(
    applicationDefault: EntityCreateDefaultsPolicy,
    entityOverrides: Map[String, EntityCreateDefaultsPolicy] = Map.empty
  ): EntityCreateDefaultsPolicy =
    byEntityName(entityOverrides, applicationDefault)

  object Default extends DefaultPolicy(
    OwnerIdSelector.principal,
    GroupIdSelector.owner,
    TenantIdSelector.none,
    OrganizationIdSelector.none
  )

  def withOwnerIdSelector(
    selector: OwnerIdSelector
  ): EntityCreateDefaultsPolicy =
    DefaultPolicy(selector, GroupIdSelector.owner, TenantIdSelector.none, OrganizationIdSelector.none)

  def withOwnerAndGroupIdSelectors(
    ownerIdSelector: OwnerIdSelector,
    groupIdSelector: GroupIdSelector
  ): EntityCreateDefaultsPolicy =
    DefaultPolicy(ownerIdSelector, groupIdSelector, TenantIdSelector.none, OrganizationIdSelector.none)

  def withSelectors(
    ownerIdSelector: OwnerIdSelector = OwnerIdSelector.principal,
    groupIdSelector: GroupIdSelector = GroupIdSelector.owner,
    tenantIdSelector: TenantIdSelector = TenantIdSelector.none,
    organizationIdSelector: OrganizationIdSelector = OrganizationIdSelector.none
  ): EntityCreateDefaultsPolicy =
    DefaultPolicy(ownerIdSelector, groupIdSelector, tenantIdSelector, organizationIdSelector)

  case class DefaultPolicy(
    ownerIdSelector: OwnerIdSelector,
    groupIdSelector: GroupIdSelector,
    tenantIdSelector: TenantIdSelector,
    organizationIdSelector: OrganizationIdSelector
  ) extends EntityCreateDefaultsPolicy {
    def complementCreateRecord[T](
      record: Record,
      id: EntityId,
      options: EntityCreateOptions
    )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Record = {
      val now = java.time.Instant.now(ctx.clock)
      val zonednow = java.time.ZonedDateTime.now(ctx.clock.withZone(ctx.timezone))
      val principalid = ctx.security.principal.id.value
      val principal = _identifier_text(principalid)
      val defaultscontext = Context(record, id, options, principalid, principal)
      val ownerid = ownerIdSelector.ownerId(defaultscontext)
      val groupid = groupIdSelector.groupId(defaultscontext, ownerid)
      val tenantid = tenantIdSelector.tenantId(defaultscontext)
      val organizationid = organizationIdSelector.organizationId(defaultscontext)
      val propertyname = ctx.runtime.context.propertyName
      val defaults = Vector.newBuilder[(String, Any)]
      val knownkeys = record.keySet

      def key(canonical: String): String =
        propertyname.preferredName(knownkeys, canonical)

      def add_if_missing(canonical: String, value: => Option[Any]): Unit =
        if (!propertyname.aliases(canonical).exists(record.keySet.contains))
          value.foreach(v => defaults += (key(canonical) -> v))

      def add_or_replace_generated_default(
        canonical: String,
        value: => Option[Any],
        isGeneratedDefault: Any => Boolean
      ): Unit = {
        val current = propertyname.aliases(canonical).iterator.flatMap(record.getAny).map(_single_value).toVector.headOption
        if (current.forall(isGeneratedDefault))
          value.foreach(v => defaults += (key(canonical) -> v))
      }

      def add_or_replace(canonical: String, value: => Option[Any]): Unit =
        value.foreach(v => defaults += (key(canonical) -> v))

      add_if_missing("id", Some(id.print))
      add_if_missing("shortid", Some(id.parts.entropy))
      add_or_replace_generated_default("name", Some(principalid), _is_generated_name_default)
      add_or_replace("createdAt", Some(now))
      add_or_replace("createdBy", Some(principal))
      add_or_replace("updatedAt", Some(now))
      add_or_replace("updatedBy", Some(principal))
      add_or_replace("postStatus", Some(PostStatus.Published))
      add_if_missing("aliveness", Some(Aliveness.default))
      val security = _default_security_attributes(ownerid, groupid, options)
      val generatedsecuritydefault = _has_generated_security_default(record, propertyname)
      add_or_replace_generated_default("ownerId", Some(security.ownerId.id.value), _is_system_or_unknown)
      add_or_replace_generated_default("groupId", Some(security.groupId.id.value), _is_system_or_unknown)
      add_if_missing("tenantId", tenantid)
      add_if_missing("organizationId", organizationid)
      if (generatedsecuritydefault)
        add_or_replace("rights", Some(security.rights.toRecord))
      else
        add_if_missing("rights", Some(security.rights.toRecord))
      add_or_replace_generated_default("privilegeId", Some(security.privilegeId.id.value), _is_system_or_unknown)
      options.defaultValues.fields.foreach { field =>
        add_if_missing(field.key, Some(field.value.single))
      }
      if (options.hasDefaultProfile("publication")) {
        add_if_missing("publishAt", Some(zonednow))
        add_if_missing("publicAt", Some(zonednow))
        add_if_missing("publishedBy", Some(principal))
      }
      add_if_missing("traceId", Some(ctx.observability.traceId.value))
      add_if_missing("correlationId", ctx.observability.correlationId.map(_.value))

      val defaultvalues = defaults.result()
      Record.dataAuto((record.asMap ++ defaultvalues.toMap).toSeq*)
    }

    private def _default_security_attributes(
      ownerId: String,
      groupId: String,
      options: EntityCreateOptions
    ): SecurityAttributes = {
      val rights =
        if (
        options.hasDefaultProfile("public-read") ||
        options.hasDefaultProfile("publication") ||
        options.hasDefaultProfile("cms") ||
        options.hasDefaultProfile("public-content")
      )
          SecurityAttributes.publicOwnedBy(ownerId).rights
        else
          SecurityAttributes.privateOwnedBy(ownerId).rights
      SecurityAttributes(
        ownerId = _object_id(ownerId),
        groupId = _object_id(groupId),
        rights = rights,
        privilegeId = _object_id(ownerId)
      )
    }

    private def _has_generated_security_default(
      record: Record,
      propertyName: org.goldenport.cncf.context.RuntimeContext.PropertyNameContext
    ): Boolean = {
      def first(canonical: String): Option[Any] =
        propertyName.aliases(canonical).iterator.flatMap(record.getAny).map(_single_value).toVector.headOption
      first("ownerId").exists(_is_system_or_unknown) &&
        first("groupId").exists(_is_system_or_unknown) &&
        first("privilegeId").exists(_is_system_or_unknown)
    }

    private def _object_id(text: String): ObjectId =
      ObjectId(Identifier(_identifier_text(text)))

    private def _is_system_or_unknown(p: Any): Boolean =
      _is_system(p) || Option(p).exists(_.toString.equalsIgnoreCase("unknown"))

    private def _is_generated_name_default(p: Any): Boolean =
      _is_system_or_unknown(p) || Option(p).exists(_.toString.equalsIgnoreCase("anonymous"))

    private def _is_system(p: Any): Boolean =
      Option(p).exists(_.toString.equalsIgnoreCase("system"))

    private def _is_epoch(p: Any): Boolean =
      Option(p).exists(_.toString.startsWith("1970-01-01T00:00"))

    private def _is_draft(p: Any): Boolean =
      p match
        case PostStatus.Draft => true
        case m: String => m.equalsIgnoreCase("draft") || m.equalsIgnoreCase("PostStatus.Draft") || m == "1"
        case n: Int => n == PostStatus.Draft.dbValue
        case n: Long => n == PostStatus.Draft.dbValue.toLong
        case m => Option(m).exists(_.toString.equalsIgnoreCase("draft"))

    private def _single_value(p: Any): Any =
      p match
        case Some(v) => _single_value(v)
        case None => ""
        case m => m

    private def _identifier_text(text: String): String = {
      val sanitized = text.trim.map {
        case c if c.isLetterOrDigit || c == '_' => c
        case _ => '_'
      }.mkString
      val nonempty = if (sanitized.isEmpty) "unknown" else sanitized
      if (nonempty.headOption.exists(_.isLetter))
        nonempty
      else
        s"id_$nonempty"
    }

  }

  final case class ByCollectionName(
    overrides: Map[String, EntityCreateDefaultsPolicy],
    fallback: EntityCreateDefaultsPolicy = default
  ) extends EntityCreateDefaultsPolicy {
    def complementCreateRecord[T](
      record: Record,
      id: EntityId,
      options: EntityCreateOptions
    )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Record =
      overrides
        .get(id.collection.name)
        .orElse(overrides.get(id.collection.print))
        .getOrElse(fallback)
        .complementCreateRecord(record, id, options)
  }
}
