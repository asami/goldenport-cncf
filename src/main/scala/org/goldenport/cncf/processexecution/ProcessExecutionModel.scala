package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.cncf.context.ScopeContext
import org.goldenport.cncf.resource.{ResourceTreeLimits, ResourceTreeReference, ResourceTreeSnapshot}

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ProcessCapabilityId private (value: String) {
  def print: String = value
}

object ProcessCapabilityId {
  private val _pattern = "[a-z][a-z0-9-]{0,63}".r

  def parseC(value: String): Consequence[ProcessCapabilityId] = {
    val text = Option(value).map(_.trim.toLowerCase).getOrElse("")
    text match {
      case _pattern() => Consequence.success(ProcessCapabilityId(text))
      case _ => Consequence.argumentFormatError("capability", "lowercase capability name", value)
    }
  }
}

final case class WorkAreaRelativePath private (value: String) {
  def print: String = value
}

object WorkAreaRelativePath {
  def parseC(value: String): Consequence[WorkAreaRelativePath] = {
    val text = Option(value).map(_.trim).getOrElse("")
    val segments = text.split("/", -1).toVector
    if (
      text.isEmpty ||
      text.startsWith("/") ||
      text.contains("\\") ||
      segments.exists(x => x.isEmpty || x == "." || x == "..")
    )
      Consequence.argumentFormatError("path", "safe non-empty WorkArea-relative path", "invalid")
    else
      Consequence.success(WorkAreaRelativePath(text))
  }
}

final case class ProcessArtifactName private (value: String) {
  def print: String = value
}

object ProcessArtifactName {
  private val _pattern = "[a-z][a-z0-9-]{0,63}".r

  def parseC(value: String): Consequence[ProcessArtifactName] = {
    val text = Option(value).map(_.trim.toLowerCase).getOrElse("")
    text match {
      case _pattern() => Consequence.success(ProcessArtifactName(text))
      case _ => Consequence.argumentFormatError("artifact", "lowercase artifact name", value)
    }
  }
}

/** Runtime-owned fixed environment bindings for one admitted program. */
final class ProcessEnvironment private[processexecution] (
  private val _values: Map[String, String]
) {
  def safeNames: Vector[String] = _values.keys.toVector.sorted

  private[processexecution] def values: Map[String, String] = _values
}

object ProcessEnvironment {
  val empty: ProcessEnvironment = new ProcessEnvironment(Map.empty)

  private[processexecution] def fixedC(
    values: Map[String, String]
  ): Consequence[ProcessEnvironment] = {
    val pairs = values.toVector.sortBy { case (name, _) => Option(name).getOrElse("") }
    pairs.foldLeft(Consequence.unit) { case (z, (name, value)) =>
      z.flatMap { _ =>
        if (name == null || !_name_pattern.matches(name))
          Consequence.argumentFormatError("environment", "uppercase environment variable name", name)
        else if (value == null || value.exists(_.isControl))
          Consequence.argumentFormatError("environment", "non-control runtime environment value", "invalid")
        else
          Consequence.unit
      }
    }.map(_ => new ProcessEnvironment(Map.from(values)))
  }

  private val _name_pattern = "[A-Z_][A-Z0-9_]*".r
}

enum ProcessArtifactKind {
  case File
}

sealed trait ProcessExecutionInput {
  def knownByteCount: Option[Long]
}

object ProcessExecutionInput {
  case object Empty extends ProcessExecutionInput {
    def knownByteCount: Option[Long] = Some(0L)
  }

  final case class Bytes(value: Vector[Byte]) extends ProcessExecutionInput {
    def knownByteCount: Option[Long] = Some(value.length.toLong)
  }

  final case class WorkAreaFile(path: WorkAreaRelativePath) extends ProcessExecutionInput {
    def knownByteCount: Option[Long] = None
  }
}

/**
 * A bounded runtime-materialized WorkArea file. Providers use this for fixed
 * capability inputs such as a schema file without receiving a host Path.
 */
final case class ProcessExecutionInputFile private (
  name: ProcessArtifactName,
  path: WorkAreaRelativePath,
  content: Vector[Byte],
  maximumBytes: Long
)

object ProcessExecutionInputFile {
  def createC(
    name: ProcessArtifactName,
    path: WorkAreaRelativePath,
    content: Vector[Byte],
    maximumbytes: Long
  ): Consequence[ProcessExecutionInputFile] =
    if (maximumbytes <= 0L)
      Consequence.argumentLimitExceeded(
        "maximumBytes",
        1L,
        maximumbytes,
        "process.execution.input-file"
      )
    else if (content.length.toLong > maximumbytes)
      Consequence.argumentLimitExceeded(
        "content",
        maximumbytes,
        content.length.toLong,
        "process.execution.input-file"
      )
    else
      Consequence.success(ProcessExecutionInputFile(name, path, content, maximumbytes))
}

/**
 * A logical, already-admitted resource tree to materialize under the
 * runtime-owned Process WorkArea. The component cannot supply a host path or
 * forge the opaque tree snapshot.
 */
final case class ProcessExecutionResourceTreeInput private (
  tree: ResourceTreeSnapshot,
  target: WorkAreaRelativePath,
  requestedLimits: ResourceTreeLimits
)

object ProcessExecutionResourceTreeInput {
  def createC(
    tree: ResourceTreeSnapshot,
    target: WorkAreaRelativePath
  ): Consequence[ProcessExecutionResourceTreeInput] =
    createC(tree, target, tree.limits)

  def createC(
    tree: ResourceTreeSnapshot,
    target: WorkAreaRelativePath,
    requestedlimits: ResourceTreeLimits
  ): Consequence[ProcessExecutionResourceTreeInput] =
    tree.tightenC(requestedlimits).map(_ => ProcessExecutionResourceTreeInput(tree, target, requestedlimits))

  private[processexecution] def fromAdmitted(
    tree: ResourceTreeSnapshot,
    target: WorkAreaRelativePath,
    requestedlimits: ResourceTreeLimits
  ): ProcessExecutionResourceTreeInput =
    ProcessExecutionResourceTreeInput(tree, target, requestedlimits)
}

final case class ProcessExecutionOutputDeclaration private (
  name: ProcessArtifactName,
  path: WorkAreaRelativePath,
  kind: ProcessArtifactKind,
  maximumBytes: Long
)

object ProcessExecutionOutputDeclaration {
  def createC(
    name: ProcessArtifactName,
    path: WorkAreaRelativePath,
    kind: ProcessArtifactKind,
    maximumbytes: Long
  ): Consequence[ProcessExecutionOutputDeclaration] =
    if (maximumbytes <= 0L)
      Consequence.argumentLimitExceeded(
        "maximumBytes",
        1L,
        maximumbytes,
        "process.execution.artifact"
      )
    else
      Consequence.success(ProcessExecutionOutputDeclaration(name, path, kind, maximumbytes))
}

final case class ProcessExecutionLimits(
  launchTimeoutMillis: Option[Long] = None,
  executionTimeoutMillis: Option[Long] = None,
  terminationGraceMillis: Option[Long] = None,
  stdinBytes: Option[Long] = None,
  stdoutBytes: Option[Long] = None,
  stderrBytes: Option[Long] = None,
  argumentCount: Option[Long] = None,
  argumentBytes: Option[Long] = None,
  artifactCount: Option[Long] = None,
  artifactBytes: Option[Long] = None,
  workAreaBytes: Option[Long] = None
) {
  def validateOptionalC: Consequence[Unit] =
    _validate_c(_values)

  def requireFiniteC: Consequence[Unit] =
    for {
      _ <- validateOptionalC
      _ <- _required_c(_values)
    } yield ()

  def tightenC(overridevalue: ProcessExecutionLimits): Consequence[ProcessExecutionLimits] =
    for {
      _ <- validateOptionalC
      _ <- overridevalue.validateOptionalC
      launch <- _tighten_c("launchTimeoutMillis", launchTimeoutMillis, overridevalue.launchTimeoutMillis)
      execution <- _tighten_c("executionTimeoutMillis", executionTimeoutMillis, overridevalue.executionTimeoutMillis)
      grace <- _tighten_c("terminationGraceMillis", terminationGraceMillis, overridevalue.terminationGraceMillis)
      stdin <- _tighten_c("stdinBytes", stdinBytes, overridevalue.stdinBytes)
      stdout <- _tighten_c("stdoutBytes", stdoutBytes, overridevalue.stdoutBytes)
      stderr <- _tighten_c("stderrBytes", stderrBytes, overridevalue.stderrBytes)
      argumentcount <- _tighten_c("argumentCount", argumentCount, overridevalue.argumentCount)
      argumentbytes <- _tighten_c("argumentBytes", argumentBytes, overridevalue.argumentBytes)
      artifactcount <- _tighten_c("artifactCount", artifactCount, overridevalue.artifactCount)
      artifactbytes <- _tighten_c("artifactBytes", artifactBytes, overridevalue.artifactBytes)
      workarea <- _tighten_c("workAreaBytes", workAreaBytes, overridevalue.workAreaBytes)
    } yield ProcessExecutionLimits(
      launch,
      execution,
      grace,
      stdin,
      stdout,
      stderr,
      argumentcount,
      argumentbytes,
      artifactcount,
      artifactbytes,
      workarea
    )

  private def _values: Vector[(String, Option[Long])] = Vector(
    "launchTimeoutMillis" -> launchTimeoutMillis,
    "executionTimeoutMillis" -> executionTimeoutMillis,
    "terminationGraceMillis" -> terminationGraceMillis,
    "stdinBytes" -> stdinBytes,
    "stdoutBytes" -> stdoutBytes,
    "stderrBytes" -> stderrBytes,
    "argumentCount" -> argumentCount,
    "argumentBytes" -> argumentBytes,
    "artifactCount" -> artifactCount,
    "artifactBytes" -> artifactBytes,
    "workAreaBytes" -> workAreaBytes
  )

  private def _validate_c(values: Vector[(String, Option[Long])]): Consequence[Unit] =
    values.foldLeft(Consequence.unit) { case (z, (name, value)) =>
      z.flatMap { _ =>
        value match {
          case Some(x) if x > 0L => Consequence.unit
          case Some(x) => Consequence.argumentLimitExceeded(name, 1L, x, "process.execution.limit")
          case None => Consequence.unit
        }
      }
    }

  private def _required_c(values: Vector[(String, Option[Long])]): Consequence[Unit] =
    values.foldLeft(Consequence.unit) { case (z, (name, value)) =>
      z.flatMap { _ =>
        if (value.nonEmpty)
          Consequence.unit
        else
          Consequence.argumentPolicyViolation(
            name,
            "process.execution.finite-limit",
            "a finite positive limit",
            "absent"
          )
      }
    }

  private def _tighten_c(
    name: String,
    current: Option[Long],
    overridevalue: Option[Long]
  ): Consequence[Option[Long]] =
    (current, overridevalue) match {
      case (Some(limit), Some(value)) if value > limit =>
        Consequence.argumentLimitExceeded(name, limit, value, "process.execution.limit")
      case (Some(limit), Some(value)) => Consequence.success(Some(math.min(limit, value)))
      case (Some(limit), None) => Consequence.success(Some(limit))
      case (None, Some(value)) => Consequence.success(Some(value))
      case (None, None) => Consequence.success(None)
    }
}

object ProcessExecutionLimits {
  val empty: ProcessExecutionLimits = ProcessExecutionLimits()

  def effectiveC(
    maximum: ProcessExecutionLimits,
    grant: ProcessExecutionLimits,
    requested: ProcessExecutionLimits
  ): Consequence[ProcessExecutionLimits] =
    for {
      _ <- maximum.requireFiniteC
      granted <- maximum.tightenC(grant)
      result <- granted.tightenC(requested)
    } yield result
}

final case class ProcessArgumentPolicy(
  fixedPrefix: Vector[String],
  permittedArguments: Set[String] = Set.empty,
  permittedArgumentVectors: Set[Vector[String]] = Set.empty
) {
  def validateC(arguments: Vector[String], limits: ProcessExecutionLimits): Consequence[Unit] =
    for {
      _ <- _validate_arguments_c(arguments)
      _ <- _validate_count_c(arguments, limits.argumentCount)
      _ <- _validate_bytes_c(arguments, limits.argumentBytes)
    } yield ()

  private def _validate_arguments_c(arguments: Vector[String]): Consequence[Unit] = {
    if (permittedArgumentVectors.nonEmpty)
      if (permittedArgumentVectors.contains(arguments))
        Consequence.unit
      else
        Consequence.argumentPolicyViolation(
          "arguments",
          "process.execution.argument-policy",
          "registered argument vector",
          "unapproved"
        )
    else {
      val rejected = arguments.find(x => !permittedArguments.contains(x))
      rejected match {
        case Some(_) =>
          Consequence.argumentPolicyViolation(
            "arguments",
            "process.execution.argument-policy",
            "registered argument value",
            "unapproved"
          )
        case None => Consequence.unit
      }
    }
  }

  private def _validate_count_c(
    arguments: Vector[String],
    maximum: Option[Long]
  ): Consequence[Unit] =
    maximum match {
      case Some(value) if arguments.length.toLong > value =>
        Consequence.argumentLimitExceeded(
          "arguments",
          value,
          arguments.length.toLong,
          "process.execution.argument-count"
        )
      case _ => Consequence.unit
    }

  private def _validate_bytes_c(
    arguments: Vector[String],
    maximum: Option[Long]
  ): Consequence[Unit] = {
    val size = arguments.foldLeft(0L)((z, x) => z + x.getBytes("UTF-8").length.toLong)
    maximum match {
      case Some(value) if size > value =>
        Consequence.argumentLimitExceeded(
          "arguments",
          value,
          size,
          "process.execution.argument-bytes"
        )
      case _ => Consequence.unit
    }
  }
}

final class ProcessProgramDefinition private (
  val capability: ProcessCapabilityId,
  val safeProgramIdentity: String,
  private[processexecution] val _executable_location: String,
  val fixedArguments: Vector[String],
  val argumentPolicy: ProcessArgumentPolicy,
  val maximumLimits: ProcessExecutionLimits,
  val allowedArtifacts: Set[ProcessArtifactName],
  val allowsWorkingDirectory: Boolean,
  val allowedInputFiles: Set[ProcessArtifactName],
  val allowedInputFilePaths: Map[ProcessArtifactName, WorkAreaRelativePath],
  val allowedResourceTrees: Map[ResourceTreeReference, ResourceTreeLimits],
  private[processexecution] val _environment: ProcessEnvironment
) {
  def validateRequestC(
    request: ProcessExecutionRequest,
    limits: ProcessExecutionLimits
  ): Consequence[Unit] =
    for {
      _ <- limits.requireFiniteC
      _ <- argumentPolicy.validateC(request.arguments, limits)
      _ <- _validate_working_directory_c(request)
      _ <- _validate_artifacts_c(request)
      _ <- _validate_input_files_c(request, limits)
      _ <- _validate_managed_input_bytes_c(request, limits)
      _ <- _validate_input_c(request.input, limits)
      _ <- _validate_output_limits_c(request.outputs, limits)
    } yield ()

  private def _validate_working_directory_c(request: ProcessExecutionRequest): Consequence[Unit] =
    if (request.workingDirectory.nonEmpty && !allowsWorkingDirectory)
      Consequence.argumentPolicyViolation(
        "workingDirectory",
        "process.execution.working-directory",
        "absent for this capability",
        "requested"
      )
    else
      Consequence.unit

  private def _validate_artifacts_c(request: ProcessExecutionRequest): Consequence[Unit] = {
    val rejected = request.outputs.find(x => !allowedArtifacts.contains(x.name))
    rejected match {
      case Some(_) =>
        Consequence.argumentPolicyViolation(
          "outputs",
          "process.execution.artifact-policy",
          "registered artifact declaration",
          "unapproved"
        )
      case None => Consequence.unit
    }
  }

  def resolveResourceTreesC(
    request: ProcessExecutionRequest,
    grant: ProcessExecutionGrant
  ): Consequence[Vector[ProcessExecutionResourceTreeInput]] =
    request.resourceTrees.foldLeft(Consequence.success(Vector.empty[ProcessExecutionResourceTreeInput])) { (z, input) =>
      z.flatMap { inputs =>
        allowedResourceTrees.get(input.tree.reference) match {
          case Some(maximum) =>
            for {
              requested <- input.tree.tightenC(input.requestedLimits)
              programbounded <- requested.narrowC(maximum)
              grantlimits <- _grant_tree_limits_c(input.tree.reference, grant)
              grantbounded <- grantlimits.fold(Consequence.success(programbounded))(programbounded.narrowC)
            } yield inputs :+ ProcessExecutionResourceTreeInput.fromAdmitted(
              grantbounded,
              input.target,
              grantbounded.limits
            )
          case None =>
            Consequence.argumentPolicyViolation(
              "resourceTrees",
              "process.execution.resource-tree-policy",
              "runtime-declared resource tree",
              input.tree.reference.name
            )
        }
      }
    }

  private def _grant_tree_limits_c(
    reference: ResourceTreeReference,
    grant: ProcessExecutionGrant
  ): Consequence[Option[ResourceTreeLimits]] =
    if (grant.resourceTreeLimits.isEmpty)
      Consequence.success(None)
    else
      grant.resourceTreeLimits.get(reference).map(x => Consequence.success(Some(x))).getOrElse(
        Consequence.argumentPolicyViolation(
          "resourceTrees",
          "process.execution.resource-tree-grant",
          "grant-authorized resource tree",
          reference.name
        )
      )

  private def _validate_input_c(
    input: ProcessExecutionInput,
    limits: ProcessExecutionLimits
  ): Consequence[Unit] =
    input.knownByteCount match {
      case Some(value) if value > limits.stdinBytes.get =>
        Consequence.argumentLimitExceeded(
          "input",
          limits.stdinBytes.get,
          value,
          "process.execution.stdin"
        )
      case _ => Consequence.unit
    }

  private def _validate_input_files_c(
    request: ProcessExecutionRequest,
    limits: ProcessExecutionLimits
  ): Consequence[Unit] = {
    val rejected = request.inputFiles.find(x => !allowedInputFiles.contains(x.name))
    val misplaced = request.inputFiles.find { input =>
      allowedInputFilePaths.get(input.name).exists(_ != input.path)
    }
    rejected match {
      case Some(_) =>
        Consequence.argumentPolicyViolation(
          "inputFiles",
          "process.execution.input-file-policy",
          "registered input-file declaration",
          "unapproved"
        )
      case None => misplaced.fold(Consequence.unit) { _ =>
        Consequence.argumentPolicyViolation(
          "inputFiles",
          "process.execution.input-file-path-policy",
          "runtime-declared WorkArea path",
          "unapproved"
        )
      }
    }
  }

  private def _validate_managed_input_bytes_c(
    request: ProcessExecutionRequest,
    limits: ProcessExecutionLimits
  ): Consequence[Unit] = {
    val fixed = request.inputFiles.foldLeft(BigInt(0))((z, x) => z + BigInt(x.content.length))
    val trees = request.resourceTrees.foldLeft(BigInt(0))((z, x) => z + BigInt(x.tree.totalByteSize))
    val total = fixed + trees
    if (total > BigInt(limits.workAreaBytes.get))
      Consequence.argumentLimitExceeded(
        "resourceTrees",
        limits.workAreaBytes.get,
        total.longValue,
        "process.execution.workarea-bytes"
      )
    else
      Consequence.unit
  }

  private def _validate_output_limits_c(
    outputs: Vector[ProcessExecutionOutputDeclaration],
    limits: ProcessExecutionLimits
  ): Consequence[Unit] = {
    val declared = outputs.foldLeft(BigInt(0))((z, x) => z + BigInt(x.maximumBytes))
    val oversized = outputs.find(_.maximumBytes > limits.artifactBytes.get)
    if (outputs.length.toLong > limits.artifactCount.get)
      Consequence.argumentLimitExceeded(
        "outputs",
        limits.artifactCount.get,
        outputs.length.toLong,
        "process.execution.artifact-count"
      )
    else if (oversized.nonEmpty)
      Consequence.argumentLimitExceeded(
        "outputs.maximumBytes",
        limits.artifactBytes.get,
        oversized.get.maximumBytes,
        "process.execution.artifact-bytes"
      )
    else if (declared > BigInt(limits.workAreaBytes.get))
      Consequence.argumentLimitExceeded(
        "outputs.maximumBytes",
        limits.workAreaBytes.get,
        declared.longValue,
        "process.execution.workarea-bytes"
      )
    else
      Consequence.unit
  }
}

object ProcessProgramDefinition {
  private val _safe_identity_pattern = "[a-z][a-z0-9-]{0,63}".r

  /**
   * Creates a definition during trusted runtime assembly. Component and
   * provider behavior must submit only ProcessExecutionRequest values to an
   * already-installed admission service.
   */
  def fromRuntimeC(
    capability: ProcessCapabilityId,
    safeprogramidentity: String,
    executablelocation: String,
    fixedarguments: Vector[String],
    argumentpolicy: ProcessArgumentPolicy,
    maximumlimits: ProcessExecutionLimits,
    allowedartifacts: Set[ProcessArtifactName],
    allowsworkingdirectory: Boolean = false,
    allowedinputfiles: Set[ProcessArtifactName] = Set.empty,
    allowedinputfilepaths: Map[ProcessArtifactName, WorkAreaRelativePath] = Map.empty,
    allowedresourcetrees: Map[ResourceTreeReference, ResourceTreeLimits] = Map.empty,
    environment: Map[String, String] = Map.empty
  ): Consequence[ProcessProgramDefinition] = {
    val identity = Option(safeprogramidentity).map(_.trim.toLowerCase).getOrElse("")
    val executable = Option(executablelocation).map(_.trim).getOrElse("")
    for {
      _ <- _validate_identity_c(identity)
      _ <- _validate_executable_c(executable)
      _ <- maximumlimits.requireFiniteC
      _ <- _validate_input_file_paths_c(allowedinputfiles, allowedinputfilepaths)
      _ <- _validate_resource_tree_limits_c(allowedresourcetrees)
      processenvironment <- ProcessEnvironment.fixedC(environment)
    } yield new ProcessProgramDefinition(
      capability,
      identity,
      executable,
      fixedarguments,
      argumentpolicy,
      maximumlimits,
      allowedartifacts,
      allowsworkingdirectory,
      allowedinputfiles,
      allowedinputfilepaths,
      allowedresourcetrees,
      processenvironment
    )
  }

  private def _validate_identity_c(value: String): Consequence[Unit] =
    value match {
      case _safe_identity_pattern() => Consequence.unit
      case _ => Consequence.argumentFormatError("safeProgramIdentity", "safe logical program identity", "invalid")
    }

  private def _validate_executable_c(value: String): Consequence[Unit] =
    if (value.nonEmpty && !value.exists(_.isControl))
      Consequence.unit
    else
      Consequence.argumentFormatError("runtimeExecutable", "runtime-owned executable location", "invalid")

  private def _validate_resource_tree_limits_c(
    trees: Map[ResourceTreeReference, ResourceTreeLimits]
  ): Consequence[Unit] =
    trees.values.foldLeft(Consequence.unit) { (z, limits) =>
      z.flatMap(_ => limits.validateC.map(_ => ()))
    }

  private def _validate_input_file_paths_c(
    files: Set[ProcessArtifactName],
    paths: Map[ProcessArtifactName, WorkAreaRelativePath]
  ): Consequence[Unit] =
    if (paths.keys.forall(files.contains))
      Consequence.unit
    else
      Consequence.argumentPolicyViolation(
        "allowedInputFilePaths",
        "process.execution.input-file-policy",
        "declared input-file name",
        "unapproved"
      )
}

final case class ProcessExecutionGrant(
  capability: ProcessCapabilityId,
  maximumLimits: ProcessExecutionLimits = ProcessExecutionLimits.empty,
  resourceTreeLimits: Map[ResourceTreeReference, ResourceTreeLimits] = Map.empty
)

/**
 * Runtime-installed admission for one component/provider execution scope.
 *
 * Component code submits a logical request. The runtime owns program
 * definitions and grants, and returns the only intent that may enter the
 * UnitOfWork Process Execution operation.
 */
final class ProcessExecutionAdmission private (
  policy: ProcessExecutionPolicy,
  grants: Map[ProcessCapabilityId, ProcessExecutionGrant]
) {
  def admitC(request: ProcessExecutionRequest): Consequence[ResolvedProcessExecution] =
    grants.get(request.capability).map(policy.resolveC(request, _)).getOrElse(
      Consequence.argumentPolicyViolation(
        "capability",
        "process.execution.capability-grant",
        "runtime-installed capability grant",
        request.capability.print
      )
    )
}

object ProcessExecutionAdmission {
  def createC(
    policy: ProcessExecutionPolicy,
    grants: Vector[ProcessExecutionGrant]
  ): Consequence[ProcessExecutionAdmission] = {
    val capabilities = grants.map(_.capability)
    if (capabilities.distinct.size != capabilities.size)
      Consequence.argumentPolicyViolation(
        "grants",
        "process.execution.capability-grant",
        "unique capability grants",
        "duplicate"
      )
    else
      grants.foldLeft(Consequence.unit) { (z, grant) =>
        z.flatMap { _ =>
          grant.maximumLimits.validateOptionalC.flatMap { _ =>
            grant.resourceTreeLimits.values.foldLeft(Consequence.unit) { (zz, limits) =>
              zz.flatMap(_ => limits.validateC.map(_ => ()))
            }
          }
        }
      }.map(_ => new ProcessExecutionAdmission(policy, grants.map(x => x.capability -> x).toMap))
  }

  def resolveC(
    scope: ScopeContext,
    request: ProcessExecutionRequest
  ): Consequence[ResolvedProcessExecution] =
    scope.processExecutionAdmissionOption
      .map(_.admitC(request))
      .getOrElse(Consequence.serviceUnavailable("Process Execution admission is not configured"))
}

final case class ProcessExecutionRequest(
  capability: ProcessCapabilityId,
  arguments: Vector[String] = Vector.empty,
  input: ProcessExecutionInput = ProcessExecutionInput.Empty,
  workingDirectory: Option[WorkAreaRelativePath] = None,
  outputs: Vector[ProcessExecutionOutputDeclaration] = Vector.empty,
  requestedLimits: ProcessExecutionLimits = ProcessExecutionLimits.empty,
  inputFiles: Vector[ProcessExecutionInputFile] = Vector.empty,
  resourceTrees: Vector[ProcessExecutionResourceTreeInput] = Vector.empty
) {
  def validateC: Consequence[Unit] =
    for {
      _ <- _validate_arguments_c(arguments)
      _ <- _validate_input_files_c(inputFiles)
      _ <- _validate_resource_trees_c(resourceTrees)
      _ <- _validate_outputs_c(outputs)
      _ <- _validate_input_output_paths_c(inputFiles, resourceTrees, workingDirectory, outputs)
      _ <- requestedLimits.validateOptionalC
    } yield ()

  private def _validate_arguments_c(arguments: Vector[String]): Consequence[Unit] =
    if (arguments.forall(x => x != null && !x.exists(_.isControl)))
      Consequence.unit
    else
      Consequence.argumentFormatError("arguments", "non-control argument vector", "invalid")

  private def _validate_outputs_c(outputs: Vector[ProcessExecutionOutputDeclaration]): Consequence[Unit] = {
    val names = outputs.map(_.name)
    val paths = outputs.map(_.path)
    if (names.distinct.size != names.size)
      Consequence.argumentPolicyViolation("outputs", "process.execution.artifact-policy", "unique artifact names", "duplicate")
    else if (paths.distinct.size != paths.size)
      Consequence.argumentPolicyViolation("outputs", "process.execution.artifact-policy", "unique artifact paths", "duplicate")
    else
      Consequence.unit
  }

  private def _validate_input_files_c(files: Vector[ProcessExecutionInputFile]): Consequence[Unit] = {
    val names = files.map(_.name)
    val paths = files.map(_.path)
    if (names.distinct.size != names.size)
      Consequence.argumentPolicyViolation("inputFiles", "process.execution.input-file-policy", "unique input-file names", "duplicate")
    else if (paths.distinct.size != paths.size)
      Consequence.argumentPolicyViolation("inputFiles", "process.execution.input-file-policy", "unique input-file paths", "duplicate")
    else
      Consequence.unit
  }

  private def _validate_resource_trees_c(
    trees: Vector[ProcessExecutionResourceTreeInput]
  ): Consequence[Unit] = {
    val references = trees.map(_.tree.reference)
    val targets = trees.map(_.target)
    if (references.distinct.size != references.size)
      Consequence.argumentPolicyViolation("resourceTrees", "process.execution.resource-tree-policy", "unique tree references", "duplicate")
    else if (_has_overlapping_paths(targets.map(_.value)))
      Consequence.argumentPolicyViolation("resourceTrees", "process.execution.resource-tree-policy", "non-overlapping WorkArea targets", "collision")
    else
      Consequence.unit
  }

  private def _validate_input_output_paths_c(
    files: Vector[ProcessExecutionInputFile],
    trees: Vector[ProcessExecutionResourceTreeInput],
    workingdirectory: Option[WorkAreaRelativePath],
    outputs: Vector[ProcessExecutionOutputDeclaration]
  ): Consequence[Unit] = {
    val outputpaths = outputs.map { output =>
      workingdirectory.fold(output.path.value)(directory => s"${directory.value}/${output.path.value}")
    }.toSet
    val inputpaths = files.map(_.path.value)
    val treepaths = trees.map(_.target.value)
    if (files.exists(file => outputpaths.contains(file.path.value)))
      Consequence.argumentPolicyViolation(
        "inputFiles",
        "process.execution.input-file-policy",
        "input paths distinct from declared output paths",
        "collision"
      )
    else if (treepaths.exists(target => inputpaths.exists(_path_overlap(_, target))))
      Consequence.argumentPolicyViolation(
        "resourceTrees",
        "process.execution.resource-tree-policy",
        "tree targets distinct from fixed input paths",
        "collision"
      )
    else if (treepaths.exists(target => outputpaths.exists(_path_overlap(_, target))))
      Consequence.argumentPolicyViolation(
        "resourceTrees",
        "process.execution.resource-tree-policy",
        "tree targets distinct from declared output paths",
        "collision"
      )
    else
      Consequence.unit
  }

  private def _path_overlap(left: String, right: String): Boolean =
    left == right || left.startsWith(s"${right}/") || right.startsWith(s"${left}/")

  private def _has_overlapping_paths(paths: Vector[String]): Boolean =
    paths.indices.exists { index =>
      paths.drop(index + 1).exists(_path_overlap(paths(index), _))
    }
}

final case class ResolvedProcessExecution(
  request: ProcessExecutionRequest,
  definition: ProcessProgramDefinition,
  effectiveLimits: ProcessExecutionLimits
) {
  def effectiveArguments: Vector[String] =
    definition.fixedArguments ++ request.arguments
}

final class ProcessExecutionPolicy private (
  private val _definitions: Map[ProcessCapabilityId, ProcessProgramDefinition]
) {
  def resolveC(
    request: ProcessExecutionRequest,
    grant: ProcessExecutionGrant
  ): Consequence[ResolvedProcessExecution] =
    for {
      _ <- request.validateC
      definition <- _definition_c(request.capability)
      _ <- _validate_grant_c(request.capability, grant)
      effective <- ProcessExecutionLimits.effectiveC(
        definition.maximumLimits,
        grant.maximumLimits,
        request.requestedLimits
      )
      trees <- definition.resolveResourceTreesC(request, grant)
      admittedrequest = request.copy(resourceTrees = trees)
      _ <- definition.validateRequestC(admittedrequest, effective)
    } yield ResolvedProcessExecution(admittedrequest, definition, effective)

  private def _definition_c(capability: ProcessCapabilityId): Consequence[ProcessProgramDefinition] =
    _definitions.get(capability).map(Consequence.success).getOrElse(
      Consequence.argumentPolicyViolation(
        "capability",
        "process.execution.program-definition",
        "registered process capability",
        capability.print
      )
    )

  private def _validate_grant_c(
    capability: ProcessCapabilityId,
    grant: ProcessExecutionGrant
  ): Consequence[Unit] =
    if (grant.capability == capability)
      grant.maximumLimits.validateOptionalC.flatMap { _ =>
        grant.resourceTreeLimits.values.foldLeft(Consequence.unit) { (z, limits) =>
          z.flatMap(_ => limits.validateC.map(_ => ()))
        }
      }
    else
      Consequence.argumentPolicyViolation(
        "capability",
        "process.execution.capability-grant",
        capability.print,
        grant.capability.print
      )
}

object ProcessExecutionPolicy {
  def createC(
    definitions: Vector[ProcessProgramDefinition]
  ): Consequence[ProcessExecutionPolicy] = {
    val capabilities = definitions.map(_.capability)
    if (capabilities.distinct.size != capabilities.size)
      Consequence.argumentPolicyViolation(
        "definitions",
        "process.execution.program-definition",
        "unique capability definitions",
        "duplicate"
      )
    else
      Consequence.success(new ProcessExecutionPolicy(definitions.map(x => x.capability -> x).toMap))
  }
}

enum ProcessExecutionStream {
  case Stdout
  case Stderr
}

enum ProcessExecutionTermination {
  case Exited(exitCode: Int)
  case LaunchFailed
  case TimedOut
  case Cancelled
  case OutputLimitExceeded(stream: ProcessExecutionStream)
  case ArtifactLimitExceeded
}

final case class ProcessExecutionCapture(
  content: Vector[Byte],
  byteCount: Long,
  truncated: Boolean
)

final case class ProcessExecutionArtifact(
  name: ProcessArtifactName,
  kind: ProcessArtifactKind,
  byteCount: Long
)

final case class ProcessExecutionResult(
  termination: ProcessExecutionTermination,
  stdout: ProcessExecutionCapture,
  stderr: ProcessExecutionCapture,
  artifacts: Vector[ProcessExecutionArtifact],
  elapsedMillis: Long,
  safeProgramIdentity: String
)
