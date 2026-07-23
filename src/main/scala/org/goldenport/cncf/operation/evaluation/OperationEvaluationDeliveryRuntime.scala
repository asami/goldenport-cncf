package org.goldenport.cncf.operation.evaluation

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.{ArrayBlockingQueue, Callable, RejectedExecutionException, ThreadFactory, ThreadPoolExecutor, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}
import scala.util.control.NonFatal

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.{CallTreeContext, ConclusionDiagnostics}
import org.goldenport.cncf.spi.evaluation.{CorpusEvaluationSink, ExperimentEvaluationSink}

/*
 * Bounded, subsystem-owned delivery boundary for auxiliary evaluation facts.
 * Provider behavior must never replace the canonical operation outcome.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationEvaluationDeliveryPolicy(
  workerCount: Int = 4,
  queueCapacity: Int = 64,
  invocationTimeout: Duration = Duration.ofSeconds(1),
  maximumItemBytes: Int = 64 * 1024,
  maximumQueuedBytes: Long = 4L * 1024L * 1024L
)

trait OperationEvaluationDeliveryObserver {
  def observe(
    diagnostic: OperationEvaluationDeliveryDiagnostic,
    elapsedmillis: Long,
    context: ExecutionContext
  ): Unit
}

object OperationEvaluationDeliveryObserver {
  val default: OperationEvaluationDeliveryObserver =
    new OperationEvaluationDeliveryObserver {
      def observe(
        diagnostic: OperationEvaluationDeliveryDiagnostic,
        elapsedmillis: Long,
        context: ExecutionContext
      ): Unit = {
        context.runtime.noteOperationEvaluationDelivery(diagnostic)
        RuntimeDashboardMetrics.recordOperationEvaluationDelivery(
          operation = diagnostic.operation.print,
          factkind = diagnostic.factKind.token,
          factsource = diagnostic.factSource.token,
          sinkcontract = diagnostic.sinkContract.print,
          socketcomponent = diagnostic.socketComponent.print,
          providercomponent = diagnostic.providerComponent.print,
          status = diagnostic.status.token,
          limitationkinds = diagnostic.limitationKinds.map(_.token),
          diagnostickeys = diagnostic.diagnosticKeys.map(_.token),
          error = diagnostic.status != OperationEvaluationDeliveryStatus.Delivered,
          elapsedmillis = Some(elapsedmillis)
        )
        val calltree = context.observability.callTreeContext
        if (calltree.isEnabled)
          calltree.mark(
            "operation-evaluation:delivery",
            Map(
              "calltree_kind" -> "operation-evaluation-delivery",
              "operation" -> diagnostic.operation.print,
              "fact_kind" -> diagnostic.factKind.token,
              "fact_source" -> diagnostic.factSource.token,
              "sink_contract" -> diagnostic.sinkContract.print,
              "socket_component" -> diagnostic.socketComponent.print,
              "provider_component" -> diagnostic.providerComponent.print,
              "outcome" ->
                (if (diagnostic.status == OperationEvaluationDeliveryStatus.Delivered) "success" else "failure"),
              "delivery_status" -> diagnostic.status.token,
              "limitation_kinds" -> diagnostic.limitationKinds.map(_.token).mkString(","),
              "diagnostic_keys" -> diagnostic.diagnosticKeys.map(_.token).mkString(","),
              "duration_ms" -> elapsedmillis.toString
            ).filter(_._2.nonEmpty)
          )
      }
    }

  val noop: OperationEvaluationDeliveryObserver =
    new OperationEvaluationDeliveryObserver {
      def observe(
        diagnostic: OperationEvaluationDeliveryDiagnostic,
        elapsedmillis: Long,
        context: ExecutionContext
      ): Unit = ()
    }
}

final class OperationEvaluationDeliveryRuntime(
  policy: OperationEvaluationDeliveryPolicy = OperationEvaluationDeliveryPolicy(),
  observer: OperationEvaluationDeliveryObserver = OperationEvaluationDeliveryObserver.default
) extends AutoCloseable {
  require(policy.workerCount > 0, "workerCount must be positive")
  require(policy.queueCapacity > 0, "queueCapacity must be positive")
  require(!policy.invocationTimeout.isNegative && !policy.invocationTimeout.isZero, "invocationTimeout must be positive")
  require(policy.maximumItemBytes > 0, "maximumItemBytes must be positive")
  require(policy.maximumQueuedBytes >= policy.maximumItemBytes, "maximumQueuedBytes must admit one item")

  private val _thread_number = new AtomicInteger(0)
  private val _queued_bytes = new AtomicLong(0L)
  private val _executor = new ThreadPoolExecutor(
    policy.workerCount,
    policy.workerCount,
    0L,
    TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue[Runnable](policy.queueCapacity),
    new ThreadFactory {
      def newThread(runnable: Runnable): Thread = {
        val thread = new Thread(runnable, s"cncf-operation-evaluation-${_thread_number.incrementAndGet()}")
        thread.setDaemon(true)
        thread
      }
    },
    new ThreadPoolExecutor.AbortPolicy
  )

  def deliverAutomatic(
    fact: OperationEvaluationFact,
    context: ExecutionContext
  ): Vector[OperationEvaluationDeliveryResult] =
    try {
      val corpus = context.cncfCore.scope.corpusEvaluationSinkOption.flatMap { sink =>
        sink.sinkIdentityOption.map(identity => _deliver_corpus(fact, sink, identity, context))
      }
      val experiment = context.cncfCore.scope.experimentEvaluationSinkOption.flatMap { sink =>
        sink.sinkIdentityOption.map(identity => _deliver_experiment(fact, sink, identity, context))
      }
      Vector(corpus, experiment).flatten
    } catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
        Vector.empty
      case NonFatal(_) =>
        Vector.empty
    }

  def deliverSupplemental(
    intent: OperationEvaluationSupplementalIntent,
    context: ExecutionContext
  ): Vector[OperationEvaluationDeliveryResult] =
    try {
      intent.fact match {
        case candidate: CorpusCandidateFact =>
          context.cncfCore.scope.corpusEvaluationSinkOption
            .flatMap(sink => sink.sinkIdentityOption.map(identity =>
              _deliver_corpus(candidate, sink, identity, context)
            ))
            .toVector
        case observation: ExperimentObservationFact =>
          context.cncfCore.scope.experimentEvaluationSinkOption
            .flatMap(sink => sink.sinkIdentityOption.map(identity =>
              _deliver_experiment(observation, sink, identity, context)
            ))
            .toVector
      }
    } catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
        Vector.empty
      case NonFatal(_) =>
        Vector.empty
    }

  def deliver(
    fact: OperationEvaluationFact,
    sink: OperationEvaluationSinkIdentity,
    context: ExecutionContext
  )(body: ExecutionContext => Consequence[OperationEvaluationDeliveryResult]): OperationEvaluationDeliveryResult =
    _observe_delivery(fact, sink, context) {
      _deliver(fact, sink, context)(body)
    }

  private def _deliver(
    fact: OperationEvaluationFact,
    sink: OperationEvaluationSinkIdentity,
    context: ExecutionContext
  )(body: ExecutionContext => Consequence[OperationEvaluationDeliveryResult]): OperationEvaluationDeliveryResult =
    try {
      val bytes = fact.toRecord.print.getBytes(StandardCharsets.UTF_8).length
      if (bytes > policy.maximumItemBytes)
        _limited(fact, sink, OperationEvaluationLimitationKind.Overflow)
      else if (!_reserve(bytes))
        _limited(fact, sink, OperationEvaluationLimitationKind.Saturated)
      else {
        val released = new AtomicBoolean(false)
        def _release_(): Unit =
          if (released.compareAndSet(false, true))
            _queued_bytes.addAndGet(-bytes.toLong)

        try {
          val future = _executor.submit(new Callable[Consequence[OperationEvaluationDeliveryResult]] {
            def call(): Consequence[OperationEvaluationDeliveryResult] =
              try {
                val observability = context.cncfCore.observability.copy(
                  callTreeContext = CallTreeContext.Disabled
                )
                body(ExecutionContext.withObservabilityContext(context, observability))
              }
              finally _release_()
          })
          try {
            future.get(policy.invocationTimeout.toNanos, TimeUnit.NANOSECONDS) match {
              case Consequence.Success(result) => result
              case Consequence.Failure(conclusion) => _failed(fact, sink, conclusion)
            }
          } catch {
            case _: TimeoutException =>
              future.cancel(true)
              _executor.purge()
              _release_()
              _limited(fact, sink, OperationEvaluationLimitationKind.Timeout)
            case e: InterruptedException =>
              future.cancel(true)
              _executor.purge()
              _release_()
              Thread.currentThread.interrupt()
              _failed(fact, sink, Conclusion.from(e))
            case NonFatal(e) =>
              _failed(fact, sink, Conclusion.from(e))
          }
        } catch {
          case _: RejectedExecutionException =>
            _release_()
            _limited(fact, sink, OperationEvaluationLimitationKind.Saturated)
          case e: InterruptedException =>
            _release_()
            Thread.currentThread.interrupt()
            _failed(fact, sink, Conclusion.from(e))
          case NonFatal(e) =>
            _release_()
            _failed(fact, sink, Conclusion.from(e))
        }
      }
    } catch {
      case e: InterruptedException =>
        Thread.currentThread.interrupt()
        _failed(fact, sink, Conclusion.from(e))
      case NonFatal(e) =>
        _failed(fact, sink, Conclusion.from(e))
    }

  private def _reserve(bytes: Int): Boolean = {
    var done = false
    var admitted = false
    while (!done) {
      val current = _queued_bytes.get()
      val next = current + bytes.toLong
      if (next > policy.maximumQueuedBytes)
        done = true
      else if (_queued_bytes.compareAndSet(current, next)) {
        admitted = true
        done = true
      }
    }
    admitted
  }

  private def _deliver_corpus(
    fact: OperationEvaluationFact,
    sink: CorpusEvaluationSink,
    identity: OperationEvaluationSinkIdentity,
    context: ExecutionContext
  ): OperationEvaluationDeliveryResult =
    _active_sink_limitation(fact, identity, context) match {
      case Some(result) =>
        _observe_delivery(fact, identity, context)(result)
      case None =>
        deliver(fact, identity, context) { deliverycontext =>
          given ExecutionContext = deliverycontext
          fact match {
            case start: OperationEvaluationStartFact => sink.recordStart(start)
            case terminal: OperationEvaluationTerminalFact => sink.recordTerminal(terminal)
            case candidate: CorpusCandidateFact => sink.submitCandidate(candidate)
            case _: ExperimentObservationFact =>
              Consequence.success(_limited(fact, identity, OperationEvaluationLimitationKind.Unsupported))
          }
        }
    }

  private def _deliver_experiment(
    fact: OperationEvaluationFact,
    sink: ExperimentEvaluationSink,
    identity: OperationEvaluationSinkIdentity,
    context: ExecutionContext
  ): OperationEvaluationDeliveryResult =
    _active_sink_limitation(fact, identity, context) match {
      case Some(result) =>
        _observe_delivery(fact, identity, context)(result)
      case None =>
        deliver(fact, identity, context) { deliverycontext =>
          given ExecutionContext = deliverycontext
          fact match {
            case start: OperationEvaluationStartFact => sink.recordStart(start)
            case terminal: OperationEvaluationTerminalFact => sink.recordTerminal(terminal)
            case observation: ExperimentObservationFact => sink.submitObservation(observation)
            case _: CorpusCandidateFact =>
              Consequence.success(_limited(fact, identity, OperationEvaluationLimitationKind.Unsupported))
          }
        }
    }

  private def _active_sink_limitation(
    fact: OperationEvaluationFact,
    identity: OperationEvaluationSinkIdentity,
    context: ExecutionContext
  ): Option[OperationEvaluationDeliveryResult] =
    context.cncfCore.scope.operationEvaluationCrossSinkPolicy
      .limitation(context.operationEvaluation.activeSinks, identity)
      .map(_limited(fact, identity, _))

  private def _limited(
    fact: OperationEvaluationFact,
    sink: OperationEvaluationSinkIdentity,
    kind: OperationEvaluationLimitationKind
  ): OperationEvaluationDeliveryResult =
    OperationEvaluationDeliveryResult(
      fact.id,
      sink,
      OperationEvaluationDeliveryStatus.Limited,
      Vector(OperationEvaluationLimitation(kind)),
      fact.confidentiality
    )

  private def _failed(
    fact: OperationEvaluationFact,
    sink: OperationEvaluationSinkIdentity,
    conclusion: Conclusion
  ): OperationEvaluationDeliveryResult =
    OperationEvaluationDeliveryResult(
      fact.id,
      sink,
      OperationEvaluationDeliveryStatus.Failed,
      Vector(OperationEvaluationLimitation(
        OperationEvaluationLimitationKind.ProviderFailure,
        diagnostic = Some(ConclusionDiagnostics.classify(conclusion))
      )),
      fact.confidentiality
    )

  private def _observe_delivery(
    fact: OperationEvaluationFact,
    sink: OperationEvaluationSinkIdentity,
    context: ExecutionContext
  )(body: => OperationEvaluationDeliveryResult): OperationEvaluationDeliveryResult = {
    val started = System.nanoTime()
    val result = body
    val diagnostic = OperationEvaluationDeliveryDiagnostic.from(fact, result)
    val elapsedmillis = (System.nanoTime() - started) / 1000000L
    _best_effort {
      observer.observe(diagnostic, elapsedmillis, context)
    }
    result
  }

  private def _best_effort(body: => Unit): Unit =
    try body
    catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
      case NonFatal(_) => ()
    }

  def close(): Unit = {
    _executor.shutdownNow()
    val _ = _executor.awaitTermination(policy.invocationTimeout.toMillis, TimeUnit.MILLISECONDS)
  }
}
