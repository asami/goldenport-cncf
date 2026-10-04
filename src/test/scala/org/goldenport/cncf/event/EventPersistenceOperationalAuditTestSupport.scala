package org.goldenport.cncf.event

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.job.JobProducerOperationalAuditTestSupport
import org.goldenport.cncf.log.{LogBackend, StructuredLogEvent}
import org.goldenport.cncf.observability.VisibilityPolicy
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object EventPersistenceOperationalAuditTestSupport extends Matchers {
  val instant: Instant = Instant.EPOCH
  val prefix: String = "event.persistence.audit.v1 bytes="
  val facets: Vector[String] = Vector("Job", "Task", "attempt", "Event", "Operation", "Workflow",
    "definition", "trace", "span", "CallTree", "payload", "recovery", "subject", "tenant",
    "credential", "provider", "path")
  val markers: Vector[String] = facets.map(facet => s"PRIVATE-$facet-秘密-/path/credential")

  final class RecordingBackend(onPersistence: StructuredLogEvent => Unit = _ => ()) extends LogBackend {
    private val _events = ArrayBuffer.empty[StructuredLogEvent]
    private val _lines = ArrayBuffer.empty[String]
    override def logStructured(event: StructuredLogEvent): Unit = synchronized {
      if (event.message.contains(prefix)) onPersistence(event)
      _events += event
      super.logStructured(event)
    }
    def writeLine(line: String): Unit = synchronized { _lines += line }
    def allEvents: Vector[StructuredLogEvent] = synchronized { _events.toVector }
    def events: Vector[StructuredLogEvent] = allEvents.filter(_.message.contains(prefix))
    def lines: Vector[String] = synchronized { _lines.toVector.filter(_.contains(prefix)) }
    def clear(): Unit = synchronized { _events.clear(); _lines.clear() }
  }

  final class ThrowingBackend extends LogBackend {
    val calls: AtomicInteger = new AtomicInteger(0)
    def writeLine(line: String): Unit = synchronized {
      if (line.contains(prefix)) {
        calls.incrementAndGet()
        throw new IllegalStateException(markers.mkString("|"))
      }
    }
  }

  def withBackend[A](backend: LogBackend, policy: VisibilityPolicy = VisibilityPolicy.AllowAll)(body: => A): A =
    JobProducerOperationalAuditTestSupport.withBackend(backend, policy)(body)

  def context(clock: JobProducerOperationalAuditTestSupport.CountingClock,
    ids: JobProducerOperationalAuditTestSupport.CountingIds): ExecutionContext =
    JobProducerOperationalAuditTestSupport.context(clock, ids)

  def eventId(entropy: String): EventId = EventId("native", "audit", Some(instant), Some(entropy))

  def acknowledgement(entropy: String = "original"): EventRecord =
    EventRecord(eventId(entropy), "original-event", "original-kind", Map.empty, Map.empty,
      instant, true, EventRecord.Status.Stored, EventLane.NonTransactional, 1L)

  def padding(constructor: String => String, bound: Int, character: String = "a"): String =
    character * (bound - constructor("").getBytes(UTF_8).length)

  def framed(line: String): Json = {
    val start = line.indexOf(prefix)
    if (start < 0) throw new IllegalArgumentException("missing Event persistence frame")
    val tail = line.substring(start + prefix.length)
    val separator = tail.indexOf(' ')
    if (separator <= 0 || !tail.take(separator).forall(_.isDigit))
      throw new IllegalArgumentException("invalid Event persistence length")
    val length = tail.take(separator).toInt
    val offset = line.take(start + prefix.length + separator + 1).getBytes(UTF_8).length
    val bytes = line.getBytes(UTF_8)
    if (length < 0 || length > 4096 || offset + length > bytes.length || bytes.length > 8192)
      throw new IllegalArgumentException("invalid Event persistence UTF8 bounds")
    val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
    val body = decoder.decode(ByteBuffer.wrap(bytes.slice(offset, offset + length))).toString
    if (body.getBytes(UTF_8).length != length)
      throw new IllegalArgumentException("split Event persistence UTF8")
    parser.parse(body).fold(error => throw error, identity)
  }

  def expected(ctx: ExecutionContext, record: EventRecord): Json =
    Json.fromFields(Vector("audit.kind" -> Json.fromString("event-persistence"),
      "operation" -> Json.fromString("persist"), "outcome" -> Json.fromString("succeeded"),
      "record-kind" -> Json.fromString("acknowledgement"),
      "trace-id" -> Json.fromString(ctx.observability.traceId.value)) ++
      ctx.observability.spanId.map(id => "span-id" -> Json.fromString(id.value)).toVector ++
      ctx.observability.correlationId.map(id => "correlation-id" -> Json.fromString(id.value)).toVector ++
      Vector("event-identity-available" -> Json.fromBoolean(true), "event-id" -> Json.fromString(record.id.value),
        "event-sequence" -> Json.fromLong(record.sequence), "event-lane" -> Json.fromString("non-transactional"),
        "occurred-at" -> Json.fromString(record.createdAt.toString)))

  def sameAtoms(record: Record, json: Json): Unit = {
    json.asObject.get.keys.toSet shouldBe record.asMap.keySet
    json.asObject.get.toVector.foreach { case (key, atom) =>
      atom.fold(fail("null atom"), boolean => record.getBoolean(key) shouldBe Some(boolean),
        number => record.getLong(key) shouldBe number.toLong, string => record.getString(key) shouldBe Some(string),
        _ => fail("array atom"), _ => fail("object atom"))
    }
  }

  enum StoreMode { case Normal, BeforeFailure, AfterFailure, ThrowBeforeReturn }

  final class NativeStore extends EventStore {
    private val _delegate = EventStore.inMemory
    private val _results = ArrayBuffer.empty[Consequence[Vector[EventRecord]]]
    val appendCalls: AtomicInteger = new AtomicInteger(0)
    val readCalls: AtomicInteger = new AtomicInteger(0)
    val failure: Consequence[Vector[EventRecord]] = Consequence.stateInvalid(markers.mkString("|"))
    val fault: IllegalStateException = new IllegalStateException(markers.mkString("|"))
    var mode: StoreMode = StoreMode.Normal
    def results: Vector[Consequence[Vector[EventRecord]]] = synchronized { _results.toVector }
    def append(records: Seq[EventRecord]): Consequence[Vector[EventRecord]] = synchronized {
      appendCalls.incrementAndGet()
      val result = mode match {
        case StoreMode.BeforeFailure => failure
        case StoreMode.ThrowBeforeReturn => throw fault
        case StoreMode.AfterFailure =>
          val _ = _delegate.append(records)
          failure
        case StoreMode.Normal => _delegate.append(records)
      }
      _results += result
      result
    }
    def load(id: EventId): Consequence[Option[EventRecord]] = { readCalls.incrementAndGet(); _delegate.load(id) }
    def query(q: EventStore.Query): Consequence[Vector[EventRecord]] = { readCalls.incrementAndGet(); _delegate.query(q) }
    def replay(q: EventStore.Query): Consequence[Vector[EventRecord]] = { readCalls.incrementAndGet(); _delegate.replay(q) }
    def stored: Vector[EventRecord] = _delegate.query(EventStore.Query()).toOption.get
  }

  final class NativeGraph {
    val clock = new JobProducerOperationalAuditTestSupport.CountingClock
    val ids = new JobProducerOperationalAuditTestSupport.CountingIds
    val executionContext: ExecutionContext = context(clock, ids)
    val store = new NativeStore
    val bus: EventBus = EventBus.default(EventEngine.noop(DataStore.noop(), eventstore = store,
      recordfactory = EventRecordFactory.from(executionContext)))
    val received = ArrayBuffer.empty[(String, DomainEvent, Option[ExecutionContext])]
    val bodyCalls: AtomicInteger = new AtomicInteger(0)
    def observations: (Int, Int, Int, Int, Int) =
      (clock.calls.get(), ids.calls.get(), store.appendCalls.get(), store.readCalls.get(), bodyCalls.get())
    def handler(label: String, response: DomainEvent => Consequence[Unit] = _ => Consequence.unit): EventDispatchHandler =
      new EventDispatchHandler {
        def dispatch(event: DomainEvent): Consequence[Unit] = {
          bodyCalls.incrementAndGet()
          received += ((label, event, None))
          response(event)
        }
        override def dispatchAuthorized(event: DomainEvent)(using current: ExecutionContext): Consequence[Unit] = {
          bodyCalls.incrementAndGet()
          received += ((label, event, Some(current)))
          response(event)
        }
      }
    def publish(event: DomainEvent): Consequence[EventPublishResult] =
      bus.publishRuntime(event, EventPublishOption(persistent = true))(using executionContext)
  }
}
