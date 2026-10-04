package org.goldenport.cncf.component

import domain.logicalui.DomainLogicalUiDefinitions
import logicalui.{ExpectedGeneratedLogicalUiFixture, GeneratedDisplayConsumerFixture, TypedDisplayProjectionFixture, TypedDisplayRuntimeFixture}
import logicalui.GeneratedDisplayConsumerFixture.{ActualComponent, ConsumerComponent, ExpectedComponent, FallbackFactory}
import logicalui.TypedDisplayProjectionFixture.{Capture, CaptureStatus}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.display.{Display, DisplayModel, DisplayProjection, DisplayRuntime, DisplayValue, GeneratedLogicalUiAbi}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.Protocol
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
final class ComponentFactoryGeneratedLogicalUiAcceptanceSpec
  extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedLogicalUiAbi.*
  import DisplayModel.*

  private val _definitions = DomainLogicalUiDefinitions.definitions
  private val _expected = ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions
  private val _fixture = TypedDisplayProjectionFixture

  "Actual Cozy generated Logical UI consumption" should {
    "normal bootstrap and contextual semantic correspondence" which {
      "GC1 publish the complete actual delivery once and preserve exact List to Detail semantics" in {
        Given("the unmodified two-file producer delivery and explicit typed ESS application bindings")
        _with_subsystem { subsystem =>
          given ExecutionContext = ExecutionContext.create()
          val rows = Vector(_success(_fixture.captureC(1, 7)), _success(_fixture.captureC(2, 19)))
          val component = _initialize(subsystem, new ActualComponent(rows))
          When("normal ComponentFactory bootstrap admits metadata and joins the complete typed binding vector")
          val result = new ComponentFactory().bootstrapC(component)
          Then("one complete source-ordered registry retains actual definition identities without evaluating its source")
          (_success(result) eq component) shouldBe true
          _published(component, _definitions)
          _idle(component)
          val query = Query("all actual resources")
          When("the caller queries List and then selects its first resource for a separate Detail read")
          val listdisplay = _success(_registry(component).queryC("collecting-resources", query))
          val list = _list(listdisplay)
          val detaildisplay = _success(_registry(component).readC(list.detailTarget.screenId,
            list.items.head.resource.entityId))
          Then("exact values roles field order resource identities revisions and inert actions follow the actual definitions")
          _check_list(listdisplay, rows)
          _check_detail(detaildisplay, rows.head)
          (listdisplay.definition eq _definitions.head) shouldBe true
          (detaildisplay.definition eq _definitions(1)) shouldBe true
          _calls(component, query, rows.take(1), summon[ExecutionContext])
        }
      }

      "GC2 equate semantic models while retaining independently asserted actual and expected provenance" in {
        Given("actual and handwritten expected providers with identical Capture inputs and independent typed handles")
        _with_subsystem { subsystem =>
          given ExecutionContext = ExecutionContext.create()
          val rows = Vector(_success(_fixture.captureC(1, 11)))
          val actual = _initialize(subsystem, new ActualComponent(rows))
          val expected = _initialize(subsystem, new ExpectedComponent(rows))
          val query = Query("same semantic input")
          When("both providers use normal bootstrap followed by the same contextual List and Detail requests")
          val factory = new ComponentFactory()
          val actualboot = factory.bootstrapC(actual)
          val expectedboot = factory.bootstrapC(expected)
          val actuallist = _success(_registry(actual).queryC("collecting-resources", query))
          val expectedlist = _success(_registry(expected).queryC("collecting-resources", query))
          val actualdetail = _success(_registry(actual).readC("capture-detail", rows.head.resource.entityId))
          val expecteddetail = _success(_registry(expected).readC("capture-detail", rows.head.resource.entityId))
          Then("models agree structurally while ABI semantic contract screen revision binding and screen agree separately from provenance")
          actualboot.isSuccess shouldBe true
          expectedboot.isSuccess shouldBe true
          _published(actual, _definitions)
          _published(expected, _expected)
          (_installed(actual).browser eq _installed(expected).browser) shouldBe false
          actuallist.model shouldBe expectedlist.model
          actualdetail.model shouldBe expecteddetail.model
          _definition_correspondence()
          _check_list(actuallist, rows)
          _check_detail(actualdetail, rows.head)
          _calls(actual, query, rows, summon[ExecutionContext])
          _calls(expected, query, rows, summon[ExecutionContext])
        }
      }

      "GC3 preserve Unicode whitespace reverse order and empty Lists alongside independent semantic consumption" in {
        Given("Unicode and whitespace Capture values supplied in reverse semantic row order and a separate empty source")
        _with_subsystem { subsystem =>
          val context = ExecutionContext.create()
          given ExecutionContext = context
          val first = _success(_fixture.captureC(1, 13)).copy(title = "  日本語 Ω e\u0301  ",
            description = "\t説明\n  ", status = CaptureStatus.Held, availability = ActionAvailability.Hidden)
          val second = _success(_fixture.captureC(2, 23)).copy(title = "\tβ 捕捉  ",
            description = "  détail\n\t", status = CaptureStatus.Ready, availability = ActionAvailability.Disabled)
          val rows = Vector(second, first)
          val component = _initialize(subsystem, new ActualComponent(rows))
          val emptycomponent = _initialize(subsystem, new ActualComponent(Vector.empty))
          val query = Query("supplied reverse order")
          val emptyquery = Query("empty semantic source")
          When("normal bootstrap publishes both sources and contextual runtime query and read consume each requested input once")
          _success(new ComponentFactory().bootstrapC(component))
          _success(new ComponentFactory().bootstrapC(emptycomponent))
          val listdisplay = _success(_registry(component).queryC("collecting-resources", query))
          val details = rows.map(row => _success(_registry(component).readC("capture-detail", row.resource.entityId)))
          val emptydisplay = _success(_registry(emptycomponent).queryC("collecting-resources", emptyquery))
          Then("supplied bytes and order remain exact with no snapshot count context-free call or eager Detail")
          _check_list(listdisplay, rows)
          details.zip(rows).foreach { case (display, row) => _check_detail(display, row) }
          _check_list(emptydisplay, Vector.empty)
          _calls(component, query, rows, context)
          _calls(emptycomponent, emptyquery, Vector.empty, context)
          val browser = _installed(component).browser
          val priorqueries = browser.queryCalls.size
          val priorreads = browser.findCalls.size
          When("a semantic client independently consumes the same typed Browser with the same caller inputs")
          val semanticrows = _success(browser.query_with_context(query))
          val semanticdetail = _success(browser.find_with_context(first.resource.entityId))
          Then("typed Capture data is unchanged and direct semantic calls are counted separately from runtime calls")
          (semanticrows eq rows) shouldBe true
          semanticrows shouldBe rows
          semanticdetail shouldBe first
          _fixture.semanticTitles(semanticrows) shouldBe rows.map(_.title)
          _fixture.semanticSummary(semanticdetail) shouldBe (first.title, first.status, first.number)
          browser.queryCalls.size shouldBe priorqueries + 1
          browser.findCalls.size shouldBe priorreads + 1
          (browser.queryCalls.last._1 eq query) shouldBe true
          (browser.queryCalls.last._2 eq context) shouldBe true
          browser.findCalls.last._1 shouldBe first.resource.entityId
          (browser.findCalls.last._2 eq context) shouldBe true
          browser.contextFreeCalls shouldBe 0
          browser.countCalls shouldBe 0
        }
      }
    }

    "component lifetime and fail-closed publication" which {
      "GC4 retain successful object identities and isolate sources resources and revisions across components" in {
        Given("actual providers with identical generated screen IDs but distinct source handles resources and revisions")
        _with_subsystem { subsystem =>
          given ExecutionContext = ExecutionContext.create()
          val firstrow = _success(_fixture.captureC(1, 17)).copy(title = "first component")
          val secondrow = _success(_fixture.captureC(2, 31)).copy(title = "second component")
          val first = _initialize(subsystem, new ActualComponent(Vector(firstrow)))
          val second = _initialize(subsystem, new ActualComponent(Vector(secondrow)))
          val factory = new ComponentFactory()
          When("normal bootstrap publishes each independent component")
          _success(factory.bootstrapC(first))
          _success(factory.bootstrapC(second))
          Then("both sources remain idle and their complete registry objects are distinct")
          _published(first, _definitions)
          _published(second, _definitions)
          _idle(first)
          _idle(second)
          val storedruntime = first.displayRuntime
          val storedmetadata = first._admitted_logical_ui_definitions
          val registry = _registry(first)
          val admitted = _admitted(first)
          val secondregistry = _registry(second)
          (registry eq secondregistry) shouldBe false
          (_installed(first).browser eq _installed(second).browser) shouldBe false
          When("successful bootstrap is requested again before any source acquisition")
          val repeated = factory.bootstrapC(first)
          Then("Option registry admitted wrapper vector and definitions retain identity without either provider reread or source execution")
          (_success(repeated) eq first) shouldBe true
          (first.displayRuntime eq storedruntime) shouldBe true
          (first._admitted_logical_ui_definitions eq storedmetadata) shouldBe true
          (_registry(first) eq registry) shouldBe true
          (_admitted(first) eq admitted) shouldBe true
          (_admitted(first).definitions eq _definitions) shouldBe true
          _published(first, _definitions)
          _idle(first)
          When("each registry reads its own contextual resource")
          val firstdisplay = _success(registry.readC("capture-detail", firstrow.resource.entityId))
          val seconddisplay = _success(secondregistry.readC("capture-detail", secondrow.resource.entityId))
          Then("neither component substitutes or alters the other's resource values revision or registry")
          _check_detail(firstdisplay, firstrow)
          _check_detail(seconddisplay, secondrow)
          firstrow.resource.entityId should not be secondrow.resource.entityId
          firstrow.resource.revision should not be secondrow.resource.revision
          (_registry(first) eq registry) shouldBe true
          (_registry(second) eq secondregistry) shouldBe true
          first.metadataReads shouldBe 1
          second.metadataReads shouldBe 1
          first.bindingReads shouldBe 1
          second.bindingReads shouldBe 1
          _installed(first).browser.findCalls.map(_._1) shouldBe Vector(firstrow.resource.entityId)
          _installed(second).browser.findCalls.map(_._1) shouldBe Vector(secondrow.resource.entityId)
        }
      }

      "GC5 preserve authoritative ABI diagnostics for modified actual vectors without fallback or publication" in {
        Given("fixture-only ABI duplicate revision and target variants derived from the actual generated vector")
        _with_subsystem { subsystem =>
          GeneratedDisplayConsumerFixture.invalidDefinitions.foreach { case (label, input, code, path) =>
            Given(s"$label with a valid shadowed factory and counted selected metadata and runtime getters")
            val fallback = new FallbackFactory()
            val component = _initialize(subsystem, new ActualComponent(Vector.empty,
              definitionTransform = _ => input), Some(fallback))
            When("normal bootstrap and authoritative direct admission interpret the same complete invalid actual-derived vector")
            val direct = GeneratedLogicalUiAbi.admitC(input)
            val result = new ComponentFactory().bootstrapC(component)
            Then("the same typed taxonomy code path and reason reject all Display publication after exactly one metadata read")
            _failure(result).conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
            _failure(result).conclusion.observation.taxonomy shouldBe _failure(direct).conclusion.observation.taxonomy
            val diagnostic = _abi_diagnostic(result)
            diagnostic shouldBe _abi_diagnostic(direct)
            diagnostic.code shouldBe code
            diagnostic.path shouldBe path
            _unpublished(component)
            component.metadataReads shouldBe 1
            component.bindingReads shouldBe 0
            component.installed shouldBe None
            fallback.metadataReads shouldBe 0
            fallback.bindingReads shouldBe 0
          }
        }
      }

      "GC6 reject an invalid last runtime binding without publishing its valid List prefix" in {
        Given("valid actual-generated metadata and an installed typed source with a wrong final Detail View key")
        _with_subsystem { subsystem =>
          val rows = Vector(_success(_fixture.captureC(1, 29)))
          val fallback = new FallbackFactory()
          val component = _initialize(subsystem, new ActualComponent(rows, invalidLastBinding = true), Some(fallback))
          When("normal bootstrap joins the entire runtime vector after legacy setup")
          val result = new ComponentFactory().bootstrapC(component)
          Then("the unchanged B1 typed diagnostic rejects metadata runtime and flag while earlier semantic registration may remain")
          _failure(result).conclusion.observation.taxonomy shouldBe Taxonomy.argumentInvalid
          _failure(result).conclusion.observation.cause.getException match {
            case Some(carrier: DisplayProjection.ProjectionException) =>
              carrier.diagnostic.code shouldBe "DSP96_PROJECTION_VIEW_BINDING_MISMATCH"
              carrier.diagnostic.path shouldBe "viewBindingId"
              carrier.diagnostic.reason should not be empty
            case _ => fail("expected preserved typed B1 projection diagnostic")
          }
          _unpublished(component)
          component.metadataReads shouldBe 1
          component.bindingReads shouldBe 1
          fallback.metadataReads shouldBe 0
          fallback.bindingReads shouldBe 0
          component.viewSpace.containsBrowser(_installed(component).browser) shouldBe true
          _idle(component)
        }
      }
    }

    "generated consumer laws" which {
      "GCP1 preserve exact actual and expected bootstrap models for at least 100 undiscarded typed cases" in {
        Given("positive revisions distinct resources Unicode whitespace text all ESS states and availability with finite row permutations including empty")
        val orders = (0 to 3).toVector.flatMap(size => Vector(0, 1, 2).combinations(size)
          .flatMap(_.permutations).toVector)
        val genrows = for {
          revisions <- Gen.listOfN(3, Gen.choose(1L, 1000000L))
          text <- Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
          unicode <- Gen.oneOf("日本語 Ω", "β e\u0301", "捕捉 é")
          statuses <- Gen.listOfN(3, Gen.oneOf(CaptureStatus.Draft, CaptureStatus.Ready, CaptureStatus.Held))
          availability <- Gen.listOfN(3, Gen.oneOf(ActionAvailability.Hidden, ActionAvailability.Disabled, ActionAvailability.Enabled))
          order <- Gen.oneOf(orders)
        } yield {
          val rows = Vector.tabulate(3) { index =>
            _success(_fixture.captureC(index + 1, revisions(index))).copy(
              title = s"  $unicode $text $index\t", description = s"\t$unicode $text\n  ",
              status = statuses(index), availability = availability(index))
          }
          order.map(rows(_))
        }
        val property = Prop.forAll(genrows) { rows =>
          Given("one generated typed case with unchanged actual definitions and a fresh subsystem and two independent consumers")
          val subsystem = TestComponentFactory.emptySubsystem("generated-logical-ui-property")
          try {
            val context = ExecutionContext.create()
            given ExecutionContext = context
            val query = Query("property contextual source")
            val actual = _initialize(subsystem, new ActualComponent(rows))
            val expected = _initialize(subsystem, new ExpectedComponent(rows))
            When("both actual and expected providers use normal bootstrap")
            val factory = new ComponentFactory()
            val actualboot = factory.bootstrapC(actual)
            val expectedboot = factory.bootstrapC(expected)
            Then("complete publication retains each source vector and does not evaluate either contextual Browser")
            actualboot.isSuccess shouldBe true
            expectedboot.isSuccess shouldBe true
            _published(actual, _definitions)
            _published(expected, _expected)
            _idle(actual)
            _idle(expected)
            (_installed(actual).browser eq _installed(expected).browser) shouldBe false
            When("the same caller Query and resource IDs are consumed by each registry under the same context")
            val actuallist = _success(_registry(actual).queryC("collecting-resources", query))
            val expectedlist = _success(_registry(expected).queryC("collecting-resources", query))
            val actualdetails = rows.map(row => _success(_registry(actual).readC("capture-detail", row.resource.entityId)))
            val expecteddetails = rows.map(row => _success(_registry(expected).readC("capture-detail", row.resource.entityId)))
            Then("models agree with exact resources revisions supplied order text roles availability and once-only contextual correspondence")
            actuallist.model shouldBe expectedlist.model
            actualdetails.map(_.model) shouldBe expecteddetails.map(_.model)
            _check_list(actuallist, rows)
            _check_list(expectedlist, rows)
            actualdetails.zip(rows).foreach { case (display, row) => _check_detail(display, row) }
            expecteddetails.zip(rows).foreach { case (display, row) => _check_detail(display, row) }
            (actuallist.definition eq _definitions.head) shouldBe true
            (expectedlist.definition eq _expected.head) shouldBe true
            actualdetails.foreach(display => (display.definition eq _definitions(1)) shouldBe true)
            expecteddetails.foreach(display => (display.definition eq _expected(1)) shouldBe true)
            _definition_correspondence()
            _calls(actual, query, rows, context)
            _calls(expected, query, rows, context)
            true
          } finally subsystem.shutdown()
        }
        When("ScalaCheck checks the bootstrap correspondence law with a minimum of 100 successful cases")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("at least 100 successful cases pass without discarded inputs")
        withClue(s"GCP1 actual generated consumer: $result: ") {
          result.passed shouldBe true
          result.succeeded should be >= 100
          result.discarded shouldBe 0
        }
      }
    }
  }

  private def _with_subsystem(body: Subsystem => Unit): Unit = {
    val subsystem = TestComponentFactory.emptySubsystem("generated-logical-ui-consumer")
    try body(subsystem) finally subsystem.shutdown()
  }
  private def _initialize[A <: ConsumerComponent](subsystem: Subsystem, component: A,
    factory: Option[Component.Factory] = None): A = {
    val componentid = ComponentId("org.goldenport.cncf.test.GeneratedLogicalUiConsumer")
    val core = factory match {
      case Some(selected) => Component.Core.create(name = componentid.name, componentId = componentid,
        instanceId = ComponentInstanceId.default(componentid), protocol = Protocol.empty, factory = selected)
      case None => Component.Core.create(name = componentid.name, componentId = componentid,
        instanceId = ComponentInstanceId.default(componentid), protocol = Protocol.empty)
    }
    component.initialize(ComponentInit(subsystem = subsystem, core = core, origin = ComponentOrigin.Builtin))
    component
  }
  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case _ => fail("expected successful consumer bootstrap or contextual interpretation")
  }
  private def _registry(component: Component): DisplayRuntime.Registry =
    component.displayRuntime.getOrElse(fail("normal bootstrap must publish complete runtime"))
  private def _admitted(component: Component): AdmittedDefinitions =
    component._admitted_logical_ui_definitions.getOrElse(fail("normal bootstrap must publish admitted metadata"))
  private def _installed(component: ConsumerComponent): TypedDisplayRuntimeFixture.Installed =
    component.installed.getOrElse(fail("selected application binding provider must install its typed source"))
  private def _list(display: Display): ListModel = display.model match {
    case model: ListModel => model
    case _ => fail("expected List model")
  }
  private def _detail(display: Display): DetailModel = display.model match {
    case model: DetailModel => model
    case _ => fail("expected Detail model")
  }
  private def _published(component: ConsumerComponent, definitions: Vector[Definition]): Unit = {
    component.collectionsBootstrapped shouldBe true
    component.metadataReads shouldBe 1
    component.bindingReads shouldBe 1
    _registry(component).screenIds shouldBe definitions.map(_.screenId)
    (_admitted(component).definitions eq definitions) shouldBe true
    _admitted(component).definitions.zip(definitions).foreach { case (actual, expected) =>
      (actual eq expected) shouldBe true
    }
  }
  private def _idle(component: ConsumerComponent): Unit = {
    val browser = _installed(component).browser
    browser.findCalls shouldBe empty
    browser.queryCalls shouldBe empty
    browser.contextFreeCalls shouldBe 0
    browser.countCalls shouldBe 0
  }
  private def _calls(component: ConsumerComponent, query: Query[?], rows: Vector[Capture], context: ExecutionContext): Unit = {
    val browser = _installed(component).browser
    browser.queryCalls.size shouldBe 1
    (browser.queryCalls.head._1 eq query) shouldBe true
    (browser.queryCalls.head._2 eq context) shouldBe true
    browser.findCalls.map(_._1) shouldBe rows.map(_.resource.entityId)
    browser.findCalls.foreach { case (_, supplied) => (supplied eq context) shouldBe true }
    browser.contextFreeCalls shouldBe 0
    browser.countCalls shouldBe 0
  }
  private def _check_list(display: Display, rows: Vector[Capture]): Unit = {
    _list(display) shouldBe ListModel("collecting-resources", "Collecting Resources",
      rows.map(row => ListItem(row.resource, Vector(
        Field("title", "Title", PresentationRole.Title, DisplayValue.Text(row.title)),
        Field("stage", "Capture stage", PresentationRole.Status, DisplayValue.Text(_fixture.stage(row.status)))
      ), Vector.empty)), DetailTarget("capture-detail"), Vector.empty, None)
  }
  private def _check_detail(display: Display, row: Capture): Unit = {
    _detail(display) shouldBe DetailModel("capture-detail", "Capture Detail", row.resource,
      Vector(Section("main", None, Vector(
        Field("title", "Title", PresentationRole.Title, DisplayValue.Text(row.title)),
        Field("stage", "Capture stage", PresentationRole.Status, DisplayValue.Text(_fixture.stage(row.status))),
        Field("description", "Description", PresentationRole.Description, DisplayValue.Text(row.description))
      ))), Vector(Action("request-review", "Request Review", "ess.request-review", row.availability)))
  }
  private def _definition_correspondence(): Unit = {
    _definitions.size shouldBe _expected.size
    _definitions.zip(_expected).foreach { case (actual, expected) =>
      actual.abiIdentity shouldBe expected.abiIdentity
      actual.abiVersion shouldBe expected.abiVersion
      actual.semanticContract shouldBe expected.semanticContract
      actual.screenId shouldBe expected.screenId
      actual.definitionRevision shouldBe expected.definitionRevision
      actual.viewBindingId shouldBe expected.viewBindingId
      actual.screen shouldBe expected.screen
      actual.provenance shouldBe SourceProvenance("cozy.logical-ui-generated.v1", "ess2-01", "ess2-01-cml-1")
      expected.provenance shouldBe SourceProvenance("cncf.expected-generated-fixture", "ess2-01", "fixture-1")
      actual.provenance should not be expected.provenance
    }
  }
  private def _unpublished(component: Component): Unit = {
    component.displayRuntime shouldBe None
    component._admitted_logical_ui_definitions shouldBe None
    component.collectionsBootstrapped shouldBe false
  }
  private def _failure(result: Consequence[?]): Consequence.Failure[?] = result match {
    case failure: Consequence.Failure[?] => failure
    case _ => fail("expected structured consumer rejection")
  }
  private def _abi_diagnostic(result: Consequence[?]): Diagnostic = {
    _failure(result).conclusion.observation.cause.getException match {
      case Some(carrier: AdmissionException) => carrier.diagnostic
      case _ => fail("expected authoritative typed ABI admission diagnostic")
    }
  }
}
