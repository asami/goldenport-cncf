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
private[entity] trait StandardEntityConditionalPart extends EntityStore { self: StandardEntityStore =>
  import EntityStore.*

  private[entity] def _prepare_conditional_successor[S](
    successor: EntitySuccessorIntent[S],
    owner: org.goldenport.cncf.datastore.DataStoreComponentOwner,
    boundevidence: Option[EntityBoundSuccessorEvidence],
    revisionbinding: EntityRevisionBinding
  )(using
    ctx: ExecutionContext
  ): Consequence[
    (
      DataStoreConditionalSuccessor,
      Vector[EntityVersionedSideEffect],
      EntityId
    )
  ] =
    successor match {
      case createintent: EntitySuccessorIntent.Create[c, S] @unchecked =>
        given EntityPersistentCreate[c] = createintent.create
        val id =
          createintent.candidateId
            .getOrElse(ctx.idGeneration.entityId(createintent.collection))
        for {
          collection <- ctx.entityStoreSpace.dataStoreCollection(id)
          entry <- ctx.entityStoreSpace.dataStoreEntryId(id)
          initialized <- revisionbinding.initializeForCreate(
              _complement_create_record(
                createintent.create.toStoreRecord(createintent.candidate),
                id,
                EntityCreateOptions.default
              )
            )
          preparation <- ContentBodyStoragePolicy.planForVersionedSave(
            id,
            initialized
          )
        } yield (
          DataStoreConditionalSuccessor.Create(
            owner,
            collection,
            entry,
            revisionbinding.storageFieldName,
            _conditional_storage_record(preparation.record)
          ),
          preparation.sideEffects,
          id
        )
      case bindintent: EntitySuccessorIntent.Bind[S] @unchecked =>
        boundevidence match {
          case Some(evidence) if evidence.id == bindintent.id =>
            for {
              collection <-
                ctx.entityStoreSpace.dataStoreCollection(bindintent.id)
              entry <-
                ctx.entityStoreSpace.dataStoreEntryId(bindintent.id)
              revision <- EntityConcurrencyMetadata
                .mutationRevision(evidence.revision)
                .map(_._1)
            } yield (
              DataStoreConditionalSuccessor.Bind(
                owner,
                collection,
                entry,
                revisionbinding.storageFieldName,
                revision
              ),
              Vector.empty,
              bindintent.id
            )
          case Some(evidence) =>
            Consequence.argumentExpectedActualMismatch(
              "boundSuccessor.id",
              bindintent.id,
              evidence.id
            )
          case None =>
            Consequence.argumentMissing("boundSuccessor")
        }
    }

  private[entity] def _admit_conditional_root_changes(
    changes: Record
  ): Consequence[Record] = {
    val domainchanges =
      SimpleEntityStorageShapePolicy.withoutManagedFields(changes)
    val managedfields = changes.keySet -- domainchanges.keySet
    if (managedfields.nonEmpty)
      Consequence.argumentPolicyViolation(
        "rootPatch",
        "entity-conditional-transition.framework-managed-field",
        "domain fields only",
        managedfields.toVector.sorted.mkString(",")
      )
    else if (domainchanges.isEmpty)
      Consequence.argumentInvalid(
        "rootPatch",
        "one or more domain changes",
        "empty patch"
      )
    else
      Consequence.success(domainchanges)
  }

  private[entity] def _require_conditional_domain_change(
    current: Record,
    candidate: Record
  ): Consequence[Unit] = {
    val currentdomain =
      _conditional_storage_record(
        SimpleEntityStorageShapePolicy.withoutManagedFields(current)
      )
    val candidatedomain =
      _conditional_storage_record(
        SimpleEntityStorageShapePolicy.withoutManagedFields(candidate)
      )
    if (_conditional_record_delta(currentdomain, candidatedomain).isEmpty)
      Consequence.argumentInvalid(
        "rootPatch",
        "one or more effective domain changes",
        "no-op patch"
      )
    else
      Consequence.unit
  }

  private[entity] def _conditional_transition_result[R, P, S](
    request: EntityConditionalTransition[R, P, S],
    successorid: EntityId,
    providerresult: DataStoreConditionalTransitionResult,
    rootbinding: EntityRevisionBinding,
    successorbinding: EntityRevisionBinding
  )(using
    ctx: ExecutionContext
  ): Consequence[EntityConditionalTransitionExecutionResult[R, S]] =
    providerresult match {
      case DataStoreConditionalTransitionResult.Transitioned(
            rootrecord,
            successorrecord
          ) =>
        (for {
          hydratedroot <-
            ContentBodyStoragePolicy.hydrate(request.rootId, rootrecord)
          admittedroot <- request.rootPersistent.admitStoreRecord(hydratedroot)
          rootvalue <- _conditional_transition_value(
            rootbinding,
            admittedroot
          )(
            EntityPersistent._decode_admitted_store_record(
              request.rootPersistent,
              request.rootId.collection,
              _
            )
          )
          hydratedsuccessor <-
            ContentBodyStoragePolicy.hydrate(successorid, successorrecord)
          admittedsuccessor <- request.successor.persisted.admitStoreRecord(hydratedsuccessor)
          successorvalue <- _conditional_transition_value(
            successorbinding,
            admittedsuccessor
          )(
            EntityPersistent._decode_admitted_store_record(
              request.successor.persisted,
              successorid.collection,
              _
            )
          )
        } yield EntityConditionalTransitionExecutionResult.Transitioned(
          EntityConditionalTransitionResult.Transitioned(
            rootvalue,
            successorvalue
          ),
          hydratedroot,
          hydratedsuccessor
        )).recoverWith(EntityConcurrencyMetadata.committedProjectionFailure)
      case DataStoreConditionalTransitionResult.NotMatched(existingroot) =>
        for {
          hydratedroot <-
            ContentBodyStoragePolicy.hydrate(request.rootId, existingroot)
          admittedroot <- request.rootPersistent.admitStoreRecord(hydratedroot)
          rootvalue <- _conditional_transition_value(
            rootbinding,
            admittedroot
          )(
            EntityPersistent._decode_admitted_store_record(
              request.rootPersistent,
              request.rootId.collection,
              _
            )
          )
        } yield EntityConditionalTransitionExecutionResult.NotMatched(
          EntityConditionalTransitionResult.NotMatched(rootvalue),
          hydratedroot
        )
    }

  private def _conditional_transition_value[A](
    binding: EntityRevisionBinding,
    record: Record
  )(
    decode: Record => Consequence[A]
  ): Consequence[EntityConditionalTransitionValue[A]] =
    binding.representation match {
      case EntityRevisionRepresentation.Embedded =>
        for {
          revision <- binding.revision(record)
          entity <- binding.decodeEntity(record)(decode)
        } yield EntityConditionalTransitionValue.Embedded(entity, revision)
      case EntityRevisionRepresentation.Detached =>
        binding
          .detachedCarrier(record)(decode)
          .map(EntityConditionalTransitionValue.Detached(_))
    }

  private[entity] def _conditional_storage_side_effects(
    effects: Vector[EntityVersionedSideEffect]
  ): Vector[EntityVersionedSideEffect] =
    effects.map {
      case save: EntityVersionedSideEffect.Save =>
        save.copy(record = _conditional_storage_record(save.record))
      case delete: EntityVersionedSideEffect.Delete =>
        delete
    }

  private[entity] def _conditional_storage_record(
    record: Record
  ): Record =
    Record.dataAuto(
      record.fields.map(field =>
        field.key -> _conditional_storage_value(field.value.single)
      )*
    )

  private[entity] def _conditional_record_delta(
    current: Record,
    candidate: Record
  ): Record = {
    val currentvalues = current.asMap
    val candidatevalues = candidate.asMap
    val changed = candidate.fields.collect {
      case field
          if currentvalues.get(field.key) !=
            Some(field.value.single) =>
        field.key -> field.value.single
    }
    val removed = current.fields.collect {
      case field if !candidatevalues.contains(field.key) =>
        field.key -> Update.SetNull
    }
    Record.dataAuto((changed ++ removed)*)
  }

  private def _conditional_storage_value(
    value: Any
  ): Any =
    value match {
      case id: EntityId => id.print
      case identifier: Identifier => identifier.value
      case scalar: NominalScalar =>
        _conditional_storage_value(scalar.value)
      case aliveness: Aliveness => aliveness.dbValue
      case status: PostStatus => status.dbValue
      case record: Record => _conditional_storage_record(record)
      case values: Seq[?] =>
        values.map(_conditional_storage_value)
      case Some(content) =>
        _conditional_storage_value(content)
      case None =>
        None
      case other =>
        other
    }

}
