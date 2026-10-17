package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{EntityConcurrencyPolicy, EntityWritePolicy, RevisionPreconditionPolicy}
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
object InMemoryEntityClearComparisonSpec {
  final case class ClearSample(text: String, replace: Boolean)
  final case class Snapshot(selected: Record, unrelated: Record, body: Record, sentinel: Record,
    candidate: Option[Record] = None)

  private val _characters = Vector('a', 'Z', '0', '日', '本', 'Ω', 'λ')
  val samples: Gen[ClearSample] = for {
    size <- Gen.chooseNum(1, 20)
    characters <- Gen.listOfN(size, Gen.oneOf(_characters))
    replace <- Gen.oneOf(false, true)
  } yield ClearSample(characters.mkString, replace)

  given Shrink[ClearSample] = Shrink { sample =>
    val text = if (sample.text.length <= 1) Stream.empty
      else Stream(sample.copy(text = sample.text.take(math.max(1, sample.text.length / 2))))
    val mode = if (sample.replace) Stream(sample.copy(replace = false)) else Stream.empty
    text #::: mode
  }
  val parameters: Test.Parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
}

final class InMemoryEntityClearComparisonSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import InMemoryEntityClearComparisonSpec.*
  import InMemoryEntityClearComparisonSpec.given

  private val _root_collection = DataStore.CollectionId("memory_clear_owned_roots")
  private val _side_collection = DataStore.CollectionId("memory_clear_owned_sides")
  private val _selected = DataStore.StringEntryId("selected-root")
  private val _unrelated = DataStore.StringEntryId("unrelated-root")
  private val _body = DataStore.StringEntryId("selected-root:body")
  private val _sentinel = DataStore.StringEntryId("unrelated-sentinel")
  private val _candidate = DataStore.StringEntryId("unpublished-candidate")
  private val _revision_field = "cncf_revision"
  private val _business = Record.dataAuto("label" -> " nested 日本 Ω ", "count" -> 3L,
    "flags" -> Vector("α", "β"))
  private val _security = Record.dataAuto("owner" -> "owner", "roles" -> Vector("reader", "writer"))

  private def _governed(example: String) = afterWord(
    s"in spec:display-mutation-service, example:$example, rules:R5,R11-R13, phase:96/P96-07C4C; " +
      "docs/spec/entity-conflict-and-conditional-transition.md, docs/spec/display-mutation-service.md " +
      "and docs/design/display-mutation-service.md C4C-D01"
  )

  "Memory versioned clear comparison" should {
    for (backend <- Vector("memory", "memory-searchable"); replace <- Vector(false, true)) {
      s"$backend with ${if (replace) "Replace" else "Patch"}" should {
        "MEM-CLEAR-E1 apply the first clear and retain the full authoritative record on repetition" which _governed("MEM-CLEAR-E1") {
          "publish only the first prescribed body" in {
            Given("R5: a fresh actual memory provider with complete root/body/unrelated/sentinel records and non-NULL empty text")
            val store = _store(backend)
            given ExecutionContext = _context(store)
            val sample = ClearSample(" \t日本 Ω\n ", replace)
            val before = _seed(store, sample.text)
            val cleared = _first_clear(store, sample, before)
            _repeat_clear(store, sample, cleared)
          }
        }
        "MEM-CLEAR-E2 reject an observed stale clear before equality" which _governed("MEM-CLEAR-E2") {
          "preserve every root and side target" in {
            Given("R5: an independently declared revision-two cleared root on a fresh actual provider")
            val store = _store(backend)
            given ExecutionContext = _context(store)
            val sample = ClearSample("stale 日本 λ", replace)
            val cleared = _first_clear(store, sample, _seed(store, sample.text))
            _stale_clear(store, sample, cleared)
          }
        }
        "MEM-CLEAR-E3 omit an already absent nullable attribute" which _governed("MEM-CLEAR-E3") {
          "retain revision two and publish no candidate side effects" in {
            Given("R5: the complete previously cleared state and an omission plan fixed before the action")
            val store = _store(backend)
            given ExecutionContext = _context(store)
            val sample = ClearSample("omitted Ω", replace)
            val cleared = _first_clear(store, sample, _seed(store, sample.text))
            _omit_clear(store, sample, cleared)
          }
        }
        "MEM-CLEAR-E4 retain an absent attribute at Long.MaxValue without overflow" which _governed("MEM-CLEAR-E4") {
          "return the full no-op record before requesting a next revision" in {
            Given("R5: an absent nullable attribute and exact maximum-revision root/body/sentinel baseline")
            val store = _store(backend)
            given ExecutionContext = _context(store)
            val sample = ClearSample("maximum 日本", replace)
            val before = _seed(store, sample.text, nullable = None, version = Long.MaxValue)
            val plan = _plan(sample, Long.MaxValue, _candidate_effects())
            _check_snapshot(store, before)
            When("a current observed WriteIfChanged requests the same logical clear")
            val result = _provider(store).mutateVersionedEntity(plan)
            Then("NoOp returns every original ordered field and no side record or revision changes")
            _check_result(result, before.selected, applied = false)
            _check_snapshot(store, before)
          }
        }
        "MEM-CLEAR-E5 preserve AlwaysWrite after an already absent clear" which _governed("MEM-CLEAR-E5") {
          "advance exactly once and publish its prescribed body" in {
            Given("R5: the exact cleared revision-two state and an AlwaysWrite plan with a new body")
            val store = _store(backend)
            given ExecutionContext = _context(store)
            val sample = ClearSample("always λ", replace)
            val cleared = _first_clear(store, sample, _seed(store, sample.text))
            val wanted = cleared.copy(selected = _root_record(sample.text, None, 3L), body = _body_record("always body"))
            val plan = _plan(sample, 2L, _effects(wanted.body), writepolicy = EntityWritePolicy.AlwaysWrite)
            _check_snapshot(store, cleared)
            When("AlwaysWrite receives the current observation and repeated logical clear")
            val result = _provider(store).mutateVersionedEntity(plan)
            Then("Applied returns the complete revision-three root and only the declared body changes")
            _check_result(result, wanted.selected, applied = true)
            _check_snapshot(store, wanted)
          }
        }
        "MEM-CLEAR-E6 preserve raw strings structured values and significant whitespace" which _governed("MEM-CLEAR-E6") {
          "compare memory values without SQL JSON decoding or trimming" in {
            Given("R5: a raw JSON business String and independently specified structured replacement of the same apparent JSON value")
            val store = _store(backend)
            given ExecutionContext = _context(store)
            val sample = ClearSample(" 日本 Ω ", replace)
            val raw = "{\"label\":\"same\"}"
            val structured = Record.dataAuto("label" -> "same")
            val before = _seed(store, sample.text, nullable = None, business = raw)
            val wanted = before.copy(selected = _root_record(sample.text, None, 2L, structured), body = _body_record("structured body"))
            val plan = _business_plan(sample, 1L, structured, _effects(wanted.body))
            _check_snapshot(store, before)
            When("the current mutation replaces the raw String with an actual nested Record")
            val changed = _provider(store).mutateVersionedEntity(plan)
            Then("the distinct memory values require Applied with all unrelated and exact raw text fields preserved")
            _check_result(changed, wanted.selected, applied = true)
            _check_snapshot(store, wanted)

            Given("the exact structured state and a second independent candidate differing only in significant outer whitespace")
            val trimmed = sample.copy(text = "日本 Ω")
            val whitespacewanted = wanted.copy(selected = _root_record(trimmed.text, None, 3L, structured),
              body = _body_record("whitespace body"))
            val whitespaceplan = _business_plan(trimmed, 2L, structured, _effects(whitespacewanted.body))
            _check_snapshot(store, wanted)
            When("the current plan changes only significant text whitespace")
            val whitespace = _provider(store).mutateVersionedEntity(whitespaceplan)
            Then("Applied advances once while the complete structured business and security data remain exact")
            _check_result(whitespace, whitespacewanted.selected, applied = true)
            _check_snapshot(store, whitespacewanted)
          }
        }
      }
    }

    "MEM-CLEAR-P1 total clear repeat stale omission sequence" which _governed("MEM-CLEAR-P1") {
      for (backend <- Vector("memory", "memory-searchable")) {
        s"retain the complete sequence for at least one hundred $backend samples with strict nonempty shrinking" in {
          Given("R5: total nonempty length-one-to-twenty ASCII/Japanese/Greek text, both mutation forms and reducing text/mode shrinkers")
          val property = Prop.forAll(samples) { sample =>
            Given(s"a fresh $backend provider for the complete generated sample $sample")
            val store = _store(backend)
            given ExecutionContext = _context(store)
            val before = _seed(store, sample.text)
            val cleared = _first_clear(store, sample, before)
            _repeat_clear(store, sample, cleared)
            _stale_clear(store, sample, cleared)
            _omit_clear(store, sample, cleared)
            true
          }
          When("ScalaCheck interprets every complete sequence with one worker and meaningful shrinking")
          val checked = Test.check(parameters, property)
          Then("the actual status passes with at least one hundred successes and zero discards")
          withClue(s"MEM-CLEAR-P1 $backend ${checked.status}: ") {
            checked.passed shouldBe true
            checked.succeeded should be >= 100
            checked.discarded shouldBe 0
          }
        }
      }
    }
  }

  private def _store(backend: String): DataStore = backend match {
    case "memory" => DataStore.inMemory()
    case "memory-searchable" => DataStore.inMemorySearchable()
    case other => fail(s"unknown memory provider: $other")
  }
  private def _context(store: DataStore): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }
  private def _provider(store: DataStore): EntityVersionedMutationDataStore =
    store.asInstanceOf[EntityVersionedMutationDataStore]
  private def _revision(version: Long): EntityRevision = EntityRevision.createC(version).TAKE

  private def _root_record(text: String, nullable: Option[String], version: Long, business: Any = _business): Record =
    Record(Vector(Field("id", Field.Value.Single(_selected.print)), Field("text", Field.Value.Single(text))) ++
      nullable.toVector.map(value => Field("nullable", Field.Value.Single(value))) ++
      Vector(Field("empty", Field.Value.Single("")), Field("business", Field.Value.Single(business)),
        Field("security", Field.Value.Single(_security)), Field("raw", Field.Value.Single(" {\"key\":1} ")),
        Field(_revision_field, Field.Value.Single(version))))
  private def _body_record(content: String): Record = Record.dataAuto("id" -> _body.print, "content" -> content,
    "security" -> _security)

  private def _seed(store: DataStore, text: String, nullable: Option[String] = Some("present 日本"),
    version: Long = 1L, business: Any = _business)(using ExecutionContext): Snapshot = {
    val expected = Snapshot(_root_record(text, nullable, version, business),
      Record.dataAuto("id" -> _unrelated.print, "text" -> "unrelated λ", "empty" -> "", "business" -> _business,
        "security" -> _security, _revision_field -> 17L),
      _body_record("initial body 日本"), Record.dataAuto("id" -> _sentinel.print, "content" -> "sentinel Ω", "security" -> _security))
    store.create(_root_collection, _selected, expected.selected).TAKE
    store.create(_root_collection, _unrelated, expected.unrelated).TAKE
    store.create(_side_collection, _body, expected.body).TAKE
    store.create(_side_collection, _sentinel, expected.sentinel).TAKE
    _check_snapshot(store, expected)
    expected
  }
  private def _effects(body: Record): Vector[EntityVersionedSideEffect] =
    Vector(EntityVersionedSideEffect.Save(_side_collection, _body, body))
  private def _candidate_effects(): Vector[EntityVersionedSideEffect] = Vector(
    EntityVersionedSideEffect.Delete(_side_collection, _body),
    EntityVersionedSideEffect.Delete(_side_collection, _sentinel),
    EntityVersionedSideEffect.Save(_side_collection, _candidate, Record.dataAuto("id" -> _candidate.print, "content" -> "must not publish")))

  private def _plan(sample: ClearSample, observed: Long, effects: Vector[EntityVersionedSideEffect],
    omit: Boolean = false, writepolicy: EntityWritePolicy = EntityWritePolicy.WriteIfChanged): EntityVersionedMutationPlan = {
    val clear = if (omit) Vector.empty else Vector(Field("nullable", Field.Value.Single(Update.SetNull)))
    val root = if (sample.replace) EntityVersionedRootMutation.Replace(
      Record(_root_record(sample.text, None, 1L).fields.filterNot(_.key == _revision_field) ++ clear))
      else EntityVersionedRootMutation.Patch(Record(clear))
    EntityVersionedMutationPlan(_root_collection, _selected, _revision_field,
      EntityConcurrencyPolicy.Optimistic, writepolicy, RevisionPreconditionPolicy.ObservedRequired,
      Some(_revision(observed)), root, sideEffects = effects)
  }
  private def _business_plan(sample: ClearSample, observed: Long, business: Any,
    effects: Vector[EntityVersionedSideEffect]): EntityVersionedMutationPlan = {
    val root = if (sample.replace) EntityVersionedRootMutation.Replace(
      Record(_root_record(sample.text, None, observed, business).fields.filterNot(_.key == _revision_field)))
      else EntityVersionedRootMutation.Patch(
        Record(_root_record(sample.text, None, observed, business).fields.filterNot(_.key == _revision_field)))
    _plan(sample, observed, effects).copy(rootMutation = root)
  }
  private def _check_result(result: Consequence[EntityVersionedMutationResult], expected: Record, applied: Boolean): Unit =
    withClue(s"actual mutation result $result: ") {
      result match {
        case Consequence.Success(EntityVersionedMutationResult.Applied(record)) if applied => record.fields shouldBe expected.fields
        case Consequence.Success(EntityVersionedMutationResult.NoOp(record)) if !applied => record.fields shouldBe expected.fields
        case other => fail(s"expected ${if (applied) "Applied" else "NoOp"} with complete authoritative record, got $other")
      }
    }
  private def _check_snapshot(store: DataStore, expected: Snapshot)(using ExecutionContext): Unit = {
    val found = Vector((_root_collection, _selected), (_root_collection, _unrelated), (_side_collection, _body),
      (_side_collection, _sentinel), (_side_collection, _candidate)).map { case (collection, identity) =>
      store.load(collection, identity).TAKE.map(_.fields)
    }
    found shouldBe Vector(Some(expected.selected.fields), Some(expected.unrelated.fields), Some(expected.body.fields),
      Some(expected.sentinel.fields), expected.candidate.map(_.fields))
  }
  private def _first_clear(store: DataStore, sample: ClearSample, before: Snapshot)(using ExecutionContext): Snapshot = {
    Given("MEM-CLEAR-E1: full independent ordered revision-one root and every side/unrelated observation before the first clear")
    val wanted = before.copy(selected = _root_record(sample.text, None, 2L), body = _body_record("cleared body 日本 Ω"))
    val plan = _plan(sample, 1L, _effects(wanted.body))
    _check_snapshot(store, before)
    When("the actual provider clears the present nullable attribute with a matched observation")
    val result = _provider(store).mutateVersionedEntity(plan)
    Then("Applied returns the full revision-two root with absent nullable, retained empty text and exactly the prescribed body")
    _check_result(result, wanted.selected, applied = true)
    _check_snapshot(store, wanted)
    wanted
  }
  private def _repeat_clear(store: DataStore, sample: ClearSample, before: Snapshot)(using ExecutionContext): Unit = {
    Given("MEM-CLEAR-E1: the fixed full cleared record and candidate body/sentinel deletion and publication before a repeated clear")
    val plan = _plan(sample, 2L, _candidate_effects())
    _check_snapshot(store, before)
    When("the current observed clear repeats against an already absent nullable attribute")
    val result = _provider(store).mutateVersionedEntity(plan)
    Then("NoOp returns the original full authoritative revision-two record and publishes none of the candidate effects")
    _check_result(result, before.selected, applied = false)
    _check_snapshot(store, before)
  }
  private def _stale_clear(store: DataStore, sample: ClearSample, before: Snapshot)(using ExecutionContext): Unit = {
    Given("MEM-CLEAR-E2: complete cleared revision-two state with an independently stale observation of one")
    val plan = _plan(sample, 1L, _candidate_effects())
    val wanted = EntityVersionedMutationResult.Stale(_revision(1L), _revision(2L))
    _check_snapshot(store, before)
    When("an equal logical clear carries that stale observation")
    val result = _provider(store).mutateVersionedEntity(plan)
    Then("typed Stale wins before equality and preserves all root/body/sentinel/unpublished targets")
    withClue(s"actual stale result $result: ") { result shouldBe Consequence.success(wanted) }
    _check_snapshot(store, before)
  }
  private def _omit_clear(store: DataStore, sample: ClearSample, before: Snapshot)(using ExecutionContext): Unit = {
    Given("MEM-CLEAR-E3: full revision-two state and an independent replacement or patch omitting the already absent attribute")
    val plan = _plan(sample, 2L, _candidate_effects(), omit = true)
    _check_snapshot(store, before)
    When("the provider interprets that current omission")
    val result = _provider(store).mutateVersionedEntity(plan)
    Then("NoOp retains every authoritative field and all original side/unrelated observations")
    _check_result(result, before.selected, applied = false)
    _check_snapshot(store, before)
  }
}
