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
import org.goldenport.cncf.processexecution.{GitExecutionResult, GitOperation, GitTarget, ProcessExecutionResult, ResolvedProcessExecution, SbtExecutionAttempt, SbtExecutionResult, SbtOperation, SbtTarget}
import org.goldenport.cncf.operation.evaluation.OperationEvaluationSupplementalIntent
import org.goldenport.cncf.oauth.OAuthConnectionRequest
import org.goldenport.cncf.workflow.{ActionExecution, ProviderExecutionRequest, StateMachineProvidedApiRequest}
import org.goldenport.value.{ContentAttributes, ContentReferenceOccurrence}
import org.goldenport.cncf.workspace.{FilePrepareDirectoryResult, FileReadBytesResult, FileReadTextResult, FileRepresentation, FileTarget, FileWriteBytesResult, FileWriteMode, FileWriteTextResult, WorkspaceFilePath}

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
 *  version Jul. 26, 2026
 * @version Oct. 27, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait UnitOfWorkOp[A]

object UnitOfWorkOp {
  /** Unit means declarative intent accepted, never physical write completed.
    * Only the managed closing preparation interpreter admits this operation.
    */
  private[cncf] final case class StageWorkflowClosingLocalIntent(
    intent: org.goldenport.cncf.datastore.WorkflowClosingLocalIntent
  ) extends UnitOfWorkOp[Unit]

  // ------------------------------------------------------------
  // State machine Provider operations
  // ------------------------------------------------------------
  final case class StateMachineProviderExecute(
    request: ProviderExecutionRequest
  ) extends UnitOfWorkOp[ActionExecution]

  final case class StateMachineProvidedApiExecute(
    request: StateMachineProvidedApiRequest
  ) extends UnitOfWorkOp[ActionExecution]

  // ------------------------------------------------------------
  // Authorization operations
  // ------------------------------------------------------------
  final case class Authorize(
    authorization: UnitOfWorkAuthorization
  ) extends UnitOfWorkOp[Unit]

  final case class StageOperationEvaluationSupplemental(
    intent: OperationEvaluationSupplementalIntent
  ) extends UnitOfWorkOp[Unit]

  private[cncf] final case class OAuthConnectionExecute[A](
    request: OAuthConnectionRequest[A]
  ) extends UnitOfWorkOp[A] {
    override def toString: String = "OAuthConnectionExecute(<redacted>)"
  }

  // ------------------------------------------------------------
  // HTTP operations
  // ------------------------------------------------------------

  final case class HttpGet(
    path: String,
    headers: Map[String, String] = Map.empty,
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse] {
    override def toString: String = "HttpGet(<redacted>)"
  }

  final case class HttpPost(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse] {
    override def toString: String = "HttpPost(<redacted>)"
  }

  final case class HttpPostBag(
    path: String,
    body: Option[org.goldenport.bag.Bag],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse] {
    override def toString: String = "HttpPostBag(<redacted>)"
  }

  final case class HttpPut(
    path: String,
    body: Option[String],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse] {
    override def toString: String = "HttpPut(<redacted>)"
  }

  final case class HttpPutBag(
    path: String,
    body: Option[org.goldenport.bag.Bag],
    headers: Map[String, String],
    properties: Vector[Property] = Vector.empty
  ) extends UnitOfWorkOp[HttpResponse] {
    override def toString: String = "HttpPutBag(<redacted>)"
  }

  final case class ShellCommandExec(
    command: ShellCommand
  ) extends UnitOfWorkOp[ShellCommandResult]

  // ------------------------------------------------------------
  // Process Execution operations
  // ------------------------------------------------------------
  final case class ProcessExec(
    execution: ResolvedProcessExecution
  ) extends UnitOfWorkOp[ProcessExecutionResult]

  final case class SbtExec(
    target: SbtTarget,
    operation: SbtOperation
  ) extends UnitOfWorkOp[SbtExecutionResult]

  final case class SbtExecAttempt(
    target: SbtTarget,
    operation: SbtOperation
  ) extends UnitOfWorkOp[SbtExecutionAttempt]

  final case class GitExec[A](
    target: GitTarget,
    operation: GitOperation[A]
  ) extends UnitOfWorkOp[GitExecutionResult[A]]

  final case class FilePrepareDirectory(
    target: FileTarget,
    path: WorkspaceFilePath
  ) extends UnitOfWorkOp[FilePrepareDirectoryResult] {
    override def toString: String = s"FilePrepareDirectory($target,FileWrite)"
  }

  final case class FileWriteBytes(
    target: FileTarget,
    path: WorkspaceFilePath,
    bytes: Vector[Byte],
    representation: FileRepresentation
  ) extends UnitOfWorkOp[FileWriteBytesResult] {
    override def toString: String = s"FileWriteBytes($target,$representation,FileWrite)"
  }

  final case class FileReadBytes(
    target: FileTarget,
    path: WorkspaceFilePath,
    representation: FileRepresentation
  ) extends UnitOfWorkOp[FileReadBytesResult] {
    override def toString: String = s"FileReadBytes($target,$representation,FileRead)"
  }

  final case class FileReadText(
    target: FileTarget,
    path: WorkspaceFilePath
  ) extends UnitOfWorkOp[FileReadTextResult] {
    override def toString: String = s"FileReadText($target,FileRead)"
  }

  final case class FileWriteText(
    target: FileTarget,
    path: WorkspaceFilePath,
    text: String,
    mode: FileWriteMode
  ) extends UnitOfWorkOp[FileWriteTextResult] {
    override def toString: String = s"FileWriteText($target,FileWrite)"
  }

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

  private[cncf] final case class EntityStoreCreateOutcome[T](
    entity: T,
    tc: EntityPersistentCreate[T],
    options: EntityCreateOptions = EntityCreateOptions.default,
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[EntityCreateMutationOutcome[T]]

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

  private[cncf] final case class EntityStoreSaveSnapshotOutcome[T](
    entity: T,
    expectedrevision: Option[EntityRevision],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization],
    executionpolicy: EntityMutationExecutionPolicy
  ) extends UnitOfWorkOp[EntityVersionedMutationCaptureOutcome[EntitySnapshot[T]]]

  private[cncf] final case class EntityStoreSaveDetachedOutcome[T](
    entity: T,
    expectedrevision: Option[EntityRevision],
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization],
    executionpolicy: EntityMutationExecutionPolicy
  ) extends UnitOfWorkOp[EntityVersionedMutationCaptureOutcome[EntityRevisionCarrier[T]]]

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

  private[cncf] final case class EntityStoreUpdateRecordSnapshotOutcome[P](
    id: EntityId,
    patch: P,
    expectedrevision: Option[EntityRevision],
    tc: EntityPersistentUpdate[P],
    authorization: Option[UnitOfWorkAuthorization],
    executionpolicy: EntityMutationExecutionPolicy
  ) extends UnitOfWorkOp[EntityVersionedMutationCaptureOutcome[EntityRecordSnapshot]]

  private[cncf] final case class EntityStoreUpdateRecordDetachedOutcome[P](
    id: EntityId,
    patch: P,
    expectedrevision: Option[EntityRevision],
    tc: EntityPersistentUpdate[P],
    authorization: Option[UnitOfWorkAuthorization],
    executionpolicy: EntityMutationExecutionPolicy
  ) extends UnitOfWorkOp[EntityVersionedMutationCaptureOutcome[EntityRevisionCarrier[Record]]]

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

  final case class EntityStoreDeleteObserved(
    id: EntityId,
    expectedRevision: EntityRevision,
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[EntityDeletionResult]

  private[cncf] final case class EntityStoreDeleteObservedOutcome(
    id: EntityId,
    expectedrevision: EntityRevision,
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[EntityDeleteMutationOutcome]

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
