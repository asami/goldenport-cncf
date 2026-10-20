package org.goldenport.cncf.processexecution

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import java.util.{Base64, UUID}
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal
import io.circe.{Json, JsonObject}
import io.circe.jawn.JawnParser
import org.goldenport.Consequence

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 * @author  ASAMI, Tomoharu
 */
/** Trusted host-file bridge; the external host owns registered-runner invocation and evidence. */
final class RegisteredSbtHostDriver(
  localDriver: LocalProcessExecutionDriver,
  configuration: RegisteredSbtHostConfiguration
) extends ProcessExecutionDriver with AutoCloseable {
  val safeIdentity: String = "registered-sbt-host"
  private val _closed = new AtomicBoolean(false)
  private val _lifecycle_lock = new AnyRef
  private val _handles = ConcurrentHashMap.newKeySet[RegisteredSbtHostHandle]()
  private var _sequence = BigInt(0)

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] =
    if (execution.definition._sbt_metadata.isEmpty) localDriver.startC(execution)
    else _start_sbt_c(execution)

  override def startC(
    execution: ResolvedProcessExecution,
    workArea: ProcessExecutionWorkArea
  ): Consequence[ProcessExecutionHandle] =
    if (execution.definition._sbt_metadata.isEmpty) localDriver.startC(execution, workArea)
    else _start_sbt_c(execution)

  private def _start_sbt_c(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] =
    _lifecycle_lock.synchronized {
      if (_closed.get)
        Consequence.serviceUnavailable("Registered SBT host driver is closed; external execution state is unknown")
      else for {
        _ <- SbtExecutionAdmission.validateRequestC(execution.request)
        _ <- execution.definition.argumentPolicy.validateC(execution.request.arguments, execution.effectiveLimits)
        selected <- _next_configuration_c
        metadata = execution.definition._sbt_metadata.get
        directory <- metadata.binding.workingDirectoryC
        requestid = UUID.randomUUID().toString
        startedat = System.nanoTime()
        _ <- RegisteredSbtHostDriver.publishRequestC(execution, metadata, directory, selected, requestid)
      } yield {
        val handle = new RegisteredSbtHostHandle(execution, selected, requestid, startedat, value => {
          _handles.remove(value)
          ()
        })
        _handles.add(handle)
        handle
      }
    }

  private def _next_configuration_c: Consequence[RegisteredSbtHostConfiguration] = {
    _sequence += 1
    val selected = if (_sequence == 1) configuration else RegisteredSbtHostConfiguration(
      configuration.requestFile.resolveSibling(configuration.requestFile.getFileName.toString + "." + _sequence.toString),
      configuration.responseFile.resolveSibling(configuration.responseFile.getFileName.toString + "." + _sequence.toString))
    RegisteredSbtHostConfiguration.createC(selected.requestFile, selected.responseFile)
  }

  def close(): Unit = {
    val handles = _lifecycle_lock.synchronized {
      _closed.set(true)
      _handles.asScala.toVector
    }
    handles.foreach(_.cancelC)
    localDriver.close()
  }
}

object RegisteredSbtHostDriver {
  private val _parser = JawnParser(allowDuplicateKeys = false)
  private val _request_bytes = 1024 * 1024
  private val _receipt_metadata_bytes = 65536L

  private[processexecution] def publishRequestC(
    execution: ResolvedProcessExecution,
    metadata: SbtProgramMetadata,
    directory: Path,
    configuration: RegisteredSbtHostConfiguration,
    requestId: String
  ): Consequence[Unit] = {
    val request = Json.obj(
      "schema" -> Json.fromString("cncf.registered-sbt-request.v1"),
      "requestId" -> Json.fromString(requestId),
      "workspaceId" -> Json.fromString(metadata.binding.workspaceId.print),
      "operation" -> Json.fromString(metadata.operation match {
        case SbtOperation.Compile => "compile"
        case SbtOperation.Test => "test"
        case SbtOperation.TestOnly => "test-only"
      }),
      "programIdentity" -> Json.fromString(execution.definition.safeProgramIdentity),
      "workingDirectory" -> Json.fromString(directory.toString),
      "executable" -> Json.fromString(execution.definition._executable_location),
      "arguments" -> Json.fromValues(execution.effectiveArguments.map(Json.fromString)),
      "environment" -> Json.obj(execution.definition._environment.values.toVector.map { case (name, value) =>
        name -> Json.fromString(value)
      }*),
      "executionTimeoutMillis" -> Json.fromLong(execution.effectiveLimits.executionTimeoutMillis.get),
      "stdoutBytes" -> Json.fromLong(execution.effectiveLimits.stdoutBytes.get),
      "stderrBytes" -> Json.fromLong(execution.effectiveLimits.stderrBytes.get),
      "responseFile" -> Json.fromString(configuration.responseFile.toString))
    val bytes = request.noSpaces.getBytes(StandardCharsets.UTF_8)
    if (bytes.length > _request_bytes)
      Consequence.configurationInvalid("Registered SBT host request exceeds its bounded wire size")
    else try {
      if (Files.exists(configuration.requestFile, LinkOption.NOFOLLOW_LINKS))
        Consequence.configurationInvalid("Registered SBT host request file already exists; invocation cannot be replayed")
      else {
        Files.createDirectories(configuration.requestFile.getParent)
        val temporary = Files.createTempFile(configuration.requestFile.getParent, ".cncf-sbt-", ".json")
        try {
          Files.write(temporary, bytes)
          Files.createLink(configuration.requestFile, temporary)
          Consequence.unit
        } finally Files.deleteIfExists(temporary)
      }
    } catch {
      case NonFatal(_) => Consequence.serviceUnavailable("Registered SBT host request publication failed; external execution state is unknown")
    }
  }

  /** Validate this trusted provider input once; cache the resulting observation in the handle. */
  private[processexecution] def readResponseC(
    execution: ResolvedProcessExecution,
    configuration: RegisteredSbtHostConfiguration,
    requestId: String
  ): Consequence[ProcessExecutionResult] = {
    val result = try {
      val maximum = ((BigInt(execution.effectiveLimits.stdoutBytes.get) + 2) / 3 * 4 +
        (BigInt(execution.effectiveLimits.stderrBytes.get) + 2) / 3 * 4 + _receipt_metadata_bytes)
        .min(BigInt(Int.MaxValue - 1)).toInt
      for {
        _ <- Either.cond(!Files.isSymbolicLink(configuration.responseFile) &&
          Files.isRegularFile(configuration.responseFile, LinkOption.NOFOLLOW_LINKS), (), "Invalid registered SBT host response file")
        bytes <- Using(Files.newInputStream(configuration.responseFile))(_.readNBytes(maximum + 1))
          .toEither.left.map(_ => "Registered SBT host response could not be read")
        _ <- Either.cond(bytes.length <= maximum, (), "Registered SBT host response exceeds its capture bounds")
        text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString
        json <- _parser.parse(text).left.map(_ => "Malformed registered SBT host response")
        observed <- _decode_response(json, execution, requestId)
      } yield observed
    } catch {
      case NonFatal(_) => Left("Invalid registered SBT host response")
    }
    result.fold(message => Consequence.serviceUnavailable(message + "; external execution state is unknown"), Consequence.success)
  }

  private def _decode_response(
    json: Json,
    execution: ResolvedProcessExecution,
    requestid: String
  ): Either[String, ProcessExecutionResult] = for {
    kind <- json.hcursor.get[String]("termination").left.map(_ => "Missing registered SBT termination")
    names = Set("schema", "requestId", "termination", "stdoutBase64", "stderrBase64", "elapsedMillis",
      "programIdentity", "processTerminated", "runner", "commandRequest", "commandResult") ++ (kind match {
      case "exited" => Set("exitCode")
      case "output-limit-exceeded" => Set("exceededStream")
      case _ => Set.empty[String]
    })
    obj <- _fields(json, names)
    schema <- _string(obj, "schema")
    _ <- Either.cond(schema == "cncf.registered-sbt-response.v1", (), "Unsupported registered SBT response schema")
    correlation <- _string(obj, "requestId")
    identity <- _string(obj, "programIdentity")
    _ <- Either.cond(correlation == requestid && identity == execution.definition.safeProgramIdentity,
      (), "Crossed registered SBT host response")
    _ <- Either.cond(obj("processTerminated").flatMap(_.asBoolean).contains(true), (), "Registered SBT process termination is unproven")
    runner <- _fields(obj("runner").get, Set("logicalRole", "agentId", "model", "reasoningEffort"))
    role <- _string(runner, "logicalRole")
    agent <- _string(runner, "agentId")
    model <- _string(runner, "model")
    effort <- _string(runner, "reasoningEffort")
    _ <- Either.cond(role == "cncf_command_runner" && agent.trim.nonEmpty && model == "gpt-6-luna" && effort == "medium",
      (), "Registered SBT runner evidence is invalid")
    commandrequest <- _string(obj, "commandRequest")
    commandresult <- _string(obj, "commandResult")
    _ <- Either.cond(commandrequest.trim.nonEmpty && commandresult.trim.nonEmpty, (), "Registered SBT command evidence is missing")
    termination <- kind match {
      case "exited" => obj("exitCode").flatMap(_.asNumber).flatMap(_.toInt)
        .toRight("Registered SBT exit code requires an integer").map(ProcessExecutionTermination.Exited.apply)
      case "timed-out" => Right(ProcessExecutionTermination.TimedOut)
      case "cancelled" => Right(ProcessExecutionTermination.Cancelled)
      case "output-limit-exceeded" => _string(obj, "exceededStream").flatMap {
        case "stdout" => Right(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
        case "stderr" => Right(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr))
        case _ => Left("Invalid registered SBT exceeded stream")
      }
      case _ => Left("Unsupported registered SBT termination")
    }
    stdouttext <- _string(obj, "stdoutBase64")
    stderrtext <- _string(obj, "stderrBase64")
    stdout <- _capture(stdouttext, execution.effectiveLimits.stdoutBytes.get,
      termination == ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
    stderr <- _capture(stderrtext, execution.effectiveLimits.stderrBytes.get,
      termination == ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr))
    elapsed <- obj("elapsedMillis").flatMap(_.asNumber).flatMap(_.toLong).filter(_ >= 0L)
      .toRight("Registered SBT elapsed duration requires a nonnegative integer")
  } yield ProcessExecutionResult(termination, stdout, stderr, Vector.empty, elapsed, identity)

  private def _capture(text: String, limit: Long, truncated: Boolean): Either[String, ProcessExecutionCapture] =
    try {
      val bytes = Base64.getDecoder.decode(text)
      Either.cond(bytes.length.toLong <= limit, ProcessExecutionCapture(bytes.toVector, bytes.length.toLong, truncated),
        "Registered SBT capture exceeds its admitted limit")
    } catch {
      case NonFatal(_) => Left("Invalid registered SBT capture encoding")
    }

  private def _fields(json: Json, names: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == names).toRight("Invalid registered SBT response fields")

  private def _string(obj: JsonObject, name: String): Either[String, String] =
    obj(name).flatMap(_.asString).toRight("Registered SBT response field requires a string")
}

private final class RegisteredSbtHostHandle(
  execution: ResolvedProcessExecution,
  configuration: RegisteredSbtHostConfiguration,
  requestid: String,
  startedat: Long,
  onterminal: RegisteredSbtHostHandle => Unit
) extends ProcessExecutionHandle {
  private val _cancelled = new AtomicBoolean(false)
  private val _monitor = new AnyRef
  private var _result: Option[Consequence[ProcessExecutionResult]] = None

  def awaitC: Consequence[ProcessExecutionResult] = synchronized {
    _result match {
      case Some(value) => value
      case None =>
        val result = _await_c
        _result = Some(result)
        onterminal(this)
        result
    }
  }

  /** Stop waiting locally. This does not stop, or prove termination of, the external runner. */
  def cancelC: Consequence[Unit] = {
    _cancelled.set(true)
    _monitor.synchronized(_monitor.notifyAll())
    Consequence.unit
  }

  private def _await_c: Consequence[ProcessExecutionResult] =
    try {
      var remaining = _remaining_millis
      while (!_cancelled.get && remaining > 0L && !Files.exists(configuration.responseFile, LinkOption.NOFOLLOW_LINKS)) {
        _monitor.synchronized {
          if (!_cancelled.get) _monitor.wait(math.min(remaining, 25L))
        }
        remaining = _remaining_millis
      }
      if (_cancelled.get)
        Consequence.serviceUnavailable("Registered SBT host wait was cancelled; external execution state is unknown")
      else if (remaining <= 0L)
        Consequence.serviceUnavailable("Registered SBT host response timed out; external execution state is unknown")
      else
        RegisteredSbtHostDriver.readResponseC(execution, configuration, requestid)
    } catch {
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        Consequence.serviceUnavailable("Registered SBT host wait was interrupted; external execution state is unknown")
      case NonFatal(_) => Consequence.serviceUnavailable("Registered SBT host wait failed; external execution state is unknown")
    }

  private def _remaining_millis: Long =
    execution.effectiveLimits.executionTimeoutMillis.get - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat)
}
