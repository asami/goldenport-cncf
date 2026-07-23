package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 24, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityConcurrencyTokenSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _e1_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E1, rules:R1,R2,R4, phase:49"
    )
  private val _e2_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E2, rules:R3, phase:49"
    )

  "Entity concurrency token" should {
    "E1 define a non-negative token with one overflow-safe advancement" must _e1_metadata {
      "when generated admitted values advance" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1,R2,R4; Example: E1; generated non-negative token values"
        )
        val property = Prop.forAll(
          Gen.chooseNum(0L, Long.MaxValue - 1L)
        ) { number =>
          val token = EntityConcurrencyTokenSupport._create(number)
          val next = token.flatMap(EntityConcurrencyTokenSupport._advance)
          token.toOption.exists(_.print == number.toString) &&
            next.toOption.exists(_.print == (number + 1L).toString)
        }

        When("the token constructor and advancement are interpreted")
        val checked =
          Test.check(
            Test.Parameters.default.withMinSuccessfulTests(100),
            property
          )

        Then("all admitted values remain exact and advance once")
        checked.passed shouldBe true
        EntityConcurrencyToken.INITIAL.print shouldBe "1"
        EntityConcurrencyTokenSupport._create(-1L) shouldBe
          a[Consequence.Failure[?]]
        classOf[EntityConcurrencyToken].getConstructors shouldBe empty
        classOf[EntityConcurrencyToken].getDeclaredConstructors
          .forall(constructor =>
            java.lang.reflect.Modifier.isPrivate(constructor.getModifiers)
          ) shouldBe true
        classOf[EntityConcurrencyToken].getMethods
          .map(_.getName) should not contain "value"
        classOf[EntityConcurrencyToken].getMethods
          .map(_.getName) should not contain "next"

        val overflow =
          EntityConcurrencyTokenSupport
            ._create(Long.MaxValue)
            .flatMap(EntityConcurrencyTokenSupport._advance)
        val conclusion = overflow match {
          case Consequence.Failure(conclusion) =>
            conclusion
          case _ =>
            fail("maximum token advancement must fail")
        }
        val cause = conclusion.observation.cause

        cause.kind shouldBe Some(Cause.Kind.Limit)
        cause.descriptor.facets should contain (
          Descriptor.Facet.Limit(Long.MaxValue - 1L)
        )
        cause.descriptor.facets should contain (
          Descriptor.Facet.Actual(Long.MaxValue)
        )
      }
    }

    "E1 carry a typed Entity beside its admitted token" must _e1_metadata {
      "when a framework snapshot is constructed" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R4; Example: E1; one typed Entity and its token"
        )
        val snapshot =
          EntitySnapshot("entity-value", EntityConcurrencyToken.INITIAL)

        When("the snapshot is inspected")
        val entity = snapshot.entity
        val token = snapshot.token

        Then("the domain value and framework token remain separate")
        entity shouldBe "entity-value"
        token shouldBe EntityConcurrencyToken.INITIAL
      }
    }

    "E1 reserve the canonical storage field during initialization" must _e1_metadata {
      "when caller-provided revision aliases are normalized for create" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1,R2; Example: E1; a create record containing caller revision aliases"
        )
        val source = Record.dataAuto(
          "name" -> "entity",
          "cncfRevision" -> 91L,
          "cncf_revision" -> 92L
        )

        When("framework concurrency metadata initializes the record")
        val initialized =
          EntityConcurrencyMetadata.initializeForCreate(source)

        Then("caller values are removed and canonical initial token one is stored")
        initialized.getString("name") shouldBe Some("entity")
        initialized.getAny("cncfRevision") shouldBe None
        initialized.getAny("cncf_revision") shouldBe Some(1L)
        EntityConcurrencyMetadata.token(initialized) shouldBe
          Consequence.success(EntityConcurrencyToken.INITIAL)
      }
    }

    "E2 interpret a physically absent revision as virtual token zero" must _e2_metadata {
      "when legacy and exact integral storage records are decoded" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R3; Example: E2; a legacy record and generated exact integral values"
        )
        val property = Prop.forAll(
          Gen.chooseNum(0L, Long.MaxValue)
        ) { number =>
          EntityConcurrencyMetadata
            .token(Record.dataAuto("cncf_revision" -> BigInt(number)))
            .toOption
            .exists(_.print == number.toString)
        }

        When("the metadata codec reads the records")
        val legacy =
          EntityConcurrencyMetadata.token(Record.dataAuto("name" -> "legacy"))
        val checked =
          Test.check(
            Test.Parameters.default.withMinSuccessfulTests(100),
            property
          )

        Then("absence maps to zero without persisting a replacement and integral values remain exact")
        legacy shouldBe Consequence.success(EntityConcurrencyToken.LEGACY)
        checked.passed shouldBe true
      }
    }

    "E2 reject malformed physical revision values" must _e2_metadata {
      "when negative fractional overflowing or duplicate values are decoded" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R3; Example: E2; inadmissible physical revision values"
        )
        val records = Vector(
          Record.dataAuto("cncf_revision" -> -1L),
          Record.dataAuto("cncf_revision" -> BigDecimal("1.5")),
          Record.dataAuto(
            "cncf_revision" -> (BigInt(Long.MaxValue) + 1)
          ),
          Record.dataAuto(
            "cncfRevision" -> 1L,
            "cncf_revision" -> 1L
          )
        )

        When("the metadata codec reads each record")
        val results =
          records.map(EntityConcurrencyMetadata.token)

        Then("every malformed value returns a structured failure")
        all(results) shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
