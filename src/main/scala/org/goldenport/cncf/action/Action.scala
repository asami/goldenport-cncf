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
 * @version May.  7, 2026
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
    CommandExecutionMode.SyncDirectNoJob
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

final case class CommandExecutionPolicy(
  interfaceMode: CommandInterfaceMode = CommandInterfaceMode.Sync,
  jobRunMode: CommandJobRunMode = CommandJobRunMode.Sync,
  managedByJob: Boolean = false
) {
  def legacyModeLabel: String =
    if (!managedByJob)
      CommandExecutionMode.SyncDirectNoJob.toString
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

  def toRecord: Record =
    Record.data(
      "interfaceMode" -> interfaceMode.toString.toLowerCase(java.util.Locale.ROOT),
      "jobRunMode" -> jobRunMode.toString.toLowerCase(java.util.Locale.ROOT),
      "managedByJob" -> managedByJob,
      "legacyMode" -> legacyModeLabel
    )
}

object CommandExecutionPolicy {
  val default: CommandExecutionPolicy = CommandExecutionPolicy()

  def fromLegacyMode(mode: CommandExecutionMode): CommandExecutionPolicy =
    mode match {
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
      case "async" | "async-job" =>
        Some(fromLegacyMode(CommandExecutionMode.AsyncJob))
      case "async-job-and-await" =>
        Some(fromLegacyMode(CommandExecutionMode.AsyncJobAndAwait))
      case "sync-job" =>
        Some(fromLegacyMode(CommandExecutionMode.SyncJob))
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
        "managedbyjob"
      )
      if (rawtokens.isEmpty || pairs.exists(_.isEmpty))
        None
      else {
        val tokens = pairs.flatten.toMap
        if (tokens.keys.exists(x => !allowedkeys.contains(x)))
          None
        else {
          val rawInterfaceMode = tokens.get("interface").orElse(tokens.get("interface-mode")).orElse(tokens.get("interfacemode"))
          val rawJobRunMode = tokens.get("job-run").orElse(tokens.get("job-run-mode")).orElse(tokens.get("jobrun")).orElse(tokens.get("jobrunmode"))
          val rawManagedByJob = tokens.get("managed").orElse(tokens.get("managed-by-job")).orElse(tokens.get("managedbyjob"))
          val interfaceMode = rawInterfaceMode.map(CommandInterfaceMode.parse)
          val jobRunMode = rawJobRunMode.map(CommandJobRunMode.parse)
          val managedByJob = rawManagedByJob.map(_parse_boolean)
          if (interfaceMode.exists(_.isEmpty) || jobRunMode.exists(_.isEmpty) || managedByJob.exists(_.isEmpty))
            None
          else
            Some(CommandExecutionPolicy(
              interfaceMode.flatten.getOrElse(default.interfaceMode),
              jobRunMode.flatten.getOrElse(default.jobRunMode),
              managedByJob.flatten.getOrElse(default.managedByJob)
            ))
        }
      }
    }

  private def _parse_boolean(value: String): Option[Boolean] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "true" | "1" | "yes" | "on" => Some(true)
      case "false" | "0" | "no" | "off" => Some(false)
      case _ => None
    }
}

abstract class QueryAction(
) extends Action {
}

// abstract class Query(
// ) extends QueryAction {
// }
