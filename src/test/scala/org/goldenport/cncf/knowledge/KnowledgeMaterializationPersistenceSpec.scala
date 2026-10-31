package org.goldenport.cncf.knowledge

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.PostgresqlAcceptance
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.AcknowledgementMode
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Required real PostgreSQL, native Entity persistence and independently reconstructed UoWs.
 * @since   Oct. 29, 2026
 * @version Oct. 29, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class KnowledgeMaterializationPersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with PostgresqlAcceptance {
  import KnowledgeMaterializationFixture.*

  "Durable canonical Knowledge materialization" should {
    "retain graph and owning references through independent PostgreSQL reconstruction" in {
      Given("required PostgreSQL with durable settings, a registered Detached Entity and one retained result identity")
      with_postgresql { database =>
        val token = database.token("knowledge_restart")
        val writerstore = database.open()
        val writer = new Session(token, writerstore)
        val value = materialization(writer.identity("selected"), "Saved original 日本" * 120)
        When("the actual native create commits and the writer pool closes before a new runtime reads")
        val created = success(writer.evaluate(writer.repository.create(value)(using writer.executionContext)))
        success(writerstore.closeC())
        val reader = new Session(token, database.open())
        val restored = success(reader.evaluate(reader.repository.get(value.id)(using reader.executionContext)))
        Then("all graph fields and source/target/context/decision attribution survive outside memory")
        restored shouldBe created
        restored.entity shouldBe value
        restored.revision.value shouldBe 1L
        success(reader.evaluate(reader.repository.findBySource(value.source)(using reader.executionContext))) shouldBe Vector(value)
        success(reader.evaluate(reader.repository.findByNode(value.snapshot.nodes.head.id)(using reader.executionContext))) shouldBe Vector(value)
      }
    }
    "preserve canonical roundtrip and exact versioned source selection over a nontrivial value space" in {
      Given("independent native writer and reader pools and bounded varying graphs, Unicode labels and source revisions")
      with_postgresql { database =>
        val token = database.token("knowledge_property")
        val writer = new Session(token, database.open())
        val reader = new Session(token, database.open())
        var sampleindex = 0
        val property = Prop.forAll(Gen.choose(2, 6), Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(50).mkString)) { (count, label) =>
          sampleindex += 1
          val value = materialization(writer.identity(s"sample_$sampleindex"), label + " Ω日本", count)
            .copy(source = source.copy(revision = sampleindex.toString))
          When("one strict native create is independently rehydrated and selected by its complete source reference")
          val created = success(writer.evaluate(writer.repository.create(value)(using writer.executionContext)))
          val loaded = success(reader.evaluate(reader.repository.get(value.id)(using reader.executionContext)))
          Then("the canonical graph and every owning version survive and source revisions do not alias")
          loaded shouldBe created
          loaded.entity shouldBe value
          success(reader.evaluate(reader.repository.findBySource(value.source)(using reader.executionContext))) shouldBe Vector(value)
          success(reader.evaluate(reader.repository.findBySource(value.source.copy(owner = "another.owner"))(using reader.executionContext))) shouldBe empty
          true
        }
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
        withClue(checked.toString) {
          checked.passed shouldBe true
          checked.succeeded should be >= 100
        }
      }
    }
    "reconcile a truly uncertain committed result through reads of the original identity only" in {
      Given("one native committed result and a provider capable of losing acknowledgement after actual commit")
      with_postgresql { database =>
        val token = database.token("knowledge_uncertain")
        val provider = database.openAcknowledgement()
        val writer = new Session(token, provider)
        val prior = materialization(writer.identity("prior"))
        success(writer.evaluate(writer.repository.create(prior)(using writer.executionContext)))
        val value = materialization(writer.identity("retained_before_call"))
        provider.arm(AcknowledgementMode.Indeterminate)
        When("native commit succeeds but returns its real indeterminate conclusion")
        val uncertain = writer.evaluate(writer.repository.create(value)(using writer.executionContext))
        Then("the repository preserves uncertainty without a success claim or a second write")
        uncertain shouldBe a[Consequence.Failure[?]]
        provider.commitAttempts shouldBe 1
        provider.rootAttempts shouldBe 1
        When("a fresh authorized runtime twice reads the same previously retained identity")
        val reader = new Session(token, database.open())
        val first = success(reader.evaluate(reader.repository.get(value.id)(using reader.executionContext)))
        val second = success(reader.evaluate(reader.repository.get(value.id)(using reader.executionContext)))
        Then("both reads reconcile the actual canonical result and leave write counts and unrelated result unchanged")
        first shouldBe second
        first.entity shouldBe value
        provider.commitAttempts shouldBe 1
        provider.rootAttempts shouldBe 1
        success(reader.evaluate(reader.repository.get(prior.id)(using reader.executionContext))).entity shouldBe prior
      }
    }
    "retain duplicate, rejected-write, missing and denied outcomes with no overwrite or fallback" in {
      Given("one real persisted result, independent reader and an explicit native commit rejection")
      with_postgresql { database =>
        val token = database.token("knowledge_failure")
        val provider = database.openAcknowledgement()
        val writer = new Session(token, provider)
        val value = materialization(writer.identity("selected"))
        val created = success(writer.evaluate(writer.repository.create(value)(using writer.executionContext)))
        val reader = new Session(token, database.open())
        When("the exact native identity is submitted again with a changed semantic context")
        val duplicate = writer.evaluate(writer.repository.create(value.copy(context = context.copy(revision = "changed")))(using writer.executionContext))
        Then("the duplicate fails and an independent native read retains the original complete target")
        duplicate shouldBe a[Consequence.Failure[?]]
        success(reader.evaluate(reader.repository.get(value.id)(using reader.executionContext))) shouldBe created
        provider.arm(AcknowledgementMode.Reject)
        val rejected = materialization(writer.identity("rejected"))
        When("the native provider rejects publication of another result")
        val failed = writer.evaluate(writer.repository.create(rejected)(using writer.executionContext))
        Then("the original remains and the rejected identity has no fabricated result")
        failed shouldBe a[Consequence.Failure[?]]
        reader.evaluate(reader.repository.get(rejected.id)(using reader.executionContext)) shouldBe a[Consequence.Failure[?]]
        success(reader.evaluate(reader.repository.get(value.id)(using reader.executionContext))) shouldBe created
        val denied = new Session(token, database.open(), principal = "foreign")
        When("a different authenticated subject attempts owning result and source reads")
        val foreignread = denied.evaluate(denied.repository.get(value.id)(using denied.executionContext))
        Then("native Entity permissions refuse the private row and the search does not leak it")
        foreignread shouldBe a[Consequence.Failure[?]]
        success(denied.evaluate(denied.repository.findBySource(source)(using denied.executionContext))) shouldBe empty
      }
    }
  }
}
