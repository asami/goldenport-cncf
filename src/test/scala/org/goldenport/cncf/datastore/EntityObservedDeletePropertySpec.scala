package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class EntityObservedDeletePropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityObservedDeleteFixture.*

  "Observed deletion provider laws" should {
    "preserve total bounded revision and scope semantics" which {
      "OD-P1 retain matched revision for physical and logical deletion across generated current states" in {
        Given("total bounded current revisions, business text, root policies and distinct physical deletion effects")
        val property = Prop.forAll(_samples) { sample =>
          val store = new MemoryStore()
          val ids = collections("p1")
          given ExecutionContext = context(store)
          val root = rootRecord(sample.actual, sample.title)
          val setup = seed(store, ids, root).flatMap(_ => _seed_effects(store, ids, sample.effects))
          val effects = _effects(ids, sample.effects)
          val before = snapshot(store, ids)
          val sidebefore = effects.map(effect => store.load(effect.collection, effect.entryId))
          val policy = if (sample.logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
          val request = plan(ids, revision(sample.actual), policy, false).copy(
            sideEffects = if (sample.logical) Vector.empty else effects
          )
          val result = setup.flatMap(_ => summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(request))
          val after = snapshot(store, ids)
          val sideafter = effects.map(effect => store.load(effect.collection, effect.entryId))
          val rootsatisfied = if (sample.logical) {
            after.root.toOption.flatten.exists(row =>
              EntityVersionedMutationSupport.revision(row, revisionField).toOption.contains(revision(sample.actual + 1L)) &&
              Vector("title", "business", "overflow_ref").forall(key => row.getAny(key) == root.getAny(key)) &&
              logicalChanges.fields.forall(field => row.getAny(field.key) == logicalChanges.getAny(field.key))
            ) && sideafter == sidebefore
          } else after.root == Consequence.success(None) && sideafter.forall(_ == Consequence.success(None))
          setup.isSuccess && result == Consequence.success(EntityCompareAndDeleteResult.Deleted(revision(sample.actual))) &&
            rootsatisfied && after.side == before.side && after.unrelated == before.unrelated
        }

        When("the current-match law executes through the real provider for at least one hundred cases")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("every generated policy preserves the exact stored and returned revision relationship without discard")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }

      "OD-P2 preserve complete root and effect records for every generated stale observation" in {
        Given("total generated actual revisions strictly above their observed revision with both root policies")
        val property = Prop.forAll(_samples, Gen.chooseNum(1L, 100L)) { (sample, distance) =>
          val store = new MemoryStore()
          val ids = collections("p2")
          given ExecutionContext = context(store)
          val actual = sample.actual + distance
          val setup = seed(store, ids, rootRecord(actual, sample.title)).flatMap(_ => _seed_effects(store, ids, sample.effects))
          val effects = _effects(ids, sample.effects)
          val before = snapshot(store, ids)
          val sidebefore = effects.map(effect => store.load(effect.collection, effect.entryId))
          val policy = if (sample.logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
          val request = plan(ids, revision(sample.actual), policy, false).copy(
            sideEffects = if (sample.logical) Vector.empty else effects
          )
          val result = setup.flatMap(_ => summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(request))
          val after = snapshot(store, ids)
          val sideafter = effects.map(effect => store.load(effect.collection, effect.entryId))
          setup.isSuccess && result == Consequence.success(EntityCompareAndDeleteResult.Stale(revision(sample.actual), revision(actual))) &&
            after == before && sideafter == sidebefore && store.checkpointCount == 0
        }

        When("the stale law executes at least one hundred real provider comparisons")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("stale observations preserve every complete record and reach no publication stage with zero discard")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }

      "OD-P3 delete exactly generated distinct selected targets and retain unselected roots and effects" in {
        Given("bounded distinct same-collection and side-collection targets with a total generated selection")
        val generator = for {
          count <- Gen.chooseNum(1, 16)
          rootcollections <- Gen.listOfN(count, Gen.oneOf(false, true))
          selections <- Gen.listOfN(count, Gen.oneOf(false, true))
          text <- _text
        } yield (rootcollections.toVector, selections.toVector, text)
        val property = Prop.forAll(generator) { case (rootcollections, selections, text) =>
          val store = new MemoryStore()
          val ids = collections("p3")
          given ExecutionContext = context(store)
          val targets = rootcollections.zipWithIndex.map { case (rootcollection, index) =>
            EntityVersionedSideEffect.Delete(if (rootcollection) ids.root else ids.side, DataStore.StringEntryId(s"target-$index"))
          }
          val setup = targets.foldLeft(seed(store, ids)) { (result, target) =>
            result.flatMap(_ => store.create(target.collection, target.entryId,
              Record.dataAuto("id" -> target.entryId.print, "title" -> text, revisionField -> 4L)))
          }
          val before = snapshot(store, ids)
          val targetbefore = targets.map(target => store.load(target.collection, target.entryId))
          val selected = targets.zip(selections).collect { case (target, true) => target }
          val request = plan(ids, includeSide = false).copy(sideEffects = selected)
          val result = setup.flatMap(_ => summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(request))
          val after = snapshot(store, ids)
          val targetafter = targets.map(target => store.load(target.collection, target.entryId))
          val exactscope = targetafter.zip(targetbefore).zip(selections).forall {
            case ((after, before), selected) => if (selected) after == Consequence.success(None) else after == before
          }
          setup.isSuccess && result == Consequence.success(EntityCompareAndDeleteResult.Deleted(EntityRevision.INITIAL)) &&
            after.root == Consequence.success(None) && after.side == before.side && after.unrelated == before.unrelated && exactscope
        }

        When("the exact target-scope law executes at least one hundred real atomic publications")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("only the guarded root and selected distinct effects disappear and no generated case is discarded")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
      }
    }
  }

  private final case class Sample(actual: Long, logical: Boolean, effects: Int, title: String)

  private val _text: Gen[String] = Gen.listOfN(12, Gen.chooseNum(32, 0xd7ff).map(_.toChar)).map(_.mkString)
  private val _samples: Gen[Sample] = for {
    actual <- Gen.chooseNum(1L, 1000000L)
    logical <- Gen.oneOf(false, true)
    effects <- Gen.chooseNum(0, 8)
    title <- _text
  } yield Sample(actual, logical, effects, title)

  private def _effects(ids: Collections, count: Int): Vector[EntityVersionedSideEffect.Delete] =
    Vector.tabulate(count)(index => EntityVersionedSideEffect.Delete(ids.side, DataStore.StringEntryId(s"effect-$index")))

  private def _seed_effects(store: DataStore, ids: Collections, count: Int)(using ctx: ExecutionContext): Consequence[Unit] =
    _effects(ids, count).foldLeft(Consequence.unit) { (result, effect) =>
      result.flatMap(_ => store.create(effect.collection, effect.entryId,
        Record.dataAuto("id" -> effect.entryId.print, "body" -> "preserved-effect")))
    }
}
