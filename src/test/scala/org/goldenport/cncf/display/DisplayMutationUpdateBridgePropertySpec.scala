package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.entity.EntityRecordSnapshot
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class DisplayMutationUpdateBridgePropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayMutationUpdateBridgeFixture.*
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private val _title = for {
    size <- Gen.choose(0, 24)
    chars <- Gen.listOfN(size, Gen.alphaChar)
  } yield "Title-" + chars.mkString
  private val _description = for {
    size <- Gen.choose(0, 32)
    chars <- Gen.listOfN(size, Gen.oneOf('a', 'b', 'Ω', ' ', '\t', '\n'))
  } yield chars.mkString
  private val _selected = Gen.oneOf(Vector(title), Vector(description), Vector(title, description))
  private def _verify(result: Test.Result): Unit = withClue(result.toString) {
    result.passed shouldBe true
    result.succeeded should be >= 100
    result.discarded shouldBe 0
  }

  "Canonical Display update properties in spec:display-mutation-bridge, phase:96" should {
    "selected patch preservation UB-P1" which {
      "change only selected business fields and advance revision exactly once iff changed" in {
        Given("total bounded title description selected-subset and changed/no-op generators without filters")
        val generator = for {
          originaltitle <- _title
          originaldescription <- _description
          newtitle <- _title
          newdescription <- _description
          selected <- _selected
          changetitle <- Gen.oneOf(true, false)
          changedescription <- Gen.oneOf(true, false)
        } yield (originaltitle, originaldescription, newtitle, newdescription, selected, changetitle, changedescription)
        val property = Prop.forAll(generator) {
          case (originaltitle, originaldescription, newtitle, newdescription, selected, changetitle, changedescription) =>
            Given("one generated real canonical Entity and a patch with unchanged unselected seed fields")
            val state = fixture(originaltitle, originaldescription)
            val bound = binding()
            val desiredtitle = if (changetitle) newtitle + "-changed" else originaltitle
            val desireddescription = if (changedescription) newdescription + "-changed" else originaldescription
            val values = selected.map(location => location -> (if (location == title) desiredtitle else desireddescription))
            val prepared = success(prepareC(state, bound, values))
            val expectedtitle = if (selected.contains(title)) desiredtitle else originaltitle
            val expecteddescription = if (selected.contains(description)) desireddescription else originaldescription
            val changed = expectedtitle != originaltitle || expecteddescription != originaldescription
            state.provider.resetObservation()

            When("the strict typed program executes through the real canonical provider")
            val program = success(registration(bound).programC(prepared, authorization(state.id)))
            val result = state.executeC(program)

            Then("only admitted selected fields differ and exactly one revision advances iff business state changed")
            result.map(snapshot => (snapshot.record.getString("item_title"), snapshot.record.getString("item_description"), snapshot.revision.value)) shouldBe Consequence.success((Some(expectedtitle), Some(expecteddescription), if (changed) 2L else 1L))
            state.provider.attempts shouldBe 1
            state.provider.mutations shouldBe (if (changed) 1 else 0)
            val raw = success(state.rawC)
            raw.getString("item_title") shouldBe Some(expectedtitle)
            raw.getString("item_description") shouldBe Some(expecteddescription)
            raw.getAny("revision") shouldBe Some(if (changed) 2L else 1L)
            state.collection.resolve(state.id).map(entity => (entity.title, entity.description, entity.revision.value)) shouldBe Consequence.success((expectedtitle, expecteddescription, if (changed) 2L else 1L))
            true
        }

        When("ScalaCheck evaluates the real-provider preservation property")
        val result = Test.check(_parameters, property)

        Then("at least one hundred generated cases pass with no discards")
        _verify(result)
      }
    }

    "stale-before-equality UB-P2" which {
      "never admit stale identical business input as a successful no-op" in {
        Given("total current/stale observations bounded current revisions and native business values")
        val generator = for {
          originaltitle <- _title
          originaldescription <- _description
          currentrevision <- Gen.choose(2, 8)
          current <- Gen.oneOf(true, false)
        } yield (originaltitle, originaldescription, currentrevision, current)
        val property = Prop.forAll(generator) { case (originaltitle, originaldescription, currentrevision, current) =>
          Given("a real Entity advanced to the generated revision only through canonical observed writes")
          val state = fixture(originaltitle, originaldescription)
          (2 to currentrevision).foreach { revision =>
            success(state.canonicalUpdateC(BridgePatch(Update.set(originaltitle + "-" + revision), Update.noop[String]),
              observed = revision.toLong - 1)).revision.value shouldBe revision.toLong
          }
          val before = success(state.rawC)
          val observed = if (current) currentrevision.toLong else currentrevision.toLong - 1
          val bound = binding()
          val prepared = success(prepareC(state, bound,
            Vector(title -> (originaltitle + "-" + currentrevision), description -> originaldescription), observed = observed))
          state.provider.resetObservation()

          When("identical business values execute with the generated current or stale observed revision")
          val program = success(registration(bound).programC(prepared, authorization(state.id)))
          val result = state.executeC(program)

          Then("only current observations succeed as no-op and neither branch changes persisted metadata or state")
          if (current) result.map(_.revision.value) shouldBe Consequence.success(currentrevision.toLong)
          else result shouldBe a[Consequence.Failure[EntityRecordSnapshot]]
          success(state.rawC).asMap shouldBe before.asMap
          state.provider.attempts shouldBe 1
          state.provider.mutations shouldBe 0
          true
        }

        When("ScalaCheck evaluates atomic stale comparison against business equality")
        val result = Test.check(_parameters, property)

        Then("at least one hundred generated cases pass with no discards")
        _verify(result)
      }
    }
  }
}
