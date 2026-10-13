package org.goldenport.cncf.display

import java.nio.charset.StandardCharsets
import java.time.Instant
import domain.logicalui.DomainLogicalUiDefinitions
import io.circe.Json
import logicalui.{DisplayServiceReadFixture, TypedDisplayProjectionFixture}
import org.goldenport.Consequence
import org.goldenport.cncf.component.DisplayProtocolComponentFixture
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, SecurityContext, SecurityLevel, SubjectKind}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.{Argument, Property, Request, Switch}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.schema.DataType
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
final class DisplayServiceReadSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _fixture = DisplayServiceReadFixture
  private val _typed = TypedDisplayProjectionFixture

  "E1 Ordinary Detail-first read" should afterWord("in spec:display-model-runtime, example:E1, rules:DSP96-R04,SR1,SR2,SR3, phase:96, slice:P96-05A") {
    "return actual delivered metadata and the contextual resource without another semantic call" in {
      Given("an installed actual-generated component and a typed revision-seven resource before any List query")
      val row = _typed.captureC(1, 7).TAKE
      val session = _fixture.open(Vector(row))
      try {
        When("the normal subsystem resolves authorizes and executes component.display.read")
        val payload = _json(session.execute(session.request("read", "capture-detail", Some(row.resource.entityId))))
        Then("the typed business object preserves delivered provenance and source identity with exactly one contextual find")
        _identity(payload, session, row.resource.entityId.value, "7")
        payload.hcursor.downField("provenance").get[String]("producerId").toOption shouldBe
          Some(DomainLogicalUiDefinitions.definitions(1).provenance.producerId)
        payload.hcursor.downField("semanticContract").focus shouldBe
          _json_metadata(session, "semanticContract")
        session.component.browser.findCalls.map(_._1) shouldBe Vector(row.resource.entityId)
        session.component.browser.findCalls.head._2.runtime.unitOfWork should not be null
        session.component.browser.queryCalls shouldBe empty
        _idle_auxiliary(session)
      } finally session.close()
    }
  }

  "E2 Canonical HTTP Detail/List/select/Detail" should afterWord("in spec:display-model-runtime, example:E2, rules:DSP96-R01,DSP96-R03,DSP96-R04,SR3,R7, phase:96, slice:P96-05A") {
    "carry an object through actual in-process Http4s routing while semantic clients keep typed values" in {
      Given("two ordered actual-generated resources with distinct identities and revisions")
      val rows = Vector(_typed.captureC(1, 7).TAKE, _typed.captureC(2, 19).TAKE)
      val session = _fixture.open(rows)
      try {
        When("normal GET .json/envelope performs Detail first then List selection and selected Detail")
        val first = session.http("read", "capture-detail", Some(rows.head.resource.entityId))
        val list = session.http("query", "collecting-resources")
        Then("initial Detail and List return successful HTTP envelopes before selection")
        Vector(first, list).foreach { response =>
          withClue(s"Initial HTTP response: ${response.text}") {
            response.status shouldBe 200
          }
        }
        When("the successful List exposes the selected resource identity")
        val selected = list.data.hcursor.downField("model").downField("items").downN(1)
          .downField("resource").get[String]("entityId").toOption
        Then("the successful List contains the selected resource identity")
        withClue(s"List HTTP response: ${list.text}") {
          selected shouldBe defined
        }
        When("normal GET .json/envelope requests the selected Detail")
        val detail = session.http("read", "capture-detail", Some(org.simplemodeling.model.datatype.EntityId.parse(selected.get).TAKE))
        Then("all responses are UTF8 application/json envelopes whose data objects retain canonical metadata and stable order")
        Vector(first, list, detail).foreach { response =>
          withClue(s"HTTP response: ${response.text}") {
            response.status shouldBe 200
          }
          response.contentType shouldBe Some("application/json")
          response.data.isObject shouldBe true
          response.json.hcursor.downField("execution").get[String]("interface-shape").toOption shouldBe Some("json")
          response.data.hcursor.get[String]("componentId").toOption shouldBe Some(session.component.name)
          response.data.hcursor.get[String]("resourceType").toOption shouldBe Some(_fixture.resourceType)
          new String(response.bytes.toArray, StandardCharsets.UTF_8) shouldBe response.text
        }
        _list_resources(list.data) shouldBe rows.map(_.resource.entityId.value)
        _identity(detail.data, session, rows(1).resource.entityId.value, "19")
        session.component.browser.findCalls.map(_._1) shouldBe rows.map(_.resource.entityId)
        session.component.browser.queryCalls.size shouldBe 1
        session.component.browser.queryCalls.head._1 shouldBe Query.fromRecord(Record.empty)
        _idle_auxiliary(session)
        When("the independent semantic consumer queries the same canonical typed Browser")
        val semantic = session.component.browser.query_with_context(Query.fromRecord(Record.empty))(using ExecutionContext.create()).TAKE
        Then("the consumer receives original Capture values and needs no Display wire representation")
        semantic shouldBe rows
        _typed.semanticTitles(semantic) shouldBe rows.map(_.title)
      } finally session.close()
    }
  }

  "E3 Lossless public typed values and List shapes" should afterWord("in spec:display-model-runtime, example:E3, rules:DSP96-R01,DSP96-R02,SR3,R3,R4,R7, phase:96, slice:P96-05A") {
    "preserve empty reversed and five-kind snapshots through real HTTP response bytes" in {
      Given("independent all-kind declarations and exact whitespace Unicode surrogate coefficient scale and nanosecond cases")
      val text = " \t\n日本 Ω e\u0301 \ud83d\ude00\ud800\udfff\u0000 "
      val decimals = Vector(
        new java.math.BigDecimal(new java.math.BigInteger("12345678901234567890123456789012345000"), 7),
        new java.math.BigDecimal(java.math.BigInteger.ONE, Int.MinValue),
        new java.math.BigDecimal(java.math.BigInteger.ONE, Int.MaxValue))
      Vector(0, 2).foreach { size => Vector(false, true).foreach { reversed => decimals.foreach { decimal =>
        Given("a finite ordered or empty typed source with separately declared all-kind provenance")
        val original = Vector.tabulate(size)(index => _typed.captureC(index + 1, Long.MaxValue).TAKE.copy(
          description = text, number = BigDecimal(decimal), instant = Instant.ofEpochSecond(-1, 987654321)))
        val rows = if (reversed) original.reverse else original
        val session = _fixture.open(rows, allKinds = true)
        try {
          When("normal HTTP query and one selected read cross the typed JSON/envelope/UTF8 boundary")
          val list = session.http("query", "collecting-resources")
          val detail = rows.headOption.map(row => session.http("read", "capture-detail", Some(row.resource.entityId)))
          Then("arrays order exact scalar bytes and independent metadata survive with no count snapshot or eager Detail")
          withClue(s"List HTTP response: ${list.text}") {
            list.status shouldBe 200
          }
          _list_resources(list.data) shouldBe rows.map(_.resource.entityId.value)
          list.data.hcursor.downField("model").downField("items").focus.flatMap(_.asArray).map(_.size) shouldBe Some(size)
          list.data.hcursor.downField("model").downField("snapshot").focus shouldBe None
          list.data.hcursor.downField("provenance").get[String]("producerId").toOption shouldBe Some("cncf.public-read-all-kind-fixture")
          detail.foreach { response =>
            withClue(s"Detail HTTP response: ${response.text}") {
              response.status shouldBe 200
            }
            response.bytes.forall(byte => byte >= 0) shouldBe true
            val fields = response.data.hcursor.downField("model").downField("sections").downN(0)
              .downField("fields").focus.get.asArray.get
            fields(1).hcursor.downField("value").get[String]("text").toOption.get.toCharArray.toVector shouldBe text.toCharArray.toVector
            fields(2).hcursor.downField("value").get[String]("number").toOption shouldBe Some(decimal.toString)
            fields(3).hcursor.downField("value").get[Boolean]("boolean").toOption shouldBe Some(true)
            fields(4).hcursor.downField("value").get[String]("instant").toOption shouldBe Some("1969-12-31T23:59:59.987654321Z")
            fields(5).hcursor.downField("value").focus shouldBe Some(Json.obj("type" -> Json.fromString("missing")))
            val definitions = DisplayProtocolComponentFixture.admittedDefinitions(session.component).get
            val restored = DisplayProtocolCodec.decodeC(_source(session), definitions, response.data.noSpaces).TAKE
            restored.model match {
              case model: DisplayModel.DetailModel => model.sections.head.fields(2).value match {
                case DisplayValue.Number(value) =>
                  value.bigDecimal.unscaledValue shouldBe decimal.unscaledValue
                  value.bigDecimal.scale shouldBe decimal.scale
                case _ => fail("expected decimal typed value")
              }
              case _ => fail("expected restored Detail")
            }
          }
          session.component.browser.queryCalls.size shouldBe 1
          session.component.browser.findCalls.size shouldBe (if (size == 0) 0 else 1)
          _idle_auxiliary(session)
        } finally session.close()
      } } }
    }
  }

  "E4 Component resource and screen confinement" should afterWord("in spec:display-model-runtime, example:E4, rules:DSP96-R03,DSP96-R04,SR2,SR3, phase:96, slice:P96-05A") {
    "reject undeclared selectors and branch or resource mismatches without a wrong-source fallback" in {
      Given("two independently installed components with different canonical Browsers")
      val row = _typed.captureC(1, 7).TAKE
      val otherrow = _typed.captureC(2, 19).TAKE
      val session = _fixture.open(Vector(row))
      val other = new _fixture.ReadComponent(Vector(otherrow))
      _fixture.initialize(other, session.subsystem, name = "ForeignDisplayConsumer")
      new org.goldenport.cncf.component.ComponentFactory().bootstrapC(other).TAKE
      session.subsystem.add(Vector(other))
      try {
        val valid = session.request("read", "capture-detail", Some(row.resource.entityId))
        val cases = Vector(
          valid.copy(component = None), valid.copy(component = Some("org.goldenport.cncf.test.AbsentDisplay")),
          valid.copy(properties = valid.properties.filterNot(_.name == "resourceType")),
          valid.copy(properties = valid.properties.map(property => if (property.name == "resourceType") property.copy(value = "foreign") else property)),
          valid.copy(properties = valid.properties.map(property => if (property.name == "screenId") property.copy(value = "undeclared") else property)),
          session.request("read", "collecting-resources", Some(row.resource.entityId)),
          session.request("query", "capture-detail"))
        When("normal operation routing receives each missing unknown foreign or unsupported business selector")
        val results = cases.map(session.execute)
        Then("each request fails before any registered source is called")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        session.component.browser.findCalls shouldBe empty
        session.component.browser.queryCalls shouldBe empty
        other.browser.findCalls shouldBe empty
        other.browser.queryCalls shouldBe empty
        When("a canonical foreign resource identity is requested from this component and this identity is requested from the other component")
        val absent = session.execute(session.request("read", "capture-detail", Some(otherrow.resource.entityId)))
        val foreign = session.execute(_fixture.request(other, "read", "capture-detail", Some(row.resource.entityId)))
        Then("each owning Browser fails once and no component searches the other source")
        Vector(absent, foreign).foreach(_ shouldBe a[Consequence.Failure[?]])
        session.component.browser.findCalls.map(_._1) shouldBe Vector(otherrow.resource.entityId)
        other.browser.findCalls.map(_._1) shouldBe Vector(row.resource.entityId)
        _idle_auxiliary(session)
      } finally session.close()
    }
  }

  "E5 Raw request admission" should afterWord("in spec:display-model-runtime, example:E5, rules:SR2,DSP96-R04, phase:96, slice:P96-05A") {
    "reject null duplicate primitive kind canonical identity and arbitrary source or query control input before coercion" in {
      Given("a normally installed read Operation and its closed three-property input")
      val row = _typed.captureC(1, 7).TAKE
      val session = _fixture.open(Vector(row))
      try {
        val operation = _operation(session, "read")
        val valid = session.request("read", "capture-detail", Some(row.resource.entityId))
        val invalid = Vector[Request](null,
          valid.copy(component = None), valid.copy(component = Some("foreign")), valid.copy(service = None),
          valid.copy(service = Some("other")), valid.copy(operation = "query"),
          valid.copy(arguments = List(Argument("resourceId", row.resource.entityId.value))),
          valid.copy(switches = List(Switch("resourceType", true, None))),
          valid.copy(properties = null), valid.copy(properties = valid.properties :+ null),
          valid.copy(properties = valid.properties :+ Property(null, "value", None)),
          valid.copy(properties = valid.properties :+ valid.properties.head),
          valid.copy(properties = valid.properties.filterNot(_.name == "screenId"))) ++
          Vector(null, 7, true, Vector("capture-detail"), " capture-detail ", "").map(value =>
            valid.copy(properties = valid.properties.map(property => if (property.name == "screenId") property.copy(value = value) else property))) ++
          Vector("invalid", " " + row.resource.entityId.value, row.resource.entityId.value + " ").map(value =>
            valid.copy(properties = valid.properties.map(property => if (property.name == "resourceId") property.copy(value = value) else property))) ++
          Vector("view", "viewName", "converter", "path", "class", "aggregateOperation", "filter", "where", "limit", "offset", "includeTotal", "RESOURCEType").map(name =>
            valid.copy(properties = valid.properties :+ Property(name, "caller-selected", None)))
        When("the real bound Operation receives raw candidates before any datatype normalizer")
        val results = invalid.map(operation.createOperationRequest)
        val query = session.request("query", "collecting-resources")
        val publicfilter = session.execute(query.copy(properties = query.properties :+ Property("filter", "title=secret", None)))
        Then("typed redacted service diagnostics reject all raw candidates and normal filters also fail without source effects")
        results.foreach { result =>
          val diagnostic = _service_diagnostic(result)
          diagnostic.code should startWith("DSP96_SERVICE_")
          diagnostic.reason should not include "caller-selected"
          diagnostic.reason should not include row.resource.entityId.value
        }
        publicfilter shouldBe a[Consequence.Failure[?]]
        session.component.browser.findCalls shouldBe empty
        session.component.browser.queryCalls shouldBe empty
        When("only existing framework transport properties accompany a valid raw request")
        val admitted = operation.createOperationRequest(valid.copy(properties = valid.properties ++
          List(Property("textus.output.shape", "envelope", None), Property("cncf.security.authenticated", "true", None))))
        Then("transport metadata can be accepted without executing a source or acquiring authority")
        admitted shouldBe a[Consequence.Success[?]]
        session.component.browser.findCalls shouldBe empty
      } finally session.close()
    }
  }

  "E6 Ordinary descriptor-bound authorization" should afterWord("in spec:display-model-runtime, example:E6, rules:DSP96-R04,DSP96-R08,SR2,SR3, phase:96, slice:P96-05A") {
    "deny anonymous spoofing before conversion and forward authorized execution under its ordinary UnitOfWork" in {
      Given("production runtime policy and a descriptor requiring authenticated access, with an enabled inert Action")
      val row = _typed.captureC(1, 7).TAKE
      val session = _fixture.open(Vector(row), production = true)
      try {
        val request = session.request("read", "capture-detail", Some(row.resource.entityId))
        val spoofed = request.copy(properties = request.properties ++ List(
          Property("principalId", "spoof", None), Property("role", "system_admin", None),
          Property("cncf.security.authenticated", "true", None), Property("cncf.security.privilege", "system", None)))
        When("anonymous and spoofed ordinary requests attempt a read")
        val denied = Vector(session.execute(request), session.execute(spoofed))
        Then("ordinary security Failures stop before any source and never become service-input diagnostics")
        denied.foreach { result =>
          val failure = _failure(result)
          failure.conclusion.observation.cause.getException.exists(_.isInstanceOf[DisplayService.ServiceException]) shouldBe false
        }
        session.component.browser.findCalls shouldBe empty
        session.component.browser.queryCalls shouldBe empty
        Given("an explicitly authenticated caller execution context already admitted by the normal in-process boundary")
        val principal = new Principal {
          val id: PrincipalId = PrincipalId("admitted-reader")
          val attributes: Map[String, String] = Map("authenticated" -> "true", "role" -> "reader")
        }
        val context = ExecutionContext.withSecurityContext(session.component.logic.executionContext(),
          SecurityContext(principal, Set.empty, SecurityLevel("user"), SubjectKind.User))
        When("the authorized ordinary subsystem executes the same read in that context")
        val payload = _json(session.executeInContext(request, context))
        val observed = session.component.browser.findCalls.head._2
        Then("the read uses the admitted caller security and normal action context exactly once without action authority inference")
        _identity(payload, session, row.resource.entityId.value, "7")
        observed.security shouldBe context.security
        observed.operationMode shouldBe context.operationMode
        observed.runtime.unitOfWork should not be null
        session.component.browser.findCalls.size shouldBe 1
        _idle_auxiliary(session)
      } finally session.close()
    }
  }

  "E7 Canonical failure preservation" should afterWord("in spec:display-model-runtime, example:E7, rules:DSP96-R01,DSP96-R02,SR3, phase:96, slice:P96-05A") {
    "retain structured Browser and projector causes and fixed redacted NonFatal diagnostics without retries or partial data" in {
      Given("ordinary read sources and projection callbacks with separately defined structured or NonFatal outcomes")
      val row = _typed.captureC(1, 7).TAKE
      val sourcefailure = Consequence.serviceUnavailable[TypedDisplayProjectionFixture.Capture]("source-domain-rejection")
      val projectionfailure = Consequence.serviceUnavailable[Vector[DisplayValue]]("projection-domain-rejection")
      val rowbinding = _typed.detailRow.copy(values = Vector(
        DisplayProjection.ValueBinding(Vector("ess.title"), _ => projectionfailure), _typed.descriptionBinding, _typed.stageBinding))
      val throwbinding = _typed.detailRow.copy(values = Vector(
        DisplayProjection.ValueBinding(Vector("ess.title"), _ => throw new IllegalStateException("secret callback")), _typed.descriptionBinding, _typed.stageBinding))
      val sessions = Vector(
        _fixture.open(Vector(row), findSource = Some((_, _) => sourcefailure)),
        _fixture.open(Vector(row), detailRow = Some(rowbinding)),
        _fixture.open(Vector(row), findSource = Some((_, _) => throw new IllegalStateException("secret source"))),
        _fixture.open(Vector(row), detailRow = Some(throwbinding)),
        _fixture.open(Vector(row.copy(title = " "))))
      try {
        When("normal public reads encounter each source callback or final projection failure")
        val results = sessions.map(session => session.execute(session.request("read", "capture-detail", Some(row.resource.entityId))))
        Then("structured conclusions pass unchanged and NonFatal details remain redacted with one contextual find each")
        (_failure(results(0)).conclusion eq _failure(sourcefailure).conclusion) shouldBe true
        (_failure(results(1)).conclusion eq _failure(projectionfailure).conclusion) shouldBe true
        _failure(results(2)).conclusion.observation.cause.getException match {
          case Some(carrier: DisplayRuntime.BindingException) => carrier.diagnostic.code shouldBe "DSP96_RUNTIME_SOURCE_FAILED"
          case _ => fail("expected redacted runtime carrier")
        }
        _failure(results(3)).conclusion.observation.cause.getException match {
          case Some(carrier: DisplayProjection.ProjectionException) => carrier.diagnostic.code shouldBe "DSP96_PROJECTION_CALLBACK_FAILED"
          case _ => fail("expected redacted projection carrier")
        }
        _failure(results(4)).conclusion.observation.cause.getException.exists(_.isInstanceOf[Display.ValidationException]) shouldBe true
        results.foreach { result =>
          result shouldBe a[Consequence.Failure[?]]
          result.display should not include "secret source"
          result.display should not include "secret callback"
        }
        sessions.foreach { session =>
          session.component.browser.findCalls.size shouldBe 1
          session.component.browser.queryCalls shouldBe empty
          _idle_auxiliary(session)
        }
      } finally sessions.foreach(_.close())
    }
  }

  "E8 Typed codec and canonical operation correspondence" should afterWord("in spec:display-model-protocol, example:E8, rules:R1,R4,R6,R7,SR1,SR2,SR3, phase:96, slice:P96-05A") {
    "share the pure payload encoder null guards and only query/read normal resolution after a real public read" in {
      Given("a complete ordinary component installation and its exact adopted definitions")
      val row = _typed.captureC(1, 7).TAKE
      val session = _fixture.open(Vector(row))
      try {
        When("public read produces a typed JSON business object and the trusted decoder restores it")
        val payload = _json(session.execute(session.request("read", "capture-detail", Some(row.resource.entityId))))
        val definitions = DisplayProtocolComponentFixture.admittedDefinitions(session.component).get
        val display = DisplayProtocolCodec.decodeC(_source(session), definitions, payload.noSpaces).TAKE
        val typed = DisplayProtocolCodec.encodeJsonC(_source(session), display).TAKE
        val text = DisplayProtocolCodec.encodeC(_source(session), display).TAKE
        Then("typed and text encoders have exactly corresponding semantics without another source or admission")
        typed shouldBe payload
        io.circe.parser.parse(text).toOption shouldBe Some(payload)
        Given("source and Display guard candidates with exactly one null member")
        Vector((null, display), (_source(session), null)).foreach { case (source, model) =>
          When("typed and text encoders interpret the same null guard candidate")
          val typedresult = DisplayProtocolCodec.encodeJsonC(source, model)
          val textresult = DisplayProtocolCodec.encodeC(source, model)
          Then("both encoders preserve exactly the same typed wire diagnostic")
          _wire_diagnostic(typedresult) shouldBe
            _wire_diagnostic(textresult)
        }
        Given("the already-admitted effective operation definitions and their normal resolver")
        val resolver = OperationResolver.build(Vector(session.component))
        When("normal resolution interprets query/read followed by list/get operation names")
        val admitted = Vector("query", "read").map(name => resolver.resolveOperationDefinition(session.component.name + ".display." + name))
        val absent = Vector("list", "get").map(name => resolver.resolveOperationDefinition(session.component.name + ".display." + name))
        Then("only query/read resolve and the canonical service retains its operations default and source counts")
        admitted.foreach(result => result shouldBe defined)
        absent.foreach(result => result shouldBe None)
        val service = session.component.protocol.services.services.find(_.name == "display").get
        service.operations.operations.toVector.map(_.name) shouldBe Vector("query", "read")
        service.operations.operations.toVector.foreach { operation =>
          operation.response.isSpecified shouldBe true
          operation.response.isVoid shouldBe false
          operation.response.result shouldBe List(DataType.Named("OperationResponse"))
        }
        service.defaultOperation shouldBe None
        session.component.browser.findCalls.size shouldBe 1
        session.component.browser.queryCalls shouldBe empty
        _idle_auxiliary(session)
      } finally session.close()
    }
  }

  private def _json(result: Consequence[OperationResponse]): Json = result.TAKE match {
    case OperationResponse.Json(value) => value
    case _ => fail("normal Display operation must return typed JSON")
  }
  private def _source(session: DisplayServiceReadFixture.Session): DisplayProtocol.Source =
    DisplayProtocol.Source.createC(session.component.componentId, _fixture.resourceType).TAKE
  private def _operation(session: DisplayServiceReadFixture.Session, opname: String): org.goldenport.protocol.spec.OperationDefinition =
    session.component.protocol.services.services.find(_.name == "display").get.operations.operations.find(_.name == opname).get
  private def _failure(result: Consequence[?]): Consequence.Failure[?] = result match {
    case value: Consequence.Failure[?] => value
    case _ => fail("expected structured failure")
  }
  private def _service_diagnostic(result: Consequence[?]): DisplayService.Diagnostic = {
    val rejected = _failure(result)
    rejected.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
    rejected.conclusion.observation.cause.getException match {
      case Some(carrier: DisplayService.ServiceException) => carrier.diagnostic
      case _ => fail("expected typed service diagnostic")
    }
  }
  private def _wire_diagnostic(result: Consequence[?]): DisplayProtocol.Diagnostic =
    _failure(result).conclusion.observation.cause.getException match {
      case Some(carrier: DisplayProtocol.CodecException) => carrier.diagnostic
      case _ => fail("expected original wire diagnostic")
    }
  private def _list_resources(payload: Json): Vector[String] = payload.hcursor.downField("model").downField("items")
    .focus.get.asArray.get.map(_.hcursor.downField("resource").get[String]("entityId").toOption.get)
  private def _identity(payload: Json, session: DisplayServiceReadFixture.Session, identity: String, revision: String): Unit = {
    payload.hcursor.get[String]("componentId").toOption shouldBe Some(session.component.name)
    payload.hcursor.get[String]("resourceType").toOption shouldBe Some(_fixture.resourceType)
    payload.hcursor.downField("model").downField("resource").get[String]("entityId").toOption shouldBe Some(identity)
    payload.hcursor.downField("model").downField("resource").get[String]("revision").toOption shouldBe Some(revision)
  }
  private def _idle_auxiliary(session: DisplayServiceReadFixture.Session): Unit = {
    session.component.browser.contextFreeCalls shouldBe 0
    session.component.browser.countCalls shouldBe 0
  }
  private def _json_metadata(session: DisplayServiceReadFixture.Session, member: String): Option[Json] = {
    val definitions = DisplayProtocolComponentFixture.admittedDefinitions(session.component).get
    val display = _typed.detailC(definitions).TAKE.projectC(_typed.captureC(1, 7).TAKE).TAKE
    DisplayProtocolCodec.encodeJsonC(_source(session), display).TAKE.hcursor.downField(member).focus
  }
}
