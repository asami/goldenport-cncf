package org.goldenport.cncf.action

import java.nio.file.Path
import java.nio.charset.Charset
import java.time.{Clock, Duration, Instant, ZonedDateTime}
import cats.free.Free
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import org.goldenport.observation.Cause
import org.goldenport.observation.Descriptor
import org.goldenport.id.UniversalId
import org.goldenport.record.Record
import org.goldenport.protocol.Property
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.http.HttpResponse
import org.goldenport.process.{ShellCommand, ShellCommandResult}
import org.goldenport.cncf.context.{
  ExecutionContext,
  ExecutionSchedulerMode,
  GlobalRuntimeContext,
  ScopeContext
}
import org.goldenport.cncf.resource.{
  ResourceContent,
  ResourceReference,
  ResourceTreeLimits,
  ResourceTreeQuery,
  ResourceTreeQueryResult,
  ResourceTreeReference,
  ResourceTreeSnapshot
}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkAuthorization}
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.embedded.{EmbeddedDataStore, EmbeddedStatement, EmbeddedUpdateResult}
import org.goldenport.cncf.security.{
  AggregateAuthorization,
  EntityAbacCondition,
  EntityAccessMode,
  EntityAccessRelation,
  EntityApplicationDomain,
  EntityAuthorizationProfile,
  EntityOperationKind,
  EntityUsageKind,
  OperationAccessPolicy,
  ServiceOperationModel
}
import org.goldenport.cncf.Program
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.goldenport.cncf.datastore.{
  ComponentDataStore,
  DataStore,
  DataStoreComponentOwner
}
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityConditionalTransition,
  EntityMutationExecutionPolicy,
  EntityConditionalTransitionResult,
  RevisionPreconditionPolicy,
  EntitySuccessorIntent
}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.EntityPersistentCreate
import org.goldenport.cncf.entity.EntityPersistentUpdate
import org.goldenport.cncf.entity.EntityRecordSnapshot
import org.goldenport.cncf.entity.EntityRevisionCarrier
import org.goldenport.cncf.entity.EntityRevisionRepresentation
import org.goldenport.cncf.entity.EntitySnapshot
import org.goldenport.cncf.entity.EntityQuery
import org.goldenport.cncf.entity.EntitySearchScope
import org.goldenport.cncf.entity.EntityIdentityScope
import org.goldenport.cncf.entity.EntityVisibilityScope
import org.goldenport.cncf.entity.EntityCreateOptions
import org.goldenport.cncf.entity.CreateResult
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.entity.SimpleEntityStorageShapePolicy
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
import org.goldenport.value.{ContentAttributes, ContentReferenceOccurrence}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.directive.SearchResult
import org.goldenport.cncf.entity.aggregate.{
  AggregateEditContext,
  AggregateEditLockScope,
  AggregateEditOwner
}
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.action.AggregateBehavior
import org.goldenport.cncf.information.InformationSpace
import org.goldenport.cncf.information.entity.Information
import org.goldenport.cncf.information.value.{
  InformationConflict,
  InformationFieldEvent,
  InformationIdentityBinding,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationValidationIssue
}
import org.goldenport.cncf.knowledge.{KnowledgeFrameId, KnowledgeWorkingSetSnapshot}
import org.goldenport.cncf.observability.{
  CallTreeValueSummary,
  ConclusionDiagnostics,
  DslChokepointContext,
  DslChokepointPhase,
  DslChokepointRunner
}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.configuration.Configuration
import org.goldenport.configuration.ConfigurationTrace
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.configuration.source.file.ConfigTextDecoder
import org.goldenport.cncf.config.RuntimeFileConfigLoader
import org.goldenport.cncf.config.{
  ComponentConfigurationAccess,
  ComponentConfigurationKey,
  ComponentConfigurationResolution,
  ComponentConfigurationSources
}
import org.goldenport.cncf.processexecution.{
  GitTarget, GitOperation, GitExecutionResult, GitObjectId, GitPath, GitBranchName, GitSelectionId,
  GitRemoteTip, GitHistoryCounts, GitCommitMetadata, GitStatusEntry, GitIndexEntry, GitBlobContent, GitDiff, GitIndexCheck,
  ProcessExecutionAdmission,
  ProcessExecutionRequest,
  ProcessExecutionResult,
  ResolvedProcessExecution,
  SbtExecutionResult,
  SbtOperation,
  SbtTarget
}

/*
 * @since   Sep. 28, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
trait BehaviorProcessExecutionPart extends BehaviorFeaturePart { self: Behavior.Core.Holder =>

  protected final def git_branch(target: GitTarget): ExecUowM[GitExecutionResult[String]] =
    _git_exec(target, GitOperation.Branch)

  protected final def git_root(target: GitTarget): ExecUowM[GitExecutionResult[String]] =
    _git_exec(target, GitOperation.Root)

  protected final def git_remote_tip(target: GitTarget): ExecUowM[GitExecutionResult[GitRemoteTip]] =
    _git_exec(target, GitOperation.RemoteTip)

  protected final def git_head(target: GitTarget): ExecUowM[GitExecutionResult[GitObjectId]] =
    _git_exec(target, GitOperation.Head)

  protected final def git_tree(target: GitTarget): ExecUowM[GitExecutionResult[GitObjectId]] =
    _git_exec(target, GitOperation.Tree)

  protected final def git_status(target: GitTarget): ExecUowM[GitExecutionResult[Vector[GitStatusEntry]]] =
    _git_exec(target, GitOperation.Status)

  protected final def git_shallow(target: GitTarget): ExecUowM[GitExecutionResult[Boolean]] =
    _git_exec(target, GitOperation.Shallow)

  protected final def git_replacements(target: GitTarget): ExecUowM[GitExecutionResult[Vector[GitObjectId]]] =
    _git_exec(target, GitOperation.Replacements)

  protected final def git_merge_head(target: GitTarget): ExecUowM[GitExecutionResult[Option[GitObjectId]]] =
    _git_exec(target, GitOperation.MergeHead)

  protected final def git_tracking_tip(target: GitTarget): ExecUowM[GitExecutionResult[Option[GitObjectId]]] =
    _git_exec(target, GitOperation.TrackingTip)

  protected final def git_relation(target: GitTarget): ExecUowM[GitExecutionResult[GitHistoryCounts]] =
    _git_exec(target, GitOperation.Relation)

  protected final def git_merge_base(target: GitTarget): ExecUowM[GitExecutionResult[Option[GitObjectId]]] =
    _git_exec(target, GitOperation.MergeBase)

  protected final def git_stage_full_state(target: GitTarget): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.StageFullState)

  protected final def git_index_tree(target: GitTarget): ExecUowM[GitExecutionResult[GitObjectId]] =
    _git_exec(target, GitOperation.IndexTree)

  protected final def git_unmerged_index(target: GitTarget): ExecUowM[GitExecutionResult[Vector[GitIndexEntry]]] =
    _git_exec(target, GitOperation.UnmergedIndex)

  protected final def git_fetch(target: GitTarget): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.Fetch)

  protected final def git_fast_forward(target: GitTarget, tip: GitObjectId): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.FastForward(tip))

  protected final def git_pending_merge(target: GitTarget, tip: GitObjectId): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.PendingMerge(tip))

  protected final def git_commit_pending(target: GitTarget): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.CommitPending)

  protected final def git_push(target: GitTarget, tip: GitObjectId, branch: GitBranchName): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.Push(tip, branch))

  protected final def git_create_checkpoint(target: GitTarget, tree: GitObjectId, parent: GitObjectId): ExecUowM[GitExecutionResult[GitObjectId]] =
    _git_exec(target, GitOperation.CreateCheckpoint(tree, parent))

  protected final def git_install_checkpoint(target: GitTarget, commit: GitObjectId, parent: GitObjectId): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.InstallCheckpoint(commit, parent))

  protected final def git_commit_metadata(target: GitTarget, commit: GitObjectId): ExecUowM[GitExecutionResult[GitCommitMetadata]] =
    _git_exec(target, GitOperation.CommitMetadata(commit))

  protected final def git_blob_content(target: GitTarget, blob: GitObjectId): ExecUowM[GitExecutionResult[GitBlobContent]] =
    _git_exec(target, GitOperation.BlobContent(blob))

  protected final def git_check_pending_index(target: GitTarget, parent: GitObjectId): ExecUowM[GitExecutionResult[GitIndexCheck]] =
    _git_exec(target, GitOperation.CheckPendingIndex(parent))

  protected final def git_pending_diff(target: GitTarget, parent: GitObjectId): ExecUowM[GitExecutionResult[GitDiff]] =
    _git_exec(target, GitOperation.PendingDiff(parent))

  protected final def git_pending_paths(target: GitTarget, parent: GitObjectId): ExecUowM[GitExecutionResult[Vector[GitPath]]] =
    _git_exec(target, GitOperation.PendingPaths(parent))

  protected final def git_stage_selected(target: GitTarget, selectionId: GitSelectionId): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.StageSelected(selectionId))

  protected final def git_stage_selected_all(target: GitTarget, selectionId: GitSelectionId): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.StageSelectedAll(selectionId))

  protected final def git_commit_selected(target: GitTarget, selectionId: GitSelectionId): ExecUowM[GitExecutionResult[Unit]] =
    _git_exec(target, GitOperation.CommitSelected(selectionId))

  private def _git_exec[A](target: GitTarget, operation: GitOperation[A]): ExecUowM[GitExecutionResult[A]] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.GitExec(target, operation)))


  protected final def sbt_compile(target: SbtTarget): ExecUowM[SbtExecutionResult] =
    _sbt_exec(target, SbtOperation.Compile)

  protected final def sbt_test(target: SbtTarget): ExecUowM[SbtExecutionResult] =
    _sbt_exec(target, SbtOperation.Test)

  protected final def sbt_test_only(target: SbtTarget): ExecUowM[SbtExecutionResult] =
    _sbt_exec(target, SbtOperation.TestOnly)

  private def _sbt_exec(target: SbtTarget, operation: SbtOperation): ExecUowM[SbtExecutionResult] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.SbtExec(target, operation)))

  /** Resolves a provider's logical request through the runtime-owned admission service before
    * creating the resolved-only UnitOfWork operation.
   */
  protected final def process_exec(
    request: ProcessExecutionRequest
  ): ExecUowM[ProcessExecutionResult] =
    exec_from(ProcessExecutionAdmission.resolveC(execution_context.cncfCore.scope, request)).flatMap(
      process_exec
    )

  protected final def process_exec_c(
    request: ProcessExecutionRequest
  )(using uow: UnitOfWork): Consequence[ProcessExecutionResult] =
    ProcessExecutionAdmission.resolveC(execution_context.cncfCore.scope, request).flatMap(
      execution => process_exec_c(execution)
    )

  protected final def process_exec_or_throw(
    request: ProcessExecutionRequest
  )(using uow: UnitOfWork): ProcessExecutionResult =
    process_exec_c(request).TAKE

  /** Creates a Process Execution intent in the canonical UnitOfWork algebra. The input is already
    * capability-admitted; components never select a host executable or invoke a driver directly.
   */
  protected final def process_exec(
    execution: ResolvedProcessExecution
  ): ExecUowM[ProcessExecutionResult] =
    ConsequenceT.liftF(Free.liftF(_op_process_exec(execution)))

  protected final def process_exec_c(
    execution: ResolvedProcessExecution
  )(using uow: UnitOfWork): Consequence[ProcessExecutionResult] =
    exec_c(_op_process_exec(execution))

  protected final def process_exec_or_throw(
    execution: ResolvedProcessExecution
  )(using uow: UnitOfWork): ProcessExecutionResult =
    exec_or_throw(_op_process_exec(execution))

  private def _op_process_exec(
    execution: ResolvedProcessExecution
  ): UnitOfWorkOp[ProcessExecutionResult] =
    UnitOfWorkOp.ProcessExec(execution)
}
