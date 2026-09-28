package org.goldenport.cncf.entity

import cats._
import cats.syntax.all.*
import scala.deprecatedName
import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.goldenport.cncf.*
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.goldenport.cncf.directive.{Query as EntityDirectiveQuery, SearchResult}
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreConditionalExpectedField,
  DataStoreConditionalRoot,
  DataStoreConditionalSuccessor,
  DataStoreConditionalTransitionPlan,
  DataStoreConditionalTransitionResult,
  EntityCompareAndSetMutationPlan,
  EntityDirectMutationPlan,
  EntityMutationExecutionPath,
  EntityMutationExclusionGuard,
  EntityMutationPathRequest,
  EntityMutationProviderReadback,
  EntityMutationProviderResult,
  EntityMutationReadbackRequirement,
  EntityVersionedMutationPlan,
  EntityVersionedMutationResult,
  EntityVersionedRootMutation,
  EntityVersionedSideEffect,
  Query as DataStoreQuery,
  QueryDirective,
  QueryLimit,
  QueryOrder,
  OrderDirection
}
import org.goldenport.cncf.datastore.DataStore.EntryId
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}
import org.simplemodeling.model.value.NominalScalar

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[entity] trait StandardEntityRecordPart extends EntityStore { self: StandardEntityStore =>
  import EntityStore.*

  private[entity] def _complement_create_record[T](
    record: Record,
    id: EntityId,
    options: EntityCreateOptions
  )(using tc: EntityPersistentCreate[T], ctx: ExecutionContext): Record =
    ctx.runtime.entityCreateDefaultsPolicy.complementCreateRecord(
      record = record,
      id = id,
      options = options
    )

  private[entity] def _complement_save_record(
    record: Record,
    id: EntityId,
    existing: Option[Record]
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = existing,
      includescreationdefaults = true,
      includesstatedefaults = true,
      createoptions = EntityCreateOptions.default
    )

  private[entity] def _complement_update_record(
    record: Record,
    id: EntityId
  )(using ctx: ExecutionContext): Record =
    _complement_record(
      record = record,
      id = id,
      existing = None,
      includescreationdefaults = false,
      includesstatedefaults = false,
      createoptions = EntityCreateOptions.default
    )

  private def _merge_update_record(
    existing: Record,
    changes: Record
  ): Consequence[Record] = {
    val sanitized = EntityConcurrencyMetadata.withoutManagedField(changes)
    val changedkeys = sanitized.keySet
    val retained = Record(_retained_existing_managed_record(existing).fields.filterNot(f =>
      changedkeys.contains(f.key)
    ))
    val domain =
      Record(SimpleEntityStorageShapePolicy.withoutManagedFields(existing).fields.filterNot(f =>
        changedkeys.contains(f.key)
      ))
    EntityConcurrencyMetadata.preserveForMutation(
      sanitized ++ retained ++ domain,
      existing
    )
  }

  private[entity] def _merge_versioned_update_record(
    existing: Record,
    changes: Record,
    revisionbinding: EntityRevisionBinding
  ): Consequence[Record] = {
    val sanitized = revisionbinding.withoutManagedRevision(changes)
    val changedkeys = sanitized.keySet
    val retained =
      Record(
        _retained_existing_managed_record(existing).fields.filterNot(field =>
          changedkeys.contains(field.key)
        )
      )
    val domain =
      Record(
        SimpleEntityStorageShapePolicy
          .withoutManagedFields(existing)
          .fields
          .filterNot(field => changedkeys.contains(field.key))
      )
    Consequence.success(
      revisionbinding.withoutManagedRevision(
        sanitized ++ retained ++ domain
      )
    )
  }

  private[entity] def _merge_plain_update_record(
    existing: Record,
    changes: Record
  ): Consequence[Record] = {
    val changedkeys = changes.keySet
    val retained =
      Record(
        _retained_existing_managed_record(existing).fields.filterNot(field =>
          changedkeys.contains(field.key)
        )
      )
    val domain =
      Record(
        SimpleEntityStorageShapePolicy
          .withoutManagedFields(existing)
          .fields
          .filterNot(field => changedkeys.contains(field.key))
      )
    Consequence.success(changes ++ retained ++ domain)
  }

  private[entity] def _save_plain[T](
    entity: T,
    id: EntityId,
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    existing: Option[Record]
  )(using
    persistent: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[Unit] =
    for {
      admitted <- _reject_detached_managed_field(
        persistent.toStoreRecord(entity),
        "entity"
      )
      candidate = _complement_save_record(admitted, id, existing)
      prepared <- ContentBodyStoragePolicy.prepareForSave(id, candidate)
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      _ <- _with_datastore_calltree(
        if (existing.isDefined) "save" else "create",
        collection,
        Some(entryid)
      ) {
        existing match {
          case Some(_) =>
            datastore.save(collection, entryid, prepared)
          case None =>
            datastore.create(collection, entryid, prepared)
        }
      }
    } yield ()

  private[entity] def _update_plain[T](
    changes: T
  )(using
    persistent: EntityPersistent[T],
    ctx: ExecutionContext
  ): Consequence[Unit] = {
    val id = persistent.id(changes)
    for {
      collection <- ctx.entityStoreSpace.dataStoreCollection(id)
      entryid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      existing <- _raw_record(collection, entryid)
      base <- _required_record(entryid, existing)
      _ <- _reject_logically_deleted_existing(id, Some(base))
      admitted <- _reject_detached_managed_field(
        persistent.toStoreRecord(changes),
        "entity"
      )
      candidate <- _merge_plain_update_record(
        base,
        _complement_update_record(admitted, id)
      )
      prepared <- ContentBodyStoragePolicy.prepareForSave(
        id,
        candidate,
        preserveExistingOverflowOnMissingContent = true
      )
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      _ <- _with_datastore_calltree(
        "save",
        collection,
        Some(entryid)
      ) {
        datastore.save(collection, entryid, prepared)
      }
    } yield ()
  }

  private[entity] def _update_by_id_plain[P](
    id: EntityId,
    patch: P
  )(using
    persistent: EntityPersistentUpdate[P],
    ctx: ExecutionContext
  ): Consequence[Unit] =
    for {
      collection <- ctx.entityStoreSpace.dataStoreCollection(id)
      entryid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      existing <- _raw_record(collection, entryid)
      base <- _required_record(entryid, existing)
      _ <- _reject_logically_deleted_existing(id, Some(base))
      admitted <- _reject_detached_managed_field(
        Update.toChangesRecord(persistent.toStoreRecord(patch)),
        "patch"
      )
      candidate <- _merge_plain_update_record(
        base,
        _complement_update_record(admitted, id)
      )
      prepared <- ContentBodyStoragePolicy.prepareForSave(
        id,
        candidate,
        preserveExistingOverflowOnMissingContent = true
      )
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      _ <- _with_datastore_calltree(
        "save",
        collection,
        Some(entryid)
      ) {
        datastore.save(collection, entryid, prepared)
      }
    } yield ()

  private[entity] def _save_plain_lifecycle(
    id: EntityId,
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId,
    existing: Record,
    changes: Record
  )(using
    ctx: ExecutionContext
  ): Consequence[Unit] =
    for {
      candidate <- _merge_plain_update_record(existing, changes)
      prepared <- ContentBodyStoragePolicy.prepareForSave(
        id,
        candidate,
        preserveExistingOverflowOnMissingContent = true
      )
      datastore <- ctx.dataStoreSpace.dataStore(collection)
      _ <- _with_datastore_calltree(
        "save",
        collection,
        Some(entryid)
      ) {
        datastore.save(collection, entryid, prepared)
      }
    } yield ()

  private[entity] def _reject_detached_managed_field(
    record: Record,
    parameter: String
  ): Consequence[Record] =
    EntityRevisionBinding(EntityRevisionRepresentation.Detached)
      .rejectManagedPatch(record, parameter)

  private def _retained_existing_managed_record(
    existing: Record
  ): Record = {
    val generalfields = Vector(
      "id",
      "shortid",
      "name",
      "createdAt",
      "createdBy",
      "postStatus",
      "aliveness",
      "tenantId",
      "organizationId",
      "publishAt",
      "publicAt",
      "publishedBy"
    ).flatMap { name =>
      SimpleEntityStorageShapePolicy.value(existing, name)
        .map(SimpleEntityStorageShapePolicy.targetName(name) -> _)
    }
    val securityfields =
      SimpleEntityStorageShapePolicy.securityAttributesFromRecord(existing)
        .toVector
        .flatMap { attributes =>
          Vector(
            Some("owner_id" -> attributes.ownerId.id.value),
            Option(attributes.groupId.id.value)
              .filter(_.nonEmpty)
              .map("group_id" -> _),
            Option(attributes.privilegeId.id.value)
              .filter(_.nonEmpty)
              .map("privilege_id" -> _),
            Some(
              "permission" ->
                SimpleEntityStorageShapePolicy.permissionJson(attributes.rights)
            )
          ).flatten
        }
    Record.dataAuto((generalfields ++ securityfields)*)
  }

  private[entity] def _reject_logically_deleted_existing(
    id: EntityId,
    existing: Option[Record]
  ): Consequence[Unit] =
    existing match {
      case Some(record) if EntityLifecycleRecordPolicy.isLogicallyDeleted(record) =>
        Consequence.entityNotFound(s"entity is logically deleted: ${id.value}")
      case _ =>
        Consequence.unit
    }

  private[entity] def _with_upsert_lock[A](
    id: EntityId
  )(
    body: => Consequence[A]
  ): Consequence[A] = {
    val index = Math.floorMod(id.print.hashCode, _upsert_locks.length)
    _upsert_locks(index).synchronized(body)
  }

  private def _complement_record(
    record: Record,
    id: EntityId,
    existing: Option[Record],
    includescreationdefaults: Boolean,
    includesstatedefaults: Boolean,
    createoptions: EntityCreateOptions
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.Instant.now(ctx.clock)
    val zonednow = java.time.ZonedDateTime.now(ctx.clock.withZone(ctx.timezone))
    val principalid = ctx.security.principal.id.value
    val principal = principalid
    val defaults = Vector.newBuilder[(String, Any)]
    val existingmap = existing.map(_.asMap).getOrElse(Map.empty)

    def _add_if_missing_(canonical: String, value: => Option[Any]): Unit =
      SimpleEntityStorageShapePolicy.value(record, canonical) match {
        case Some(current) =>
          defaults += (SimpleEntityStorageShapePolicy.targetName(canonical) -> current)
        case None =>
          value.foreach(v =>
            defaults += (SimpleEntityStorageShapePolicy.targetName(canonical) -> v)
          )
      }

    def _add_or_replace_(canonical: String, value: => Option[Any]): Unit =
      value.foreach(v => defaults += (SimpleEntityStorageShapePolicy.targetName(canonical) -> v))

    def _existing_value_(canonical: String): Option[Any] =
      existing.flatMap(SimpleEntityStorageShapePolicy.value(_, canonical))

    if (includescreationdefaults) {
      _add_if_missing_("id", _existing_value_("id").orElse(Some(id.value)))
      _add_if_missing_("shortid", _existing_value_("shortid").orElse(Some(id.parts.entropy)))
      _add_if_missing_("name", _existing_value_("name").orElse(Some(principalid)))
      _add_if_missing_("createdAt", _existing_value_("createdAt").orElse(Some(now)))
      _add_if_missing_("createdBy", _existing_value_("createdBy").orElse(Some(principal)))
    }

    _add_or_replace_("updatedAt", Some(now))
    _add_or_replace_("updatedBy", Some(principal))
    if (includesstatedefaults) {
      _add_if_missing_(
        "postStatus",
        _existing_value_("postStatus").orElse(Some(_default_post_status(createoptions)))
      )
      _add_if_missing_("aliveness", _existing_value_("aliveness").orElse(Some(Aliveness.default)))
    }
    if (includescreationdefaults) {
      val security =
        if (createoptions.hasDefaultProfile("publication"))
          org.simplemodeling.model.value.SecurityAttributes.publicOwnedBy(principal)
        else
          org.simplemodeling.model.value.SecurityAttributes.privateOwnedBy(principal)
      _add_if_missing_(
        "ownerId",
        _existing_value_("ownerId").orElse(Some(security.ownerId.id.value))
      )
      _add_if_missing_(
        "groupId",
        _existing_value_("groupId").orElse(Some(security.groupId.id.value))
      )
      _add_if_missing_(
        "privilegeId",
        _existing_value_("privilegeId").orElse(Some(security.privilegeId.id.value))
      )
      _add_if_missing_(
        "permission",
        Some(SimpleEntityStorageShapePolicy.permissionJson(security.rights))
      )
    }
    if (includescreationdefaults && createoptions.hasDefaultProfile("publication")) {
      _add_if_missing_("publishAt", _existing_value_("publishAt").orElse(Some(zonednow)))
      _add_if_missing_("publicAt", _existing_value_("publicAt").orElse(Some(zonednow)))
      _add_if_missing_("publishedBy", _existing_value_("publishedBy").orElse(Some(principal)))
    }
    _add_if_missing_("traceId", Some(ctx.observability.traceId.value))
    _add_if_missing_("correlationId", ctx.observability.correlationId.map(_.value))

    val complement = Record.dataAuto(defaults.result()*)
    complement ++
      SimpleEntityStorageShapePolicy.withoutManagedFields(
        EntityConcurrencyMetadata.withoutManagedField(record)
      )
  }

  private def _default_post_status(
    options: EntityCreateOptions
  ): Any =
    if (options.hasDefaultProfile("publication"))
      PostStatus.Published
    else
      PostStatus.default

  private[entity] def _soft_delete_record(
    existing: Record
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.Instant.now(ctx.clock)
    val principalid = ctx.security.principal.id.value
    val principal = principalid
    val base = Vector.newBuilder[(String, Any)]
    base += SimpleEntityStorageShapePolicy.targetName("postStatus") -> PostStatus.Archived
    base += SimpleEntityStorageShapePolicy.targetName("aliveness") -> Aliveness.Dead
    base += SimpleEntityStorageShapePolicy.targetName("updatedAt") -> now
    base += SimpleEntityStorageShapePolicy.targetName("updatedBy") -> principal
    base += SimpleEntityStorageShapePolicy.targetName("traceId") -> ctx.observability.traceId.value
    ctx.observability.correlationId.foreach { x =>
      base += SimpleEntityStorageShapePolicy.targetName("correlationId") -> x.value
    }
    base += SimpleEntityStorageShapePolicy.targetName("deletedAt") -> now
    base += SimpleEntityStorageShapePolicy.targetName("deletedBy") -> principal
    Record.dataAuto(base.result()*)
  }

  private[entity] def _restore_record(
    existing: Record
  )(using ctx: ExecutionContext): Record = {
    val now = java.time.Instant.now(ctx.clock)
    val principal = ctx.security.principal.id.value
    val deletedfields =
      Set("deletedAt", "deleted_at", "deletedBy", "deleted_by")
    val preserved =
      Record(existing.fields.filterNot(field => deletedfields.contains(field.key)))
    val restored = Record.dataAuto(
      SimpleEntityStorageShapePolicy.targetName("postStatus") -> PostStatus.Draft,
      SimpleEntityStorageShapePolicy.targetName("aliveness") -> Aliveness.Alive,
      SimpleEntityStorageShapePolicy.targetName("updatedAt") -> now,
      SimpleEntityStorageShapePolicy.targetName("updatedBy") -> principal,
      SimpleEntityStorageShapePolicy.targetName("traceId") ->
        ctx.observability.traceId.value
    )
    val correlated =
      ctx.observability.correlationId
        .map(value =>
          Record.dataAuto(
            SimpleEntityStorageShapePolicy.targetName("correlationId") ->
              value.value
          )
        )
        .getOrElse(Record.empty)
    preserved ++ restored ++ correlated
  }

  private[entity] def _is_soft_delete_target(existing: Record): Boolean = {
    val keyset = existing.keySet
    keyset.contains("aliveness")
  }

}
