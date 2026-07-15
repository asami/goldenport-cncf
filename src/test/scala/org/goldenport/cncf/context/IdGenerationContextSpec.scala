package org.goldenport.cncf.context

import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.context.EntropyContext
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class IdGenerationContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _purpose =
    Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)

  "IdGenerationContext" should {
    "reproduce collection-local controlled sequences without consuming another collection" in {
      Given("two invocation-local ID contexts with the same selected clock and entropy seed")
      val clock = Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC)
      val articles = EntityCollectionId("sample", "catalog", "article")
      val books = EntityCollectionId("sample", "catalog", "book")

      When("generated purposes are exercised with different interleavings")
      val property = Prop.forAll(_purpose) { purpose =>
        val left = IdGenerationContext.deterministic(
          IdGenerationContext.DefaultNamespace,
          clock,
          s"id-seed-$purpose"
        )
        val right = IdGenerationContext.deterministic(
          IdGenerationContext.DefaultNamespace,
          clock,
          s"id-seed-$purpose"
        )
        val leftfirst = left.entityId(articles, purpose)
        left.entityId(books, purpose)
        val leftsecond = left.entityId(articles, purpose)
        val rightfirst = right.entityId(articles, purpose)
        val rightsecond = right.entityId(articles, purpose)

        leftfirst == rightfirst &&
          leftsecond == rightsecond &&
          leftfirst != leftsecond &&
          leftfirst.timestamp.contains(clock.instant())
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("the selected clock and per-collection deterministic sequences define the IDs")
      checked.passed shouldBe true
    }

    "keep opaque IDs in an independent purpose sequence" in {
      Given("two equivalent controlled ID contexts")
      val clock = Clock.fixed(Instant.parse("2026-07-15T11:00:00Z"), ZoneOffset.UTC)
      val left = IdGenerationContext.deterministic(IdGenerationContext.DefaultNamespace, clock, "opaque-seed")
      val right = IdGenerationContext.deterministic(IdGenerationContext.DefaultNamespace, clock, "opaque-seed")

      When("an unrelated Entity ID is generated between opaque IDs in one context")
      val leftfirst = left.opaqueId("association")
      left.entityId(EntityCollectionId("sample", "catalog", "article"), "create")
      val leftsecond = left.opaqueId("association")
      val rightfirst = right.opaqueId("association")
      val rightsecond = right.opaqueId("association")

      Then("opaque ID replay is stable and its values stay distinct")
      leftfirst shouldBe rightfirst
      leftsecond shouldBe rightsecond
      leftfirst should not be leftsecond
      leftfirst should fullyMatch regex "[0-9a-f]{32}"
    }

    "preserve collection namespace IDs where the EntityId must round-trip independently" in {
      Given("a runtime namespace different from the Entity collection namespace")
      val clock = Clock.fixed(Instant.parse("2026-07-15T11:30:00Z"), ZoneOffset.UTC)
      val collection = EntityCollectionId("cncf", "builtin", "blob")
      val context = IdGenerationContext.deterministic(IdGenerationContext.DefaultNamespace, clock, "collection-seed")

      When("the collection-namespace capability creates an Entity ID")
      val id = context.entityIdInCollectionNamespace(collection, "blob.register")

      Then("the opaque ID remains self-describing when parsed")
      id.major shouldBe collection.major
      id.minor shouldBe collection.minor
      id.collection shouldBe collection
      EntityId.parse(id.value).toOption.map(_.collection) shouldBe Some(collection)
    }
  }
}
