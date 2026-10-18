package org.goldenport.cncf.knowledge

import org.goldenport.Consequence
import org.goldenport.cncf.information.value.{InformationBindingStatus, InformationIdentityBinding}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.value.ModelValueEncoder

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase115KnowledgeExternalConversionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Knowledge values at the generated Information boundary" should {
    "preserve scalar identity and structured ordered collections" which {
      "project all four typed values and reconstruct the generated store projection" in {
        Given("typed scalar identities and ordered duplicate identifiers and bindings with optional metadata")
        val identifier = ExternalKnowledgeIdentifier("catalog", "entry-115", Some("work"))
        val binding = KnowledgeEntityBinding("Paper", "paper-115", Some("v2"), Some("catalog"))
        val input = _binding(
          Some(RdfNodeName("urn:phase115:subject")),
          Vector(identifier, ExternalKnowledgeIdentifier("other", "entry-116"), identifier),
          Vector(binding, KnowledgeEntityBinding("Note", "note-116"), binding),
          Some(KnowledgeNodeId("knowledge:phase115")))

        _check_projection(input)
      }

      "retain present empty Vectors while omitting absent optional scalar identities" in {
        Given("a generated binding with empty collections and absent scalar identities")
        val input = _binding(None, Vector.empty, Vector.empty, None)

        _check_projection(input)
      }

      "obey the independent collection shape and generated roundtrip law for one hundred fixed-seed samples" in {
        Given("generated optional metadata and collections with deliberately repeated identifiers and bindings")
        val optionaltext = Gen.option(Gen.oneOf("first", "second", "雪", ""))
        val identifiers = for {
          count <- Gen.choose(0, 6)
          values <- Gen.listOfN(count, for {
            system <- Gen.oneOf("catalog", "archive")
            value <- Gen.oneOf("entry-a", "entry-b", "雪")
            kind <- optionaltext
          } yield ExternalKnowledgeIdentifier(system, value, kind))
        } yield (values ++ values).toVector
        val bindings = for {
          count <- Gen.choose(0, 6)
          values <- Gen.listOfN(count, for {
            name <- Gen.oneOf("Paper", "Note")
            id <- Gen.oneOf("a", "b", "雪")
            version <- optionaltext
            component <- optionaltext
          } yield KnowledgeEntityBinding(name, id, version, component))
        } yield (values ++ values).toVector
        val samples = for {
          subject <- Gen.option(Gen.oneOf("urn:subject:a", "urn:subject:雪"))
          ids <- identifiers
          entities <- bindings
          node <- Gen.option(Gen.oneOf("knowledge:a", "knowledge:雪"))
        } yield _binding(subject.map(RdfNodeName.apply), ids, entities, node.map(KnowledgeNodeId.apply))
        val property = Prop.forAll(samples) { input =>
          Given("one typed Information binding with independently expected external fields")
          _check_projection(input)
          true
        }

        When("ScalaCheck interprets the generated conversion law with a reproducible seed")
        val checked = Test.check(Test.Parameters.default
          .withMinSuccessfulTests(100)
          .withInitialSeed(Seed(11520261018L)), property)

        Then("at least one hundred successful cases satisfy shape, omission, order, duplicates and reconstruction")
        withClue(checked.toString) { checked.passed shouldBe true }
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }

    "reject objects without an external conversion contract" in {
      Given("an arbitrary object with no nominal scalar or Record capability")
      val input = new java.lang.Object

      When("the actual model encoder interprets the object")
      val result = ModelValueEncoder.encodeC(input)

      Then("the object remains a structured conversion failure")
      result shouldBe a[Consequence.Failure[?]]
    }
  }

  private def _binding(
    subject: Option[RdfNodeName],
    identifiers: Vector[ExternalKnowledgeIdentifier],
    bindings: Vector[KnowledgeEntityBinding],
    node: Option[KnowledgeNodeId]
  ): InformationIdentityBinding =
    InformationIdentityBinding(subject, identifiers, bindings, node, None, None, InformationBindingStatus.selected)

  private def _check_projection(input: InformationIdentityBinding): Unit = {
    val expectedidentifiers = input.externalIdentifiers.map { identifier =>
      Record.dataAuto((Vector[(String, Any)]("system" -> identifier.system, "value" -> identifier.value) ++
        identifier.kind.map("kind" -> _).toVector)*)
    }
    val expectedbindings = input.entityBindings.map { binding =>
      Record.dataAuto((Vector[(String, Any)]("entityName" -> binding.entityName, "entityId" -> binding.entityId) ++
        binding.entityVersion.map("entityVersion" -> _).toVector ++
        binding.component.map("component" -> _).toVector)*)
    }

    When("the model encoder and actual generated Record and DataStore writers project the typed binding")
    val subject = input.rdfSubject.map(value => _take(ModelValueEncoder.encodeC(value)))
    val node = input.knowledgeNodeId.map(value => _take(ModelValueEncoder.encodeC(value)))
    val identifiers = _take(ModelValueEncoder.encodeC(input.externalIdentifiers))
    val bindings = _take(ModelValueEncoder.encodeC(input.entityBindings))
    val record = input.toRecord()
    val stored = input.toDataStore()
    val restored = _take(InformationIdentityBinding.createC(stored))

    Then("nominal identities remain scalars and canonical Records retain optional omission, Vector order and duplicates")
    subject shouldBe input.rdfSubject.map(_.value)
    node shouldBe input.knowledgeNodeId.map(_.value)
    identifiers shouldBe a[Vector[?]]
    identifiers shouldBe expectedidentifiers
    bindings shouldBe a[Vector[?]]
    bindings shouldBe expectedbindings
    Vector(record, stored).foreach { projection =>
      projection.getAny("rdfSubject") shouldBe input.rdfSubject.map(_.value)
      projection.getAny("knowledgeNodeId") shouldBe input.knowledgeNodeId.map(_.value)
      projection.getAny("externalIdentifiers") shouldBe Some(expectedidentifiers)
      projection.getAny("entityBindings") shouldBe Some(expectedbindings)
      projection.getAny("externalIdentifiers").get shouldBe a[Vector[?]]
      projection.getAny("entityBindings").get shouldBe a[Vector[?]]
    }
    expectedidentifiers.zip(input.externalIdentifiers).foreach { case (expected, identifier) =>
      expected.fields.map(_.key) shouldBe
        (Vector("system", "value") ++ identifier.kind.map(_ => "kind").toVector)
    }
    expectedbindings.zip(input.entityBindings).foreach { case (expected, binding) =>
      expected.fields.map(_.key) shouldBe
        (Vector("entityName", "entityId") ++ binding.entityVersion.map(_ => "entityVersion").toVector ++
          binding.component.map(_ => "component").toVector)
    }
    record.getAny("status") shouldBe Some("selected")
    stored.getAny("status") shouldBe Some("selected")
    restored shouldBe input
  }

  private def _take[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(s"expected successful conversion, got $result"))
}
