package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentIdentityCompatibilityAdapter, ComponentInit, ComponentInstanceId, ComponentInstanceMetadata, ComponentLocator, ComponentOrigin, ComponentSpace}
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.component.builtin.admin.AdminComponent
import org.goldenport.cncf.config.ComponentParameterDiagnostics
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
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug. 15, 2026
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
  private val _e23 = afterWord("in spec:phase-56-runtime-identity-projection, example:E23, rules:CID06C-R8,R9, phase:56, slice:CID-06C")
  private val _e8 = afterWord("in spec:phase-56-runtime-identity-projection, example:E8, rules:CID05C-R8, phase:56, slice:CID-05C")
  private val _e9 = afterWord("in spec:phase-56-runtime-identity-projection, example:E9, rules:CID05C-R8, phase:56, slice:CID-05C")
  private def _metadata(exampleid: String) =
    afterWord(s"in spec:phase-56-runtime-identity-projection, example:$exampleid, rules:CID05C-R9, phase:56, slice:CID-05C")
  "Phase 56 runtime identity projection" should {
    "use one canonical identity across runtime selectors and projections" which {
    "E12 select ComponentSpace by exact ComponentId and reject ambiguous displays" must _e12 {
      "when exercising: E12 select ComponentSpace by exact ComponentId and reject ambiguous displays" in {
        Given("two qualified components with distinct visible display identities")
        val alpha = _component("org.alpha.Shared", "Shared")
        val beta = _component("org.beta.Other", "Other")
        val space = new ComponentSpace().add(Vector(alpha, beta))

        When("one component is selected by its exact ComponentId or a bare display locator")
        val exact = space.find(ComponentLocator.ComponentIdLocator(ComponentId("org.alpha.Shared")))
        val display = space.find(ComponentLocator.NameLocator("Shared"))

        Then("exact identity succeeds and a unique bare display identity is rejected")
        exact shouldBe Some(alpha)
        display shouldBe None
      }
    }

    "E23 resolve normalized qualified IDs only on the WebPath surface" must _e23 {
      "when exercising: E23 resolve normalized qualified IDs only on the WebPath surface" in {
        Given("one admitted canonical component and two constructible canonical IDs whose normalized paths collide")
        val canonical = ComponentId("org.alpha.Shared")
        val aliases = Vector(ComponentIdentityCompatibilityAdapter.AliasCandidate(canonical, Vector.empty))
        val space = new ComponentSpace().add(_component(canonical.name, "Shared"))
        val first = ComponentId("org.alpha.OAuth")
        val second = ComponentId("org.alpha.OAUTH")
        val collisionaliases = Vector(
          ComponentIdentityCompatibilityAdapter.AliasCandidate(first, Vector.empty),
          ComponentIdentityCompatibilityAdapter.AliasCandidate(second, Vector.empty)
        )
        val normalized = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(canonical.name)
        val collision = org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(first.name)

        When("normalized qualified paths are resolved through WebPath and strict local lookup")
        val web = ComponentIdentityCompatibilityAdapter.resolveAliases(
          normalized,
          aliases,
          ComponentIdentityCompatibilityAdapter.Surface.WebPath
        )
        val strict = space.find(ComponentLocator.NameLocator(normalized))
        val ambiguous = ComponentIdentityCompatibilityAdapter.resolveAliases(
          collision,
          collisionaliases,
          ComponentIdentityCompatibilityAdapter.Surface.WebPath
        )
        val unsupported = ComponentIdentityCompatibilityAdapter.resolveAliases(
          "org-alpha-Missing",
          aliases,
          ComponentIdentityCompatibilityAdapter.Surface.WebPath
        )

        Then("WebPath admits the unique canonical projection, preserves ambiguity, rejects unsupported paths, and leaves strict lookup closed")
        web shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        web.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].componentid shouldBe canonical
        strict shouldBe None
        collision shouldBe org.goldenport.cncf.naming.NamingConventions.toNormalizedSegment(second.name)
        ambiguous shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        ambiguous.asInstanceOf[ComponentIdentityCompatibilityAdapter.Rejected].rejection shouldBe a[ComponentIdentityCompatibilityAdapter.Ambiguous]
        unsupported shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        unsupported.asInstanceOf[ComponentIdentityCompatibilityAdapter.Rejected].rejection shouldBe a[ComponentIdentityCompatibilityAdapter.Unsupported]
      }
    }

    "E20 select the declared default within one qualified ComponentId for exact locators" must _e20 {
      "when exercising: E20 select the declared default within one qualified ComponentId for exact locators" in {
        Given("two instances of one qualified component inserted in dynamic then static/default order")
        val dynamic = _component("org.alpha.Shared", "Shared", instance = "dynamic")
        val static = _component("org.alpha.Shared", "Shared", instance = "static", isdefault = true)
        val space = new ComponentSpace().add(Vector(dynamic, static))

        When("the component is selected by its qualified string and exact ComponentId")
        val qualified = space.find(ComponentLocator.NameLocator("org.alpha.Shared"))
        val exact = space.find(ComponentLocator.ComponentIdLocator(ComponentId("org.alpha.Shared")))

        Then("both exact locators select the declared static default rather than insertion-order dynamic")
        qualified shouldBe Some(static)
        exact shouldBe Some(static)
      }
    }

    "E13 resolve exact qualified identities before unique local and presentation compatibility aliases" must _e13 {
      "when exercising: E13 resolve exact qualified identities before unique local and presentation compatibility aliases" in {
        Given("two admitted canonical components with one local and one presentation compatibility selector")
        val alpha = _component("org.alpha.Catalog", "sanpomap", service = "notice", operation = "search")
        val beta = _component("org.beta.Other", "Other", service = "notice", operation = "search")
        val resolver = OperationResolver.build(Vector(alpha, beta))

        When("canonical, local, and presentation selectors are resolved")
        val canonical = resolver.resolveWithNotices("org.alpha.Catalog.notice.search")
        val local = resolver.resolveWithNotices("Catalog.notice.search")
        val presentation = resolver.resolveWithNotices("sanpomap.notice.search")

        Then("the exact selector wins without a notice and unique admitted aliases resolve with notices")
        canonical.result shouldBe ResolutionResult.Resolved(
          "org.alpha.Catalog.notice.search",
          "org.alpha.Catalog",
          "notice",
          "search"
        )
        canonical.notices shouldBe empty
        local.result shouldBe canonical.result
        local.notices.map(_.aliaskind) shouldBe Vector(ComponentIdentityCompatibilityAdapter.AliasKind.Bare)
        presentation.result shouldBe canonical.result
        presentation.notices.map(_.aliaskind) shouldBe Vector(ComponentIdentityCompatibilityAdapter.AliasKind.Presentation)

        When("a presentation selector collides or is unknown")
        val ambiguousresolver = OperationResolver.build(Vector(alpha, _component(
          "org.beta.Other",
          "Sanpomap",
          service = "notice",
          operation = "search"
        )))
        val ambiguous = ambiguousresolver.resolveWithNotices("sanpomap.notice.search")
        val unknown = resolver.resolveWithNotices("missing.notice.search")

        Then("collisions and unknown selectors fail without adaptation")
        ambiguous.result shouldBe ResolutionResult.Ambiguous(
          "sanpomap",
          Vector(
            "org.alpha.Catalog.notice.search",
            "org.beta.Other.notice.search"
          )
        )
        ambiguous.notices shouldBe empty
        unknown.result shouldBe ResolutionResult.NotFound(OperationResolver.ResolutionStage.Component, "missing")
        unknown.notices shouldBe empty
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

        Then("the presentation alias is rejected while exact identity remains authoritative")
        alias shouldBe None
        exact shouldBe Some(alpha)
      }
    }

    "E14 keep local Admin identity strict while accepting its unique legacy HTTP routes" must _e14 {
      "when exercising: E14 exact Admin lookup and canonical and unique legacy HTTP presentation routing" in {
        Given("the admitted default subsystem")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("server"))

        When("Admin is located through its exact ID while canonical and unique legacy HTTP routes are resolved")
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

        Then("local display lookup remains strict while canonical and both unique legacy routes resolve")
        exact.map(_.componentId) shouldBe Some(BuiltinComponentIdentity.ADMIN)
        display shouldBe None
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

    "E19 accept exact and unique presentation Help selectors with canonical usage" must _e19 {
      "when exercising: E19 project exact and unique presentation selectors within one component boundary" in {
        Given("one canonical component with a unique presentation selector")
        val subsystem = TestComponentFactory.emptySubsystem("phase56-ambiguous-help")
        val alpha = _component("org.alpha.Shared", "Shared", subsystem = subsystem)

        When("Help is projected for exact canonical, unique presentation, unknown qualified, and malformed selectors")
        val componenthelp = HelpProjection.projectModel(alpha, Some("org.alpha.Shared"))
        val servicehelp = HelpProjection.projectModel(alpha, Some("org.alpha.Shared.service"))
        val operationhelp = HelpProjection.projectModel(alpha, Some("org.alpha.Shared.service.operation"))
        val selector = operationhelp.selector.getOrElse(fail("selector is missing"))
        val unknownqualified = HelpProjection.projectModel(alpha, Some("shared.alias.service.operation"))
        val malformed = HelpProjection.projectModel(alpha, Some("shared..alias.service.operation"))

        Then("canonical output and usage remain authoritative while unknown and malformed selectors are rejected")
        selector.accepted should contain ("org.alpha.Shared.service.operation")
        selector.accepted should contain ("Shared.service.operation")
        selector.cli shouldBe "shared.service.operation"
        selector.rest shouldBe "/shared/service/operation"
        componenthelp.usage shouldBe Vector("command help org.alpha.Shared.meta")
        servicehelp.usage shouldBe Vector("command help org.alpha.Shared.service.operation")
        operationhelp.usage shouldBe Vector("command org.alpha.Shared.service.operation")
        unknownqualified.`type` shouldBe "error"
        malformed.`type` shouldBe "error"
      }
    }

    "E21 converge Help and Meta projections for unique, ambiguous, and unknown runtime selectors" must _e21 {
      "when exercising: converge Help and Meta projections for unique, ambiguous, and unknown runtime selectors" in {
        Given("two qualified components that share one display alias and a third component with a unique display alias")
        val subsystem = TestComponentFactory.emptySubsystem("phase56-help-meta-boundary")
        val alpha = _component("org.alpha.Shared", "Shared", subsystem = subsystem)
        val beta = _component("org.beta.Shared", "Shared", subsystem = subsystem)
        val gamma = _component("org.gamma.Unique", "Unique", subsystem = subsystem)
        subsystem.add(Vector(alpha, beta, gamma))

        When("Help and Meta are projected through exact, noncanonical, ambiguous, and unknown selectors")
        val uniquehelp = HelpProjection.projectModel(alpha, Some("org.alpha.Shared.service.operation"))
        val uniquealiashelp = HelpProjection.projectModel(gamma, Some("Unique.service.operation"))
        val ambiguoushelp = HelpProjection.projectModel(alpha, Some("Shared.service.operation"))
        val unique = DescribeProjection.project(gamma, Some("org.gamma.Unique"))
        val ambiguous = DescribeProjection.project(alpha, Some("Shared"))
        val unknown = SchemaProjection.project(gamma, Some("org.unknown.Unique"))

        Then("Help accepts the unique admitted presentation selector while preserving canonical output")
        uniquehelp.`type` shouldBe "operation"
        uniquealiashelp.`type` shouldBe "operation"
        uniquehelp.selector.map(_.accepted) shouldBe Some(Vector("org.alpha.Shared.service.operation"))
        uniquealiashelp.selector.map(_.accepted) shouldBe Some(Vector(
          "org.gamma.Unique.service.operation",
          "Unique.service.operation"
        ))
        uniquealiashelp.usage shouldBe Vector("command org.gamma.Unique.service.operation")
        ambiguoushelp.`type` shouldBe "error"
        ambiguoushelp.selector shouldBe None
        ambiguoushelp.usage shouldBe empty

        And("Meta preserves exact qualified projection while ambiguous and unknown selectors remain errors")
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
    val id = org.goldenport.cncf.testutil.TestComponentFactory.componentId(componentid)
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
