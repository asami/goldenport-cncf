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
      val registrationcontext = ExecutionContext.create(Clock.fixed(createdat, ZoneOffset.UTC))
      val mutationcontext = ExecutionContext.create(Clock.fixed(updatedat, ZoneOffset.UTC))
      val space = new InformationSpace

      When("a minimal Information record is registered")
      val registered = _success(space.registerInformation(
        "paper",
        Vector(Record.data("title" -> "Generated runtime identity"))
      )(using registrationcontext)).head

      Then("registration and snapshot retain the generated Entity identity and lifecycle timestamp")
      registered shouldBe a[org.goldenport.cncf.information.entity.Information]
      space.snapshot.information.head should be theSameInstanceAs registered
      registered.lifecycleAttributes.createdAt shouldBe createdat
      registered.lifecycleAttributes.updatedAt shouldBe createdat
      registered.revision shouldBe org.simplemodeling.model.datatype.EntityRevision.INITIAL

      When("the registered Information is mutated at a later deterministic instant")
      val updated = _success(space.updateInformation(
        registered.id,
        Record.data("title" -> "Generated runtime identity, updated")
      )(using mutationcontext))

      Then("mutation retains generated creation provenance while advancing its lifecycle update")
      updated.lifecycleAttributes.createdAt shouldBe registered.lifecycleAttributes.createdAt
      updated.lifecycleAttributes.createdBy shouldBe registered.lifecycleAttributes.createdBy
      updated.lifecycleAttributes.updatedAt shouldBe updatedat
      space.snapshot.information.head should be theSameInstanceAs updated
      updated.revision shouldBe org.simplemodeling.model.datatype.EntityRevision.INITIAL
    }

    "E2 default an omitted binding status, preserve an explicit status, and reject alias conflicts" must _e2 {
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
