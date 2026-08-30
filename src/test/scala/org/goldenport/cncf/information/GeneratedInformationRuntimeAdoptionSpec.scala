package org.goldenport.cncf.information

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
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
  "Generated Information runtime adoption" should {
    "E1 register and snapshot the generated Entity with lifecycle data" in {
      Given("an InformationSpace with a deterministic execution clock")
      val instant = Instant.parse("2026-08-31T00:00:00Z")
      given ExecutionContext = ExecutionContext.create(Clock.fixed(instant, ZoneOffset.UTC))
      val space = new InformationSpace

      When("a minimal Information record is registered")
      val registered = _success(space.registerInformation(
        "paper",
        Vector(Record.data("title" -> "Generated runtime identity"))
      )).head

      Then("registration and snapshot retain the generated Entity identity and lifecycle timestamp")
      registered shouldBe a[org.goldenport.cncf.information.entity.Information]
      space.snapshot.information.head should be theSameInstanceAs registered
      registered.lifecycleAttributes.updatedAt shouldBe instant
      registered.revision shouldBe org.simplemodeling.model.datatype.EntityRevision.INITIAL
    }

    "E2 default an omitted binding status, preserve an explicit status, and reject alias conflicts" in {
      Given("a binding payload using the admitted snake-case aliases")
      val aliases = Record.data(
        "rdf_subject" -> "https://example.org/information/legacy",
        "knowledge_node_id" -> "legacy-node"
      )

      When("the compatibility adapter decodes the payload")
      val decoded = _success(InformationIdentityBinding.createC(aliases))
      val explicit = _success(InformationIdentityBinding.createC(Record.data(
        "status" -> InformationBindingStatus.selected
      )))
      val conflicting = InformationIdentityBinding.createC(Record.data(
        "rdfSubject" -> "https://example.org/information/canonical",
        "rdf_subject" -> "https://example.org/information/conflict"
      ))
      val unnamed = _success(InformationIdentityBinding.createC(Record.data(
        "rdf-subject" -> "https://example.org/information/not-an-alias"
      )))

      Then("the aliases project to generated fields and differing duplicates fail deterministically")
      decoded shouldBe a[org.goldenport.cncf.information.value.InformationIdentityBinding]
      decoded.rdfSubject.map(_.print) shouldBe Some("https://example.org/information/legacy")
      decoded.knowledgeNodeId.map(_.print) shouldBe Some("legacy-node")
      decoded.status shouldBe InformationBindingStatus.candidate
      explicit.status shouldBe InformationBindingStatus.selected
      conflicting shouldBe a[Consequence.Failure[_]]
      unnamed.rdfSubject shouldBe None
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
