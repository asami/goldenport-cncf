package org.goldenport.cncf.entity

import org.goldenport.record.Record
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}
import org.simplemodeling.model.value.SecurityAttributes

/*
 * Entity create default variation point.
 *
 * This is CNCF runtime policy, not a simplemodeling-model concern.
 *
 * @since   Apr. 13, 2026
 * @version Apr. 13, 2026
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

  def byCollectionName(
    overrides: Map[String, EntityCreateDefaultsPolicy],
    fallback: EntityCreateDefaultsPolicy = default
  ): EntityCreateDefaultsPolicy =
    ByCollectionName(overrides, fallback)

  object Default extends EntityCreateDefaultsPolicy {
    def complementCreateRecord[T](
      record: Record,
      id: EntityId,
      options: EntityCreateOptions
    )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Record = {
      val now = java.time.ZonedDateTime.now(ctx.clock.withZone(ctx.timezone))
      val principalid = ctx.security.principal.id.value
      val principal = _identifier_text(principalid)
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
      add_or_replace("name", Some(principalid))
      add_or_replace("createdAt", Some(now))
      add_or_replace("createdBy", Some(principal))
      add_or_replace("updatedAt", Some(now))
      add_or_replace("updatedBy", Some(principal))
      add_or_replace("postStatus", Some(PostStatus.Published))
      add_if_missing("aliveness", Some(Aliveness.default))
      val security = _default_security_attributes(principal, options)
      add_if_missing("ownerId", Some(security.ownerId.id.value))
      add_if_missing("groupId", Some(security.groupId.id.value))
      add_if_missing("rights", Some(security.rights.toRecord))
      add_if_missing("privilegeId", Some(security.privilegeId.id.value))
      options.defaultValues.fields.foreach { field =>
        add_if_missing(field.key, Some(field.value.single))
      }
      if (options.hasDefaultProfile("publication")) {
        add_if_missing("publishAt", Some(now))
        add_if_missing("publicAt", Some(now))
        add_if_missing("publishedBy", Some(principal))
      }
      add_if_missing("traceId", Some(ctx.observability.traceId.value))
      add_if_missing("correlationId", ctx.observability.correlationId.map(_.value))

      val defaultvalues = defaults.result()
      Record.dataAuto((record.asMap ++ defaultvalues.toMap).toSeq*)
    }

    private def _default_security_attributes(
      principal: String,
      options: EntityCreateOptions
    ): SecurityAttributes =
      if (
        options.hasDefaultProfile("public-read") ||
        options.hasDefaultProfile("publication") ||
        options.hasDefaultProfile("cms") ||
        options.hasDefaultProfile("public-content")
      )
        SecurityAttributes.publicOwnedBy(principal)
      else
        SecurityAttributes.privateOwnedBy(principal)

    private def _is_system_or_unknown(p: Any): Boolean =
      _is_system(p) || Option(p).exists(_.toString.equalsIgnoreCase("unknown"))

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
