package org.goldenport.cncf.display

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.entity.EntityRevisionRepresentation
import org.goldenport.protocol.Property
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
object DisplayBusinessReviewPropertySpec {
  import DisplayMutationServiceFixture.Target
  final case class Sample(payload: String, initialrevision: Long, target: Target, representation: EntityRevisionRepresentation) {
    def title: String = "T" + payload
  }
  enum InvalidCase {
    case MissingResource, MissingRevision, MalformedIdentity
    case InvalidRevision(spelling: Int)
    case Anonymous, Foreign, Factory
  }
  final case class Rejected(sample: Sample, invalid: InvalidCase)
  given Shrink[Sample] = Shrink { sample =>
    val text = if (sample.payload.isEmpty) Stream.empty else
      Stream(sample.copy(payload = sample.payload.take(sample.payload.length / 2)))
    val revision = if (sample.initialrevision == 1L) Stream.empty else
      Stream(sample.copy(initialrevision = math.max(1L, sample.initialrevision / 2L)))
    val target = if (sample.target == Target.Entity) Stream.empty else Stream(sample.copy(target = Target.Entity))
    val representation = if (sample.representation == EntityRevisionRepresentation.Embedded) Stream.empty else
      Stream(sample.copy(representation = EntityRevisionRepresentation.Embedded))
    text #::: revision #::: target #::: representation
  }
  given Shrink[InvalidCase] = Shrink { invalid =>
    val spelling = invalid match {
      case InvalidCase.InvalidRevision(index) if index > 0 => Stream(InvalidCase.InvalidRevision(index / 2))
      case _ => Stream.empty
    }
    spelling #::: (if (invalid == InvalidCase.MissingResource) Stream.empty else Stream(InvalidCase.MissingResource))
  }
  given Shrink[Rejected] = Shrink { rejected =>
    summon[Shrink[Sample]].shrink(rejected.sample).map(value => rejected.copy(sample = value)) #:::
      summon[Shrink[InvalidCase]].shrink(rejected.invalid).map(value => rejected.copy(invalid = value))
  }
}

final class DisplayBusinessReviewPropertySpec extends AnyWordSpec with DisplayBusinessReviewFixture.Vocabulary {
  import DisplayBusinessReviewFixture.*
  import DisplayBusinessReviewPropertySpec.*
  import DisplayBusinessReviewPropertySpec.given
  import DisplayMutationServiceFixture.{Target, representations}
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
  private val _samples = for {
    length <- Gen.choose(1, 18)
    characters <- Gen.listOfN(length, Gen.oneOf('a', 'Z', '日', 'Ω', '\u0301', '\ud800', '\udfff', '\n', '\u0000'))
    revision <- Gen.choose(1L, 16L)
    target <- Gen.oneOf(Target.values.toVector)
    representation <- Gen.oneOf(representations)
  } yield Sample(characters.mkString, revision, target, representation)
  private val _invalid_revisions = Vector("0", "-1", "+1", "01", "1.0", "1e0", " 1", "9223372036854775808")
  private val _rejected = for {
    sample <- _samples
    invalid <- Gen.oneOf(Gen.const(InvalidCase.MissingResource), Gen.const(InvalidCase.MissingRevision),
      Gen.const(InvalidCase.MalformedIdentity), Gen.choose(0, _invalid_revisions.size - 1).map(InvalidCase.InvalidRevision.apply),
      Gen.const(InvalidCase.Anonymous), Gen.const(InvalidCase.Foreign), Gen.const(InvalidCase.Factory))
  } yield Rejected(sample, invalid)
  private def _law(result: Test.Result): Unit = {
    withClue(result.toString) { result.passed shouldBe true }
    result.succeeded should be >= 100
    result.discarded shouldBe 0
  }
  private def _check_title(value: Json, expected: String): Unit = {
    val section = value.hcursor.downField("model").downField("sections").focus.get.asArray.get
      .find(_.hcursor.get[String]("id") == Right("main")).get
    val field = section.hcursor.downField("fields").focus.get.asArray.get
      .find(_.hcursor.get[String]("id") == Right("title")).get
    field.hcursor.downField("value").get[String]("text") shouldBe Right(expected)
  }
  private def _committed(sample: Sample): Boolean = {
    Given("a sample's lawful title containing its exact raw payload and complete independent state after trusted seeding and warming both Views")
    val session = open(sample.target, sample.representation)
    try {
      session.seed(sample.title, sample.initialrevision)
      val before = baseline(session)
      Then("the initial logical and Display semantic titles equal the independently supplied exact lawful title")
      before.resident.views.selected.record.getString("title") shouldBe Some(sample.title)
      _check_title(before.display, sample.title)
      val wanted = expected(session, before, sample.initialrevision + 1L)
      When("the direct Business Operation observes the sample's exact current revision")
      val attempt = session.execute(request(session, sample.initialrevision.toString))
      Then("stage and canonical managed metadata alone change, and only explicit Detail read observes fresh state")
      val response = checkCommit(attempt, session, before, wanted, sample.initialrevision + 1L)
      val fresh = checkReload(session, before, response, sample.initialrevision + 1L)
      Then("the explicitly reloaded Display semantic title equals the independently supplied exact lawful title")
      _check_title(fresh, sample.title)
      checkState(session, wanted)
      true
    } finally session.close()
  }

  "BRP1 Generated canonical review correspondence" should afterWord(
    "in spec:display-business-operation-descriptor, example:BRP1, rules:BR1,BR2, phase:96") {
    "preserve arbitrary Unicode payload across commit and explicit reload for every generated target and revision" in {
      Given("docs/spec/display-business-operation-descriptor.md BR1/BR2: total bounded Unicode, revision 1..16, target and representation generators with reducing lawful shrinkers")
      val payload = "\n\u0000"
      When("the original newline/NUL payload at revision 16 uses the same commit/reload oracle for every target and representation")
      for {
        target <- Target.values.toVector
        representation <- representations
      } {
        val committed = _committed(Sample(payload, 16L, target, representation))
        Then("the exact original control payload commits and reloads inside a lawful nonblank title")
        committed shouldBe true
      }
      When("one hundred samples each own a real SQLite fixture and invoke the same ordinary canonical Business command oracle")
      val result = Test.check(_parameters, Prop.forAll(_samples)(sample => _committed(sample)))
      Then("at least one hundred successful witnesses and zero discards establish the law")
      _law(result)
    }
  }

  "BRP2 Generated malformed and denied preservation" should afterWord(
    "in spec:display-business-operation-descriptor, example:BRP2, rules:BR1,BR2, phase:96") {
    "retain every independent pre-state dimension without filtering or shrinking an invalid revision into a valid one" in {
      Given("docs/spec/display-business-operation-descriptor.md BR1/BR2: explicit invalid/denied case classes and strictly reducing still-invalid shrink witnesses")
      When("one hundred total bounded cases each own their canonical fixture and rejected ordinary request")
      val result = Test.check(_parameters, Prop.forAll(_rejected) { rejected =>
        Given("the generated title/revision/target/representation and a warmed complete baseline")
        val sample = rejected.sample
        val session = open(sample.target, sample.representation)
        try {
          session.seed(sample.title, sample.initialrevision)
          val before = baseline(session)
          val valid = request(session, sample.initialrevision.toString)
          val candidate = rejected.invalid match {
            case InvalidCase.MissingResource => valid.copy(properties = valid.properties.filterNot(_.name == "resourceId"))
            case InvalidCase.MissingRevision => valid.copy(properties = valid.properties.filterNot(_.name == "revision"))
            case InvalidCase.MalformedIdentity => valid.copy(properties = List(Property("resourceId", "not-an-entity-id", None),
              Property("revision", sample.initialrevision.toString, None)))
            case InvalidCase.InvalidRevision(index) => request(session, _invalid_revisions(index))
            case _ => valid
          }
          session.log.denyFactory = rejected.invalid == InvalidCase.Factory
          val active = session.execution(principal = if (rejected.invalid == InvalidCase.Foreign) "foreign" else "owner",
            authenticated = rejected.invalid != InvalidCase.Anonymous)
          When("the ordinary Operation receives the malformed input or denied subject")
          val attempt = session.execute(candidate, active)
          Then("structured rejection publishes no provider mutation or Browser read and all complete data and cache-build observations remain exact")
          attempt.result shouldBe a[Consequence.Failure[?]]
          active.attempts shouldBe 0
          checkPreservedBusiness(session, before)
          true
        } finally session.close()
      })
      Then("at least one hundred successful preservation witnesses and zero discards establish the law")
      _law(result)
    }
  }
}
