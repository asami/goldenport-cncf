package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import io.circe.{Json, parser}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, IdGenerationContext, TraceId}
import org.goldenport.cncf.log.{LogBackend, StructuredLogEvent}
import org.goldenport.cncf.observability.VisibilityPolicy

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
/** Fixture-only access to original native records; no production observer seam. */
private[cncf] object JobProducerOperationalAuditTestSupport {
  val instant: Instant = Instant.EPOCH
  val secret: String = "PRIVATE-credential-provider-/path/request-秘密"

  final class RecordingBackend(onProducer: StructuredLogEvent => Unit = _ => ()) extends LogBackend {
    private val _events = ArrayBuffer.empty[StructuredLogEvent]
    private val _lines = ArrayBuffer.empty[String]
    override def logStructured(event: StructuredLogEvent): Unit = synchronized {
      if (event.message.contains("job.producer.audit.v1 bytes=")) onProducer(event)
      _events += event
      super.logStructured(event)
    }
    def writeLine(line: String): Unit = synchronized { _lines += line }
    def events: Vector[StructuredLogEvent] = synchronized {
      _events.toVector.filter(_.message.contains("job.producer.audit.v1 bytes="))
    }
    def lines: Vector[String] = synchronized {
      _lines.toVector.filter(_.contains("job.producer.audit.v1 bytes="))
    }
    def clear(): Unit = synchronized { _events.clear(); _lines.clear() }
  }

  final class ThrowingBackend extends LogBackend {
    val calls: AtomicInteger = new AtomicInteger(0)
    def writeLine(line: String): Unit = {
      if (line.contains("job.producer.audit.v1 bytes=")) {
        calls.incrementAndGet()
        throw new IllegalStateException(secret)
      }
    }
  }

  def withBackend[A](backend: LogBackend, policy: VisibilityPolicy = VisibilityPolicy.AllowAll)(body: => A): A =
    JobExperienceOperationalAuditFixtures.withBackend(backend, policy)(body)

  def framed(line: String): Json = {
    val marker = "job.producer.audit.v1 bytes="
    val start = line.indexOf(marker)
    if (start < 0) throw new IllegalArgumentException("missing producer frame")
    val tail = line.substring(start + marker.length)
    val separator = tail.indexOf(' ')
    val length = tail.take(separator).toInt
    val offset = line.take(start + marker.length + separator + 1).getBytes(UTF_8).length
    val bytes = line.getBytes(UTF_8)
    if (length < 0 || length > 4096 || offset + length > bytes.length)
      throw new IllegalArgumentException("invalid producer UTF8 frame length")
    val body = new String(bytes.slice(offset, offset + length), UTF_8)
    if (body.getBytes(UTF_8).length != length)
      throw new IllegalArgumentException("producer frame splits UTF8")
    parser.parse(body).fold(error => throw error, identity)
  }

  def nativeRecord(engine: JobEngine, jobId: JobId): JobRecord = engine match {
    case native: InMemoryJobEngine => native._get_record(jobId).getOrElse(
      throw new IllegalStateException("native submitted Job missing"))
    case _ => throw new IllegalArgumentException("fixture requires real InMemoryJobEngine")
  }

  final class CountingClock extends Clock {
    val calls: AtomicInteger = new AtomicInteger(0)
    var failOnCall: Option[Int] = None
    val fault: IllegalStateException = new IllegalStateException("native-clock-domain-fault")
    def getZone: java.time.ZoneId = ZoneOffset.UTC
    override def withZone(zone: java.time.ZoneId): Clock = Clock.fixed(JobProducerOperationalAuditTestSupport.instant, zone)
    def instant(): Instant = {
      val count = calls.incrementAndGet()
      if (failOnCall.contains(count)) throw fault
      JobProducerOperationalAuditTestSupport.instant
    }
  }

  final class CountingIds extends IdGenerationContext {
    private val _delegate = IdGenerationContext.deterministic(IdGenerationContext.DEFAULT_NAMESPACE,
      Clock.fixed(instant, ZoneOffset.UTC), "native-producer-audit")
    val calls: AtomicInteger = new AtomicInteger(0)
    def namespace: IdGenerationContext.IdNamespace = _delegate.namespace
    def entityId(collection: org.simplemodeling.model.datatype.EntityCollectionId, purpose: String): org.simplemodeling.model.datatype.EntityId = {
      calls.incrementAndGet()
      _delegate.entityId(collection, purpose)
    }
    def opaqueId(purpose: String): String = { calls.incrementAndGet(); _delegate.opaqueId(purpose) }
  }

  def context(clock: CountingClock, ids: CountingIds): ExecutionContext = {
    val base = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
    val observed = ExecutionContext.withObservabilityContext(base, base.observability.copy(
      traceId = TraceId("native", "producer"), correlationId = Some(CorrelationId("native", "producer"))))
    ExecutionContext.withSecurityContext(observed, JobExperienceOperationalAuditFixtures.context().security)
  }
}
