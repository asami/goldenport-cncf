package org.goldenport.cncf.unitofwork

import java.nio.file.Path
import org.goldenport.http.HttpResponse
import org.goldenport.id.UniversalId
import org.goldenport.process.{ShellCommand, ShellCommandResult}
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.*
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.directive.*
import org.goldenport.cncf.blob.{
  ContentReferenceAttachResult,
  ContentReferenceContent,
  ContentReferenceNormalizeResult,
  ContentRenderResult,
  InlineImageAttachResult,
  InlineImageContent,
  InlineImageNormalizeResult,
  InlineImageOccurrence
}
import org.goldenport.cncf.embedded.{EmbeddedDataStore, EmbeddedStatement, EmbeddedUpdateResult}
import org.goldenport.cncf.processexecution.{ProcessExecutionResult, ResolvedProcessExecution}
import org.goldenport.cncf.operation.evaluation.OperationEvaluationSupplementalIntent
import org.goldenport.value.{ContentAttributes, ContentReferenceOccurrence}

/*
 * UnitOfWork operation algebra.
 *
 * This ADT defines the canonical execution operations interpreted
 * by UnitOfWork. Both declarative (Free/UoW) and direct execution
 * DSLs must construct these operations.
 *
 * This is the single source of truth for executable intents.
 *
 * @since   Jan. 10, 2026
 *  version Feb. 25, 2026
 *  version Mar. 24, 2026
 *  version Apr. 29, 2026
 *  version May.  4, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait UnitOfWorkOp[A]

object UnitOfWorkOp {

  // ------------------------------------------------------------
  // Authorization operations
  // ------------------------------------------------------------
  final case class Authorize(
    authorization: UnitOfWorkAuthorization
  ) extends UnitOfWorkOp[Unit]

  final case class StageOperationEvaluationSupplemental(
    intent: OperationEvaluationSupplementalIntent
  ) extends UnitOfWorkOp[Unit]

  // ------------------------------------------------------------
  // HTTP operations
  // ------------------------------------------------------------

  final case class HttpGet(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse]

  final case class HttpPost(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse]

  final case class HttpPostBag(
    path: String,
    body: Option[org.goldenport.bag.Bag],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse]

  final case class HttpPut(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse]

  final case class ShellCommandExec(
    command: ShellCommand
  ) extends UnitOfWorkOp[ShellCommandResult]

  // ------------------------------------------------------------
  // Process Execution operations
  // ------------------------------------------------------------
  final case class ProcessExec(
    execution: ResolvedProcessExecution
  ) extends UnitOfWorkOp[ProcessExecutionResult]

  // ------------------------------------------------------------
  // DataStore operations
  // ------------------------------------------------------------
  final case class DataStoreLoad(
    id: UniversalId
  ) extends UnitOfWorkOp[Option[Record]]

  final case class DataStoreSave(
    id: UniversalId,
    record: Record
  ) extends UnitOfWorkOp[Unit]

  final case class DataStoreDelete(
    id: UniversalId
  ) extends UnitOfWorkOp[Unit]

  // ------------------------------------------------------------
  // Component-local embedded datastore operations
  // ------------------------------------------------------------
  final case class LocalDataDir(
    componentName: String
  ) extends UnitOfWorkOp[Path]

  final case class EmbeddedDataStoreOpen(
    componentName: String,
    name: String,
    path: Option[Path] = None
  ) extends UnitOfWorkOp[EmbeddedDataStore]

  final case class EmbeddedDataStoreRead(
    store: EmbeddedDataStore,
    statement: EmbeddedStatement
  ) extends UnitOfWorkOp[Vector[Record]]

  final case class EmbeddedDataStoreUpdate(
    store: EmbeddedDataStore,
    statement: EmbeddedStatement
  ) extends UnitOfWorkOp[EmbeddedUpdateResult]

  final case class EmbeddedDataStoreMigrate(
    store: EmbeddedDataStore,
    statements: Vector[String]
  ) extends UnitOfWorkOp[Unit]

  // ------------------------------------------------------------
  // EntityStore operations
  // ------------------------------------------------------------
  final case class EntityStoreCreate[T](
    entity: T,
    tc: EntityPersistentCreate[T],
    options: EntityCreateOptions = EntityCreateOptions.default,
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[CreateResult[T]]

  final case class EntityStoreClaimOrLoad[C, P](
    entity: C,
    create: EntityPersistentCreate[C],
    persisted: EntityPersistent[P],
    options: EntityCreateOptions = EntityCreateOptions.default,
    createAuthorization: Option[UnitOfWorkAuthorization] = None,
    loadAuthorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[EntityStore.EntityClaimResult[C, P]]

  /** Stable-identity create-or-update through the versioned mutation path. */
  final case class EntityStoreUpsert[T](
    entity: T,
    id: EntityId,
    policy: EntityUpsertPolicy = EntityUpsertPolicy.default,
    tc: EntityPersistentCreate[T],
    options: EntityCreateOptions = EntityCreateOptions.default,
    createAuthorization: Option[UnitOfWorkAuthorization] = None,
    updateAuthorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[CreateResult[T]]

  final case class EntityStoreLoad[T](
    id: EntityId,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None,
    visibilityScope: Option[EntityVisibilityScope] = None,
    useEntitySpace: Boolean = true
  ) extends UnitOfWorkOp[Option[T]]

  final case class EntityStoreLoadSnapshot[T](
      id: EntityId,
      tc: EntityPersistent[T],
      authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Option[EntitySnapshot[T]]]

  final case class EntityStoreLoadDetached[T](
    id: EntityId,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Option[EntityRevisionCarrier[T]]]

  // Special-use direct path to EntityStoreSpace (bypasses EntitySpace/MemoryRealm).
  final case class EntityStoreLoadDirect[T](
    id: EntityId,
    tc: EntityPersistent[T]
  ) extends UnitOfWorkOp[Option[T]]

  final case class EntityStoreSave[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  ) extends UnitOfWorkOp[EntitySnapshot[T]]
  object EntityStoreSave {
    def apply[T](
      entity: T,
      expectedRevision: EntityRevision,
      tc: EntityPersistent[T]
    ): EntityStoreSave[T] =
      new EntityStoreSave(
        entity,
        Some(expectedRevision),
        tc,
        executionPolicy = EntityMutationExecutionPolicy(
          concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
        )
      )

    def apply[T](
      entity: T,
      expectedRevision: EntityRevision,
      tc: EntityPersistent[T],
      authorization: Option[UnitOfWorkAuthorization]
    ): EntityStoreSave[T] =
      new EntityStoreSave(
        entity,
        Some(expectedRevision),
        tc,
        authorization,
        EntityMutationExecutionPolicy(
          concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
        )
      )
  }

  final case class EntityStoreSaveDetached[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  ) extends UnitOfWorkOp[EntityRevisionCarrier[T]]

  final case class EntityStoreSaveManaged[T](
    entity: T,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  ) extends UnitOfWorkOp[T]

  final case class EntityStoreSaveUnversioned[T](
      entity: T,
      purpose: EntityUnversionedMutationPurpose,
      tc: EntityPersistent[T],
      authorization: Option[UnitOfWorkAuthorization]
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreUpsertUnversioned[T](
    entity: T,
    id: EntityId,
      purpose: EntityUnversionedMutationPurpose,
    tc: EntityPersistentCreate[T],
    options: EntityCreateOptions = EntityCreateOptions.default,
    createAuthorization: Option[UnitOfWorkAuthorization] = None,
    updateAuthorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[CreateResult[T]]

  final case class EntityStoreUpdate[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  ) extends UnitOfWorkOp[EntitySnapshot[T]]
  object EntityStoreUpdate {
    def apply[T](
      entity: T,
      expectedRevision: EntityRevision,
      tc: EntityPersistent[T]
    ): EntityStoreUpdate[T] =
      new EntityStoreUpdate(
        entity,
        Some(expectedRevision),
        tc,
        executionPolicy = EntityMutationExecutionPolicy(
          concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
        )
      )

    def apply[T](
      entity: T,
      expectedRevision: EntityRevision,
      tc: EntityPersistent[T],
      authorization: Option[UnitOfWorkAuthorization]
    ): EntityStoreUpdate[T] =
      new EntityStoreUpdate(
        entity,
        Some(expectedRevision),
        tc,
        authorization,
        EntityMutationExecutionPolicy(
          concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
        )
      )
  }

  final case class EntityStoreUpdateDetached[T](
    entity: T,
    expectedRevision: Option[EntityRevision],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  ) extends UnitOfWorkOp[EntityRevisionCarrier[T]]

  // Patch-oriented update route for cozy-generated update shapes (no id field in patch).
  final case class EntityStoreUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    authorization: Option[UnitOfWorkAuthorization] = None,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  ) extends UnitOfWorkOp[Record]

  final case class EntityStoreUpdateByIdObserved[P](
    id: EntityId,
    patch: P,
    expectedRevision: EntityRevision,
    tc: EntityPersistentUpdate[P],
    authorization: Option[UnitOfWorkAuthorization] = None,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy(
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
      )
  ) extends UnitOfWorkOp[EntityRecordSnapshot]

  final case class EntityStoreUpdateByIdDetached[P](
    id: EntityId,
    patch: P,
    expectedRevision: Option[EntityRevision],
    tc: EntityPersistentUpdate[P],
    authorization: Option[UnitOfWorkAuthorization] = None,
    executionPolicy: EntityMutationExecutionPolicy =
      EntityMutationExecutionPolicy.default
  ) extends UnitOfWorkOp[EntityRevisionCarrier[Record]]

  private[cncf] final case class EntityStoreConditionalTransition[R, P, S](
    request: EntityConditionalTransition[R, P, S],
    componentOwner: org.goldenport.cncf.datastore.DataStoreComponentOwner,
    rootReadAuthorization: Option[UnitOfWorkAuthorization],
    rootUpdateAuthorization: Option[UnitOfWorkAuthorization],
    successorAuthorization: Option[UnitOfWorkAuthorization]
  ) extends UnitOfWorkOp[EntityConditionalTransitionResult[R, S]]

  final case class EntityStoreUpdateUnversioned[T](
      entity: T,
      purpose: EntityUnversionedMutationPurpose,
      tc: EntityPersistent[T],
      authorization: Option[UnitOfWorkAuthorization]
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreUpdateByIdUnversioned[P](
      id: EntityId,
      patch: P,
      purpose: EntityUnversionedMutationPurpose,
      tc: EntityPersistentUpdate[P],
      authorization: Option[UnitOfWorkAuthorization]
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreDelete(
    id: EntityId,
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreRestore(
    id: EntityId,
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreDeleteHard(
    id: EntityId
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreSearch[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[SearchResult[T]]

  // Special-use direct path to EntityStoreSpace (bypasses EntitySpace/MemoryRealm).
  final case class EntityStoreSearchDirect[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[SearchResult[T]]

  final case class EntityStoreSearchInternal[T](
    query: EntityQuery[T],
    tc: EntityPersistent[T]
  ) extends UnitOfWorkOp[SearchResult[T]]

  final case class EntityStoreUniqueValueExists[T](
    collection: EntityCollectionId,
    fieldName: String,
    value: String,
    excludeId: Option[EntityId] = None,
    scope: EntityIdentityScope = EntityIdentityScope.CurrentContext,
    includeEntityIdEntropy: Boolean = false,
    tc: EntityPersistent[T]
  ) extends UnitOfWorkOp[Boolean]

  final case class EntityStoreResolveIdentity[T](
    collection: EntityCollectionId,
    value: String,
    fieldNames: Vector[String],
    includeEntityIdEntropy: Boolean = true,
    scope: EntityIdentityScope = EntityIdentityScope.CurrentContext,
    tc: EntityPersistent[T]
  ) extends UnitOfWorkOp[Option[EntityId]]

  // ------------------------------------------------------------
  // Blob operations
  // ------------------------------------------------------------
  final case class BlobNormalizeInlineImages(
    content: InlineImageContent
  ) extends UnitOfWorkOp[InlineImageNormalizeResult]

  final case class BlobAttachInlineImages(
    sourceEntityId: String,
    occurrences: Vector[InlineImageOccurrence]
  ) extends UnitOfWorkOp[InlineImageAttachResult]

  final case class ContentNormalizeReferences(
    content: ContentReferenceContent
  ) extends UnitOfWorkOp[ContentReferenceNormalizeResult]

  final case class ContentAttachReferences(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  ) extends UnitOfWorkOp[ContentReferenceAttachResult]

  final case class ContentValidateReferences(
    references: Vector[ContentReferenceOccurrence]
  ) extends UnitOfWorkOp[Unit]

  final case class ContentSyncInlineReferences(
    sourceEntityId: String,
    references: Vector[ContentReferenceOccurrence]
  ) extends UnitOfWorkOp[ContentReferenceAttachResult]

  final case class ContentRenderHtml(
    content: ContentAttributes
  ) extends UnitOfWorkOp[ContentRenderResult]
}
