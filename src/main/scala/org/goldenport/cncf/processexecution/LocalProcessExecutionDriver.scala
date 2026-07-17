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
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Runtime-owned local driver. It accepts only a resolved program definition,
 * starts an argument vector directly, and clears ambient process environment
 * values before launch. It never evaluates shell text.
 */
final class LocalProcessExecutionDriver(
  val safeIdentity: String = "local-process",
  private[processexecution] val _launcher: LocalProcessLauncher = LocalProcessLauncher.local
) extends ProcessExecutionDriver with AutoCloseable {
  private val _launch_executor: ExecutorService =
    Executors.newCachedThreadPool(new _daemon_thread_factory("cncf-process-launch"))
  private val _active_processes: java.util.Set[Process] =
    ConcurrentHashMap.newKeySet[Process]()
  private val _active_handles: java.util.Set[_local_process_execution_handle] =
    ConcurrentHashMap.newKeySet[_local_process_execution_handle]()
  private val _closed = new AtomicBoolean(false)
  private val _lifecycle_lock = new AnyRef

  def startC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] =
    if (_closed.get)
      Consequence.serviceUnavailable("Local Process Execution driver is closed")
    else
      _validate_prelaunch_c(execution).flatMap { _ =>
        _launch_c(execution)
      }

  override def startC(
    execution: ResolvedProcessExecution,
    workArea: ProcessExecutionWorkArea
  ): Consequence[ProcessExecutionHandle] =
    if (_closed.get)
      Consequence.serviceUnavailable("Local Process Execution driver is closed")
    else
      for {
        workingDirectory <- workArea.workingDirectoryC(execution.request.workingDirectory)
        inputFile <- _input_file_c(execution, workArea)
        handle <- _launch_c(execution, Some(workingDirectory), inputFile, Some(workArea))
      } yield handle

  /** Package-visible diagnostic seam for controlled local-driver specifications. */
  private[processexecution] def activeProcessCount: Int = _active_processes.size

  /** Package-visible lifecycle seam for controlled local-driver specifications. */
  private[processexecution] def activeHandleCount: Int = _active_handles.size

  def close(): Unit = {
    val handles = _lifecycle_lock.synchronized {
      _closed.set(true)
      _active_handles.asScala.toVector
    }
    handles.foreach { handle =>
      handle._cancel_and_close()
    }
    _active_handles.clear()
    _active_processes.clear()
    _launch_executor.shutdownNow()
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
    _launch_c(execution, None, None, None)

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
    val task = _launch_executor.submit(new Callable[Process] {
      def call(): Process = {
        val process = workingdirectory match {
          case Some(value) => _launcher.startBlocking(command, value, execution.definition._environment.values)
          case None => _launcher.startBlocking(command, execution.definition._environment.values)
        }
        launched.set(process)
        if (abandoned.get) {
          _local_process_execution_handle._destroy_process_tree(process, force = true)
          throw new InterruptedException("Process launch timed out")
        }
        process
      }
    })
    try {
      val process = task.get(execution.effectiveLimits.launchTimeoutMillis.get, TimeUnit.MILLISECONDS)
      val reference = new AtomicReference[_local_process_execution_handle](null)
      val handle = new _local_process_execution_handle(
        process,
        execution,
        startedat,
        inputfile,
        workarea,
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
        handle._cancel_and_close()
        Consequence.success(_completed_handle(execution, startedat, ProcessExecutionTermination.LaunchFailed))
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
      _local_process_execution_handle._destroy_process_tree(process, force = true)
    }
    task.cancel(true)
  }

  private def _completed_handle(
    execution: ResolvedProcessExecution,
    startedat: Long,
    termination: ProcessExecutionTermination
  ): ProcessExecutionHandle = {
    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat)
    val capture = ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
    new _completed_process_execution_handle(ProcessExecutionResult(
      termination,
      capture,
      capture,
      Vector.empty,
      elapsed,
      execution.definition.safeProgramIdentity
    ))
  }
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

private final class _completed_process_execution_handle(
  result: ProcessExecutionResult
) extends ProcessExecutionHandle {
  def awaitC: Consequence[ProcessExecutionResult] = Consequence.success(result)
  def cancelC: Consequence[Unit] = Consequence.unit
}

private final class _local_process_execution_handle(
  process: Process,
  execution: ResolvedProcessExecution,
  startedat: Long,
  inputfile: Option[Path],
  workarea: Option[ProcessExecutionWorkArea],
  onterminal: () => Unit
) extends ProcessExecutionHandle {
  private val _io_executor = Executors.newFixedThreadPool(3, new _daemon_thread_factory("cncf-process-io"))
  private val _cancelled = new AtomicBoolean(false)
  private val _finished = new AtomicBoolean(false)
  private val _termination_started = new AtomicBoolean(false)
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

  def cancelC: Consequence[Unit] = {
    if (_result.isEmpty && process.isAlive) {
      _cancelled.set(true)
      _terminate_process()
    }
    Consequence.unit
  }

  private[processexecution] def _cancel_and_close(): Unit = {
    cancelC
    _finish()
  }

  private def _await_c: Consequence[ProcessExecutionResult] =
    try {
      val exited = process.waitFor(_remaining_execution_timeout_millis, TimeUnit.MILLISECONDS)
      val termination =
        if (!exited) {
          _terminate_process()
          ProcessExecutionTermination.TimedOut
        } else if (_cancelled.get) {
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
      _result_c(termination, stdout, stderr).map { result =>
        _complete(result)
        result
      }
    } catch {
      case _: TimeoutException =>
        _terminate_process()
        val result = _terminal_result(_timeout_termination)
        _complete(result)
        Consequence.success(result)
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        _cancelled.set(true)
        _terminate_process()
        val result = _terminal_result(ProcessExecutionTermination.Cancelled)
        _complete(result)
        Consequence.success(result)
      case NonFatal(_) =>
        _terminate_process()
        Consequence.serviceUnavailable("Process Execution stream handling failed")
    } finally {
      _finish()
    }

  private def _complete(result: ProcessExecutionResult): Unit =
    if (_result.isEmpty)
      _result = Some(result)

  private def _finish(): Unit =
    if (_finished.compareAndSet(false, true)) {
      _io_executor.shutdownNow()
      onterminal()
    }

  private def _terminal_result(
    termination: ProcessExecutionTermination
  ): ProcessExecutionResult = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
    ProcessExecutionResult(
      termination,
      capture,
      capture,
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
            if (collected.limitExceeded)
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
    future.get(_remaining_execution_timeout_millis, TimeUnit.MILLISECONDS)

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
          _limit_output(streamkind)
        }
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

  private def _terminate_process(): Unit =
    if (_termination_started.compareAndSet(false, true)) {
      _local_process_execution_handle._destroy_process_tree(process, force = false)
      try {
        if (!process.waitFor(execution.effectiveLimits.terminationGraceMillis.get, TimeUnit.MILLISECONDS))
          _local_process_execution_handle._destroy_process_tree(process, force = true)
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          _local_process_execution_handle._destroy_process_tree(process, force = true)
      }
    }
}

private object _local_process_execution_handle {
  def _destroy_process_tree(process: Process, force: Boolean): Unit = {
    val descendants = process.toHandle.descendants().iterator().asScala.toVector.reverse
    descendants.foreach { handle =>
      if (force) handle.destroyForcibly() else handle.destroy()
    }
    if (force) process.destroyForcibly() else process.destroy()
  }
}

private final class _daemon_thread_factory(
  prefix: String
) extends java.util.concurrent.ThreadFactory {
  private val _counter = new AtomicInteger(0)

  def newThread(runnable: Runnable): Thread = {
    val thread = new Thread(runnable, s"$prefix-${_counter.incrementAndGet}")
    thread.setDaemon(true)
    thread
  }
}
