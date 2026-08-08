package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInit, ComponentInstanceId, ComponentInstanceMetadata, ComponentLocator, ComponentOrigin, ComponentSpace}
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.config.{ComponentParameterDiagnostics, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.projection.DescribeProjection
import org.goldenport.cncf.projection.HelpProjection
import org.goldenport.cncf.projection.SchemaProjection
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, TestComponentFactory}
import org.goldenport.observation.Descriptor
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56RuntimeIdentityProjectionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {
  private val _e12 = afterWord("in spec:phase-56-runtime-identity-projection, example:E12, rules:CID05C-R8, phase:56, slice:CID-05C")
  private val _e13 = afterWord("in spec:phase-56-runtime-identity-projection, example:E13, rules:CID06C-R3,R4,R6, phase:56, slice:CID-06C")
  private val _e14 = afterWord("in spec:phase-56-runtime-identity-projection, example:E14, rules:CID05C-R8, phase:56, slice:CID-05C")
  private val _e15 = afterWord("in spec:phase-56-runtime-identity-projection, example:E15, rules:CID05C-R8, phase:56, slice:CID-05C")
  private val _e16 = afterWord("in spec:phase-56-runtime-identity-projection, example:E16, rules:CID05C-R8, phase:56, slice:CID-05C")
  private val _e17 = afterWord("in spec:phase-56-runtime-identity-projection, example:E17, rules:CID05C-R8, phase:56, slice:CID-05C")
  private val _e18 = afterWord("in spec:phase-56-runtime-identity-projection, example:E18, rules:CID05C-R8, phase:56, slice:CID-05C")
  private val _e19 = afterWord("in spec:phase-56-runtime-identity-projection, example:E19, rules:CID05C-R6,R7, phase:56, slice:CID-05C")
  private val _e20 = afterWord("in spec:phase-56-runtime-identity-projection, example:E20, rules:CID05C-R10, phase:56, slice:CID-05C")
  private val _e21 = afterWord("in spec:phase-56-runtime-identity-projection, example:E21, rules:CID06C-R6,R7, phase:56, slice:CID-06C")
  private val _e22 = afterWord("in spec:phase-56-runtime-identity-projection, example:E22, rules:CID06C-R8,R9, phase:56, slice:CID-06C")
  private val _e8 = afterWord("in spec:phase-56-runtime-identity-projection, example:E8, rules:CID05C-R8, phase:56, slice:CID-05C")
  private val _e9 = afterWord("in spec:phase-56-runtime-identity-projection, example:E9, rules:CID05C-R8, phase:56, slice:CID-05C")
  private def _metadata(exampleid: String) =
    afterWord(s"in spec:phase-56-runtime-identity-projection, example:$exampleid, rules:CID05C-R9, phase:56, slice:CID-05C")
  "Phase 56 runtime identity projection" should {
    "use one canonical identity across runtime selectors and projections" which {
    "E12 select ComponentSpace by exact ComponentId and reject ambiguous displays" must _e12 {
      "when exercising: E12 select ComponentSpace by exact ComponentId and reject ambiguous displays" in {
        Given("two qualified components with display identities that collide after compatibility normalization")
        val alpha = _component("org.alpha.Shared", "Shared")
        val beta = _component("org.beta.Shared", "shared")
        val space = new ComponentSpace().add(Vector(alpha, beta))

        When("one component is selected by its exact ComponentId or by its display")
        val exact = space.find(ComponentLocator.ComponentIdLocator(ComponentId("org.alpha.Shared")))
        val display = space.find(ComponentLocator.NameLocator("Shared"))

        Then("exact identity succeeds and ambiguous display identity never chooses first")
        exact shouldBe Some(alpha)
        display shouldBe None
      }
    }

    "E20 select the declared default within one qualified ComponentId for exact and display locators" must _e20 {
      "when exercising: E20 select the declared default within one qualified ComponentId for exact and display locators" in {
        Given("two instances of one qualified component inserted in dynamic then static/default order")
        val dynamic = _component("org.alpha.Shared", "Shared", instance = "dynamic")
        val static = _component("org.alpha.Shared", "Shared", instance = "static", isdefault = true)
        val space = new ComponentSpace().add(Vector(dynamic, static))

        When("the component is selected by its display alias and exact ComponentId")
        val display = space.find(ComponentLocator.NameLocator("Shared"))
        val exact = space.find(ComponentLocator.ComponentIdLocator(ComponentId("org.alpha.Shared")))

        Then("both locators select the declared static default rather than insertion-order dynamic")
        display shouldBe Some(static)
        exact shouldBe Some(static)
      }
    }

    "E13 resolve qualified identities exactly and display aliases only when unique" must _e13 {
      "when exercising: E13 resolve qualified identities exactly and display aliases only when unique" in {
        Given("two namespace-qualified components with one visible display alias")
        val alpha = _component("org.alpha.Shared", "Shared", service = "notice", operation = "search")
        val beta = _component("org.beta.Shared", "Other", service = "notice", operation = "search")
        val resolver = OperationResolver.build(Vector(alpha, beta))

        When("canonical and display selectors are resolved")
        val canonical = resolver.resolve("org.alpha.Shared.notice.search")
        val display = resolver.resolve("Shared.notice.search")
        val detailed = resolver.resolveWithNotices("Shared.notice.search")

        Then("the exact selector resolves while the colliding bare local identity remains ambiguous")
        canonical shouldBe ResolutionResult.Resolved(
          "org.alpha.Shared.notice.search",
          "org.alpha.Shared",
          "notice",
          "search"
        )
        display shouldBe ResolutionResult.Ambiguous(
          "Shared",
          Vector(
            "org.alpha.Shared.notice.search",
            "org.beta.Shared.notice.search"
          )
        )
        detailed.result shouldBe display
        detailed.notices shouldBe empty

        When("the display alias becomes ambiguous")
        val ambiguous = OperationResolver.build(Vector(alpha, _component(
          "org.beta.Shared",
          "Shared",
          service = "notice",
          operation = "search"
        ))).resolve("Shared.notice.search")

        Then("the candidates retain qualified identities")
        ambiguous shouldBe ResolutionResult.Ambiguous(
          "Shared",
          Vector(
            "org.alpha.Shared.notice.search",
            "org.beta.Shared.notice.search"
          )
        )
      }
    }

    "E1 reject display and artifact compatibility collisions while retaining exact ComponentId lookup" must _metadata("E1") {
      "when exercising: reject display and artifact compatibility collisions while retaining exact ComponentId lookup" in {
        Given("one component display and another component artifact spelling with the same compatibility alias")
        val alpha = _component("org.alpha.Shared", "Shared")
        val beta = _component("org.beta.Other", "Other").withArtifactMetadata(
          Component.ArtifactMetadata(
            sourceType = "spec",
            name = "shared",
            version = "0.1.0",
            component = Some("Other"),
            componentId = Some(ComponentId("org.beta.Other"))
          )
        )
        val space = new ComponentSpace().add(Vector(alpha, beta))

        When("the shared compatibility alias and exact alpha identity are selected")
        val alias = space.find(ComponentLocator.NameLocator("Shared"))
        val exact = space.find(ComponentLocator.ComponentIdLocator(ComponentId("org.alpha.Shared")))

        Then("the full alias candidate set is ambiguous while exact identity remains authoritative")
        alias shouldBe None
        exact shouldBe Some(alpha)
      }
    }

    "E14 find and route builtin Admin through exact and display-compatible selectors" must _e14 {
      "when exercising: E14 find and route builtin Admin through exact and display-compatible selectors" in {
        Given("the admitted default subsystem")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))

        When("Admin is located through its exact ID and its compatibility display")
        val exact = subsystem.findComponent(BuiltinComponentIdentity.ADMIN)
        val display = subsystem.findComponent(AdminComponent.name)
        val canonicalresponse = subsystem.executeHttp(
          HttpRequest.fromPath(HttpRequest.GET, "/org.goldenport.cncf.Admin/system/ping")
        )
        val legacyresponse = subsystem.executeHttp(
          HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")
        )
        val dotresponse = subsystem.executeHttp(
          HttpRequest.fromPath(HttpRequest.GET, "/admin.system.ping")
        )

        Then("both lookups and canonical/legacy routes resolve the same runtime operation")
        exact shouldBe display
        exact.map(_.componentId) shouldBe Some(BuiltinComponentIdentity.ADMIN)
        canonicalresponse.code shouldBe 200
        legacyresponse.code shouldBe 200
        dotresponse.code shouldBe 200
      }
    }

    "E16 preserve exact builtin registry identities and visible displays" must _e16 {
      "when exercising: E16 preserve exact builtin registry identities and visible displays" in {
        Given("the framework builtin identity registry")
        val ids = Vector(
          BuiltinComponentIdentity.ADMIN,
          BuiltinComponentIdentity.AUTH,
          BuiltinComponentIdentity.BLOB,
          BuiltinComponentIdentity.CLIENT,
          BuiltinComponentIdentity.DEBUG,
          BuiltinComponentIdentity.EVENT,
          BuiltinComponentIdentity.JOB_CONTROL,
          BuiltinComponentIdentity.MESSAGE_DELIVERY_STUB,
          BuiltinComponentIdentity.METRICS,
          BuiltinComponentIdentity.SPECIFICATION,
          BuiltinComponentIdentity.TAG,
          BuiltinComponentIdentity.TOOL,
          BuiltinComponentIdentity.WORKFLOW
        )

        When("the registry is projected")
        val names = ids.map(_.name)

        Then("every runtime identity is exact and unique in the CNCF namespace")
        names.distinct shouldBe names
        names.foreach { name =>
          name should startWith("org.goldenport.cncf.")
        }
        BuiltinComponentIdentity.ADMIN shouldBe ComponentId("org.goldenport.cncf.Admin")
      }
    }

    "E17 retain every default builtin display independently from its qualified ID" must _e17 {
      "when exercising: E17 retain every default builtin display independently from its qualified ID" in {
        Given("the default subsystem builtin set")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val expected = Table(
          ("componentId", "displayName"),
          (BuiltinComponentIdentity.ADMIN, "admin"),
          (BuiltinComponentIdentity.AUTH, "auth"),
          (BuiltinComponentIdentity.BLOB, "blob"),
          (BuiltinComponentIdentity.CLIENT, "client"),
          (BuiltinComponentIdentity.DEBUG, "debug"),
          (BuiltinComponentIdentity.EVENT, "event"),
          (BuiltinComponentIdentity.JOB_CONTROL, "job_control"),
          (BuiltinComponentIdentity.MESSAGE_DELIVERY_STUB, "MessageDeliveryStub"),
          (BuiltinComponentIdentity.METRICS, "metrics"),
          (BuiltinComponentIdentity.SPECIFICATION, "spec"),
          (BuiltinComponentIdentity.TAG, "tag"),
          (BuiltinComponentIdentity.TOOL, "tool"),
          (BuiltinComponentIdentity.WORKFLOW, "workflow")
        )

        When("the default builtin components are projected")
        val displays = subsystem.components.map(component => component.componentId -> component.displayName).toMap

        Then("each exact registry ID retains its established visible display")
        forAll(expected) { (id, display) =>
          displays.get(id) shouldBe Some(display)
        }
      }
    }

    "E18 resolve a qualified selector from the right and retain its canonical identity" must _e18 {
      "when exercising: E18 resolve a qualified selector from the right and retain its canonical identity" in {
        Given("qualified runtime operation identities sharing a visible local ID")
        val resolver = OperationResolver.fromFqns(Vector(
          "org.alpha.textus.Shared.notice.search",
          "org.beta.textus.Shared.notice.search"
        ))

        When("the exact qualified selector is resolved")
        val result = resolver.resolve("org.alpha.textus.Shared.notice.search")

        Then("the qualified component prefix is retained exactly")
        result shouldBe ResolutionResult.Resolved(
          "org.alpha.textus.Shared.notice.search",
          "org.alpha.textus.Shared",
          "notice",
          "search"
        )
      }
    }

    "E15 project qualified help selectors while preserving visible usage" must _e15 {
      "when exercising: E15 project qualified help selectors while preserving visible usage" in {
        Given("the builtin Admin component in the default subsystem")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val admin = subsystem.findComponent(BuiltinComponentIdentity.ADMIN).getOrElse(fail("Admin is missing"))

        When("qualified component, service, and operation help is projected")
        val component = HelpProjection.projectModel(admin, Some("org.goldenport.cncf.Admin"))
        val service = HelpProjection.projectModel(admin, Some("org.goldenport.cncf.Admin.system"))
        val operation = HelpProjection.projectModel(admin, Some("org.goldenport.cncf.Admin.system.ping"))

        Then("selectors are canonical while visible help remains the legacy display")
        component.componentId shouldBe Some("org.goldenport.cncf.Admin")
        component.name shouldBe "admin"
        component.selector.map(_.canonical) shouldBe Some("org.goldenport.cncf.Admin")
        service.componentId shouldBe Some("org.goldenport.cncf.Admin")
        service.selector.map(_.canonical) shouldBe Some("org.goldenport.cncf.Admin.system")
        operation.componentId shouldBe Some("org.goldenport.cncf.Admin")
        operation.selector.map(_.canonical) shouldBe Some("org.goldenport.cncf.Admin.system.ping")
        operation.selector.map(_.rest) shouldBe Some("/admin/system/ping")
      }
    }

    "E19 advertise only canonical help selectors and usage when display aliases collide with artifact aliases" must _e19 {
      "when exercising: E19 advertise only canonical help selectors and usage when display aliases collide with artifact aliases" in {
        Given("one component display and another component artifact alias that collide after normalization")
        val subsystem = TestComponentFactory.emptySubsystem("phase56-ambiguous-help")
        val alpha = _component("org.alpha.Shared", "Shared", subsystem = subsystem)
        val beta = _component("org.beta.Other", "Other", subsystem = subsystem).withArtifactMetadata(
          Component.ArtifactMetadata(
            sourceType = "spec",
            name = "shared.alias",
            version = "0.1.0",
            component = Some("shared"),
            componentId = Some(ComponentId("org.beta.Other"))
          )
        )
        subsystem.add(Vector(alpha, beta))

        When("help is projected for the exact alpha identity and non-exact qualified aliases")
        val componenthelp = HelpProjection.projectModel(alpha, Some("org.alpha.Shared"))
        val servicehelp = HelpProjection.projectModel(alpha, Some("org.alpha.Shared.service"))
        val operationhelp = HelpProjection.projectModel(alpha, Some("org.alpha.Shared.service.operation"))
        val selector = operationhelp.selector.getOrElse(fail("selector is missing"))
        val unknownqualified = HelpProjection.projectModel(alpha, Some("shared.alias.service.operation"))
        val malformed = HelpProjection.projectModel(alpha, Some("shared..alias.service.operation"))

        Then("canonical selection and usage remain authoritative while qualified aliases and malformed selectors are not projected")
        selector.accepted should contain ("org.alpha.Shared.service.operation")
        selector.accepted should not contain "Shared.service.operation"
        selector.cli shouldBe "shared.service.operation"
        selector.rest shouldBe "/shared/service/operation"
        componenthelp.usage shouldBe Vector("command help org.alpha.Shared.meta")
        servicehelp.usage shouldBe Vector("command help org.alpha.Shared.service.operation")
        operationhelp.usage shouldBe Vector("command org.alpha.Shared.service.operation")
        unknownqualified.`type` shouldBe "error"
        malformed.`type` shouldBe "error"
      }
    }

    "E21 converge Help and Meta projections for unique, ambiguous, and unknown qualified selectors" must _e21 {
      "when exercising: converge Help and Meta projections for unique, ambiguous, and unknown qualified selectors" in {
        Given("two qualified components that share one display alias and a third component with a unique display alias")
        val subsystem = TestComponentFactory.emptySubsystem("phase56-help-meta-boundary")
        val alpha = _component("org.alpha.Shared", "Shared", subsystem = subsystem)
        val beta = _component("org.beta.Shared", "Shared", subsystem = subsystem)
        val gamma = _component("org.gamma.Unique", "Unique", subsystem = subsystem)
        subsystem.add(Vector(alpha, beta, gamma))

        When("Help and Meta are projected through unique, ambiguous, and unknown qualified selectors")
        val uniquehelp = HelpProjection.projectModel(alpha, Some("org.alpha.Shared.service.operation"))
        val uniquealiashelp = HelpProjection.projectModel(gamma, Some("Unique.service.operation"))
        val ambiguoushelp = HelpProjection.projectModel(alpha, Some("Shared.service.operation"))
        val unique = DescribeProjection.project(gamma, Some("org.gamma.Unique"))
        val ambiguous = DescribeProjection.project(alpha, Some("Shared"))
        val unknown = SchemaProjection.project(gamma, Some("org.unknown.Unique"))

        Then("Help keeps the exact selector and advertises a unique display alias only when it is accepted")
        uniquehelp.`type` shouldBe "operation"
        uniquealiashelp.`type` shouldBe "operation"
        uniquehelp.selector.map(_.accepted) shouldBe Some(Vector("org.alpha.Shared.service.operation"))
        uniquealiashelp.selector.map(_.accepted) shouldBe Some(Vector(
          "org.gamma.Unique.service.operation",
          "Unique.service.operation"
        ))
        uniquealiashelp.usage shouldBe Vector("command unique.service.operation")
        ambiguoushelp.`type` shouldBe "error"
        ambiguoushelp.selector shouldBe None
        ambiguoushelp.usage shouldBe empty

        And("Meta preserves exact qualified projection while ambiguous and unknown qualified aliases remain errors")
        unique.getString("type") shouldBe Some("component")
        unique.getString("name") shouldBe Some("org.gamma.Unique")
        ambiguous.getString("type") shouldBe Some("error")
        unknown.getString("type") shouldBe Some("error")
      }
    }

    "E8 expose Admin assembly records with exact ID and separate display name" must _e8 {
      "when exercising: expose Admin assembly records with exact ID and separate display name" in {
        Given("the builtin Admin component")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))
        val admin = subsystem.findComponent(BuiltinComponentIdentity.ADMIN).getOrElse(fail("Admin is missing"))

        When("the Admin assembly record is projected")
        val record = AdminComponent._assembly_component_record(admin)

        Then("identity and presentation are separate")
        record.getString("componentId") shouldBe Some("org.goldenport.cncf.Admin")
        record.getString("name") shouldBe Some("admin")
      }
    }

    "E9 retain exact ComponentId and canonical ComponentInstanceId in diagnostics" must _e9 {
      "when exercising: retain exact ComponentId and canonical ComponentInstanceId in diagnostics" in {
        Given("a qualified component instance")
        val componentid = ComponentId("org.alpha.Shared")
        val instanceid = ComponentInstanceId(componentid, "blue")

        When("a parameter-context diagnostic is created")
        val result = ComponentParameterDiagnostics.contextMissing[Unit](
          "missing",
          componentid,
          instanceid
        )

        Then("both identity facets are exact and distinguish the instance")
        val ids = result match {
          case Consequence.Failure(conclusion) =>
            conclusion.observation.cause.descriptor.facets.collect {
              case Descriptor.Facet.Id(value) => value
            }
          case _ => fail("expected failure")
        }
        ids should contain allOf ("org.alpha.Shared", "org.alpha.Shared@blue")
      }
    }

    "E22 expose deduplicated compatibility warnings through the Admin assembly report operation" must _e22 {
      "when exercising: expose deduplicated compatibility warnings through the Admin assembly report operation" in {
        Given("a server subsystem owned by one runtime whose Admin operation is reached through one legacy display alias repeatedly")
        val configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
        val runtime = GlobalRuntimeContext.create(
          "phase56-admin-observability",
          RuntimeConfig.default,
          configuration,
          ExecutionContext.create().observability,
          AliasResolver.empty
        )
        val previous = GlobalRuntimeContext.current
        GlobalRuntimeContext.current = Some(runtime)
        try {
          val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"), configuration)
          val legacy = HttpRequest.fromPath(HttpRequest.GET, "/admin/system/ping")

          When("the accepted alias is resolved twice and the canonical assembly report is requested")
          subsystem.executeHttp(legacy).code shouldBe 200
          subsystem.executeHttp(legacy).code shouldBe 200
          val reportresult = subsystem.executeOperationResponse(Request.of(
            component = "org.goldenport.cncf.Admin",
            service = "assembly",
            operation = "report"
          ))
          val report = reportresult.toOption.collect {
            case OperationResponse.RecordResponse(record) => record
          }.getOrElse(fail(s"Admin assembly report was not a RecordResponse: $reportresult"))
          val warnings = report.getRecord("warnings").getOrElse(fail("assembly warning projection is missing"))
          val warningrecords = warnings.getAny("warnings").collect {
            case xs: Seq[?] => xs.collect { case value: Record => value }.toVector
          }.getOrElse(Vector.empty)

          Then("the report exposes one compatibility warning with stable fields despite repeated alias resolution")
          warnings.getString("status") shouldBe Some("warning")
          warnings.getInt("warningCount") shouldBe Some(1)
          warningrecords should have size 1
          warningrecords.head.getString("kind") shouldBe Some("component-identity-compatibility")
          warningrecords.head.getString("component") shouldBe Some("org.goldenport.cncf.Admin")
          warningrecords.head.getString("reason").getOrElse("") should include ("surface=runtime-selector")
          warningrecords.head.getString("reason").getOrElse("") should include ("alias=admin")
          warningrecords.head.getString("message").getOrElse("") should include ("canonical=org.goldenport.cncf.Admin")
        } finally {
          GlobalRuntimeContext.current = previous
        }
      }
    }
    }
  }

  private def _component(
    componentid: String,
    displayname: String,
    service: String = "service",
    operation: String = "operation",
    subsystem: Subsystem = null,
    instance: String = "default",
    isdefault: Boolean = false
  ): Component = {
    val id = ComponentId(componentid)
    val effectivesubsystem = Option(subsystem).getOrElse(TestComponentFactory.emptySubsystem(componentid))
    val metadata =
      if (instance == "default" && !isdefault) None
      else Some(ComponentInstanceMetadata(
        componentName = displayname,
        instance = instance,
        isDefault = isdefault,
        componentId = Some(id)
      ))
    val component = new Component() {
      override def displayName: String = displayname
    }
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = service,
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation(operation))
            )
          )
        )
      )
    )
    component.initialize(
      ComponentInit(
        subsystem = effectivesubsystem,
        core = Component.Core.create(
          id.name,
          id,
          ComponentInstanceId(id, instance),
          protocol
        ),
        origin = ComponentOrigin.Main,
        componentDescriptors = Vector.empty,
        participantRole = Component.ParticipantRole.Primary,
        instanceMetadata = metadata
      )
    )
  }

  private final case class NoopOperation(
    override val name: String
  ) extends spec.OperationDefinition {
    override val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = name,
        request = spec.RequestDefinition(),
        response = spec.ResponseDefinition.void
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.notImplemented("not used")
  }
}
