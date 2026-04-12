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
      val principal = principalid
      val propertyname = ctx.runtime.context.propertyName
      val defaults = Vector.newBuilder[(String, Any)]
      val knownkeys = record.keySet

      def key(canonical: String): String =
        propertyname.preferredName(knownkeys, canonical)

      def add_if_missing(canonical: String, value: => Option[Any]): Unit =
        if (!propertyname.aliases(canonical).exists(record.keySet.contains))
          value.foreach(v => defaults += (key(canonical) -> v))

      add_if_missing("id", Some(id.print))
      add_if_missing("name", Some(principalid))
      add_if_missing("createdAt", Some(now))
      add_if_missing("createdBy", Some(principal))
      add_if_missing("updatedAt", Some(now))
      add_if_missing("updatedBy", Some(principal))
      add_if_missing("postStatus", Some(PostStatus.Published))
      add_if_missing("aliveness", Some(Aliveness.default))
      add_if_missing("securityAttributes", Some(_default_security_attributes(principal, options)))
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

      record ++ Record.dataAuto(defaults.result()*)
    }

    private def _default_security_attributes(
      principal: String,
      options: EntityCreateOptions
    ): Record =
      if (
        options.hasDefaultProfile("public-read") ||
        options.hasDefaultProfile("publication") ||
        options.hasDefaultProfile("cms") ||
        options.hasDefaultProfile("public-content")
      )
        SecurityAttributes.publicOwnedBy(principal).toRecord
      else
        SecurityAttributes.privateOwnedBy(principal).toRecord
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
