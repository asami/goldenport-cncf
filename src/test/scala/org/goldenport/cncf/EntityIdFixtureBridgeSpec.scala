package org.goldenport.cncf

import java.time.Instant
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the deterministic EntityId fixture bridge.
 *
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityIdFixtureBridgeSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "EntityIdFixtureBridge" should {
    "fix every fixture timestamp at the epoch" in {
      Given("fixed structural parts and nonempty fixture entropy")
      val major = "test"
      val minor = "fixture"
      val entropy = "epoch_fixture"

      When("the fixture bridge materializes the EntityId")
      val id = EntityIdFixtureBridge.fromParts(
        major,
        minor,
        _collection,
        entropy
      )

      Then("the bridge identity carries the epoch timestamp")
      id.timestamp shouldBe Some(Instant.EPOCH)
    }

    "reproduce an ID from equal structural parts and entropy" in {
      Given("equal structural parts and nonempty fixture entropy")
      val major = "test"
      val minor = "fixture"
      val entropy = "same_entropy"

      When("the fixture bridge materializes the same parts twice")
      val first = EntityIdFixtureBridge.fromParts(
        major,
        minor,
        _collection,
        entropy
      )
      val second = EntityIdFixtureBridge.fromParts(
        major,
        minor,
        _collection,
        entropy
      )

      Then("both bridge identities are equal")
      first shouldBe second
    }

    "differentiate equal structural parts by entropy" in {
      Given("equal structural parts and two distinct nonempty entropy values")
      val major = "test"
      val minor = "fixture"
      val firstentropy = "first_entropy"
      val secondentropy = "second_entropy"

      When("the fixture bridge materializes each entropy value")
      val first = EntityIdFixtureBridge.fromParts(
        major,
        minor,
        _collection,
        firstentropy
      )
      val second = EntityIdFixtureBridge.fromParts(
        major,
        minor,
        _collection,
        secondentropy
      )

      Then("the bridge identities are distinct")
      first should not be second
    }

    "preserve repeatability and the epoch timestamp for generated entropy" in {
      Given("valid nonempty alphabetic entropy values")
      val property = Prop.forAll(_entropy) { entropy =>
        val first = EntityIdFixtureBridge.fromParts(
          "test",
          "fixture",
          _collection,
          entropy
        )
        val second = EntityIdFixtureBridge.fromParts(
          "test",
          "fixture",
          _collection,
          entropy
        )

        first == second && first.timestamp == Some(Instant.EPOCH)
      }

      When("the fixture bridge materializes each generated entropy twice")
      val checked = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(40),
        property
      )

      Then("every generated bridge identity is repeatable and epoch-timestamped")
      checked.passed shouldBe true
    }
  }

  private val _entropy =
    Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)
  private val _collection = EntityCollectionId("test", "fixture", "entity")
}
