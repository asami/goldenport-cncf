package org.goldenport.cncf.display

import java.time.Instant
import java.nio.file.Files
import logicalui.DisplayMutationServiceDomain.BodyMode
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.Database
import org.goldenport.cncf.entity.EntityRevisionRepresentation
import org.goldenport.observation.Taxonomy
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 16, 2026
 * @version Oct. 22, 2026
 */
object DisplayMutationServiceLifecyclePropertySpec {
  import DisplayMutationServiceFixture.*
  final case class Sample(text: String, description: String, coefficient: Long, scale: Int, flag: Boolean,
    seconds: Long, nanos: Int, nullable: Option[String], reversed: Boolean, omitnullable: Boolean,
    target: Target, representation: EntityRevisionRepresentation, supplied: Boolean, bodymode: BodyMode,
    version: Long, stale: Boolean, logical: Boolean)
  private val _characters = Vector('a', 'Z', '0', '日', '本', 'Ω', 'λ', '\ud800', '\udfff', '\n')
  private val _text = for { size <- Gen.chooseNum(0, 20); values <- Gen.listOfN(size, Gen.oneOf(_characters)) } yield values.mkString
  val samples: Gen[Sample] = for {
    text <- _text; description <- _text; coefficient <- Gen.chooseNum(-1000000L, 1000000L)
    scale <- Gen.chooseNum(0, 10); flag <- Gen.oneOf(false, true)
    seconds <- Gen.chooseNum(0L, 2000000000L); nanos <- Gen.chooseNum(0, 999999999)
    nullable <- Gen.option(_text); reversed <- Gen.oneOf(false, true); omit <- Gen.oneOf(false, true)
    target <- Gen.oneOf(Target.values.toVector); representation <- Gen.oneOf(representations)
    supplied <- Gen.oneOf(false, true); mode <- Gen.oneOf(BodyMode.values.toVector)
    version <- Gen.chooseNum(1L, 1000000L); stale <- Gen.oneOf(false, true); logical <- Gen.oneOf(false, true)
  } yield Sample(text, description, coefficient, scale, flag, seconds, nanos, nullable, reversed, omit,
    target, representation, supplied, mode, version, stale, logical)

  given Shrink[Sample] = Shrink { sample =>
    val text = if (sample.text.isEmpty) Stream.empty else Stream(sample.copy(text = sample.text.take(sample.text.length / 2)))
    val description = if (sample.description.isEmpty) Stream.empty else Stream(sample.copy(description = sample.description.take(sample.description.length / 2)))
    val number = if (sample.coefficient == 0L) Stream.empty else Stream(sample.copy(coefficient = sample.coefficient / 2L))
    val scale = if (sample.scale == 0) Stream.empty else Stream(sample.copy(scale = sample.scale / 2))
    val seconds = if (sample.seconds == 0L) Stream.empty else Stream(sample.copy(seconds = sample.seconds / 2L))
    val nanos = if (sample.nanos == 0) Stream.empty else Stream(sample.copy(nanos = sample.nanos / 2))
    val nullable = sample.nullable.fold(Stream.empty[Sample])(text => Stream(sample.copy(nullable = None)) #:::
      (if (text.isEmpty) Stream.empty else Stream(sample.copy(nullable = Some(text.take(text.length / 2))))))
    val version = if (sample.version == 1L) Stream.empty else Stream(sample.copy(version = math.max(1L, sample.version / 2L)))
    val modes = (if (sample.flag) Stream(sample.copy(flag = false)) else Stream.empty) #:::
      (if (sample.reversed) Stream(sample.copy(reversed = false)) else Stream.empty) #:::
      (if (sample.omitnullable) Stream(sample.copy(omitnullable = false)) else Stream.empty) #:::
      (if (sample.supplied) Stream(sample.copy(supplied = false)) else Stream.empty) #:::
      (if (sample.stale) Stream(sample.copy(stale = false)) else Stream.empty) #:::
      (if (sample.logical) Stream(sample.copy(logical = false)) else Stream.empty) #:::
      (if (sample.target == Target.Entity) Stream.empty else Stream(sample.copy(target = Target.Entity))) #:::
      (if (sample.representation == EntityRevisionRepresentation.Embedded) Stream.empty else Stream(sample.copy(representation = EntityRevisionRepresentation.Embedded))) #:::
      (if (sample.bodymode == BodyMode.Absent) Stream.empty else Stream(sample.copy(bodymode = BodyMode.Absent)))
    text #::: description #::: number #::: scale #::: seconds #::: nanos #::: nullable #::: version #::: modes
  }

  val parameters: Test.Parameters = Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
  // Generated data deliberately includes unpaired UTF-16 surrogates. Keep them
  // in the law inputs, but make failure clues safe for the UTF-8 JUnit reporter.
  private def _diagnostic_text(value: String): String =
    value.flatMap { character =>
      if (Character.isSurrogate(character)) f"\\u${character.toInt}%04x" else character.toString
    }
  def fields(sample: Sample): Vector[DisplayMutation.FieldInput] = {
    val values = Vector(field("title", DisplayValue.Text("Created:" + sample.text)),
      field("description", DisplayValue.Text(sample.description)),
      field("number", DisplayValue.Number(BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(sample.coefficient), sample.scale)))),
      field("flag", DisplayValue.Boolean(sample.flag)), field("instant", DisplayValue.Timestamp(Instant.ofEpochSecond(sample.seconds, sample.nanos.toLong)))) ++
      (if (sample.omitnullable) Vector.empty else Vector(field("nullable", sample.nullable.map(DisplayValue.Text.apply).getOrElse(DisplayValue.Missing))))
    if (sample.reversed) values.reverse else values
  }

  /** Finite target/representation/server-seed/body modes retain at most four live runtimes. */
  final class Pool(backend: String, database: Option[Database] = None) {
    private var _sessions = Map.empty[(Target, EntityRevisionRepresentation, Boolean, BodyMode), Session]
    def liveSessionCount: Int = _sessions.size
    def session(sample: Sample, create: Boolean): Session = {
      val key = (sample.target, sample.representation, if (create) sample.supplied else true,
        if (create) sample.bodymode else BodyMode.Overflow)
      _sessions.get(key) match {
        case Some(value) => value
        case None =>
          if (_sessions.size >= 4) {
            val (retiredkey, retired) = _sessions.head
            _sessions = _sessions.removed(retiredkey)
            retired.close()
          }
          val value = open(key._1, key._2, database, backend = backend,
            lifecycle = Some(LifecycleSetup(key._3, key._4)))
          _sessions = _sessions.updated(key, value)
          value
      }
    }
    def close(): Unit = {
      var failure: Option[Throwable] = None
      _sessions.values.foreach { session =>
        try session.close() catch { case scala.util.control.NonFatal(error) => if (failure.isEmpty) failure = Some(error) }
      }
      _sessions = Map.empty
      failure.foreach(error => throw error)
    }
  }

  trait Laws extends DisplayMutationServiceFixture.Vocabulary { self: org.scalatest.Informing =>
    def createLaw(pool: Pool, label: String): Test.Result = {
      val property = Prop.forAll(samples) { sample =>
        Given("an independently reset absent selected root, warm stale residents/Views and generated typed fields/server defaults")
        When("the generated create mode selects its owned session from the bounded live pool")
        val session = pool.session(sample, true)
        Then("selection retains at most four live owned sessions after closing any evicted runtime before replacement")
        pool.liveSessionCount should be <= 4
        Given("the selected owned session reset to the independently declared create baseline")
        session.prepareLifecycle(true)
        val inputs = fields(sample)
        val before = session.lifecycleSnapshot()
        val wanted = expectedLifecycleCreate(session, before, inputs)
        before.rows.selected shouldBe None
        before.rows.body shouldBe None
        When("the ordinary authorized create runs once on the actual current UnitOfWork")
        val attempt = session.execute(session.lifecycleRequest(DisplayMutation.Operation.Create, inputs))
        Then("the exact typed fields, server identity/defaults, native state, INITIAL receipt and contextual Detail equal the pre-action oracle")
        checkLifecycleResult(attempt, session, true, 1L)
        session.log.conversions shouldBe logicalui.DisplayMutationServiceDomain.fieldIds.filter(name => inputs.exists(_.location.fieldId == name))
        session.log.validations shouldBe 1
        session.log.commands shouldBe (if (sample.target == Target.Entity) 0 else 1)
        session.log.idCalls shouldBe (if (sample.supplied) Vector.empty else Vector(session.component.collectionId -> "entity-create"))
        session.log.events.count(_.event == "capture") shouldBe 1
        session.log.events.count(_.event == "find") shouldBe 1
        checkLifecycleState(session, wanted)
        true
      }
      When("the total positive-create law runs sequentially with reducing Unicode, number, time, optional-value and mode shrinkers")
      val result = Test.check(parameters, property)
      Then("at least one hundred successful samples and zero discards are required")
      withClue(s"$label ${_diagnostic_text(result.status.toString)}: ") { result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0 }
      result
    }
    def deleteLaw(pool: Pool, label: String): Test.Result = {
      val property = Prop.forAll(samples) { sample =>
        Given("a reset root at a generated legal revision with independently selected logical/physical and current/stale modes")
        When("the generated delete mode selects its owned session from the bounded live pool")
        val session = pool.session(sample, false)
        Then("selection retains at most four live owned sessions after closing any evicted runtime before replacement")
        pool.liveSessionCount should be <= 4
        Given("the selected owned session reset to the independently declared delete baseline")
        session.prepareLifecycle(false, sample.version, sample.logical)
        val before = session.lifecycleSnapshot()
        val wanted = if (sample.stale) expectedLifecycleDeleteRejection(before, DeleteRejectionBoundary.BeforeCapture)
          else expectedLifecycleDelete(session, before, sample.version, sample.logical)
        val observed = if (sample.stale) sample.version + 1L else sample.version
        val expectedreason = "stale-entity-revision"
        When("one ordinary observed delete checks the external observation before the canonical capture")
        val attempt = session.execute(session.lifecycleRequest(DisplayMutation.Operation.Delete, observed = observed))
        Then("current deletes retain matched prior revision and reload-list; stale requests preserve the complete pre-action state without replay")
        if (sample.stale) {
          conclusion(attempt.result).observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Conflict
          reasons(attempt.result) should contain(expectedreason)
          checkLifecyclePreserved(session, wanted, attempt)
          session.log.validations shouldBe 0
        } else {
          checkLifecycleResult(attempt, session, false, sample.version)
          session.log.validations shouldBe 1
          checkLifecycleState(session, wanted)
        }
        session.log.events.count(_.event == "find") shouldBe 0
        session.log.commands shouldBe 0
        session.log.idCalls shouldBe Vector.empty
        true
      }
      When("the total observed-delete law runs sequentially with positive revision and finite-mode shrinking")
      val result = Test.check(parameters, property)
      Then("at least one hundred successful samples and zero discards are required")
      withClue(s"$label ${_diagnostic_text(result.status.toString)}: ") { result.passed shouldBe true; result.succeeded should be >= 100; result.discarded shouldBe 0 }
      result
    }
  }
}

final class DisplayMutationServiceLifecyclePropertySpec extends AnyWordSpec
    with DisplayMutationServiceLifecyclePropertySpec.Laws {
  import DisplayMutationServiceLifecyclePropertySpec.*
  private val _governed = afterWord("under docs/spec/display-mutation-service.md DLC-10..12, docs/design/display-mutation-service.md DLC-D01 and RULE.md")
  "Ordinary Display lifecycle generated laws" should {
    "E1 retain malformed UTF-16 counterexamples in UTF-8 XML failure diagnostics" must afterWord("in spec:display-mutation-service, example:E1, rules:DLC-10,DLC-11,DLC-12, phase:116") {
      "preserve the actual counterexample through XML-safe failure clues" in {
        Given("Spec: docs/spec/display-mutation-service.md; Rules: DLC-10,DLC-11,DLC-12; Example: E1; a failing generated sample containing unpaired high and low surrogates")
        val sample = "日\ud800x\udfff"
        val result = Test.check(parameters, Prop.forAll(Gen.const(sample))(_ => false))
        When("the actual ScalaCheck counterexample is rendered as a JUnit failure clue")
        val diagnostic = _diagnostic_text(result.status.toString)
        val report = Files.createTempFile("cncf-lifecycle-diagnostic-", ".xml")
        try {
          scala.xml.XML.save(report.toString, <testsuite><failure>{diagnostic}</failure></testsuite>, "UTF-8")
          Then("UTF-8 XML retains both offending code units without changing the generated input")
          result.passed shouldBe false
          diagnostic should include("\\ud800")
          diagnostic should include("\\udfff")
          (scala.xml.XML.loadFile(report.toFile) \ "failure").text shouldBe diagnostic
          sample.charAt(1) shouldBe '\ud800'
          sample.charAt(3) shouldBe '\udfff'
        } finally Files.deleteIfExists(report)
      }
    }
    "total canonical create and observed delete" which _governed {
      "DLC-P1 preserve independently expected positive typed creates on memory and SQLite with meaningful shrinking" in {
        for (backend <- Vector("memory", "sqlite")) {
          Given(s"a reusable $backend lifecycle pool retaining at most four live sessions with exact owned-ID reset and finally cleanup")
          val pool = new Pool(backend)
          When("the positive-create law interprets one hundred generated ordinary commands with full pre-action expectations")
          val result = try createLaw(pool, "DLC-P1 " + backend) finally pool.close()
          Then("the portable create law is successful without discarded samples")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
      "DLC-P2 preserve independently expected current or stale observed deletes on memory and SQLite with meaningful shrinking" in {
        for (backend <- Vector("memory", "sqlite")) {
          Given(s"a reusable $backend lifecycle pool retaining at most four live sessions over targets and revision representations")
          val pool = new Pool(backend)
          When("the observed-delete law interprets one hundred generated current/stale requests with full pre-action expectations")
          val result = try deleteLaw(pool, "DLC-P2 " + backend) finally pool.close()
          Then("the portable observed-delete law is successful without discarded samples")
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }
  }
}
