package org.goldenport.cncf.processexecution

import java.io.{ByteArrayOutputStream, IOException, InputStream, OutputStream}
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.{Callable, ConcurrentHashMap, ExecutionException, ExecutorService, Executors, Future, TimeUnit, TimeoutException}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 17, 2026
 *  version Jul. 18, 2026
 * @version Oct. 28, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Runtime-owned local driver. It accepts only a resolved program definition,
 * starts an argument vector directly, and clears ambient process environment
 * values before launch. It never evaluates shell text.
 */
final class LocalProcessExecutionDriver(
  val safeIdentity: String = "local-process",
  private[processexecution] val _launcher: LocalProcessLauncher = LocalProcessLauncher.local,
  progress: ProcessExecutionProgress = ProcessExecutionProgress.none
) extends ProcessExecutionDriver with AutoCloseable {
  // Retain the original JVM constructor as well as existing Scala call forms.
  def this(safeIdentity: String, launcher: LocalProcessLauncher) =
    this(safeIdentity, launcher, ProcessExecutionProgress.none)

  private val _active_processes: java.util.Set[Process] =
    ConcurrentHashMap.newKeySet[Process]()
  private val _active_handles: java.util.Set[LocalProcessExecutionHandle] =
    ConcurrentHashMap.newKeySet[LocalProcessExecutionHandle]()
  private val _closed = new AtomicBoolean(false)
  private val _lifecycle_lock = new AnyRef
  private val _launches_in_flight = new AtomicInteger(0)
  private val _termination_unconfirmed = new AtomicBoolean(false)

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] =
    if (_closed.get && execution.definition._sbt_metadata.isEmpty)
      Consequence.serviceUnavailable("Local Process Execution driver is closed")
    else
      for {
        _ <- _validate_sbt_c(execution)
        _ <- _validate_prelaunch_c(execution)
        terminal <- _sbt_prelaunch_c(execution)
        handle <- terminal.fold(_launch_c(execution))(Consequence.success)
      } yield handle

  override def startC(
    execution: ResolvedProcessExecution,
    workArea: ProcessExecutionWorkArea
  ): Consequence[ProcessExecutionHandle] =
    if (_closed.get && execution.definition._sbt_metadata.isEmpty)
      Consequence.serviceUnavailable("Local Process Execution driver is closed")
    else
      for {
        _ <- _validate_sbt_c(execution)
        terminal <- _sbt_prelaunch_c(execution)
        handle <- terminal match {
          case Some(value) => Consequence.success(value)
          case None => for {
            workingdirectory <- _working_directory_c(execution, Some(workArea))
            inputfile <- _input_file_c(execution, workArea)
            launched <- _launch_c(execution, workingdirectory, inputfile, Some(workArea))
          } yield launched
        }
      } yield handle

  /** Only owner-observed facts before submission can confirm a tagged build never started. */
  private def _sbt_prelaunch_c(
    execution: ResolvedProcessExecution
  ): Consequence[Option[ProcessExecutionHandle]] =
    if (execution.definition._sbt_metadata.isEmpty)
      Consequence.success(None)
    else {
      val startedat = System.nanoTime()
      try {
        val executable = Path.of(execution.definition._executable_location)
        if (_closed.get || !Files.isRegularFile(executable) || !Files.isExecutable(executable))
          Consequence.success(Some(_completed_handle(execution, startedat, ProcessExecutionTermination.NotStarted)))
        else
          Consequence.success(None)
      } catch {
        case NonFatal(_) => Consequence.serviceUnavailable("SBT executable inspection failed before launch")
      }
    }

  private def _validate_sbt_c(execution: ResolvedProcessExecution): Consequence[Unit] =
    execution.definition._sbt_metadata.fold(Consequence.unit)(_ =>
      SbtExecutionAdmission.validateRequestC(execution.request)).flatMap { _ =>
      execution.definition._git_metadata.fold(Consequence.unit)(_ =>
        execution.definition.validateGitRequestC(execution.request).flatMap(_ =>
          execution.definition.argumentPolicy.validateC(execution.request.arguments, execution.effectiveLimits)))
    }

  private def _working_directory_c(
    execution: ResolvedProcessExecution,
    workarea: Option[ProcessExecutionWorkArea]
  ): Consequence[Option[Path]] =
    execution.definition._sbt_metadata match {
      case Some(metadata) => metadata.binding.workingDirectoryC.map(Some(_))
      case None => execution.definition._git_metadata match {
        case Some(metadata) => metadata.binding.workingDirectoryC.map(Some(_))
        case None => workarea match {
          case Some(value) => value.workingDirectoryC(execution.request.workingDirectory).map(Some(_))
          case None => Consequence.success(None)
        }
      }
    }

  /** Package-visible diagnostic seam for controlled local-driver specifications. */
  private[processexecution] def activeProcessCount: Int = _active_processes.size

  /** Package-visible lifecycle seam for controlled local-driver specifications. */
  private[processexecution] def activeHandleCount: Int = _active_handles.size

  /** Final runtime audit remains truthful after ordinary close clears resource registries. */
  def terminationConfirmed: Boolean =
    _launches_in_flight.get == 0 && _active_handles.isEmpty && _active_processes.isEmpty &&
      !_termination_unconfirmed.get

  /** Close launch admission while retaining handles and capture resources for the owning await. */
  def requestStopC(): Consequence[Unit] = {
    val handles = _lifecycle_lock.synchronized {
      _closed.set(true)
      _active_handles.asScala.toVector
    }
    var result: Consequence[Unit] = Consequence.unit
    handles.foreach { handle =>
      handle.cancelC match {
        case failure @ Consequence.Failure(_) if result.isSuccess => result = failure
        case _ => ()
      }
    }
    result
  }

  def close(): Unit = {
    val handles = _lifecycle_lock.synchronized {
      _closed.set(true)
      _active_handles.asScala.toVector
    }
    handles.foreach { handle =>
      try {
        if (!handle._cancel_and_close()) _termination_unconfirmed.set(true)
      } catch {
        case NonFatal(_) => _termination_unconfirmed.set(true)
      }
    }
    _active_handles.clear()
    _active_processes.clear()
    if (!terminationConfirmed)
      throw new IllegalStateException("Local Process Execution termination could not be confirmed")
  }

  private def _validate_prelaunch_c(
    execution: ResolvedProcessExecution
  ): Consequence[Unit] =
    execution.request.input match {
      case ProcessExecutionInput.WorkAreaFile(_) =>
        Consequence.operationIllegal(
          "process_exec",
          "WorkArea-file input requires Process Execution WorkArea support"
        )
      case _ if execution.request.workingDirectory.nonEmpty =>
        Consequence.operationIllegal(
          "process_exec",
          "WorkArea working directory requires Process Execution WorkArea support"
        )
      case _ if execution.request.outputs.nonEmpty =>
        Consequence.operationIllegal(
          "process_exec",
          "declared artifacts require Process Execution WorkArea support"
        )
      case _ if execution.request.inputFiles.nonEmpty =>
        Consequence.operationIllegal(
          "process_exec",
          "managed input files require Process Execution WorkArea support"
        )
      case _ if execution.request.resourceTrees.nonEmpty =>
        Consequence.operationIllegal(
          "process_exec",
          "managed resource trees require Process Execution WorkArea support"
        )
      case _ =>
        Consequence.unit
    }

  private def _launch_c(
    execution: ResolvedProcessExecution
  ): Consequence[ProcessExecutionHandle] =
    _working_directory_c(execution, None).flatMap(directory => _launch_c(execution, directory, None, None))

  private def _launch_c(
    execution: ResolvedProcessExecution,
    workingdirectory: Option[Path],
    inputfile: Option[Path],
    workarea: Option[ProcessExecutionWorkArea]
  ): Consequence[ProcessExecutionHandle] = {
    val startedat = System.nanoTime()
    val abandoned = new AtomicBoolean(false)
    val launched = new AtomicReference[Process](null)
    val command = execution.definition._executable_location +: execution.effectiveArguments
    ProcessExecutionProgress.observe(progress, execution, ProcessExecutionProgressState.Launching)
    _launches_in_flight.incrementAndGet()
    val task = try LocalProcessExecutionDriver._launch_executor.submit(new Callable[Process] {
      def call(): Process = try {
        val process = workingdirectory match {
          case Some(value) => _launcher.startBlocking(command, value, execution.definition._environment.values)
          case None => _launcher.startBlocking(command, execution.definition._environment.values)
        }
        launched.set(process)
        if (abandoned.get) {
          LocalProcessExecutionHandle._destroy_process_tree(process, force = true)
          throw new InterruptedException("Process launch timed out")
        }
        process
      } finally {
        _launches_in_flight.decrementAndGet()
      }
    }) catch {
      case NonFatal(_) =>
        _launches_in_flight.decrementAndGet()
        return Consequence.success(_completed_handle(execution, startedat, ProcessExecutionTermination.LaunchFailed))
    }
    try {
      val process = task.get(execution.effectiveLimits.launchTimeoutMillis.get, TimeUnit.MILLISECONDS)
      ProcessExecutionProgress.observe(progress, execution, ProcessExecutionProgressState.Started, Some(process))
      val reference = new AtomicReference[LocalProcessExecutionHandle](null)
      val handle = new LocalProcessExecutionHandle(
        process,
        execution,
        startedat,
        inputfile,
        workarea,
        progress,
        () => _termination_unconfirmed.set(true),
        () => {
          _active_processes.remove(process)
          Option(reference.get).foreach { handle =>
            _active_handles.remove(handle)
            ()
          }
        }
      )
      reference.set(handle)
      val accepted = _lifecycle_lock.synchronized {
        if (_closed.get)
          false
        else {
          _active_processes.add(process)
          _active_handles.add(handle)
          true
        }
      }
      if (accepted)
        Consequence.success(handle)
      else {
        handle.cancelC
        Consequence.success(handle)
      }
    } catch {
      case _: TimeoutException =>
        _abandon_launch(task, abandoned, launched)
        Consequence.success(_completed_handle(execution, startedat, ProcessExecutionTermination.LaunchFailed))
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        _abandon_launch(task, abandoned, launched)
        Consequence.success(_completed_handle(execution, startedat, ProcessExecutionTermination.LaunchFailed))
      case _: ExecutionException =>
        Consequence.success(_completed_handle(execution, startedat, ProcessExecutionTermination.LaunchFailed))
      case NonFatal(_) =>
        Consequence.success(_completed_handle(execution, startedat, ProcessExecutionTermination.LaunchFailed))
    }
  }

  private def _input_file_c(
    execution: ResolvedProcessExecution,
    workarea: ProcessExecutionWorkArea
  ): Consequence[Option[Path]] =
    execution.request.input match {
      case ProcessExecutionInput.WorkAreaFile(path) => workarea.inputPathC(path).map(Some(_))
      case _ => Consequence.success(None)
    }

  private def _abandon_launch(
    task: Future[Process],
    abandoned: AtomicBoolean,
    launched: AtomicReference[Process]
  ): Unit = {
    abandoned.set(true)
    Option(launched.get).foreach { process =>
      LocalProcessExecutionHandle._destroy_process_tree(process, force = true)
    }
    task.cancel(true)
  }

  private def _completed_handle(
    execution: ResolvedProcessExecution,
    startedat: Long,
    termination: ProcessExecutionTermination
  ): ProcessExecutionHandle = {
    if (termination == ProcessExecutionTermination.LaunchFailed) _termination_unconfirmed.set(true)
    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat)
    val capture = ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
    val result = ProcessExecutionResult(
      termination,
      capture,
      capture,
      Vector.empty,
      elapsed,
      execution.definition.safeProgramIdentity
    )
    ProcessExecutionProgress.observe(progress, execution, ProcessExecutionProgressState.Terminal, result = Some(result))
    new CompletedProcessExecutionHandle(result)
  }
}

object LocalProcessExecutionDriver {
  // The local driver is component-scoped, while launch workers are process-scoped
  // daemon infrastructure. Closing one component driver must not disable another.
  private val _launch_executor: ExecutorService =
    Executors.newCachedThreadPool(new DaemonThreadFactory("cncf-process-launch"))
}

private[processexecution] trait LocalProcessLauncher {
  def startBlocking(command: Vector[String]): Process

  def startBlocking(
    command: Vector[String],
    environment: Map[String, String]
  ): Process =
    if (environment.isEmpty)
      startBlocking(command)
    else
      throw new UnsupportedOperationException(
        "Local Process launcher does not support runtime-owned environment bindings"
      )

  def startBlocking(command: Vector[String], workingdirectory: Path): Process =
    startBlocking(command)

  def startBlocking(
    command: Vector[String],
    workingdirectory: Path,
    environment: Map[String, String]
  ): Process =
    if (environment.isEmpty)
      startBlocking(command, workingdirectory)
    else
      throw new UnsupportedOperationException(
        "Local Process launcher does not support runtime-owned environment bindings"
      )
}

private[processexecution] object LocalProcessLauncher {
  val local: LocalProcessLauncher = new LocalProcessLauncher {
    def startBlocking(command: Vector[String]): Process = {
      _start(command, None, Map.empty)
    }

    override def startBlocking(command: Vector[String], workingdirectory: Path): Process = {
      _start(command, Some(workingdirectory), Map.empty)
    }

    override def startBlocking(
      command: Vector[String],
      environment: Map[String, String]
    ): Process =
      _start(command, None, environment)

    override def startBlocking(
      command: Vector[String],
      workingdirectory: Path,
      environment: Map[String, String]
    ): Process =
      _start(command, Some(workingdirectory), environment)

    private def _start(
      command: Vector[String],
      workingdirectory: Option[Path],
      environment: Map[String, String]
    ): Process = {
      val builder = new ProcessBuilder(command.asJava)
      workingdirectory.foreach(path => builder.directory(path.toFile))
      builder.redirectErrorStream(false)
      // Runtime definitions own every environment value; never inherit caller state.
      builder.environment().clear()
      builder.environment().putAll(environment.asJava)
      builder.start()
    }
  }
}

private final class CompletedProcessExecutionHandle(
  result: ProcessExecutionResult
) extends ProcessExecutionHandle {
  def awaitC: Consequence[ProcessExecutionResult] = Consequence.success(result)
  def cancelC: Consequence[Unit] = Consequence.unit
}

private final class LocalProcessExecutionHandle(
  process: Process,
  execution: ResolvedProcessExecution,
  startedat: Long,
  inputfile: Option[Path],
  workarea: Option[ProcessExecutionWorkArea],
  progress: ProcessExecutionProgress,
  onunconfirmed: () => Unit,
  onterminal: () => Unit
) extends ProcessExecutionHandle {
  private val _io_executor = Executors.newFixedThreadPool(3, new DaemonThreadFactory("cncf-process-io"))
  private val _cancelled = new AtomicBoolean(false)
  private val _finished = new AtomicBoolean(false)
  private val _termination_started = new AtomicBoolean(false)
  private val _termination_lock = new AnyRef
  @volatile private var _termination_confirmed = false
  private var _tracked_descendants = Vector.empty[ProcessHandle]
  private val _stdout_capture = new AtomicReference[ProcessExecutionCapture](ProcessExecutionCapture(Vector.empty, 0L, truncated = false))
  private val _stderr_capture = new AtomicReference[ProcessExecutionCapture](ProcessExecutionCapture(Vector.empty, 0L, truncated = false))
  private val _output_limit_stream = new AtomicReference[ProcessExecutionStream](null)
  private val _stdout_future = _io_executor.submit(new Callable[ProcessExecutionCapture] {
    def call(): ProcessExecutionCapture =
      _read_capture(process.getInputStream, execution.effectiveLimits.stdoutBytes.get, ProcessExecutionStream.Stdout)
  })
  private val _stderr_future = _io_executor.submit(new Callable[ProcessExecutionCapture] {
    def call(): ProcessExecutionCapture =
      _read_capture(process.getErrorStream, execution.effectiveLimits.stderrBytes.get, ProcessExecutionStream.Stderr)
  })
  private val _stdin_future = _io_executor.submit(new Callable[Unit] {
    def call(): Unit =
      _write_input(process.getOutputStream, execution.request.input, inputfile)
  })
  @volatile private var _result: Option[ProcessExecutionResult] = None

  def awaitC: Consequence[ProcessExecutionResult] = synchronized {
    _result match {
      case Some(result) => Consequence.success(result)
      case None => _await_c
    }
  }

  def cancelC: Consequence[Unit] = _termination_lock.synchronized {
    if (_result.isEmpty) {
      _cancelled.set(true)
      _terminate_process()
      if (_termination_confirmed) Consequence.unit
      else {
        onunconfirmed()
        Consequence.serviceUnavailable("Process Execution termination could not be confirmed")
      }
    } else Consequence.unit
  }

  private[processexecution] def _cancel_and_close(): Boolean = {
    val cancelled = cancelC
    if (_result.isEmpty)
      ProcessExecutionProgress.observe(progress, execution, ProcessExecutionProgressState.Uncertain, Some(process))
    _finish()
    cancelled.isSuccess && !_result.exists(_.termination == ProcessExecutionTermination.TerminationUnconfirmed)
  }

  private def _await_c: Consequence[ProcessExecutionResult] = {
    ProcessExecutionProgress.observe(progress, execution, ProcessExecutionProgressState.Waiting, Some(process))
    try {
      val exited = process.waitFor(_remaining_execution_timeout_millis, TimeUnit.MILLISECONDS)
      val termination =
        if (!exited) {
          _terminate_process()
          ProcessExecutionTermination.TimedOut
        } else if (_cancelled.get) {
          _terminate_process()
          ProcessExecutionTermination.Cancelled
        } else {
          Option(_output_limit_stream.get) match {
            case Some(stream) => ProcessExecutionTermination.OutputLimitExceeded(stream)
            case None => ProcessExecutionTermination.Exited(process.exitValue())
          }
        }
      val stdout = _await_capture(_stdout_future)
      val stderr = _await_capture(_stderr_future)
      _await_input(_stdin_future)
      val completed = _termination_lock.synchronized {
        _result_c(_confirmed_termination(termination), stdout, stderr).map { result =>
          _complete(result)
          result
        }
      }
      if (completed.isFaillure)
        ProcessExecutionProgress.observe(progress, execution, ProcessExecutionProgressState.Uncertain, Some(process))
      completed
    } catch {
      case _: TimeoutException =>
        _terminate_process()
        val result = _terminal_result(_confirmed_termination(_timeout_termination))
        _complete(result)
        Consequence.success(result)
      case _: InterruptedException =>
        _cancelled.set(true)
        _terminate_process()
        val result = _terminal_result(_confirmed_termination(ProcessExecutionTermination.Cancelled))
        _complete(result)
        Thread.currentThread().interrupt()
        Consequence.success(result)
      case NonFatal(_) =>
        _terminate_process()
        ProcessExecutionProgress.observe(progress, execution, ProcessExecutionProgressState.Uncertain, Some(process))
        Consequence.serviceUnavailable("Process Execution stream handling failed")
    } finally {
      _finish()
    }
  }

  private def _complete(result: ProcessExecutionResult): Unit =
    if (_result.isEmpty) {
      _result = Some(result)
      if (result.termination == ProcessExecutionTermination.TerminationUnconfirmed) onunconfirmed()
      ProcessExecutionProgress.observe(progress, execution, ProcessExecutionProgressState.Terminal, Some(process), Some(result))
    }

  private def _finish(): Unit =
    if (_finished.compareAndSet(false, true)) {
      _io_executor.shutdownNow()
      onterminal()
    }

  private def _terminal_result(
    termination: ProcessExecutionTermination
  ): ProcessExecutionResult = {
    ProcessExecutionResult(
      termination,
      _stdout_capture.get,
      _stderr_capture.get,
      Vector.empty,
      TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat),
      execution.definition.safeProgramIdentity
    )
  }

  private def _result_c(
    termination: ProcessExecutionTermination,
    stdout: ProcessExecutionCapture,
    stderr: ProcessExecutionCapture
  ): Consequence[ProcessExecutionResult] =
    workarea match {
      case Some(value) =>
        value.collectArtifactsC(execution).map { collected =>
          val effectivetermination =
            if (collected.limitExceeded && termination != ProcessExecutionTermination.TerminationUnconfirmed)
              ProcessExecutionTermination.ArtifactLimitExceeded
            else
              termination
          ProcessExecutionResult(
            effectivetermination,
            stdout,
            stderr,
            collected.artifacts,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat),
            execution.definition.safeProgramIdentity
          )
        }
      case None =>
        Consequence.success(ProcessExecutionResult(
          termination,
          stdout,
          stderr,
          Vector.empty,
          TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat),
          execution.definition.safeProgramIdentity
        ))
    }

  private def _await_capture(
    future: Future[ProcessExecutionCapture]
  ): ProcessExecutionCapture =
    future.get(if (_termination_started.get) execution.effectiveLimits.terminationGraceMillis.get
      else _remaining_execution_timeout_millis, TimeUnit.MILLISECONDS)

  private def _await_input(
    future: Future[Unit]
  ): Unit =
    try {
      future.get(_remaining_execution_timeout_millis, TimeUnit.MILLISECONDS)
      ()
    } catch {
      case _: ExecutionException => () // A process may close stdin after accepting enough input.
    }

  private def _remaining_execution_timeout_millis: Long = {
    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat)
    math.max(1L, execution.effectiveLimits.executionTimeoutMillis.get - elapsed)
  }

  private def _timeout_termination: ProcessExecutionTermination =
    if (_cancelled.get)
      ProcessExecutionTermination.Cancelled
    else
      Option(_output_limit_stream.get)
        .map(ProcessExecutionTermination.OutputLimitExceeded.apply)
        .getOrElse(ProcessExecutionTermination.TimedOut)

  private def _read_capture(
    stream: InputStream,
    limit: Long,
    streamkind: ProcessExecutionStream
  ): ProcessExecutionCapture = {
    val output = new ByteArrayOutputStream()
    val buffer = new Array[Byte](8192)
    var captured = 0L
    var truncated = false
    try {
      var read = stream.read(buffer)
      while (read != -1) {
        val remaining = math.max(0L, limit - captured)
        val accepted = math.min(remaining, read.toLong).toInt
        if (accepted > 0) {
          output.write(buffer, 0, accepted)
          captured += accepted.toLong
        }
        if (accepted < read) {
          truncated = true
          _retain_capture(streamkind, output, captured, truncated)
          _limit_output(streamkind)
        }
        _retain_capture(streamkind, output, captured, truncated)
        read = stream.read(buffer)
      }
      ProcessExecutionCapture(output.toByteArray.toVector, captured, truncated)
    } catch {
      // A forced terminal process close may race a blocked reader after the
      // bounded portion has already been captured.
      case _: IOException if _termination_started.get =>
        ProcessExecutionCapture(output.toByteArray.toVector, captured, truncated)
    } finally {
      stream.close()
    }
  }

  private def _write_input(
    stream: OutputStream,
    input: ProcessExecutionInput,
    inputfile: Option[Path]
  ): Unit =
    try {
      input match {
        case ProcessExecutionInput.Empty => ()
        case ProcessExecutionInput.Bytes(value) => stream.write(value.toArray)
        case ProcessExecutionInput.WorkAreaFile(_) =>
          inputfile.foreach { path =>
            Files.copy(path, stream)
          }
      }
      stream.flush()
    } finally {
      stream.close()
    }

  private def _limit_output(stream: ProcessExecutionStream): Unit =
    if (_output_limit_stream.compareAndSet(null, stream))
      _terminate_process()

  private def _retain_capture(
    streamkind: ProcessExecutionStream,
    output: ByteArrayOutputStream,
    captured: Long,
    truncated: Boolean
  ): Unit = {
    val capture = ProcessExecutionCapture(output.toByteArray.toVector, captured, truncated)
    if (streamkind == ProcessExecutionStream.Stdout) _stdout_capture.set(capture)
    else _stderr_capture.set(capture)
  }

  private def _confirmed_termination(termination: ProcessExecutionTermination): ProcessExecutionTermination = {
    if (_termination_started.get || _cancelled.get || _output_limit_stream.get != null) {
      _terminate_process()
      if (!_termination_confirmed) ProcessExecutionTermination.TerminationUnconfirmed
      else if (_cancelled.get) ProcessExecutionTermination.Cancelled
      else Option(_output_limit_stream.get).map(ProcessExecutionTermination.OutputLimitExceeded.apply).getOrElse(termination)
    } else termination
  }

  /** One completion for cancel, await and output-limit races; never waits for an IO worker here. */
  private def _terminate_process(): Unit = _termination_lock.synchronized {
    if (!_termination_started.get) {
      _termination_started.set(true)
      var interrupted = Thread.interrupted()
      var inspected = true
      val grace = execution.effectiveLimits.terminationGraceMillis.get
      try {
        if (process.isAlive)
          _tracked_descendants = process.toHandle.descendants().iterator().asScala.toVector.reverse
        else
          inspected = false // Root exit before any tree observation cannot prove descendant absence.
      } catch { case NonFatal(_) => inspected = false }
      def _alive_(): Boolean =
        try process.isAlive || _tracked_descendants.exists(_.isAlive)
        catch { case NonFatal(_) => inspected = false; true }
      def _signal_(force: Boolean): Unit = {
        _tracked_descendants.foreach { handle =>
          try {
            if (handle.isAlive) { if (force) handle.destroyForcibly() else handle.destroy() }
          } catch { case NonFatal(_) => inspected = false }
        }
        try {
          if (process.isAlive) { if (force) process.destroyForcibly() else process.destroy() }
        } catch { case NonFatal(_) => inspected = false }
      }
      def _wait_(): Unit = {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(grace)
        while (_alive_() && System.nanoTime() < deadline) {
          try TimeUnit.MILLISECONDS.sleep(1L)
          catch { case _: InterruptedException => interrupted = true }
        }
      }
      try {
        _signal_(false)
        _wait_()
        // Retained handles remain valid even after the root exits and descendants reparent.
        _signal_(true)
        _wait_()
        _termination_confirmed = inspected && !_alive_()
      } finally {
        if (interrupted) Thread.currentThread().interrupt()
      }
    }
  }
}

private object LocalProcessExecutionHandle {
  def _destroy_process_tree(process: Process, force: Boolean): Unit = {
    val descendants = process.toHandle.descendants().iterator().asScala.toVector.reverse
    descendants.foreach { handle =>
      if (force) handle.destroyForcibly() else handle.destroy()
    }
    if (force) process.destroyForcibly() else process.destroy()
  }
}

private final class DaemonThreadFactory(
  prefix: String
) extends java.util.concurrent.ThreadFactory {
  private val _counter = new AtomicInteger(0)

  def newThread(runnable: Runnable): Thread = {
    val thread = new Thread(runnable, s"$prefix-${_counter.incrementAndGet}")
    thread.setDaemon(true)
    thread
  }
}
