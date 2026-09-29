package org.goldenport.cncf.composite

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
enum CompositeQueryV2Target {
  case Local
  case Subsystem(id: String)
}

enum CompositeQueryV2DependencyMode {
  case RequireValue
  case AfterCompletion
}

final case class CompositeQueryV2Dependency(
  branchId: String,
  mode: CompositeQueryV2DependencyMode = CompositeQueryV2DependencyMode.RequireValue
)

enum CompositeQueryV2FailureMode {
  case CollectAll
  case FailFastRequired
}

enum CompositeQueryV2Persistence {
  case Ephemeral
  case PersistentDiagnostics
}

enum CompositeQueryV2Outcome {
  case Succeeded
  case Fallback
  case Failed
  case Skipped
  case Cancelled
  case TimedOut

  def isUsable: Boolean = this match {
    case Succeeded | Fallback => true
    case _ => false
  }

  def isTerminal: Boolean = true
}

enum CompositeQueryV2FailureCode {
  case AccessDenied
  case QueryRejected
  case QueryFailed
  case SchedulerRejected
  case DependencyUnavailable
  case Cancelled
  case TimedOut
  case ResponseLimitExceeded
  case UnsupportedResponse
  case ContextUnavailable
  case InternalFailure
}

final case class CompositeQueryV2Fallback(
  response: OperationResponse,
  onCodes: Set[CompositeQueryV2FailureCode] = Set(CompositeQueryV2FailureCode.QueryFailed)
)

final case class CompositeQueryV2Branch(
  branchId: String,
  request: Request,
  required: Boolean = true,
  target: CompositeQueryV2Target = CompositeQueryV2Target.Local,
  dependencies: Vector[CompositeQueryV2Dependency] = Vector.empty,
  timeoutMillis: Option[Long] = None,
  fallback: Option[CompositeQueryV2Fallback] = None
)

final case class CompositeQueryV2Policy(
  maxParallelism: Int = 4,
  timeoutMillis: Long = 30000L,
  maxBranches: Int = 64,
  maxResponseBytes: Int = 262144,
  failureMode: CompositeQueryV2FailureMode = CompositeQueryV2FailureMode.CollectAll,
  persistence: CompositeQueryV2Persistence = CompositeQueryV2Persistence.Ephemeral
)

final case class CompositeQueryV2Request(
  branches: Vector[CompositeQueryV2Branch],
  policy: CompositeQueryV2Policy = CompositeQueryV2Policy(),
  protocolVersion: Int = 2
) {
  def validate: Consequence[CompositeQueryV2Request] =
    CompositeQueryV2Request.validate(this)
}

final case class CompositeQueryV2Failure(
  code: CompositeQueryV2FailureCode
)

final case class CompositeQueryV2BranchResult(
  branchId: String,
  required: Boolean,
  outcome: CompositeQueryV2Outcome,
  response: Option[OperationResponse] = None,
  failure: Option[CompositeQueryV2Failure] = None,
  recoveredFailure: Option[CompositeQueryV2FailureCode] = None
) {
  def isUsable: Boolean = outcome.isUsable
}

final case class CompositeQueryV2Diagnostic(
  branchId: String,
  targetId: String,
  outcome: CompositeQueryV2Outcome,
  failureCode: Option[CompositeQueryV2FailureCode],
  elapsedMillis: Long,
  queuedMillis: Long,
  cancelled: Boolean,
  fallback: Boolean,
  jobId: Option[String]
)

enum CompositeQueryV2AggregateStatus {
  case Succeeded
  case PartialFailure
  case Failed
  case Cancelled
  case TimedOut
}

final case class CompositeQueryV2Response(
  results: Vector[CompositeQueryV2BranchResult],
  diagnostics: Vector[CompositeQueryV2Diagnostic],
  status: CompositeQueryV2AggregateStatus
) {
  lazy val resultMap: Map[String, CompositeQueryV2BranchResult] =
    results.map(x => x.branchId -> x).toMap

  def result(branchId: String): Option[CompositeQueryV2BranchResult] =
    resultMap.get(branchId)

  def requiredSucceeded: Boolean =
    results.filter(_.required).forall(_.isUsable)

  def requiredRecord(branchId: String): Consequence[Record] =
    result(branchId) match {
      case Some(CompositeQueryV2BranchResult(_, _, _, Some(OperationResponse.RecordResponse(record)), _, _)) =>
        Consequence.success(record)
      case Some(_) => Consequence.operationInvalid("composite-query-v2.required-record")
      case None => Consequence.operationInvalid("composite-query-v2.branch-not-found")
    }

  def optionalRecord(branchId: String): Option[Record] =
    result(branchId).flatMap(_.response).collect {
      case OperationResponse.RecordResponse(record) => record
    }

  def toLegacyResponse: Consequence[CompositeQueryResponse] = {
    val required = results.find(x => x.required && !x.isUsable)
    required match {
      case Some(_) => Consequence.operationInvalid("composite-query-v2.required-branch-unusable")
      case None =>
        val legacy = results.map { result =>
          val diagnostic =
            if (result.isUsable) None
            else Some(CompositeQueryDiagnostic(result.branchId, result.required, "composite-query-v2.optional-branch-unusable"))
          CompositeQueryResult(result.branchId, result.required, result.response, diagnostic)
        }
        val diagnostics = legacy.flatMap(_.diagnostic)
        Consequence.success(CompositeQueryResponse(legacy, diagnostics))
    }
  }
}

final class CompositeQueryV2Cancellation private () {
  private val _cancelled = new AtomicBoolean(false)
  private var _callbacks = Map.empty[Long, () => Unit]
  private var _next_id = 0L

  def isCancelled: Boolean = _cancelled.get()

  def cancel(): Unit = {
    val callbacks = synchronized {
      if (!_cancelled.compareAndSet(false, true)) Vector.empty
      else {
        val result = _callbacks.values.toVector
        _callbacks = Map.empty
        result
      }
    }
    callbacks.foreach(_())
  }

  private[composite] def register(callback: => Unit): AutoCloseable = {
    val call = () => callback
    val registration = synchronized {
      if (_cancelled.get()) None
      else {
        _next_id += 1L
        val id = _next_id
        _callbacks = _callbacks.updated(id, call)
        Some(id)
      }
    }
    registration match {
      case Some(id) => new AutoCloseable {
        private var _closed = false
        def close(): Unit = CompositeQueryV2Cancellation.this.synchronized {
          if (!_closed) {
            _closed = true
            _callbacks = _callbacks - id
          }
        }
      }
      case None =>
        call()
        new AutoCloseable { def close(): Unit = () }
    }
  }
}

object CompositeQueryV2Cancellation {
  def fresh: CompositeQueryV2Cancellation = new CompositeQueryV2Cancellation()
}

object CompositeQueryV2Request {
  private val _branch_id = "[A-Za-z][A-Za-z0-9_.-]{0,63}".r

  def validate(request: CompositeQueryV2Request): Consequence[CompositeQueryV2Request] = {
    val policy = request.policy
    val ids = request.branches.map(_.branchId)
    val requestchecks = Vector(
      _require(request.protocolVersion == 2, "composite-query-v2.protocol-version"),
      _require(policy.maxBranches >= 1 && request.branches.size <= policy.maxBranches && policy.maxBranches <= 64, "composite-query-v2.branch-limit"),
      _require(policy.maxParallelism >= 1 && policy.maxParallelism <= 16, "composite-query-v2.parallelism"),
      _require(policy.timeoutMillis >= 1L && policy.timeoutMillis <= 60000L, "composite-query-v2.timeout"),
      _require(policy.maxResponseBytes >= 1 && policy.maxResponseBytes <= 262144, "composite-query-v2.response-limit"),
      _require(ids.distinct.size == ids.size, "composite-query-v2.duplicate-branch"),
      _require(request.branches.flatMap(_.dependencies).size <= 256, "composite-query-v2.dependency-limit"),
      _require(!_has_cycle(request.branches), "composite-query-v2.cyclic-dependency")
    )
    val branchchecks = request.branches.flatMap { branch =>
      val validid = _branch_id.findFirstIn(branch.branchId).contains(branch.branchId)
      val targetvalid = branch.target match {
        case CompositeQueryV2Target.Subsystem(id) => id.trim.nonEmpty
        case _ => true
      }
      val timeoutvalid = branch.timeoutMillis.forall(timeout => timeout >= 1L && timeout <= policy.timeoutMillis)
      val edgeids = branch.dependencies.map(x => x.branchId -> x.mode)
      val fallbackvalid = branch.fallback.forall { fallback =>
        CompositeQueryV2ResponseEncoding.byteSize(fallback.response).exists(_ <= policy.maxResponseBytes)
      }
      Vector(
        _require(validid, "composite-query-v2.branch-id"),
        _require(branch.request.operation.trim.nonEmpty, "composite-query-v2.empty-request"),
        _require(targetvalid, "composite-query-v2.target-id"),
        _require(timeoutvalid, "composite-query-v2.branch-timeout"),
        _require(edgeids.distinct.size == edgeids.size, "composite-query-v2.duplicate-dependency"),
        _require(branch.dependencies.forall(dependency => ids.contains(dependency.branchId) && dependency.branchId != branch.branchId), "composite-query-v2.dependency"),
        _require(fallbackvalid, "composite-query-v2.fallback-response")
      )
    }
    Consequence.zipN(requestchecks ++ branchchecks).map(_ => request)
  }

  private def _require(condition: Boolean, code: String): Consequence[Unit] =
    if (condition) Consequence.unit else Consequence.argumentInvalid(code)

  private def _has_cycle(branches: Vector[CompositeQueryV2Branch]): Boolean = {
    val dependencies = branches.map(x => x.branchId -> x.dependencies.map(_.branchId)).toMap
    def _visit_(id: String, active: Set[String], done: Set[String]): Boolean =
      if (active.contains(id)) true
      else if (done.contains(id)) false
      else dependencies.getOrElse(id, Vector.empty).exists(_visit_(_, active + id, done + id))
    branches.exists(branch => _visit_(branch.branchId, Set.empty, Set.empty))
  }
}

object CompositeQueryV2ResponseEncoding {
  def byteSize(response: OperationResponse): Option[Int] =
    try {
      response match {
        case OperationResponse.Void() => Some(0)
        case OperationResponse.Json(json) => Some(json.noSpaces.getBytes(StandardCharsets.UTF_8).length)
        case OperationResponse.Yaml(yaml) => Some(yaml.getBytes(StandardCharsets.UTF_8).length)
        case OperationResponse.RecordResponse(record) => org.goldenport.record.io.RecordEncoder().jsonC(record).toOption.map(_.getBytes(StandardCharsets.UTF_8).length)
        case scalar: OperationResponse.Scalar[?] => scalar.value match {
          case value: String => Some(value.getBytes(StandardCharsets.UTF_8).length)
          case value: Byte => Some(value.toString.getBytes(StandardCharsets.UTF_8).length)
          case value: Short => Some(value.toString.getBytes(StandardCharsets.UTF_8).length)
          case value: Int => Some(value.toString.getBytes(StandardCharsets.UTF_8).length)
          case value: Long => Some(value.toString.getBytes(StandardCharsets.UTF_8).length)
          case value: Float => Some(value.toString.getBytes(StandardCharsets.UTF_8).length)
          case value: Double => Some(value.toString.getBytes(StandardCharsets.UTF_8).length)
          case value: Boolean => Some(value.toString.getBytes(StandardCharsets.UTF_8).length)
          case _ => None
        }
        case _: OperationResponse.Http | _: OperationResponse.Opaque => None
      }
    } catch {
      case _: Throwable => None
    }
}
