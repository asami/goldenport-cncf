package org.goldenport.cncf.observability

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.{Duration, Instant}
import java.security.MessageDigest
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.util.control.NonFatal
import io.circe.Json
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.{RuntimeMetricPoint, RuntimeMetricsSnapshot}
import org.goldenport.record.Record
import org.goldenport.observation.calltree.CallTree

/*
 * @since   May. 11, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OpenTelemetryExportConfig(
  enabled: Boolean = false,
  endpoint: Option[String] = None,
  protocol: String = OpenTelemetryExportConfig.ProtocolOtlpHttp,
  tracesEnabled: Boolean = true,
  metricsEnabled: Boolean = true,
  logsEnabled: Boolean = false,
  validationError: Option[String] = None
) {
  def normalizedEndpoint(operationMode: OperationMode): Option[String] =
    endpoint.map(_.trim).filter(_.nonEmpty).orElse {
      Option.when(enabled && operationMode != OperationMode.Production)(
        OpenTelemetryExportConfig.DefaultEndpoint
      )
    }
}

object OpenTelemetryExportConfig {
  val ProtocolOtlpHttp: String = "otlp-http"
  val DefaultEndpoint: String = "http://127.0.0.1:4318"

  def fromValues(
    enabled: Boolean,
    endpoint: Option[String],
    protocol: Option[String],
    tracesEnabled: Option[Boolean],
    metricsEnabled: Option[Boolean],
    logsEnabled: Option[Boolean],
    operationMode: OperationMode
  ): OpenTelemetryExportConfig = {
    val normalizedProtocol =
      protocol.map(_.trim.toLowerCase(java.util.Locale.ROOT).replace('_', '-'))
        .filter(_.nonEmpty)
        .getOrElse(ProtocolOtlpHttp)
    val config = OpenTelemetryExportConfig(
      enabled = enabled,
      endpoint = endpoint.map(_.trim).filter(_.nonEmpty),
      protocol = normalizedProtocol,
      tracesEnabled = tracesEnabled.getOrElse(true),
      metricsEnabled = metricsEnabled.getOrElse(true),
      logsEnabled = logsEnabled.getOrElse(false)
    )
    val error =
      if (enabled && normalizedProtocol != ProtocolOtlpHttp)
        Some(s"unsupported OpenTelemetry protocol: $normalizedProtocol")
      else if (enabled && operationMode == OperationMode.Production && config.normalizedEndpoint(operationMode).isEmpty)
        Some("textus.observability.otel.endpoint is required when OpenTelemetry export is enabled in production")
      else
        None
    config.copy(validationError = error)
  }
}

final case class OpenTelemetryExportResult(
  signal: String,
  status: String,
  statusCode: Option[Int] = None,
  message: Option[String] = None
)

object OpenTelemetryExporter {
  def fromGlobal: OpenTelemetryExporter =
    org.goldenport.cncf.context.GlobalRuntimeContext.current
      .map(global => OpenTelemetryExporter(global.config.openTelemetryExportConfig, global.config.operationMode))
      .getOrElse(OpenTelemetryExporter.disabled)

  val disabled: OpenTelemetryExporter =
    OpenTelemetryExporter(OpenTelemetryExportConfig(), OperationMode.Develop)
}

object OpenTelemetryExportQueue {
  private val MaxQueueSize = 256
  private val _queue = new LinkedBlockingQueue[() => Unit](MaxQueueSize)
  private val _active = new AtomicInteger(0)
  private val _worker = new Thread(
    new Runnable {
      def run(): Unit =
        while (true) {
          try {
            _run(_queue.take())
          } catch {
            case _: InterruptedException =>
              Thread.currentThread().interrupt()
              return
            case NonFatal(_) =>
              RuntimeDashboardMetrics.recordOpenTelemetryExport("traces", "failed")
          }
        }
    },
    "cncf-otel-exporter"
  )

  _worker.setDaemon(true)
  _worker.start()
  Runtime.getRuntime.addShutdownHook(new Thread(
    new Runnable {
      def run(): Unit =
        flush(Duration.ofSeconds(2))
    },
    "cncf-otel-exporter-shutdown"
  ))

  def submit(
    signal: String
  )(
    task: => OpenTelemetryExportResult
  ): OpenTelemetryExportResult =
    if (_queue.offer(() => { task; () })) {
      RuntimeDashboardMetrics.recordOpenTelemetryExport(signal, "queued")
      OpenTelemetryExportResult(signal, "queued")
    } else {
      RuntimeDashboardMetrics.recordOpenTelemetryExport(signal, "dropped")
      OpenTelemetryExportResult(signal, "dropped", message = Some("OpenTelemetry export queue is full"))
    }

  def flush(timeout: Duration): Unit = {
    val deadline = System.nanoTime() + timeout.toNanos
    while (System.nanoTime() < deadline && (!_queue.isEmpty || _active.get() > 0)) {
      val task = _queue.poll(25, TimeUnit.MILLISECONDS)
      if (task != null)
        _run(task)
    }
  }

  private def _run(task: () => Unit): Unit = {
    _active.incrementAndGet()
    try {
      task()
    } catch {
      case NonFatal(_) =>
        RuntimeDashboardMetrics.recordOpenTelemetryExport("traces", "failed")
    } finally {
      _active.decrementAndGet()
    }
  }
}

final case class OpenTelemetryExporter(
  config: OpenTelemetryExportConfig,
  operationMode: OperationMode,
  client: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(2))
    .build()
) {
  import OpenTelemetryExporterSupport.*

  def exportActionTrace(
    operation: String,
    calltree: Option[CallTree],
    jobId: Option[String],
    taskId: Option[String],
    sagaId: Option[String],
    outcome: String,
    startedAtNanos: Long,
    endedAtNanos: Long
  ): OpenTelemetryExportResult =
    if (!config.enabled) {
      _record("traces", "disabled")
      OpenTelemetryExportResult("traces", "disabled")
    } else if (!config.tracesEnabled) {
      _record("traces", "disabled")
      OpenTelemetryExportResult("traces", "disabled")
    } else if (config.validationError.nonEmpty) {
      _record("traces", "unavailable")
      OpenTelemetryExportResult("traces", "unavailable", message = config.validationError)
    } else {
      val payload = tracePayload(
        operation = operation,
        calltreeRecord = calltree.map(x => ObservabilityEngine.callTreeRecord(x, jobId)),
        jobId = jobId,
        taskId = taskId,
        sagaId = sagaId,
        outcome = outcome,
        startedAtNanos = startedAtNanos,
        endedAtNanos = endedAtNanos
      )
      OpenTelemetryExportQueue.submit("traces") {
        _post("traces", "/v1/traces", payload)
      }
    }

  def exportMetrics(
    snapshot: RuntimeMetricsSnapshot
  ): OpenTelemetryExportResult =
    if (!config.enabled) {
      _record("metrics", "disabled")
      OpenTelemetryExportResult("metrics", "disabled")
    } else if (!config.metricsEnabled) {
      _record("metrics", "disabled")
      OpenTelemetryExportResult("metrics", "disabled")
    } else if (config.validationError.nonEmpty) {
      _record("metrics", "unavailable")
      OpenTelemetryExportResult("metrics", "unavailable", message = config.validationError)
    } else {
      _post("metrics", "/v1/metrics", metricsPayload(snapshot))
    }

  private def _post(
    signal: String,
    path: String,
    payload: Json
  ): OpenTelemetryExportResult =
    config.normalizedEndpoint(operationMode) match {
      case Some(endpoint) =>
        try {
          val uri = URI.create(endpoint.stripSuffix("/") + path)
          val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.noSpaces))
            .build()
          val response = client.send(request, HttpResponse.BodyHandlers.ofString())
          val ok = response.statusCode() >= 200 && response.statusCode() < 300
          val status = if (ok) "exported" else "failed"
          _record(signal, status)
          OpenTelemetryExportResult(signal, status, Some(response.statusCode()), Option(response.body()).filter(_.nonEmpty))
        } catch {
          case NonFatal(e) =>
            _record(signal, "failed")
            OpenTelemetryExportResult(signal, "failed", message = Some(e.getMessage))
        }
      case None =>
        _record(signal, "unavailable")
        OpenTelemetryExportResult(signal, "unavailable", message = Some("OpenTelemetry endpoint is not configured"))
    }

  private def _record(signal: String, status: String): Unit =
    RuntimeDashboardMetrics.recordOpenTelemetryExport(signal, status)
}

object OpenTelemetryExporterSupport {
  def tracePayload(
    operation: String,
    calltreeRecord: Option[Record],
    jobId: Option[String],
    taskId: Option[String],
    sagaId: Option[String],
    outcome: String,
    startedAtNanos: Long,
    endedAtNanos: Long
  ): Json = {
    val traceId = _hex("trace:" + operation + ":" + startedAtNanos, 16)
    val nodes = calltreeRecord.toVector.flatMap(_records(_, "calltree"))
    val spans =
      if (nodes.nonEmpty) {
        val bounds = _bounds(nodes).getOrElse((startedAtNanos, endedAtNanos))
        val baseUnix = _now_unix_nanos() - math.max(1L, bounds._2 - bounds._1)
        nodes.zipWithIndex.flatMap { case (node, index) =>
          _span_json(
            traceId = traceId,
            node = node,
            index = index,
            parentSpanId = None,
            baseStartNanos = bounds._1,
            baseUnixNanos = baseUnix,
            operation = operation,
            jobId = jobId,
            taskId = taskId,
            sagaId = sagaId,
            outcome = outcome
          )
        }
      } else {
        Vector(_span_json(
          traceId = traceId,
          spanId = _hex("span:" + operation + ":root", 8),
          parentSpanId = None,
          name = operation,
          startTimeUnixNano = (_now_unix_nanos() - math.max(1L, endedAtNanos - startedAtNanos)).toString,
          endTimeUnixNano = _now_unix_nanos().toString,
          attributes = _common_attributes(operation, jobId, taskId, sagaId, outcome)
        ))
      }
    Json.obj(
      "resourceSpans" -> Json.arr(Json.obj(
        "resource" -> Json.obj("attributes" -> Json.arr(_attr("service.name", "goldenport-cncf"))),
        "scopeSpans" -> Json.arr(Json.obj(
          "scope" -> Json.obj("name" -> Json.fromString("org.goldenport.cncf")),
          "spans" -> Json.arr(spans*)
        ))
      ))
    )
  }

  def metricsPayload(
    snapshot: RuntimeMetricsSnapshot
  ): Json = {
    val points = snapshot.points.flatMap(_metric_json(_, snapshot.generatedAt))
    Json.obj(
      "resourceMetrics" -> Json.arr(Json.obj(
        "resource" -> Json.obj("attributes" -> Json.arr(_attr("service.name", "goldenport-cncf"))),
        "scopeMetrics" -> Json.arr(Json.obj(
          "scope" -> Json.obj("name" -> Json.fromString("org.goldenport.cncf")),
          "metrics" -> Json.arr(points*)
        ))
      ))
    )
  }

  private def _span_json(
    traceId: String,
    node: Record,
    index: Int,
    parentSpanId: Option[String],
    baseStartNanos: Long,
    baseUnixNanos: Long,
    operation: String,
    jobId: Option[String],
    taskId: Option[String],
    sagaId: Option[String],
    outcome: String
  ): Vector[Json] = {
    val spanId = _hex("span:" + operation + ":" + index + ":" + _string(node, "label").getOrElse("node"), 8)
    val children = _records(node, "flow")
    val attrs = _node_attributes(node) ++ _common_attributes(operation, jobId, taskId, sagaId, outcome)
    val current = _span_json(
      traceId,
      spanId,
      parentSpanId,
      _string(node, "display_label").orElse(_string(node, "label")).getOrElse(operation),
      (_node_start(node).getOrElse(baseStartNanos) - baseStartNanos + baseUnixNanos).toString,
      (_node_end(node).getOrElse(_node_start(node).getOrElse(baseStartNanos) + 1L) - baseStartNanos + baseUnixNanos).toString,
      attrs
    )
    current +: children.zipWithIndex.flatMap { case (child, childIndex) =>
      _span_json(
        traceId,
        child,
        index * 1000 + childIndex + 1,
        Some(spanId),
        baseStartNanos,
        baseUnixNanos,
        operation,
        jobId,
        taskId,
        sagaId,
        outcome
      )
    }
  }

  private def _span_json(
    traceId: String,
    spanId: String,
    parentSpanId: Option[String],
    name: String,
    startTimeUnixNano: String,
    endTimeUnixNano: String,
    attributes: Vector[(String, String)]
  ): Json = {
    val parent = parentSpanId.map(x => "parentSpanId" -> Json.fromString(x)).toVector
    val fields =
      Vector(
        "traceId" -> Json.fromString(traceId),
        "spanId" -> Json.fromString(spanId)
      ) ++ parent ++ Vector(
        "name" -> Json.fromString(name),
        "kind" -> Json.fromInt(1),
        "startTimeUnixNano" -> Json.fromString(startTimeUnixNano),
        "endTimeUnixNano" -> Json.fromString(endTimeUnixNano),
        "attributes" -> Json.arr(attributes.distinct.map { case (k, v) => _attr(k, v) }*)
      )
    Json.obj(fields*)
  }

  private def _common_attributes(
    operation: String,
    jobId: Option[String],
    taskId: Option[String],
    sagaId: Option[String],
    outcome: String
  ): Vector[(String, String)] =
    Vector(
      "cncf.operation" -> operation,
      "cncf.job.id" -> jobId.getOrElse("none"),
      "cncf.task.id" -> taskId.getOrElse("none"),
      "cncf.saga.id" -> sagaId.getOrElse("none"),
      "cncf.outcome" -> outcome
    )

  private def _node_attributes(node: Record): Vector[(String, String)] = {
    val direct = Vector(
      "cncf.calltree.kind" -> _string(node, "kind").getOrElse("step"),
      "cncf.calltree.label" -> _string(node, "label").getOrElse("")
    ) ++ Vector("component", "service", "operation", "source", "target", "entity", "collection", "datastore", "outcome", "highlights")
      .flatMap(k => _string(node, k).map(v => s"cncf.$k" -> v))
    val payload = Vector("request", "response", "result", "query", "web_parameters").flatMap { key =>
      _record(node, key).toVector.flatMap(summary =>
        Vector("kind", "value_type", "size_bytes", "field_count", "record_count", "fetched_count", "total_count", "payload_href", "externalization_status")
          .flatMap(k => _string(summary, k).map(v => s"cncf.payload.$key.$k" -> v))
      )
    }
    direct ++ payload
  }

  private def _metric_json(
    point: RuntimeMetricPoint,
    instant: Instant
  ): Vector[Json] = {
    val base = _metric_name(point)
    val timestamp = (instant.toEpochMilli * 1000000L).toString
    val attrs = point.labels.toVector.sortBy(_._1).map { case (k, v) => _attr(s"cncf.$k", v) }
    val count = Some(_sum_metric(base + ".count", point.count, attrs, timestamp))
    val errors = Option.when(point.errorCount > 0)(_sum_metric(base + ".error_count", point.errorCount, attrs, timestamp))
    val avg = point.durationAvgMillis.map(v => _gauge_metric(base + ".duration.avg_millis", v.toDouble, attrs, timestamp))
    Vector(count, errors, avg).flatten
  }

  private def _metric_name(point: RuntimeMetricPoint): String =
    "cncf." + (point.scope + "." + point.name)
      .replaceAll("[^A-Za-z0-9_.-]", "_")
      .replace('-', '_')

  private def _sum_metric(
    name: String,
    value: Long,
    attrs: Vector[Json],
    timestamp: String
  ): Json =
    Json.obj(
      "name" -> Json.fromString(name),
      "unit" -> Json.fromString("1"),
      "sum" -> Json.obj(
        "aggregationTemporality" -> Json.fromInt(2),
        "isMonotonic" -> Json.fromBoolean(true),
        "dataPoints" -> Json.arr(Json.obj(
          "attributes" -> Json.arr(attrs*),
          "timeUnixNano" -> Json.fromString(timestamp),
          "asInt" -> Json.fromString(value.toString)
        ))
      )
    )

  private def _gauge_metric(
    name: String,
    value: Double,
    attrs: Vector[Json],
    timestamp: String
  ): Json =
    Json.obj(
      "name" -> Json.fromString(name),
      "unit" -> Json.fromString("ms"),
      "gauge" -> Json.obj(
        "dataPoints" -> Json.arr(Json.obj(
          "attributes" -> Json.arr(attrs*),
          "timeUnixNano" -> Json.fromString(timestamp),
          "asDouble" -> Json.fromDoubleOrNull(value)
        ))
      )
    )

  private def _attr(key: String, value: String): Json =
    Json.obj("key" -> Json.fromString(key), "value" -> Json.obj("stringValue" -> Json.fromString(value)))

  private def _records(record: Record, key: String): Vector[Record] =
    record.asMap.get(key) match {
      case Some(xs: Seq[?]) => xs.toVector.collect { case r: Record => r }
      case Some(xs: Array[?]) => xs.toVector.collect { case r: Record => r }
      case _ => Vector.empty
    }

  private def _record(record: Record, key: String): Option[Record] =
    record.asMap.get(key).collect { case r: Record => r }

  private def _string(record: Record, key: String): Option[String] =
    record.getString(key).map(_.trim).filter(_.nonEmpty)

  private def _node_start(record: Record): Option[Long] =
    _string(record, "started_at_nanos").flatMap(_to_long)

  private def _node_end(record: Record): Option[Long] =
    _string(record, "ended_at_nanos").flatMap(_to_long)
      .orElse(_node_start(record).map(_ + 1L))

  private def _bounds(nodes: Vector[Record]): Option[(Long, Long)] = {
    val starts = nodes.flatMap(_node_start)
    val ends = nodes.flatMap(_node_end)
    for {
      start <- starts.minOption
      end <- ends.maxOption.orElse(Some(start + 1L))
    } yield start -> math.max(start + 1L, end)
  }

  private def _to_long(value: String): Option[Long] =
    scala.util.Try(value.toLong).toOption

  private def _now_unix_nanos(): Long =
    Instant.now().toEpochMilli * 1000000L

  private def _hex(seed: String, bytes: Int): String = {
    val digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    digest.take(bytes).map("%02x".format(_)).mkString
  }
}
