package org.goldenport.cncf.display

import java.time.Instant
import logicalui.{DisplayServiceReadFixture, TypedDisplayProjectionFixture}
import org.goldenport.Consequence
import org.goldenport.cncf.component.DisplayProtocolComponentFixture
import org.goldenport.cncf.directive.Query
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
final class DisplayServiceReadPropertySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _fixture = DisplayServiceReadFixture
  private val _typed = TypedDisplayProjectionFixture
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)
  private final case class ReadCase(rows: Vector[TypedDisplayProjectionFixture.Capture], detailfirst: Boolean)
  private val _text = for {
    size <- Gen.choose(0, 24)
    units <- Gen.listOfN(size, Gen.choose(0, 65535))
  } yield units.map(_.toChar).mkString
  private val _decimal = for {
    coefficient <- Gen.choose(-999999999999L, 999999999999L)
    scale <- Gen.oneOf(Gen.choose(-30, 30), Gen.oneOf(Int.MinValue, Int.MaxValue, 7, -7))
  } yield BigDecimal(new java.math.BigDecimal(java.math.BigInteger.valueOf(coefficient), scale))
  private val _cases = for {
    size <- Gen.choose(0, 4)
    order <- Gen.pick(size, 1 to size)
    reverse <- Gen.oneOf(false, true)
    revision <- Gen.choose(1L, Long.MaxValue)
    text <- Gen.oneOf(_text, Gen.const("\t日本 Ω e\u0301\ud83d\ude00\ud800\udfff\u0000 "))
    number <- _decimal
    flag <- Gen.oneOf(false, true)
    seconds <- Gen.choose(-1000000000L, 1000000000L)
    nanos <- Gen.choose(0, 999999999)
    detailfirst <- Gen.oneOf(false, true)
  } yield {
    val rows = order.toVector.map(index => _typed.captureC(index, revision).TAKE.copy(
      title = "title " + text, description = text, number = number, flag = flag,
      instant = Instant.ofEpochSecond(seconds, nanos.toLong)))
    ReadCase(if (reverse) rows.reverse else rows, detailfirst)
  }

  "E1 Public List/select/Detail preservation law" should afterWord("in spec:display-model-runtime, example:E1, rules:SR1,SR3,DSP96-R01,DSP96-R02,R3,R4,R7, phase:96, slice:P96-05A") {
    "preserve finite permutations empty arrays exact resources revisions and all five values with one contextual call per public operation" in {
      Given("direct legal generators for bounded UTF16 vectors decimal coefficients/scales nanos and ordered empty or permuted sources")
      val property = Prop.forAll(_cases) { candidate =>
        Given("one ordinarily installed component with independent all-kind declarations and finite immutable typed source rows")
        val session = _fixture.open(candidate.rows, allKinds = true)
        try {
          val definitions = DisplayProtocolComponentFixture.admittedDefinitions(session.component).get
          val source = DisplayProtocol.Source.createC(session.component.componentId, _fixture.resourceType).TAKE
          When("normal HTTP optionally reads Detail first then queries selects and reads from the versioned JSON envelope")
          val first = if (candidate.detailfirst) candidate.rows.headOption.map(row =>
            session.http("read", "capture-detail", Some(row.resource.entityId))) else None
          val list = session.http("query", "collecting-resources")
          val items = list.data.hcursor.downField("model").downField("items").focus.get.asArray.get
          val selected = items.headOption.map(_.hcursor.downField("resource").get[String]("entityId").toOption.get)
          val detail = selected.map(id => session.http("read", "capture-detail",
            Some(org.simplemodeling.model.datatype.EntityId.parse(id).TAKE)))
          val restoredlist = DisplayProtocolCodec.decodeC(source, definitions, list.data.noSpaces).TAKE
          val restoreddetail = detail.map(response => DisplayProtocolCodec.decodeC(source, definitions, response.data.noSpaces).TAKE)
          Then("public data is an object with exact trusted metadata and typed source semantics, not stringified or source-reissued")
          (Vector(list) ++ first.toVector ++ detail.toVector).foreach { response =>
            response.status shouldBe 200
            response.contentType shouldBe Some("application/json")
            response.data.isObject shouldBe true
            response.json.hcursor.downField("execution").get[String]("interface-shape").toOption shouldBe Some("json")
            response.data.hcursor.get[String]("componentId").toOption shouldBe Some(session.component.name)
            response.data.hcursor.get[String]("resourceType").toOption shouldBe Some(_fixture.resourceType)
            response.bytes.forall(_ >= 0) shouldBe true
          }
          restoredlist.model match {
            case model: DisplayModel.ListModel =>
              model.items.map(_.resource) shouldBe candidate.rows.map(_.resource)
              model.snapshot shouldBe None
              model.items.zip(candidate.rows).foreach { case (item, row) => _fields(item.fields, row) }
            case _ => fail("expected restored List")
          }
          items.size shouldBe candidate.rows.size
          restoreddetail.zip(candidate.rows.headOption).foreach { case (display, row) =>
            display.model match {
              case model: DisplayModel.DetailModel =>
                model.resource shouldBe row.resource
                _fields(model.sections.head.fields, row)
              case _ => fail("expected restored selected Detail")
            }
          }
          restoredlist.definition.provenance shouldBe definitions.definitions.head.provenance
          restoredlist.definition.semanticContract shouldBe definitions.definitions.head.semanticContract
          session.component.browser.queryCalls.map(_._1) shouldBe Vector(Query.fromRecord(Record.empty))
          val expectedfinds = (if (first.isDefined) candidate.rows.headOption.toVector else Vector.empty) ++
            (if (detail.isDefined) candidate.rows.headOption.toVector else Vector.empty)
          session.component.browser.findCalls.map(_._1) shouldBe expectedfinds.map(_.resource.entityId)
          (session.component.browser.queryCalls.map(_._2) ++ session.component.browser.findCalls.map(_._2)).foreach { context =>
            context.runtime.unitOfWork should not be null
          }
          session.component.browser.contextFreeCalls shouldBe 0
          session.component.browser.countCalls shouldBe 0
          session.component.serviceReads shouldBe 1
          session.component.runtimeReads shouldBe 1
          session.component.metadataReads shouldBe 1
          true
        } finally session.close()
      }
      When("ScalaCheck evaluates at least one hundred valid public-read cases without filtering generators")
      val result = Test.check(_parameters, property)
      Then("the preservation law succeeds at least one hundred times with zero discards")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }

  "E2 Closed public selection law" should afterWord("in spec:display-model-runtime, example:E2, rules:SR2,SR3,DSP96-R03,DSP96-R04, phase:96, slice:P96-05A") {
    "never invoke either component Browser for wrong component resourceType screen or extra business control inputs" in {
      Given("unfiltered finite defect generators for both ordinary read/query routes and all forbidden selector/control categories")
      val inputs = for {
        read <- Gen.oneOf(false, true)
        defect <- Gen.choose(0, 9)
        token <- Gen.choose(1, 100000)
      } yield (read, defect, token)
      val property = Prop.forAll(inputs) { case (read, defect, token) =>
        Given("a normally installed owner and a separately registered foreign component with distinct source identity")
        val row = _typed.captureC(1, 7).TAKE
        val session = _fixture.open(Vector(row))
        val foreign = new DisplayServiceReadFixture.ReadComponent(Vector(_typed.captureC(2, 19).TAKE))
        _fixture.initialize(foreign, session.subsystem, name = "PropertyForeignDisplay")
        new org.goldenport.cncf.component.ComponentFactory().bootstrapC(foreign).TAKE
        session.subsystem.add(Vector(foreign))
        try {
          val request = session.request(if (read) "read" else "query",
            if (read) "capture-detail" else "collecting-resources", if (read) Some(row.resource.entityId) else None)
          val invalid = defect match {
            case 0 => request.copy(component = Some("org.goldenport.cncf.test.Absent" + token))
            case 1 => request.copy(component = None)
            case 2 => request.copy(properties = request.properties.map(property =>
              if (property.name == "resourceType") property.copy(value = "foreign" + token) else property))
            case 3 => request.copy(properties = request.properties.map(property =>
              if (property.name == "screenId") property.copy(value = "undeclared" + token) else property))
            case 4 => request.copy(properties = request.properties :+ Property("view", "foreign" + token, None))
            case 5 => request.copy(properties = request.properties :+ Property("filter", "private" + token, None))
            case 6 => request.copy(properties = request.properties :+ Property("converter", "class" + token, None))
            case 7 => request.copy(properties = request.properties :+ Property("path", "store" + token, None))
            case 8 => request.copy(properties = request.properties :+ Property("aggregateOperation", "invoke" + token, None))
            case _ => request.copy(properties = request.properties :+ Property("limit", token, None))
          }
          When("normal subsystem resolution authorization and bound raw-request conversion interpret this single defect")
          val result = session.execute(invalid)
          Then("ordinary or structured service failure exposes no partial Display and both registered sources remain untouched")
          result shouldBe a[Consequence.Failure[?]]
          Vector(session.component, foreign).foreach { component =>
            component.browser.findCalls shouldBe empty
            component.browser.queryCalls shouldBe empty
            component.browser.contextFreeCalls shouldBe 0
            component.browser.countCalls shouldBe 0
          }
          true
        } finally session.close()
      }
      When("ScalaCheck evaluates at least one hundred closed-selection cases with no implication/discard branch")
      val result = Test.check(_parameters, property)
      Then("every generated rejection succeeds as specified with at least one hundred successes and zero discards")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }

  private def _fields(fields: Vector[DisplayModel.Field], row: TypedDisplayProjectionFixture.Capture): Unit = {
    fields(0).value shouldBe DisplayValue.Text(row.title)
    fields(1).value match {
      case DisplayValue.Text(value) => value.toCharArray.toVector shouldBe row.description.toCharArray.toVector
      case _ => fail("expected exact text")
    }
    fields(2).value match {
      case DisplayValue.Number(value) =>
        value.bigDecimal.unscaledValue shouldBe row.number.bigDecimal.unscaledValue
        value.bigDecimal.scale shouldBe row.number.bigDecimal.scale
      case _ => fail("expected exact number")
    }
    fields(3).value shouldBe DisplayValue.Boolean(row.flag)
    fields(4).value shouldBe DisplayValue.Timestamp(row.instant)
    fields(5).value shouldBe DisplayValue.Missing
  }
}
