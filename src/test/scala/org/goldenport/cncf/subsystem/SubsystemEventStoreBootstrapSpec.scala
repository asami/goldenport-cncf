package example.workflowhost

import java.nio.file.Files
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, SubsystemEventStoreBootstrap}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Deliberately outside org.goldenport.cncf: only public host APIs are usable.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class SubsystemEventStoreBootstrapSpec extends AnyWordSpec with Matchers {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Host bootstrap rejected: $failure")
  }
  private def collection(name: String) =
    DataStore.CollectionId.EntityStore(EntityCollectionId("test", "host", name))
  private val policy = take(EventPersistencePolicy.createC("host-policy-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("retained"), EventPersistencePolicy.Mode.Persist))))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("facts"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(ComponentId("org.example.Workflow"),
        "workflow", collection("instances"), collection("continuations")))))
  private given ExecutionContext = ExecutionContext.create()
  private def scope = ScopeContext(ScopeKind.Subsystem, "host", None, summon[ExecutionContext].observability)
  private def configuration(path: String) = ResolvedConfiguration(Configuration(Map(
    "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
    "textus.datastore.path" -> ConfigurationValue.StringValue(path))), ConfigurationTrace.empty)

  "Public EventStore host bootstrap" should {
    "capture policy without leaking it to a subsequently constructed ordinary host" in {
      // Given a fixed persistence policy and exact storage grants at host construction.
      val file = Files.createTempDirectory("eventstore-host-policy-").resolve("host.sqlite")
      val host = take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, configuration(file.toString)))
      val ordinary = DefaultSubsystemFactory.defaultWithScope(scope)
      try {
        val event = EventRecord(EventId("test", "host", Some(Instant.EPOCH), Some("transient")),
          "unmatched", "unmatched", Map.empty, Map.empty, Instant.EPOCH, true,
          EventRecord.Status.Stored, EventLane.Transactional)
        // When ordinary sends use the two stores without caller-selected policies.
        val selected = take(host.subsystem.eventStore.append(Vector(event))).head
        val legacy = take(ordinary.eventStore.append(Vector(event))).head
        // Then only the configured host selects unmatched kinds as transient.
        selected.persistent shouldBe false
        take(host.subsystem.eventStore.load(event.id)) shouldBe None
        legacy.persistent shouldBe true
        take(ordinary.eventStore.load(event.id)) shouldBe Some(legacy)
      } finally {
        take(host.shutdownC())
        ordinary.shutdown()
      }
    }

    "initialize managed SQLite once and preserve the existing cursor across host reopen" in {
      // Given canonical managed SQLite and explicit first-install authority.
      val file = Files.createTempDirectory("eventstore-host-cursor-").resolve("host.sqlite")
      val config = configuration(file.toString)
      val first = take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, config))
      try {
        // When the trusted bootstrap initializes its cursor.
        val cursor = take(first.initializeCursorC())
        // Then duplicate initialization cannot reset retained state.
        cursor.revision shouldBe EventCursorRecordV1.initial.revision
        cursor.lastSequence shouldBe EventCursorRecordV1.initial.lastSequence
        first.initializeCursorC() shouldBe a[Consequence.Failure[?]]
      } finally take(first.shutdownC())
      val reopened = take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, config))
      try {
        // When a fresh host attempts first-install against the same database.
        val duplicate = reopened.initializeCursorC()
        // Then it observes the existing cursor and refuses replacement.
        duplicate shouldBe a[Consequence.Failure[?]]
      } finally take(reopened.shutdownC())
    }

    "reject missing host authority before constructing a Subsystem" in {
      // Given missing policy or declaration rather than a sender-controlled default.
      val config = configuration("unused.sqlite")
      // When host construction is requested with incomplete authority.
      val missingPolicy = SubsystemEventStoreBootstrap.createC(null, declaration, scope, config)
      val missingDeclaration = SubsystemEventStoreBootstrap.createC(policy, null, scope, config)
      // Then no fallback host is returned.
      missingPolicy shouldBe a[Consequence.Failure[?]]
      missingDeclaration shouldBe a[Consequence.Failure[?]]
    }
  }
}
