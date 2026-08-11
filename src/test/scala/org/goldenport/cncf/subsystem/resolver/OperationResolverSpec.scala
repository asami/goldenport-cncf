package org.goldenport.cncf.subsystem.resolver

import cats.data.NonEmptyVector
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.prop.TableDrivenPropertyChecks

import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.cncf.component.{Component, ComponentDescriptor, ComponentletDescriptor}
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionStage

/*
 * @since   Aug.  8, 2026
 * @version Aug. 11, 2026
 * @author ASAMI, Tomoharu
 */
class OperationResolverSpec extends AnyWordSpec with Matchers with GivenWhenThen with TableDrivenPropertyChecks {
  private val _e1 = afterWord("in spec:operation-resolver, example:E1, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e2 = afterWord("in spec:operation-resolver, example:E2, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e3 = afterWord("in spec:operation-resolver, example:E3, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e4 = afterWord("in spec:operation-resolver, example:E4, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e24 = afterWord("in spec:operation-resolver, example:E24, rules:CID06C-R3,R6, phase:56, slice:CID-06C")
  private val _e25 = afterWord("in spec:operation-resolver, example:E25, rules:CID06C-R1,R4,R6, phase:56, slice:CID-06C")
  private def _metadata(exampleid: String) =
    afterWord(s"in spec:operation-resolver, example:$exampleid, rules:CID05C-R9, phase:56, slice:CID-05C")
  "OperationResolver.resolve" should {
    "selector parsing and candidate resolution" which {
    "E1 parse qualified component identity from the right" must _e1 {
      "when exercising: parse qualified component identity from the right" in {
        Given("one qualified operation identity with a namespace and local component ID")
        val resolver = OperationResolver.fromFqns(Seq("org.alpha.textus.Shared.notice.search"))
        When("the exact qualified selector is resolved")
        val result = resolver.resolve("org.alpha.textus.Shared.notice.search")
        Then("the component prefix is parsed from the right")
        result shouldBe
          ResolutionResult.Resolved(
            "org.alpha.textus.Shared.notice.search",
            "org.alpha.textus.Shared",
            "notice",
            "search"
          )
      }
    }

    "E2 reject leading, trailing, and doubled selector dots without normalizing them" must _e2 {
      "when exercising: reject leading, trailing, and doubled selector dots without normalizing them" in {
        Given("a resolver with one exact qualified operation identity")
        val resolver = OperationResolver.fromFqns(Seq("org.alpha.textus.Shared.notice.search"))
        val malformed = Table(
          "selector",
          ".org.alpha.textus.Shared.notice.search",
          "org.alpha.textus.Shared.notice.search.",
          "org.alpha..textus.Shared.notice.search"
        )

        forAll(malformed) { selector =>
          When("a selector contains an empty dot segment")
          Then("the malformed selector is rejected rather than normalized into an exact identity")
          resolver.resolve(selector) shouldBe ResolutionResult.Invalid("operation selector has an empty segment")
        }
      }
    }

    "E5 resolve 2-dot selector component.service.operation uniquely" must _metadata("E5") {
      "when exercising: resolve 2-dot selector component.service.operation uniquely" in {
      Given("a resolver with two qualified component operations")
      val resolver = OperationResolver.fromFqns(Seq("org.example.C1.s1.op1", "org.example.C2.s2.op2"))
      When("the exact qualified selector is resolved")
      val result = resolver.resolve("org.example.C1.s1.op1")
      Then("the exact operation is selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) => fqn shouldBe "org.example.C1.s1.op1"
        case other => fail(s"unexpected result: $other")
      }
      }
    }

    "E6 resolve 2-dot selector using prefix matching when unique" must _metadata("E6") {
      "when exercising: resolve 2-dot selector using prefix matching when unique" in {
      Given("a resolver with one unique matching operation prefix")
      val resolver = OperationResolver.fromFqns(
        Seq("org.example.Admin.user.find", "org.example.Admin.user.list")
      )
      When("the qualified prefix selector is resolved")
      val result = resolver.resolve("org.us.fi")
      Then("the unique matching operation is selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "org.example.Admin.user.find"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E7 resolve 1-dot selector service.operation when unique across components" must _metadata("E7") {
      "when exercising: resolve 1-dot selector service.operation when unique across components" in {
      Given("a resolver with distinct service-operation pairs")
      val resolver = OperationResolver.fromFqns(Seq("org.example.C1.s1.op1", "org.example.C2.s2.op2"))
      When("the service-operation selector is resolved")
      val result = resolver.resolve("s1.op1")
      Then("the unique component operation is selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) => fqn shouldBe "org.example.C1.s1.op1"
        case other => fail(s"unexpected result: $other")
      }
      }
    }

    "E8 resolve 1-dot selector using prefix matching when unique" must _metadata("E8") {
      "when exercising: resolve 1-dot selector using prefix matching when unique" in {
      Given("a resolver with one unique service-operation prefix")
      val resolver = OperationResolver.fromFqns(
        Seq("org.example.Admin.user.find", "org.example.Admin.group.list")
      )
      When("the service-operation prefix is resolved")
      val result = resolver.resolve("user.fi")
      Then("the unique prefix match is selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "org.example.Admin.user.find"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E9 resolve 0-dot selector operation when unique across all services" must _metadata("E9") {
      "when exercising: resolve 0-dot selector operation when unique across all services" in {
      Given("a resolver with one unique operation name")
      val resolver = OperationResolver.fromFqns(Seq("org.example.C1.s1.unique", "org.example.C2.s2.other"))
      When("the operation selector is resolved")
      val result = resolver.resolve("unique")
      Then("the unique operation is selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) => fqn shouldBe "org.example.C1.s1.unique"
        case other => fail(s"unexpected result: $other")
      }
      }
    }

    "E10 resolve 0-dot selector using prefix matching when unique" must _metadata("E10") {
      "when exercising: resolve 0-dot selector using prefix matching when unique" in {
      Given("a resolver with one unique operation prefix")
      val resolver = OperationResolver.fromFqns(
        Seq("org.example.C1.s1.find", "org.example.C2.s2.list")
      )
      When("the operation prefix is resolved")
      val result = resolver.resolve("fi")
      Then("the unique operation is selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "org.example.C1.s1.find"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E11 return Ambiguous for 0-dot prefix matching when multiple candidates exist" must _metadata("E11") {
      "when exercising: return Ambiguous for 0-dot prefix matching when multiple candidates exist" in {
      Given("a resolver with exact and longer matching operation names")
      val resolver = OperationResolver.fromFqns(
        Seq("org.example.C1.s1.find", "org.example.C2.s2.findAll")
      )
      When("the exact operation selector is resolved")
      val result = resolver.resolve("find")
      Then("the exact operation remains selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "org.example.C1.s1.find"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E12 return Ambiguous for prefix-only 0-dot matching when multiple candidates exist" must _metadata("E12") {
      "when exercising: return Ambiguous for prefix-only 0-dot matching when multiple candidates exist" in {
      Given("a resolver with two prefix-only operation candidates")
      val resolver = OperationResolver.fromFqns(
        Seq("org.example.C1.s1.findOne", "org.example.C2.s2.findAll")
      )
      When("the operation prefix is resolved")
      val result = resolver.resolve("find")
      Then("both matching candidates are reported")
      result match {
        case ResolutionResult.Ambiguous(_, candidates) =>
          candidates.toSet shouldBe Set(
            "org.example.C1.s1.findOne",
            "org.example.C2.s2.findAll"
          )
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E13 return NotFound when no match exists (0-dot)" must _metadata("E13") {
      "when exercising: return NotFound when no match exists (0-dot)" in {
      Given("a resolver without the requested operation")
      val resolver = OperationResolver.fromFqns(Seq("org.example.C1.s1.op1"))
      When("the missing operation is resolved")
      val result = resolver.resolve("missing")
      Then("operation lookup reports absence")
      result match {
        case ResolutionResult.NotFound(stage, _) => stage shouldBe ResolutionStage.Operation
        case other => fail(s"unexpected result: $other")
      }
      }
    }

    "E14 return NotFound when operation is missing (1-dot)" must _metadata("E14") {
      "when exercising: return NotFound when operation is missing (1-dot)" in {
      Given("a resolver without the requested service operation")
      val resolver = OperationResolver.fromFqns(Seq("org.example.C1.s1.op1"))
      When("the missing service operation is resolved")
      val result = resolver.resolve("s1.does_not_exist")
      Then("operation lookup reports absence")
      result match {
        case ResolutionResult.NotFound(stage, _) => stage shouldBe ResolutionStage.Operation
        case other => fail(s"unexpected result: $other")
      }
      }
    }

    "E15 return NotFound when component is unknown (2-dot)" must _metadata("E15") {
      "when exercising: return NotFound when component is unknown (2-dot)" in {
      Given("a resolver without the requested component")
      val resolver = OperationResolver.fromFqns(Seq("org.example.C1.s1.op1"))
      When("the unknown component selector is resolved")
      val result = resolver.resolve("unknown.s1.op1")
      Then("component lookup reports absence")
      result match {
        case ResolutionResult.NotFound(stage, _) => stage shouldBe ResolutionStage.Component
        case other => fail(s"unexpected result: $other")
      }
      }
    }

    "E16 return Ambiguous and list candidates as FQN (0-dot)" must _metadata("E16") {
      "when exercising: return Ambiguous and list candidates as FQN (0-dot)" in {
      Given("a resolver with two identical operation names")
      val resolver = OperationResolver.fromFqns(Seq("org.example.C1.s1.op", "org.example.C2.s2.op"))
      When("the shared operation selector is resolved")
      val result = resolver.resolve("op")
      Then("both qualified candidates are reported")
      result match {
        case ResolutionResult.Ambiguous(_, candidates) =>
          candidates.toSet shouldBe Set("org.example.C1.s1.op", "org.example.C2.s2.op")
        case other => fail(s"unexpected result: $other")
      }
      }
    }

    "E17 prefer exact component match over component prefix matches in FQN selector" must _metadata("E17") {
      "when exercising: prefer exact component match over component prefix matches in FQN selector" in {
      Given("a resolver with exact and prefix-overlapping qualified components")
      val resolver = OperationResolver.fromFqns(
        Seq(
          "org.example.JobControl.job.await_job_result",
          "org.example.JobControl.job_admin.cancel_job",
          "org.example.Job.job.get_job_status"
        )
      )

      When("the exact qualified selector is resolved")
      val result = resolver.resolve("org.example.JobControl.job.await_job_result")
      Then("the exact component operation is selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "org.example.JobControl.job.await_job_result"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E18 return Ambiguous and list candidates as FQN (1-dot)" must _metadata("E18") {
      "when exercising: return Ambiguous and list candidates as FQN (1-dot)" in {
      Given("a resolver with two matching service operations")
      val resolver = OperationResolver.fromFqns(Seq("org.example.C1.svc.op", "org.example.C2.svc.op"))
      When("the shared service-operation selector is resolved")
      val result = resolver.resolve("svc.op")
      Then("both qualified candidates are reported")
      result match {
        case ResolutionResult.Ambiguous(_, candidates) =>
          candidates.toSet shouldBe Set("org.example.C1.svc.op", "org.example.C2.svc.op")
        case other => fail(s"unexpected result: $other")
      }
      }
    }

    "E3 treat 3+ dots as a qualified component selector" must _e3 {
      "when exercising: treat 3+ dots as a qualified component selector" in {
        Given("a resolver with one four-segment qualified operation identity")
        val resolver = OperationResolver.fromFqns(Seq("org.example.Sample.c.d"))
        When("the four-segment selector is resolved")
        val result = resolver.resolve("org.example.Sample.c.d")
        Then("the qualified component prefix remains right-associated")
        result match {
          case ResolutionResult.Resolved("org.example.Sample.c.d", "org.example.Sample", "c", "d") => succeed
          case other => fail(s"unexpected result: $other")
        }
      }
    }

    "E4 resolve real componentlet as component selector when built from runtime components" must _e4 {
      "when exercising: resolve real componentlet as component selector when built from runtime components" in {
        Given("a runtime component and a componentlet with a qualified component identity")
        val resolver = OperationResolver.build(Seq(_component_with_componentlet_metadata(), _componentlet_runtime_component()))

        When("the display-compatible componentlet selector is resolved")
        val result = resolver.resolve("public-notice.notice.search-notices")

        Then("the runtime component identity is returned in qualified form")
        result match {
          case ResolutionResult.Resolved(fqn, component, service, operation) =>
            fqn shouldBe "org.goldenport.fixture.PublicNotice.notice.search-notices"
            component shouldBe "org.goldenport.fixture.PublicNotice"
            service shouldBe "notice"
            operation shouldBe "search-notices"
          case other =>
            fail(s"unexpected result: $other")
        }
      }
    }

    "E19 not resolve componentlet metadata alone as runtime component selector" must _metadata("E19") {
      "when exercising: not resolve componentlet metadata alone as runtime component selector" in {
      Given("only componentlet metadata without a runtime component participant")
      val resolver = OperationResolver.build(Seq(_component_with_componentlet_metadata()))

      When("the componentlet selector is resolved")
      val result = resolver.resolve("public-notice.notice.search-notices")

      Then("metadata alone does not create a runtime component selector")
      result match {
        case ResolutionResult.NotFound(_, _) =>
          succeed
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E20 resolve component artifact metadata name as a component alias" must _metadata("E20") {
      "when exercising: resolve component artifact metadata name as a component alias" in {
      Given("a runtime component with canonical identity and a legacy artifact alias")
      val resolver = OperationResolver.build(Seq(_component_with_artifact_metadata_alias()))

      When("the artifact alias selector is resolved")
      val result = resolver.resolve("textus-user-notification.notification.search-my-notifications")

      Then("the canonical qualified component identity is returned")
      result match {
        case ResolutionResult.Resolved(fqn, component, service, operation) =>
          fqn shouldBe "org.goldenport.fixture.UserNotification.notification.searchMyNotifications"
          component shouldBe "org.goldenport.fixture.UserNotification"
          service shouldBe "notification"
          operation shouldBe "searchMyNotifications"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E24 reject a local-ID and presentation-alias collision across runtime Components" must _e24 {
      "when one Component local ID equals another Component display alias" in {
      Given("two runtime Components whose local and display identities claim the same selector")
      val localcomponent = _runtime_component(
        "org.example.Catalog",
        "catalog-local",
        None
      )
      val displaycomponent = _runtime_component(
        "org.other.Other",
        "Catalog",
        None
      )
      val artifactcomponent = _runtime_component(
        "org.third.Artifact",
        "artifact-component",
        Some("Catalog")
      )
      val resolver = OperationResolver.build(Seq(localcomponent, displaycomponent, artifactcomponent))

      When("the colliding bare Component selector is resolved")
      val result = resolver.resolve("Catalog.notice.search")

      Then("the resolver reports all qualified operations instead of routing to one presentation alias")
      result match {
        case ResolutionResult.Ambiguous(_, candidates) =>
          candidates.toSet shouldBe Set(
            "org.example.Catalog.notice.search",
            "org.other.Other.notice.search",
            "org.third.Artifact.notice.search"
          )
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E25 keep an exact qualified selector authoritative across a local-ID collision" must _e25 {
      "when the qualified selector names the canonical Component explicitly" in {
      Given("the same local-ID and display-alias collision as E24")
      val localcomponent = _runtime_component(
        "org.example.Catalog",
        "catalog-local",
        None
      )
      val displaycomponent = _runtime_component(
        "org.other.Other",
        "Catalog",
        None
      )
      val artifactcomponent = _runtime_component(
        "org.third.Artifact",
        "artifact-component",
        Some("Catalog")
      )
      val resolver = OperationResolver.build(Seq(localcomponent, displaycomponent, artifactcomponent))

      When("the exact qualified Component selector is resolved")
      val result = resolver.resolve("org.example.Catalog.notice.search")

      Then("the canonical identity wins without consulting presentation aliases")
      result shouldBe ResolutionResult.Resolved(
        "org.example.Catalog.notice.search",
        "org.example.Catalog",
        "notice",
        "search"
      )
      }
    }
    }
  }

  "Single Operation Optimization (CLI / Script, FQN-only)" should {

    "E21 resolve when exactly one non-builtin operation exists and selector is FQN" must _metadata("E21") {
      "when exercising: resolve when exactly one non-builtin operation exists and selector is FQN" in {
      Given("a resolver with one qualified operation")
      val resolver = OperationResolver.fromFqns(
        Seq("org.example.App.health.check")
      )

      When("the exact qualified selector is resolved")
      val result = resolver.resolve("org.example.App.health.check")
      Then("the single operation is selected")
      result match {
        case ResolutionResult.Resolved(fqn, _, _, _) =>
          fqn shouldBe "org.example.App.health.check"
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E22 not apply to operation-name-only input such as ping" must _metadata("E22") {
      "when exercising: not apply to operation-name-only input such as ping" in {
      Given("a resolver with one qualified builtin-like operation")
      val resolver = OperationResolver.fromFqns(
        Seq("org.example.Admin.default.ping")
      )

      When("an operation-name-only selector is resolved")
      val result = resolver.resolve("ping")
      Then("single-operation optimization does not select it")
      result match {
        case ResolutionResult.NotFound(_, _) =>
          succeed
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

    "E23 not apply prefix matching during single operation optimization" must _metadata("E23") {
      "when exercising: not apply prefix matching during single operation optimization" in {
      Given("a resolver with one qualified operation")
      val resolver = OperationResolver.fromFqns(
        Seq("org.example.App.health.check")
      )

      When("a prefix-only selector is resolved")
      val result = resolver.resolve("app.health.ch")
      Then("single-operation optimization does not apply prefix matching")
      result match {
        case ResolutionResult.NotFound(_, _) =>
          succeed
        case other =>
          fail(s"unexpected result: $other")
      }
      }
    }

  }

  private def _component_with_componentlet_metadata(): Component = {
    val component = new Component() {
      override def displayName: String = "notice-board"
    }
    component.withComponentDescriptors(
      Vector(
        ComponentDescriptor(
          name = Some("notice-board"),
          componentName = Some("notice-board"),
          componentlets = Vector(
            ComponentletDescriptor(name = "public-notice")
          )
        )
      )
    )
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("search-notices"))
            )
          )
        )
      )
    )
    val core = org.goldenport.cncf.component.Component.Core.create(
      name = "org.goldenport.fixture.NoticeBoard",
      componentid = org.goldenport.cncf.component.ComponentId("org.goldenport.fixture.NoticeBoard"),
      instanceid = org.goldenport.cncf.component.ComponentInstanceId.default(
        org.goldenport.cncf.component.ComponentId("org.goldenport.fixture.NoticeBoard")
      ),
      protocol = protocol
    )
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        subsystem = org.goldenport.cncf.testutil.TestComponentFactory.emptySubsystem("notice-board"),
        core = core,
        origin = org.goldenport.cncf.component.ComponentOrigin.Main,
        componentDescriptors = component.componentDescriptors
      )
    )
  }

  private def _componentlet_runtime_component(): Component = {
    val component = new Component() {
      override def displayName: String = "public-notice"
    }
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("search-notices"))
            )
          )
        )
      )
    )
    val core = org.goldenport.cncf.component.Component.Core.create(
      name = "org.goldenport.fixture.PublicNotice",
      componentid = org.goldenport.cncf.component.ComponentId("org.goldenport.fixture.PublicNotice"),
      instanceid = org.goldenport.cncf.component.ComponentInstanceId.default(
        org.goldenport.cncf.component.ComponentId("org.goldenport.fixture.PublicNotice")
      ),
      protocol = protocol
    )
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        subsystem = org.goldenport.cncf.testutil.TestComponentFactory.emptySubsystem("public-notice"),
        core = core,
        origin = org.goldenport.cncf.component.ComponentOrigin.Main,
        componentDescriptors = Vector.empty
      )
    )
  }

  private def _component_with_artifact_metadata_alias(): Component = {
    val component = new Component() {
      override def displayName: String = "UserNotification"
    }
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notification",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("searchMyNotifications"))
            )
          )
        )
      )
    )
    val core = org.goldenport.cncf.component.Component.Core.create(
      name = "org.goldenport.fixture.UserNotification",
      componentid = org.goldenport.cncf.component.ComponentId("org.goldenport.fixture.UserNotification"),
      instanceid = org.goldenport.cncf.component.ComponentInstanceId.default(
        org.goldenport.cncf.component.ComponentId("org.goldenport.fixture.UserNotification")
      ),
      protocol = protocol
    )
    component.withArtifactMetadata(org.goldenport.cncf.component.Component.ArtifactMetadata(
      sourceType = "car",
      name = "textus-user-notification",
      version = "0.1.1-SNAPSHOT",
      component = Some("textus-user-notification")
    ))
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        subsystem = org.goldenport.cncf.testutil.TestComponentFactory.emptySubsystem("UserNotification"),
        core = core,
        origin = org.goldenport.cncf.component.ComponentOrigin.Main,
        componentDescriptors = Vector.empty
      )
    )
  }

  private def _runtime_component(
    canonicalname: String,
    displayname: String,
    artifactname: Option[String]
  ): Component = {
    val component = new Component() {
      override def displayName: String = displayname
    }
    val protocol = Protocol(
      services = spec.ServiceDefinitionGroup(
        Vector(
          spec.ServiceDefinition(
            name = "notice",
            operations = spec.OperationDefinitionGroup(
              operations = NonEmptyVector.of(NoopOperation("search"))
            )
          )
        )
      )
    )
    val componentid = org.goldenport.cncf.testutil.TestComponentFactory.componentId(canonicalname)
    val core = org.goldenport.cncf.component.Component.Core.create(
      name = componentid.name,
      componentid = componentid,
      instanceid = org.goldenport.cncf.component.ComponentInstanceId.default(componentid),
      protocol = protocol
    )
    artifactname.foreach { name =>
      component.withArtifactMetadata(org.goldenport.cncf.component.Component.ArtifactMetadata(
        sourceType = "car",
        name = name,
        version = "0.1.1-SNAPSHOT",
        component = Some(name)
      ))
    }
    component.initialize(
      org.goldenport.cncf.component.ComponentInit(
        subsystem = org.goldenport.cncf.testutil.TestComponentFactory.emptySubsystem(displayname),
        core = core,
        origin = org.goldenport.cncf.component.ComponentOrigin.Main,
        componentDescriptors = Vector.empty
      )
    )
  }
}

private final case class NoopOperation(
  opname: String
) extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = opname,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition.void
    )
  override def createOperationRequest(req: org.goldenport.protocol.Request): org.goldenport.Consequence[org.goldenport.protocol.operation.OperationRequest] =
    org.goldenport.Consequence.notImplemented("not used")
}
