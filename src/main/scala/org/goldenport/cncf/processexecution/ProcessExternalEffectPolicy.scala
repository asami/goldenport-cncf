package org.goldenport.cncf.processexecution

import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.effect.{ExternalEffectClassification, ExternalEffectKind, ExternalEffectRoute, ExternalEffectUsage}
import org.goldenport.cncf.workspace.WorkspacePermission

/*
 * @since   Oct. 16, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Bounded command-name classification shared by runtime admission and tooling.
  * Names describe recognized effects; they never grant execution authority.
  */
object ProcessExternalEffectPolicy {
  private val _direct_names = Set(
    "sh", "bash", "zsh", "dash", "ksh", "csh", "tcsh", "fish", "cmd", "powershell", "pwsh",
    "java", "javaw", "python", "python2", "python3", "node", "nodejs", "ruby", "perl", "php",
    "env", "busybox", "xargs", "sudo", "doas", "command", "exec"
  )
  private val _python_version = "python[0-9]+(?:\\.[0-9]+)*".r
  private val _file_read_names = Set("cat", "head", "tail", "find", "ls")
  private val _file_write_names = Set("rm", "mkdir", "rmdir", "touch", "tee")
  private val _file_read_write_names = Set("cp", "mv")
  private val _http_names = Set("curl", "wget")
  private val _persistence_names = Set("psql", "mysql", "sqlite3", "redis-cli")

  /** Classifies supplied observations without resolving or executing their source. */
  def classifyUsageC(usage: ExternalEffectUsage): Consequence[ExternalEffectClassification] = {
    def _managed_(kind: ExternalEffectKind): Consequence[ExternalEffectClassification] =
      Consequence.success(ExternalEffectClassification(Set(kind), ExternalEffectRoute.Managed))

    usage match {
      case null => _invalid_usage_c
      case ExternalEffectUsage.ProcessCommand(commandname) =>
        if (commandname == null || commandname.isEmpty || commandname.contains('\u0000'))
          _invalid_usage_c
        else Consequence.success(classifyCommandName(commandname))
      case ExternalEffectUsage.TypedGit(null) | ExternalEffectUsage.TypedSbt(null) =>
        _invalid_usage_c
      case ExternalEffectUsage.TypedGit(kind) =>
        _managed_(if (kind.permission == WorkspacePermission.ReadOnly)
          ExternalEffectKind.GitRead else ExternalEffectKind.GitWrite)
      case ExternalEffectUsage.TypedSbt(_) => _managed_(ExternalEffectKind.Build)
      case ExternalEffectUsage.ResourceRead => _managed_(ExternalEffectKind.ResourceRead)
    }
  }

  private def _invalid_usage_c: Consequence[ExternalEffectClassification] =
    Consequence.argumentFormatError("usage", "non-null typed external effect observation", "invalid")

  /** Does not inspect files, resolve symlinks, or parse command text or argv. */
  def classifyCommandName(commandName: String): ExternalEffectClassification = {
    val basename = Option(commandName).getOrElse("").replace('\\', '/').split("/", -1).last
      .toLowerCase(Locale.ROOT).stripSuffix(".exe")
    if (_direct_names.contains(basename) || _python_version.matches(basename))
      ExternalEffectClassification(Set(ExternalEffectKind.Process), ExternalEffectRoute.Direct)
    else {
      val kinds = basename match {
        case name if _file_read_names.contains(name) => Set(ExternalEffectKind.FileRead)
        case name if _file_write_names.contains(name) => Set(ExternalEffectKind.FileWrite)
        case name if _file_read_write_names.contains(name) => Set(ExternalEffectKind.FileRead, ExternalEffectKind.FileWrite)
        case name if _http_names.contains(name) => Set(ExternalEffectKind.Http)
        case name if _persistence_names.contains(name) => Set(ExternalEffectKind.Persistence)
        case "git" => Set(ExternalEffectKind.GitRead, ExternalEffectKind.GitWrite)
        case "sbt" | "sbt.cmd" | "sbt.bat" => Set(ExternalEffectKind.Build)
        case _ => Set.empty[ExternalEffectKind]
      }
      if (kinds.nonEmpty)
        ExternalEffectClassification(kinds, ExternalEffectRoute.CapabilityLaundering)
      else
        ExternalEffectClassification(Set(ExternalEffectKind.Process), ExternalEffectRoute.Unclassified)
    }
  }

  /** Only CNCF-owned semantic tags establish the Managed route. */
  def classifyDefinition(definition: ProcessProgramDefinition): ExternalEffectClassification =
    if (definition._sbt_metadata.nonEmpty)
      ExternalEffectClassification(Set(ExternalEffectKind.Build), ExternalEffectRoute.Managed)
    else definition._git_metadata match {
      case Some(metadata) =>
        val kind = if (metadata.kind.permission == WorkspacePermission.ReadOnly)
          ExternalEffectKind.GitRead else ExternalEffectKind.GitWrite
        ExternalEffectClassification(Set(kind), ExternalEffectRoute.Managed)
      case None => classifyCommandName(definition._executable_location)
    }

  /** Eligibility still requires the existing exact program and component grant. */
  def validateGenericDefinitionC(definition: ProcessProgramDefinition): Consequence[Unit] = {
    val classification = classifyDefinition(definition)
    classification.route match {
      case ExternalEffectRoute.Unclassified => Consequence.unit
      case route =>
        Consequence.argumentPolicyViolation(
          "effect", route.diagnosticCode, "ordinary registered program",
          classification.effectKinds.toVector.map(_.toString).sorted.mkString("+") + "/" + route.toString
        )
    }
  }

  def rejectOrdinaryShellC[A]: Consequence[A] =
    Consequence.argumentPolicyViolation(
      "effect", "external.effect.ordinary-shell", "managed external effect", "Process/Direct"
    )
}
