package org.goldenport.cncf.information

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.information.value.{InformationBindingStatus, InformationIdentityBinding}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 31, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedInformationRuntimeAdoptionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-61.1-generated-information-runtime-adoption, example:E1, rules:IC-03, phase:61.1, slice:IC-03A"
  )
  private val _e2 = afterWord(
    "in spec:phase-61.1-generated-information-runtime-adoption, example:E2, rules:IC-03, phase:61.1, slice:IC-03A"
  )

  "Generated Information runtime adoption" should {
    "E1 register and snapshot the generated Entity with lifecycle data" must _e1 {
      Given("an InformationSpace with a deterministic execution clock")
      val createdat = Instant.parse("2026-08-31T00:00:00Z")
      val updatedat = Instant.parse("2026-08-31T00:01:00Z")
      val clock = new _MutableTestClock(createdat, ZoneOffset.UTC)
      val context = ExecutionContext.create(clock)
      val space = new InformationSpace

      When("a minimal Information record is registered")
      val registered = _success(space.registerInformation(
        "paper",
        Vector(Record.data("title" -> "Generated runtime identity"))
      )(using context)).head

      Then("registration and snapshot retain the generated Entity identity, lifecycle timestamp, and managed initial revision")
      registered shouldBe a[org.goldenport.cncf.information.entity.Information]
      space.snapshot.information.head should be theSameInstanceAs registered
      registered.lifecycleAttributes.createdAt shouldBe createdat
      registered.lifecycleAttributes.updatedAt shouldBe createdat
      registered.revision.value shouldBe 1L

      When("the registered Information is mutated at a later deterministic instant")
      clock.advanceTo(updatedat)
      val updated = _success(space.updateInformation(
        registered.id,
        Record.data("title" -> "Generated runtime identity, updated")
      )(using context))

      Then("mutation retains generated creation provenance while advancing its lifecycle update and managed revision")
      updated.lifecycleAttributes.createdAt shouldBe registered.lifecycleAttributes.createdAt
      updated.lifecycleAttributes.createdBy shouldBe registered.lifecycleAttributes.createdBy
      updated.lifecycleAttributes.updatedAt shouldBe updatedat
      space.snapshot.information.head should be theSameInstanceAs updated
      updated.revision.value shouldBe 2L
    }

    "E2 decode canonical binding records through the generated value codec" must _e2 {
      Given("a binding payload using canonical generated field spellings and an explicit status")
      val canonical = Record.data(
        "rdfSubject" -> "https://example.org/information/canonical",
        "knowledgeNodeId" -> "generated-node",
        "status" -> InformationBindingStatus.selected
      )

      When("the generated value codec decodes the canonical payload")
      val decoded = _success(InformationIdentityBinding.createC(canonical))

      Then("the generated codec preserves canonical fields")
      decoded shouldBe a[org.goldenport.cncf.information.value.InformationIdentityBinding]
      decoded.rdfSubject.map(_.print) shouldBe Some("https://example.org/information/canonical")
      decoded.knowledgeNodeId.map(_.print) shouldBe Some("generated-node")
      decoded.status shouldBe InformationBindingStatus.selected
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }

  private final class _MutableTestClock(
    initial: Instant,
    zone: ZoneId
  ) extends Clock {
    private val _instant = new AtomicReference[Instant](initial)

    def advanceTo(value: Instant): Unit = {
      _instant.set(value)
    }

    override def getZone(): ZoneId = zone

    override def withZone(value: ZoneId): Clock =
      new _MutableTestClock(_instant.get(), value)

    override def instant(): Instant =
      _instant.get()
  }
}
