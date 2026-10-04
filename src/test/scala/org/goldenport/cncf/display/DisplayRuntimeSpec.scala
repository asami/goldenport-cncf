package org.goldenport.cncf.display

import logicalui.{TypedDisplayProjectionFixture, TypedDisplayRuntimeFixture}
import logicalui.TypedDisplayProjectionFixture.Capture
import logicalui.TypedDisplayRuntimeFixture.{Badge, SourceBrowser}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.view.{Browser, ViewSpace}
import org.goldenport.observation.Taxonomy
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
final class DisplayRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import DisplayModel.*
  import DisplayProjection.{ActionBinding, RowBinding, ValueBinding}
  import GeneratedLogicalUiAbi.*

  private val _fixture = TypedDisplayProjectionFixture
  private val _runtime_fixture = TypedDisplayRuntimeFixture
  private val _definitions = _fixture.definitions

  private final case class BindingCase(bindings: Vector[DisplayRuntime.Binding], code: String, path: String)
  private final case class EqualBrowser(source: Capture) extends Browser[Capture] {
    def find(id: EntityId): Consequence[Capture] = Consequence.success(source)
    def query(q: Query[?]): Consequence[Vector[Capture]] = Consequence.success(Vector(source))
  }

  private def _success[A](result: Consequence[A]): A = {
    result match {
      case Consequence.Success(value) => value
      case _ => fail("typed fixture interpretation must succeed")
    }
  }
  private def _admit(definitions: Vector[Definition]): AdmittedDefinitions = {
    _success(GeneratedLogicalUiAbi.admitC(definitions))
  }
  private def _capture(index: Int, revision: Long): Capture = _success(_fixture.captureC(index, revision))
  private def _detail(display: Display): DetailModel = {
    display.model match {
      case model: DetailModel => model
      case _ => fail("expected Detail model")
    }
  }
  private def _list(display: Display): ListModel = {
    display.model match {
      case model: ListModel => model
      case _ => fail("expected List model")
    }
  }
  private def _failure(result: Consequence[?]): Consequence.Failure[?] = {
    result match {
      case rejected: Consequence.Failure[?] => rejected
      case _ => fail("a structured failure without partial runtime or Display is required")
    }
  }
  private def _diagnostic(result: Consequence[?]): DisplayRuntime.Diagnostic = {
    val rejected = _failure(result)
    rejected.conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
    rejected.conclusion.observation.cause.getException match {
      case Some(carrier: DisplayRuntime.BindingException) => carrier.diagnostic
      case _ => fail("expected typed runtime diagnostic")
    }
  }
  private def _expect(result: Consequence[?], code: String, path: String): Unit = {
    val diagnostic = _diagnostic(result)
    diagnostic.code shouldBe "DSP96_RUNTIME_" + code
    diagnostic.path shouldBe path
    diagnostic.reason should not be empty
  }
  private def _projection_diagnostic(result: Consequence[?]): DisplayProjection.Diagnostic = {
    _failure(result).conclusion.observation.cause.getException match {
      case Some(carrier: DisplayProjection.ProjectionException) => carrier.diagnostic
      case _ => fail("expected unchanged projection diagnostic")
    }
  }
  private def _registry(space: ViewSpace, browser: Browser[Capture]): DisplayRuntime.Registry = {
    _success(DisplayRuntime.bindC(_admit(_definitions), space, _runtime_fixture.bindings(browser)))
  }
  private def _bindings(browser: Browser[Capture], detailrow: RowBinding[Capture],
    listrow: RowBinding[Capture]): Vector[DisplayRuntime.Binding] = {
    Vector(DisplayRuntime.list("collecting-resources", "ess.reference", browser, listrow, Vector.empty),
      DisplayRuntime.detail("capture-detail", "ess.reference", browser, detailrow))
  }

  "Component-scoped contextual Display runtime" should {
    "typed semantic consumption" which {
      "R1 read an exact ESS Detail with the unchanged caller context and independently retain the Capture" in {
        Given("registered typed Capture Browser and complete admitted ESS bindings with no source execution")
        given ExecutionContext = ExecutionContext.create()
        val ctx = summon[ExecutionContext]
        val source = _capture(1, 7)
        val browser = _runtime_fixture.captureBrowser(Vector(source))
        val space = new ViewSpace
        _runtime_fixture.register(space, "capture-source", browser)
        val registry = _registry(space, browser)
        browser.findCalls shouldBe empty
        browser.queryCalls shouldBe empty
        val expected = _success(_fixture.detailC(_admit(_definitions)).flatMap(_.projectC(source)))
        When("the registry reads the resource and a semantic client independently reads the same Browser")
        val display = _success(registry.readC("capture-detail", source.resource.entityId))
        val independent = _success(browser.find_with_context(source.resource.entityId))
        Then("both reads retain typed identity and revision with exactly one contextual invocation for each request")
        display.definition shouldBe _definitions(1)
        display.model shouldBe expected.model
        _detail(display).resource shouldBe source.resource
        (independent eq source) shouldBe true
        _fixture.semanticSummary(independent) shouldBe _fixture.semanticSummary(source)
        browser.findCalls.map(_._1) shouldBe Vector.fill(2)(source.resource.entityId)
        browser.findCalls.foreach { case (id, context) =>
          (id eq source.resource.entityId) shouldBe true
          (context eq ctx) shouldBe true
        }
        browser.queryCalls shouldBe empty
        browser.contextFreeCalls shouldBe 0
        browser.countCalls shouldBe 0
      }

      "R2 query ordered rows and empty results then select a separate matching Detail without count or eager reads" in {
        Given("a contextual Browser returning typed rows in supplied order and an independently selectable empty query")
        given ExecutionContext = ExecutionContext.create()
        val ctx = summon[ExecutionContext]
        val rows = Vector(_capture(2, 19), _capture(1, 7))
        val query = Query("capture-order")
        val emptyquery = Query("empty-captures")
        val browser = new SourceBrowser[Capture](
          (id, _) => Consequence.success(rows.find(_.resource.entityId == id).getOrElse(rows.head)),
          (q, _) => Consequence.success(if (q eq emptyquery) Vector.empty else rows))
        val space = new ViewSpace
        _runtime_fixture.register(space, "named-capture", browser, named = true)
        val registry = _registry(space, browser)
        When("Display query and an independent semantic query interpret the same request")
        val display = _list(_success(registry.queryC("collecting-resources", query)))
        val independent = _success(browser.query_with_context(query))
        val readsafterlist = browser.findCalls.size
        val emptymodel = _list(_success(registry.queryC("collecting-resources", emptyquery)))
        Then("List contains only ordered resource items and a logical target while an empty semantic result remains valid")
        display.items.map(_.resource) shouldBe rows.map(_.resource)
        display.items.map(_.fields.head.value) shouldBe rows.map(row => DisplayValue.Text(row.title))
        display.detailTarget shouldBe DetailTarget("capture-detail")
        display.snapshot shouldBe None
        emptymodel.items shouldBe empty
        emptymodel.snapshot shouldBe None
        (independent eq rows) shouldBe true
        readsafterlist shouldBe 0
        browser.findCalls shouldBe empty
        browser.queryCalls.map(_._1) shouldBe Vector(query, query, emptyquery)
        browser.queryCalls.foreach { case (_, context) => (context eq ctx) shouldBe true }
        (browser.queryCalls.head._1 eq query) shouldBe true
        browser.countCalls shouldBe 0
        browser.contextFreeCalls shouldBe 0
        When("the first List item is selected for a separate Detail read")
        val detail = _detail(_success(registry.readC(display.detailTarget.screenId, display.items.head.resource.entityId)))
        Then("the selected Detail retains that item's exact resource and source revision")
        detail.resource shouldBe display.items.head.resource
        detail.sections.head.fields.head.value shouldBe DisplayValue.Text(rows.head.title)
        browser.findCalls.size shouldBe 1
        (browser.findCalls.head._2 eq ctx) shouldBe true
        browser.countCalls shouldBe 0
      }

      "R3 retain heterogeneous types and accept only identity-registered default named or explicitly shared handles" in {
        Given("ESS Capture and unrelated Badge sources registered through default and named typed handles")
        given ExecutionContext = ExecutionContext.create()
        val source = _capture(1, 11)
        val badge = Badge(_success(_fixture.resourceC(3, 23)), "  バッジ Ω  ")
        val browser = _runtime_fixture.captureBrowser(Vector(source))
        val badgebrowser = new SourceBrowser[Badge]((_, _) => Consequence.success(badge),
          (_, _) => Consequence.success(Vector(badge)))
        val badgedefinition = _definitions(1).copy(screenId = "badge-detail", viewBindingId = "badge-key",
          screen = DetailDefinition("Badge", Vector(SectionDefinition("badge", None,
            Vector(FieldDefinition("caption", "Caption", PresentationRole.Content,
              ValueDefinition("badge.caption", Vector(ValueKind.Text)))))), Vector.empty))
        val badgerow = RowBinding[Badge](value => Consequence.success(value.resource),
          Vector(ValueBinding[Badge](Vector("badge.caption"), value => Consequence.success(Vector(DisplayValue.Text(value.caption))))), Vector.empty)
        val space = new ViewSpace
        _runtime_fixture.register(space, "capture-default", browser)
        space.register("capture-default", "badge-named", badgebrowser)
        val admitted = _admit(_definitions :+ badgedefinition)
        val bindings = _runtime_fixture.bindings(browser) :+
          DisplayRuntime.detail("badge-detail", "badge-key", badgebrowser, badgerow)
        val equalregistered = EqualBrowser(source)
        val equivalent = EqualBrowser(source)
        val identityspace = new ViewSpace
        _runtime_fixture.register(identityspace, "equal", equalregistered)
        val foreignspace = new ViewSpace
        val foreign = _runtime_fixture.captureBrowser(Vector(source))
        _runtime_fixture.register(foreignspace, "foreign", foreign)
        val sharedspace = new ViewSpace
        _runtime_fixture.register(sharedspace, "shared", browser)
        When("heterogeneous bindings are admitted and each typed Detail is read without a generic value lookup")
        val registry = _success(DisplayRuntime.bindC(admitted, space, bindings.reverse))
        val capturemodel = _detail(_success(registry.readC("capture-detail", source.resource.entityId)))
        val badgemodel = _detail(_success(registry.readC("badge-detail", badge.resource.entityId)))
        val rejected = DisplayRuntime.bindC(_admit(_definitions), identityspace, _runtime_fixture.bindings(equivalent))
        val foreignresult = DisplayRuntime.bindC(_admit(_definitions), space, _runtime_fixture.bindings(foreign))
        val shared = DisplayRuntime.bindC(_admit(_definitions), sharedspace, _runtime_fixture.bindings(browser))
        Then("definition order and distinct typed values survive while equality and foreign ownership cannot substitute for membership")
        registry.screenIds shouldBe admitted.definitions.map(_.screenId)
        capturemodel.resource shouldBe source.resource
        badgemodel.resource shouldBe badge.resource
        badgemodel.sections.head.fields.head.value shouldBe DisplayValue.Text(badge.caption)
        equalregistered shouldBe equivalent
        (equalregistered eq equivalent) shouldBe false
        identityspace.containsBrowser(equalregistered) shouldBe true
        identityspace.containsBrowser(equivalent) shouldBe false
        space.containsBrowser(badgebrowser) shouldBe true
        space.containsBrowser(null) shouldBe false
        _expect(rejected, "SOURCE_NOT_REGISTERED", "bindings[0].browser")
        _expect(foreignresult, "SOURCE_NOT_REGISTERED", "bindings[0].browser")
        shared.isSuccess shouldBe true
        foreign.findCalls shouldBe empty
        foreign.queryCalls shouldBe empty
      }
    }

    "complete admission and total lookup" which {
      "R4 reject every malformed registration boundary deterministically without evaluating sources or conversions" in {
        Given("complete ESS definitions and counted typed resource value and availability callbacks")
        val source = _capture(1, 5)
        val browser = _runtime_fixture.captureBrowser(Vector(source))
        val space = new ViewSpace
        _runtime_fixture.register(space, "admission-source", browser)
        val admitted = _admit(_definitions)
        var callbacks = 0
        val detailrow = _fixture.detailRow.copy[Capture](resource = value => { callbacks += 1; Consequence.success(value.resource) },
          values = _fixture.detailRow.values.map(binding => binding.copy[Capture](convert = value => {
            callbacks += 1
            binding.convert(value)
          })), actions = Vector(ActionBinding[Capture]("ess.request-review", value => {
            callbacks += 1
            Consequence.success(value.availability)
          })))
        val valid = _bindings(browser, detailrow, _fixture.listRow)
        val cases = Vector(
          BindingCase(null, "ELEMENT_INVALID", "bindings"),
          BindingCase(Vector(null, valid(1)), "ELEMENT_INVALID", "bindings[0]"),
          BindingCase(Vector(valid.head, null), "ELEMENT_INVALID", "bindings[1]"),
          BindingCase(Vector.empty, "SCREEN_MISSING", "definitions[0].screenId"),
          BindingCase(Vector(valid.head), "SCREEN_MISSING", "definitions[1].screenId"),
          BindingCase(Vector(valid(1)), "SCREEN_MISSING", "definitions[0].screenId"),
          BindingCase(valid :+ valid.head, "SCREEN_DUPLICATE", "bindings[2].screenId"),
          BindingCase(valid :+ DisplayRuntime.detail("foreign-screen", "ess.reference", browser, detailrow),
            "SCREEN_UNDECLARED", "bindings[2].screenId"),
          BindingCase(Vector(DisplayRuntime.list(null, "ess.reference", browser, _fixture.listRow, Vector.empty), valid(1)),
            "ID_INVALID", "bindings[0].screenId"),
          BindingCase(Vector(DisplayRuntime.list(" collecting-resources ", "ess.reference", browser, _fixture.listRow, Vector.empty), valid(1)),
            "ID_INVALID", "bindings[0].screenId"),
          BindingCase(Vector(valid.head, DisplayRuntime.detail("capture-detail", "", browser, detailrow)),
            "ID_INVALID", "bindings[1].viewBindingId"),
          BindingCase(Vector(DisplayRuntime.list("collecting-resources", "ess.reference", null, _fixture.listRow, Vector.empty), valid(1)),
            "ELEMENT_INVALID", "bindings[0].browser"),
          BindingCase(Vector(valid.head, DisplayRuntime.detail("capture-detail", "ess.reference", null, detailrow)),
            "ELEMENT_INVALID", "bindings[1].browser"))
        val projectioncases = Vector(
          DisplayRuntime.detail("collecting-resources", "ess.reference", browser, detailrow),
          DisplayRuntime.detail("capture-detail", "different-key", browser, detailrow),
          DisplayRuntime.detail("capture-detail", "ess.reference", browser, null),
          DisplayRuntime.detail("capture-detail", "ess.reference", browser, detailrow.copy[Capture](values = Vector.empty)),
          DisplayRuntime.detail("capture-detail", "ess.reference", browser,
            detailrow.copy[Capture](values = Vector(ValueBinding[Capture](Vector("undeclared"), _ => { callbacks += 1; Consequence.success(Vector.empty) })))),
          DisplayRuntime.detail("capture-detail", "ess.reference", browser, detailrow.copy[Capture](actions = Vector.empty)),
          DisplayRuntime.detail("capture-detail", "ess.reference", browser,
            detailrow.copy[Capture](actions = Vector(ActionBinding[Capture]("undeclared", _ => { callbacks += 1; Consequence.success(ActionAvailability.Enabled) })))))
        val listaction = DisplayRuntime.list("collecting-resources", "ess.reference", browser, _fixture.listRow,
          Vector(ActionBinding[Vector[Capture]]("undeclared", _ => { callbacks += 1; Consequence.success(ActionAvailability.Enabled) })))
        When("the whole candidate vectors and inherited B1 branch key converter and action gates are interpreted")
        val nullroots = Vector(DisplayRuntime.bindC(null, space, valid), DisplayRuntime.bindC(admitted, null, valid))
        val results = cases.map(input => (input, DisplayRuntime.bindC(admitted, space, input.bindings),
          DisplayRuntime.bindC(admitted, space, input.bindings)))
        val inherited = projectioncases.zipWithIndex.map { case (binding, index) =>
          val candidates = if (index == 0) Vector(binding, valid(1)) else Vector(valid.head, binding)
          DisplayRuntime.bindC(admitted, space, candidates)
        }
        val listrejection = DisplayRuntime.bindC(admitted, space, Vector(listaction, valid(1)))
        val expected = Vector(
          DisplayProjection.detailC(admitted, "collecting-resources", "ess.reference", detailrow),
          DisplayProjection.detailC(admitted, "capture-detail", "different-key", detailrow),
          DisplayProjection.detailC[Capture](admitted, "capture-detail", "ess.reference", null),
          DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", detailrow.copy[Capture](values = Vector.empty)),
          DisplayProjection.detailC(admitted, "capture-detail", "ess.reference",
            detailrow.copy[Capture](values = Vector(ValueBinding[Capture](Vector("undeclared"), _ => Consequence.success(Vector.empty))))),
          DisplayProjection.detailC(admitted, "capture-detail", "ess.reference", detailrow.copy[Capture](actions = Vector.empty)),
          DisplayProjection.detailC(admitted, "capture-detail", "ess.reference",
            detailrow.copy[Capture](actions = Vector(ActionBinding[Capture]("undeclared", _ => Consequence.success(ActionAvailability.Enabled))))))
        Then("every shape or mapping failure is stable and no valid prefix becomes a registry or evaluates an application callback")
        _expect(nullroots.head, "ELEMENT_INVALID", "definitions")
        _expect(nullroots(1), "ELEMENT_INVALID", "viewSpace")
        results.foreach { case (input, first, repeated) =>
          _expect(first, input.code, input.path)
          _diagnostic(first) shouldBe _diagnostic(repeated)
        }
        inherited.zip(expected).foreach { case (actual, direct) =>
          _projection_diagnostic(actual) shouldBe _projection_diagnostic(direct)
        }
        _projection_diagnostic(listrejection).code shouldBe "DSP96_PROJECTION_BINDING_UNDECLARED"
        callbacks shouldBe 0
        browser.findCalls shouldBe empty
        browser.queryCalls shouldBe empty
        browser.countCalls shouldBe 0
      }

      "R5 reject malformed unknown wrong-branch and null request inputs without executing any source" in {
        Given("a complete component-local registry and valid opaque resource and semantic query")
        val source = _capture(1, 5)
        val browser = _runtime_fixture.captureBrowser(Vector(source))
        val space = new ViewSpace
        _runtime_fixture.register(space, "lookup-source", browser)
        val registry = _registry(space, browser)
        val context = ExecutionContext.create()
        val query = Query("all")
        val id = source.resource.entityId
        When("read and query interpret invalid selection and request shapes")
        val screens = Vector(null, "", " capture-detail", "capture-detail ").map(screen =>
          registry.readC(screen, id)(using context))
        val queryscreens = Vector(null, "", " collecting-resources", "collecting-resources ").map(screen =>
          registry.queryC(screen, query)(using context))
        val unknownread = registry.readC("unknown", id)(using context)
        val unknownquery = registry.queryC("unknown", query)(using context)
        val listread = registry.readC("collecting-resources", id)(using context)
        val detailquery = registry.queryC("capture-detail", query)(using context)
        val nullid = registry.readC("capture-detail", null)(using context)
        val nullquery = registry.queryC("collecting-resources", null)(using context)
        val nullreadcontext = registry.readC("capture-detail", id)(using null)
        val nullquerycontext = registry.queryC("collecting-resources", query)(using null)
        Then("exact local lookup and input diagnostics precede every Browser call with no cross-component fallback")
        screens.foreach(result => _expect(result, "ID_INVALID", "screenId"))
        queryscreens.foreach(result => _expect(result, "ID_INVALID", "screenId"))
        _expect(unknownread, "SCREEN_NOT_FOUND", "screenId")
        _expect(unknownquery, "SCREEN_NOT_FOUND", "screenId")
        _expect(listread, "OPERATION_UNSUPPORTED", "screenId")
        _expect(detailquery, "OPERATION_UNSUPPORTED", "screenId")
        _expect(nullid, "ELEMENT_INVALID", "resourceId")
        _expect(nullquery, "ELEMENT_INVALID", "query")
        _expect(nullreadcontext, "ELEMENT_INVALID", "context")
        _expect(nullquerycontext, "ELEMENT_INVALID", "context")
        browser.findCalls shouldBe empty
        browser.queryCalls shouldBe empty
        browser.contextFreeCalls shouldBe 0
        browser.countCalls shouldBe 0
      }
    }

    "source and projection failures" which {
      "R6 preserve contextual denial and failure causes redact NonFatal exceptions and pass projector diagnostics unchanged" in {
        Given("typed contextual sources failing at read or query before counted conversion callbacks")
        given ExecutionContext = ExecutionContext.create()
        val source = _capture(1, 3)
        val sentinel = new IllegalStateException("private application source payload")
        val denied: Consequence[Capture] = Consequence.securityPermissionDenied("private source denial")
        val deniedrows: Consequence[Vector[Capture]] = Consequence.securityPermissionDenied("private source denial")
        val failed: Consequence[Capture] = Consequence.fail(Taxonomy.argumentInvalid, sentinel)
        var callbacks = 0
        val row = _fixture.detailRow.copy[Capture](resource = value => { callbacks += 1; Consequence.success(value.resource) })
        val browsers = Vector(
          new SourceBrowser[Capture]((_, _) => denied, (_, _) => deniedrows),
          new SourceBrowser[Capture]((_, _) => throw sentinel, (_, _) => throw sentinel),
          new SourceBrowser[Capture]((_, _) => null, (_, _) => null),
          new SourceBrowser[Capture]((_, _) => Consequence.success(null), (_, _) => Consequence.success(null)),
          new SourceBrowser[Capture]((_, _) => Consequence.success(source), (_, _) => Consequence.success(Vector(null))))
        val registries = browsers.zipWithIndex.map { case (browser, index) =>
          val space = new ViewSpace
          _runtime_fixture.register(space, "failure:" + index, browser)
          _success(DisplayRuntime.bindC(_admit(_definitions), space, _bindings(browser, row, _fixture.listRow)))
        }
        val projectorfailure = _fixture.detailRow.copy[Capture](values = Vector(
          ValueBinding[Capture](Vector("ess.title"), _ => failed.map(_ => Vector.empty)),
          _fixture.stageBinding, _fixture.descriptionBinding))
        val healthybrowser = _runtime_fixture.captureBrowser(Vector(source))
        val healthyspace = new ViewSpace
        _runtime_fixture.register(healthyspace, "projector-failure", healthybrowser)
        val failingregistry = _success(DisplayRuntime.bindC(_admit(_definitions), healthyspace,
          _bindings(healthybrowser, projectorfailure, _fixture.listRow)))
        var latercallbacks = 0
        val throwingrow = _fixture.detailRow.copy[Capture](resource = _ => throw sentinel,
          values = _fixture.detailRow.values.map(binding => binding.copy[Capture](convert = value => {
            latercallbacks += 1
            binding.convert(value)
          })))
        val throwingregistry = _success(DisplayRuntime.bindC(_admit(_definitions), healthyspace,
          _bindings(healthybrowser, throwingrow, _fixture.listRow)))
        val failedbrowser = new SourceBrowser[Capture]((_, _) => failed, (_, _) => failed.map(value => Vector(value)))
        val failedspace = new ViewSpace
        _runtime_fixture.register(failedspace, "structured-failure", failedbrowser)
        val failedregistry = _registry(failedspace, failedbrowser)
        val control = new scala.util.control.ControlThrowable {}
        val fatal = new LinkageError("fatal source boundary")
        val escaping = Vector[Throwable](control, fatal).zipWithIndex.map { case (exception, index) =>
          val browser = new SourceBrowser[Capture]((_, _) => throw exception, (_, _) => throw exception)
          val space = new ViewSpace
          _runtime_fixture.register(space, "escaping:" + index, browser)
          (_registry(space, browser), browser)
        }
        When("each source is invoked once through the registry and projection is allowed to retain its own failure policy")
        val reads = registries.take(4).map(_.readC("capture-detail", source.resource.entityId))
        val queries = registries.map(_.queryC("collecting-resources", Query("all")))
        val callbackfailure = failingregistry.readC("capture-detail", source.resource.entityId)
        val thrownprojection = throwingregistry.readC("capture-detail", source.resource.entityId)
        val sourcefailure = failedregistry.readC("capture-detail", source.resource.entityId)
        val escapedcontrol = intercept[scala.util.control.ControlThrowable] {
          escaping.head._1.readC("capture-detail", source.resource.entityId)
        }
        val escapedfatal = intercept[LinkageError] { escaping(1)._1.queryC("collecting-resources", Query("all")) }
        Then("original Conclusions survive and fixed redacted diagnostics do not permit retry or later conversion")
        _failure(reads.head).conclusion shouldBe _failure(denied).conclusion
        _failure(queries.head).conclusion shouldBe _failure(deniedrows).conclusion
        _failure(sourcefailure).conclusion shouldBe _failure(failed).conclusion
        _failure(sourcefailure).conclusion.observation.cause.getException shouldBe Some(sentinel)
        _failure(callbackfailure).conclusion shouldBe _failure(failed).conclusion
        _failure(callbackfailure).conclusion.observation.cause.getException shouldBe Some(sentinel)
        _projection_diagnostic(thrownprojection).code shouldBe "DSP96_PROJECTION_CALLBACK_FAILED"
        _projection_diagnostic(thrownprojection).path shouldBe "model.resource"
        _projection_diagnostic(thrownprojection).reason shouldBe "callback did not return normally"
        latercallbacks shouldBe 0
        Vector(reads(1), queries(1)).foreach { result =>
          _expect(result, "SOURCE_FAILED", "source")
          _diagnostic(result).reason shouldBe "contextual source did not return normally"
          _diagnostic(result).reason should not include sentinel.getMessage
        }
        Vector(reads(2), queries(2)).foreach(result => _expect(result, "ELEMENT_INVALID", "source"))
        _projection_diagnostic(reads(3)).code shouldBe "DSP96_PROJECTION_ELEMENT_INVALID"
        _projection_diagnostic(reads(3)).path shouldBe "source"
        _projection_diagnostic(queries(3)).path shouldBe "source"
        _projection_diagnostic(queries(4)).path shouldBe "source[0]"
        callbacks shouldBe 0
        browsers.zipWithIndex.foreach { case (browser, index) =>
          browser.findCalls.size shouldBe (if (index < 4) 1 else 0)
          browser.queryCalls.size shouldBe 1
          browser.contextFreeCalls shouldBe 0
          browser.countCalls shouldBe 0
        }
        healthybrowser.findCalls.size shouldBe 2
        failedbrowser.findCalls.size shouldBe 1
        escapedcontrol shouldBe control
        escapedfatal shouldBe fatal
        escaping.head._2.findCalls.size shouldBe 1
        escaping(1)._2.queryCalls.size shouldBe 1
      }

      "R7 reject unrelated find identity and preserve the requested resource revision and inert availability" in {
        Given("a faulty Browser returning another resource and a lawful source at the requested identity")
        given ExecutionContext = ExecutionContext.create()
        val requested = _capture(1, 97)
        val unrelated = _capture(2, 101)
        val faulty = new SourceBrowser[Capture]((_, _) => Consequence.success(unrelated), (_, _) => Consequence.success(Vector(unrelated)))
        val faultyspace = new ViewSpace
        _runtime_fixture.register(faultyspace, "faulty", faulty)
        val faultyregistry = _registry(faultyspace, faulty)
        val good = _runtime_fixture.captureBrowser(Vector(requested))
        val goodspace = new ViewSpace
        _runtime_fixture.register(goodspace, "good", good)
        val registry = _registry(goodspace, good)
        When("each Browser answers the same requested resource and the lawful source is read repeatedly")
        val mismatch = faultyregistry.readC("capture-detail", requested.resource.entityId)
        val first = _detail(_success(registry.readC("capture-detail", requested.resource.entityId)))
        val second = _detail(_success(registry.readC("capture-detail", requested.resource.entityId)))
        Then("unrelated resources cannot escape and actions remain data without changing the source revision or executing commands")
        _expect(mismatch, "SOURCE_ID_MISMATCH", "model.resource.entityId")
        first shouldBe second
        first.resource shouldBe requested.resource
        first.actions.head.availability shouldBe ActionAvailability.Enabled
        first.actions.head.bindingId shouldBe "ess.request-review"
        faulty.findCalls.size shouldBe 1
        good.findCalls.size shouldBe 2
        good.queryCalls shouldBe empty
        good.contextFreeCalls shouldBe 0
        good.countCalls shouldBe 0
        _fixture.semanticSummary(requested) shouldBe _fixture.semanticSummary(_capture(1, 97))
      }

      "R8 close public typed binding construction while a real external typed fixture produces Display" in {
        Given("an external typed Capture fixture with matching registered Browser and RowBinding")
        given ExecutionContext = ExecutionContext.create()
        val source = _capture(1, 13)
        val browser = _runtime_fixture.captureBrowser(Vector(source))
        val space = new ViewSpace
        _runtime_fixture.register(space, "external", browser)
        When("the external compile probes reject mismatched type and construction escapes and the valid binding is read")
        _runtime_fixture.verifyTypeBoundary()
        val registry = _registry(space, browser)
        val display = _success(registry.readC("capture-detail", source.resource.entityId))
        Then("the valid typed path returns observable Display while arbitrary subclasses Registry constructors and copy bypasses are closed")
        _detail(display).resource shouldBe source.resource
        _detail(display).sections.head.fields.head.value shouldBe DisplayValue.Text(source.title)
        browser.findCalls.size shouldBe 1
        browser.contextFreeCalls shouldBe 0
      }
    }

    "generated component laws" which {
      "RP1 preserve definition and row order Unicode revisions independent semantics and component isolation in at least one hundred cases" in {
        Given("finite registration and row permutations plus empty rows Unicode payloads revisions and distinct component data")
        val input = for {
          count <- Gen.choose(0, 5)
          order <- Gen.oneOf(Vector.tabulate(count)(index => index).permutations.toVector)
          registration <- Gen.oneOf(Vector(0, 1).permutations.toVector)
          definitionorder <- Gen.oneOf(Vector(0, 1).permutations.toVector)
          chars <- Gen.listOf(Gen.oneOf('a', 'Ω', '日', 'é', '\u0301', ' ', '\t', '\n'))
          revision <- Gen.choose(1L, 1000000L)
        } yield (order, registration, definitionorder, chars.mkString, revision)
        val context = ExecutionContext.create()
        When("two independent registered components repeatedly query and read their generated typed data")
        val property = Prop.forAll(input) { case (order, registration, definitionorder, text, revision) =>
          val rows = order.map(index => _capture(index + 1, revision).copy(title = "prefix:" + text + ":" + index,
            description = text))
          val otherrows = rows.map(source => source.copy(title = "other:" + source.title))
          val browser = _runtime_fixture.captureBrowser(rows)
          val otherbrowser = _runtime_fixture.captureBrowser(otherrows)
          val firstspace = new ViewSpace
          val secondspace = new ViewSpace
          _runtime_fixture.register(firstspace, "first", browser)
          _runtime_fixture.register(secondspace, "second", otherbrowser, named = true)
          val admitted = _admit(definitionorder.map(_definitions))
          val firstbindings = _runtime_fixture.bindings(browser)
          val otherbindings = _runtime_fixture.bindings(otherbrowser)
          val first = _success(DisplayRuntime.bindC(admitted, firstspace, registration.map(firstbindings)))
          val second = _success(DisplayRuntime.bindC(admitted, secondspace, registration.reverse.map(otherbindings)))
          val query = Query("same-query:" + text)
          val list = _list(_success(first.queryC("collecting-resources", query)(using context)))
          val repeated = _list(_success(first.queryC("collecting-resources", query)(using context)))
          val otherlist = _list(_success(second.queryC("collecting-resources", query)(using context)))
          val semantic = _success(browser.query_with_context(query)(using context))
          val details = rows.map { source =>
            val model = _detail(_success(first.readC("capture-detail", source.resource.entityId)(using context)))
            val independent = _success(browser.find_with_context(source.resource.entityId)(using context))
            model.resource == source.resource && model.sections.head.fields.head.value == DisplayValue.Text(source.title) &&
              (independent eq source) && _fixture.semanticSummary(independent) == _fixture.semanticSummary(source)
          }
          val foreign = DisplayRuntime.bindC(admitted, firstspace, otherbindings)
          first.screenIds == admitted.definitions.map(_.screenId) && second.screenIds == first.screenIds &&
            list == repeated && list.snapshot.isEmpty && otherlist.snapshot.isEmpty &&
            list.items.map(_.resource) == rows.map(_.resource) &&
            list.items.map(_.fields.head.value) == rows.map(source => DisplayValue.Text(source.title)) &&
            otherlist.items.map(_.fields.head.value) == otherrows.map(source => DisplayValue.Text(source.title)) &&
            (semantic eq rows) && details.forall(value => value) &&
            _diagnostic(foreign).code == "DSP96_RUNTIME_SOURCE_NOT_REGISTERED" &&
            browser.findCalls.size == rows.size * 2 && browser.queryCalls.size == 3 &&
            otherbrowser.queryCalls.size == 1 && otherbrowser.findCalls.isEmpty &&
            browser.findCalls.forall(_._2 eq context) && browser.queryCalls.forall(call => (call._1 eq query) && (call._2 eq context)) &&
            browser.contextFreeCalls == 0 && otherbrowser.contextFreeCalls == 0 && browser.countCalls == 0 && otherbrowser.countCalls == 0
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least one hundred successful undiscarded cases retain deterministic models and isolated semantic sources")
        withClue(s"RP1: $result: ") {
          result.passed shouldBe true
          result.succeeded should be >= 100
        }
      }
    }
  }
}
