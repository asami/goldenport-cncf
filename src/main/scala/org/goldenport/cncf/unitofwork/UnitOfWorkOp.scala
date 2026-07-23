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
import org.goldenport.cncf.blob.{ContentReferenceAttachResult, ContentReferenceContent, ContentReferenceNormalizeResult, ContentRenderResult, InlineImageAttachResult, InlineImageContent, InlineImageNormalizeResult, InlineImageOccurrence}
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
 * @version Jul. 23, 2026
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

  final case class EntityStoreLoad[T](
    id: EntityId,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None,
    visibilityScope: Option[EntityVisibilityScope] = None
  ) extends UnitOfWorkOp[Option[T]]

  // Special-use direct path to EntityStoreSpace (bypasses EntitySpace/MemoryRealm).
  final case class EntityStoreLoadDirect[T](
    id: EntityId,
    tc: EntityPersistent[T]
  ) extends UnitOfWorkOp[Option[T]]

  final case class EntityStoreSave[T](
    entity: T,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreUpsert[T](
    entity: T,
    id: EntityId,
    tc: EntityPersistentCreate[T],
    options: EntityCreateOptions = EntityCreateOptions.default,
    createAuthorization: Option[UnitOfWorkAuthorization] = None,
    updateAuthorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[CreateResult[T]]

  final case class EntityStoreUpdate[T](
    entity: T,
    tc: EntityPersistent[T],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  // Patch-oriented update route for cozy-generated update shapes (no id field in patch).
  final case class EntityStoreUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    authorization: Option[UnitOfWorkAuthorization] = None
  ) extends UnitOfWorkOp[Unit]

  final case class EntityStoreDelete(
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
