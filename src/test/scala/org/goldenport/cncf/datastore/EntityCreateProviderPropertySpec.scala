package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.entity.{ContentBodyStoragePolicy, EntityCreateFailureSafetyFixture, EntityRevisionRepresentation}
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.{reason, success}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 14, 2026
 * @version Oct. 16, 2026
 */
final class EntityCreateProviderPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityCreateFailureSafetyFixture.*

  private implicit val _sample_shrinker: Shrink[Sample] = Shrink { sample =>
    val revisions = Vector(1L, sample.oldrevision / 2L).distinct
      .filter(value => value >= 1L && value < sample.oldrevision)
      .map(value => sample.copy(oldrevision = value))
    val lengths = Vector(4200, 4200 + (sample.content.length - 4200) / 2).distinct
      .filter(value => value >= 4200 && value < sample.content.length)
      .map(value => sample.copy(content = sample.content.take(value)))
    (revisions ++ lengths).distinct.toStream
  }

  "CA12 total real-provider create preservation laws" should {
    "retain every duplicate input and complete selected/unrelated map" which {
      Vector("memory", "searchable", "sqlite").foreach { backend =>
        s"CFS-P1 preserve one hundred varied $backend duplicate attempts without discarded inputs" in {
          Given("a total generator of both managed representations, four body modes, old revisions and multibyte text")
          val property = Prop.forAll(_samples) { sample =>
            _with_provider(backend) { (provider, reader) =>
              Given(s"fresh actual $backend state with complete old root and overflow records")
              val setup = fixture("create_prop_duplicate", representation = sample.representation, provider = provider, oldRevision = sample.oldrevision)
              setup.seed()
              val before = setup.snapshot(setup.base.id, reader())
              val candidate = setup.input(sample.mode, Some(setup.base.id), sample.content)
                .copy(record = setup.input(sample.mode, Some(setup.base.id), sample.content).record.upsertSingle("title", s"Candidate-${sample.oldrevision}"))
              When("canonical strict create attempts the existing identity")
              val result = setup.createC(candidate)
              val after = setup.snapshot(setup.base.id, reader())
              Then("native duplicate preserves every full stored map")
              Prop.all("native duplicate" |: Prop(taxonomy(result) == duplicateTaxonomy(setup.base.id)),
                "complete storage retention" |: Prop(after == before))
            }
          }
          When("ScalaCheck executes all deferred real-provider attempts")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("the desired duplicate law succeeds at least one hundred times with zero discard")
          withClue(checked.toString) {
            checked.passed shouldBe true
            checked.succeeded should be >= 100
            checked.discarded shouldBe 0
          }
        }
      }
    }

    "retain complete absence and unrelated state on unpublished failure" which {
      Vector("memory", "searchable", "sqlite").foreach { backend =>
        s"CFS-P2/CA-PUBLISH preserve one hundred $backend failures across representations and body modes" in {
          Given("a total bounded generator with varying old revisions, representations, bodies and failure checkpoints")
          val property = Prop.forAll(_samples, Gen.oneOf(FaultMode.RootCreate, FaultMode.BeforePublish)) { (sample, mode) =>
            _with_provider(backend) { (provider, reader) =>
              Given(s"fresh real $backend storage seeded before arming exactly one $mode fault")
              val setup = fixture("create_prop_failure", representation = sample.representation, provider = provider, oldRevision = sample.oldrevision)
              setup.seed()
              val before = setup.snapshot(setup.freshId, reader())
              provider match {
                case memory: EntityCreateProviderFixture.PlainMemoryStore => memory.arm(setup.fault(mode))
                case memory: MemoryStore => memory.arm(setup.fault(mode))
                case sql: SqlStore => sql.arm(setup.fault(mode))
                case _ => throw new IllegalStateException("Property requires the actual checkpoint provider")
              }
              val candidate = setup.input(sample.mode, Some(setup.freshId), sample.content)
              When("canonical create reaches the actual root or before-publication rejection")
              val result = setup.createC(candidate)
              val after = setup.snapshot(setup.freshId, reader())
              Then("the failure retains fresh root/body absence and every complete old map")
              Prop.all("structured provider failure" |: Prop(result.isFaillure && reason(result).contains("provider-failure")),
                "fresh absence" |: Prop(after.root.isEmpty && after.overflow.isEmpty),
                "complete selected and unrelated retention" |: Prop(after == before))
            }
          }
          When("ScalaCheck executes the deferred actual failure scenarios")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("the unpublished preservation law passes one hundred samples with zero discarded cases")
          withClue(checked.toString) {
            checked.passed shouldBe true
            checked.succeeded should be >= 100
            checked.discarded shouldBe 0
          }
        }
      }
    }

    "return positive canonical results from compatible actual providers" which {
      Vector("memory", "searchable", "sqlite").foreach { backend =>
        s"CA1/CA9/CA11 preserve one hundred $backend authoritative INITIAL results and full bodies" in {
          Given("total bounded positive samples spanning both representations and every body mode")
          val property = Prop.forAll(_samples) { sample =>
            _with_provider(backend) { (provider, reader) =>
              Given(s"fresh $backend storage behind an explicitly qualified atomic-create wrapper")
              val wrapper = new EntityCreateProviderFixture.QualifiedStore(provider)
              val setup = fixture("create_prop_positive", representation = sample.representation, provider = wrapper, oldRevision = sample.oldrevision)
              setup.seed()
              val before = setup.base.snapshot(reader())
              val candidate = setup.input(sample.mode, None, sample.content)
              When("canonical create generates its admitted identity and the wrapper delegates one real operation")
              val result = setup.createC(candidate)
              Then("an acknowledged record is exact readback with INITIAL, a correct body and complete older-state retention")
              result match {
                case Consequence.Success(created) =>
                  val after = setup.snapshot(created.id, reader())
                  val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(created.id, candidate.record))
                  val expectedbody = prepared.sideEffects.collectFirst { case save: EntityVersionedSideEffect.Save => save.record.asMap }
                  Prop.all("authoritative full root" |: Prop(created.record.map(_.asMap) == after.root),
                    "exact root body metadata" |: Prop(prepared.record.fields.filter(_.key.startsWith("content")).forall { field =>
                      created.record.flatMap(_.getAny(field.key)).exists(value =>
                        EntityCreateProviderFixture.storageScalar(value) == EntityCreateProviderFixture.storageScalar(field.value.single))
                    }),
                    "admitted INITIAL" |: Prop(created.record.flatMap(_.getAny(setup.base.revisionField))
                      .exists(value => EntityRevision.createC(value).toOption.contains(EntityRevision.INITIAL))),
                    "exact complete body" |: Prop(after.overflow == expectedbody),
                    "exact unrelated full maps" |: Prop(after.preexisting == before),
                    "one qualified attempt" |: Prop(wrapper.createAttempts == 1))
                case _: Consequence.Failure[?] => Prop.falsified
              }
            }
          }
          When("ScalaCheck executes every deferred positive canonical scenario")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("actual compatible creation succeeds at least one hundred times with zero discarded inputs")
          withClue(checked.toString) {
            checked.passed shouldBe true
            checked.succeeded should be >= 100
            checked.discarded shouldBe 0
          }
        }
      }
    }
  }

  "CPR-SHR-P1 preserve create failure categories while reducing counterexamples" should afterWord(
    "in spec:entity-create-atomic-provider, example:CPR-SHR-P1, rules:CA12, phase:96") {
    "produce finite distinct smaller samples and stop at revision one with 4200 characters" in {
      Given("Spec: docs/spec/entity-create-atomic-provider.md, CA12; the total create Sample generator and its suite-local implicit shrinker")
      val property = Prop.forAll(_samples) { sample =>
        Given("a generated managed representation, positive old revision, body mode and homogeneous large text")
        val minimum = sample.oldrevision == 1L && sample.content.length == 4200
        When("the actual implicit Sample shrinker is consumed completely")
        val candidates = Shrink.shrink(sample).toVector
        Then("candidates retain the create domain and categories and strictly reduce the independent numeric rank")
        candidates.size should be <= 4
        candidates.distinct shouldBe candidates
        candidates should not contain sample
        candidates.isEmpty shouldBe minimum
        candidates.foreach { candidate =>
          candidate.representation shouldBe sample.representation
          Set(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached) should contain (candidate.representation)
          candidate.mode shouldBe sample.mode
          Set(BodyMode.Overflow, BodyMode.Inline, BodyMode.Absent, BodyMode.SetNull) should contain (candidate.mode)
          candidate.oldrevision should be >= 1L
          candidate.oldrevision should be <= 1000000L
          candidate.content.length should be >= 4200
          candidate.content.length should be <= 9000
          Set('x', '日', 'λ') should contain (candidate.content.head)
          candidate.content.forall(_ == sample.content.head) shouldBe true
          sample.content.startsWith(candidate.content) shouldBe true
          candidate.oldrevision + candidate.content.length.toLong should be < (sample.oldrevision + sample.content.length.toLong)
        }
        true
      }
      When("ScalaCheck checks one hundred total shrink contracts with one worker")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
      Then("every shrink contract succeeds without discarded inputs")
      checked.passed shouldBe true
      checked.succeeded should be >= 100
      checked.discarded shouldBe 0
    }
  }

  private val _samples: Gen[Sample] = for {
    representation <- Gen.oneOf(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
    oldrevision <- Gen.chooseNum(1L, 1000000L)
    mode <- Gen.oneOf(BodyMode.Overflow, BodyMode.Inline, BodyMode.Absent, BodyMode.SetNull)
    size <- Gen.chooseNum(4200, 9000)
    character <- Gen.oneOf("x", "日", "λ")
  } yield Sample(representation, oldrevision, mode, character * size)

  private def _with_provider[A](backend: String)(body: (DataStore, () => DataStore) => A): A =
    if (backend == "sqlite") withSqlite { (database, writer) => body(writer, () => database.open()) }
    else {
      val provider: DataStore = if (backend == "searchable") new MemoryStore() else new EntityCreateProviderFixture.PlainMemoryStore()
      body(provider, () => provider)
    }
}
