package org.goldenport.cncf.action

import org.goldenport.protocol.*
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.record.Record
import org.goldenport.text.Presentable

/*
 * @since   Apr. 12, 2025
 *  version Jan.  1, 2026
 *  version Jan. 22, 2026
 *  version Feb. 27, 2026
 *  version Mar. 24, 2026
 * @version May. 31, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class Action extends OperationRequest with Presentable {
  def name: String = request.name
  def createCall(core: ActionCall.Core): ActionCall

  override def print: String = s"Action(${request.name})"
  override def display: String = request.name
  override def show: String = display

  def arguments: List[Argument] = request.arguments
  def switches: List[Switch] = request.switches
  def properties: List[Property] = request.properties
  def args: List[String] = request.args
}

abstract class CommandAction(
) extends Action {
  def commandExecutionMode: CommandExecutionMode =
    CommandExecutionMode.Sync
}

// abstract class Command(
// ) extends CommandAction {
// }
object CommandAction {
  // case class Instance(
  //   name: String,
  //   core: OperationRequest.Core
  // ) extends Command() {
  //   def createCall(core: ActionCall.Core): ActionCall = ProcedureActionCall
  // }
}

enum CommandExecutionMode {
  case Sync
  case JobSync
  case JobAsync
  case JobSyncWithAsyncCont
  case AsyncJob
  case AsyncJobAndAwait
  case SyncJob
  case SyncJobAsyncInterface
  case SyncDirectNoJob
}

enum CommandInterfaceMode {
  case Sync
  case Async
}

object CommandInterfaceMode {
  def parse(value: String): Option[CommandInterfaceMode] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "sync" | "synchronous" => Some(Sync)
      case "async" | "asynchronous" => Some(Async)
      case _ => None
    }
}

enum CommandJobRunMode {
  case Sync
  case Async
}

object CommandJobRunMode {
  def parse(value: String): Option[CommandJobRunMode] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "sync" | "synchronous" => Some(Sync)
      case "async" | "asynchronous" => Some(Async)
      case _ => None
    }
}

enum CallerTransactionPolicy {
  case JoinCaller
  case NewTransaction

  def print: String = this match {
    case CallerTransactionPolicy.JoinCaller => "join-caller"
    case CallerTransactionPolicy.NewTransaction => "new-transaction"
  }
}

object CallerTransactionPolicy {
  def parse(value: String): Option[CallerTransactionPolicy] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "join-caller" | "joincaller" | "join" | "caller" =>
        Some(JoinCaller)
      case "new-transaction" | "newtransaction" | "new" | "own" =>
        Some(NewTransaction)
      case _ =>
        None
    }
}

enum OperationEventTransactionRequirement {
  case Required
  case BestEffort
  case Ignore

  def print: String = this match {
    case OperationEventTransactionRequirement.Required => "required"
    case OperationEventTransactionRequirement.BestEffort => "best-effort"
    case OperationEventTransactionRequirement.Ignore => "ignore"
  }
}

object OperationEventTransactionRequirement {
  def parse(value: String): Option[OperationEventTransactionRequirement] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "required" | "require" | "same-transaction" | "same_transaction" =>
        Some(Required)
      case "best-effort" | "besteffort" | "best_effort" | "optional" =>
        Some(BestEffort)
      case "ignore" | "ignored" | "none" =>
        Some(Ignore)
      case _ =>
        None
    }
}

enum JobTransactionScope {
  case PerTask
  case WholeJob

  def print: String = this match {
    case JobTransactionScope.PerTask => "per-task"
    case JobTransactionScope.WholeJob => "whole-job"
  }
}

object JobTransactionScope {
  def parse(value: String): Option[JobTransactionScope] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "per-task" | "pertask" | "task" =>
        Some(PerTask)
      case "whole-job" | "wholejob" | "job" =>
        Some(WholeJob)
      case _ =>
        None
    }
}

enum TaskTransactionRole {
  case Own
  case Join

  def print: String = this match {
    case TaskTransactionRole.Own => "own"
    case TaskTransactionRole.Join => "join"
  }
}

final case class CommandExecutionPolicy(
  interfaceMode: CommandInterfaceMode = CommandInterfaceMode.Sync,
  jobRunMode: CommandJobRunMode = CommandJobRunMode.Sync,
  managedByJob: Boolean = false,
  asyncContinuation: Boolean = false,
  callerTransactionPolicy: CallerTransactionPolicy = CallerTransactionPolicy.JoinCaller,
  eventTransactionRequirement: OperationEventTransactionRequirement = OperationEventTransactionRequirement.Required,
  jobTransactionScope: JobTransactionScope = JobTransactionScope.PerTask,
  continuationEventTransactionRequirement: OperationEventTransactionRequirement = OperationEventTransactionRequirement.Required
) {
  def modeLabel: String =
    if (!managedByJob)
      CommandExecutionMode.Sync.toString
    else if (asyncContinuation && interfaceMode == CommandInterfaceMode.Sync && jobRunMode == CommandJobRunMode.Sync)
      CommandExecutionMode.JobSyncWithAsyncCont.toString
    else
      (interfaceMode, jobRunMode) match {
        case (CommandInterfaceMode.Async, CommandJobRunMode.Async) =>
          CommandExecutionMode.JobAsync.toString
        case (CommandInterfaceMode.Sync, CommandJobRunMode.Sync) =>
          CommandExecutionMode.JobSync.toString
        case (CommandInterfaceMode.Sync, CommandJobRunMode.Async) =>
          CommandExecutionMode.AsyncJobAndAwait.toString
        case (CommandInterfaceMode.Async, CommandJobRunMode.Sync) =>
          CommandExecutionMode.SyncJobAsyncInterface.toString
      }

  def legacyModeLabel: String =
    if (!managedByJob)
      CommandExecutionMode.SyncDirectNoJob.toString
    else if (asyncContinuation)
      CommandExecutionMode.SyncJob.toString
    else
      (interfaceMode, jobRunMode) match {
        case (CommandInterfaceMode.Async, CommandJobRunMode.Async) =>
          CommandExecutionMode.AsyncJob.toString
        case (CommandInterfaceMode.Sync, CommandJobRunMode.Async) =>
          CommandExecutionMode.AsyncJobAndAwait.toString
        case (CommandInterfaceMode.Sync, CommandJobRunMode.Sync) =>
          CommandExecutionMode.SyncJob.toString
        case (CommandInterfaceMode.Async, CommandJobRunMode.Sync) =>
          CommandExecutionMode.SyncJobAsyncInterface.toString
      }

  def isDeprecatedCompatibilityMode: Boolean =
    managedByJob && !asyncContinuation && (
      (interfaceMode == CommandInterfaceMode.Sync && jobRunMode == CommandJobRunMode.Async) ||
      (interfaceMode == CommandInterfaceMode.Async && jobRunMode == CommandJobRunMode.Sync)
    )

  def toRecord: Record =
    Record.data(
      "mode" -> modeLabel,
      "interfaceMode" -> interfaceMode.toString.toLowerCase(java.util.Locale.ROOT),
      "jobRunMode" -> jobRunMode.toString.toLowerCase(java.util.Locale.ROOT),
      "managedByJob" -> managedByJob,
      "asyncContinuation" -> asyncContinuation,
      "callerTransactionPolicy" -> callerTransactionPolicy.print,
      "eventTransactionRequirement" -> eventTransactionRequirement.print,
      "jobTransactionScope" -> jobTransactionScope.print,
      "continuationEventTransactionRequirement" -> continuationEventTransactionRequirement.print,
      "continuationMode" -> (if (asyncContinuation) Some("event-async-same-job-task") else None),
      "legacyMode" -> legacyModeLabel,
      "modeStatus" -> (if (isDeprecatedCompatibilityMode) "deprecated-compatibility" else "canonical"),
      "deprecated" -> isDeprecatedCompatibilityMode
    )
}

object CommandExecutionPolicy {
  val default: CommandExecutionPolicy = CommandExecutionPolicy()

  def fromLegacyMode(mode: CommandExecutionMode): CommandExecutionPolicy =
    mode match {
      case CommandExecutionMode.Sync =>
        default
      case CommandExecutionMode.JobSync =>
        CommandExecutionPolicy(CommandInterfaceMode.Sync, CommandJobRunMode.Sync, managedByJob = true)
      case CommandExecutionMode.JobAsync =>
        CommandExecutionPolicy(CommandInterfaceMode.Async, CommandJobRunMode.Async, managedByJob = true)
      case CommandExecutionMode.JobSyncWithAsyncCont =>
        CommandExecutionPolicy(CommandInterfaceMode.Sync, CommandJobRunMode.Sync, managedByJob = true, asyncContinuation = true)
      case CommandExecutionMode.AsyncJob =>
        CommandExecutionPolicy(CommandInterfaceMode.Async, CommandJobRunMode.Async, managedByJob = true)
      case CommandExecutionMode.AsyncJobAndAwait =>
        CommandExecutionPolicy(CommandInterfaceMode.Sync, CommandJobRunMode.Async, managedByJob = true)
      case CommandExecutionMode.SyncJob =>
        CommandExecutionPolicy(CommandInterfaceMode.Sync, CommandJobRunMode.Sync, managedByJob = true)
      case CommandExecutionMode.SyncJobAsyncInterface =>
        CommandExecutionPolicy(CommandInterfaceMode.Async, CommandJobRunMode.Sync, managedByJob = true)
      case CommandExecutionMode.SyncDirectNoJob =>
        default
    }

  def fromLegacyExecution(value: String): Option[CommandExecutionPolicy] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "sync" | "sync-direct" | "sync-direct-no-job" =>
        Some(default)
      case "job-sync" =>
        Some(fromLegacyMode(CommandExecutionMode.JobSync))
      case "job-async" =>
        Some(fromLegacyMode(CommandExecutionMode.JobAsync))
      case "job-sync-with-async-cont" | "job-sync-with-async-continuation" =>
        Some(fromLegacyMode(CommandExecutionMode.JobSyncWithAsyncCont))
      case "async" | "async-job" =>
        Some(fromLegacyMode(CommandExecutionMode.JobAsync))
      case "async-job-and-await" =>
        Some(fromLegacyMode(CommandExecutionMode.AsyncJobAndAwait))
      case "sync-job" =>
        Some(fromLegacyMode(CommandExecutionMode.JobSync))
      case "sync-job-async-interface" =>
        Some(fromLegacyMode(CommandExecutionMode.SyncJobAsyncInterface))
      case _ => None
    }

  def parse(value: String): Option[CommandExecutionPolicy] =
    fromLegacyExecution(value).orElse {
      val rawtokens = value
        .split("[,;]")
        .toVector
        .map(_.trim)
        .filter(_.nonEmpty)
      val pairs = rawtokens.map { token =>
        val i = token.indexOf('=')
        if (i < 0)
          None
        else
          Some(token.substring(0, i).trim.toLowerCase(java.util.Locale.ROOT) -> token.substring(i + 1).trim)
      }
      val allowedkeys = Set(
        "interface",
        "interface-mode",
        "interfacemode",
        "job-run",
        "job-run-mode",
        "jobrun",
        "jobrunmode",
        "managed",
        "managed-by-job",
        "managedbyjob",
        "async-cont",
        "async-continuation",
        "asynccontinuation",
        "continuation",
        "continuation-mode",
        "caller-transaction",
        "caller-transaction-policy",
        "callertransactionpolicy",
        "event-transaction",
        "event-transaction-requirement",
        "eventtransactionrequirement",
        "job-transaction-scope",
        "jobtransactionscope",
        "continuation-event-transaction",
        "continuation-event-transaction-requirement",
        "continuationeventtransactionrequirement"
      )
      if (rawtokens.isEmpty || pairs.exists(_.isEmpty))
        None
      else {
        val tokens = pairs.flatten.toMap
        if (tokens.keys.exists(x => !allowedkeys.contains(x)))
          None
        else {
          val rawinterfacemode = tokens.get("interface").orElse(tokens.get("interface-mode")).orElse(tokens.get("interfacemode"))
          val rawjobrunmode = tokens.get("job-run").orElse(tokens.get("job-run-mode")).orElse(tokens.get("jobrun")).orElse(tokens.get("jobrunmode"))
          val rawmanagedbyjob = tokens.get("managed").orElse(tokens.get("managed-by-job")).orElse(tokens.get("managedbyjob"))
          val rawasynccont = tokens.get("async-cont").orElse(tokens.get("async-continuation")).orElse(tokens.get("asynccontinuation"))
            .orElse(tokens.get("continuation")).orElse(tokens.get("continuation-mode"))
          val rawcallertransaction = tokens.get("caller-transaction").orElse(tokens.get("caller-transaction-policy")).orElse(tokens.get("callertransactionpolicy"))
          val raweventtransaction = tokens.get("event-transaction").orElse(tokens.get("event-transaction-requirement")).orElse(tokens.get("eventtransactionrequirement"))
          val rawjobtransactionscope = tokens.get("job-transaction-scope").orElse(tokens.get("jobtransactionscope"))
          val rawcontinuationeventtransaction = tokens.get("continuation-event-transaction").orElse(tokens.get("continuation-event-transaction-requirement")).orElse(tokens.get("continuationeventtransactionrequirement"))
          val interfacemode = rawinterfacemode.map(CommandInterfaceMode.parse)
          val jobrunmode = rawjobrunmode.map(CommandJobRunMode.parse)
          val managedbyjob = rawmanagedbyjob.map(_parse_boolean)
          val asynccont = rawasynccont.map(_parse_continuation)
          val callertransaction = rawcallertransaction.map(CallerTransactionPolicy.parse)
          val eventtransaction = raweventtransaction.map(OperationEventTransactionRequirement.parse)
          val jobtransactionscope = rawjobtransactionscope.map(JobTransactionScope.parse)
          val continuationeventtransaction = rawcontinuationeventtransaction.map(OperationEventTransactionRequirement.parse)
          if (
            interfacemode.exists(_.isEmpty) ||
            jobrunmode.exists(_.isEmpty) ||
            managedbyjob.exists(_.isEmpty) ||
            asynccont.exists(_.isEmpty) ||
            callertransaction.exists(_.isEmpty) ||
            eventtransaction.exists(_.isEmpty) ||
            jobtransactionscope.exists(_.isEmpty) ||
            continuationeventtransaction.exists(_.isEmpty)
          )
            None
          else {
            val policy = CommandExecutionPolicy(
              interfacemode.flatten.getOrElse(default.interfaceMode),
              jobrunmode.flatten.getOrElse(default.jobRunMode),
              managedbyjob.flatten.getOrElse(asynccont.flatten.exists(identity)),
              asynccont.flatten.getOrElse(default.asyncContinuation),
              callertransaction.flatten.getOrElse(default.callerTransactionPolicy),
              eventtransaction.flatten.getOrElse(default.eventTransactionRequirement),
              jobtransactionscope.flatten.getOrElse(default.jobTransactionScope),
              continuationeventtransaction.flatten.getOrElse(default.continuationEventTransactionRequirement)
            )
            if (policy.asyncContinuation && (!policy.managedByJob || policy.interfaceMode != CommandInterfaceMode.Sync || policy.jobRunMode != CommandJobRunMode.Sync))
              None
            else
              Some(policy)
          }
        }
      }
    }

  private def _parse_boolean(value: String): Option[Boolean] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "1" | "yes" | "on" => Some(true)
      case "false" | "0" | "no" | "off" => Some(false)
      case _ => None
    }

  private def _parse_continuation(value: String): Option[Boolean] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "async" | "async-same-job" | "same-job-task" | "event-async-same-job-task" | "async-new-job" | "event-async-new-job" | "true" | "1" | "yes" | "on" => Some(true)
      case "none" | "false" | "0" | "no" | "off" => Some(false)
      case _ => None
    }
}

abstract class QueryAction(
) extends Action {
}

// abstract class Query(
// ) extends QueryAction {
// }
