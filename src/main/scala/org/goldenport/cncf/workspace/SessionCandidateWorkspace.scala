package org.goldenport.cncf.workspace

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.processexecution.{GitExecutionOutcome, GitExecutionResult, GitObjectId, GitOperation, ProcessExecutionTermination}

/** Pure logical record retained by the workflow runtime, without host paths. */
final case class SessionCandidateWorkspaceRecord(component: String, source: String, parent: String,
  alias: String, session: String, candidate: String)

/** The record identifies a selection; it does not grant host authority. */
final class SessionCandidateWorkspaceReference private[workspace] (
  val record: SessionCandidateWorkspaceRecord
)

object SessionCandidateWorkspaceReference {
  def fromRecordC(record: SessionCandidateWorkspaceRecord): Consequence[SessionCandidateWorkspaceReference] = for {
    _ <- if (record != null) Consequence.unit else Consequence.configurationInvalid("Missing candidate reference")
    _ <- ComponentId.parseC(record.component)
    source <- WorkspaceId.parseC(record.source)
    parent <- WorkspaceId.parseC(record.parent)
    alias <- WorkspaceId.parseC(record.alias)
    _ <- if (source != parent && source != alias && parent != alias) Consequence.unit
      else Consequence.configurationInvalid("Candidate reference requires distinct logical workspaces")
    _ <- SessionCandidateWorkspace.validIdentityC(record.session)
    _ <- SessionCandidateWorkspace.validIdentityC(record.candidate)
  } yield new SessionCandidateWorkspaceReference(record)
}

/** Effect knowledge is supplied by the runtime's retained native Git result. */
enum SessionCandidateWorkspaceState {
  case Empty
  case Materialized(result: GitExecutionResult[Unit], root: GitExecutionResult[String], head: GitExecutionResult[GitObjectId])
  case Unknown
}

final case class SessionCandidateWorkspaceSelection(
  reference: SessionCandidateWorkspaceReference,
  context: ExecutionContext,
  alias: WorkspaceId
)

object SessionCandidateWorkspace {
  /** Allocate exclusively under the configured parent; existing candidates are never adopted here. */
  private[cncf] def prepareC(context: ExecutionContext, source: WorkspaceId, parent: WorkspaceId, alias: WorkspaceId,
    session: String, candidate: String): Consequence[SessionCandidateWorkspaceSelection] = for {
    _ <- validIdentityC(session)
    _ <- validIdentityC(candidate)
    _ <- if (source.value != null && source != alias && source != parent) Consequence.unit else _invalid_c
    admission <- _admission_c(context)
    scope = context.cncfCore.scope
    _ <- scope.processExecutionAdmissionOption.map(_ => Consequence.unit)
      .getOrElse(Consequence.serviceUnavailable("Candidate process admission is not configured"))
    _ <- admission.candidatePolicyC(scope, parent, alias)
    owner <- WorkspaceExecutionAdmission.componentIdC(scope)
    parentbinding <- admission.admitC(scope, parent, WorkspacePermission.Manage)
    root <- parentbinding.workingDirectoryC
    sourcebinding <- admission.admitC(scope, source, WorkspacePermission.Manage)
    sourceroot <- sourcebinding.workingDirectoryC
    _ <- if (!root.startsWith(sourceroot) && !sourceroot.startsWith(root)) Consequence.unit
      else Consequence.stateConflict("Candidate parent must be separate from the source workspace")
    _ <- _create_c(root, owner.name, session, candidate)
    _ <- parentbinding.workingDirectoryC
    reference <- SessionCandidateWorkspaceReference.fromRecordC(SessionCandidateWorkspaceRecord(owner.name,
      source.value, parent.value, alias.value, session, candidate))
    selection <- _select_c(context, reference, SessionCandidateWorkspaceState.Empty)
  } yield selection

  /** Reacquire only a runtime-retained reference and an explicitly known effect state. */
  def reacquireC(context: ExecutionContext, reference: SessionCandidateWorkspaceReference,
    expected: SessionCandidateWorkspaceRecord, state: SessionCandidateWorkspaceState): Consequence[SessionCandidateWorkspaceSelection] = for {
    _ <- _expected_c(reference, expected)
    selection <- _select_c(context, reference, state)
  } yield selection

  /** An observation-only context permits the host-granted Git Root and Head intents. */
  def observationContextC(context: ExecutionContext,
    reference: SessionCandidateWorkspaceReference, expected: SessionCandidateWorkspaceRecord): Consequence[ExecutionContext] = for {
    _ <- _expected_c(reference, expected)
    scoped <- _scoped_c(context, reference, true)
  } yield scoped

  private def _expected_c(reference: SessionCandidateWorkspaceReference,
    expected: SessionCandidateWorkspaceRecord): Consequence[Unit] =
    if (reference != null && expected != null && reference.record == expected) Consequence.unit
    else Consequence.stateConflict("Candidate reference does not match the retained selection")

  private def _select_c(context: ExecutionContext, reference: SessionCandidateWorkspaceReference,
    state: SessionCandidateWorkspaceState): Consequence[SessionCandidateWorkspaceSelection] = for {
    _ <- if (reference != null && state != null) Consequence.unit else _invalid_c
    selected <- _binding_c(context, reference)
    (policy, binding) = selected
    path <- binding.workingDirectoryC
    _ <- _state_c(reference, path, state)
    scoped <- _scoped_c(context, reference, false)
  } yield SessionCandidateWorkspaceSelection(reference, scoped, policy.alias)

  private def _scoped_c(context: ExecutionContext, reference: SessionCandidateWorkspaceReference,
    observation: Boolean): Consequence[ExecutionContext] = for {
    _ <- if (reference != null) Consequence.unit else _invalid_c
    admission <- _admission_c(context)
    scope = context.cncfCore.scope
    selected <- _binding_c(context, reference)
    (policy, binding) = selected
    original <- admission.admitC(scope, policy.alias, WorkspacePermission.Manage)
    scopedworkspace <- admission.candidateBindingC(scope, policy, binding, observation)
    process <- scope.processExecutionAdmissionOption.map(Consequence.success)
      .getOrElse(Consequence.serviceUnavailable("Candidate process admission is not configured"))
    scopedprocess <- if (observation) process.candidateObservationC(original, binding)
      else process.rebindWorkspaceC(original, binding)
    scoped = ScopeContext.Instance(scope.core.copy(parent = Some(scope),
      workspaceExecutionAdmissionOption = Some(scopedworkspace), processExecutionAdmissionOption = Some(scopedprocess)))
  } yield context.withScope(scoped)

  private def _binding_c(context: ExecutionContext, reference: SessionCandidateWorkspaceReference):
      Consequence[(SessionCandidateWorkspacePolicy, WorkspaceBinding)] = for {
    _ <- if (reference != null) Consequence.unit else _invalid_c
    record = reference.record
    admission <- _admission_c(context)
    scope = context.cncfCore.scope
    owner <- WorkspaceExecutionAdmission.componentIdC(scope)
    _ <- if (owner.name == record.component) Consequence.unit else _invalid_c
    source <- WorkspaceId.parseC(record.source)
    parent <- WorkspaceId.parseC(record.parent)
    alias <- WorkspaceId.parseC(record.alias)
    policy <- admission.candidatePolicyC(scope, parent, alias)
    parentbinding <- admission.admitC(scope, parent, WorkspacePermission.Manage)
    root <- parentbinding.workingDirectoryC
    sourcebinding <- admission.admitC(scope, source, WorkspacePermission.Manage)
    sourceroot <- sourcebinding.workingDirectoryC
    _ <- if (!root.startsWith(sourceroot) && !sourceroot.startsWith(root)) Consequence.unit
      else Consequence.stateConflict("Candidate parent must be separate from the source workspace")
    path <- _existing_c(root, owner.name, record.session, record.candidate)
    _ <- parentbinding.workingDirectoryC
    binding <- WorkspaceBinding.createC(alias, path)
  } yield policy -> binding

  private def _state_c(reference: SessionCandidateWorkspaceReference, path: Path,
    state: SessionCandidateWorkspaceState): Consequence[Unit] = state match {
    case SessionCandidateWorkspaceState.Empty => try {
      val entries = Files.list(path)
      val empty = try !entries.findAny().isPresent finally entries.close()
      if (empty) Consequence.unit else Consequence.stateConflict("Candidate workspace is occupied")
    } catch { case NonFatal(_) => WorkspaceBinding.unavailableC }
    case SessionCandidateWorkspaceState.Materialized(result, root, head) =>
      if (result != null && result.target != null && result.target.workspaceId.value == reference.record.source &&
        result.outcome == GitExecutionOutcome.Succeeded && result.value.contains(()) &&
        result.processResult != null && result.processResult.termination == ProcessExecutionTermination.Exited(0) &&
        (result.operation match {
          case GitOperation.MaterializePurposeCandidate(_, candidate, destination) =>
            destination.value == reference.record.alias && candidate != null &&
              root != null && root.target != null && root.target.workspaceId == destination && root.operation == GitOperation.Root &&
              root.outcome == GitExecutionOutcome.Succeeded && root.value.contains(path.toString) &&
              root.processResult != null && root.processResult.termination == ProcessExecutionTermination.Exited(0) &&
              head != null && head.target != null && head.target.workspaceId == destination && head.operation == GitOperation.Head &&
              head.outcome == GitExecutionOutcome.Succeeded && head.value.contains(candidate.commit) &&
              head.processResult != null && head.processResult.termination == ProcessExecutionTermination.Exited(0)
          case _ => false
        }) && Files.isRegularFile(path.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) Consequence.unit
      else Consequence.stateConflict("Candidate materialization result is unavailable or foreign")
    case SessionCandidateWorkspaceState.Unknown => Consequence.stateConflict("Candidate effect state is unknown")
  }

  private def _admission_c(context: ExecutionContext): Consequence[WorkspaceExecutionAdmission] =
    if (context == null) _invalid_c else context.workspaceExecutionAdmissionOption.map(Consequence.success)
      .getOrElse(Consequence.serviceUnavailable("Session candidate workspace admission is not configured"))

  private[workspace] def validIdentityC(value: String): Consequence[Unit] =
    if (value != null && value.nonEmpty && value.length <= 1024 &&
      StandardCharsets.UTF_8.newEncoder().canEncode(value)) Consequence.unit else _invalid_c

  private def _segments(value: String): Vector[String] =
    value.getBytes(StandardCharsets.UTF_8).map(x => f"${x & 0xff}%02x").mkString.grouped(120).toVector

  private def _parts(owner: String, session: String, candidate: String): Vector[String] =
    Vector("component") ++ _segments(owner) ++ Vector("session") ++ _segments(session) ++
      Vector("candidate") ++ _segments(candidate) ++ Vector("entry")

  private def _existing_c(root: Path, owner: String, session: String, candidate: String): Consequence[Path] = try {
    val result = _parts(owner, session, candidate).foldLeft(root) { (path, part) =>
      val next = path.resolve(part)
      if (!Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(next))
        throw new IllegalStateException("candidate unavailable")
      next
    }
    Consequence.success(result)
  } catch { case NonFatal(_) => WorkspaceBinding.unavailableC }

  private def _create_c(root: Path, owner: String, session: String, candidate: String): Consequence[Path] = try {
    val parts = _parts(owner, session, candidate)
    var current = root
    parts.dropRight(1).foreach { part =>
      current = current.resolve(part)
      try Files.createDirectory(current) catch { case _: java.nio.file.FileAlreadyExistsException => () }
      if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(current))
        throw new IllegalStateException("unavailable candidate parent")
    }
    Consequence.success(Files.createDirectory(current.resolve(parts.last)))
  } catch {
    case _: java.nio.file.FileAlreadyExistsException => Consequence.stateConflict("Candidate workspace is occupied")
    case NonFatal(_) => Consequence.serviceUnavailable("Candidate workspace parent is unavailable")
  }

  private def _invalid_c[A]: Consequence[A] =
    Consequence.argumentPolicyViolation("candidate", "workspace.session-candidate", "valid retained candidate selection", "invalid")
}
